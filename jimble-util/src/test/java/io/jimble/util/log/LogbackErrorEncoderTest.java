package io.jimble.util.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.jimble.util.data.Data;
import io.jimble.util.log.encoder.LogbackErrorEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 例外のログの見た目（要件 D-84）
 *
 * <p>
 * 例外を出すところで<b>いちばん要る情報が落ちていた</b>。
 * 呼んだ側が書いた説明（「保存できませんでした」）と、
 * どのリクエストの話なのか（実行 ID）である。
 * </p>
 */
class LogbackErrorEncoderTest {

	/**
	 * ログのイベントを組み立てる
	 *
	 * @param data	ログのデータ
	 * @return	イベント
	 */
	private static LoggingEvent event (Data data) {

		LoggerContext context = new LoggerContext();

		LoggingEvent event = new LoggingEvent();
		event.setLoggerContext(context);
		event.setLoggerName("error");
		event.setLevel(Level.ERROR);
		event.setThreadName("test");
		event.setTimeStamp(System.currentTimeMillis());
		event.setMessage("わざと失敗");
		event.setArgumentArray(new Object[]{ data });

		return event;

	}

	/**
	 * 出力を文字列で取る
	 *
	 * @param data	ログのデータ
	 * @return	出力
	 */
	private static String encode (Data data) {

		return new String(new LogbackErrorEncoder().encode(event(data)), StandardCharsets.UTF_8);

	}

	@Test
	@DisplayName("D-84 呼んだ側が書いた説明が消えない")
	void keepsExplanation () {

		Data data = new Data();
		data.put("throwable", new IllegalStateException("わざと失敗"));
		data.put("objects", List.of("保存できませんでした: id=3"));

		String out = encode(data);

		assertTrue(out.contains("保存できませんでした: id=3"), out);
		assertTrue(out.contains("java.lang.IllegalStateException: わざと失敗"), out);

	}

	@Test
	@DisplayName("D-84 実行 ID が入る（アクセスログと突き合わせるため）")
	void keepsRequestId () {

		Data data = new Data();
		data.put("throwable", new IllegalStateException("わざと失敗"));
		data.put("request_id", "abc-def-1");

		assertTrue(encode(data).contains("[abc-def-1]"), encode(data));

	}

	@Test
	@DisplayName("D-84 スコープの外（request_id が -）では出さない")
	void skipsEmptyRequestId () {

		Data data = new Data();
		data.put("throwable", new IllegalStateException("わざと失敗"));
		data.put("request_id", "-");

		assertFalse(encode(data).contains("[-]"), encode(data));

	}

	@Test
	@DisplayName("D-84 メッセージの無い例外でもログが消えない")
	void handlesNullMessage () {

		Data data = new Data();
		data.put("throwable", new NullPointerException());

		String out = encode(data);

		/*
		 * 前は throwable.getMessage() を素で使っていたので、
		 * ここで NullPointerException が起きて encode が空を返していた。
		 * つまり「落ちたことだけが分からない」形になっていた。
		 */
		assertFalse(out.isEmpty(), "ログの行が消えている");
		assertTrue(out.contains("java.lang.NullPointerException"), out);

	}

	@Test
	@DisplayName("D-84 原因の例外にメッセージが無くてもログが消えない")
	void handlesNullMessageInCause () {

		Data data = new Data();
		data.put("throwable", new IllegalStateException("外側", new NullPointerException()));

		String out = encode(data);

		assertFalse(out.isEmpty(), "ログの行が消えている");
		assertTrue(out.contains("Caused by: java.lang.NullPointerException"), out);

	}

}
