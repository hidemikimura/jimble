package io.jimble.util.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SipHash-2-4（{@link SipHash}）
 *
 * <p>
 * <b>ここで出す値は DB に入っている。</b>{@code sql_cache} のタグ、{@code DBLock} のキー、
 * レート制限の行として保存されているので、<b>1 bit でも変わると既存のデータと突き合わなくなる</b>。
 * guava の {@code Hashing.sipHash24()} から自前に置き換えたとき（要件 D-121）、
 * <b>長さ 0〜300 の乱数 90,300 件で guava と全部一致する</b>ことを確かめてある。
 * ここはその値を固定するためのテストである。
 * </p>
 */
class SipHashTest {

	@Test
	@DisplayName("仕様書のテストベクタと合う")
	void specVector () {

		// SipHash-2-4 の仕様書が載せている、鍵 00 01 02 … 0f・入力なしのときの値。
		// byte で書くと 31 0e 0e dd 47 db 6f 72（little endian）
		assertEquals(0x726fdb47dd0e0e31L, SipHash.hash(new byte[0]));

	}

	@Test
	@DisplayName("guava が返していた値と同じ")
	void sameAsGuava () {

		assertEquals(3144613055062689994L, hash("a"));
		assertEquals(6754548778392356773L, hash("abc"));
		assertEquals(-7018363015828086103L, hash("user:1234"));
		assertEquals(764425549633940792L, hash("sql_cache:posts"));
		assertEquals(5088949743686896128L, hash("記事一覧"));

		// 8 byte の区切りをまたぐ長さ（64 文字 = ちょうど 8 ブロック）
		assertEquals(6424133887193564075L, hash("x".repeat(64)));

	}

	@Test
	@DisplayName("Hash.sipHash から呼んでも同じ")
	void throughHash () {

		assertEquals(hash("user:1234"), Hash.sipHash("user:1234"));

		// 空文字は 0（ハッシュを取らない）
		assertEquals(0, Hash.sipHash(""));
		assertEquals(0, Hash.sipHash(null));

	}

	/**
	 * UTF-8 にしてからハッシュを取る
	 *
	 * @param value 文字列
	 * @return ハッシュ
	 */
	private static long hash (String value) {

		return SipHash.hash(value.getBytes(StandardCharsets.UTF_8));

	}

}
