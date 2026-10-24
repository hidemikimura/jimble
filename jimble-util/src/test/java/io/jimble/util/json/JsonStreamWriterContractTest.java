package io.jimble.util.json;

import io.jimble.util.data.Data;
import io.jimble.util.internal.json.stream.writer.JsonArrayWriter;
import io.jimble.util.internal.json.stream.writer.JsonHashWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 流して書く JsonHashWriter / JsonArrayWriter で守る約束（作り直しのあとも同じでなければならないもの）
 *
 * <p>今の挙動のうち、作り直しで変える予定のものは {@link DsonCurrentQuirksTest} にある。</p>
 */
class JsonStreamWriterContractTest {

	/** 書き先 */
	private final StringWriter sink = new StringWriter();

	/** 書き先を包んだもの（JsonHashWriter / JsonArrayWriter に渡す） */
	private final BufferedWriter buffered = new BufferedWriter(sink);

	/**
	 * 書いたものを取り出す
	 *
	 * @return	書いた JSON
	 * @throws IOException	書き出せなかったとき
	 */
	private String written () throws IOException {

		buffered.flush();
		return sink.toString();

	}

	@Test
	@DisplayName("JsonHashWriter はキーを書いた順に並べる")
	void hashKeepsOrder () throws IOException {

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			hash.write("z", 1);
			hash.write("a", 2);
		}

		assertEquals("{\"z\":1,\"a\":2}", written());

	}

	@Test
	@DisplayName("JsonHashWriter は null の値を null と書く")
	void hashNull () throws IOException {

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			hash.write("a", null);
		}

		assertEquals("{\"a\":null}", written());

	}

	@Test
	@DisplayName("JsonHashWriter のキーと文字列の値をエスケープする（/ は \\/）")
	void hashEscapes () throws IOException {

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			hash.write("c/\"", "x/y");
		}

		assertEquals("{\"c\\/\\\"\":\"x\\/y\"}", written());

	}

	@Test
	@DisplayName("何も書かずに閉じれば {}")
	void hashEmpty () throws IOException {

		new JsonHashWriter(buffered).close();

		assertEquals("{}", written());

	}

	@Test
	@DisplayName("JsonHashWriter に Data を渡すと、入れ子のオブジェクトとして書く")
	void hashData () throws IOException {

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			hash.write("d", new Data().putData("q", 1).putData("r", List.of("s")));
		}

		assertEquals("{\"d\":{\"q\":1,\"r\":[\"s\"]}}", written());

	}

	@Test
	@DisplayName("hash(key) / array(key) の子は、次に書くときか親を閉じるときに閉じる")
	void hashChildrenClosedByParent () throws IOException {

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			JsonArrayWriter array = hash.array("arr");
			array.write(1);
			hash.write("x", 2);
			JsonHashWriter child = hash.hash("h");
			child.write("k", "v");
		}

		assertEquals("{\"arr\":[1],\"x\":2,\"h\":{\"k\":\"v\"}}", written());

	}

	@Test
	@DisplayName("JsonArrayWriter は配列・Iterable を1要素ずつ平らにして書く")
	void arrayFlattens () throws IOException {

		try (JsonArrayWriter array = new JsonArrayWriter(buffered)) {
			array.write(1);
			array.write(new int[]{2, 3});
			array.write(List.of(4));
			array.write((Object) null);
		}

		assertEquals("[1,2,3,4,null]", written());

	}

	@Test
	@DisplayName("JsonArrayWriter に空の Iterable を渡しても何も書かない")
	void arrayEmptyIterable () throws IOException {

		try (JsonArrayWriter array = new JsonArrayWriter(buffered)) {
			array.write(List.of());
			array.write(1);
		}

		assertEquals("[1]", written());

	}

	@Test
	@DisplayName("JsonArrayWriter は文字列・Data・入れ子の配列を書ける")
	void arrayMixed () throws IOException {

		try (JsonArrayWriter array = new JsonArrayWriter(buffered)) {
			array.write("s");
			array.write(new Data().putData("a", 1));
			JsonArrayWriter inner = array.array();
			inner.write(1);
			JsonHashWriter hash = array.hash();
			hash.write("k", "v");
			array.write(2);
		}

		assertEquals("[\"s\",{\"a\":1},[1],{\"k\":\"v\"},2]", written());

	}

	@Test
	@DisplayName("何も書かずに閉じれば []")
	void arrayEmpty () throws IOException {

		new JsonArrayWriter(buffered).close();

		assertEquals("[]", written());

	}

	@Test
	@DisplayName("書き出し中に Writer を閉じない（BufferedWriter に続けて書ける）")
	void doesNotCloseUnderlyingWriter () throws IOException {

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			hash.write("a", new Data().putData("b", 1));
			hash.write("c", 2);
		}
		buffered.write("\n");

		assertEquals("{\"a\":{\"b\":1},\"c\":2}\n", written());

	}

	@Test
	@DisplayName("ファイルを渡して作ると UTF-8 で書き、閉じたときにファイルを閉じる")
	void hashToFile () throws IOException {

		File file = File.createTempFile("json-hash", ".json");
		file.deleteOnExit();

		try (JsonHashWriter hash = new JsonHashWriter(file)) {
			hash.write("a", 1);
			hash.write("b", "あ");
		}

		assertEquals("{\"a\":1,\"b\":\"あ\"}", Files.readString(file.toPath(), StandardCharsets.UTF_8));

	}

	@Test
	@DisplayName("JsonArrayWriter もファイルを渡して作れる")
	void arrayToFile () throws IOException {

		File file = File.createTempFile("json-array", ".json");
		file.deleteOnExit();

		try (JsonArrayWriter array = new JsonArrayWriter(file)) {
			array.write("あ");
			array.write(1);
		}

		assertEquals("[\"あ\",1]", Files.readString(file.toPath(), StandardCharsets.UTF_8));

	}

	@Test
	@DisplayName("JsonHashWriter に File を値で渡すと、ファイルの中身をそのまま値として埋め込む")
	void hashEmbedsFile () throws IOException {

		File src = File.createTempFile("json-embed", ".json");
		src.deleteOnExit();
		Files.writeString(src.toPath(), "{\"x\":[1,2]}", StandardCharsets.UTF_8);

		try (JsonHashWriter hash = new JsonHashWriter(buffered)) {
			hash.write("raw", src);
		}

		assertEquals("{\"raw\":{\"x\":[1,2]}}", written());

	}

}
