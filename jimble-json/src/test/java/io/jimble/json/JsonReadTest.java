package io.jimble.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON を読む（D-307）
 */
class JsonReadTest {

	private static Map<?, ?> object (String json) {

		return assertInstanceOf(Map.class, Json.read(json));

	}

	private static Object value (String json) {

		return object("{\"v\":" + json + "}").get("v");

	}

	private static JsonException fails (String json) {

		return assertThrows(JsonException.class, () -> Json.read(json));

	}

	private static final JsonReadOptions STRICT = JsonReadOptions.builder().strict(true).build();

	@Nested
	@DisplayName("形")
	class Shape {

		@Test
		@DisplayName("オブジェクトは LinkedHashMap で、キーは読んだ順")
		void objectKeepsOrder () {

			Map<?, ?> map = object("{\"b\":1,\"a\":2,\"c\":3}");

			assertInstanceOf(LinkedHashMap.class, map);
			assertEquals(List.of("b", "a", "c"), new ArrayList<>(map.keySet()));

		}

		@Test
		@DisplayName("配列は ArrayList で、入れ子もそのまま")
		void arrays () {

			Object value = Json.read("[1,[2,[3]],{\"a\":[]}]");

			assertEquals(List.of(1, List.of(2, List.of(3)), Map.of("a", List.of())), value);
			assertInstanceOf(ArrayList.class, value);

		}

		@Test
		@DisplayName("同じキーは後のほうの値になる")
		void duplicateKeyLastWins () {

			assertEquals(2, object("{\"a\":1,\"a\":2}").get("a"));

		}

		@Test
		@DisplayName("空・空白だけ・コメントだけ・null は null")
		void emptyIsNull () {

			assertNull(Json.read(""));
			assertNull(Json.read("  \n\t "));
			assertNull(Json.read("// なにもない\n/* ほんとうに */"));
			assertNull(Json.read("null"));
			assertNull(Json.read((String) null));

		}

		@Test
		@DisplayName("一番外がスカラーでも読める")
		void scalarRoot () {

			assertEquals(123, Json.read("123"));
			assertEquals("abc", Json.read("\"abc\""));
			assertEquals(Boolean.TRUE, Json.read("true"));

		}

		@Test
		@DisplayName("先頭の BOM は読み飛ばす")
		void bom () {

			assertEquals(Map.of("a", 1), Json.read("\uFEFF{\"a\":1}"));

		}

		@Test
		@DisplayName("作る Map と List は差し替えられる")
		void factories () {

			JsonReadOptions options = JsonReadOptions.builder()
				.mapFactory(TreeMap::new)
				.listFactory(java.util.LinkedList::new)
				.build();

			Map<?, ?> map = assertInstanceOf(TreeMap.class, Json.read("{\"b\":[1],\"a\":2}", options));
			assertInstanceOf(java.util.LinkedList.class, map.get("b"));

		}

	}

	@Nested
	@DisplayName("値の型（作り直す前と同じ。D-298）")
	class Types {

		@Test
		@DisplayName("int に収まる整数は Integer、long なら Long、それより大きければ BigDecimal")
		void integers () {

			assertEquals(Integer.valueOf(42), value("42"));
			assertEquals(Integer.valueOf(-2147483648), value("-2147483648"));
			assertEquals(Long.valueOf(2147483648L), value("2147483648"));
			assertEquals(Long.valueOf(Long.MAX_VALUE), value("9223372036854775807"));
			assertEquals(Long.valueOf(Long.MIN_VALUE), value("-9223372036854775808"));
			assertEquals(new BigDecimal("9223372036854775808"), value("9223372036854775808"));
			assertEquals(Integer.valueOf(1), value("00000000000000000000000001"));

		}

		@Test
		@DisplayName("19 桁の整数も long に収まれば Long、あふれれば BigDecimal（乱数で確かめる）")
		void nineteenDigitIntegers () {

			java.util.Random random = new java.util.Random(7);

			for (int i = 0; i < 20_000; i++) {
				long expected = random.nextLong();
				assertEquals(expected, ((Number) value(Long.toString(expected))).longValue(), Long.toString(expected));
			}

			assertEquals(new BigDecimal("9999999999999999999"), value("9999999999999999999"));
			assertEquals(new BigDecimal("-9999999999999999999"), value("-9999999999999999999"));
			assertEquals(Long.valueOf(-9223372036854775807L), value("-9223372036854775807"));

		}

		@Test
		@DisplayName("小数は Double、Double に収まらなければ BigDecimal")
		void decimals () {

			assertEquals(Double.valueOf(0.1), value("0.1"));
			assertEquals(Double.valueOf(1.0), value("1.0"));
			assertEquals(Double.valueOf(-0.0), value("-0.0"));
			assertEquals(Double.valueOf(1.5e300), value("1.5e300"));
			assertEquals(new BigDecimal("1.5e400"), value("1.5e400"));

		}

		@Test
		@DisplayName("小数の値は Double.parseDouble と1ビットも違わない（速い道と遅い道の両方）")
		void decimalsMatchParseDouble () {

			java.util.Random random = new java.util.Random(42);
			StringBuilder json = new StringBuilder("[");
			List<String> texts = new ArrayList<>();

			for (int i = 0; i < 20_000; i++) {
				int intDigits = random.nextInt(12);
				int fracDigits = 1 + random.nextInt(i % 3 == 0 ? 25 : 10);
				StringBuilder text = new StringBuilder(random.nextBoolean() ? "-" : "");
				for (int d = 0; d < intDigits; d++) {
					text.append((char) ('0' + random.nextInt(10)));
				}
				text.append('.');
				for (int d = 0; d < fracDigits; d++) {
					text.append((char) ('0' + random.nextInt(10)));
				}
				texts.add(text.toString());
				json.append(i == 0 ? "" : ",").append(text);
			}
			json.append("]");

			List<?> values = (List<?>) Json.read(json.toString());

			for (int i = 0; i < texts.size(); i++) {
				assertEquals(Double.doubleToRawLongBits(Double.parseDouble(texts.get(i))), Double.doubleToRawLongBits((Double) values.get(i)), texts.get(i));
			}

		}

		@Test
		@DisplayName("「.」の無い指数は値で決める（1e3 は Integer、1e-3 は Double）")
		void exponentWithoutDot () {

			assertEquals(Integer.valueOf(1000), value("1e3"));
			assertEquals(Integer.valueOf(1000), value("1E3"));
			assertEquals(Double.valueOf(0.001), value("1e-3"));
			assertEquals(Integer.valueOf(1), value("10e-1"));

		}

		@Test
		@DisplayName("-0 は Integer の 0")
		void negativeZero () {

			assertEquals(Integer.valueOf(0), value("-0"));

		}

		@Test
		@DisplayName("文字列の \"true\" / \"false\" は既定で Boolean。設定で文字列のまま")
		void stringBooleans () {

			assertEquals(Boolean.TRUE, value("\"true\""));
			assertEquals(Boolean.FALSE, value("'false'"));
			assertEquals("TRUE", value("\"TRUE\""));

			JsonReadOptions keep = JsonReadOptions.builder().stringBooleans(false).build();
			assertEquals("true", ((Map<?, ?>) Json.read("{\"v\":\"true\"}", keep)).get("v"));

		}

		@Test
		@DisplayName("キーはいつも文字列（true / null / 数字でも）")
		void keysAreStrings () {

			Map<?, ?> map = object("{\"true\":1, true:2, null:3, 12:4}");

			assertEquals(List.of("true", "null", "12"), new ArrayList<>(map.keySet()));
			assertEquals(2, map.get("true"));

		}

		@Test
		@DisplayName("文字列の値は、読んだあとに設定の処理を通せる")
		void stringValueHook () {

			JsonReadOptions options = JsonReadOptions.builder()
				.stringValueHook(s -> s.startsWith("n::") ? Integer.parseInt(s.substring(3)) : s)
				.build();

			Map<?, ?> map = (Map<?, ?>) Json.read("{\"a\":\"n::5\",\"b\":\"x\",\"n::7\":1}", options);

			assertEquals(5, map.get("a"));
			assertEquals("x", map.get("b"));
			assertTrue(map.containsKey("n::7"), "キーには効かない");

		}

	}

	@Nested
	@DisplayName("ゆるい書き方を受け付ける")
	class Lenient {

		@Test
		@DisplayName("クォートの無いキーと値")
		void unquoted () {

			assertEquals(Map.of("name", "jimble", "n", 1, "ok", true), Json.read("{name: jimble, n: 1, ok: true}"));

		}

		@Test
		@DisplayName("シングルクォート（中のダブルクォートと \\' も）")
		void singleQuotes () {

			assertEquals(Map.of("a", "say \"hi\"", "b", "it's"), Json.read("{'a':'say \"hi\"', 'b':'it\\'s'}"));

		}

		@Test
		@DisplayName("末尾のカンマ")
		void trailingCommas () {

			assertEquals(Map.of("a", List.of(1, 2)), Json.read("{\"a\":[1,2,],}"));

		}

		@Test
		@DisplayName("コメント")
		void comments () {

			assertEquals(Map.of("a", 1, "b", 2), Json.read("""
				{
					// 行のコメント
					"a": 1, /* かこみの
					コメント */ "b": 2
				}
				"""));

		}

		@Test
		@DisplayName("値のすぐあとのコメントは値に含めない。a/b の / は値の一部")
		void commentRightAfterValue () {

			assertEquals(List.of(1, 2.5, "x", "a/b"), Json.read("[1/*c*/,2.5//d\n,x/*e*/, a/b]"));

		}

		@Test
		@DisplayName("NaN / Infinity は Double")
		void specialDoubles () {

			assertTrue(Double.isNaN((Double) value("NaN")));
			assertEquals(Double.POSITIVE_INFINITY, value("Infinity"));
			assertEquals(Double.NEGATIVE_INFINITY, value("-Infinity"));

		}

		@Test
		@DisplayName("+1 / 01 / .5 / 1. は数値")
		void looseNumbers () {

			assertEquals(1, value("+1"));
			assertEquals(1, value("01"));
			assertEquals(0.5, value(".5"));
			assertEquals(1.0, value("1."));

		}

		@Test
		@DisplayName("数値の形でないクォートの無い値は文字列（0x10 / TRUE / 1d / -）")
		void bareStrings () {

			assertEquals("0x10", value("0x10"));
			assertEquals("TRUE", value("TRUE"));
			assertEquals("1d", value("1d"));
			assertEquals("-", value("-"));

		}

		@Test
		@DisplayName("文字列の中の生の改行とタブ")
		void rawControlCharacters () {

			assertEquals("a\nb\tc", value("\"a\nb\tc\""));

		}

	}

	@Nested
	@DisplayName("壊れたものは断る（位置つき）")
	class Rejects {

		@ParameterizedTest
		@ValueSource(strings = {
			"{\"a\":1 \"b\":2}",
			"[1,,2]",
			"[1 2]",
			"[1:2]",
			"{\"a\":}",
			"{\"a\"::1}",
			"{\"a\" 1}",
			"{:1}",
			"{a b:1}",
			"{\"s\":abc def}",
			"{\"f\":function(){return 1}}",
			"{\"a\":1",
			"[1,2",
			"{\"a\":\"x",
			"{\"a\":1} x",
			"garbage]",
			"/* 閉じていない"
		})
		@DisplayName("区切りの抜け・値の無いキー・閉じていないもの・後ろの余分")
		void broken (String json) {

			fails(json);

		}

		@ParameterizedTest
		@ValueSource(strings = {"\"\\q\"", "\"\\uZZZZ\"", "\"\\u12\"", "\"\\あ\"", "\"abc\\"})
		@DisplayName("壊れたエスケープ")
		void brokenEscapes (String json) {

			JsonException e = fails("{\"a\":" + json + "}");
			assertTrue(e.getMessage().contains("\\"), e.getMessage());

		}

		@Test
		@DisplayName("例外に行と文字の位置が載る")
		void position () {

			JsonException e = fails("{\n  \"a\": 1\n  \"b\": 2\n}");

			assertEquals(3, e.line());
			assertEquals(3, e.column());
			assertTrue(e.getMessage().contains("3 行 3 文字目"), e.getMessage());

		}

	}

	@Nested
	@DisplayName("厳密な読み")
	class Strict {

		@ParameterizedTest
		@ValueSource(strings = {
			"{a:1}", "{'a':1}", "{\"a\":'x'}", "{\"a\":x}", "[1,]", "{\"a\":1,}",
			"[NaN]", "[+1]", "[01]", "[.5]", "[1.]", "// c\n[1]", "[\"a\nb\"]"
		})
		@DisplayName("ゆるい書き方を断る")
		void rejectsLenient (String json) {

			assertThrows(JsonException.class, () -> Json.read(json, STRICT));

		}

		@Test
		@DisplayName("JSON の決まりどおりのものは読める")
		void readsStandard () {

			assertEquals(Map.of("a", List.of(1, 2.5, -0.0, "x", true), "b", Map.of()), Json.read("{\"a\":[1,2.5,-0.0,\"x\",true],\"b\":{}}", STRICT));

		}

	}

	@Nested
	@DisplayName("上限")
	class Limits {

		@Test
		@DisplayName("入れ子が深すぎれば断る（既定 512 段）")
		void depth () {

			assertInstanceOf(List.class, Json.read("[".repeat(512) + "]".repeat(512)));
			assertThrows(JsonException.class, () -> Json.read("[".repeat(513) + "]".repeat(513)));

			JsonReadOptions shallow = JsonReadOptions.builder().maxDepth(2).build();
			assertThrows(JsonException.class, () -> Json.read("[[[1]]]", shallow));

		}

		@Test
		@DisplayName("長すぎる数値は断る（既定 1000 文字）")
		void numberLength () {

			assertInstanceOf(BigDecimal.class, value("9".repeat(1000)));

			JsonException e = fails("{\"v\":" + "9".repeat(1001) + "}");
			assertTrue(e.getMessage().contains("数値が長すぎます"), e.getMessage());

		}

		@Test
		@DisplayName("長い数値を並べても、読む時間は長さに比例する範囲に収まる")
		void manyLongNumbers () {

			// 上限ぎりぎりの数値を 1000 個（約 1MB）
			String json = "[" + String.join(",", java.util.Collections.nCopies(1000, "9".repeat(1000))) + "]";

			assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> Json.read(json));

		}

		@Test
		@DisplayName("長すぎる文字列は断る")
		void stringLength () {

			JsonReadOptions small = JsonReadOptions.builder().maxStringLength(5).build();

			assertEquals("abcde", ((List<?>) Json.read("[\"abcde\"]", small)).getFirst());
			assertThrows(JsonException.class, () -> Json.read("[\"abcdef\"]", small));
			assertThrows(JsonException.class, () -> Json.read("[\"abc\\ndef\"]", small));
			assertThrows(JsonException.class, () -> Json.read("[abcdef]", small));

		}

	}

	@Nested
	@DisplayName("文字列")
	class Strings {

		@Test
		@DisplayName("エスケープを戻す（サロゲートペアも）")
		void escapes () {

			assertEquals("\"\\/\b\f\n\r\t'", value("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\'\""));
			assertEquals("A\u3042\uD83D\uDE00", value("\"\\u0041\\u3042\\uD83D\\uDE00\""));

		}

		@Test
		@DisplayName("同じキーが続いても、別の文字のキーと取り違えない")
		void keyCache () {

			StringBuilder json = new StringBuilder("[");
			for (int i = 0; i < 2000; i++) {
				json.append(i == 0 ? "" : ",").append("{\"k").append(i % 700).append("\":").append(i).append(",\"name\":1}");
			}
			json.append("]");

			List<?> list = (List<?>) Json.read(json.toString());

			for (int i = 0; i < 2000; i++) {
				Map<?, ?> map = (Map<?, ?>) list.get(i);
				assertEquals(i, map.get("k" + (i % 700)), "i=" + i);
				assertEquals(1, map.get("name"));
			}

		}

	}

	@Nested
	@DisplayName("読み元")
	class Sources {

		@Test
		@DisplayName("Reader と InputStream から読める（8KB を超えても）")
		void readerAndStream () {

			String big = "{\"s\":\"" + "あ".repeat(10_000) + "\",\"n\":[1,2,3]}";

			Map<?, ?> fromReader = (Map<?, ?>) Json.read(new StringReader(big), JsonReadOptions.DEFAULT);
			Map<?, ?> fromStream = (Map<?, ?>) Json.read(new ByteArrayInputStream(big.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8, JsonReadOptions.DEFAULT);

			assertEquals(10_000, ((String) fromReader.get("s")).length());
			assertEquals(fromReader, fromStream);

		}

		@Test
		@DisplayName("Reader からでも、壊れていれば断る（途中までの値を返さない）")
		void brokenFromReader () {

			assertThrows(JsonException.class, () -> Json.read(new StringReader("{\"a\":1"), JsonReadOptions.DEFAULT));
			assertThrows(JsonException.class, () -> Json.read(new StringReader("{\"a\":1} x"), JsonReadOptions.DEFAULT));

		}

	}

}
