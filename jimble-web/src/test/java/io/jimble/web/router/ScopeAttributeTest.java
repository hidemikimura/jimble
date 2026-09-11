package io.jimble.web.router;

import io.jimble.web.ratelimit.RateLimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ブロック単位のルート属性（要件 F-R-26）
 *
 * <h2>ここで見ているもの</h2>
 * <p>
 * <b>「ルート1本ずつに書かなくてよい」ことと、「書いたら勝てる」ことの両方。</b>
 * 片方だけでは使えない——全部に付くだけなら例外を作れないし、
 * ルートが勝つだけなら1本ずつ書くのと変わらない。
 * </p>
 *
 * <p>
 * 強い順に <b>ルート &gt; 内側のブロック &gt; 外側のブロック &gt; キーの既定値</b>。
 * </p>
 *
 * <p>
 * <b>パスのノードには付かない</b>（要件 D-69）。ここも見ている——
 * 同じパスでも別のブロックなら付かない、が守れていないと、
 * <b>コードを読んでも付いているかどうか分からなくなる。</b>
 * </p>
 */
class ScopeAttributeTest {

	/** 公開か（既定は「公開ではない」＝黙って足したら閉じている） */
	private static final AttributeKey<Boolean> PUBLIC = new AttributeKey<>("public", false);

	/** 要る役割 */
	private static final AttributeKey<String> ROLE = new AttributeKey<>("role", "");

	// region 効く範囲

	@Test
	@DisplayName("F-R-26 ブロックに書くと、その中のルート全部に付く")
	void appliesToRoutesInTheBlock () {

		Router router = new Router();

		router.attribute(PUBLIC, true);

		router.get("/a", context -> { });
		router.get("/b", context -> { });

		router.seal();

		assertTrue(attribute(router, "GET", "/a", PUBLIC));
		assertTrue(attribute(router, "GET", "/b", PUBLIC));

	}

	@Test
	@DisplayName("F-R-26 書いた順より前に登録したルートにも付く")
	void appliesRegardlessOfOrder () {

		Router router = new Router();

		/*
		 * <b>先にルート、あとから属性。</b>
		 * before と同じで、配るのは確定のとき（seal）である。
		 * ここが順番に依存すると、<b>ブロックの最後に書いた宣言だけが効かない</b>
		 * という読みにくい形になる。
		 */
		router.get("/a", context -> { });

		router.attribute(PUBLIC, true);

		router.seal();

		assertTrue(attribute(router, "GET", "/a", PUBLIC));

	}

	@Test
	@DisplayName("F-R-26 path() の中にも付く")
	void appliesToNestedPath () {

		Router router = new Router();

		router.attribute(PUBLIC, true);

		Router docs = router.path("/docs");
		docs.get("/guide", context -> { });

		router.seal();

		assertTrue(attribute(router, "GET", "/docs/guide", PUBLIC));

	}

	@Test
	@DisplayName("F-R-26 外のブロックのものは付かない（パスのノードには付かない。D-69）")
	void doesNotLeakToAnotherBlock () {

		Router router = new Router();

		Router open = router.path("/docs");
		open.attribute(PUBLIC, true);
		open.get("/guide", context -> { });

		/*
		 * <b>同じ /docs だが、別のブロックで登録している。</b>
		 * パスに付く作りだと、ここまで公開になってしまう。
		 */
		Router secret = router.path("/docs");
		secret.get("/internal", context -> { });

		router.seal();

		assertTrue(attribute(router, "GET", "/docs/guide", PUBLIC));
		assertFalse(attribute(router, "GET", "/docs/internal", PUBLIC)
			, "別のブロックのルートにまで付いている（パスに付いてしまっている）");

	}

	// endregion

	// region 勝ち負け

	@Test
	@DisplayName("F-R-26 ルートが書いていればルートが勝つ")
	void routeWins () {

		Router router = new Router();

		router.attribute(PUBLIC, true);

		router.get("/guide", context -> { });
		router.get("/me", context -> { }).attribute(PUBLIC, false);

		router.seal();

		assertTrue(attribute(router, "GET", "/guide", PUBLIC));
		assertFalse(attribute(router, "GET", "/me", PUBLIC), "ルートの上書きが効いていない");

	}

	@Test
	@DisplayName("F-R-26 入れ子は内側が勝つ")
	void innerWins () {

		Router router = new Router();

		router.attribute(PUBLIC, true);
		router.attribute(ROLE, "guest");

		Router admin = router.path("/admin");
		admin.attribute(PUBLIC, false);
		admin.get("/users", context -> { });

		router.get("/guide", context -> { });

		router.seal();

		assertTrue(attribute(router, "GET", "/guide", PUBLIC));
		assertFalse(attribute(router, "GET", "/admin/users", PUBLIC), "内側が勝っていない");

		// 内側が書いていないものは、外側から降りてくる
		assertEquals("guest", attribute(router, "GET", "/admin/users", ROLE));

	}

	@Test
	@DisplayName("F-R-26 どこにも書いていなければキーの既定値")
	void fallsBackToKeyDefault () {

		Router router = new Router();

		router.get("/a", context -> { });

		router.seal();

		assertFalse(attribute(router, "GET", "/a", PUBLIC));
		assertEquals("", attribute(router, "GET", "/a", ROLE));

	}

	// endregion

	// region 流量制限が今までどおり動く

	@Test
	@DisplayName("F-R-22 rateLimit() はブロック単位の属性の上に乗っている")
	void rateLimitStillWorks () {

		Router router = new Router();

		Router api = router.path("/api");
		api.rateLimit(RateLimit.perIp(5, Duration.ofMinutes(1)));
		api.get("/items", context -> { });

		router.get("/guide", context -> { });

		router.seal();

		/*
		 * <b>rateLimit は特別扱いをやめて、属性の1つになった。</b>
		 * 仕組みを差し替えたので、今までどおり効くことをここで見ておく。
		 */
		assertNotNull(attribute(router, "GET", "/api/items", RateLimit.KEY)
			, "ブロックに書いた流量制限が付いていない");

		assertNull(attribute(router, "GET", "/guide", RateLimit.KEY)
			, "書いていないブロックにまで付いている");

	}

	@Test
	@DisplayName("F-R-22 ルートに書いた流量制限がブロックより勝つ")
	void routeRateLimitWins () {

		RateLimit strict = RateLimit.perIp(1, Duration.ofMinutes(1));

		Router router = new Router();

		Router api = router.path("/api");
		api.rateLimit(RateLimit.perIp(100, Duration.ofMinutes(1)));
		api.post("/login", context -> { }).attribute(RateLimit.KEY, strict);

		router.seal();

		assertEquals(strict, attribute(router, "POST", "/api/login", RateLimit.KEY));

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>確定したあとに attribute() を呼ぶと例外</b>になることは見ていない。
	 *   Scope#checkOpen の共通の道なので、before / after のテストが見ている
	 * - <b>merge（install）で取り込んだ側に付くこと</b>は見ていない。
	 *   スコープの親子関係は before / after と同じものを使っており、
	 *   ControllerInstallTest がその形を見ている
	 * - <b>値が null の属性</b>は「書いていない」と同じ扱いになる。
	 *   キーの既定値が null のとき（RateLimit.KEY）に効いてくるが、
	 *   「ブロックで消す」用途が実在しないので、そう決めたままにしてある
	 */

	// endregion

	// region 道具

	/**
	 * マッチさせて属性を読む
	 *
	 * @param router	ルーター
	 * @param method	メソッド
	 * @param path		パス
	 * @param key		キー
	 * @param <T>		値の型
	 * @return	値
	 */
	private static <T> T attribute (Router router, String method, String path, AttributeKey<T> key) {

		RouteMatch match = router.match(method, path);

		assertTrue(match.matched(), "%s %s に当たらない".formatted(method, path));

		return match.route().attribute(key);

	}

	// endregion

}
