package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 先読み（要件 F-A-06〜08 / NF-T-04）
 *
 * <p>
 * NF-T-04 が挙げている3点を固定する。
 * </p>
 * <ol>
 *   <li>先読みの有無で<b>出力が完全に一致する</b></li>
 *   <li><b>クエリ本数</b>が 1+N から 1+1 に減る</li>
 *   <li><b>循環参照下でも止まる</b></li>
 * </ol>
 */
class AsyncPrefetchTest {

	/* 個別に読んだ回数 */
	static final AtomicInteger LOAD_COUNT = new AtomicInteger();

	/* まとめて読んだ回数 */
	static final AtomicInteger BATCH_COUNT = new AtomicInteger();

	/**
	 * コメントの一覧（記事1件ぶん）
	 */
	static class Comments extends AsyncList {

		private static final long serialVersionUID = 1L;

		final long postId;

		Comments (long postId) { this.postId = postId; }

		@Override
		protected List<Data> load () {

			LOAD_COUNT.incrementAndGet();

			return rows(postId);

		}

		@Override
		protected Map<Object, List<Data>> loadBatch (List<Object> ids) {

			BATCH_COUNT.incrementAndGet();

			Map<Object, List<Data>> result = new LinkedHashMap<>();

			for (Object id : ids) {
				result.put(id, rows((Long) id));
			}

			return result;

		}

		@Override
		protected void setData (Data data) { add(data); }

		@Override
		protected String hashKey () { return "Comments:" + postId; }

		@Override
		public String batchKey () { return "Comments"; }

		@Override
		public Object batchId () { return postId; }

		/* 記事 id が偶数なら2件、奇数なら1件、100 以上は0件 */
		static List<Data> rows (long postId) {

			List<Data> rows = new ArrayList<>();

			if (postId >= 100) {
				return rows;
			}

			rows.add(new Data().putData("body", "c%d-1".formatted(postId)));

			if (postId % 2 == 0) {
				rows.add(new Data().putData("body", "c%d-2".formatted(postId)));
			}

			return rows;

		}

	}

	/** 記事 id を並べたツリーを作る */
	static Data tree (long... postIds) {

		List<Data> posts = new ArrayList<>();

		for (long id : postIds) {
			posts.add(new Data()
				.putData("id", id)
				.putData("comments", new Comments(id)));
		}

		return new Data().putData("posts", posts);

	}

	// region 1. 出力が一致する（要件 F-A-08）

	@Test
	@DisplayName("先読みしてもしなくても、出力が完全に一致する")
	void sameOutput () {

		long[] ids = { 1, 2, 3, 4, 5, 100 };

		String withoutPrefetch = tree(ids).getJsonString();

		Data prefetched = tree(ids);
		AsyncPrefetch.run(prefetched);
		String withPrefetch = prefetched.getJsonString();

		assertEquals(withoutPrefetch, withPrefetch);

	}

	@Test
	@DisplayName("まとめて読んで返ってこなかった id は、個別に読んで0件だったのと同じ")
	void missingIdIsEmpty () {

		Data data = tree(100, 101);

		AsyncPrefetch.run(data);

		assertEquals(tree(100, 101).getJsonString(), data.getJsonString());

	}

	// endregion

	// region 2. クエリ本数（要件 F-A-06）

	@Test
	@DisplayName("先読みしないと記事の数だけ読みにいく")
	void withoutPrefetchIsNPlusOne () {

		LOAD_COUNT.set(0);
		BATCH_COUNT.set(0);

		tree(1, 2, 3, 4, 5).getJsonString();

		assertEquals(5, LOAD_COUNT.get());
		assertEquals(0, BATCH_COUNT.get());

	}

	@Test
	@DisplayName("先読みすると1回にまとまる")
	void withPrefetchIsOne () {

		LOAD_COUNT.set(0);
		BATCH_COUNT.set(0);

		Data data = tree(1, 2, 3, 4, 5);

		AsyncPrefetch.Result result = AsyncPrefetch.run(data);

		data.getJsonString();

		assertEquals(0, LOAD_COUNT.get(), "個別に読みにいってはいけない");
		assertEquals(1, BATCH_COUNT.get());
		assertEquals(1, result.groups());
		assertEquals(5, result.filled());

	}

	@Test
	@DisplayName("同じ記事が何度も出てきても、まとめて読むのは1回")
	void duplicatedIdsAreLoadedOnce () {

		BATCH_COUNT.set(0);

		Data data = tree(1, 1, 1, 2, 2);

		AsyncPrefetch.run(data);

		assertEquals(1, BATCH_COUNT.get());

	}

	// endregion

	// region 走査そのものが読み込みを起こさない（要件 F-A-04）

	@Test
	@DisplayName("走査しただけでは読み込みが起きない")
	void walkDoesNotLoad () {

		LOAD_COUNT.set(0);
		BATCH_COUNT.set(0);

		Data data = new Data().putData("posts", List.of(
			new Data().putData("comments", new NotBatchable(1))));

		AsyncPrefetch.run(data);

		assertEquals(0, LOAD_COUNT.get(), "batchKey が無いものを読みにいってはいけない");
		assertEquals(0, BATCH_COUNT.get());

	}

	/** batchKey を返さない = 先読みの対象外 */
	static class NotBatchable extends AsyncList {

		private static final long serialVersionUID = 1L;

		final long id;

		NotBatchable (long id) { this.id = id; }

		@Override
		protected List<Data> load () {

			LOAD_COUNT.incrementAndGet();
			return List.of(new Data().putData("body", "x"));

		}

		@Override
		protected void setData (Data data) { add(data); }

		@Override
		protected String hashKey () { return "NotBatchable:" + id; }

	}

	// endregion

	// region 3. 循環参照下でも止まる（要件 F-A-07）

	/**
	 * 自分の中にまた自分と同じ種類の枝が生えるもの
	 *
	 * <p>読み込むたびに次の階層が生えるので、止める仕掛けが無ければ無限に回る。</p>
	 */
	static class Endless extends AsyncData {

		private static final long serialVersionUID = 1L;

		static final AtomicInteger BATCHES = new AtomicInteger();

		final long id;

		Endless (long id) { this.id = id; }

		@Override
		protected Data load () { return new Data().putData("next", new Endless(id + 1)); }

		@Override
		protected Map<Object, Data> loadBatch (List<Object> ids) {

			BATCHES.incrementAndGet();

			Map<Object, Data> result = new LinkedHashMap<>();

			for (Object id : ids) {
				result.put(id, new Data().putData("next", new Endless(((Long) id) + 1)));
			}

			return result;

		}

		@Override
		protected void setData (Data data) { putAllData(data); }

		@Override
		protected String hashKey () { return "Endless:" + id; }

		@Override
		public String batchKey () { return "Endless"; }

		@Override
		public Object batchId () { return id; }

	}

	@Test
	@DisplayName("枝が生え続けても、周回の上限で止まる")
	void endlessStopsAtMaxDepth () {

		Endless.BATCHES.set(0);

		Data data = new Data().putData("root", new Endless(1));

		AsyncPrefetch.Result result = AsyncPrefetch.run(data, 3);

		assertEquals(3, result.rounds());
		assertEquals(3, Endless.BATCHES.get());

	}

	/**
	 * 互いを持ち合うもの
	 */
	static class Ping extends AsyncData {

		private static final long serialVersionUID = 1L;

		static final AtomicInteger BATCHES = new AtomicInteger();

		final long id;

		Ping (long id) { this.id = id; }

		@Override
		protected Data load () { return new Data(); }

		@Override
		protected Map<Object, Data> loadBatch (List<Object> ids) {

			BATCHES.incrementAndGet();

			Map<Object, Data> result = new LinkedHashMap<>();

			/* 1 は 2 を、2 は 1 を持つ */
			for (Object id : ids) {
				result.put(id, new Data().putData("pair", new Ping(((Long) id) == 1L ? 2L : 1L)));
			}

			return result;

		}

		@Override
		protected void setData (Data data) { putAllData(data); }

		@Override
		protected String hashKey () { return "Ping:" + id; }

		@Override
		public String batchKey () { return "Ping"; }

		@Override
		public Object batchId () { return id; }

	}

	@Test
	@DisplayName("互いを持ち合っていても、(batchKey, batchId) ごとに1回で止まる")
	void cycleStops () {

		Ping.BATCHES.set(0);

		Data data = new Data().putData("root", new Ping(1));

		AsyncPrefetch.Result result = AsyncPrefetch.run(data, 100);

		/* 1 を展開 → 2 が生える → 2 を展開 → 1 が生えるが展開済み。ここで終わり */
		assertEquals(2, result.rounds());
		assertEquals(2, Ping.BATCHES.get());
		assertEquals(2, result.filled());

	}

	@Test
	@DisplayName("同じオブジェクトが二度ぶら下がっていても、走査は止まる")
	void sharedNodeStops () {

		Data shared = new Data();
		shared.putData("self", shared);

		Data data = new Data()
			.putData("a", shared)
			.putData("b", shared);

		assertEquals(AsyncPrefetch.Result.NOTHING, AsyncPrefetch.run(data));

	}

	// endregion

	// region まとめて読めなかったとき

	/** まとめて読むと落ちるもの */
	static class Broken extends AsyncList {

		private static final long serialVersionUID = 1L;

		final long id;

		Broken (long id) { this.id = id; }

		@Override
		protected List<Data> load () {

			LOAD_COUNT.incrementAndGet();
			return List.of(new Data().putData("body", "b" + id));

		}

		@Override
		protected Map<Object, List<Data>> loadBatch (List<Object> ids) {

			throw new IllegalStateException("まとめては引けない");

		}

		@Override
		protected void setData (Data data) { add(data); }

		@Override
		protected String hashKey () { return "Broken:" + id; }

		@Override
		public String batchKey () { return "Broken"; }

		@Override
		public Object batchId () { return id; }

	}

	@Test
	@DisplayName("まとめて読むのに失敗したら、個別に読み直して結果は変わらない")
	void failedBatchFallsBack () {

		LOAD_COUNT.set(0);

		Data data = new Data().putData("items", List.of(new Broken(1), new Broken(2)));

		AsyncPrefetch.Result result = AsyncPrefetch.run(data);

		assertEquals(0, result.filled(), "1つも読み込み済みにしてはいけない");

		String json = data.getJsonString();

		assertEquals(2, LOAD_COUNT.get(), "個別に読み直す");
		assertTrue(json.contains("b1"), json);
		assertTrue(json.contains("b2"), json);

	}

	@Test
	@DisplayName("loadBatch を書いていないものは、そのまま個別に読まれる")
	void noLoadBatchFallsBack () {

		LOAD_COUNT.set(0);

		Data data = new Data().putData("items", List.of(new NoBatch(1), new NoBatch(2)));

		assertEquals(0, AsyncPrefetch.run(data).filled());

		data.getJsonString();

		assertEquals(2, LOAD_COUNT.get());

	}

	/** batchKey は返すが loadBatch を書いていない */
	static class NoBatch extends AsyncList {

		private static final long serialVersionUID = 1L;

		final long id;

		NoBatch (long id) { this.id = id; }

		@Override
		protected List<Data> load () {

			LOAD_COUNT.incrementAndGet();
			return List.of(new Data().putData("body", "n" + id));

		}

		@Override
		protected void setData (Data data) { add(data); }

		@Override
		protected String hashKey () { return "NoBatch:" + id; }

		@Override
		public String batchKey () { return "NoBatch"; }

		@Override
		public Object batchId () { return id; }

	}

	// endregion

	// region そのほか

	@Test
	@DisplayName("読み込み済みの枝の中にある未読み込みの枝も拾う")
	void walksIntoLoadedBranches () {

		BATCH_COUNT.set(0);

		Comments loaded = new Comments(1);
		loaded.putData(List.of(new Data().putData("child", new Comments(7))));

		assertTrue(loaded.isLoaded());

		Data data = new Data().putData("root", loaded);

		AsyncPrefetch.Result result = AsyncPrefetch.run(data);

		assertEquals(1, result.filled());
		assertEquals(1, BATCH_COUNT.get());

	}

	@Test
	@DisplayName("null を渡しても落ちない")
	void nullIsSafe () {

		assertEquals(AsyncPrefetch.Result.NOTHING, AsyncPrefetch.run(null));
		assertEquals(AsyncPrefetch.Result.NOTHING, AsyncPrefetch.run(new Data(), 0));

	}

	@Test
	@DisplayName("読み込み済みのものは二度読まない")
	void loadedIsSkipped () {

		BATCH_COUNT.set(0);

		Data data = tree(1, 2);

		AsyncPrefetch.run(data);
		assertEquals(1, BATCH_COUNT.get());

		AsyncPrefetch.run(data);
		assertEquals(1, BATCH_COUNT.get(), "2回目は何もしない");

	}

	@Test
	@DisplayName("先読みで埋めたものは、読み込み済みかつ失敗していない")
	void filledIsLoaded () {

		Data data = tree(1);

		AsyncPrefetch.run(data);

		Comments comments = (Comments) data.getDataList("posts").get(0).get("comments");

		assertNotNull(comments);
		assertTrue(comments.isLoaded());
		assertFalse(comments.isLoadFailed());

	}

	// endregion

}
