package io.jimble.util.json;

import io.jimble.util.convertor.Configration;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON の書き出しで守る約束（作り直しのあとも同じでなければならないもの）
 *
 * <p>
 * 公開の入口（{@code Dson.encodes} / {@code Data.getJsonString} / {@code Data.outputJsonString} /
 * {@code Dson.escapeString}）だけを通して確かめる。期待値は今の出力そのもの（バイト単位で同じ）。
 * </p>
 *
 * <p>今の挙動のうち、作り直しで変える予定のものは {@link DsonCurrentQuirksTest} にある。</p>
 */
class DsonWriteContractTest {

	@Nested
	@DisplayName("オブジェクトの形")
	class Shape {

		@Test
		@DisplayName("キーは入れた順に書く")
		void keepsInsertionOrder () {

			Data data = new Data();
			for (String key : List.of("z", "a", "m")) {
				data.putData(key, 1);
			}

			assertEquals("{\"z\":1,\"a\":1,\"m\":1}", Dson.encodes(data));

		}

		@Test
		@DisplayName("null の値も書く（既定）")
		void writesNull () {

			Data data = new Data();
			data.put("a", null);
			data.put("b", 1);

			assertEquals("{\"a\":null,\"b\":1}", Dson.encodes(data));

		}

		@Test
		@DisplayName("setOutputNullValue(false) なら null の項目を書かない")
		void omitsNullWhenConfigured () {

			Data data = new Data();
			data.put("a", null);
			data.put("b", 1);

			assertEquals("{\"b\":1}", Dson.encodes(new Configration().setOutputNullValue(false), data));

		}

		@Test
		@DisplayName("入れ子の Data とリストをそのまま書く")
		void nested () {

			Data data = new Data()
				.putData("user", new Data().putData("name", "A"))
				.putData("tags", List.of("x", "y"));

			assertEquals("{\"user\":{\"name\":\"A\"},\"tags\":[\"x\",\"y\"]}", Dson.encodes(data));

		}

		@Test
		@DisplayName("空の Data は {}、空のリストは []")
		void empty () {

			assertEquals("{}", Dson.encodes(new Data()));
			assertEquals("[]", Dson.encodes(new ArrayList<>()));
			assertEquals("{}", new Data().getJsonString());

		}

		@Test
		@DisplayName("リストの中の null は null と書く")
		void nullInList () {

			assertEquals("[1,null,\"a\"]", Dson.encodes(Arrays.asList(1, null, "a")));

		}

		@Test
		@DisplayName("Map の文字でないキーは文字にして書く")
		void nonStringMapKey () {

			Map<Object, Object> map = new LinkedHashMap<>();
			map.put(1, "a");
			map.put(true, "b");

			assertEquals("{\"1\":\"a\",\"true\":\"b\"}", Dson.encodes(map));

		}

		@Test
		@DisplayName("Map の null のキーは書かない")
		void skipsNullKey () {

			Map<String, Object> map = new HashMap<>();
			map.put(null, 1);
			map.put("a", 2);

			assertEquals("{\"a\":2}", Dson.encodes(map));

		}

		@Test
		@DisplayName("TreeMap はその並び（キーの順）で書く")
		void treeMapOrder () {

			assertEquals("{\"a\":2,\"b\":1}", Dson.encodes(new TreeMap<>(Map.of("b", 1, "a", 2))));

		}

		@Test
		@DisplayName("同じ Data を2か所から指していても（循環でなければ）両方に中身を書く")
		void sharedNotCycle () {

			Data shared = new Data().putData("v", 1);

			assertEquals("{\"a\":{\"v\":1},\"b\":{\"v\":1}}",
				Dson.encodes(new Data().putData("a", shared).putData("b", shared)));

		}

		@Test
		@DisplayName("同じリストを2か所から指していても両方に中身を書く")
		void sharedListNotCycle () {

			List<Integer> shared = new ArrayList<>(List.of(1));

			assertEquals("{\"a\":[1],\"b\":[1]}",
				Dson.encodes(new Data().putData("a", shared).putData("b", shared)));

		}

	}

	@Nested
	@DisplayName("循環参照")
	class Cycles {

		@Test
		@DisplayName("自分自身を含む Data は、2回目を {} と書く（落ちない）")
		void dataCycle () {

			Data data = new Data();
			data.put("self", data);
			data.put("x", 1);

			assertEquals("{\"self\":{},\"x\":1}", Dson.encodes(data));

		}

		@Test
		@DisplayName("自分自身を含むリストは、2回目を [] と書く（落ちない）")
		void listCycle () {

			List<Object> list = new ArrayList<>();
			list.add(1);
			list.add(list);

			assertEquals("[1,[]]", Dson.encodes(list));

		}

		@Test
		@DisplayName("循環する Data の getJsonString も {} で切って返す")
		void dataCycleGetJsonString () {

			Data parent = new Data();
			Data child = new Data().putData("parent", parent);
			parent.put("child", child);

			assertEquals("{\"child\":{\"parent\":{}}}", parent.getJsonString());

		}

	}

	@Nested
	@DisplayName("文字列のエスケープ")
	class Escapes {

		@Test
		@DisplayName("/ は \\/ と書く")
		void slash () {

			assertEquals("{\"m\":\"x\\/y\"}", Dson.encodes(new Data().putData("m", "x/y")));

		}

		@Test
		@DisplayName("\" と \\ をエスケープする")
		void quoteAndBackslash () {

			assertEquals("{\"s\":\"a\\\"b\\\\c\"}", Dson.encodes(new Data().putData("s", "a\"b\\c")));

		}

		@Test
		@DisplayName("\\b \\f \\n \\r \\t は短い形で書く")
		void shortEscapes () {

			assertEquals("{\"s\":\"\\b\\f\\n\\r\\t\"}", Dson.encodes(new Data().putData("s", "\b\f\n\r\t")));

		}

		@Test
		@DisplayName("そのほかの制御文字と DEL は \\u00XX（16進は大文字）で書く")
		void controlChars () {

			assertEquals("{\"s\":\"\\u0000\\u001F\\u007F\"}",
				Dson.encodes(new Data().putData("s", "\u0000\u001f\u007f")));

		}

		@Test
		@DisplayName("U+2028 / U+2029 は \\u2028 / \\u2029 で書く")
		void lineSeparators () {

			assertEquals("{\"s\":\"\\u2028\\u2029\"}", Dson.encodes(new Data().putData("s", "  ")));

		}

		@Test
		@DisplayName("ASCII 以外の文字（日本語・é）はエスケープせずにそのまま書く")
		void nonAsciiRaw () {

			assertEquals("{\"s\":\"きむらé\"}", Dson.encodes(new Data().putData("s", "きむらé")));

		}

		@Test
		@DisplayName("非 BMP 文字はエスケープせずにそのまま書く")
		void nonBmpRaw () {

			assertEquals("{\"s\":\"😀𠮷\"}", Dson.encodes(new Data().putData("s", "😀𠮷")));

		}

		@Test
		@DisplayName("< と > は既定ではエスケープしない")
		void angleBracketsRaw () {

			assertEquals("{\"s\":\"<b>\"}", Dson.encodes(new Data().putData("s", "<b>")));

		}

		@Test
		@DisplayName("isOutputStrict なら < と > を \\u003C / \\u003E で書く")
		void angleBracketsStrict () {

			Configration conf = new Configration();
			conf.isOutputStrict(true);

			assertEquals("{\"s\":\"\\u003Cb\\u003E\"}", Dson.encodes(conf, new Data().putData("s", "<b>")));

		}

		@Test
		@DisplayName("isRemove4ByteCharacter なら非 BMP 文字を落とす")
		void removeFourByte () {

			Configration conf = new Configration();
			conf.isRemove4ByteCharacter(true);

			assertEquals("{\"s\":\"ab\"}", Dson.encodes(conf, new Data().putData("s", "a😀b")));

		}

		@Test
		@DisplayName("キーもエスケープする")
		void keyEscape () {

			assertEquals("{\"a\\\"\\/b\":1}", Dson.encodes(new Data().putData("a\"/b", 1)));

		}

		@Test
		@DisplayName("エスケープの要る文字は、書いて読むと元に戻る")
		void escapeRoundTrip () {

			String text = "\"\\/\b\f\n\r\t\u0000\u001f\u007f  <>é";

			assertEquals(text, Data.fromJsonString(new Data().putData("s", text).getJsonString()).get("s"));

		}

		@Test
		@DisplayName("非 BMP 文字は、書いて読むと元に戻る")
		void nonBmpRoundTrip () {

			assertEquals("😀𠮷", Data.fromJsonString(new Data().putData("s", "😀𠮷").getJsonString()).get("s"));

		}

		@Test
		@DisplayName("1024 文字を超える長い文字列も切らずに書く")
		void longString () {

			String text = "y".repeat(3000);

			assertEquals("{\"s\":\"" + text + "\"}", Dson.encodes(new Data().putData("s", text)));

		}

	}

	@Nested
	@DisplayName("escapeString")
	class EscapeString {

		@Test
		@DisplayName("\" \\ / と制御文字を、書き出しと同じ形でエスケープする")
		void escapes () {

			assertEquals("a\\\"b\\\\c\\/d\\n\\u0000\\u2028", Dson.escapeString("a\"b\\c/d\n\u0000 "));

		}

		@Test
		@DisplayName("ASCII 以外と非 BMP 文字はそのまま")
		void nonAscii () {

			assertEquals("きむら😀", Dson.escapeString("きむら😀"));

		}

		@Test
		@DisplayName("null と空文字は空文字")
		void nullAndEmpty () {

			assertEquals("", Dson.escapeString(null));
			assertEquals("", Dson.escapeString(""));

		}

	}

	@Nested
	@DisplayName("値の書き方")
	class Values {

		@Test
		@DisplayName("int / long / short / byte はそのまま数で書く")
		void integers () {

			Data data = new Data()
				.putData("i", 1)
				.putData("l", Long.MAX_VALUE)
				.putData("s", (short) 3)
				.putData("b", (byte) 4);

			assertEquals("{\"i\":1,\"l\":9223372036854775807,\"s\":3,\"b\":4}", Dson.encodes(data));

		}

		@Test
		@DisplayName("double は Java の文字表現で書く（1.0 / 1.5 / -0.0）")
		void doubles () {

			Data data = new Data()
				.putData("a", 1.0)
				.putData("b", 1.5)
				.putData("c", -0.0);

			assertEquals("{\"a\":1.0,\"b\":1.5,\"c\":-0.0}", Dson.encodes(data));

		}

		@Test
		@DisplayName("大きい・小さい double は指数の形（1.0E20 / 1.0E-7）で書く")
		void doubleExponent () {

			assertEquals("{\"a\":1.0E20,\"b\":1.0E-7}", Dson.encodes(new Data().putData("a", 1e20).putData("b", 1e-7)));

		}

		@Test
		@DisplayName("float は float の文字表現で書く（0.1f は 0.1）")
		void floats () {

			assertEquals("{\"f\":0.1}", Dson.encodes(new Data().putData("f", 0.1f)));

		}

		@Test
		@DisplayName("BigDecimal は桁をそのまま書く（1.50 の 0 も残す）")
		void bigDecimal () {

			assertEquals("{\"a\":1.50}", Dson.encodes(new Data().putData("a", new BigDecimal("1.50"))));

		}

		@Test
		@DisplayName("BigInteger は桁をそのまま書く")
		void bigInteger () {

			assertEquals("{\"a\":123456789012345678901234567890}",
				Dson.encodes(new Data().putData("a", new BigInteger("123456789012345678901234567890"))));

		}

		@Test
		@DisplayName("Boolean は true / false")
		void booleans () {

			assertEquals("{\"t\":true,\"f\":false}", Dson.encodes(new Data().putData("t", true).putData("f", false)));

		}

		@Test
		@DisplayName("char は1文字の文字列")
		void character () {

			assertEquals("{\"c\":\"c\"}", Dson.encodes(new Data().putData("c", 'c')));

		}

		@Test
		@DisplayName("enum は名前の文字列")
		void enums () {

			assertEquals("{\"c\":\"RED\"}", Dson.encodes(new Data().putData("c", Color.RED)));

		}

		@Test
		@DisplayName("StringBuilder / URI / File は文字列（/ は \\/）")
		void stringLike () {

			Data data = new Data()
				.putData("sb", new StringBuilder("q"))
				.putData("u", URI.create("http://x/y"))
				.putData("f", new File("/tmp/a"));

			assertEquals("{\"sb\":\"q\",\"u\":\"http:\\/\\/x\\/y\",\"f\":\"\\/tmp\\/a\"}", Dson.encodes(data));

		}

		@Test
		@DisplayName("配列（int[] / String[] / char[] / byte[]）は JSON の配列")
		void arrays () {

			Data data = new Data()
				.putData("i", new int[]{1, 2})
				.putData("s", new String[]{"a", null})
				.putData("c", new char[]{'a', 'b'})
				.putData("b", new byte[]{1, 2});

			assertEquals("{\"i\":[1,2],\"s\":[\"a\",null],\"c\":[\"a\",\"b\"],\"b\":[1,2]}", Dson.encodes(data));

		}

		@Test
		@DisplayName("Set は回る順で JSON の配列にする")
		void sets () {

			assertEquals("{\"s\":[3,1,2]}", Dson.encodes(new Data().putData("s", new LinkedHashSet<>(List.of(3, 1, 2)))));

		}

		@Test
		@DisplayName("List.of のような不変のリストも JSON の配列")
		void immutableList () {

			assertEquals("[1,2]", Dson.encodes(List.of(1, 2)));

		}

		@Test
		@DisplayName("record はコンポーネントをキーにしたオブジェクト（順番は見ない）")
		void record () {

			String json = Dson.encodes(new Item("a", 2));

			assertEquals(new Data().putData("name", "a").putData("count", 2), Data.fromJsonString(json));

		}

		@Test
		@DisplayName("一番外が文字列・数・真偽値なら、その値だけを書く")
		void topLevelScalar () {

			assertEquals("\"a\\/b\\\"c\"", Dson.encodes("a/b\"c"));
			assertEquals("5", Dson.encodes(5));
			assertEquals("true", Dson.encodes(true));
			assertEquals("\"BLUE\"", Dson.encodes(Color.BLUE));

		}

		@Test
		@DisplayName("一番外が null なら空文字")
		void topLevelNull () {

			assertEquals("", Dson.encodes((Object) null));

		}

		@Test
		@DisplayName("いろいろな値の Data は、書いて読み戻すと同じ JSON になる")
		void roundTrip () {

			Data data = new Data()
				.putData("i", 1)
				.putData("l", 2147483648L)
				.putData("d", 1.5)
				.putData("s", "x/y")
				.putData("b", true)
				.putData("list", List.of(1, "a"))
				.putData("nested", new Data().putData("k", "v"));
			data.put("n", null);

			assertEquals(data.getJsonString(), Data.fromJsonString(data.getJsonString()).getJsonString());
			assertEquals(data, Data.fromJsonString(data.getJsonString()));

		}

	}

	@Nested
	@DisplayName("日時（時間帯は Asia/Tokyo に固定）")
	class Dates {

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
		@DisplayName("Date は既定の時間帯で yyyy/MM/dd HH:mm:ss の文字列にする")
		void date () {

			assertEquals("{\"d\":\"1970/01/01 09:00:00\"}", Dson.encodes(new Data().putData("d", new Date(0))));

		}

		@Test
		@DisplayName("Date の書き方は既定の時間帯に従う（UTC なら 00:00:00）")
		void dateFollowsZone () {

			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

			assertEquals("{\"d\":\"1970/01/01 00:00:00\"}", Dson.encodes(new Data().putData("d", new Date(0))));

		}

		@Test
		@DisplayName("ミリ秒は書かない")
		void dropsMillis () {

			assertEquals("{\"d\":\"1970/01/01 09:00:01\"}", Dson.encodes(new Data().putData("d", new Date(1999))));

		}

		@Test
		@DisplayName("java.sql.Timestamp も Date と同じ書き方")
		void timestamp () {

			assertEquals("{\"d\":\"1970/01/01 09:00:00\"}", Dson.encodes(new Data().putData("d", new Timestamp(0))));

		}

		@Test
		@DisplayName("LocalDateTime も yyyy/MM/dd HH:mm:ss（ナノ秒は書かない）")
		void localDateTime () {

			assertEquals("{\"d\":\"2026/01/02 03:04:05\"}",
				Dson.encodes(new Data().putData("d", LocalDateTime.of(2026, 1, 2, 3, 4, 5, 6))));

		}

		@Test
		@DisplayName("Date を書いて読み戻すと文字列になる（Date には戻らない）")
		void dateRoundTripIsString () {

			assertEquals("1970/01/01 09:00:00",
				Data.fromJsonString(new Data().putData("d", new Date(0)).getJsonString()).get("d"));

		}

		@Test
		@DisplayName("isOutputDateType なら \"date::\" を前に付ける")
		void dateTypePrefix () {

			Configration conf = new Configration();
			conf.isOutputDateType(true);

			assertEquals("{\"d\":\"date::1970/01/01 09:00:00\"}", Dson.encodes(conf, new Data().putData("d", new Date(0))));

		}

	}

	@Nested
	@DisplayName("書き先（ストリーム・Writer）")
	class Sinks {

		@Test
		@DisplayName("OutputStream に UTF-8 で書く")
		void outputStreamUtf8 () {

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			Dson.encodes(new Data().putData("a", "é😀"), out, "UTF-8");

			assertEquals("{\"a\":\"é😀\"}", out.toString(StandardCharsets.UTF_8));

		}

		@Test
		@DisplayName("OutputStream に指定した文字コード（Shift_JIS）で書く")
		void outputStreamShiftJis () {

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			Dson.encodes(new Data().putData("a", "あ"), out, "Shift_JIS");

			assertArrayEquals("{\"a\":\"あ\"}".getBytes(Charset.forName("Shift_JIS")), out.toByteArray());

		}

		@Test
		@DisplayName("Writer に書く")
		void writer () {

			StringWriter writer = new StringWriter();

			Dson.encodes(new Data().putData("a", 1), writer);

			assertEquals("{\"a\":1}", writer.toString());

		}

		@Test
		@DisplayName("isAutoClose(false) なら Writer を閉じない")
		void writerNotClosedWhenConfigured () {

			ClosingWriter writer = new ClosingWriter();
			Configration conf = new Configration();
			conf.isAutoClose(false);

			Dson.encodes(conf, new Data().putData("a", 1), writer);

			assertFalse(writer.closed);
			assertEquals("{\"a\":1}", writer.toString());

		}

		@Test
		@DisplayName("1024 文字を超える長い文字列も Writer に切らずに書く")
		void writerLongString () {

			StringWriter writer = new StringWriter();
			String text = "y".repeat(3000);

			Dson.encodes(new Data().putData("s", text), writer);

			assertEquals("{\"s\":\"" + text + "\"}", writer.toString());

		}

		@Test
		@DisplayName("Data.outputJsonString(OutputStream) は UTF-8 で書く")
		void outputJsonStringStream () {

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			new Data().putData("a", "é").outputJsonString(out);

			assertEquals("{\"a\":\"é\"}", out.toString(StandardCharsets.UTF_8));

		}

		@Test
		@DisplayName("Data.outputJsonString(OutputStream, false) はストリームを閉じない")
		void outputJsonStringStreamNotClosed () {

			ClosingStream out = new ClosingStream();

			new Data().putData("a", 1).outputJsonString(out, false);

			assertFalse(out.closed);
			assertEquals("{\"a\":1}", out.toString(StandardCharsets.UTF_8));

		}

		@Test
		@DisplayName("Data.outputJsonString(Writer) は null の値も書く")
		void outputJsonStringWriter () {

			StringWriter writer = new StringWriter();
			Data data = new Data();
			data.put("a", null);

			data.outputJsonString(writer);

			assertEquals("{\"a\":null}", writer.toString());

		}

		@Test
		@DisplayName("Data.outputJsonString(Writer, Configration) は設定に従う")
		void outputJsonStringWriterConf () {

			StringWriter writer = new StringWriter();
			Data data = new Data();
			data.put("a", null);
			data.put("b", 1);

			data.outputJsonString(writer, new Configration().setOutputNullValue(false));

			assertEquals("{\"b\":1}", writer.toString());

		}

		@Test
		@DisplayName("getJsonString と Dson.encodes は同じ JSON を返す")
		void getJsonStringEqualsEncodes () {

			Data data = new Data().putData("a", List.of(1, new Data().putData("b", "c/d")));

			assertEquals(Dson.encodes(data), data.getJsonString());

		}

		@Test
		@DisplayName("getJsonString(true) の整形した JSON も、読み戻すと同じ中身")
		void prettyPrintRoundTrip () {

			Data data = new Data()
				.putData("a", 1)
				.putData("b", List.of(1, 2))
				.putData("c", new Data().putData("d", "x"));

			assertEquals(data, Data.fromJsonString(data.getJsonString(true)));
			assertTrue(data.getJsonString(true).contains("\n"));

		}

	}

	/** 書く enum */
	enum Color { RED, BLUE }

	/** 書く record */
	record Item(String name, int count) {}

	/** close されたかを覚える Writer */
	static final class ClosingWriter extends StringWriter {

		private boolean closed;

		@Override
		public void close () {
			closed = true;
		}

	}

	/** close されたかを覚える OutputStream */
	static final class ClosingStream extends ByteArrayOutputStream {

		private boolean closed;

		@Override
		public void close () {
			closed = true;
		}

	}

}
