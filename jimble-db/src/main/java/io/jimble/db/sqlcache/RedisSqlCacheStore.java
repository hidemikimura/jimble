package io.jimble.db.sqlcache;

import io.jimble.db.redis.RedisClient;
import org.redisson.api.RBatch;
import org.redisson.api.RSet;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Set;

/**
 * Redis に置く（要件 F-D-28）
 *
 * <p>
 * 値は {@code jimble:sql_cache:<キー>}、
 * タグは {@code jimble:sql_cache_tag:<タグ>}（キーの集合）に持つ。
 * </p>
 *
 * <p>
 * <b>タグ→キーの集合には期限を置かない。</b>
 * 値のほうが期限で消えても集合には名前が残るが、
 * 消すときに空振りするだけで害はない。
 * 集合は<b>消したときにその場で片づける</b>。
 * </p>
 */
public final class RedisSqlCacheStore implements SqlCacheStore {

	/** 値の接頭辞 */
	public static final String PREFIX = "jimble:sql_cache:";

	/** タグの接頭辞 */
	public static final String TAG_PREFIX = "jimble:sql_cache_tag:";

	/** 1回に取り出す数 */
	private static final int DRAIN_SIZE = 500;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String get (String key) {

		return RedisClient.client().<String>getBucket(PREFIX + key, StringCodec.INSTANCE).get();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put (String key, Set<String> tags, String value, Duration ttl) {

		if (tags.isEmpty()) {
			return;
		}

		RBatch batch = RedisClient.client().createBatch();

		if (ttl == null || ttl.isZero()) {
			batch.getBucket(PREFIX + key, StringCodec.INSTANCE).setAsync(value);
		} else {
			batch.getBucket(PREFIX + key, StringCodec.INSTANCE).setAsync(value, ttl);
		}

		for (String tag : tags) {
			batch.getSet(TAG_PREFIX + tag, StringCodec.INSTANCE).addAsync(key);
		}

		batch.execute();

	}

	/**
	 * {@inheritDoc}
	 */
	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>集合を「読んでから消す」のではなく、まとめて取り出す</b>（{@code SPOP}）。
	 * 読んでから集合ごと消すと、その隙間に別のスレッドが
	 * {@code put} で足したキーの<b>索引だけが消えて値が残る</b>。
	 * 残った値はどのタグからも辿れず、期限が来るまで古いまま返り続ける。
	 * </p>
	 */
	@Override
	public void invalidate (Set<String> tags) {

		for (String tag : tags) {

			RSet<String> index = RedisClient.client().getSet(TAG_PREFIX + tag, StringCodec.INSTANCE);

			while (true) {

				Set<String> members = index.removeRandom(DRAIN_SIZE);

				if (members == null || members.isEmpty()) {
					break;
				}

				String[] names = new String[members.size()];

				int i = 0;
				for (String member : members) {
					names[i++] = PREFIX + member;
				}

				RedisClient.client().getKeys().delete(names);

				if (members.size() < DRAIN_SIZE) {
					break;
				}

			}

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear () {

		RedisClient.client().getKeys().deleteByPattern(PREFIX + "*");
		RedisClient.client().getKeys().deleteByPattern(TAG_PREFIX + "*");

	}

}
