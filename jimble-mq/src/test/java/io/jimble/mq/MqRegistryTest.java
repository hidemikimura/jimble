package io.jimble.mq;

import io.jimble.db.DB;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MQ Executor の登録（要件 F-M-07 / F-M-08）
 */
class MqRegistryTest {

	@AfterEach
	void clear () {

		MqRegistry.clear();

	}

	/**
	 * テスト用の Executor
	 */
	static class AExecutor extends MqExecutor {

		@Override public String queueName () { return "mq_test"; }
		@Override public String key () { return "a"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }
		@Override public MqStatus execute (DB db, Data row) { return MqStatus.completed; }

	}

	/**
	 * 種別の違う Executor
	 */
	static class BExecutor extends MqExecutor {

		@Override public String queueName () { return "mq_test"; }
		@Override public String key () { return "b"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.long_time; }
		@Override public MqStatus execute (DB db, Data row) { return MqStatus.completed; }

	}

	/**
	 * キーが空の Executor
	 */
	static class NoKeyExecutor extends MqExecutor {

		@Override public String queueName () { return "mq_test"; }
		@Override public String key () { return ""; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }
		@Override public MqStatus execute (DB db, Data row) { return MqStatus.completed; }

	}

	@Test
	@DisplayName("明示登録したものを引ける")
	void addAndCreate () {

		MqRegistry.add(AExecutor::new);

		assertNotNull(MqRegistry.create("mq_test", "a"));
		assertNull(MqRegistry.create("mq_test", "nothing"));
		assertNull(MqRegistry.create("mq_other", "a"));

	}

	@Test
	@DisplayName("毎回あたらしいインスタンスを返す")
	void createsNewInstance () {

		/*
		 * 移送元は1つ作って使い回し、メッセージごとに
		 * setCancelOrderNotify() でその共有インスタンスを書き換えていた。
		 * Executor にフィールドを持たせると別のメッセージと混ざる。
		 */
		MqRegistry.add(AExecutor::new);

		MqExecutor first = MqRegistry.create("mq_test", "a");
		MqExecutor second = MqRegistry.create("mq_test", "a");

		assertTrue(first != second, "同じインスタンスが返っている");

	}

	@Test
	@DisplayName("二重登録は例外")
	void duplicate () {

		MqRegistry.add(AExecutor::new);

		assertThrows(IllegalStateException.class, () -> MqRegistry.add(AExecutor::new));

	}

	@Test
	@DisplayName("キーが空なら例外")
	void emptyKey () {

		assertThrows(IllegalArgumentException.class, () -> MqRegistry.add(NoKeyExecutor::new));

	}

	@Test
	@DisplayName("使われている実行種別だけを返す")
	void executeTypes () {

		MqRegistry.add(AExecutor::new);
		MqRegistry.add(BExecutor::new);

		List<MqExecuteType> types = MqRegistry.executeTypes("mq_test");

		assertEquals(2, types.size());
		assertTrue(types.contains(MqExecuteType.short_time));
		assertTrue(types.contains(MqExecuteType.long_time));
		assertTrue(MqRegistry.executeTypes("mq_other").isEmpty());

	}

	@Test
	@DisplayName("スレッド数は設定で変えられる（要件 F-M-08）")
	void threadCount () {

		// application.dbtest.conf で short_time = 2
		assertTrue(MqConf.threadCount(MqExecuteType.short_time) >= 1);
		assertEquals(MqExecuteType.long_time.defaultThreadCount()
			, MqConf.threadCount(MqExecuteType.long_time), "設定が無ければ既定値");

	}

	@Test
	@DisplayName("リトライの間隔は回を追うごとに伸び、上限で止まる")
	void backoff () {

		long base = MqConf.retryBackoffSeconds();
		long max = MqConf.retryBackoffMaxSeconds();

		assertEquals(Math.min(base, max), MqConf.backoffSeconds(1));
		assertTrue(MqConf.backoffSeconds(2) >= MqConf.backoffSeconds(1));
		assertEquals(max, MqConf.backoffSeconds(100), "上限で止まっていない");

	}

}
