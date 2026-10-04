package io.jimble.web.ratelimit;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.hash.Hash;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB の流量制限（D-285）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class DbRateLimitStoreIntegrationTest {

	@BeforeAll
	static void load () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), DbRateLimitStoreIntegrationTest.class);

	}

	@AfterAll
	static void stop () {

		DBUtil.stop();

	}

	@Test
	@DisplayName("上限まで通し、超えたら止める")
	void countsUpToLimit () throws Exception {

		DbRateLimitStore store = new DbRateLimitStore();
		String key = UUID.randomUUID().toString();

		assertTrue(store.consume(key, 2, Duration.ofMinutes(1)).allowed());
		assertTrue(store.consume(key, 2, Duration.ofMinutes(1)).allowed());
		assertFalse(store.consume(key, 2, Duration.ofMinutes(1)).allowed());

	}

	@Test
	@DisplayName("D-285 初めてのキーへ同時に来ても行き詰まらない。同じキーなら、通すのは上限の数だけ")
	void concurrentFirstHits () throws Exception {

		DbRateLimitStore store = new DbRateLimitStore();
		String shared = UUID.randomUUID().toString();

		int threads = 16;
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger allowed = new AtomicInteger();
		List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
		List<Thread> workers = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			String own = UUID.randomUUID().toString();
			workers.add(Thread.ofVirtual().start(() -> {
				try {
					start.await();
					// 自分だけのキー（初めて）と、みんなのキー（初めて）を同時に
					store.consume(own, 5, Duration.ofMinutes(1));
					if (store.consume(shared, 5, Duration.ofMinutes(1)).allowed()) {
						allowed.incrementAndGet();
					}
				} catch (Throwable ex) {
					failures.add(ex);
				}
			}));
		}

		start.countDown();
		for (Thread worker : workers) {
			worker.join(30_000);
		}

		assertTrue(failures.isEmpty(), "失敗した: " + failures);
		assertEquals(5, allowed.get(), "同じキーで上限を超えて通した、または足りない");

	}

	@Test
	@DisplayName("D-285 最後に数えてから時間が経った行だけを消す")
	void purgesIdleRows () throws Exception {

		DbRateLimitStore store = new DbRateLimitStore();
		String fresh = UUID.randomUUID().toString();

		store.consume(fresh, 5, Duration.ofMinutes(1));

		try (DB db = DBUtil.getMainDB()) {
			db.execute("INSERT INTO %s (rate_key, tokens, updated_at) VALUES (?, 1, 1000)".formatted(DbRateLimitStore.TABLE)
				, Hash.sipHash("idle-" + UUID.randomUUID()));
		}

		assertTrue(DbRateLimitStore.purgeIdle(System.currentTimeMillis() - 60_000) >= 1);

		try (DB db = DBUtil.getMainDB()) {
			assertEquals(0L, db.select("SELECT count(*) AS n FROM %s WHERE updated_at = 1000".formatted(DbRateLimitStore.TABLE))
				.map(row -> row.getLong("n")).orElse(-1L));
			assertEquals(1L, db.select("SELECT count(*) AS n FROM %s WHERE rate_key = ?".formatted(DbRateLimitStore.TABLE), Hash.sipHash(fresh))
				.map(row -> row.getLong("n")).orElse(-1L), "いま数えた行まで消えた");
		}

	}

}
