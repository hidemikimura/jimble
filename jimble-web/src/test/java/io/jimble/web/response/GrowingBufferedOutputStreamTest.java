package io.jimble.web.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小さく始めて、足りなければ伸びるバッファ（D-200）
 */
class GrowingBufferedOutputStreamTest {

	/** 書かれた回数と大きさ、flush / close を覚える出力 */
	private static final class Recorder extends ByteArrayOutputStream {

		final List<Integer> writes = new ArrayList<>();
		int flushes = 0;
		boolean closed = false;

		@Override
		public synchronized void write (byte[] b, int off, int len) {
			writes.add(len);
			super.write(b, off, len);
		}

		@Override
		public synchronized void write (int b) {
			writes.add(1);
			super.write(b);
		}

		@Override
		public void flush () { flushes++; }

		@Override
		public void close () { closed = true; }

	}

	private static byte[] bytes (int n) {

		byte[] b = new byte[n];
		new Random(n).nextBytes(b);
		return b;

	}

	@Test
	@DisplayName("小さな中身は 8KiB のまま。閉じるまで下には書かない")
	void smallStaysSmall () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 256 * 1024);

		byte[] body = bytes(100);
		bos.write(body);

		assertEquals(8 * 1024, bos.capacity());
		assertEquals(0, out.writes.size(), "閉じる前に書き出しています");

		bos.close();

		assertArrayEquals(body, out.toByteArray());
		assertEquals(List.of(100), out.writes);
		assertTrue(out.closed, "下を閉じていません");

	}

	@Test
	@DisplayName("足りなければ倍にしていき、上限まで伸ばす。上限までは1回にまとめて書く")
	void growsUpToMax () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 256 * 1024);

		byte[] body = bytes(200 * 1024);
		for (int i = 0; i < body.length; i += 100) {
			bos.write(body, i, Math.min(100, body.length - i));
		}

		assertEquals(256 * 1024, bos.capacity());
		assertEquals(0, out.writes.size(), "上限に届く前に書き出しています");

		bos.close();

		assertArrayEquals(body, out.toByteArray());
		assertEquals(List.of(200 * 1024), out.writes);

	}

	@Test
	@DisplayName("上限を超える中身は、上限ごとに書き出す（上限より大きくは伸びない）")
	void flushesAtMax () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 64 * 1024);

		byte[] body = bytes(300 * 1024 + 7);
		for (int i = 0; i < body.length; i += 1000) {
			bos.write(body, i, Math.min(1000, body.length - i));
		}
		bos.close();

		assertEquals(64 * 1024, bos.capacity());
		assertArrayEquals(body, out.toByteArray());
		assertTrue(out.writes.stream().allMatch(n -> n <= 64 * 1024), out.writes.toString());
		assertTrue(out.writes.size() >= 5, out.writes.toString());

	}

	@Test
	@DisplayName("上限より大きな1回の書き込みは、溜めずにそのまま書く（順番は崩さない）")
	void largeWritePassesThrough () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 16 * 1024);

		byte[] head = bytes(10);
		byte[] big = bytes(40 * 1024);
		byte[] tail = bytes(20);

		bos.write(head);
		bos.write(big);
		bos.write(tail);
		bos.close();

		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(head);
		expected.write(big);
		expected.write(tail);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
		assertEquals(List.of(10, 40 * 1024, 20), out.writes);

	}

	@Test
	@DisplayName("1バイトずつ書いても、中身は変わらない")
	void singleBytes () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 32 * 1024);

		byte[] body = bytes(100 * 1024);
		for (byte b : body) {
			bos.write(b);
		}
		bos.close();

		assertArrayEquals(body, out.toByteArray());
		assertEquals(32 * 1024, bos.capacity());

	}

	@Test
	@DisplayName("上限が 8KiB より小さければ、上限の大きさで始める")
	void maxBelowInitial () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 1000);

		assertEquals(1000, bos.capacity());

		byte[] body = bytes(2500);
		bos.write(body);
		bos.close();

		assertArrayEquals(body, out.toByteArray());

	}

	@Test
	@DisplayName("flush は溜めたものを書き出し、下の flush も呼ぶ")
	void flushPropagates () throws IOException {

		Recorder out = new Recorder();
		GrowingBufferedOutputStream bos = new GrowingBufferedOutputStream(out, 256 * 1024);

		bos.write(bytes(10));
		bos.flush();

		assertEquals(List.of(10), out.writes);
		assertEquals(1, out.flushes);

	}

	@Test
	@DisplayName("上限が 0 以下なら断る")
	void rejectsNonPositive () {

		assertThrows(IllegalArgumentException.class, () -> new GrowingBufferedOutputStream(new Recorder(), 0));

	}

}
