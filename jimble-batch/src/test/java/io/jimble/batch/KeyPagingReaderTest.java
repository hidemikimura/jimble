package io.jimble.batch;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * キー順ページングの読み取り（要件 F-B-12）
 *
 * <p>DB は要らない。ページを返すものを差し替えて確かめる。</p>
 */
class KeyPagingReaderTest {

	/**
	 * id が 1..count の行を用意する
	 *
	 * @param count	件数
	 * @return	行
	 */
	private static List<Data> rows (int count) {

		List<Data> rows = new ArrayList<>();

		for (int i = 1; i <= count; i++) {
			rows.add(new Data().putData("id", (long) i).putData("name", "n" + i));
		}

		return rows;

	}

	/**
	 * 「id より大きいものを limit 件」を、用意した行に対して行う
	 *
	 * @param all		全部の行
	 * @param lastKey	前のページの最後のキー
	 * @param limit		件数
	 * @return	ページ
	 */
	private static List<Data> page (List<Data> all, Object lastKey, int limit) {

		long from = ((Number) lastKey).longValue();

		return all.stream()
			.filter(row -> row.getLong("id") > from)
			.limit(limit)
			.toList();

	}

	@Test
	@DisplayName("ページをまたいで全件を順に読む")
	void readsAcrossPages () {

		List<Data> all = rows(10);

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> page(all, lastKey, limit));

		List<Long> got = new ArrayList<>();

		while (reader.hasNext()) {
			got.add(reader.next().getLong("id"));
		}

		assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), got);

	}

	@Test
	@DisplayName("ちょうど割り切れるときも最後まで読む")
	void readsWhenDivisible () {

		List<Data> all = rows(6);

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> page(all, lastKey, limit));

		int count = 0;

		while (reader.hasNext()) {
			reader.next();
			count++;
		}

		assertEquals(6, count);

	}

	@Test
	@DisplayName("満たないページで終わり、空を確かめるためだけに引き直さない")
	void stopsOnShortPage () {

		List<Data> all = rows(5);
		AtomicInteger fetches = new AtomicInteger();

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> {
			fetches.incrementAndGet();
			return page(all, lastKey, limit);
		});

		while (reader.hasNext()) {
			reader.next();
		}

		// 3件 + 2件。2件目で「満たない」と分かるので、3回目は引かない
		assertEquals(2, fetches.get());

	}

	@Test
	@DisplayName("ちょうど割り切れるときは空を1回引いて終わる")
	void fetchesEmptyWhenDivisible () {

		List<Data> all = rows(6);
		AtomicInteger fetches = new AtomicInteger();

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> {
			fetches.incrementAndGet();
			return page(all, lastKey, limit);
		});

		while (reader.hasNext()) {
			reader.next();
		}

		// 3件 + 3件 + 空。満杯で返った以上、次があるかは引かないと分からない
		assertEquals(3, fetches.get());

	}

	@Test
	@DisplayName("1件も無ければ1回引いて終わる")
	void empty () {

		AtomicInteger fetches = new AtomicInteger();

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> {
			fetches.incrementAndGet();
			return List.of();
		});

		assertFalse(reader.hasNext());
		assertEquals(1, fetches.get());

		// 何度呼んでも引き直さない
		assertFalse(reader.hasNext());
		assertEquals(1, fetches.get());

	}

	@Test
	@DisplayName("null が返っても終わりとして扱う")
	void nullPage () {

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> null);

		assertFalse(reader.hasNext());

	}

	@Test
	@DisplayName("hasNext を何度呼んでもページは1回しか引かない")
	void hasNextDoesNotRefetch () {

		List<Data> all = rows(4);
		AtomicInteger fetches = new AtomicInteger();

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> {
			fetches.incrementAndGet();
			return page(all, lastKey, limit);
		});

		assertTrue(reader.hasNext());
		assertTrue(reader.hasNext());
		assertTrue(reader.hasNext());

		assertEquals(1, fetches.get());

	}

	@Test
	@DisplayName("読み終わった後の next() は例外")
	void nextAfterEnd () {

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> List.of());

		assertThrows(NoSuchElementException.class, reader::next);

	}

	@Test
	@DisplayName("キーが進まないと例外になる（無限ループにしない）")
	@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	void keyDoesNotAdvance () {

		/*
		 * WHERE の向きと ORDER BY が食い違っている、
		 * あるいはキーが一意でないと、同じページが返り続ける。
		 * 黙って回り続けるより落ちたほうがよい。
		 */
		List<Data> same = rows(3);

		KeyPagingReader reader = KeyPagingReader.of("id", 3L, 3, (lastKey, limit) -> same);

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
			while (reader.hasNext()) {
				reader.next();
			}
		});

		assertTrue(ex.getMessage().contains("キーが進みません"), ex.getMessage());

	}

	@Test
	@DisplayName("キーの列が結果に無ければ例外")
	void keyColumnMissing () {

		List<Data> rows = List.of(new Data().putData("name", "a"));

		KeyPagingReader reader = KeyPagingReader.of("id", 0L, 3, (lastKey, limit) -> rows);

		IllegalStateException ex = assertThrows(IllegalStateException.class, reader::next);

		assertTrue(ex.getMessage().contains("キーの列が結果にありません"), ex.getMessage());

	}

	@Test
	@DisplayName("開始キーより後ろから読む")
	void startKey () {

		List<Data> all = rows(10);

		KeyPagingReader reader = KeyPagingReader.of("id", 7L, 3, (lastKey, limit) -> page(all, lastKey, limit));

		List<Long> got = new ArrayList<>();

		while (reader.hasNext()) {
			got.add(reader.next().getLong("id"));
		}

		assertEquals(List.of(8L, 9L, 10L), got);

	}

	@Test
	@DisplayName("文字列のキーでも読める")
	void stringKey () {

		List<Data> all = List.of(
			new Data().putData("code", "a"),
			new Data().putData("code", "b"),
			new Data().putData("code", "c"));

		KeyPagingReader reader = KeyPagingReader.of("code", "", 2, (lastKey, limit) -> all.stream()
			.filter(row -> row.getString("code").compareTo(String.valueOf(lastKey)) > 0)
			.limit(limit)
			.toList());

		List<String> got = new ArrayList<>();

		while (reader.hasNext()) {
			got.add(reader.next().getString("code"));
		}

		assertEquals(List.of("a", "b", "c"), got);

	}

	@Test
	@DisplayName("作るときの指定が変なら例外")
	void badArguments () {

		assertThrows(IllegalArgumentException.class,
			() -> KeyPagingReader.of("", 0L, 3, (lastKey, limit) -> List.of()));

		assertThrows(IllegalArgumentException.class,
			() -> KeyPagingReader.of("id", 0L, 0, (lastKey, limit) -> List.of()));

		assertThrows(IllegalArgumentException.class,
			() -> KeyPagingReader.of("id", 0L, 3, null));

	}

}
