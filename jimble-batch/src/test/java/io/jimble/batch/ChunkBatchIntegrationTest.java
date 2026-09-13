package io.jimble.batch;

import com.typesafe.config.ConfigValueFactory;
import io.jimble.batch.status.BatchHistoryStatus;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * チャンクバッチが実 DB に対して動くことの確認（要件 F-B-12）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-batch:dbTest
 * ./gradlew :jimble-batch:pgTest
 * </pre>
 */
@Tag("db")
class ChunkBatchIntegrationTest {

	/* 何チャンク目で落とすか（0 なら落とさない） */
	static volatile int failAtChunk = 0;

	/* よける id */
	static volatile java.util.Set<Long> filterIds = java.util.Set.of();

	/* write が呼ばれた回数と、そのときの件数 */
	static final List<Integer> WRITE_SIZES = java.util.Collections.synchronizedList(new ArrayList<>());

	/* reader / write に渡された DB */
	static final AtomicReference<DB> READ_DB = new AtomicReference<>();
	static final AtomicReference<DB> WRITE_DB = new AtomicReference<>();

	/* 1チャンク書いたら待たせるラッチ */
	static volatile CountDownLatch hold = null;

	/* 1チャンク書いたことを知らせるラッチ */
	static volatile CountDownLatch wrote = null;

	/* 何チャンク目から待たせるか */
	static volatile int holdFromChunk = 1;

	// region テスト用のバッチ

	/**
	 * chunk_src を読んで chunk_dst に写すバッチ
	 */
	public static class CopyBatch extends AbstractChunkBatch<Data> {

		@Override public String batchName () { return "テスト（チャンク）"; }

		@Override public int chunkSize () { return 3; }

		@Override
		protected Iterator<Data> reader (BatchArgs args, DB db) {

			READ_DB.set(db);

			return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
					SELECT id, name FROM chunk_src WHERE id > ? ORDER BY id ASC LIMIT ?
				""", lastKey, limit));

		}

		@Override
		protected Data process (Data item) {

			if (filterIds.contains(item.getLong("id"))) {
				return null;
			}

			return item;

		}

		@Override
		protected void write (List<Data> items, DB db) {

			WRITE_DB.set(db);
			WRITE_SIZES.add(items.size());

			for (Data item : items) {
				db.insert("INSERT INTO chunk_dst (id, name) VALUES (?, ?)"
					, item.getLong("id"), item.getString("name"));
			}

			/*
			 * <b>書いた後で落とす。</b>
			 * 書く前に落としたのでは、トランザクションで囲っていなくても
			 * 「そのチャンクは入っていない」になってしまい、
			 * ロールバックが効いているかを確かめられない。
			 */
			if (failAtChunk > 0 && WRITE_SIZES.size() == failAtChunk) {
				throw new IllegalStateException("わざと落とす");
			}

			if (wrote != null) {
				wrote.countDown();
			}

			if (hold != null && WRITE_SIZES.size() >= holdFromChunk) {
				try {
					hold.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}

		}

	}

	/**
	 * 中断されるまで写し続けるバッチ（1チャンクごとに少し待つ）
	 */
	public static class SlowCopyBatch extends CopyBatch {

		@Override public String batchName () { return "テスト（チャンク・遅い）"; }

		@Override
		protected void write (List<Data> items, DB db) {

			super.write(items, db);

			try {
				Thread.sleep(60);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

		}

	}

	/**
	 * chunkSize が 0 のバッチ
	 */
	public static class ZeroChunkBatch extends AbstractChunkBatch<Data> {

		@Override public String batchName () { return "テスト（チャンク 0）"; }

		@Override public int chunkSize () { return 0; }

		@Override protected Iterator<Data> reader (BatchArgs args, DB db) { return List.<Data>of().iterator(); }

		@Override protected void write (List<Data> items, DB db) {}

	}

	/**
	 * reader が null を返すバッチ
	 */
	public static class NullReaderBatch extends AbstractChunkBatch<Data> {

		@Override public String batchName () { return "テスト（reader が null）"; }

		@Override protected Iterator<Data> reader (BatchArgs args, DB db) { return null; }

		@Override protected void write (List<Data> items, DB db) {}

	}

	/**
	 * 例外を投げずに SQL だけ失敗させるバッチ
	 *
	 * <p>
	 * {@code db.insert()} は失敗しても例外を投げない。
	 * <b>枠が {@code db.isError()} を見ていなければ、
	 * 何も入っていないのに completed になる。</b>
	 * </p>
	 */
	public static class SilentFailBatch extends AbstractChunkBatch<Data> {

		@Override public String batchName () { return "テスト（黙って失敗）"; }

		@Override
		protected Iterator<Data> reader (BatchArgs args, DB db) {
			return List.of(new Data().putData("id", 1L)).iterator();
		}

		@Override
		protected void write (List<Data> items, DB db) {
			// 無い列。例外は投げられず、db.isError() が立つだけ
			db.insert("INSERT INTO chunk_dst (id, nothing_here) VALUES (?, ?)", 1L, "x");
		}

	}

	/**
	 * 検査例外を投げるバッチ
	 */
	public static class CheckedFailBatch extends AbstractChunkBatch<Data> {

		@Override public String batchName () { return "テスト（検査例外）"; }

		@Override
		protected Iterator<Data> reader (BatchArgs args, DB db) {
			return List.of(new Data().putData("id", 1L)).iterator();
		}

		@Override
		protected void write (List<Data> items, DB db) throws Exception {
			throw new java.io.IOException("外に出られない");
		}

	}

	// endregion

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), ChunkBatchIntegrationTest.class), "DB に接続できませんでした");

		DB db = DBUtil.getMainDB();

		BatchTables.install(db);

		db.execute("CREATE TABLE IF NOT EXISTS chunk_src (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(40) NOT NULL)");
		db.execute("CREATE TABLE IF NOT EXISTS chunk_dst (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(40) NOT NULL)");

	}

	@AfterAll
	static void stopDataSource () {

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS chunk_src");
		db.execute("DROP TABLE IF EXISTS chunk_dst");

		BatchRegistry.clear();
		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE batch_master");
		db.execute("TRUNCATE TABLE batch_history");
		db.execute("TRUNCATE TABLE batch_execute_info");
		db.execute("TRUNCATE TABLE chunk_src");
		db.execute("TRUNCATE TABLE chunk_dst");

		BatchExecutor.releaseAll();
		BatchRegistry.clear();

		failAtChunk = 0;
		filterIds = java.util.Set.of();
		WRITE_SIZES.clear();
		READ_DB.set(null);
		WRITE_DB.set(null);
		hold = null;
		wrote = null;
		holdFromChunk = 1;

		BatchRegistry.add(CopyBatch::new);
		BatchRegistry.add(SlowCopyBatch::new);
		BatchRegistry.add(ZeroChunkBatch::new);
		BatchRegistry.add(NullReaderBatch::new);
		BatchRegistry.add(CheckedFailBatch::new);
		BatchRegistry.add(SilentFailBatch::new);
		BatchRegistry.sync(db);

	}

	/**
	 * 元データを入れる
	 *
	 * @param count	件数
	 */
	private void seed (int count) {

		DB db = DBUtil.getMainDB();

		for (int i = 1; i <= count; i++) {
			db.insert("INSERT INTO chunk_src (id, name) VALUES (?, ?)", (long) i, "n" + i);
		}

	}

	/**
	 * 写った id
	 *
	 * @return	id
	 */
	private List<Long> copiedIds () {

		return DBUtil.getMainDB().selectList("SELECT id FROM chunk_dst ORDER BY id").stream()
			.map(row -> row.getLong("id"))
			.toList();

	}

	/**
	 * 引数を作る
	 *
	 * @param batchClass	バッチのクラス
	 * @return	引数
	 */
	private BatchArgs args (Class<? extends AbstractBatch> batchClass) {

		return BatchExecutor.parseArgs(new String[]{ "class=" + batchClass.getName() });

	}

	/**
	 * 最新の履歴
	 *
	 * @param batchClass	バッチのクラス
	 * @return	履歴
	 */
	private Data history (Class<? extends AbstractBatch> batchClass) {

		return DBUtil.getMainDB().select(
			"SELECT * FROM batch_history WHERE class_name = ? ORDER BY id DESC LIMIT 1"
			, batchClass.getName());

	}

	// region ふつうに回る

	@Test
	@DisplayName("チャンクごとに書いて全件を写す")
	void copiesAll () {

		seed(10);

		assertEquals(BatchResult.completed, BatchExecutor.execute(args(CopyBatch.class)));

		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), copiedIds());

		// 3 + 3 + 3 + 1。まとめて1回ではなく、4回に分けて書いている
		assertEquals(List.of(3, 3, 3, 1), WRITE_SIZES);

	}

	@Test
	@DisplayName("件数が実行情報に残る")
	void countsInHistory () {

		seed(10);

		BatchExecutor.execute(args(CopyBatch.class));

		Data info = history(CopyBatch.class).getDataOptional("execute_info");

		assertEquals(10, info.getInt(AbstractChunkBatch.KEY_READ), info.toString());
		assertEquals(10, info.getInt(AbstractChunkBatch.KEY_WRITTEN), info.toString());
		assertEquals(4, info.getInt(AbstractChunkBatch.KEY_CHUNKS), info.toString());
		assertEquals(0, info.getInt(AbstractChunkBatch.KEY_FILTERED), info.toString());
		assertFalse(info.getBoolean(AbstractChunkBatch.KEY_CANCELED), info.toString());

	}

	@Test
	@DisplayName("1件も無ければ write は呼ばれない")
	void noRows () {

		assertEquals(BatchResult.completed, BatchExecutor.execute(args(CopyBatch.class)));

		assertTrue(WRITE_SIZES.isEmpty(), WRITE_SIZES.toString());

		Data info = history(CopyBatch.class).getDataOptional("execute_info");

		assertEquals(0, info.getInt(AbstractChunkBatch.KEY_READ));
		assertEquals(0, info.getInt(AbstractChunkBatch.KEY_CHUNKS));

	}

	@Test
	@DisplayName("読む DB と書く DB は別のインスタンスである")
	void separateDbInstances () {

		seed(4);

		BatchExecutor.execute(args(CopyBatch.class));

		/*
		 * 同じ DB を渡すと、チャンクを1つ確定した時点で
		 * DBTransaction.close() が db.close() を呼び、読みかけが死ぬ。
		 */
		assertNotSame(READ_DB.get(), WRITE_DB.get());

	}

	// endregion

	// region よける（要件 F-B-12）

	@Test
	@DisplayName("process が null を返した1件は書かずによける")
	void filtered () {

		seed(10);
		filterIds = java.util.Set.of(5L);

		BatchExecutor.execute(args(CopyBatch.class));

		assertEquals(List.of(1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L, 10L), copiedIds());

		Data info = history(CopyBatch.class).getDataOptional("execute_info");

		assertEquals(10, info.getInt(AbstractChunkBatch.KEY_READ));
		assertEquals(1, info.getInt(AbstractChunkBatch.KEY_FILTERED));
		assertEquals(9, info.getInt(AbstractChunkBatch.KEY_WRITTEN));

	}

	@Test
	@DisplayName("1チャンク分まるごとよけられても、その先を読み続ける")
	void wholeChunkFiltered () {

		/*
		 * chunkSize は 3。id 4・5・6 をよける。
		 * <b>よけられた分でかたまりを打ち切ってはいけない。</b>
		 * 内側のループは、かたまりが満たない限り読み続ける。
		 */
		seed(9);
		filterIds = java.util.Set.of(4L, 5L, 6L);

		assertEquals(BatchResult.completed, BatchExecutor.execute(args(CopyBatch.class)));

		assertEquals(List.of(1L, 2L, 3L, 7L, 8L, 9L), copiedIds());

		// 3件 + 3件。まん中の3件をよけた分だけ余分に読んで、かたまりは満たしている
		assertEquals(List.of(3, 3), WRITE_SIZES);

		Data info = history(CopyBatch.class).getDataOptional("execute_info");

		assertEquals(9, info.getInt(AbstractChunkBatch.KEY_READ), info.toString());
		assertEquals(3, info.getInt(AbstractChunkBatch.KEY_FILTERED), info.toString());
		assertEquals(6, info.getInt(AbstractChunkBatch.KEY_WRITTEN), info.toString());

	}

	@Test
	@DisplayName("末尾が全部よけられても、空のまま write を呼ばない")
	void filteredTail () {

		/*
		 * chunkSize は 3。id 7・8・9 をよけると、
		 * 最後のかたまりに入るものが1つも無い。
		 */
		seed(9);
		filterIds = java.util.Set.of(7L, 8L, 9L);

		assertEquals(BatchResult.completed, BatchExecutor.execute(args(CopyBatch.class)));

		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), copiedIds());

		// 0 件で呼ばれていない
		assertEquals(List.of(3, 3), WRITE_SIZES);

		Data info = history(CopyBatch.class).getDataOptional("execute_info");

		assertEquals(9, info.getInt(AbstractChunkBatch.KEY_READ), info.toString());
		assertEquals(3, info.getInt(AbstractChunkBatch.KEY_FILTERED), info.toString());
		assertEquals(6, info.getInt(AbstractChunkBatch.KEY_WRITTEN), info.toString());
		assertEquals(2, info.getInt(AbstractChunkBatch.KEY_CHUNKS), info.toString());

	}

	// endregion

	// region 落ちたとき

	@Test
	@DisplayName("落ちたチャンクだけ戻り、そこまでは確定している")
	void failedChunkRollsBack () {

		seed(10);
		failAtChunk = 3;

		assertEquals(BatchResult.error, BatchExecutor.execute(args(CopyBatch.class)));

		// 1・2チャンク目は確定。3チャンク目は丸ごと戻る。4チャンク目には進まない
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), copiedIds());

	}

	@Test
	@DisplayName("どこまで確定したかが履歴に残る")
	void failureLeavesCounts () {

		seed(10);
		failAtChunk = 3;

		BatchExecutor.execute(args(CopyBatch.class));

		Data row = history(CopyBatch.class);
		Data info = row.getDataOptional("execute_info");

		assertEquals(BatchHistoryStatus.error.name(), row.getString("status"));

		assertEquals(6, info.getInt(AbstractChunkBatch.KEY_WRITTEN), info.toString());
		assertEquals(2, info.getInt(AbstractChunkBatch.KEY_CHUNKS), info.toString());
		assertEquals(3, info.getInt(AbstractChunkBatch.KEY_FAILED_AT), info.toString());

		assertEquals("java.lang.IllegalStateException"
			, info.getDataOptional("exception").getString("class"), info.toString());

	}

	@Test
	@DisplayName("検査例外も原因が読める形で残る")
	void checkedExceptionIsReadable () {

		assertEquals(BatchResult.error, BatchExecutor.execute(args(CheckedFailBatch.class)));

		Data info = history(CheckedFailBatch.class).getDataOptional("execute_info");
		Data exception = info.getDataOptional("exception");

		/*
		 * execute() は検査例外を投げられないので包んでいる。
		 * 包んでも原因のクラスとメッセージがスタックトレースに残ることを確かめる。
		 */
		assertTrue(exception.getString("stack_trace").contains("java.io.IOException")
			, exception.getString("stack_trace"));
		assertTrue(exception.getString("message").contains("外に出られない")
			, exception.getString("message"));

	}

	@Test
	@DisplayName("例外を投げない SQL の失敗も拾ってチャンクを失敗させる")
	void silentSqlFailure () {

		assertEquals(BatchResult.error, BatchExecutor.execute(args(SilentFailBatch.class)));

		assertTrue(copiedIds().isEmpty(), copiedIds().toString());

		Data info = history(SilentFailBatch.class).getDataOptional("execute_info");

		assertEquals(0, info.getInt(AbstractChunkBatch.KEY_WRITTEN), info.toString());
		assertTrue(info.getDataOptional("exception").getString("message")
			.contains("チャンクの書き込みが失敗しています")
			, info.getDataOptional("exception").toString());

	}

	@Test
	@DisplayName("chunkSize が 0 なら落ちる")
	void zeroChunkSize () {

		assertEquals(BatchResult.error, BatchExecutor.execute(args(ZeroChunkBatch.class)));

		Data exception = history(ZeroChunkBatch.class)
			.getDataOptional("execute_info").getDataOptional("exception");

		assertTrue(exception.getString("message").contains("chunkSize()"), exception.toString());

	}

	@Test
	@DisplayName("reader が null を返したら落ちる")
	void nullReader () {

		assertEquals(BatchResult.error, BatchExecutor.execute(args(NullReaderBatch.class)));

		Data exception = history(NullReaderBatch.class)
			.getDataOptional("execute_info").getDataOptional("exception");

		assertTrue(exception.getString("message").contains("reader()"), exception.toString());

	}

	// endregion

	// region 中断（要件 F-B-06）

	@Test
	@DisplayName("中断はチャンクの切れ目で効き、確定した分はそのまま残る")
	void canceledAtChunkBoundary () throws Exception {

		seed(60);

		SlowCopyBatch batch = new SlowCopyBatch();
		AtomicReference<BatchResult> result = new AtomicReference<>();

		wrote = new CountDownLatch(2);

		Thread thread = Thread.ofVirtual().start(() ->
			result.set(batch.run(args(SlowCopyBatch.class), null)));

		assertTrue(wrote.await(10, TimeUnit.SECONDS), "2チャンク書かれるまで待てなかった");

		batch.doCancel();
		thread.join();

		assertEquals(BatchResult.canceled, result.get());

		Data row = history(SlowCopyBatch.class);
		Data info = row.getDataOptional("execute_info");

		assertEquals(BatchHistoryStatus.canceled.name(), row.getString("status"));
		assertTrue(info.getBoolean(AbstractChunkBatch.KEY_CANCELED), info.toString());

		// 最後まで回りきっていない
		assertTrue(info.getInt(AbstractChunkBatch.KEY_WRITTEN) < 60, info.toString());
		assertTrue(info.getInt(AbstractChunkBatch.KEY_WRITTEN) > 0, info.toString());

		// 確定した分は消えない。書いた件数と写った件数が合う
		assertEquals(info.getInt(AbstractChunkBatch.KEY_WRITTEN), copiedIds().size(), info.toString());

	}

	// endregion

	// region 進み具合

	@Test
	@DisplayName("走っている最中に進み具合が履歴から読める")
	void progressIsVisibleWhileRunning () throws Exception {

		// 間隔を 0 にして、チャンクごとに書かせる
		Conf.replace(Conf.conf().config()
			.withValue(BatchConf.KEY_PROGRESS, ConfigValueFactory.fromAnyRef("0s")));

		try {

			seed(30);

			CopyBatch batch = new CopyBatch();
			AtomicReference<BatchResult> result = new AtomicReference<>();

			/*
			 * 1チャンク目は確定させ、2チャンク目で待たせる。
			 * 待つのは write() の中なので、
			 * <b>1チャンク目から待たせると確定も進み具合の書き込みも起きない。</b>
			 */
			wrote = new CountDownLatch(2);
			hold = new CountDownLatch(1);
			holdFromChunk = 2;

			Thread thread = Thread.ofVirtual().start(() ->
				result.set(batch.run(args(CopyBatch.class), null)));

			assertTrue(wrote.await(10, TimeUnit.SECONDS), "2チャンク書かれるまで待てなかった");

			/*
			 * バッチはまだ走っている（hold で止めてある）。
			 * この時点で履歴の execute_info に件数が入っていること。
			 * 移送元のバッチは開始と終了しか書かないので、
			 * <b>長いバッチはずっと「実行中・件数不明」だった。</b>
			 */
			AtomicInteger seen = new AtomicInteger(-1);

			for (int i = 0; i < 100; i++) {

				Data row = DBUtil.getMainDB().select(
					"SELECT execute_info FROM batch_history WHERE id = ?", batch.batchId());

				int written = row.getDataOptional("execute_info")
					.getInt(AbstractChunkBatch.KEY_WRITTEN);

				if (written > 0) {
					seen.set(written);
					break;
				}

				Thread.sleep(20);

			}

			assertTrue(seen.get() > 0, "走っている間、履歴に件数が出てこなかった");
			assertTrue(seen.get() < 30, "終わってから読んでいる: " + seen.get());

			hold.countDown();
			thread.join();

			assertEquals(BatchResult.completed, result.get());

		} finally {

			if (hold != null) {
				hold.countDown();
			}

			Conf.reload();

		}

	}

	@Test
	@DisplayName("間隔の間は履歴を書き換えない")
	void progressIsThrottled () throws Exception {

		// 既定より長くして、走っている間は1回も書かせない
		Conf.replace(Conf.conf().config()
			.withValue(BatchConf.KEY_PROGRESS, ConfigValueFactory.fromAnyRef("1h")));

		try {

			seed(30);

			CopyBatch batch = new CopyBatch();
			AtomicReference<BatchResult> result = new AtomicReference<>();

			/*
			 * 1チャンク目は確定させ、2チャンク目で待たせる。
			 * 待つのは write() の中なので、
			 * <b>1チャンク目から待たせると確定も進み具合の書き込みも起きない。</b>
			 */
			wrote = new CountDownLatch(2);
			hold = new CountDownLatch(1);
			holdFromChunk = 2;

			Thread thread = Thread.ofVirtual().start(() ->
				result.set(batch.run(args(CopyBatch.class), null)));

			assertTrue(wrote.await(10, TimeUnit.SECONDS), "2チャンク書かれるまで待てなかった");

			Data row = DBUtil.getMainDB().select(
				"SELECT execute_info FROM batch_history WHERE id = ?", batch.batchId());

			assertEquals(0, row.getDataOptional("execute_info")
				.getInt(AbstractChunkBatch.KEY_WRITTEN), row.getStringOptional("execute_info"));

			hold.countDown();
			thread.join();

			// 終わったときは finishHistory がまとめて書くので、そこには出る
			assertEquals(30, history(CopyBatch.class).getDataOptional("execute_info")
				.getInt(AbstractChunkBatch.KEY_WRITTEN));

		} finally {

			if (hold != null) {
				hold.countDown();
			}

			Conf.reload();

		}

	}

	// endregion

}
