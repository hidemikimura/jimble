package io.jimble.web.auth;

import io.jimble.web.context.WebContext;
import io.jimble.web.router.AttributeKey;
import io.jimble.web.router.RouteMatch;
import io.jimble.web.router.Router;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 見張りが読むルート属性（要件 F-W-28）
 *
 * <h2>ここで見ているもの</h2>
 * <p>
 * <b>「何も書かなければ閉じている」こと。</b>
 * {@link Auth#guard} が守れるかどうかは、突き詰めると<b>属性の既定値</b>で決まる。
 * ここが {@code true}（公開）に倒れていると、
 * <b>ルートを足した人が何も書かなければ誰でも入れる。</b>
 * </p>
 *
 * <p>
 * 実際に 401 / 403 を返すところは、サーバーを立てる結合テスト
 * （{@code examples/approval-auth}）が見ている。
 * ここは<b>サーバーを立てずに決まること</b>だけを固定する。
 * </p>
 */
class AuthGuardTest {

	// region 既定は閉じている

	@Test
	@DisplayName("F-W-28 何も書かないルートは、ログインが要る")
	void closedByDefault () {

		Router router = new Router();
		router.get("/requests", context -> { });
		router.seal();

		assertFalse(attribute(router, "/requests", Auth.PUBLIC)
			, "何も書いていないルートが公開になっている（足した人が書き忘れたら開く）");

	}

	@Test
	@DisplayName("F-W-28 何も書かないルートは、役割を問わない")
	void noRoleByDefault () {

		Router router = new Router();
		router.get("/requests", context -> { });
		router.seal();

		assertEquals("", attribute(router, "/requests", Auth.ROLE));

	}

	@Test
	@DisplayName("F-W-28 何も書かないルートは、セッションを使う")
	void sessionByDefault () {

		Router router = new Router();
		router.get("/requests", context -> { });
		router.seal();

		/*
		 * <b>ここが true に倒れていると、ログインの入口がセッションを持てない。</b>
		 * 「ログインが要らない＝セッションも要らない」と繋げた作りで実際に踏んだ
		 * （302 は返るのに、次のリクエストで 401 になる）。
		 */
		assertFalse(attribute(router, "/requests", Auth.NO_SESSION));

	}

	// endregion

	// region ブロック単位で開ける（要件 F-R-26）

	@Test
	@DisplayName("F-W-28 ブロックごと公開にでき、その中の1本だけ閉じられる")
	void openBlockWithOneClosedRoute () {

		Router router = new Router();

		Router pub = router.path("/public");
		pub.attribute(Auth.PUBLIC, true);
		pub.attribute(Auth.NO_SESSION, true);
		pub.get("/guide", context -> { });
		pub.get("/faq", context -> { });

		// ブロックの中だが、ここだけ要ログイン
		pub.get("/me", context -> { }).attribute(Auth.PUBLIC, false);

		router.get("/requests", context -> { });

		router.seal();

		assertTrue(attribute(router, "/public/guide", Auth.PUBLIC));
		assertTrue(attribute(router, "/public/faq", Auth.PUBLIC));
		assertTrue(attribute(router, "/public/guide", Auth.NO_SESSION));

		assertFalse(attribute(router, "/public/me", Auth.PUBLIC), "ルートの上書きが効いていない");

		// ブロックの外には漏れない
		assertFalse(attribute(router, "/requests", Auth.PUBLIC));
		assertFalse(attribute(router, "/requests", Auth.NO_SESSION));

	}

	@Test
	@DisplayName("F-W-28 ログインの入口は「公開だがセッションは要る」と書ける")
	void loginRouteIsPublicButKeepsSession () {

		Router router = new Router();

		router.get("/login", context -> { }).attribute(Auth.PUBLIC, true);
		router.post("/login", context -> { }).attribute(Auth.PUBLIC, true);

		router.seal();

		for (String method : new String[] { "GET", "POST" }) {

			RouteMatch match = router.match(method, "/login");

			assertTrue(match.route().attribute(Auth.PUBLIC));

			/*
			 * <b>ここが分かれていることが要点である。</b>
			 * 公開だが、セッションは要る——でなければ CSRF トークンも
			 * ログイン後のセッションも持てない。
			 */
			assertFalse(match.route().attribute(Auth.NO_SESSION)
				, "ログインの入口でセッションが切られている（誰もログインできなくなる）");

		}

	}

	@Test
	@DisplayName("F-W-28 役割はブロックにも書ける")
	void roleOnBlock () {

		Router router = new Router();

		Router admin = router.path("/admin");
		admin.attribute(Auth.ROLE, "approver");
		admin.get("/approvals", context -> { });
		admin.get("/staff", context -> { });

		router.seal();

		assertEquals("approver", attribute(router, "/admin/approvals", Auth.ROLE));
		assertEquals("approver", attribute(router, "/admin/staff", Auth.ROLE));

	}

	// endregion

	// region ルートが決まっていないとき

	@Test
	@DisplayName("F-W-28 ルートが決まっていなければ何もしない（NPE を投げない）")
	void doesNothingWithoutRoute () {

		/*
		 * <b>ディスパッチャは未マッチなら before を回す前に 404 を投げる</b>ので、
		 * ふつうの流れでここへは来ない。
		 * それでも見るのは、<b>route() が null を返しうる</b>ためである——
		 * 外すと 401 ではなく <b>NullPointerException（500）</b>になる。
		 */
		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/nothing"), new Fakes.FakeResponseSink())) {

			assertDoesNotThrow(() -> Auth.guard(context)
				, "ルートが決まっていないのに見張ろうとしている");

		}

	}

	// endregion

	// region Principal

	@Test
	@DisplayName("F-W-28 未ログインは null ではなく ANONYMOUS")
	void anonymousIsNotNull () {

		assertFalse(Principal.ANONYMOUS.isAuthenticated());
		assertEquals("", Principal.ANONYMOUS.name());
		assertEquals("", Principal.ANONYMOUS.role());

	}

	@Test
	@DisplayName("F-W-28 id が 0 以下ならログインしていない")
	void idDecidesAuthentication () {

		assertTrue(Principal.of(1, "きむら").isAuthenticated());
		assertFalse(Principal.of(0, "きむら").isAuthenticated());
		assertFalse(Principal.of(-1, "きむら").isAuthenticated());

	}

	@Test
	@DisplayName("F-W-28 null を渡しても空文字になる（NPE を配らない）")
	void nullsBecomeEmpty () {

		Principal principal = Principal.of(1, null, null);

		assertEquals("", principal.name());
		assertEquals("", principal.role());
		assertFalse(principal.hasRole("approver"));

	}

	@Test
	@DisplayName("F-W-28 役割は完全一致（前方一致で通らない）")
	void roleIsExactMatch () {

		Principal principal = Principal.of(1, "きむら", "approver");

		assertTrue(principal.hasRole("approver"));
		assertFalse(principal.hasRole("approve"), "前方一致で通っている");
		assertFalse(principal.hasRole("approver2"));
		assertFalse(principal.hasRole(""));

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>実際に 401 / 403 が返ることは見ていない。</b>サーバーと DB が要るので、
	 *   examples/approval-auth の結合テストが見ている
	 * - <b>「未マッチのとき 401 を返さない」ことは、実は見ようがない。</b>
	 *   ディスパッチャが <b>before を回す前に 404 を投げる</b>ので、
	 *   そもそも見張りが呼ばれない（サンプルには長らく
	 *   「ここで 401 を返すと存在を漏らす」と書いてあったが、この道は通らない）。
	 *   上のテストが見ているのは <b>route() が null でも落ちない</b>ことである
	 * - <b>checkPassword の「時間が揃う」ことは測っていない。</b>
	 *   時間で落とすテストは共用のランナーでぶれる（ベンチマークを時間で落とさないのと同じ理由）。
	 *   見ているのは<b>相手がいなくても false を返すこと</b>までである
	 */

	// endregion

	// region 道具

	/**
	 * マッチさせて属性を読む
	 *
	 * @param router	ルーター
	 * @param path		パス
	 * @param key		キー
	 * @param <T>		値の型
	 * @return	値
	 */
	private static <T> T attribute (Router router, String path, AttributeKey<T> key) {

		RouteMatch match = router.match("GET", path);

		assertTrue(match.matched(), "GET %s に当たらない".formatted(path));

		return match.route().attribute(key);

	}

	// endregion

}
