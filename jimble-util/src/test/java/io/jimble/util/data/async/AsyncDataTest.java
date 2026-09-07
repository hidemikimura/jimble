package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遅延読み込みデータのテスト（要件 F-A-01〜05 / F-A-09 / F-A-10 / F-D-27）
 */
class AsyncDataTest {

	@AfterEach
	void resetLog () {

		Log.resetSink();

	}

	// region テスト用の実装

	/**
	 * 数えられる遅延読み込みデータ
	 */
	static class CountingData extends AsyncData {

		/** load() が呼ばれた回数 */
		final AtomicInteger loadCount = new AtomicInteger();

		/** 呼ばれた順 */
		final List<String> calls = new ArrayList<>();

		/** 読み込みにかける時間 */
		long delayMillis = 0;

		/** 失敗させるか */
		boolean fails = false;

		private final String key;

		CountingData (String key) {

			this.key = key;

		}

		@Override
		protected Data load () throws Exception {

			loadCount.incrementAndGet();
			calls.add("load");

			if (fails) {
				throw new IllegalStateException("読めない");
			}

			if (delayMillis > 0) {
				Thread.sleep(delayMillis);
			}

			return new Data().putData("name", "値").putData("count", 3);

		}

		@Override
		protected void setData (Data data) {

			calls.add("setData");
			putAllData(data);

		}

		@Override
		protected void setRelationData (Data data) {

			calls.add("setRelationData");
			putData("relation", "つけた");

		}

		@Override
		protected String hashKey () {

			return "CountingData:" + key;

		}

	}

	// endregion

	// region 遅延（要件 F-A-01）

	@Test
	@DisplayName("参照されるまで読まない")
	void loadsOnAccess () {

		CountingData data = new CountingData("a");

		assertEquals(0, data.loadCount.get(), "作っただけで読んでいる");

		assertEquals("値", data.getString("name"));
		assertEquals(1, data.loadCount.get());

	}

	@Test
	@DisplayName("何度参照しても読むのは1回")
	void loadsOnce () {

		CountingData data = new CountingData("a");

		data.getString("name");
		data.getString("name");
		data.size();
		data.keySet();

		assertEquals(1, data.loadCount.get());

	}

	@Test
	@DisplayName("setData のあとに setRelationData")
	void callOrder () {

		CountingData data = new CountingData("a");
		data.getString("name");

		assertEquals(List.of("load", "setData", "setRelationData"), data.calls);
		assertEquals("つけた", data.getString("relation"));

	}

	// endregion

	// region 読み込みを起こさないもの（要件 F-A-04 / F-A-05 / F-D-27）

	@Test
	@DisplayName("状態を見るだけでは読まない")
	void stateDoesNotLoad () {

		CountingData data = new CountingData("a");

		assertFalse(data.isLoaded());
		assertFalse(data.isLoadFailed());
		assertNull(data.batchKey());
		assertNull(data.batchId());
		assertTrue(data.loadedValues().isEmpty());

		assertEquals(0, data.loadCount.get(), "状態を見ただけで読んでいる");

	}

	@Test
	@DisplayName("比較しただけでは読まない")
	void compareDoesNotLoad () {

		// 要件 F-A-05
		CountingData a1 = new CountingData("a");
		CountingData a2 = new CountingData("a");
		CountingData b = new CountingData("b");

		assertEquals(a1, a2);
		assertEquals(a1.hashCode(), a2.hashCode());
		assertFalse(a1.equals(b));

		Set<AsyncData> set = new HashSet<>();
		set.add(a1);
		set.add(a2);
		set.add(b);

		assertEquals(2, set.size());
		assertEquals(0, a1.loadCount.get() + a2.loadCount.get() + b.loadCount.get()
			, "比較しただけで読んでいる");

	}

	@Test
	@DisplayName("toString しただけでは読まない")
	void toStringDoesNotLoad () {

		// 要件 F-D-27。移送元は Data.toString() が JSON 化だったので、
		// ログに1行出すだけで枝という枝に SQL が飛んでいた
		CountingData data = new CountingData("a");

		assertEquals("CountingData(未読み込み)", data.toString());
		assertEquals(0, data.loadCount.get(), "toString で読んでいる");

		data.getString("name");

		assertTrue(data.toString().contains("name"), data.toString());

	}

	@Test
	@DisplayName("入れ子にしても toString で読まない")
	void nestedToStringDoesNotLoad () {

		CountingData child = new CountingData("child");

		Data parent = new Data()
			.putData("id", 1L)
			.putData("child", child);

		String text = parent.toString();

		assertEquals(0, child.loadCount.get(), "親の toString で子を読んでいる");
		assertTrue(text.contains("CountingData(未読み込み)"), text);

	}

	// endregion

	// region 直した穴

	@Test
	@DisplayName("containsValue も読む")
	void containsValueLoads () {

		// 移送元はここが抜けていて、未読み込みだと黙って false を返していた
		CountingData data = new CountingData("a");

		assertTrue(data.containsValue("値"));
		assertEquals(1, data.loadCount.get());

	}

	@Test
	@DisplayName("同時に触られても1回しか読まず、空の中身が見えない")
	void concurrentAccess () throws Exception {

		/*
		 * 移送元は isLoaded が volatile でなく、しかも読み込みの本体より先に
		 * 立てていた。別のスレッドが「読み込み済みで中身が空」を見る。
		 */
		for (int attempt = 0; attempt < 20; attempt++) {

			CountingData data = new CountingData("a");
			data.delayMillis = 5;

			int threads = 32;
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch go = new CountDownLatch(1);
			AtomicReference<String> wrong = new AtomicReference<>();
			List<Thread> workers = new ArrayList<>();

			for (int i = 0; i < threads; i++) {

				workers.add(Thread.ofVirtual().start(() -> {
					try {
						ready.countDown();
						go.await();
						String name = data.getString("name");
						if (!"値".equals(name)) {
							wrong.compareAndSet(null, String.valueOf(name));
						}
					} catch (Exception ex) {
						wrong.compareAndSet(null, ex.toString());
					}
				}));

			}

			assertTrue(ready.await(5, TimeUnit.SECONDS));
			go.countDown();

			for (Thread worker : workers) {
				worker.join();
			}

			assertNull(wrong.get(), "空の中身が見えた: " + wrong.get());
			assertEquals(1, data.loadCount.get(), "何度も読んでいる");

		}

	}

	@Test
	@DisplayName("読み込みの最中に自分を参照しても止まる")
	void reentrantAccess () {

		AsyncData data = new AsyncData() {

			int loadCount = 0;

			@Override
			protected Data load () {
				loadCount++;
				return new Data().putData("a", 1);
			}

			@Override
			protected void setData (Data loaded) {
				putAllData(loaded);
				// 反映の途中で自分を読む
				putData("b", getLong("a") + 1);
			}

			@Override
			protected String hashKey () {
				return "reentrant";
			}

		};

		assertEquals(2L, data.getLong("b"));

	}

	// endregion

	// region 失敗したとき（要件 F-A-09）

	@Test
	@DisplayName("読み込みに失敗しても例外を投げず、状態が残る")
	void loadFailure () {

		List<String> errorLog = new ArrayList<>();
		Log.sink((loggerName, level, message, data, throwable) -> errorLog.add(String.valueOf(message)));

		CountingData data = new CountingData("a");
		data.fails = true;

		// 例外は投げない（移送元と同じ）
		assertNull(data.getString("name"));

		assertTrue(data.isLoaded());
		assertTrue(data.isLoadFailed(), "失敗したことが分からない");
		assertFalse(errorLog.isEmpty(), "ログに出ていない");

	}

	@Test
	@DisplayName("失敗したら二度は読みにいかない")
	void failureIsNotRetried () {

		Log.sink((loggerName, level, message, data, throwable) -> {});

		CountingData data = new CountingData("a");
		data.fails = true;

		data.getString("name");
		data.getString("name");
		data.size();

		assertEquals(1, data.loadCount.get(), "落ちるクエリを何度も投げている");

	}

	@Test
	@DisplayName("失敗したものは toString でも分かる")
	void failureToString () {

		Log.sink((loggerName, level, message, data, throwable) -> {});

		CountingData data = new CountingData("a");
		data.fails = true;
		data.getString("name");

		assertEquals("CountingData(読み込み失敗)", data.toString());

	}

	// endregion

	// region 先読みの下ごしらえ（要件 F-A-10）

	@Test
	@DisplayName("putData で読み込まずに入れられる")
	void putDataSkipsLoad () {

		CountingData data = new CountingData("a");

		data.putData(new Data().putData("name", "外から"));

		assertEquals("外から", data.getString("name"));
		assertEquals(0, data.loadCount.get(), "外から入れたのに読んでいる");
		assertTrue(data.isLoaded());

	}

	@Test
	@DisplayName("読み込み済みなら putData は何もしない")
	void putDataAfterLoad () {

		CountingData data = new CountingData("a");

		assertEquals("値", data.getString("name"));

		data.putData(new Data().putData("name", "あとから"));

		assertEquals("値", data.getString("name"), "読み込み済みのものが上書きされている");

	}

	@Test
	@DisplayName("batchKey / batchId を返せる")
	void batchKeys () {

		AsyncData data = new AsyncData() {

			@Override protected Data load () { return new Data(); }
			@Override protected void setData (Data loaded) {}
			@Override protected String hashKey () { return "x"; }
			@Override public String batchKey () { return "site_feeds"; }
			@Override public Object batchId () { return 42L; }

		};

		assertEquals("site_feeds", data.batchKey());
		assertEquals(42L, data.batchId());
		assertFalse(data.isLoaded(), "batchKey / batchId で読んでいる");

	}

	// endregion

}
