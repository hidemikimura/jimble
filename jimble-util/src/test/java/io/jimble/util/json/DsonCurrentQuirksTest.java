package io.jimble.util.json;

import io.jimble.util.data.Data;
import io.jimble.util.internal.json.stream.writer.JsonHashWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 今の挙動の記録（作り直しで変える予定のもの）
 *
 * <p>
 * <b>ここにあるのは「正しい」挙動ではない。</b>今はこう動く、という記録である。
 * 作り直し（jimble-json）で直したら、そのテストは「守る約束」の側
 * （{@link DsonReadContractTest} / {@link DsonWriteContractTest} / {@link JsonStreamWriterContractTest}）に
 * 新しい期待値で書き直し、ここからは消す。
 * </p>
 *
 * <p>
 * 落ちたときは「作り直しで意図して変えたのか」「たまたま変わったのか」を見分けること。
 * 前者ならここを消して約束の側へ、後者なら作り直しの側を疑う。
 * </p>
 */
class DsonCurrentQuirksTest {

	/**
	 * JSON の値を1つ、オブジェクトの中に置いて読む（形の検査を通る入口 Dson.decodes）
	 *
	 * @param json	値の書き方
	 * @return	読んだ値
	 */
	private static Object value (String json) {

		Data data = Dson.decodes("{\"v\":" + json + "}", Data.class);
		return data.get("v");

	}

	@Nested
	@DisplayName("現状: 読み先の型との食い違い（空の配列）")
	class EmptyArrayTarget {

		@Test
		@DisplayName("現状: [] を Dson.decodes(Data) は空の Data で返し、Data.fromJsonString は JsonParseException")
		void 現状_emptyArrayAsData () {

			assertEquals(new Data(), Dson.decodes("[]", Data.class));
			assertThrows(JsonParseException.class, () -> Data.fromJsonString("[]"));

		}

	}

	@Nested
	@DisplayName("現状: 読み先の型との食い違い")
	class TargetMismatch {

		/** 作り直しで JsonParseException（または変換の失敗）にする予定 */
		@Test
		@DisplayName("現状: 配列の JSON を Data で読むと、添字がキーの {\"0\":..,\"1\":..} になる")
		void 現状_arrayAsData () {

			Data data = Dson.decodes("[1,2]", Data.class);

			assertEquals(List.of("0", "1"), new ArrayList<>(data.keySet()));
			assertEquals(1, data.get("0"));

		}

		/** 作り直しで JsonParseException にする予定 */
		@Test
		@DisplayName("現状: Data.fromJsonString にオブジェクトの配列を渡すと {\"0\":{..}} になる")
		void 現状_arrayOfObjectsFromJsonString () {

			Data data = Data.fromJsonString("[{\"a\":1}]");

			assertEquals(Map.of("0", Map.of("a", 1)), data);

		}

		/** 作り直しで扱いを揃える予定 */
		@Test
		@DisplayName("現状: 配列の JSON を Map で読んでも、添字がキーになる")
		void 現状_arrayAsMap () {

			Map<?, ?> map = Dson.decodes("[1,2]", Map.class);

			assertEquals(Map.of("0", 1, "1", 2), map);

		}

		/** 作り直しで変換の失敗にする予定 */
		@Test
		@DisplayName("現状: オブジェクトの JSON を List で読むと、値だけの List になる（キーを捨てる）")
		void 現状_objectAsList () {

			List<?> list = Dson.decodes("{\"a\":1,\"b\":2}", List.class);

			assertEquals(List.of(1, 2), list);

		}

		/** 作り直しで、変換できない要素を分かる言葉で言う予定 */
		@Test
		@DisplayName("現状: 数の配列を (ArrayList, Data) で読むと、モジュールの例外を包んだ JsonParseException")
		void 現状_numbersAsDataList () {

			JsonParseException ex = assertThrows(JsonParseException.class,
				() -> Dson.decodes("[1,2]", ArrayList.class, Data.class));

			assertTrue(ex.getMessage().contains("java.lang"), ex.getMessage());

		}

	}

	@Nested
	@DisplayName("現状: 書けない型が黙って {} や途中で切れた JSON になる（作り直しで書けるようにするか、例外にする予定）")
	class UnsupportedTypes {

		/**
		 * 1つの型について、3つの入口の結果をまとめて確かめる
		 *
		 * <ul>
		 *   <li>{@code Dson.encodes} は空文字（ERROR のログを出す）</li>
		 *   <li>{@code getJsonString} は {@code {}}（中の {@code "x":1} も消える）</li>
		 *   <li>{@code outputJsonString} は途中まで書いて切れる</li>
		 * </ul>
		 *
		 * @param value			書けない値
		 * @param truncated		outputJsonString が書いたところまで
		 */
		private void assertSilentlyBroken (Object value, String truncated) {

			Data data = new Data().putData("x", 1).putData("d", value);

			assertEquals("", Dson.encodes(data));
			assertEquals("{}", data.getJsonString());

			StringWriter writer = new StringWriter();
			data.outputJsonString(writer);
			assertEquals(truncated, writer.toString());

		}

		/** 作り直しで "2026-01-02"（ISO）などの文字列で書く予定 */
		@Test
		@DisplayName("現状: java.time.LocalDate は {} になり、流すと途中で切れる")
		void 現状_localDate () {

			assertSilentlyBroken(LocalDate.of(2026, 1, 2), "{\"x\":1,\"d\":{");

		}

		/** 作り直しで文字列で書く予定 */
		@Test
		@DisplayName("現状: UUID は {} になり、流すと途中で切れる")
		void 現状_uuid () {

			assertSilentlyBroken(UUID.fromString("00000000-0000-0000-0000-000000000001"), "{\"x\":1,\"d\":{");

		}

		/** 作り直しで中身（無ければ null）を書く予定 */
		@Test
		@DisplayName("現状: Optional は {} になり、流すと途中で切れる")
		void 現状_optional () {

			assertSilentlyBroken(Optional.of("x"), "{\"x\":1,\"d\":{");

		}

		/** 作り直しで日付の文字列で書く予定（toInstant が UnsupportedOperationException を投げている） */
		@Test
		@DisplayName("現状: java.sql.Date は {} になり、流すと値の前で切れる")
		void 現状_sqlDate () {

			assertSilentlyBroken(new java.sql.Date(0), "{\"x\":1,\"d\":");

		}

		/** 作り直しで時刻の文字列で書く予定 */
		@Test
		@DisplayName("現状: java.sql.Time は {} になり、流すと途中で切れる（Bean として書こうとする）")
		void 現状_sqlTime () {

			assertSilentlyBroken(new java.sql.Time(0), "{\"x\":1,\"d\":{");

		}

		/** 作り直しで Date と同じ書き方にする予定（GregorianCalendar は書き方の表に無い） */
		@Test
		@DisplayName("現状: Calendar（GregorianCalendar）は書けず空文字になる")
		void 現状_calendar () {

			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(0);

			assertEquals("", Dson.encodes(new Data().putData("d", calendar)));

		}

		/** 作り直しで文字列で書く予定 */
		@Test
		@DisplayName("現状: Instant / ZonedDateTime / OffsetDateTime / LocalTime / Duration は書けず空文字になる")
		void 現状_otherJavaTime () {

			assertEquals("", Dson.encodes(new Data().putData("d", Instant.EPOCH)));
			assertEquals("", Dson.encodes(new Data().putData("d", ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC))));
			assertEquals("", Dson.encodes(new Data().putData("d", OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC))));
			assertEquals("", Dson.encodes(new Data().putData("d", LocalTime.of(1, 2))));
			assertEquals("", Dson.encodes(new Data().putData("d", Duration.ofSeconds(5))));

		}

		/** 作り直しで数で書く予定 */
		@Test
		@DisplayName("現状: AtomicInteger は書けず空文字になる")
		void 現状_atomicInteger () {

			assertEquals("", Dson.encodes(new Data().putData("a", new AtomicInteger(3))));

		}

		/** 作り直しで扱いを決め直す予定（書けないものは例外にする） */
		@Test
		@DisplayName("現状: フィールドの無い new Object() は {} と書く")
		void 現状_plainObject () {

			assertEquals("{\"c\":{}}", Dson.encodes(new Data().putData("c", new Object())));

		}

	}

	@Nested
	@DisplayName("現状: 数と特殊な値の書き方")
	class SpecialValues {

		/** 作り直しで null にするか例外にする予定（NaN は JSON に無い） */
		@Test
		@DisplayName("現状: Double の NaN / Infinity をそのまま NaN / Infinity と書く（JSON として読めない）")
		void 現状_nanAndInfinity () {

			Data data = new Data()
				.putData("a", Double.NaN)
				.putData("b", Double.POSITIVE_INFINITY)
				.putData("c", Double.NEGATIVE_INFINITY)
				.putData("d", Float.NaN);

			assertEquals("{\"a\":NaN,\"b\":Infinity,\"c\":-Infinity,\"d\":NaN}", Dson.encodes(data));

		}

		/** 作り直しで扱いを決め直す予定 */
		@Test
		@DisplayName("現状: 例外はメッセージとスタックトレースの文字列の配列で書く")
		void 現状_throwable () {

			RuntimeException ex = new RuntimeException("boom");
			ex.setStackTrace(new StackTraceElement[0]);

			assertEquals("{\"e\":[\"boom\"]}", Dson.encodes(new Data().putData("e", ex)));

		}

		/** 作り直しで扱いを決め直す予定 */
		@Test
		@DisplayName("現状: Class は toString の \"class java.lang.String\" と書く")
		void 現状_classValue () {

			assertEquals("{\"c\":\"class java.lang.String\"}", Dson.encodes(new Data().putData("c", String.class)));

		}

		/** 作り直しで扱いを決め直す予定 */
		@Test
		@DisplayName("現状: BigDecimal の指数の形 1E+3 をそのまま書く")
		void 現状_bigDecimalExponent () {

			assertEquals("{\"b\":1E+3}", Dson.encodes(new Data().putData("b", new java.math.BigDecimal("1E+3"))));

		}

	}

	@Nested
	@DisplayName("現状: 入れ子の深さの上限（50）を超えると黙って切る（作り直しで例外にする予定）")
	class DepthLimit {

		/**
		 * 入れ子の Data を作る
		 *
		 * @param depth	段数
		 * @return	一番外の Data
		 */
		private Data nestedData (int depth) {

			Data root = new Data();
			Data current = root;
			for (int i = 0; i < depth; i++) {
				Data next = new Data();
				current.put("a", next);
				current = next;
			}
			current.put("v", 1);
			return root;

		}

		/**
		 * 文字の中に部分の文字が何回出るか
		 *
		 * @param text	文字
		 * @param part	部分
		 * @return	回数
		 */
		private int count (String text, String part) {

			return (text.length() - text.replace(part, "").length()) / part.length();

		}

		/** 作り直しで、上限を超えたら例外にする予定 */
		@Test
		@DisplayName("現状: 55 段の Data は 51 段まで書き、その下を黙って {} にする（値 1 が消える）")
		void 現状_deepDataTruncated () {

			String json = Dson.encodes(nestedData(55));

			assertEquals(51, count(json, "\"a\""));
			assertTrue(json.endsWith("\"a\":{}" + "}".repeat(51)), json);
			assertTrue(!json.contains("\"v\""), json);

		}

		/** 作り直しで、上限を超えたら例外にする予定 */
		@Test
		@DisplayName("現状: 50 段の Data は最後まで書ける")
		void 現状_depthFiftyIsWritten () {

			assertTrue(Dson.encodes(nestedData(50)).contains("\"v\":1"));

		}

		/** 作り直しで、上限を超えたら例外にする予定 */
		@Test
		@DisplayName("現状: 55 段のリストは 52 個目の [ を [] にして切る（値 1 が消える）")
		void 現状_deepListTruncated () {

			List<Object> root = new ArrayList<>();
			List<Object> current = root;
			for (int i = 0; i < 55; i++) {
				List<Object> next = new ArrayList<>();
				current.add(next);
				current = next;
			}
			current.add(1);

			assertEquals("[".repeat(52) + "]".repeat(52), Dson.encodes(root));

		}

		/** 作り直しで、上限を超えたら例外にする予定 */
		@Test
		@DisplayName("現状: 60 段の JSON は読めるが、書き戻すと 51 段で切れる")
		void 現状_readDeepWriteTruncated () {

			String deep = "{\"a\":".repeat(60) + "1" + "}".repeat(60);

			String json = Data.fromJsonString(deep).getJsonString();

			assertEquals(51, count(json, "\"a\""));

		}

	}

	@Nested
	@DisplayName("現状: Bean の書き出し")
	class Beans {

		/** 作り直しで、公開されたもの（getter / public フィールド / record）だけを書く予定 */
		@Test
		@DisplayName("現状: Bean は private / transient のフィールドも書き、static は書かない（順番は見ない）")
		void 現状_beanWritesPrivateAndTransient () {

			Data back = Data.fromJsonString(Dson.encodes(new Bean()));

			assertEquals(Set.of("a", "b", "t", "secret"), back.keySet());
			assertEquals("hidden", back.get("secret"));
			assertEquals(9, back.get("t"));

		}

		/** 作り直しで、宣言の順など決まった順にする予定。今は JVM ごとに変わりうるので中身だけを見る */
		@Test
		@DisplayName("現状: getter / setter の Bean は全部のプロパティを書く（順番は JVM ごとに変わりうるので集合で見る）")
		void 現状_pojoProperties () {

			Pojo pojo = new Pojo();
			pojo.setName("n");
			pojo.setAge(3);

			Data back = Data.fromJsonString(Dson.encodes(pojo));

			assertEquals(Map.of("name", "n", "age", 3, "ok", false), back);

		}

		/** 作り直しで、null を書かない設定と同じく Bean でも揃える予定 */
		@Test
		@DisplayName("現状: Bean の null のフィールドも null と書く")
		void 現状_beanNullField () {

			Data back = Data.fromJsonString(Dson.encodes(new Pojo()));

			assertTrue(back.containsKey("name"));
			assertNull(back.get("name"));

		}

	}

	@Nested
	@DisplayName("現状: 書き出しの細かいところ")
	class WriteDetails {

		/** 作り直しで、Javadoc のとおり閉じないようにする（または Javadoc を直す）予定 */
		@Test
		@DisplayName("現状: Dson.encodes(obj, Writer) は既定で Writer を閉じる（Javadoc は「閉じない」と書いている）")
		void 現状_closesWriterByDefault () {

			ClosingWriter writer = new ClosingWriter();

			Dson.encodes(new Data().putData("a", 1), writer);

			assertTrue(writer.closed);

		}

		/** 作り直しで、閉じるかどうかを揃える予定 */
		@Test
		@DisplayName("現状: Data.outputJsonString(OutputStream) は既定でストリームを閉じる")
		void 現状_closesStreamByDefault () {

			ClosingStream out = new ClosingStream();

			new Data().putData("a", 1).outputJsonString(out);

			assertTrue(out.closed);

		}

		/** 作り直しで、エスケープの規則を1つにする予定 */
		@Test
		@DisplayName("現状: escapeString は < > を \\u003C / \\u003E にするが、書き出しは既定でそのまま")
		void 現状_escapeStringDiffersOnAngleBrackets () {

			assertEquals("\\u003Cb\\u003E", Dson.escapeString("<b>"));
			assertEquals("{\"s\":\"<b>\"}", Dson.encodes(new Data().putData("s", "<b>")));

		}

		/** 作り直しで字下げの形を決め直す予定（いまはタブと改行） */
		@Test
		@DisplayName("現状: getJsonString(true) はタブで字下げし、: の後ろに空白を入れない")
		void 現状_prettyPrintFormat () {

			Data data = new Data()
				.putData("a", 1)
				.putData("b", List.of(1, 2))
				.putData("c", new Data().putData("d", 1));

			assertEquals("{\n\t\"a\":1,\n\t\"b\":[\n\t\t1,\n\t\t2\n\t],\n\t\"c\":{\n\t\t\"d\":1\n\t}\n}", data.getJsonString(true));

		}

	}

	@Nested
	@DisplayName("現状: JsonHashWriter の落とし穴")
	class StreamWriters {

		/** 作り直しで、子を2回閉じないようにする予定 */
		@Test
		@DisplayName("現状: 子の hash を自分で閉じたあとに親へ書くと、} が2回出て JSON が壊れる")
		void 現状_childClosedTwice () throws IOException {

			StringWriter sink = new StringWriter();
			BufferedWriter buffered = new BufferedWriter(sink);

			try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
				try (JsonHashWriter child = hash.hash("c")) {
					child.write("x", 1);
				}
				hash.write("y", 2);
			}
			buffered.flush();

			assertEquals("{\"c\":{\"x\":1}},\"y\":2}", sink.toString());

		}

		/** 作り直しで、書けない値は例外にする予定 */
		@Test
		@DisplayName("現状: 書けない値（LocalDate）を渡すと { だけ書いて、壊れた JSON のまま続ける")
		void 現状_unsupportedValueBreaksStream () throws IOException {

			StringWriter sink = new StringWriter();
			BufferedWriter buffered = new BufferedWriter(sink);

			try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
				hash.write("d", LocalDate.of(2026, 1, 2));
				hash.write("z", 1);
			}
			buffered.flush();

			assertEquals("{\"d\":{,\"z\":1}", sink.toString());

		}

		/** 作り直しで、同じキーは例外にするかを決める予定 */
		@Test
		@DisplayName("現状: 同じキーを2回書くと、そのまま2回出る")
		void 現状_duplicateKey () throws IOException {

			StringWriter sink = new StringWriter();
			BufferedWriter buffered = new BufferedWriter(sink);

			try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
				hash.write("a", 1);
				hash.write("a", 2);
			}
			buffered.flush();

			assertEquals("{\"a\":1,\"a\":2}", sink.toString());

		}

		/** 作り直しで、埋め込む中身が JSON かを確かめる予定 */
		@Test
		@DisplayName("現状: File を値で渡すと、JSON でない中身もそのまま埋め込む")
		void 現状_embedsNonJsonFile () throws IOException {

			java.io.File src = java.io.File.createTempFile("json-embed", ".txt");
			src.deleteOnExit();
			java.nio.file.Files.writeString(src.toPath(), "not json", StandardCharsets.UTF_8);
			StringWriter sink = new StringWriter();
			BufferedWriter buffered = new BufferedWriter(sink);

			try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
				hash.write("raw", src);
			}
			buffered.flush();

			assertEquals("{\"raw\":not json}", sink.toString());

		}

	}

	/** 書く Bean（private / transient / static のフィールドを持つ） */
	public static class Bean {

		public int a = 1;

		private String b = "x";

		@SuppressWarnings("unused")
		private String secret = "hidden";

		transient int t = 9;

		public static int S = 5;

		public String getB () {
			return b;
		}

	}

	/** getter / setter の Bean */
	public static class Pojo {

		private String name;

		private int age;

		private boolean ok;

		public String getName () {
			return name;
		}

		public void setName (String name) {
			this.name = name;
		}

		public int getAge () {
			return age;
		}

		public void setAge (int age) {
			this.age = age;
		}

		public boolean isOk () {
			return ok;
		}

		public void setOk (boolean ok) {
			this.ok = ok;
		}

	}

	/** close されたかを覚える Writer */
	static final class ClosingWriter extends StringWriter {

		private boolean closed;

		@Override
		public void close () {
			closed = true;
		}

	}

	/** close されたかを覚える OutputStream */
	static final class ClosingStream extends java.io.ByteArrayOutputStream {

		private boolean closed;

		@Override
		public void close () {
			closed = true;
		}

	}

}
