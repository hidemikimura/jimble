package io.jimble.web.auth;

import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ApiToken.authenticate の3つの形が、ラムダの引数の数で曖昧にならないこと（D-305）
 *
 * <p>
 * <b>コンパイルが通ること自体が確かめ</b>である。曖昧なら、このファイルがコンパイルできない。
 * </p>
 */
class ApiTokenOverloadTest {

	/* ID だけを受け取る（Function の形になる） */
	static Principal byId (long id) {
		return Principal.of(id, "x", "member");
	}

	/* 種別と ID を受け取る（BiFunction の形になる） */
	static Principal byRealm (String realm, long id) {
		return Principal.of(id, realm, "member");
	}

	/* リクエスト・種別・ID を受け取る（PrincipalLookup の形になる） */
	static Principal byContext (WebContext context, String realm, long id) {
		return Principal.of(id, realm, "member");
	}

	@Test
	@DisplayName("D-305 引数が1つ・2つ・3つのラムダとメソッド参照が、それぞれ ID だけ・種別つき・リクエストつきの形に決まる")
	void lambdasResolveByArity () {

		Handler one = ApiToken.authenticate(id -> Principal.of(id, "x", "member"));
		Handler two = ApiToken.authenticate((realm, id) -> Principal.of(id, realm, "member"));
		Handler three = ApiToken.authenticate((context, realm, id) -> Principal.of(id, realm, "member"));

		Handler oneRef = ApiToken.authenticate(ApiTokenOverloadTest::byId);
		Handler twoRef = ApiToken.authenticate(ApiTokenOverloadTest::byRealm);
		Handler threeRef = ApiToken.authenticate(ApiTokenOverloadTest::byContext);

		for (Handler handler : new Handler[] { one, two, three, oneRef, twoRef, threeRef }) {
			assertNotNull(handler);
		}

	}

}
