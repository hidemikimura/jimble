package io.jimble.db.sqlcache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
 */
public final class MemorySqlCacheStore implements SqlCacheStore {

	/* 値。挿入順を保つ（古い順に捨てるため） */
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	/* タグ → キー */
	private final Map<String, Set<String>> byTag = new ConcurrentHashMap<>();

	/* 排他 */
	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * 1件
	 *
	 * @param value		値
	 * @param tags		依存するタグ
	 * @param expiresAt	期限。0 なら無期限
	 */
	private record Entry(String value, Set<String> tags, long expiresAt) {

		boolean isExpired (long now) {

			return expiresAt > 0 && expiresAt <= now;

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String get (String key) {

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

			return entry.value();

		} finally {

			lock.unlock();

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put (String key, Set<String> tags, String value, Duration ttl) {

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

			entries.put(key, new Entry(value, Set.copyOf(tags), expiresAt));

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
