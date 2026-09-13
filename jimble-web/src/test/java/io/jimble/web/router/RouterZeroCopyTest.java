package io.jimble.web.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * パスを割らずに当てる（要件 NF-P-04 / D-168）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>マッチのやり方を、文字列を作らない形に入れ替えた。</b>
 * 区切りの位置だけ覚えて {@link String#regionMatches} で直に比べ、
 * <b>固定セグメントは自前の表（開番地法）で引く</b>。
 * </p>
 *
 * <p>
 * <b>速くするための入れ替えなので、答えが変わっていないことが全部である。</b>
 * ここが見ているのは<b>ふつうのテストでは踏まない境目</b>——
 * 表がぶつかるところ、パーセント記号、深いパス、空のパス。
 * </p>
 */
class RouterZeroCopyTest {

	/** 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	@Test
	@DisplayName("D-168 兄弟がたくさんいても、全部それぞれに当たる")
	void manySiblingsAllMatch () {

		/*
		 * <b>自前の表は、ぶつかったら隣を見る（開番地法）。</b>
		 * 1本でも「隣を見る」を書き損なうと、
		 * <b>ぶつかった組だけ当たらなくなる</b>——
		 * 兄弟が少ないテストでは<b>まず踏まない</b>。
		 */
		Router router = new Router();

		List<Route> routes = new ArrayList<>();

		for (int i = 0; i < 500; i++) {
			routes.add(router.get("/x/seg" + i, NOOP));
		}

		router.seal();

		for (int i = 0; i < 500; i++) {
			assertSame(routes.get(i), router.match("GET", "/x/seg" + i).route(), "seg" + i);
		}

		assertFalse(router.match("GET", "/x/seg500").matched());

	}

	@Test
	@DisplayName("D-168 表の外の名前は、隣を見ても見つからない")
	void anAbsentSegmentStops () {

		Router router = new Router();

		for (int i = 0; i < 100; i++) {
			router.get("/x/seg" + i, NOOP);
		}

		router.seal();

		/*
		 * <b>空きに当たったら打ち切る。</b>打ち切らないと、
		 * 無いものを探すたびに<b>表を1周なめる</b>ことになる。
		 */
		assertFalse(router.match("GET", "/x/nope").matched());
		assertFalse(router.match("GET", "/x/").matched());

	}

	@Test
	@DisplayName("D-168 パーセント記号を含むパスも、いままでどおりデコードして当てる")
	void percentEncodedSegmentsStillMatch () {

		/*
		 * <b>ルートのパターンはデコードしない字面で持っている</b>のに対し、
		 * リクエストのパスは<b>セグメントごとにデコードしてから</b>当てる決まりである
		 * （要件 F-R-23）。<b>生のまま比べると、ここが外れる。</b>
		 */
		Router router = new Router();

		Route spaced = router.get("/a b/c", NOOP);
		Route japanese = router.get("/犬", NOOP);

		router.seal();

		assertSame(spaced, router.match("GET", "/a%20b/c").route(), "%20 をデコードしていません");
		assertSame(japanese, router.match("GET", "/%E7%8A%AC").route(), "日本語をデコードしていません");

		// デコードしたものと同じ字面で来ても当たる
		assertSame(spaced, router.match("GET", "/a b/c").route());

	}

	@Test
	@DisplayName("D-168 パスパラメータの %2F は区切りに化けない（要件 F-R-23）")
	void encodedSlashStaysInsideOneSegment () {

		Router router = new Router();

		router.get("/search/{q}", NOOP);
		router.seal();

		RouteMatch match = router.match("GET", "/search/a%2Fb");

		assertTrue(match.matched(), "セグメントが割れています");
		assertEquals("a/b", match.variables().get("q"));

	}

	@Test
	@DisplayName("D-168 パスパラメータの値はデコードして渡す")
	void variablesAreDecoded () {

		Router router = new Router();

		router.get("/posts/{id}", NOOP);
		router.seal();

		assertEquals("12345", router.match("GET", "/posts/12345").variables().get("id"));
		assertEquals("犬 と 猫", router.match("GET", "/posts/%E7%8A%AC%20%E3%81%A8%20%E7%8C%AB")
			.variables().get("id"));

	}

	@Test
	@DisplayName("D-168 変数が2つ以上でも、全部そろって渡る")
	void severalVariablesAreAllBound () {

		/*
		 * <b>1つだけのときは表を作らない</b>ようにしてある（そのほうが軽い）。
		 * <b>2つ目で表へ移す</b>ところを踏む。
		 */
		Router router = new Router();

		router.get("/{a}/{b}/{c}", NOOP);
		router.seal();

		RouteMatch match = router.match("GET", "/1/2/3");

		assertEquals("1", match.variables().get("a"));
		assertEquals("2", match.variables().get("b"));
		assertEquals("3", match.variables().get("c"));

		assertEquals(3, match.variables().values().size());

	}

	@Test
	@DisplayName("D-168 変数が無いルートでも、変数の入れ物は空で返る")
	void withoutVariablesTheHolderIsEmpty () {

		Router router = new Router();

		router.get("/a/b", NOOP);
		router.seal();

		RouteMatch match = router.match("GET", "/a/b");

		assertTrue(match.matched());
		assertTrue(match.variables().values().isEmpty());
		assertEquals(null, match.variables().get("id"));

	}

	@Test
	@DisplayName("D-168 深いパスでも当たる（区切りを覚える上限を超えても）")
	void veryDeepPathsStillMatch () {

		/*
		 * <b>{@code %} の印は 64 個までしか覚えていない。</b>
		 * それを超えた位置は<b>常に「デコードが要る」</b>ものとして扱う——
		 * <b>遅いだけで答えは同じ</b>であることを踏む。
		 */
		StringBuilder pattern = new StringBuilder();
		StringBuilder path = new StringBuilder();

		for (int i = 0; i < 80; i++) {
			pattern.append("/s").append(i);
			path.append("/s").append(i);
		}

		pattern.append("/{last}");
		path.append("/%E7%8A%AC");

		Router router = new Router();

		Route deep = router.get(pattern.toString(), NOOP);

		router.seal();

		RouteMatch match = router.match("GET", path.toString());

		assertSame(deep, match.route());
		assertEquals("犬", match.variables().get("last"), "上限より先をデコードしていません");

	}

	@Test
	@DisplayName("D-168 空のパスと \"/\" と \"//\"")
	void emptyPaths () {

		Router router = new Router();

		Route root = router.get("/", NOOP);

		router.seal();

		assertSame(root, router.match("GET", "/").route());
		assertSame(root, router.match("GET", "//").route());
		assertSame(root, router.match("GET", "").route());

	}

	@Test
	@DisplayName("D-168 ワイルドカードの残りは \"/\" でつないで渡る")
	void theWildcardTailIsJoined () {

		Router router = new Router();

		router.get("/assets/*", NOOP);
		router.seal();

		assertEquals("css/app.css"
			, router.match("GET", "/assets/css/app.css").variables().wildcard());

		// 余分なスラッシュは落ちる（要件 F-R-24）
		assertEquals("css/app.css"
			, router.match("GET", "/assets//css//app.css/").variables().wildcard());

		// デコードもする
		assertEquals("犬/a b"
			, router.match("GET", "/assets/%E7%8A%AC/a%20b").variables().wildcard());

	}

	@Test
	@DisplayName("D-168 固定セグメントは、前方一致では当たらない")
	void aPrefixIsNotAMatch () {

		/*
		 * <b>長さを見ないと、{@code /ab} が {@code /a} に当たる。</b>
		 * {@code regionMatches} は<b>渡した長さのぶんしか見ない</b>ので、
		 * <b>長さの突き合わせを落とすと前方一致になる</b>。
		 */
		Router router = new Router();

		Route a = router.get("/a", NOOP);

		router.seal();

		assertSame(a, router.match("GET", "/a").route());
		assertFalse(router.match("GET", "/ab").matched(), "前方一致で当たっています");
		assertFalse(router.match("GET", "/").matched());

	}

	// region ここで固定していないこと

	/*
	 * - <b>速さと割り当て</b>は {@code RouterBench} が見ている。
	 *   ここは<b>答えが変わっていないこと</b>だけを見る
	 * - <b>表がどう並ぶか</b>（どの位置に入るか）は固定していない。
	 *   ハッシュの散らし方を変えても<b>答えは変わらない</b>ので、
	 *   ここで並びを書くと<b>直せなくなる</b>
	 * - <b>{@code seal()} を呼ばずに使ったとき</b>も見ていない。
	 *   表が無ければ元の道（{@code Map} を引く）を通るようにしてあり、
	 *   そちらは {@code UnreachableRoutesTest} が踏んでいる
	 */

	// endregion

}
