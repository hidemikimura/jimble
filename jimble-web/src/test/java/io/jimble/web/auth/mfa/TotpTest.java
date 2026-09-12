package io.jimble.web.auth.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * TOTP（要件 F-W-32）
 *
 * <h2>RFC の試験ベクタで確かめる</h2>
 * <p>
 * <b>自分の実装を自分で確かめても、間違いは見つからない。</b>
 * RFC 4226 と RFC 6238 は<b>期待される値そのもの</b>を載せているので、それと突き合わせる。
 * ここが合っていれば、<b>世の中の認証アプリと同じ数字が出る</b>と言える。
 * </p>
 *
 * <p>
 * あわせて Python の独立した実装でも同じ値が出ることを確かめてある
 * （RFC の値と一致した）。
 * </p>
 */
class TotpTest {

	/** RFC 4226 / 6238 の秘密鍵（ASCII の "12345678901234567890"） */
	private static final byte[] SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	// region RFC の試験ベクタ

	@Test
	@DisplayName("F-W-32 RFC 4226 の HOTP と一致する")
	void matchesRfc4226 () {

		String[] expected = {
			"755224", "287082", "359152", "969429", "338314"
			, "254676", "287922", "162583", "399871", "520489"
		};

		for (int counter = 0; counter < expected.length; counter++) {
			assertEquals(expected[counter], Totp.generate(SEED, counter, 6)
				, "counter=%d で RFC と違う".formatted(counter));
		}

	}

	@Test
	@DisplayName("F-W-32 RFC 6238 の TOTP と一致する")
	void matchesRfc6238 () {

		long[] times = { 59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L };

		String[] expected = {
			"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"
		};

		for (int i = 0; i < times.length; i++) {
			assertEquals(expected[i], Totp.at(SEED, times[i], 30, 8)
				, "t=%d で RFC と違う".formatted(times[i]));
		}

	}

	@Test
	@DisplayName("F-W-32 2038年より先（32ビットを超える時刻）でも合う")
	void worksBeyond2038 () {

		/*
		 * RFC のベクタに <b>20000000000（西暦 2603 年）</b>が入っているのは、
		 * <b>時刻を int で持つと壊れる</b>ことを見るためである。
		 */
		assertEquals("65353130", Totp.at(SEED, 20000000000L, 30, 8));

	}

	// endregion

	// region 窓

	@Test
	@DisplayName("F-W-32 いまのコードは通り、窓の番号が返る")
	void verifiesCurrent () {

		long now = 1111111111L;
		String code = Totp.at(SEED, now, 30, 6);

		assertEquals(now / 30, Totp.verify(SEED, code, now, 30, 6, 1));

	}

	@Test
	@DisplayName("F-W-32 前後1つの窓までは通る")
	void verifiesNeighbours () {

		long now = 1111111111L;

		assertNotEquals(Totp.NO_MATCH, Totp.verify(SEED, Totp.at(SEED, now - 30, 30, 6), now, 30, 6, 1));
		assertNotEquals(Totp.NO_MATCH, Totp.verify(SEED, Totp.at(SEED, now + 30, 30, 6), now, 30, 6, 1));

	}

	@Test
	@DisplayName("F-W-32 窓の外は通らない")
	void rejectsOutsideWindow () {

		long now = 1111111111L;

		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, Totp.at(SEED, now - 90, 30, 6), now, 30, 6, 1));
		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, Totp.at(SEED, now + 90, 30, 6), now, 30, 6, 1));

	}

	@Test
	@DisplayName("F-W-32 窓を 0 にすると、いまのぶんだけ通る")
	void windowZero () {

		long now = 1111111111L;

		assertNotEquals(Totp.NO_MATCH, Totp.verify(SEED, Totp.at(SEED, now, 30, 6), now, 30, 6, 0));
		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, Totp.at(SEED, now - 30, 30, 6), now, 30, 6, 0));

	}

	@Test
	@DisplayName("F-W-32 違うコード・桁数違い・空は通らない")
	void rejectsWrong () {

		long now = 1111111111L;

		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, "000000", now, 30, 6, 1));
		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, "12345", now, 30, 6, 1));
		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, "", now, 30, 6, 1));
		assertEquals(Totp.NO_MATCH, Totp.verify(SEED, null, now, 30, 6, 1));

	}

	@Test
	@DisplayName("F-W-32 空白やハイフンが入っていても読む")
	void ignoresSpacing () {

		long now = 1111111111L;
		String code = Totp.at(SEED, now, 30, 6);

		String spaced = code.substring(0, 3) + " " + code.substring(3);

		assertNotEquals(Totp.NO_MATCH, Totp.verify(SEED, spaced, now, 30, 6, 1)
			, "画面から貼り付けると空白が入ることがある");

	}

	@Test
	@DisplayName("F-W-32 別の秘密鍵では通らない")
	void rejectsOtherSecret () {

		long now = 1111111111L;

		assertEquals(Totp.NO_MATCH
			, Totp.verify(Totp.secret(), Totp.at(SEED, now, 30, 6), now, 30, 6, 1));

	}

	// endregion

	// region base32

	@Test
	@DisplayName("F-W-32 base32 が RFC 4648 と一致する")
	void base32MatchesRfc4648 () {

		// 認証アプリはこの形で受け取る。1文字でも違うと、出る数字が変わる
		assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.toBase32(SEED));

		assertEquals("MY", Totp.toBase32("f".getBytes(StandardCharsets.US_ASCII)));
		assertEquals("MZXQ", Totp.toBase32("fo".getBytes(StandardCharsets.US_ASCII)));
		assertEquals("MZXW6", Totp.toBase32("foo".getBytes(StandardCharsets.US_ASCII)));
		assertEquals("MZXW6YQ", Totp.toBase32("foob".getBytes(StandardCharsets.US_ASCII)));
		assertEquals("MZXW6YTB", Totp.toBase32("fooba".getBytes(StandardCharsets.US_ASCII)));
		assertEquals("MZXW6YTBOI", Totp.toBase32("foobar".getBytes(StandardCharsets.US_ASCII)));

	}

	@Test
	@DisplayName("F-W-32 base32 は往復する")
	void base32RoundTrips () {

		assertArrayEquals(SEED, Totp.fromBase32(Totp.toBase32(SEED)));

		for (int i = 0; i < 20; i++) {
			byte[] secret = Totp.secret();
			assertArrayEquals(secret, Totp.fromBase32(Totp.toBase32(secret)));
		}

	}

	@Test
	@DisplayName("F-W-32 base32 は詰め物・空白・小文字を読める")
	void base32IsForgiving () {

		assertArrayEquals("foobar".getBytes(StandardCharsets.US_ASCII), Totp.fromBase32("MZXW6YTBOI======"));
		assertArrayEquals("foobar".getBytes(StandardCharsets.US_ASCII), Totp.fromBase32("mzxw 6ytb oi"));

	}

	// endregion

	// region 秘密鍵

	@Test
	@DisplayName("F-W-32 秘密鍵は毎回違う")
	void secretsDiffer () {

		assertNotEquals(Totp.toBase32(Totp.secret()), Totp.toBase32(Totp.secret()));
		assertEquals(20, Totp.secret().length);

	}

	// endregion

}
