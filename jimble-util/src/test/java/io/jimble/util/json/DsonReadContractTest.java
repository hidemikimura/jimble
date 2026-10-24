package io.jimble.util.json;

import io.jimble.util.convertor.Configration;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON の読みで守る約束（作り直しのあとも同じでなければならないもの）
 *
 * <p>
 * {@code Dson} を jimble-json に置き換えるときの足場。
 * 公開の入口（{@code Dson.decodes} / {@code Data.fromJsonString}）だけを通して確かめる。
 * ここが落ちたら、作り直しが今の利用者の読み方を壊している。
 * </p>
 *
 * <p>今の挙動のうち、作り直しで変える予定のものは {@link DsonCurrentQuirksTest} にある。</p>
 */
class DsonReadContractTest {

	/**
	 * JSON の値を1つ、オブジェクトの中に置いて読む
	 *
	 * @param json	値の書き方
	 * @return	読んだ値
	 */
	private static Object value (String json) {

		return Data.fromJsonString("{\"v\":" + json + "}").get("v");

	}

	@Nested
	@DisplayName("オブジェクトと配列の形")
	class Shape {

		@Test
		@DisplayName("キーは書かれた順に並ぶ")
		void keepsKeyOrder () {

			Data data = Data.fromJsonString("{\"z\":1,\"a\":2,\"m\":3}");

			assertEquals(List.of("z", "a", "m"), new ArrayList<>(data.keySet()));

		}

		@Test
		@DisplayName("入れ子のオブジェクトは Data で読む")
		void nestedObjectIsData () {

			Data data = Data.fromJsonString("{\"user\":{\"name\":\"A\"}}");

			Data user = assertInstanceOf(Data.class, data.get("user"));
			assertEquals("A", user.get("name"));

		}

		@Test
		@DisplayName("配列は ArrayList で読む")
		void arrayIsArrayList () {

			Data data = Data.fromJsonString("{\"tags\":[\"x\",\"y\"]}");

			assertInstanceOf(ArrayList.class, data.get("tags"));
			assertEquals(List.of("x", "y"), data.get("tags"));

		}

		@Test
		@DisplayName("配列の中のオブジェクトも Data で読む")
		void objectInArrayIsData () {

			Data data = Data.fromJsonString("{\"items\":[{\"id\":1},{\"id\":2}]}");

			List<?> items = assertInstanceOf(List.class, data.get("items"));
			assertInstanceOf(Data.class, items.get(0));
			assertEquals(2, ((Data) items.get(1)).get("id"));

		}

		@Test
		@DisplayName("配列の中の配列はそのまま入れ子の List で読む")
		void arrayInArray () {

			Data data = Data.fromJsonString("{\"m\":[[1,2],[3]]}");

			assertEquals(List.of(List.of(1, 2), List.of(3)), data.get("m"));

		}

		@Test
		@DisplayName("空のオブジェクトは空の Data、空の配列は空の List")
		void emptyContainers () {

			Data data = Data.fromJsonString("{\"o\":{},\"a\":[]}");

			assertEquals(new Data(), data.get("o"));
			assertEquals(List.of(), data.get("a"));
			assertEquals(new Data(), Data.fromJsonString("{}"));

		}

		@Test
		@DisplayName("前後の空白と改行は読み飛ばす")
		void skipsWhitespace () {

			Data data = Data.fromJsonString("  \n\t{ \"a\" : 1 ,\r\n \"b\" : [ 1 , 2 ] }  \n");

			assertEquals(1, data.get("a"));
			assertEquals(List.of(1, 2), data.get("b"));

		}

		@Test
		@DisplayName("先頭の BOM（U+FEFF）は読み飛ばす")
		void skipsBom () {

			assertEquals(1, Data.fromJsonString("\uFEFF{\"a\":1}").get("a"));

		}

		@Test
		@DisplayName("同じキーが2回出たら、後のほうの値になる")
		void duplicateKeyLastWins () {

			Data data = Data.fromJsonString("{\"a\":1,\"a\":2}");

			assertEquals(2, data.get("a"));
			assertEquals(1, data.size());

		}

		@Test
		@DisplayName("空白を含むキーもそのまま読む")
		void keyWithSpace () {

			assertEquals(1, Data.fromJsonString("{\"k y\":1}").get("k y"));

		}

		@Test
		@DisplayName("入れ子の深い Data も、読み直すと同じ中身になる")
		void deepNestedRoundTrip () {

			Data data = Data.fromJsonString("{\"a\":{\"b\":{\"c\":[1,{\"d\":[true,null,\"x\"]}]}}}");

			assertEquals("{\"a\":{\"b\":{\"c\":[1,{\"d\":[true,null,\"x\"]}]}}}", data.getJsonString());

		}

	}

	@Nested
	@DisplayName("値の型")
	class Values {

		@Test
		@DisplayName("文字列は String で読む")
		void string () {

			assertEquals("きむら", value("\"きむら\""));

		}

		@Test
		@DisplayName("null はキーがあって値が null になる")
		void nullValue () {

			Data data = Data.fromJsonString("{\"a\":null}");

			assertTrue(data.containsKey("a"));
			assertNull(data.get("a"));

		}

		@Test
		@DisplayName("true / false は Boolean で読む")
		void booleans () {

			assertEquals(Boolean.TRUE, value("true"));
			assertEquals(Boolean.FALSE, value("false"));

		}

		@Test
		@DisplayName("型の無い読み（Data）では、文字列の \"true\" / \"false\" も Boolean にする")
		void stringBooleanIntoData () {

			assertEquals(Boolean.TRUE, value("\"true\""));
			assertEquals(Boolean.FALSE, value("\"false\""));

		}

		@Test
		@DisplayName("型の無い読み（Map）でも、文字列の \"true\" は Boolean にする")
		void stringBooleanIntoMap () {

			Map<?, ?> map = Dson.decodes("{\"t\":\"true\"}", Map.class);

			assertEquals(Boolean.TRUE, map.get("t"));

		}

		@Test
		@DisplayName("型の無い読み（List）でも、文字列の \"true\" / \"false\" は Boolean にする")
		void stringBooleanIntoList () {

			List<?> list = Dson.decodes("[\"true\",\"false\"]", List.class);

			assertEquals(List.of(true, false), list);

		}

		@Test
		@DisplayName("要素の型に String を指定した読みでは、文字列の \"true\" は String のまま")
		void stringBooleanIntoTypedList () {

			List<String> list = Dson.decodes("[\"true\",\"x\"]", List.class, String.class);

			assertEquals(List.of("true", "x"), list);

		}

		@Test
		@DisplayName("Data から getString で取れば、文字列の \"true\" は \"true\" に戻る")
		void stringBooleanGetString () {

			assertEquals("true", Data.fromJsonString("{\"s\":\"true\"}").getString("s"));

		}

		@Test
		@DisplayName("文字列の \"null\" は null にせず、文字列のまま")
		void stringNullStaysString () {

			assertEquals("null", value("\"null\""));

		}

		@Test
		@DisplayName("数字だけの文字列は数にせず、文字列のまま（先頭の 0 も残る）")
		void numericStringStaysString () {

			assertEquals("012", value("\"012\""));

		}

	}

	@Nested
	@DisplayName("数値の型（D-298）")
	class Numbers {

		@Test
		@DisplayName("int に収まる整数は Integer")
		void intRange () {

			assertEquals(Integer.MAX_VALUE, value("2147483647"));
			assertEquals(Integer.MIN_VALUE, value("-2147483648"));
			assertEquals(0, value("0"));

		}

		@Test
		@DisplayName("int を超えて long に収まる整数は Long")
		void longRange () {

			assertEquals(2147483648L, value("2147483648"));
			assertEquals(-2147483649L, value("-2147483649"));
			assertEquals(Long.MAX_VALUE, value("9223372036854775807"));
			assertEquals(Long.MIN_VALUE, value("-9223372036854775808"));

		}

		@Test
		@DisplayName("long を超える整数は BigDecimal")
		void beyondLong () {

			assertEquals(new BigDecimal("9223372036854775808"), value("9223372036854775808"));
			assertEquals(new BigDecimal("-9223372036854775809"), value("-9223372036854775809"));

		}

		@Test
		@DisplayName("小数は Double（Float ではない）")
		void decimalIsDouble () {

			assertEquals(1.5d, value("1.5"));
			assertEquals(-1.5d, value("-1.5"));
			assertEquals(1234567.89d, value("1234567.89"));

		}

		@Test
		@DisplayName("0.1 は Double の 0.1 で、getBigDecimal で 0.1 が取れる")
		void pointOne () {

			Data data = Data.fromJsonString("{\"v\":0.1}");

			assertEquals(0.1d, data.get("v"));
			assertEquals(new BigDecimal("0.1"), data.getBigDecimal("v"));

		}

		@Test
		@DisplayName("小数点が 0 だけの小数（1.0 / 100.00）も Double")
		void zeroFraction () {

			assertEquals(1.0d, value("1.0"));
			assertEquals(100.0d, value("100.00"));

		}

		@Test
		@DisplayName("「.」の無い指数の小数（1e-3）は Double")
		void negativeExponentIsDouble () {

			assertEquals(0.001d, value("1e-3"));

		}

		@Test
		@DisplayName("指数で整数になるもの（1e3 / 1E3）は Integer")
		void positiveExponentIsInteger () {

			assertEquals(1000, value("1e3"));
			assertEquals(1000, value("1E3"));

		}

		@Test
		@DisplayName("Double に収まる大きな小数は Double、収まらなければ BigDecimal")
		void doubleRange () {

			assertEquals(1.5e308d, value("1.5e308"));
			assertEquals(new BigDecimal("-1.0e309"), value("-1.0e309"));

		}

		@Test
		@DisplayName("-0.0 は Double の -0.0")
		void negativeZeroDecimal () {

			assertEquals(-0.0d, value("-0.0"));

		}

	}

	@Nested
	@DisplayName("ゆるい書き方を受け付ける")
	class Lenient {

		@Test
		@DisplayName("クォートの無いキーを読む")
		void unquotedKey () {

			Data data = Data.fromJsonString("{a:1,b:2}");

			assertEquals(1, data.get("a"));
			assertEquals(2, data.get("b"));

		}

		@Test
		@DisplayName("クォートの無い値は文字列で読む")
		void unquotedValue () {

			assertEquals("x", Data.fromJsonString("{a:x}").get("a"));

		}

		@Test
		@DisplayName("クォートの無いキーの true / null / 数字は、その字面のキーになる")
		void unquotedLiteralKeys () {

			Data data = Data.fromJsonString("{true:1,null:2,1:3}");

			assertEquals(List.of("true", "null", "1"), new ArrayList<>(data.keySet()));

		}

		@Test
		@DisplayName("シングルクォートのキーと値を読む")
		void singleQuote () {

			assertEquals("b", Data.fromJsonString("{'a':'b'}").get("a"));

		}

		@Test
		@DisplayName("シングルクォートの中のダブルクォートはそのまま")
		void doubleQuoteInsideSingleQuote () {

			assertEquals("say \"hi\"", Data.fromJsonString("{'a':'say \"hi\"'}").get("a"));

		}

		@Test
		@DisplayName("オブジェクトの末尾のカンマを受け付ける")
		void trailingCommaInObject () {

			Data data = Data.fromJsonString("{\"a\":1,}");

			assertEquals(1, data.get("a"));
			assertEquals(1, data.size());

		}

		@Test
		@DisplayName("配列の末尾のカンマを受け付ける")
		void trailingCommaInArray () {

			List<?> list = Dson.decodes("[1,2,]", List.class);

			assertEquals(List.of(1, 2), list);

		}

	}

	@Nested
	@DisplayName("文字列のエスケープ")
	class Escapes {

		@Test
		@DisplayName("\\\" \\\\ \\/ \\b \\f \\n \\r \\t を戻す")
		void simpleEscapes () {

			assertEquals("\"\\/\b\f\n\r\t", value("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\""));

		}

		@Test
		@DisplayName("\\uXXXX を戻す（16進は小文字も大文字も）")
		void unicodeEscape () {

			assertEquals("é", value("\"\\u00e9\""));
			assertEquals("é", value("\"\\u00E9\""));
			assertEquals("あ", value("\"\\u3042\""));

		}

		@Test
		@DisplayName("サロゲートペアの \\uXXXX\\uXXXX は非 BMP 文字に戻る")
		void surrogatePairEscape () {

			assertEquals("😀", value("\"\\ud83d\\ude00\""));

		}

		@Test
		@DisplayName("非 BMP 文字をそのまま書いた JSON を読める")
		void rawNonBmp () {

			assertEquals("😀𠮷", value("\"😀𠮷\""));

		}

		@Test
		@DisplayName("文字列の中の } や ] は区切りとみなさない")
		void bracketInsideString () {

			assertEquals("}]", value("\"}]\""));
			assertEquals("\"}", value("\"\\\"}\""));

		}

		@Test
		@DisplayName("1024 文字を超える長い文字列も切らずに読む")
		void longString () {

			String text = "x".repeat(5000);

			assertEquals(text, value("\"" + text + "\""));

		}

	}

	@Nested
	@DisplayName("読み先の型")
	class Targets {

		@Test
		@DisplayName("配列の JSON は List を指定して読む")
		void arrayAsList () {

			List<?> list = Dson.decodes("[1,\"a\",true,null]", List.class);

			assertInstanceOf(ArrayList.class, list);
			assertEquals(java.util.Arrays.asList(1, "a", true, null), list);

		}

		@Test
		@DisplayName("オブジェクトの配列は (ArrayList, Data) を指定すると List<Data> になる")
		void arrayAsDataList () {

			List<Data> list = Dson.decodes("[{\"a\":1},{\"a\":2}]", ArrayList.class, Data.class);

			assertEquals(2, list.size());
			assertEquals(2, list.get(1).get("a"));

		}

		@Test
		@DisplayName("クラスを指定しないと Data で読む")
		void noClassIsData () {

			Object data = Dson.decodes("{\"a\":1}");

			assertInstanceOf(Data.class, data);

		}

		@Test
		@DisplayName("Object を指定すると、オブジェクトは Data、配列は ArrayList")
		void objectTarget () {

			assertInstanceOf(Data.class, Dson.decodes("{\"a\":1}", Object.class));
			assertInstanceOf(ArrayList.class, Dson.decodes("[1]", Object.class));

		}

		@Test
		@DisplayName("Map を指定すると LinkedHashMap で、キーの順を保ち、入れ子は Data")
		void mapTarget () {

			Map<?, ?> map = Dson.decodes("{\"b\":1,\"a\":{\"c\":2}}", Map.class);

			assertInstanceOf(LinkedHashMap.class, map);
			assertEquals(List.of("b", "a"), new ArrayList<>(map.keySet()));
			assertInstanceOf(Data.class, map.get("a"));

		}

		@Test
		@DisplayName("record に読める")
		void recordTarget () {

			Item item = Dson.decodes("{\"name\":\"a\",\"count\":2}", Item.class);

			assertEquals(new Item("a", 2), item);

		}

		@Test
		@DisplayName("Bean の String のフィールドには、文字列の \"true\" が \"true\" で入る")
		void beanStringFieldKeepsTrue () {

			Named named = Dson.decodes("{\"name\":\"true\"}", Named.class);

			assertEquals("true", named.getName());

		}

		@Test
		@DisplayName("Data の値が JSON の文字なら、getData / getDataList で読める")
		void dataFromJsonStringValue () {

			Data data = new Data()
				.putData("one", "{\"a\":1}")
				.putData("many", "[{\"a\":2}]");

			assertEquals(1, data.getData("one").get("a"));
			assertEquals(2, data.getDataList("many").get(0).get("a"));

		}

	}

	@Nested
	@DisplayName("読み元")
	class Sources {

		@Test
		@DisplayName("Reader から読める")
		void reader () {

			Data data = Dson.decodes(new StringReader("{\"a\":\"あ\"}"), Data.class);

			assertEquals("あ", data.get("a"));

		}

		@Test
		@DisplayName("Reader から非 BMP 文字を読める")
		void readerNonBmp () {

			Data data = Dson.decodes(new StringReader("{\"a\":\"😀\"}"), Data.class);

			assertEquals("😀", data.get("a"));

		}

		@Test
		@DisplayName("InputStream から UTF-8 で読める（非 BMP 文字とそのエスケープも）")
		void inputStreamUtf8 () {

			byte[] bytes = "{\"a\":\"😀\",\"b\":\"\\ud83d\\ude00\"}".getBytes(StandardCharsets.UTF_8);

			Data data = new Dson().decodes(new ByteArrayInputStream(bytes), "UTF-8", Data.class);

			assertEquals("😀", data.get("a"));
			assertEquals("😀", data.get("b"));

		}

		@Test
		@DisplayName("InputStream から指定した文字コード（Shift_JIS）で読める")
		void inputStreamShiftJis () {

			byte[] bytes = "{\"a\":\"あ\"}".getBytes(Charset.forName("Shift_JIS"));

			Data data = new Dson().decodes(new ByteArrayInputStream(bytes), "Shift_JIS", Data.class);

			assertEquals("あ", data.get("a"));

		}

		@Test
		@DisplayName("ファイルから読める")
		void file () throws IOException {

			File file = File.createTempFile("dson-read", ".json");
			file.deleteOnExit();
			Files.writeString(file.toPath(), "{\"a\":\"é\"}", StandardCharsets.UTF_8);

			Data data = Dson.decodes(file, "UTF-8", Data.class);

			assertEquals("é", data.get("a"));

		}

		@Test
		@DisplayName("設定（Configration）つきの文字列の読みも同じ結果になる")
		void withConfigration () {

			Data data = Dson.decodes(new Configration(), "{\"a\":1}", Data.class);

			assertEquals(1, data.get("a"));

		}

	}

	@Nested
	@DisplayName("空と壊れた JSON（D-195）")
	class EmptyAndBroken {

		@Test
		@DisplayName("空文字・空白だけ・\"null\" は null（例外にしない）")
		void emptyIsNull () {

			assertNull(Dson.decodes("", Data.class));
			assertNull(Dson.decodes("   ", Data.class));
			assertNull(Dson.decodes("null", Data.class));
			assertNull(Data.fromJsonString(""));
			assertNull(Data.fromJsonString("null"));

		}

		@Test
		@DisplayName("Data.fromJsonString(null) は null")
		void fromJsonStringNull () {

			assertNull(Data.fromJsonString(null));

		}

		@Test
		@DisplayName("閉じていないオブジェクト・配列は JsonParseException")
		void unclosed () {

			assertThrows(JsonParseException.class, () -> Dson.decodes("{\"a\":1", Data.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes("[1,2", List.class));
			assertThrows(JsonParseException.class, () -> Data.fromJsonString("{\"a\":1"));

		}

		@Test
		@DisplayName("値のあとに余分なものがあれば JsonParseException")
		void trailingGarbage () {

			assertThrows(JsonParseException.class, () -> Dson.decodes("{\"a\":1} x", Data.class));
			assertThrows(JsonParseException.class, () -> Data.fromJsonString("{\"a\":1}{}"));

		}

		@Test
		@DisplayName("JSON ではない文字は JsonParseException")
		void notJson () {

			assertThrows(JsonParseException.class, () -> Dson.decodes("これは JSON ではない", Data.class));
			assertThrows(JsonParseException.class, () -> Data.fromJsonString("abc"));

		}

		@Test
		@DisplayName("512 段より深い入れ子は、文字列でも Reader でも JsonParseException")
		void tooDeep () {

			String deep = "[".repeat(600) + "]".repeat(600);

			assertThrows(JsonParseException.class, () -> Dson.decodes(deep, List.class));
			assertThrows(JsonParseException.class, () -> Dson.decodes(new StringReader(deep), List.class));

		}

	}

	@Nested
	@DisplayName("日付の型つきの読み（isOutputDateType）")
	class DateType {

		private TimeZone saved;

		@BeforeEach
		void fixZone () {

			saved = TimeZone.getDefault();
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

		}

		@AfterEach
		void restoreZone () {

			TimeZone.setDefault(saved);

		}

		@Test
		@DisplayName("\"date::yyyy/MM/dd HH:mm:ss\" を Date で読む")
		void dateTypePrefix () {

			Configration conf = new Configration();
			conf.isOutputDateType(true);

			Data data = Dson.decodes(conf, "{\"d\":\"date::2026/01/02 03:04:05\"}", Data.class);

			Date date = assertInstanceOf(Date.class, data.get("d"));
			assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5),
				LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));

		}

		@Test
		@DisplayName("設定が無ければ \"date::...\" は文字列のまま")
		void withoutDateType () {

			assertEquals("date::2026/01/02 03:04:05", value("\"date::2026/01/02 03:04:05\""));

		}

	}

	/** 読み先の record */
	record Item(String name, int count) {}

	/** 読み先の Bean */
	public static class Named {

		private String name;

		public String getName () {
			return name;
		}

		public void setName (String name) {
			this.name = name;
		}

	}

}
