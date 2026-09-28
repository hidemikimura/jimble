package io.jimble.util.data;

import io.jimble.util.exception.CodeException;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jimble-util で黙って間違えていたところ（要件 D-190）
 */
class UtilTrapTest {

	private final List<String> errors = new ArrayList<>();

	@AfterEach
	void reset () {

		Log.resetSink();

	}

	private void capture () {

		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level.toInt() >= org.slf4j.event.Level.WARN.toInt()) {
				errors.add(level + " " + message + " " + data);
			}
		});

	}

	/** 書くと落ちる書き先（書き先の失敗＝相手が切った、に当たる） */
	private static final java.io.OutputStream BROKEN_STREAM = new java.io.OutputStream() {
		@Override
		public void write (int b) throws IOException {
			throw new IOException("Broken pipe");
		}
	};

	/** 書くと枠組みの外の例外で落ちる書き先（書き出しそのものの失敗に当たる） */
	private static final java.io.Writer EXPLODING_WRITER = new java.io.Writer() {
		@Override
		public void write (char[] buf, int off, int len) {
			throw new IllegalStateException("boom");
		}
		@Override
		public void flush () {}
		@Override
		public void close () {}
	};

	@Test
	@DisplayName("D-190 getObjectListOptional が要素の型を変換する（1.4 までは types を捨てていた）")
	void objectListOptionalConvertsElements () {

		Data data = Data.fromJsonString("{\"ids\":[\"1\",\"2\"]}");
		List<Long> ids = data.getObjectListOptional("ids", Long.class);

		assertEquals(List.of(1L, 2L), ids);
		assertEquals(Long.class, ids.getFirst().getClass());

	}

	@Test
	@DisplayName("D-190 流す版の Dson.encodes も失敗をログに出す（1.4 までは誰も読まなかった）")
	void streamEncodeReportsFailure () {

		capture();

		Dson.encodes(Data.fromJsonString("{\"a\":1}"), EXPLODING_WRITER);
		assertTrue(errors.stream().anyMatch(m -> m.startsWith("ERROR") && m.contains("JSON に書き出せませんでした")), errors.toString());

		// 書き先の失敗は警告に留める（相手が切るのは珍しくない）
		errors.clear();
		Dson.encodes(Data.fromJsonString("{\"a\":1}"), BROKEN_STREAM, "UTF-8");
		assertTrue(errors.stream().anyMatch(m -> m.startsWith("WARN") && m.contains("書き先が失敗")), errors.toString());

		// 成功したら何も言わない
		errors.clear();
		Dson.encodes(Data.fromJsonString("{\"a\":1}"), new ByteArrayOutputStream(), "UTF-8");
		Dson.encodes(Data.fromJsonString("{\"a\":1}"), new StringWriter());
		assertEquals(List.of(), errors);

	}

	@Test
	@DisplayName("D-190 CodeException(Exception) が元の例外を cause に持つ")
	void codeExceptionKeepsCause () {

		IOException io = new IOException("disk");
		assertSame(io, new CodeException(io).getCause());
		assertSame(io, new CodeException("X_001", "包んだ", io).getCause());

	}

}
