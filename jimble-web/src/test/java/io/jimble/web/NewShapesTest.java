package io.jimble.web;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.auth.Auth;
import io.jimble.web.context.WebContext;
import io.jimble.web.cookie.Cookie;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.router.RouteMatch;
import io.jimble.web.router.Router;
import io.jimble.web.support.Fakes;
import io.jimble.web.validation.ValidationRules;
import io.jimble.web.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 の形を 1.5 に先に足したもの（Web。要件 D-191）
 */
class NewShapesTest {

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	// region Router.path(String, Consumer)

	@Test
	@DisplayName("D-191 path(パス, ブロック) の中で登録したルートは、そのパスの下に付く")
	void pathBlock () {

		Router router = new Router();
		router.path("/admin", admin -> admin.get("/users", context -> context.response().send("ok")));

		RouteMatch nested = router.match("GET", "/admin/users");
		assertTrue(nested.matched(), "/admin/users に当たらない");
		assertFalse(router.match("GET", "/users").matched(), "親に登録されている");

	}

	// endregion

	// region Cookie

	@Test
	@DisplayName("D-191 putSigned は属性を保ったまま署名し、次のリクエストの get で読める。渡した Cookie は書き換えない")
	void putSigned () {

		Conf.replace(ConfigFactory.parseString("cookie.secret = \"test-secret\"").withFallback(Conf.conf().config()));

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		Cookie cookie = new Cookie("user", "42").path("/app").maxAge(60);

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().putSigned(cookie);
			assertEquals("42", context.cookies().get("user"), "同じリクエストで読み返せない");
			context.response().send("ok");
		}

		assertEquals("42", cookie.value(), "渡した Cookie が書き換わっている");

		String setCookie = sink.setCookies().getFirst();
		assertTrue(setCookie.contains("Path=/app"), setCookie);
		assertTrue(setCookie.contains("Max-Age=60"), setCookie);
		String value = setCookie.substring("user=".length(), setCookie.indexOf(';'));
		assertNotEquals("42", value, "署名されていない");

		Fakes.FakeRequestSource next = new Fakes.FakeRequestSource("GET", "/");
		next.cookie("user", value);
		try (WebContext context = new WebContext(next, new Fakes.FakeResponseSink())) {
			assertEquals("42", context.cookies().get("user"));
		}

	}

	@Test
	@DisplayName("D-191 putUnsigned は署名しない（署名を使っていると get では読めず、raw で読める）")
	void putUnsigned () {

		Conf.replace(ConfigFactory.parseString("cookie.secret = \"test-secret\"").withFallback(Conf.conf().config()));

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().putUnsigned(new Cookie("theme", "dark"));
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();
		assertTrue(setCookie.startsWith("theme=dark;"), setCookie);

		Fakes.FakeRequestSource next = new Fakes.FakeRequestSource("GET", "/");
		next.cookie("theme", "dark");
		try (WebContext context = new WebContext(next, new Fakes.FakeResponseSink())) {
			assertEquals("", context.cookies().get("theme"));
			assertEquals("dark", context.cookies().raw("theme"));
		}

	}

	// endregion

	// region @CheckReturnValue

	@Test
	@DisplayName("D-191 戻り値を捨てると効かないメソッドに @CheckReturnValue が付いている")
	void checkReturnValue () throws Exception {

		assertAnnotated(Router.class, "path", 1);
		assertAnnotated(Auth.class, "attemptLogin", 4);
		assertAnnotated(Csrf.class, "isValid", 1);
		assertAnnotated(ValidationRules.class, "validate", 2);
		assertAnnotated(ValidationRules.class, "errors", 2);
		assertAnnotated(Validator.class, "validate", 3);
		assertAnnotated(Validator.class, "errors", 3);

	}

	/**
	 * クラスファイルを読んで、その名前・引数の数のメソッドに注釈が付いているかを見る
	 * （CLASS 保持なのでリフレクションからは見えない）
	 */
	static void assertAnnotated (Class<?> type, String method, int arity) throws Exception {

		byte[] bytes;
		try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
			bytes = in.readAllBytes();
		}
		ClassModel model = ClassFile.of().parse(bytes);

		List<MethodModel> methods = model.methods().stream()
			.filter(m -> m.methodName().equalsString(method))
			.filter(m -> m.methodTypeSymbol().parameterCount() == arity)
			.toList();
		assertFalse(methods.isEmpty(), type.getSimpleName() + "." + method + "/" + arity + " がありません");

		for (MethodModel m : methods) {
			boolean annotated = m.findAttribute(Attributes.runtimeInvisibleAnnotations())
				.map(a -> a.annotations().stream()
					.anyMatch(an -> an.className().equalsString("Lio/jimble/util/annotation/CheckReturnValue;")))
				.orElse(false);
			assertTrue(annotated, type.getSimpleName() + "." + method + m.methodTypeSymbol().displayDescriptor()
				+ " に @CheckReturnValue が付いていません");
		}

	}

	// endregion

}
