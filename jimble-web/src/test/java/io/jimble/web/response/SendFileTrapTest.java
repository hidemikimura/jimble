package io.jimble.web.response;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 無いファイルを送ろうとしたら 404（要件 D-190）
 *
 * <p>
 * 1.4 までは<b>状態コードと Content-Length を先に送り、開けなかった例外を握りつぶしていた</b>——
 * 200 の空の応答になり、ログにも何も出なかった。
 * </p>
 */
class SendFileTrapTest {

	@Test
	@DisplayName("D-190 無いファイルは 404 の HttpException で、何も送らない")
	void missingFileIs404 (@TempDir Path dir) {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/file"), sink);
		File missing = dir.resolve("nope.txt").toFile();

		HttpException e = assertThrows(HttpException.class,
			() -> context.response().send(missing, "text/plain"));
		assertEquals(404, e.statusCode());
		assertTrue(e.getMessage().contains("nope.txt"), e.getMessage());
		assertFalse(sink.isSent(), "送り始めています");

	}

	@Test
	@DisplayName("D-190 ディレクトリも 404")
	void directoryIs404 (@TempDir Path dir) {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/file"), sink);
		assertThrows(HttpException.class, () -> context.response().send(dir.toFile(), "text/plain"));

	}

	@Test
	@DisplayName("D-190 あるファイルはこれまでどおり送る")
	void existingFileIsSent (@TempDir Path dir) throws Exception {

		Path file = dir.resolve("a.txt");
		Files.writeString(file, "hello");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/file"), sink);
		context.response().send(file.toFile(), "text/plain");

		assertTrue(sink.isSent());
		assertEquals(200, sink.status());

	}

}
