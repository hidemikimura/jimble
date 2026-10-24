package io.jimble.util.json;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守る約束：読みの文法（D-307。jimble-json に替えてから）
 *
 * <p>
 * <b>意味がはっきりしているゆるさは受け付け、データを黙って壊すものは断る。</b>
 * 作り直す前は、区切りの抜けや壊れたエスケープを黙って通し、値やキーが消えたり文字列の終わりがずれたりしていた
 * （その記録は {@link DsonCurrentQuirksTest} にあったもの）。
 * </p>
 */
class DsonReadGrammarContractTest {

	private static Object value (String json) {

		Data data = Dson.decodes("{\"v\":" + json + "}", Data.class);
		return data.get("v");

	}

	@Nested
	@DisplayName("受け付けるゆるさ")
	class Accepts {

		@Test
		@DisplayName("文字列の中の生の改行・タブ")
		void rawControlCharacters () {

			assertEquals("x\ny\tz", value("\"x\ny\tz\""));

		}

		@Test
		@DisplayName("01 / +1 / .5 / 1. は数")
		void looseNumbers () {

			assertEquals(1, value("01"));
			assertEquals(1, value("+1"));
			assertEquals(0.5d, value(".5"));
			assertEquals(1.0d, value("1."));

		}

		@Test
		@DisplayName("-0 は Integer の 0")
		void negativeZero () {

			assertEquals(0, value("-0"));

		}

		@Test
		@DisplayName("クォートの無い NaN / Infinity は Double。TRUE / 0x10 は文字列")
		void unquotedWords () {

			assertTrue(Double.isNaN((Double) value("NaN")));
			assertEquals(Double.POSITIVE_INFINITY, value("Infinity"));
			assertEquals("TRUE", value("TRUE"));
			assertEquals("0x10", value("0x10"));

		}

		@Test
		@DisplayName("シングルクォートの文字列の \\' は ' に戻る")
		void escapedSingleQuote () {

			assertEquals("it's", Data.fromJsonString("{'s':'it\\'s'}").get("s"));

		}

		@Test
		@DisplayName("シングルクォートの文字列の中の } や ] は区切りではない")
		void bracesInSingleQuotedString () {

			assertEquals(Map.of("a", "}"), Data.fromJsonString("{'a':'}'}"));
			assertEquals(Map.of("a", "]"), Dson.decodes("{\"a\":']'}", Data.class));

		}

		@Test
		@DisplayName("コメント（// と /* */）")
		void comments () {

			assertEquals(Map.of("a", 1, "b", 2), Data.fromJsonString("{ // 一つめ\n\"a\":1, /* 二つめ */ \"b\":2 }"));

		}

	}

	@Nested
	@DisplayName("断るもの（JsonParseException。行と文字の位置つき）")
	class Rejects {

		@ParameterizedTest
		@ValueSource(strings = {
			"{\"a\":1 \"b\":2}",
			"{\"a\":}",
			"{\"a\"::1}",
			"{\"a\" 1}",
			"{:1}",
			"{a b:1}",
			"{\"s\":abc def}",
			"{\"f\":function(){return 1;}}"
		})
		@DisplayName("オブジェクトの区切りの抜け・値やキーの無いもの・空白を含むクォートの無い語・function")
		void brokenObjects (String json) {

			assertThrows(JsonParseException.class, () -> Dson.decodes(json, Data.class));
			assertThrows(JsonParseException.class, () -> Data.fromJsonString(json));

		}

		@ParameterizedTest
		@ValueSource(strings = {"[1,,2]", "[1 2]", "[1:2]"})
		@DisplayName("配列の区切りの抜け・空の要素・: の混ざり")
		void brokenArrays (String json) {

			assertThrows(JsonParseException.class, () -> Dson.decodes(json, List.class));

		}

		@ParameterizedTest
		@ValueSource(strings = {"\"\\qabcdz\"", "\"\\uZZZZab\"", "\"\\u12\"", "\"\\あx\""})
		@DisplayName("壊れたエスケープ（作り直す前は、後ろの文字を飲み込んだり U+FFFF にしたりしていた）")
		void brokenEscapes (String json) {

			assertThrows(JsonParseException.class, () -> value(json));

		}

		@Test
		@DisplayName("例外に行と文字の位置が載る")
		void position () {

			JsonParseException ex = assertThrows(JsonParseException.class, () -> Dson.decodes("{\n\"a\":1\n\"b\":2}", Data.class));

			assertTrue(ex.getMessage().contains("3 行 1 文字目"), ex.getMessage());

		}

	}

	@Nested
	@DisplayName("読み元で食い違わない")
	class Sources {

		@Test
		@DisplayName("Reader / InputStream からでも、閉じていないもの・後ろの余分・JSON でないものは断る")
		void readerRejectsBroken () {

			assertThrows(JsonParseException.class, () -> Dson.decodes(new StringReader("{\"a\":1"), Data.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes(new StringReader("{\"a\":1} x"), Data.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes(new StringReader("garbage"), Data.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes("garbage", Data.class));

			assertThrows(JsonParseException.class,
				() -> new Dson().decodes(new ByteArrayInputStream("garbage".getBytes(StandardCharsets.UTF_8)), "UTF-8", Data.class));

			// インスタンスの decode は、例外ではなく isError で知らせる
			Dson dson = new Dson();
			assertNull(dson.decode(new StringReader("garbage"), Data.class));
			assertTrue(dson.isError());

		}

		@Test
		@DisplayName("一番外がスカラー（123 / \"abc\" / true）なら、文字列でも Reader でも断る")
		void scalarRoot () {

			assertThrows(JsonParseException.class, () -> Dson.decodes("123", Data.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes("\"abc\"", Object.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes("true", Object.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes(new StringReader("123"), Object.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes(new StringReader("\"abc\""), Object.class));

		}

		@Test
		@DisplayName("null の文字は、Dson.decodes でも Data.fromJsonString でも null")
		void nullText () {

			assertNull(Dson.decodes((String) null, Data.class));
			assertNull(Data.fromJsonString(null));

		}

	}

	@Nested
	@DisplayName("読み先の型との食い違い（jimble-converter に替えてから）")
	class TargetMismatch {

		@Test
		@DisplayName("配列の JSON を Data / Map で読むと断る（作り直す前は添字がキーの {\"0\":..} になっていた）")
		void arrayAsMap () {

			assertThrows(JsonParseException.class, () -> Dson.decodes("[1,2]", Data.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes("[1,2]", Map.class));
			assertThrows(JsonParseException.class, () -> Data.fromJsonString("[{\"a\":1}]"));

		}

		@Test
		@DisplayName("型を指定せずに配列の JSON を読むと List（中のオブジェクトは Data。作り直す前は添字がキーの Data）")
		void untypedArray () {

			Object read = Dson.decodes("[{\"a\":1},{\"a\":2}]");

			assertEquals(List.of(Map.of("a", 1), Map.of("a", 2)), read);
			assertTrue(((List<?>) read).getFirst() instanceof Data);
			assertTrue(Dson.decodes("{\"a\":1}") instanceof Data);

		}

		@Test
		@DisplayName("空の配列 [] を Dson.decodes(Data) で読むと空の Data（オブジェクトが空のときに [] を返す API がある）")
		void emptyArrayAsData () {

			assertEquals(new Data(), Dson.decodes("[]", Data.class));

		}

	}

	@Nested
	@DisplayName("上限")
	class Limits {

		@Test
		@DisplayName("1000 文字を超える数値は断る")
		void longNumber () {

			assertEquals(new java.math.BigDecimal("9".repeat(1000)), value("9".repeat(1000)));
			assertThrows(JsonParseException.class, () -> value("9".repeat(1001)));

		}

	}

}
