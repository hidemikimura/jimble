package io.jimble.db.migration;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration.install() を DBUtil.load のあとに呼んだら言う（要件 D-189）
 *
 * <p>
 * 登録した処理は load の中で走るので、あとから呼んでも<b>黙って1つも流れない</b>。
 * 投げない（別に migrate しているアプリを起動できなくしない）が、言う。
 * </p>
 */
@Tag("db")
class MigrationInstallOrderTest {

	private final List<String> warns = new CopyOnWriteArrayList<>();

	@AfterEach
	void tearDown () {

		Log.resetSink();
		DBUtil.stop();

	}

	private void capture () {

		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level.toInt() >= org.slf4j.event.Level.WARN.toInt()) {
				warns.add(message);
			}
		});

	}

	@Test
	@DisplayName("D-189 load のあとに呼ぶと言う。前に呼べば言わない")
	void warnsAfterLoad () {

		Conf.reload();
		capture();

		Migration.install();
		assertTrue(warns.stream().noneMatch(message -> message.contains("DBUtil.load(...) のあとに")), warns.toString());

		assertTrue(DBUtil.load(Conf.conf().config(), MigrationInstallOrderTest.class), "DB に接続できませんでした");

		Migration.install();

		assertEquals(1, warns.stream().filter(message -> message.contains("DBUtil.load(...) のあとに")).count(), warns.toString());
		assertTrue(warns.stream().anyMatch(message -> message.contains("codegen.md")), warns.toString());

	}

}
