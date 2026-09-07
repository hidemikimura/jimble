package io.jimble.util.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * XML の値の取り出し（要件 F-Y-09 / D-87）
 *
 * <p>
 * 数値の getter が<b>戻り値の型だけ {@code byte} のまま</b>だった。
 * 中では正しく変換していたのに、それを {@code Byte} にキャストするところで
 * 落ちて、{@code catch} が <b>0 を返していた</b>。
 * </p>
 *
 * <p>
 * 「0 が入っている」と区別がつかないので、
 * <b>気づかないまま使われる</b>形になっていた。
 * </p>
 */
class XmlDataTest {

	/**
	 * 値を1つ持つ要素を作る
	 *
	 * @param text	値
	 * @return	要素
	 */
	private static XmlData of (String text) {

		return new XmlData("value", text);

	}

	@Test
	@DisplayName("D-87 整数が取れる")
	void integers () {

		assertEquals((byte) 12, of("12").getByte());
		assertEquals((short) 1234, of("1234").getShort());
		assertEquals(123456, of("123456").getInt());
		assertEquals(12345678901L, of("12345678901").getLong());

	}

	@Test
	@DisplayName("D-87 実数が取れる")
	void decimals () {

		assertEquals(1.5f, of("1.5").getFloat(), 0.0001f);
		assertEquals(1.25d, of("1.25").getDouble(), 0.0001d);

	}

	@Test
	@DisplayName("数字でなければ 0")
	void notANumber () {

		assertEquals(0, of("あ").getInt());
		assertEquals(0L, of("").getLong());

	}

	@Test
	@DisplayName("文字列はそのまま")
	void text () {

		assertEquals("あ", of("あ").getString());

	}

}
