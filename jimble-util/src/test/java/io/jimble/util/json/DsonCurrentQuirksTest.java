package io.jimble.util.json;

import io.jimble.util.data.Data;
import io.jimble.util.internal.json.stream.writer.JsonHashWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	@DisplayName("現状: 読み先の型との食い違い")
	class TargetMismatch {

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
