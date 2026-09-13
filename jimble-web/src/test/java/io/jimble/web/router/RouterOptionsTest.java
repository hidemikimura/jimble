package io.jimble.web.router;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ルーティングの選べるところ（要件 F-R-24 / D-166）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>スラッシュはもともと無視している。</b>
 * {@link PathSegments} が空のセグメントを落とすので、
 * {@code /a/b} {@code /a/b/} {@code /a//b} {@code //a/b} は<b>前から同じルート</b>である。
 * </p>
 *
 * <p>
 * <b>足したのは「どれか1つに寄せるか」と「綴りを見ないか」である。</b>
 * 無視するだけだと<b>同じ内容が複数の URL で 200 を返す</b>——
 * キャッシュも検索エンジンも前段の ACL も、<b>別の URL として数える</b>。
 * </p>
 */
class RouterOptionsTest {

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private static void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	// region 既定

	@Test
	@DisplayName("D-166 既定では、どちらも切れている")
	void bothAreOffByDefault () {

		conf("");

		assertFalse(RouterConf.ignoreCase());
		assertFalse(RouterConf.redirectToCanonical());

	}

	@Test
	@DisplayName("スラッシュは、設定に関係なく無視する（要件 F-R-24）")
	void slashesAreAlwaysIgnored () {

		conf("");

		Router router = new Router();
		Route users = router.get("/users", context -> {});

		router.seal();

		assertSame(users, router.match("GET", "/users").route());
		assertSame(users, router.match("GET", "/users/").route(), "末尾のスラッシュ");
		assertSame(users, router.match("GET", "//users").route(), "先頭の連続");
		assertSame(users, router.match("GET", "/users//").route(), "末尾の連続");

	}

	// endregion

	// region 大文字小文字

	@Test
	@DisplayName("D-166 既定では、綴りが違えば当たらない")
	void caseMattersByDefault () {

		conf("");

		Router router = new Router();
		router.get("/users", context -> {});
		router.seal();

		assertFalse(router.match("GET", "/USERS").matched());

	}

	@Test
	@DisplayName("D-166 ignore_case なら綴りを見ない")
	void ignoreCaseMatchesAnySpelling () {

		conf("router { ignore_case = true }");

		Router router = new Router();
		Route users = router.get("/Users", context -> {});
		router.seal();

		assertSame(users, router.match("GET", "/Users").route());
		assertSame(users, router.match("GET", "/users").route());
		assertSame(users, router.match("GET", "/USERS").route());
		assertSame(users, router.match("GET", "/uSeRs").route());

	}

	@Test
	@DisplayName("D-166 見ないのは固定の部分だけ。パスパラメータの値は変えない")
	void pathVariablesKeepTheirCase () {

		conf("router { ignore_case = true }");

		Router router = new Router();
		router.get("/Users/{id}", context -> {});
		router.seal();

		RouteMatch match = router.match("GET", "/USERS/AbC123");

		assertTrue(match.matched());

		/*
		 * <b>ここを小文字にすると、大文字を含む ID が壊れる。</b>
		 * Hashids も UUID の大文字表記も、<b>1文字変われば別のものである</b>。
		 */
		assertEquals("AbC123", match.variables().get("id"), "値まで小文字にしています");

	}

	@Test
	@DisplayName("そのままの綴りが先に当たる")
	void theExactSpellingWinsFirst () {

		conf("router { ignore_case = true }");

		Router router = new Router();
		Route a = router.get("/a/Deep", context -> {});
		router.seal();

		assertSame(a, router.match("GET", "/a/Deep").route());
		assertSame(a, router.match("GET", "/a/deep").route());

	}

	@Test
	@DisplayName("D-166 綴りだけ違う2本を書いてあったら、起動時に落ちる")
	void twoRoutesDifferingOnlyInCaseAreRefused () {

		/*
		 * <b>どちらに当たるか読み手が決められない。</b>
		 * しかも<b>片方にだけ認証を書いていたら、緩いほうに当たりうる</b>——
		 * 動きはするので、テストでも見つからない。
		 */
		conf("router { ignore_case = true }");

		Router router = new Router();
		router.get("/Admin", context -> {});
		router.get("/admin", context -> {});

		IllegalStateException ex = assertThrows(IllegalStateException.class, router::seal);

		assertTrue(ex.getMessage().contains("ignore_case"), ex.getMessage());
		assertTrue(ex.getMessage().contains("Admin"), ex.getMessage());

	}

	@Test
	@DisplayName("切ってあれば、綴り違いの2本は今までどおり別のルート")
	void withoutIgnoreCaseTheyAreTwoRoutes () {

		conf("");

		Router router = new Router();
		Route upper = router.get("/Admin", context -> {});
		Route lower = router.get("/admin", context -> {});

		router.seal();

		assertSame(upper, router.match("GET", "/Admin").route());
		assertSame(lower, router.match("GET", "/admin").route());

	}

	// endregion

	// region 正規の形

	@Test
	@DisplayName("D-166 スラッシュを正規の形にする")
	void canonicalSlashes () {

		assertEquals("/a/b", PathSegments.canonicalRawPath("/a/b"));
		assertEquals("/a/b", PathSegments.canonicalRawPath("/a/b/"));
		assertEquals("/a/b", PathSegments.canonicalRawPath("//a//b//"));
		assertEquals("/", PathSegments.canonicalRawPath("/"));
		assertEquals("/", PathSegments.canonicalRawPath("//"));
		assertEquals("/", PathSegments.canonicalRawPath(""));

	}

	@Test
	@DisplayName("D-166 正規の形にしてもデコードしない")
	void canonicalDoesNotDecode () {

		/*
		 * <b>ここで作った文字列は {@code Location} ヘッダに載る。</b>
		 * デコードすると <b>{@code %20} が空白になって壊れる</b>し、
		 * <b>{@code %2F} がセグメントの区切りに化ける</b>。
		 */
		assertEquals("/a%20b/c", PathSegments.canonicalRawPath("/a%20b/c/"));
		assertEquals("/search/a%2Fb", PathSegments.canonicalRawPath("//search//a%2Fb"));

	}

	@Test
	@DisplayName("D-166 綴りは、ルートに書いてあるほうへ寄せる")
	void canonicalUsesTheRegisteredSpelling () {

		assertEquals("/Users/AbC", PathSegments.canonicalRawPath("/USERS/AbC", "/Users/{id}"));

		// パスパラメータの値は1文字も変えない
		assertEquals("/Users/%E7%8A%AC"
			, PathSegments.canonicalRawPath("/users/%E7%8A%AC", "/Users/{id}"));

		// ワイルドカードから先はそのまま
		assertEquals("/Assets/A/B/c"
			, PathSegments.canonicalRawPath("/assets/A/B/c", "/Assets/*"));

		// パターンが無ければスラッシュだけ
		assertEquals("/USERS/AbC", PathSegments.canonicalRawPath("/USERS/AbC/", null));

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>実際に 301 が返るところ</b>は {@code CanonicalRedirectTest} が見ている。
	 *   ここはルーターの中だけを見る
	 * - <b>{@code %2F} を含むパスパラメータ</b>の扱いは変えていない
	 *   （要件 F-R-23。セグメントごとにデコードするので区切りには化けない）
	 * - <b>大文字小文字を見ないときの速さ</b>は測っていない。
	 *   索引は {@code seal()} で1度だけ作るので、
	 *   <b>リクエストごとの走査は増えない</b>——そのつもりだが、測ってはいない
	 */

	// endregion

}
