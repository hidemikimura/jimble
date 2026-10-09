package io.jimble.db.sqlcache;

import io.jimble.util.data.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * メモリに置く（要件 F-D-28）
 *
 * <p>
 * <b>この台の中でしか効かない。</b>複数台で動かすなら
 * {@code sql_cache.store = "redis"} か {@code "db"} にすること。
 * 1台目で消しても2台目には伝わらず、<b>台によって古いデータが出る</b>。
 * </p>
 *
 * <p>
 * 件数が上限を超えたら<b>古い順に捨てる</b>（{@code sql_cache.max}）。
 * 捨てても引き直すだけなので、上限に当たっても壊れない。
 * </p>
 *
 * <p>
 * <b>結果は書き出さずに、行のまま持つ</b>（D-296）。入れるときと渡すときに複製する（{@link SqlCacheRows}）。
 * 2.5.4 までは Redis・DB と同じく Java の直列化で書き出した文字列を持っていたので、
 * <b>ヒットするたびに全部の行を読み戻していた</b>（100 行 × 10 列で 1 回 約 130µs。複製なら 約 7µs）。
 * 複製できない型が入っていたときだけ、これまでどおり書き出して持つ。
 * </p>
 */
public final class MemorySqlCacheStore implements SqlCacheStore {

	/* 値。挿入順を保つ（古い順に捨てるため） */
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	/* タグ → キー */
	private final Map<String, Set<String>> byTag = new ConcurrentHashMap<>();

	/* 排他 */
	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * 1件（{@code value} と {@code rows} のどちらか一方だけが入る）
	 *
	 * @param value		書き出した値
	 * @param rows		行のまま持つ結果（誰にも渡さない。渡すときは複製する）
	 * @param tags		依存するタグ
	 * @param expiresAt	期限。0 なら無期限
	 */
	private record Entry(String value, List<Data> rows, Set<String> tags, long expiresAt) {

		boolean isExpired (long now) {

			return expiresAt > 0 && expiresAt <= now;

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String get (String key) {

		Entry entry = lookup(key);

		if (entry == null) {
			return null;
		}

		if (entry.value() != null) {
			return entry.value();
		}

		try {
			return SqlCache.serialize(entry.rows());
		} catch (Exception ex) {
			// 行のまま持てたものは、書き出せる型だけでできている
			throw new IllegalStateException("SQL結果キャッシュの値を書き出せませんでした: " + key, ex);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Data> getRows (String key) throws Exception {

		Entry entry = lookup(key);

		if (entry == null) {
			return null;
		}

		// 複製は鍵の外で（ほかのスレッドを待たせない）
		return entry.rows() != null ? SqlCacheRows.copy(entry.rows()) : SqlCache.deserialize(entry.value());

	}

	/**
	 * 期限内の1件を探す
	 *
	 * @param key	キー
	 * @return	1件。無いか期限切れなら null
	 */
	private Entry lookup (String key) {

		lock.lock();

		try {

			Entry entry = entries.get(key);

			if (entry == null) {
				return null;
			}

			if (entry.isExpired(System.currentTimeMillis())) {
				removeEntry(key, entry);
				return null;
			}

			return entry;

		} finally {

			lock.unlock();

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put (String key, Set<String> tags, String value, Duration ttl) {

		store(key, tags, value, null, ttl);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putRows (String key, Set<String> tags, List<Data> rows, Duration ttl) throws Exception {

		if (tags.isEmpty()) {
			return;
		}

		// 入れたあとで呼んだ側が書き換えても変わらないように、複製を持つ
		List<Data> copied = SqlCacheRows.copy(rows);

		if (copied == null) {
			// 複製できない型が入っている。これまでどおり書き出して持つ
			store(key, tags, SqlCache.serialize(rows), null, ttl);
			return;
		}

		store(key, tags, null, copied, ttl);

	}

	/**
	 * 入れる
	 *
	 * @param key	キー
	 * @param tags	依存するタグ
	 * @param value	書き出した値（rows と片方だけ）
	 * @param rows	行のまま持つ結果（value と片方だけ）
	 * @param ttl	期限。0 なら無期限
	 */
	private void store (String key, Set<String> tags, String value, List<Data> rows, Duration ttl) {

		if (tags.isEmpty()) {
			// 何にも紐づいていないものは、消す手立てが無い
			return;
		}

		lock.lock();

		try {

			Entry old = entries.remove(key);
			if (old != null) {
				unlink(key, old.tags());
			}

			long expiresAt = ttl == null || ttl.isZero() ? 0 : System.currentTimeMillis() + ttl.toMillis();

			entries.put(key, new Entry(value, rows, Set.copyOf(tags), expiresAt));

			for (String tag : tags) {
				byTag.computeIfAbsent(tag, name -> ConcurrentHashMap.newKeySet()).add(key);
			}

			evict();

		} finally {

			lock.unlock();

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invalidate (Set<String> tags) {

		lock.lock();

		try {

			for (String tag : tags) {

				Set<String> keys = byTag.remove(tag);

				if (keys == null) {
					continue;
				}

				for (String key : new ArrayList<>(keys)) {

					Entry entry = entries.remove(key);

					if (entry != null) {
						unlink(key, entry.tags());
					}

				}

			}

		} finally {

			lock.unlock();

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear () {

		lock.lock();

		try {

			entries.clear();
			byTag.clear();

		} finally {

			lock.unlock();

		}

	}

	/**
	 * 上限を超えたぶんを古い順に捨てる
	 */
	private void evict () {

		int max = SqlCacheConf.max();

		Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();

		while (entries.size() > max && iterator.hasNext()) {

			Map.Entry<String, Entry> entry = iterator.next();
			iterator.remove();
			unlink(entry.getKey(), entry.getValue().tags());

		}

	}

	/**
	 * 1件消す
	 *
	 * @param key	キー
	 * @param entry	中身
	 */
	private void removeEntry (String key, Entry entry) {

		entries.remove(key);
		unlink(key, entry.tags());

	}

	/**
	 * タグからの参照を外す
	 *
	 * @param key	キー
	 * @param tags	タグ
	 */
	private void unlink (String key, Set<String> tags) {

		for (String tag : tags) {

			Set<String> keys = byTag.get(tag);

			if (keys == null) {
				continue;
			}

			keys.remove(key);

			if (keys.isEmpty()) {
				byTag.remove(tag);
			}

		}

	}

	/**
	 * 持っている件数（テスト用）
	 *
	 * @return	件数
	 */
	public int size () {

		lock.lock();

		try {
			return entries.size();
		} finally {
			lock.unlock();
		}

	}

	/**
	 * 覚えているタグの数（テスト用）
	 *
	 * @return	タグ数
	 */
	public int tagCount () {

		return byTag.size();

	}

}
