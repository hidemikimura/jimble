package io.jimble.web.auth.passkey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CBOR を読む（D-261）。値は RFC 8949 の付録 A の例
 */
class CborTest {

	private static Object decode (String hex) {

		return Cbor.decode(HexFormat.of().parseHex(hex));

	}

	@Test
	@DisplayName("整数・バイト列・文字列・配列・マップ・真偽・null を読む（RFC 8949 付録 A）")
	void decodes () {

		assertEquals(0L, decode("00"));
		assertEquals(23L, decode("17"));
		assertEquals(24L, decode("1818"));
		assertEquals(1000L, decode("1903e8"));
		assertEquals(1000000000000L, decode("1b000000e8d4a51000"));
		assertEquals(new BigInteger("18446744073709551615"), decode("1bffffffffffffffff"));
		assertEquals(-1L, decode("20"));
		assertEquals(-1000L, decode("3903e7"));
		assertArrayEquals(new byte[] { 1, 2, 3, 4 }, (byte[]) decode("4401020304"));
		assertEquals("IETF", decode("6449455446"));
		assertEquals("水", decode("63e6b0b4"));
		assertEquals(List.of(1L, List.of(2L, 3L), List.of(4L, 5L)), decode("8301820203820405"));
		assertEquals(Map.of(1L, 2L, 3L, 4L), decode("a201020304"));
		assertEquals(true, decode("f5"));
		assertEquals(false, decode("f4"));
		assertNull(decode("f6"));
		assertEquals(1363896240L, decode("c11a514b67b0"));            // タグは中身だけ

	}

	@Test
	@DisplayName("後ろに余りがある・途中で終わる・長さが本文より長いものは断る")
	void rejectsBrokenLength () {

		assertThrows(IllegalArgumentException.class, () -> decode("0000"));
		assertThrows(IllegalArgumentException.class, () -> decode("44010203"));
		assertThrows(IllegalArgumentException.class, () -> decode("19"));
		// 長さ 2^32-1 のバイト列（数バイトで大きく確保させない）
		assertThrows(IllegalArgumentException.class, () -> decode("5affffffff00"));
		// 要素数 2^32-1 の配列
		assertThrows(IllegalArgumentException.class, () -> decode("9affffffff00"));

	}

	@Test
	@DisplayName("長さを決めない形・浮動小数・重なったキー・深すぎる入れ子は断る")
	void rejectsUnsupported () {

		assertThrows(IllegalArgumentException.class, () -> decode("9f01ff"));
		assertThrows(IllegalArgumentException.class, () -> decode("f93c00"));
		assertThrows(IllegalArgumentException.class, () -> decode("a201020103"));
		assertThrows(IllegalArgumentException.class, () -> decode("81".repeat(Cbor.MAX_DEPTH + 2) + "00"));

	}

	@Test
	@DisplayName("テスト用の書き手と読み手が往復する")
	void roundTrip () {

		Map<Object, Object> map = new LinkedHashMap<>();
		map.put(1L, 2L);
		map.put(-1L, new byte[300]);
		map.put("fmt", "none");
		map.put(3L, -257L);

		@SuppressWarnings("unchecked")
		Map<Object, Object> back = (Map<Object, Object>) Cbor.decode(FakeAuthenticator.encode(map));

		assertEquals(2L, back.get(1L));
		assertEquals(300, ((byte[]) back.get(-1L)).length);
		assertEquals("none", back.get("fmt"));
		assertEquals(-257L, back.get(3L));

	}

}
