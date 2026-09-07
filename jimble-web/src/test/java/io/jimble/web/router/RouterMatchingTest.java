package io.jimble.web.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Router のマッチングのテスト
 */
class RouterMatchingTest {

	/* 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	@Test
	@DisplayName("T-1 固定パス・パスパラメータ・ワイルドカード・ネスト")
	void basicMatching () {

		Router router = new Router();
		Route root = router.get("/", NOOP);
		Route users = router.get("/users", NOOP);
		Route user = router.get("/users/{id}", NOOP);
		Route nested = router.get("/users/{id}/posts/{postId}", NOOP);
		Route files = router.get("/files/*", NOOP);

		assertSame(root, router.match("GET", "/").route());
		assertSame(users, router.match("GET", "/users").route());
		assertSame(users, router.match("GET", "/users/").route(), "末尾スラッシュは同一視");

		RouteMatch userMatch = router.match("GET", "/users/42");
		assertSame(user, userMatch.route());
		assertEquals("42", userMatch.variables().get("id"));

		RouteMatch nestedMatch = router.match("GET", "/users/42/posts/7");
		assertSame(nested, nestedMatch.route());
		assertEquals("42", nestedMatch.variables().get("id"));
		assertEquals("7", nestedMatch.variables().get("postId"));

		RouteMatch fileMatch = router.match("GET", "/files/a/b/c.txt");
		assertSame(files, fileMatch.route());
		assertEquals("a/b/c.txt", fileMatch.variables().wildcard());

	}

	@Test
	@DisplayName("T-1 未マッチ")
	void unmatched () {

		Router router = new Router();
		router.get("/users", NOOP);

		assertFalse(router.match("GET", "/nope").matched());
		assertNull(router.match("GET", "/nope").route());
		assertFalse(router.match("POST", "/users").matched(), "メソッドが違えば未マッチ");

	}

	@Test
	@DisplayName("T-2 優先順位は 固定 > パスパラメータ > ワイルドカード")
	void priority () {

		Router router = new Router();
		Route fixed = router.get("/a/b", NOOP);
		Route variable = router.get("/a/{x}", NOOP);
		Route wildcard = router.get("/a/*", NOOP);

		assertSame(fixed, router.match("GET", "/a/b").route(), "固定が最優先");
		assertSame(variable, router.match("GET", "/a/c").route(), "次にパスパラメータ");

		RouteMatch deep = router.match("GET", "/a/c/d");
		assertSame(wildcard, deep.route(), "どちらも駄目ならワイルドカード");
		assertEquals("c/d", deep.variables().wildcard());

	}

	@Test
	@DisplayName("T-2 ワイルドカードは深い位置のものが優先される")
	void deeperWildcardWins () {

		Router router = new Router();
		router.get("/*", NOOP);
		Route deeper = router.get("/a/*", NOOP);

		RouteMatch match = router.match("GET", "/a/b/c");

		assertSame(deeper, match.route());
		assertEquals("b/c", match.variables().wildcard());

	}

	@Test
	@DisplayName("/a/* は /a にはマッチしない")
	void wildcardDoesNotMatchEmptyRemainder () {

		Router router = new Router();
		router.get("/a/*", NOOP);

		assertFalse(router.match("GET", "/a").matched());
		assertTrue(router.match("GET", "/a/b").matched());

	}

	@Test
	@DisplayName("T-17 %2F を含むパスがパスパラメータ1つにマッチする")
	void encodedSlashMatchesSingleVariable () {

		Router router = new Router();
		Route search = router.get("/search/{keyword}", NOOP);

		RouteMatch match = router.match("GET", "/search/a%2Fb");

		assertSame(search, match.route(), "デコード済みパスを分割していたら未マッチになる");
		assertEquals("a/b", match.variables().get("keyword"));

	}

	@Test
	@DisplayName("T-19 日本語のパスパラメータ")
	void japaneseVariable () {

		Router router = new Router();
		router.get("/search/{keyword}", NOOP);

		RouteMatch match = router.match("GET", "/search/%E3%81%A6%E3%81%99%E3%81%A8");

		assertEquals("てすと", match.variables().get("keyword"));

	}

	@Test
	@DisplayName("T-12 any() は全メソッドに登録される")
	void anyRegistersAllMethods () {

		Router router = new Router();
		List<Route> routes = router.any("/proxy/*", NOOP);

		assertEquals(HttpMethods.ALL.size(), routes.size());

		for (String method : HttpMethods.ALL) {
			assertTrue(router.match(method, "/proxy/x").matched(), method + " がマッチしない");
		}

	}

	@Test
	@DisplayName("head と trace も登録できる")
	void headAndTrace () {

		Router router = new Router();
		router.head("/health", NOOP);
		router.trace("/debug", NOOP);

		assertTrue(router.match("HEAD", "/health").matched());
		assertTrue(router.match("TRACE", "/debug").matched());

	}

	@Test
	@DisplayName("ルートの重複は登録時に例外")
	void duplicateRouteThrows () {

		Router router = new Router();
		router.get("/users", NOOP);

		IllegalStateException ex = assertThrows(
			IllegalStateException.class
			, () -> router.get("/users", NOOP));

		assertTrue(ex.getMessage().contains("重複"), ex.getMessage());

	}

	@Test
	@DisplayName("ワイルドカードは末尾以外に置けない")
	void wildcardMustBeLast () {

		Router router = new Router();

		assertThrows(IllegalArgumentException.class, () -> router.get("/a/*/b", NOOP));

	}

	@Test
	@DisplayName("F-R-12 ルート一覧が絶対パスで取れる")
	void routeListing () {

		Router router = new Router();
		router.get("/users", NOOP);
		router.post("/users", NOOP);
		router.get("/users/{id}", NOOP);
		router.get("/files/*", NOOP);

		Router admin = router.path("/admin");
		admin.get("/dashboard", NOOP);

		List<String> lines = router.routes().stream().map(RouteInfo::toString).toList();

		assertEquals(
			List.of(
				"GET     /admin/dashboard"
				, "GET     /files/*"
				, "GET     /users"
				, "POST    /users"
				, "GET     /users/{id}"
			)
			, lines);

	}

	@Test
	@DisplayName("path() のネストでパスが連結される")
	void nestedPath () {

		Router router = new Router();
		Router api = router.path("/api");
		Router v1 = api.path("/v1");
		Route route = v1.get("/ping", NOOP);

		assertSame(route, router.match("GET", "/api/v1/ping").route());

	}

	@Test
	@DisplayName("Executor 形式で登録できる")
	void executorRoute () {

		Router router = new Router();
		Route route = router.post("/save", () -> context -> { }, () -> context -> { });

		assertEquals(2, route.executorSuppliers().size());
		assertNull(route.handler());
		assertSame(route, router.match("POST", "/save").route());

	}

	@Test
	@DisplayName("Executor を1つも渡さない登録は例外")
	void emptyExecutorsThrows () {

		Router router = new Router();

		assertThrows(IllegalArgumentException.class, () -> router.post("/x"));

	}

}
