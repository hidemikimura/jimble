package io.jimble.cli.internal;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import io.jimble.util.log.encoder.LogbackJsonEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jimble new の logback.xml（D-278）
 *
 * <p>
 * アクセスログを同期で書くと、それだけで秒あたりの本数が 3 割ほど落ちる（D-172）。
 * 雛形で別のスレッドに回し、しかも<b>黙って捨てない</b>設定にしてあることを固定する。
 * </p>
 */
class SkeletonLogbackTest {

	@Test
	@DisplayName("D-278 アクセスログは AsyncAppender を通り、捨てない（discardingThreshold = 0・neverBlock = false）")
	void accessLogIsAsyncAndLossless () throws Exception {

		LoggerContext context = new LoggerContext();

		try (InputStream in = SkeletonLogbackTest.class.getResourceAsStream("/io/jimble/cli/skeleton/logback.xml.txt")) {

			assertNotNull(in, "雛形の logback.xml がありません");

			String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("__NAME__", "memo");

			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		}

		for (String name : new String[] {"access", "access.bot"}) {

			Appender<?> appender = context.getLogger(name).iteratorForAppenders().next();
			AsyncAppender async = assertInstanceOf(AsyncAppender.class, appender, name + " が同期で書いています");

			assertEquals(0, async.getDiscardingThreshold(), name + "：溜まると INFO を捨てる設定です");
			assertFalse(async.isNeverBlock(), name + "：溜まりきると捨てる設定です");
			assertTrue(async.getQueueSize() >= 1024, name + "：キューが小さすぎます");

			Appender<?> json = async.getAppender("json");
			assertInstanceOf(ConsoleAppender.class, json);
			assertInstanceOf(LogbackJsonEncoder.class, ((OutputStreamAppender<?>) json).getEncoder());

		}

		// 例外のログは同期のまま（落ちる直前のものを取りこぼさない）
		assertFalse(context.getLogger("error").iteratorForAppenders().next() instanceof AsyncAppender);

		context.stop();

	}

}
