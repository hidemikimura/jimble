package io.jimble.util.hash;

import com.dynatrace.hash4j.hashing.Hashing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * XXH3（{@link Hash#xxh3(String)} / {@link Hash#xxh3Hex128(byte[])}。2.8.0）
 *
 * <p>
 * <b>ここで出す値は DB と Redis に入る</b>（ロックのキー・SQL 結果キャッシュのタグとキー・流量制限の行）。
 * 1 bit でも変わると、上げた日に既存の行と突き合わなくなるので、公式の参照の値と、決まった入力の値を固定する。
 * </p>
 */
class Xxh3Test {

	@Test
	@DisplayName("XXH3-128 は公式の参照の値（空の入力・seed 0）と一致する")
	void referenceVector128 () {

		assertEquals("99aa06d3014798d86001c324468d497f", Hash.xxh3Hex128(new byte[0]));
		assertEquals("99aa06d3014798d86001c324468d497f", Hash.xxh3Hex128(null));

	}

	@Test
	@DisplayName("XXH3-64 は公式の参照の値（空の入力・seed 0）と一致する（Hash.xxh3 は空を 0 にするので、使っている実装で見る）")
	void referenceVector64 () {

		assertEquals(0x2d06800538d394c2L, Hashing.xxh3_64().hashBytesToLong(new byte[0]));

	}

	@Test
	@DisplayName("文字列は UTF-8 にしてから XXH3-64 を取る。null と空文字は 0（sipHash と同じ）")
	void utf8AndEmpty () {

		for (String value : new String[] { "a", "user:1234", "sql_cache:posts", "記事一覧", "x".repeat(300) }) {
			assertEquals(Hashing.xxh3_64().hashBytesToLong(value.getBytes(StandardCharsets.UTF_8)), Hash.xxh3(value), value);
		}

		assertEquals(0, Hash.xxh3(null));
		assertEquals(0, Hash.xxh3(""));
		assertNotEquals(Hash.xxh3("a"), Hash.xxh3("b"));

	}

	@Test
	@DisplayName("値を固定する（変わると、上げた日に DB と Redis の行と突き合わなくなる）")
	void pinned () {

		assertEquals(Pinned.XXH3_USER_1234, Hash.xxh3("user:1234"));
		assertEquals(Pinned.XXH3_128_JP, Hash.xxh3Hex128("記事一覧".getBytes(StandardCharsets.UTF_8)));

	}

	/**
	 * 固定する値（2.8.0 で hash4j 0.31.0 の XXH3 から求めた。上の参照の値と一致する実装）
	 */
	private static final class Pinned {

		static final long XXH3_USER_1234 = 0xb10d9bc42a04d581L;

		static final String XXH3_128_JP = "9ae70deddb493aa697ee86eb17f110fe";

	}

}
