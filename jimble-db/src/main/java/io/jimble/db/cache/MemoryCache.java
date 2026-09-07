package io.jimble.db.cache;

import io.jimble.util.conf.Conf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * メモリキャッシュ
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>{@code HashMap} を複数スレッドから触っていた。</b>プロセス内で共有する
 *       static なマップなので、仮想スレッドから同時に読み書きすると壊れる。
 *       {@code ConcurrentHashMap} にした</li>
 *   <li><b>{@code set()} が成功時に {@code false} を返していた。</b>
 *       他の実装（Redis）は成功で {@code true} を返す。戻り値を見た側が失敗と判断する</li>
 *   <li><b>{@code has(key, group)} が別のマップを別のキーで見ていた。</b>
 *       {@code cacheGroupMap.containsKey(key)} となっており、
 *       <b>キャッシュの有無をまったく判定できていなかった</b></li>
 *   <li>有効期限の設定をクラス初期化時に読んでいた（設定の読み込み直しが効かない）</li>
 *   <li><b>期限の計算でミリ秒から「秒」を引いていた。</b>{@code cache.memory.expire = 60} なら
 *       60 <b>ミリ秒</b>で消えるため、設定した瞬間にキャッシュがほぼ効かなくなる</li>
 *   <li><b>{@code tryLock()} に失敗しても {@code unlock()} していた。</b>
 *       ロックを持たないスレッドが {@code IllegalMonitorStateException} を投げる</li>
 * </ol>
 */
public class MemoryCache extends AbstractCache {

	/** 設定キー：有効期限（秒） */
	public static final String KEY_EXPIRE = "cache.memory.expire";

	/* キャッシュ情報 */
	private static final Map<String, CacheData> cacheMap = new ConcurrentHashMap<>();

	/* グループ情報 */
	private static final Map<String, List<String>> cacheGroupMap = new ConcurrentHashMap<>();

	/**
	 * 有効期限（秒）
	 *
	 * @return	秒数（0 以下で無期限）
	 */
	private static long expireSecond () {

		return Conf.conf().getLong(KEY_EXPIRE, 0);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key) {

		if (cacheMap.containsKey(key)) {
			return cacheMap.get(key).contentString();
		}

		return "";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key, String group) {

		return getString(key);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getStringGroup(String group) {

		List<String> list = cacheGroupMap.get(group);
		if (list == null) {
			return null;
		}

		List<String> res = new ArrayList<>();
		for (String key : list) {

			if (cacheMap.containsKey(key)) {
				res.add(cacheMap.get(key).contentString());
			} else {
				res.add("");
			}

		}

		return res;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheData get(String key) {

		return get(key, null);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheData get(String key, String group) {

		if (cacheMap.containsKey(key)) {
			return cacheMap.get(key);
		}

		return new CacheData(key, group);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CacheData> getGroup(String group) {

		List<String> list = cacheGroupMap.get(group);
		if (list == null) {
			return null;
		}

		List<CacheData> res = new ArrayList<>();
		for (String key : list) {

			if (cacheMap.containsKey(key)) {
				res.add(cacheMap.get(key));
			} else {
				res.add(new CacheData(key, group));
			}

		}

		return res;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean set(String key, String value, String contentType) {

		return set(key, value, contentType, null);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean set(String key, String value, String contentType, String group) {

		if (group != null && !group.isEmpty()) {
			cacheGroupMap.computeIfAbsent(group, k -> Collections.synchronizedList(new ArrayList<>())).add(key);
		}

		cacheMap.put(key, new CacheData(key, group, value, contentType, new Date()));

		cleanExpired();

		// 移送元はここで false を返していた（成功なのに失敗に見える）
		return true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(String key) {

		cacheMap.remove(key);

		for (Map.Entry<String, List<String>> entry : cacheGroupMap.entrySet()) {
			entry.getValue().remove(key);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeGroup(String group) {

		if (group == null || group.isEmpty()) {
			return;
		}

		List<String> keys = new ArrayList<>();
		for (String key : cacheMap.keySet()) {
			CacheData cacheData = cacheMap.get(key);
			if (group.equals(cacheData.groupKey())) {
				keys.add(key);
			}
		}

		for (String key : keys) {
			cacheMap.remove(key);
		}

		cacheGroupMap.remove(group);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean has(String key, String group) {

		// 移送元は cacheGroupMap.containsKey(key) で、まったく判定できていなかった
		CacheData cacheData = cacheMap.get(key);

		if (cacheData == null) {
			return false;
		}

		return group == null || group.isEmpty() || group.equals(cacheData.groupKey());

	}


	/* 期限チェック用ロック */
	private static final ReentrantLock cleanLock = new ReentrantLock();

	/**
	 * 期限チェック
	 */
	private static void cleanExpired () {

		long expireSecond = expireSecond();

		if (expireSecond <= 0) {
			return;
		}

		/*
		 * 掃除は誰か1人がやれば足りる。取れなければ何もしない。
		 *
		 * 移送元は tryLock() が false でも finally で unlock() していたため、
		 * ロックを持っていないスレッドが IllegalMonitorStateException を投げていた。
		 */
		if (!cleanLock.tryLock()) {
			return;
		}

		try {

			// 移送元はミリ秒から「秒」を引いていた（expire=60 で 60 ミリ秒で消える）
			long expire = System.currentTimeMillis() - expireSecond * 1000;

			List<String> keys = new ArrayList<>();
			for (Map.Entry<String, CacheData> entry : cacheMap.entrySet()) {
				CacheData cacheData = entry.getValue();
				if (cacheData != null && cacheData.objectCreatedAt().getTime() <= expire) {
					keys.add(entry.getKey());
				}
			}

			for (String key : keys) {
				cacheMap.remove(key);
			}

		} finally {

			cleanLock.unlock();

		}

	}

}
