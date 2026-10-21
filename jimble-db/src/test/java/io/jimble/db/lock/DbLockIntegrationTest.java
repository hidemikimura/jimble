package io.jimble.db.lock;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.Tx;
import io.jimble.util.conf.Conf;
import io.jimble.util.hash.Hash;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DBLock.create の結合テスト
 *
 * <p>
 * <b>あるキーの create は、ロックを取らない読みだけで済ませる。</b>
 * MySQL（InnoDB）の {@code INSERT IGNORE} は、すでにある行に当たると重複を確かめるために
 * <b>その行に共有ロックを取る</b>。ほかのトランザクションが {@code lock()}（{@code FOR UPDATE}）で
 * 持っている間は、それが終わるまで待ち、トランザクションの中で呼べば共有ロックが commit まで残る。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-db:dbTest} / {@code pgTest}</p>
 */
@Tag("db")
class DbLockIntegrationTest {

	/* 待たないことを確かめるときの上限（秒）。待つ実装なら、持っている側が離すまで戻らない */
	private static final long NO_WAIT_SECONDS = 3;

	/* 同時に呼ぶ数 */
	private static final int THREADS = 8;

	@BeforeAll
	static void load () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), DbLockIntegrationTest.class);

	}

	@AfterAll
	static void stop () {

		DBUtil.stop();

	}

	private static String uniqueKey (String name) {

		return "lock-test-" + name + "-" + System.nanoTime();

	}

	private static long rows (String key) {

		return DBUtil.getMainDB()
			.select("SELECT COUNT(*) AS c FROM db_lock WHERE lock_key = ?", Hash.sipHash(key))
			.orElseThrow()
			.getLong("c");

	}

	@Test
	@DisplayName("あるキーの create は、ほかのトランザクションが lock で持っていても待たない")
	void createDoesNotWaitWhileKeyIsLocked () throws Exception {

		String key = uniqueKey("held");
		DBLock.create(DBUtil.getMainDB(), key);

		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		try {

			Future<?> holder = pool.submit(() -> {
				DB db = DBUtil.getMainDB();
				db.transaction(tx -> {
					DBLock.lock(db, key);
					locked.countDown();
					await(release);
				});
				return null;
			});

			locked.await(10, TimeUnit.SECONDS);

			Future<?> create = pool.submit(() -> {
				DBLock.create(DBUtil.getMainDB(), key);
				return null;
			});

			// 待つ実装なら、ここで TimeoutException になる
			create.get(NO_WAIT_SECONDS, TimeUnit.SECONDS);

			release.countDown();
			holder.get(10, TimeUnit.SECONDS);

		} finally {
			release.countDown();
			pool.shutdownNow();
		}

	}

	@Test
	@DisplayName("無いキーを同時に create しても、重複で落ちずに1行だけできる")
	void concurrentCreateOfNewKeyMakesOneRow () throws Exception {

		String key = uniqueKey("race");
		CyclicBarrier start = new CyclicBarrier(THREADS);
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);

		try {

			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				futures.add(pool.submit(() -> {
					start.await(10, TimeUnit.SECONDS);
					DBLock.create(DBUtil.getMainDB(), key);
					return null;
				}));
			}

			for (Future<?> future : futures) {
				future.get(30, TimeUnit.SECONDS);   // 例外ならここで落ちる
			}

		} finally {
			pool.shutdownNow();
		}

		assertEquals(1, rows(key));

	}

	@Test
	@DisplayName("あるキーと無いキーを一緒に渡すと、無いものだけ足す")
	void createWithExistingAndMissingKeys () {

		String existing = uniqueKey("existing");
		String missing1 = uniqueKey("missing1");
		String missing2 = uniqueKey("missing2");
		DB db = DBUtil.getMainDB();

		DBLock.create(db, existing);
		DBLock.create(db, existing, missing1, null, "", missing2, missing1);

		assertEquals(1, rows(existing));
		assertEquals(1, rows(missing1));
		assertEquals(1, rows(missing2));

		// 作ったキーはロックできる
		db.transaction(tx -> DBLock.lock(db, existing, missing1, missing2));

	}

	@Test
	@DisplayName("トランザクションの中で create → lock を同時にしても、デッドロックにならない")
	void createThenLockInsideTransactionsDoesNotDeadlock () throws Exception {

		String key = uniqueKey("upgrade");
		DBLock.create(DBUtil.getMainDB(), key);

		// 両方が create を終えてから lock に進む。create が共有ロックを残すと、互いの共有ロックを待ってデッドロックになる
		CyclicBarrier created = new CyclicBarrier(2);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		try {

			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					DB db = DBUtil.getMainDB();
					try (Tx tx = db.begin()) {
						DBLock.create(db, key);
						created.await(10, TimeUnit.SECONDS);
						DBLock.lock(db, key);
						tx.commit();
					}
					return null;
				}));
			}

			for (Future<?> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}

		} finally {
			pool.shutdownNow();
		}

	}

	private static void await (CountDownLatch latch) {

		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}

	}

}
