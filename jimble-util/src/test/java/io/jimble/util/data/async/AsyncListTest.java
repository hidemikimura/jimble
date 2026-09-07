package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遅延読み込みリストのテスト（要件 F-A-01〜05 / F-A-09 / F-A-10 / F-D-27）
 */
class AsyncListTest {

	@AfterEach
	void resetLog () {

		Log.resetSink();

	}

	// region テスト用の実装

	/**
	 * 数えられる遅延読み込みリスト
	 */
	static class CountingList extends AsyncList {

		/** load() が呼ばれた回数 */
		final AtomicInteger loadCount = new AtomicInteger();

		/** setData が呼ばれた回数 */
		final AtomicInteger setDataCount = new AtomicInteger();

		/** 呼ばれた順 */
		final List<String> calls = new ArrayList<>();

		/** 失敗させるか */
		boolean fails = false;

		private final String key;

		CountingList (String key) {

			this.key = key;

		}

		@Override
		protected List<Data> load () {

			loadCount.incrementAndGet();

			if (fails) {
				throw new IllegalStateException("読めない");
			}

			return List.of(
				new Data().putData("name", "A")
				, new Data().putData("name", "B")
				, new Data().putData("name", "C")
			);

		}

		@Override
		protected void setData (Data data) {

			setDataCount.incrementAndGet();
			calls.add("setData:" + data.getString("name"));
			add(data);

		}

		@Override
		protected void setRelationData (List<Data> dataList) {

			calls.add("setRelationData:" + dataList.size());

		}

		@Override
		protected String hashKey () {

			return "CountingList:" + key;

		}

	}

	// endregion

	// region 遅延（要件 F-A-01 / F-A-03）

	@Test
	@DisplayName("参照されるまで読まない")
	void loadsOnAccess () {

		CountingList list = new CountingList("a");

		assertEquals(0, list.loadCount.get());
		assertEquals(3, list.size());
		assertEquals(1, list.loadCount.get());

	}

	@Test
	@DisplayName("setRelationData は全件の setData のあとに1回だけ")
	void relationDataAfterAll () {

		// 要件 F-A-03
		CountingList list = new CountingList("a");
		list.size();

		assertEquals(List.of("setData:A", "setData:B", "setData:C", "setRelationData:3"), list.calls);

	}

	@Test
	@DisplayName("何度参照しても読むのは1回")
	void loadsOnce () {

		CountingList list = new CountingList("a");

		list.size();
		list.get(0);
		list.iterator();
		list.stream().count();

		assertEquals(1, list.loadCount.get());

	}

	// endregion

	// region 直した穴：内部配列を直接見るメソッド

	@Test
	@DisplayName("toArray も読む")
	void toArrayLoads () {

		/*
		 * 移送元はここが抜けていた。ArrayList.toArray() は内部配列を直接見るので、
		 * 未読み込みのリストが黙って空の配列になっていた。
		 */
		CountingList list = new CountingList("a");

		assertEquals(3, list.toArray().length, "空の配列が返っている");
		assertEquals(3, list.toArray(new Object[0]).length);

	}

	@Test
	@DisplayName("コピーしても中身が消えない")
	void copyLoads () {

		// new ArrayList<>(list) は toArray() を通るので、移送元では空になっていた
		assertEquals(3, new ArrayList<>(new CountingList("a")).size(), "コピーが空になっている");
		assertEquals(3, List.copyOf(new CountingList("b")).size());

	}

	@Test
	@DisplayName("indexOf / lastIndexOf / subList / getFirst / getLast も読む")
	void indexBasedLoads () {

		/*
		 * いずれも内部配列を直接見る。移送元では indexOf は -1、
		 * subList(0, 2) は範囲外で例外、
		 * getFirst / getLast は NoSuchElementException になっていた。
		 */
		CountingList list = new CountingList("a");
		Object first = list.get(0);

		assertEquals(0, new CountingList("b").indexOf(first));
		assertEquals(0, new CountingList("c").lastIndexOf(first));
		assertEquals(2, new CountingList("d").subList(0, 2).size());
		assertEquals("A", ((Data) new CountingList("e").getFirst()).getString("name"));
		assertEquals("C", ((Data) new CountingList("f").getLast()).getString("name"));

	}

	@Test
	@DisplayName("JSON にすると中身が出る")
	void jsonHasContent () {

		Data parent = new Data()
			.putData("id", 1L)
			.putData("items", new CountingList("a"));

		String json = parent.getJsonString();

		assertTrue(json.contains("\"A\""), json);
		assertTrue(json.contains("\"C\""), json);

	}

	@Test
	@DisplayName("Dson で直接書き出しても中身が出る")
	void dsonHasContent () {

		String json = Dson.encodes(new CountingList("a"));

		assertTrue(json.contains("\"A\""), json);

	}

	// endregion

	// region 読み込みを起こさないもの

	@Test
	@DisplayName("状態を見るだけでは読まない")
	void stateDoesNotLoad () {

		CountingList list = new CountingList("a");

		assertFalse(list.isLoaded());
		assertFalse(list.isLoadFailed());
		assertNull(list.batchKey());
		assertNull(list.batchId());
		assertEquals(0, list.loadedSize());
		assertTrue(list.loadedValues().isEmpty());

		assertEquals(0, list.loadCount.get(), "状態を見ただけで読んでいる");

	}

	@Test
	@DisplayName("比較しただけでは読まない")
	void compareDoesNotLoad () {

		/*
		 * 移送元は hashCode() だけ hashKey() 基準にしていて equals() が無かった。
		 * ArrayList.equals() は内部配列を見るので、
		 * 未読み込みどうしは中身に関係なく等しくなっていた。
		 */
		CountingList a1 = new CountingList("a");
		CountingList a2 = new CountingList("a");
		CountingList b = new CountingList("b");

		assertEquals(a1, a2);
		assertEquals(a1.hashCode(), a2.hashCode());
		assertFalse(a1.equals(b), "キーが違うのに等しいと言っている");

		Set<AsyncList> set = new HashSet<>();
		set.add(a1);
		set.add(a2);
		set.add(b);

		assertEquals(2, set.size());
		assertEquals(0, a1.loadCount.get() + a2.loadCount.get() + b.loadCount.get());

	}

	@Test
	@DisplayName("toString しただけでは読まない")
	void toStringDoesNotLoad () {

		CountingList list = new CountingList("a");

		assertEquals("CountingList(未読み込み)", list.toString());
		assertEquals(0, list.loadCount.get(), "toString で読んでいる");

		list.size();

		assertEquals("CountingList(3件)", list.toString());

	}

	@Test
	@DisplayName("読み込み済みの分だけ取り出せる")
	void loadedValues () {

		CountingList list = new CountingList("a");

		assertTrue(list.loadedValues().isEmpty());

		list.size();

		assertEquals(3, list.loadedValues().size());
		assertEquals(1, list.loadCount.get());

	}

	// endregion

	// region 失敗したとき（要件 F-A-09）

	@Test
	@DisplayName("読み込みに失敗しても例外を投げず、状態が残る")
	void loadFailure () {

		List<String> errorLog = new ArrayList<>();
		Log.sink((loggerName, level, message, data, throwable) -> errorLog.add(String.valueOf(message)));

		CountingList list = new CountingList("a");
		list.fails = true;

		assertEquals(0, list.size());
		assertTrue(list.isLoaded());
		assertTrue(list.isLoadFailed());
		assertFalse(errorLog.isEmpty());

		list.size();
		assertEquals(1, list.loadCount.get(), "落ちるクエリを何度も投げている");

	}

	// endregion

	// region 先読みの下ごしらえ（要件 F-A-10）

	@Test
	@DisplayName("putData で読み込まずに入れられる")
	void putDataSkipsLoad () {

		CountingList list = new CountingList("a");

		list.putData(List.of(new Data().putData("name", "外から")));

		assertEquals(1, list.size());
		assertEquals("外から", ((Data) list.get(0)).getString("name"));
		assertEquals(0, list.loadCount.get());
		assertEquals(List.of("setData:外から", "setRelationData:1"), list.calls);

	}

	// endregion

}
