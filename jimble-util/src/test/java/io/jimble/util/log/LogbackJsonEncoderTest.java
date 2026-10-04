package io.jimble.util.log;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.jimble.util.data.Data;
import io.jimble.util.log.encoder.LogbackJsonEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1行に1つの JSON（アクセスログ）
 */
class LogbackJsonEncoderTest {

	@Test
	@DisplayName("1行の JSON にする。引数の Data は行に混ぜる。改行で終わる")
	void encodesOneLine () {

		LoggingEvent event = new LoggingEvent();
		event.setLoggerContext(new LoggerContext());
		event.setLoggerName("access");
		event.setLevel(Level.INFO);
		event.setThreadName("test");
		event.setTimeStamp(0L);
		event.setMessage("GET /");
		Data data = new Data();
		data.put("status", 200);
		data.put("path", "/請求");
		event.setArgumentArray(new Object[] {data});

		String line = new String(new LogbackJsonEncoder().encode(event), StandardCharsets.UTF_8);

		assertTrue(line.endsWith("}\n"), line);
		assertEquals(1, line.split("\n").length, line);

		Data parsed = Data.fromJsonString(line.trim());
		assertEquals("GET /", parsed.getString("message"));
		assertEquals("access", parsed.getString("logger_name"));
		assertEquals(200, parsed.getInt("status"));
		assertEquals("/請求", parsed.getString("path"));
		assertTrue(parsed.getString("@timestamp").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), parsed.getString("@timestamp"));

	}

	@Test
	@DisplayName("AsyncAppender を通しても、捨てずに全部書き、閉じると溜まった行を書き出す（雛形の設定と同じ形）")
	void asyncDeliversEverything () {

		LoggerContext context = new LoggerContext();
		// 本物の設定では logback が入れる（テストで作ると無い）
		context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
		List<String> lines = new CopyOnWriteArrayList<>();

		AppenderBase<ILoggingEvent> sink = new AppenderBase<>() {
			private final LogbackJsonEncoder encoder = new LogbackJsonEncoder();
			@Override
			protected void append (ILoggingEvent event) {
				// 書き出しが遅い出し先のつもり
				Thread.onSpinWait();
				lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
			}
		};
		sink.setContext(context);
		sink.start();

		AsyncAppender async = new AsyncAppender();
		async.setContext(context);
		async.setQueueSize(64);
		async.setDiscardingThreshold(0);
		async.setNeverBlock(false);
		async.addAppender(sink);
		async.start();

		Logger access = context.getLogger("access");
		access.setAdditive(false);
		access.addAppender(async);

		for (int i = 0; i < 1000; i++) {
			Data data = new Data();
			data.put("n", i);
			access.info("access", data);
		}

		context.stop();

		assertEquals(1000, lines.size(), "アクセスログが捨てられた");
		assertEquals(999, Data.fromJsonString(lines.get(999).trim()).getInt("n"));

	}

}
