package io.jimble.web.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 写すときの配列の大きさは、渡された {@code io.buffer_size}（D-200）
 */
class ResponseSinkCopyTest {

	/** 1回に読もうとした最大の大きさを覚える入力 */
	private static final class Recorder extends ByteArrayInputStream {

		int maxAsked = 0;

		Recorder (byte[] buf) { super(buf); }

		@Override
		public synchronized int read (byte[] b, int off, int len) {
			maxAsked = Math.max(maxAsked, len);
			return super.read(b, off, len);
		}

	}

	private static byte[] bytes (int n) {

		byte[] b = new byte[n];
		new Random(n).nextBytes(b);
		return b;

	}

	@Test
	@DisplayName("渡された大きさで読み、中身は欠けずに写る")
	void usesGivenSize () throws IOException {

		byte[] body = bytes(1024 * 1024 + 3);
		Recorder in = new Recorder(body);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		HelidonResponseSink.copy(in, out, 64 * 1024);

		assertEquals(64 * 1024, in.maxAsked);
		assertArrayEquals(body, out.toByteArray());

	}

	@Test
	@DisplayName("大きさが 0 以下なら 16KiB で読む")
	void fallback () throws IOException {

		Recorder in = new Recorder(bytes(100 * 1024));

		HelidonResponseSink.copy(in, new ByteArrayOutputStream(), 0);

		assertEquals(16 * 1024, in.maxAsked);

	}

}
