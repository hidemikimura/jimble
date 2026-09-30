package io.jimble.web.response;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.internal.WarnOnce;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code io.buffer_size} の使われ方（D-200）
 *
 * <ul>
 * 	<li>ファイル・ストリームは、<b>読む側に BufferedInputStream を挟まず</b>、写す配列の大きさとして sink に渡す</li>
 * 	<li>{@code ioBufferSize(n)} で決めた大きさが、設定より優先される</li>
 * 	<li>JSON / JSONL は、伸びるバッファを通しても中身が変わらない</li>
 * </ul>
 */
class IoBufferSizeUseTest {

	/* 元の設定 */
	private Config originalConf;

	@BeforeEach
	void keep () {

		Conf.reload();
		originalConf = Conf.conf().config();
		WarnOnce.reset();

	}

	@AfterEach
	void restore () {

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

		WarnOnce.reset();

	}

	private static Fakes.FakeResponseSink sink () {

		return new Fakes.FakeResponseSink();

	}

	private static Response response (Fakes.FakeResponseSink sink) {

		return new WebContext(new Fakes.FakeRequestSource("GET", "/x"), sink).response();

	}

	@Test
	@DisplayName("ファイルは BufferedInputStream を挟まず、io.buffer_size を写す大きさとして渡す")
	void fileUsesBufferSize (@TempDir Path dir) throws Exception {

		Conf.replace(ConfigFactory.parseString("jimble.io.buffer_size = 64KiB").withFallback(originalConf));

		Path file = dir.resolve("a.txt");
		Files.writeString(file, "hello");

		Fakes.FakeResponseSink sink = sink();
		response(sink).send(file.toFile(), "text/plain");

		assertEquals("hello", sink.body());
		assertEquals(64 * 1024, sink.streamBufferSize());
		assertFalse(sink.sentStream() instanceof BufferedInputStream, "読む側にバッファを挟んでいます");
		assertEquals("5", sink.headers().get("Content-Length"));

	}

	@Test
	@DisplayName("ストリームも BufferedInputStream を挟まず、ioBufferSize(n) の大きさを渡す")
	void streamUsesOverride () {

		Fakes.FakeResponseSink sink = sink();
		ByteArrayInputStream in = new ByteArrayInputStream("abc".getBytes());

		response(sink).ioBufferSize(12345).send(in, "text/plain");

		assertEquals("abc", sink.body());
		assertEquals(12345, sink.streamBufferSize());
		assertTrue(sink.sentStream() == in, "渡したストリームをそのまま送っていません");

	}

	@Test
	@DisplayName("書かなければ既定の大きさを渡す")
	void defaultSize () {

		Fakes.FakeResponseSink sink = sink();
		response(sink).send(new ByteArrayInputStream("abc".getBytes()), "text/plain");

		assertEquals(Response.DEFAULT_IO_BUFFER_SIZE, sink.streamBufferSize());

	}

	/**
	 * 大きな JSON（伸びるバッファを何度も伸ばし、上限で書き出す大きさ）
	 *
	 * @return	Data
	 */
	private static Data bigData () {

		Data data = new Data();
		for (int i = 0; i < 5000; i++) {
			data.put("key" + i, "値".repeat(i % 50) + i);
		}
		return data;

	}

	@Test
	@DisplayName("大きな JSON も、小さな上限で書いても中身は変わらない")
	void jsonSameContent () {

		Data data = bigData();

		Fakes.FakeResponseSink big = sink();
		response(big).ioBufferSize(1024 * 1024).send(data);

		Fakes.FakeResponseSink small = sink();
		response(small).ioBufferSize(1000).send(data);

		assertTrue(big.body().length() > 100_000, "JSON が小さすぎて上限まで届きません: " + big.body().length());
		assertEquals(big.body(), small.body());
		assertTrue(big.body().startsWith("{") && big.body().endsWith("}"), big.body().substring(0, 50));

	}

	@Test
	@DisplayName("JSONL も、小さな上限で書いても中身は変わらない")
	void jsonlSameContent () {

		List<Data> rows = List.of(bigData(), bigData(), bigData());

		Fakes.FakeResponseSink big = sink();
		response(big).ioBufferSize(1024 * 1024).send(rows);

		Fakes.FakeResponseSink small = sink();
		response(small).ioBufferSize(1000).send(rows);

		assertEquals(big.body(), small.body());
		assertEquals(3, big.body().split("\n").length);

	}

}
