package io.jimble.db.cache;

import java.time.Duration;
import io.jimble.util.conf.Conf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

	/*
	 * グループ情報（グループ → 入れた順のキー。重ねない）。
	 * 2.5.1 までは List で、同じキーを入れ直すたびに足し、期限で消えても外さなかったので増え続けた（D-283）
	 */
	private static final Map<String, Set<String>> cacheGroupMap = new ConcurrentHashMap<>();

	/* 期限切れを最後に掃除した時刻 */
	private static final java.util.concurrent.atomic.AtomicLong lastClean = new java.util.concurrent.atomic.AtomicLong(0);

	/** 期限切れを掃除する間隔の下限（ミリ秒） */
	static final long CLEAN_INTERVAL_MILLIS = 1000;

	/**
	 * 有効期限（秒）
	 *
	 * @return	秒数（0 以下で無期限）
	 */
	private static long expireSecond () {

		return Conf.conf().getDuration(KEY_EXPIRE, Duration.ZERO).toSeconds();

	}

	/**
	 * 生きているものを引く（期限が切れていれば消して null）
	 *
	 * <p>
	 * <b>読むときにも期限を見る</b>（D-283）。2.5.1 までは掃除（入れるときに走る）でしか消えなかったので、
	 * 入れる人がいなければ、期限を過ぎたものをいつまでも返した。
	 * </p>
	 */
	private static CacheData live (String key) {

		CacheData cacheData = cacheMap.get(key);

		if (cacheData == null) {
			return null;
		}

		long expireSecond = expireSecond();

		if (expireSecond > 0 && cacheData.objectCreatedAt().getTime() <= System.currentTimeMillis() - expireSecond * 1000) {
			removeEntry(key, cacheData);
			return null;
		}

		return cacheData;

	}

	/**
	 * 1件消す（グループからも外す）
	 */
	private static void removeEntry (String key, CacheData cacheData) {

		if (!cacheMap.remove(key, cacheData)) {
			return;
		}

		ungroup(key, cacheData.groupKey());

	}

	/**
	 * グループから外す
	 */
	private static void ungroup (String key, String group) {

		if (group == null || group.isEmpty()) {
			return;
		}

		Set<String> keys = cacheGroupMap.get(group);

		if (keys == null) {
			return;
		}

		synchronized (keys) {
			keys.remove(key);
			if (keys.isEmpty()) {
				cacheGroupMap.remove(group, keys);
			}
		}

	}

	/**
	 * グループのキー（写し）
	 */
	private static List<String> groupKeys (String group) {

		Set<String> keys = cacheGroupMap.get(group);

		if (keys == null) {
			return null;
		}

		synchronized (keys) {
			return new ArrayList<>(keys);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key) {

		CacheData cacheData = live(key);

		return cacheData == null ? "" : cacheData.contentString();

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

		List<String> list = groupKeys(group);
		if (list == null) {
			return null;
		}

		List<String> res = new ArrayList<>();
		for (String key : list) {
			CacheData cacheData = live(key);
			res.add(cacheData == null ? "" : cacheData.contentString());
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

		CacheData cacheData = live(key);

		return cacheData == null ? new CacheData(key, group) : cacheData;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CacheData> getGroup(String group) {

		List<String> list = groupKeys(group);
		if (list == null) {
			return null;
		}

		List<CacheData> res = new ArrayList<>();
		for (String key : list) {
			CacheData cacheData = live(key);
			res.add(cacheData == null ? new CacheData(key, group) : cacheData);
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

		CacheData previous = cacheMap.put(key, new CacheData(key, group, value, contentType, new Date()));

		// 別のグループで入れ直したら、前のグループから外す
		if (previous != null && !Objects.equals(previous.groupKey(), group)) {
			ungroup(key, previous.groupKey());
		}

		if (group != null && !group.isEmpty()) {
			Set<String> keys = cacheGroupMap.computeIfAbsent(group, k -> Collections.synchronizedSet(new LinkedHashSet<>()));
			synchronized (keys) {
				keys.add(key);
			}
		}

		cleanExpired();

		// 移送元はここで false を返していた（成功なのに失敗に見える）
		return true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(String key) {

		CacheData cacheData = cacheMap.remove(key);

		// 2.5.1 までは全部のグループを見て回っていた
		if (cacheData != null) {
			ungroup(key, cacheData.groupKey());
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

		List<String> keys = groupKeys(group);

		if (keys != null) {
			for (String key : keys) {
				CacheData cacheData = cacheMap.get(key);
				if (cacheData != null && group.equals(cacheData.groupKey())) {
					cacheMap.remove(key, cacheData);
				}
			}
		}

		cacheGroupMap.remove(group);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean has(String key, String group) {

		// 移送元は cacheGroupMap.containsKey(key) で、まったく判定できていなかった
		CacheData cacheData = live(key);

		if (cacheData == null) {
			return false;
		}

		return group == null || group.isEmpty() || group.equals(cacheData.groupKey());

	}

	/**
	 * 期限切れを掃除する（多くて {@value #CLEAN_INTERVAL_MILLIS} ミリ秒に1回）
	 *
	 * <p>
	 * <b>2.5.1 までは入れるたびに全件を見ていた</b>（D-283）。件数が多いと、入れるたびに全部をなめる。
	 * 読むときにも期限を見るようになったので、掃除はメモリを返すためだけで、間が空いても正しさは変わらない。
	 * </p>
	 */
	private static void cleanExpired () {

		long expireSecond = expireSecond();

		if (expireSecond <= 0) {
			return;
		}

		long now = System.currentTimeMillis();
		long last = lastClean.get();

		// 掃除は誰か1人がやれば足りる。間が空いていなければ、取れなければ何もしない
		if (now - last < CLEAN_INTERVAL_MILLIS || !lastClean.compareAndSet(last, now)) {
			return;
		}

		// 移送元はミリ秒から「秒」を引いていた（expire=60 で 60 ミリ秒で消える）
		long expire = now - expireSecond * 1000;

		for (Map.Entry<String, CacheData> entry : cacheMap.entrySet()) {
			CacheData cacheData = entry.getValue();
			if (cacheData != null && cacheData.objectCreatedAt().getTime() <= expire) {
				removeEntry(entry.getKey(), cacheData);
			}
		}

	}

	/**
	 * 中身を全部捨てる（テストから）
	 */
	static void clearAll () {

		cacheMap.clear();
		cacheGroupMap.clear();
		lastClean.set(0);

	}

}
