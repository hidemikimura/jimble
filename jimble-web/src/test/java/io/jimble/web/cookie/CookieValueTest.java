package io.jimble.web.cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cookie の値の符号化（{@link CookieValue}）
 *
 * <p>
 * <b>日本語を Cookie に入れると、例外も警告も無しに {@code ?} になって届いていた。</b>
 * ヘッダは ASCII なので当たり前なのだが、<b>誰も落ちないので気づけない</b>。
 * </p>
 */
class CookieValueTest {

	@Test
	@DisplayName("ASCII はそのまま通る（いまブラウザにある Cookie の見た目を変えない）")
	void asciiIsUntouched () {

		/*
		 * <b>ここが崩れると、版を上げただけで全員の Cookie が読めなくなる。</b>
		 * 署名は Base64（URL 安全）＋ 区切りの | なので、その形を必ず通すこと
		 */
		assertSame("abc123", CookieValue.encode("abc123"));
		assertSame("aGVsbG8-_x|value", CookieValue.encode("aGVsbG8-_x|value"));
		assertSame("a+b=c/d", CookieValue.encode("a+b=c/d"));

	}

	@Test
	@DisplayName("+ を空白にしない（URLEncoder を使わない理由）")
	void plusIsNotSpace () {

		/*
		 * URLEncoder / URLDecoder を使うと、+ が空白になって戻る。
		 * <b>署名にも Base64 にも + は出うる</b>ので、それでは使えない
		 */
		assertEquals("a+b", CookieValue.decode("a+b"));

	}

	@Test
	@DisplayName("日本語は往復して元に戻る")
	void japaneseRoundTrips () {

		String value = "ログインIDかパスワードが違います";

		String encoded = CookieValue.encode(value);

		assertTrue(encoded.chars().allMatch(c -> c < 0x80), "ASCII になっていない: " + encoded);
		assertEquals(value, CookieValue.decode(encoded));

	}

	@Test
	@DisplayName("Cookie に入れられない文字を符号化する")
	void unsafeCharactersAreEncoded () {

		// 空白・; ・, ・" ・\ はヘッダの区切りとぶつかる
		assertEquals("a%20b", CookieValue.encode("a b"));
		assertEquals("a%3Bb", CookieValue.encode("a;b"));
		assertEquals("a%2Cb", CookieValue.encode("a,b"));
		assertEquals("a%22b", CookieValue.encode("a\"b"));
		assertEquals("a%5Cb", CookieValue.encode("a\\b"));

		assertEquals("a b", CookieValue.decode("a%20b"));
		assertEquals("a;b", CookieValue.decode("a%3Bb"));

	}

	@Test
	@DisplayName("% 自身も符号化する（しないと復号のときに区別が付かない）")
	void percentIsEncoded () {

		assertEquals("100%25", CookieValue.encode("100%"));
		assertEquals("100%", CookieValue.decode("100%25"));

		// 往復すること
		assertEquals("%41", CookieValue.decode(CookieValue.encode("%41")));

	}

	@Test
	@DisplayName("壊れた並びで落ちない（他人が置いた Cookie でリクエストを落とさない）")
	void brokenInputDoesNotThrow () {

		assertEquals("%", CookieValue.decode("%"));
		assertEquals("%z", CookieValue.decode("%z"));
		assertEquals("%zz", CookieValue.decode("%zz"));
		assertEquals("a%", CookieValue.decode("a%"));
		assertEquals("a%1", CookieValue.decode("a%1"));

	}

	@Test
	@DisplayName("符号化されていない日本語が来ても、そのまま読める")
	void rawJapaneseSurvivesDecode () {

		/*
		 * 直す前の版が書いた Cookie や、他所が置いたものが来ることがある。
		 * <b>復号で壊さないこと</b>
		 */
		assertEquals("あいう", CookieValue.decode("あいう"));

	}

	@Test
	@DisplayName("null と空は触らない")
	void nullAndEmpty () {

		assertEquals(null, CookieValue.encode(null));
		assertEquals(null, CookieValue.decode(null));
		assertEquals("", CookieValue.encode(""));
		assertEquals("", CookieValue.decode(""));

	}

	// region ここで固定していないこと

	/*
	 * - <b>「% が入っている古い Cookie を一度だけ読み違える」ことは直していない。</b>
	 *   %41 が A になる。直しようがない（区別できない）ので、CHANGELOG に書いた
	 * - Cookie の名前は符号化していない。名前に非 ASCII を使う道はそもそも無い
	 */

	// endregion

}
