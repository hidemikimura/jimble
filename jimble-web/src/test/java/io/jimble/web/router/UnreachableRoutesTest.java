package io.jimble.web.router;

import io.jimble.util.conf.Conf;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一生呼ばれないルートを見つける（{@link UnreachableRoutes}／要件 F-R-13 / D-10）
 *
 * <p>
 * <b>登録できてしまうのに、どんなリクエストでも他が先に当たる</b>ルートがある。
 * 重複（同じメソッド・同じパス）はその場で落ちるが、こちらは落ちない——
 * <b>書いた本人は登録したつもりで、動かない理由が分からない</b>。
 * </p>
 */
class UnreachableRoutesTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	/**
	 * 探す
	 *
	 * @param router	ルーター
	 * @return	見つけたもの
	 */
	private static List<UnreachableRoutes.Finding> detect (Router router) {

		return UnreachableRoutes.detect(router.tree());

	}

	/**
	 * 何もしないハンドラ
	 *
	 * @return	ハンドラ
	 */
	private static Handler nothing () {

		return context -> { };

	}

	// region 見つけるもの

	@Test
	@DisplayName("変数の名前だけ違う2本目は、一生呼ばれない")
	void duplicateVariable () {

		Router router = new Router();

		router.get("/users/{id}", nothing());
		router.get("/users/{userId}", nothing());

		List<UnreachableRoutes.Finding> findings = detect(router);

		assertEquals(1, findings.size(), findings.toString());
		assertEquals("/users/{userId}", findings.get(0).route().path());
		assertEquals("/users/{id}", findings.get(0).winner().path());

	}

	@Test
	@DisplayName("変数の下の枝が丸ごと同じでも、一生呼ばれない")
	void duplicateVariableDeep () {

		Router router = new Router();

		router.get("/users/{id}/posts", nothing());
		router.get("/users/{userId}/posts", nothing());

		assertEquals("/users/{userId}/posts", detect(router).get(0).route().path());

	}

	@Test
	@DisplayName("2つを見比べるだけでは見つからない隠れ方も見つける")
	void hiddenByTwoTogether () {

		Router router = new Router();

		router.get("/a/{x}", nothing());
		router.get("/a/{x}/*", nothing());
		router.get("/a/*", nothing());

		/*
		 * <b>/a/* を隠しているのは1本ではない。</b>
		 * 残り1つなら {x} が、2つ以上なら {x}/* が先に当たる。
		 * どちらか一方とだけ見比べる作りでは<b>「隠れていない」と答えてしまう</b>
		 */
		List<UnreachableRoutes.Finding> findings = detect(router);

		assertEquals(1, findings.size(), findings.toString());
		assertEquals("/a/*", findings.get(0).route().path());

	}

	@Test
	@DisplayName("メソッドが同じときだけ数える")
	void perMethod () {

		Router router = new Router();

		router.get("/users/{id}", nothing());
		router.post("/users/{userId}", nothing());

		// POST は GET に隠されない（終端でメソッドを見ている）
		assertTrue(detect(router).isEmpty(), detect(router).toString());

	}

	// endregion

	// region 見つけないもの

	@Test
	@DisplayName("固定が変数より先に当たるのは正しい動きなので、数えない")
	void staticWinsIsFine () {

		Router router = new Router();

		router.get("/users/me", nothing());
		router.get("/users/{id}", nothing());

		/*
		 * {id} は "me" 以外のすべてで呼ばれる。
		 * <b>「ほぼ届かない」ではなく「1本も来ない」だけを見る</b>
		 */
		assertTrue(detect(router).isEmpty(), detect(router).toString());

	}

	@Test
	@DisplayName("ワイルドカードは固定を隠さない")
	void wildcardHidesNothing () {

		Router router = new Router();

		router.get("/files/*", nothing());
		router.get("/files/readme", nothing());

		/*
		 * 優先順位が<b>固定 &gt; 変数 &gt; ワイルドカード</b>なので、
		 * ワイルドカードを先に書いても後ろが隠れることはない。
		 * 要件の文面（「ワイルドカードに隠れるもの」）だけを実装すると、
		 * <b>ここで誤検知する</b>
		 */
		assertTrue(detect(router).isEmpty(), detect(router).toString());

	}

	@Test
	@DisplayName("変数に隠れて見えても、深いパスならワイルドカードに来る")
	void wildcardReachableWhenDeeper () {

		Router router = new Router();

		router.get("/a/{x}", nothing());
		router.get("/a/*", nothing());

		/*
		 * <b>残り1つのときは {x} が勝つが、2つ以上なら * しか当たらない。</b>
		 * 試しのパスを1つの長さでしか流さないと、
		 * <b>ここを「一生呼ばれない」と誤って言う</b>
		 */
		assertTrue(detect(router).isEmpty(), detect(router).toString());

	}

	@Test
	@DisplayName("枝の形が違えば、変数が2つあってもどちらも呼ばれる")
	void differentBranches () {

		Router router = new Router();

		router.get("/users/{id}/posts", nothing());
		router.get("/users/{userId}/comments", nothing());

		// 深いところで失敗すると戻ってもう一方を試す（バックトラック）
		assertTrue(detect(router).isEmpty(), detect(router).toString());

	}

	@Test
	@DisplayName("試しのパスが固定セグメントとぶつからない")
	void probeAvoidsStatics () {

		Router router = new Router();

		/*
		 * 試しに使う語がそのまま固定セグメントとして登録されていると、
		 * <b>そちらが先に当たって「到達不能」と誤って言う</b>
		 */
		router.get("/x/jimble_probe", nothing());
		router.get("/x/{name}", nothing());

		assertTrue(detect(router).isEmpty(), detect(router).toString());

	}

	@Test
	@DisplayName("1本しか無ければ何も言わない")
	void single () {

		Router router = new Router();

		router.get("/hello", nothing());

		assertTrue(detect(router).isEmpty());

	}

	// endregion

	// region 起動時の扱い

	@Test
	@DisplayName("既定は警告だけで、起動は止めない")
	void warnByDefault () {

		Conf.replace(ConfigFactory.empty());

		Router router = new Router();

		router.get("/users/{id}", nothing());
		router.get("/users/{userId}", nothing());

		// 落ちない
		router.seal();

	}

	@Test
	@DisplayName("server.strict_routes = true なら起動時に落ちる")
	void strict () {

		Conf.replace(ConfigFactory.parseString("server { strict_routes = true }"));

		Router router = new Router();

		router.get("/users/{id}", nothing());
		router.get("/users/{userId}", nothing());

		IllegalStateException thrown = assertThrows(IllegalStateException.class, router::seal);

		assertTrue(thrown.getMessage().contains("/users/{userId}"), thrown.getMessage());

		// 何に負けているかも言う（言わないと、どこを直せばよいか分からない）
		assertTrue(thrown.getMessage().contains("/users/{id}"), thrown.getMessage());

	}

	@Test
	@DisplayName("問題が無ければ strict でも落ちない")
	void strictButClean () {

		Conf.replace(ConfigFactory.parseString("server { strict_routes = true }"));

		Router router = new Router();

		router.get("/users/me", nothing());
		router.get("/users/{id}", nothing());
		router.post("/users/{id}", nothing());

		router.seal();

	}

	// endregion

}
