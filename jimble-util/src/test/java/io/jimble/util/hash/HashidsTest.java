package io.jimble.util.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 連番を短い文字列に置き換える（要件 F-U-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>ドキュメントに名前が載っているのに、テストが1度も触っていなかった。</b>
 * URL に出す ID をここで作るので、<b>戻せなくなったら過去のリンクが全部死ぬ</b>。
 * </p>
 *
 * <p>
 * <b>これは暗号ではない。</b>連番だと分かりにくくするだけのもので、
 * <b>塩を知らなくても総当たりで戻せる</b>。
 * 見せてはいけないものを隠す用途に使ってはいけない。
 * </p>
 */
class HashidsTest {

	@Test
	@DisplayName("入れた数がそのまま戻る")
	void numbersComeBack () {

		Hashids hashids = new Hashids("しお");

		for (long n : new long[] { 0, 1, 42, 12345, 9007199254740992L }) {
			assertArrayEquals(new long[] { n }, hashids.decode(hashids.encode(n)), "戻りません: " + n);
		}

		assertArrayEquals(new long[] { 1, 2, 3 }, hashids.decode(hashids.encode(1, 2, 3)));

	}

	@Test
	@DisplayName("連番でも、並びが読み取れる形にはならない")
	void consecutiveNumbersDoNotLookConsecutive () {

		Hashids hashids = new Hashids("しお");

		String one = hashids.encode(1);
		String two = hashids.encode(2);

		assertNotEquals(one, two);

		/*
		 * <b>これが目的である。</b>1 と 2 が「1文字違い」になってしまうと、
		 * <b>隣の ID を打つだけで他人のものが引ける</b>。
		 */
		assertFalse(one.length() == two.length() && distance(one, two) == 1
			, "隣の番号が1文字違いです: " + one + " / " + two);

	}

	@Test
	@DisplayName("塩が違えば、別の文字列になる")
	void theSaltChangesTheResult () {

		Hashids a = new Hashids("あ");
		Hashids b = new Hashids("い");

		assertNotEquals(a.encode(1), b.encode(1));

		/*
		 * <b>別の塩では戻らない。</b>——ただし<b>例外は出ない</b>。
		 * 空の配列か、<b>まったく別の数</b>が返る。
		 * <b>塩を変えたら、それまでに配った URL は全部使えなくなる。</b>
		 */
		assertFalse(Arrays.equals(new long[] { 1 }, b.decode(a.encode(1)))
			, "別の塩で戻ってしまいました");

	}

	@Test
	@DisplayName("最短の長さを決められる")
	void theMinimumLengthIsHonoured () {

		Hashids hashids = new Hashids("しお", 12);

		String hash = hashids.encode(1);

		assertTrue(hash.length() >= 12, "短すぎます: " + hash);
		assertArrayEquals(new long[] { 1 }, hashids.decode(hash), "詰め物のせいで戻りません");

	}

	@Test
	@DisplayName("負の数と空は、黙って空文字になる")
	void negativeAndEmptyBecomeAnEmptyString () {

		Hashids hashids = new Hashids("しお");

		/*
		 * <b>例外にはならない。</b>負の ID を渡した側は
		 * <b>空文字を URL に埋め込んだことに気づけない</b>ので、
		 * <b>呼ぶ前に自分で見ること</b>。
		 */
		assertEquals("", hashids.encode(-1));
		assertEquals("", hashids.encode());

	}

	@Test
	@DisplayName("上限を超えた数は落ちる")
	void tooLargeThrows () {

		Hashids hashids = new Hashids("しお");

		assertThrows(IllegalArgumentException.class, () -> hashids.encode(9007199254740993L));

	}

	@Test
	@DisplayName("読めない文字列は、空の配列で戻る")
	void garbageDecodesToNothing () {

		Hashids hashids = new Hashids("しお");

		// 使っていない文字（大文字）が混ざっている
		assertEquals(0, hashids.decode("ZZZZ").length);
		assertEquals(0, hashids.decode("").length);

	}

	@Test
	@DisplayName("16進の文字列も行き来できる")
	void hexRoundTrips () {

		Hashids hashids = new Hashids("しお");

		String hex = "507f1f77bcf86cd799439011";

		assertEquals(hex, hashids.decodeHex(hashids.encodeHex(hex)));

		// 16進でないものは空文字
		assertEquals("", hashids.encodeHex("xyz"));

	}

	@Test
	@DisplayName("塩なしの共有インスタンスがある")
	void thereIsASharedInstanceWithoutASalt () {

		/*
		 * <b>{@code Hashids.HASHIDS} は塩が空である。</b>
		 * 誰でも同じものを作れるので、<b>隠す力は無い</b>——
		 * 短くしたいだけのときに使うこと。
		 */
		assertArrayEquals(new long[] { 1 }, Hashids.HASHIDS.decode(Hashids.HASHIDS.encode(1)));
		assertEquals(Hashids.HASHIDS.encode(1), new Hashids().encode(1));

	}

	@Test
	@DisplayName("int に収まらない値は落ちる")
	void checkedCastRefusesOverflow () {

		assertEquals(42, Hashids.checkedCast(42L));
		assertThrows(IllegalArgumentException.class, () -> Hashids.checkedCast(Long.MAX_VALUE));

	}

	// region ここで固定していないこと

	/*
	 * - <b>出来上がる文字列そのもの</b>は固定していない。
	 *   ここで文字列を書くと<b>実装を1文字も直せなくなる</b>——
	 *   見ているのは「戻ること」と「塩で変わること」だけである。
	 *   <b>実装を変えたら過去の URL が死ぬ</b>ことに変わりはないので、
	 *   直すときは移行の道を先に決めること
	 * - <b>文字の種類を差し替えたとき</b>（4引数のコンストラクタ）は見ていない
	 * - <b>衝突しないこと</b>も見ていない。可逆な置き換えなので<b>原理的に衝突しない</b>
	 */

	// endregion

	/**
	 * 何文字違うか（長さが同じ前提）
	 *
	 * @param a	片方
	 * @param b	もう片方
	 * @return	違う文字の数
	 */
	private static int distance (String a, String b) {

		int count = 0;

		for (int i = 0; i < a.length(); i++) {
			if (a.charAt(i) != b.charAt(i)) {
				count++;
			}
		}

		return count;

	}

}
