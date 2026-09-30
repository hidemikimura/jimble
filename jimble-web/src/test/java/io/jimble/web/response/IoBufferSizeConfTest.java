package io.jimble.web.response;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.util.internal.WarnOnce;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code jimble.io.buffer_size} を単位つきで読む
 *
 * <p>
 * ドキュメント（config.md）は {@code io.buffer_size = 256KiB} と書く例なのに、コードは {@code getInt} で読んでいた。
 * <b>書いてあるとおりに設定すると、ファイルを送るたびに 500</b> になっていた。
 * 素の数値（{@code getInt} で動いていた書き方）は、バイトとして受け続ける。
 * </p>
 */
class IoBufferSizeConfTest {

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

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	足す設定
	 */
	private void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon).withFallback(originalConf));

	}

	@Test
	@DisplayName("ドキュメントどおり 256KiB と書いて読める")
	void documentedForm () {

		conf("jimble { io.buffer_size = 256KiB }");

		assertEquals(256 * 1024, Response.configuredIoBufferSize());
		assertFalse(WarnOnce.warned(Response.KEY_IO_BUFFER_SIZE), "単位を書いたのに警告しています");

	}

	@Test
	@DisplayName("ほかの単位も読める（1MiB / 64kB）")
	void otherUnits () {

		conf("jimble.io.buffer_size = 1MiB");
		assertEquals(1024 * 1024, Response.configuredIoBufferSize());

		conf("jimble.io.buffer_size = 64kB");
		assertEquals(64_000, Response.configuredIoBufferSize());

	}

	@Test
	@DisplayName("書かなければ 256KiB")
	void defaultValue () {

		assertEquals(Response.DEFAULT_IO_BUFFER_SIZE, Response.configuredIoBufferSize());

	}

	@Test
	@DisplayName("素の数値はバイトとして読み、単位を書くよう1度だけ警告する（これまで動いていた書き方）")
	void bareNumberStillWorks () {

		conf("jimble.io.buffer_size = 65536");

		assertEquals(65536, Response.configuredIoBufferSize());
		assertTrue(WarnOnce.warned(Response.KEY_IO_BUFFER_SIZE), "単位が無いのに警告していません");

	}

	@Test
	@DisplayName("0 や 2GiB 以上は、直し方つきで落とす")
	void outOfRange () {

		conf("jimble.io.buffer_size = 0KiB");
		IllegalStateException zero = assertThrows(IllegalStateException.class, Response::configuredIoBufferSize);
		assertTrue(zero.getMessage().contains("jimble.io.buffer_size"), zero.getMessage());

		conf("jimble.io.buffer_size = 3GiB");
		assertThrows(IllegalStateException.class, Response::configuredIoBufferSize);

	}

	@Test
	@DisplayName("256KiB と書いたまま、ファイルを送れる（報告の形）")
	void sendFileWithDocumentedForm (@TempDir Path dir) throws Exception {

		conf("jimble { io.buffer_size = 256KiB }");

		Path file = dir.resolve("a.txt");
		Files.writeString(file, "hello");

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/file"), sink);
		context.response().send(file.toFile(), "text/plain");

		assertTrue(sink.isSent());
		assertEquals(200, sink.status());

	}

}
