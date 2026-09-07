package io.jimble.web.sse;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE の1件の組み立て（要件 F-W-21）
 */
class SseEventTest {

	@Test
	@DisplayName("本文だけなら data 1行と空行")
	void dataOnly () {

		assertEquals("data: hello\n\n", SseEvent.of("hello").format());

	}

	@Test
	@DisplayName("種別・識別子・再接続まで出る")
	void allFields () {

		String text = new SseEvent("progress", "{\"n\":1}", "42", 3000, null).format();

		assertEquals("""
			id: 42
			event: progress
			retry: 3000
			data: {"n":1}

			""", text);

	}

	@Test
	@DisplayName("本文の改行は行ごとに data: を付ける")
	void multiline () {

		/*
		 * ここを手を抜くと、本文に改行があるだけで壊れる。
		 * 改行は「次のフィールド」の合図なので、
		 * そのまま流すと2件目・3件目として読まれる。
		 */
		assertEquals("data: 1行目\ndata: 2行目\n\n", SseEvent.of("1行目\n2行目").format());

		// \r\n も \r も1つの改行として扱う
		assertEquals("data: a\ndata: b\n\n", SseEvent.of("a\r\nb").format());
		assertEquals("data: a\ndata: b\n\n", SseEvent.of("a\rb").format());

	}

	@Test
	@DisplayName("種別と識別子の改行は落とす")
	void noNewlineInFields () {

		String text = new SseEvent("a\nb", "x", "1\n2", 0, null).format();

		assertTrue(text.contains("event: a b"), text);
		assertTrue(text.contains("id: 1 2"), text);

	}

	@Test
	@DisplayName("キープアライブはコメント1行")
	void keepAlive () {

		// ":" だけの行。受け取り側は読み飛ばす
		assertEquals(":\n\n", SseEvent.KEEP_ALIVE.format());

	}

	@Test
	@DisplayName("JSON で送れる")
	void json () {

		String text = SseEvent.json("tick", new Data().putData("n", 1)).format();

		assertTrue(text.startsWith("event: tick\n"), text);
		assertTrue(text.contains("data: {\"n\":1}"), text);

	}

	@Test
	@DisplayName("空の本文でも1件として成立する")
	void emptyData () {

		assertEquals("data:\n\n", SseEvent.of("").format());

	}

}
