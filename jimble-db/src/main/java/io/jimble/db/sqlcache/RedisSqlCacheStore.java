package io.jimble.db.sqlcache;

import io.jimble.db.redis.RedisClient;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * <p>
 * <b>入れたキーは {@code jimble:sql_cache_all}、使ったタグの集合の名前は {@code jimble:sql_cache_all_tags} にも覚えておく</b>（D-296）。
 * 全部消すときはこの2つから取り出して消すので、Redis のほかのキーを見にいかない。
 * どちらも<b>期限の時刻を点数にした順序つきの集合</b>で、入れるたびに期限の過ぎたものを外す
 * （期限で消えた値の名前が、いつまでも残らない）。
 * </p>
 */
public final class RedisSqlCacheStore implements SqlCacheStore {

	/** 値の接頭辞 */
	public static final String PREFIX = "jimble:sql_cache:";

	/** タグの接頭辞 */
	public static final String TAG_PREFIX = "jimble:sql_cache_tag:";

	/** 入れたキーの集合（全部消すときに使う） */
	public static final String ALL_KEYS = "jimble:sql_cache_all";

	/** 使ったタグの集合の名前の集合（全部消すときに使う） */
	public static final String ALL_TAGS = "jimble:sql_cache_all_tags";

	/** 1回に取り出す数 */
	private static final int DRAIN_SIZE = 500;

	/* タグの集合を、値より長く持つ分 */
	private static final Duration TAG_MARGIN = Duration.ofMinutes(1);

	/* 期限なしの点数 */
	private static final double NO_EXPIRY = (double) Long.MAX_VALUE;

	/* 前の版が入れたもの（覚えておく集合に入っていない）を、もう走査して消したか */
	private final AtomicBoolean legacySwept = new AtomicBoolean(false);

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

		boolean expires = ttl != null && !ttl.isZero();
		long now = System.currentTimeMillis();
		double keyExpiry = expires ? now + ttl.toMillis() : NO_EXPIRY;
		double tagExpiry = expires ? now + ttl.plus(TAG_MARGIN).toMillis() : NO_EXPIRY;

		// 全部消すときのために覚えておく。期限の過ぎたものは、ここで外す
		batch.<String>getScoredSortedSet(ALL_KEYS, StringCodec.INSTANCE).addAsync(keyExpiry, key);
		batch.<String>getScoredSortedSet(ALL_KEYS, StringCodec.INSTANCE).removeRangeByScoreAsync(0, true, now, true);
		batch.<String>getScoredSortedSet(ALL_TAGS, StringCodec.INSTANCE).removeRangeByScoreAsync(0, true, now, true);

		for (String tag : tags) {

			batch.getSet(TAG_PREFIX + tag, StringCodec.INSTANCE).addAsync(key);
			batch.<String>getScoredSortedSet(ALL_TAGS, StringCodec.INSTANCE).addAsync(tagExpiry, TAG_PREFIX + tag);

			/*
			 * <b>タグの集合にも期限を付ける</b>（D-282）。2.5.1 までは付けておらず、書き換えの少ない表を
			 * いろいろなパラメータで引くと、もう無い値のキーが集合に溜まり続けた。
			 * 値の期限は全体で1つ（sql_cache.ttl）なので、足すたびに「値の期限 + 1 分」へ延ばせば、
			 * 集合が中の値より先に消えることは無い
			 */
			if (ttl != null && !ttl.isZero()) {
				batch.getSet(TAG_PREFIX + tag, StringCodec.INSTANCE).expireAsync(ttl.plus(TAG_MARGIN));
			}

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
	 *
	 * <p>
	 * <b>全部のタグの取り出しを、1回の往復にまとめる</b>（D-273）。2.5.1 まではタグごとに往復していたので、
	 * {@code UPDATE ... WHERE id IN (100 個)} は 100 回以上の往復を順に待っていた。
	 * </p>
	 */
	@Override
	public void invalidate (Set<String> tags) {

		List<String> sets = new ArrayList<>(tags.size());

		for (String tag : tags) {
			sets.add(TAG_PREFIX + tag);
		}

		drainAndDelete(sets);

	}

	/**
	 * 集合から取り出したキーの値を消す
	 *
	 * <p>
	 * 消したキーは {@link #ALL_KEYS} からも外す（外さないと、無期限のときに集合が育ち続ける）。
	 * </p>
	 *
	 * @param sets	キーの集合の名前
	 */
	private void drainAndDelete (List<String> sets) {

		List<String> pending = new ArrayList<>(sets);

		while (!pending.isEmpty()) {

			RBatch batch = RedisClient.client().createBatch();
			List<RFuture<Set<String>>> drained = new ArrayList<>(pending.size());

			for (String set : pending) {
				drained.add(batch.<String>getSet(set, StringCodec.INSTANCE).removeRandomAsync(DRAIN_SIZE));
			}

			batch.execute();

			Set<String> names = new LinkedHashSet<>();
			Set<String> members = new LinkedHashSet<>();
			List<String> more = new ArrayList<>();

			for (int i = 0; i < pending.size(); i++) {

				Set<String> taken = drained.get(i).toCompletableFuture().join();

				if (taken == null || taken.isEmpty()) {
					continue;
				}

				for (String member : taken) {
					names.add(PREFIX + member);
					members.add(member);
				}

				// 取り切れていなければ、次の回でもう一度
				if (taken.size() >= DRAIN_SIZE) {
					more.add(pending.get(i));
				}

			}

			if (!names.isEmpty()) {
				RedisClient.client().getKeys().delete(names.toArray(String[]::new));
				RedisClient.client().<String>getScoredSortedSet(ALL_KEYS, StringCodec.INSTANCE).removeAll(members);
			}

			pending = more;

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear () {

		/*
		 * <b>覚えておいた集合から取り出して消す</b>（D-296）。2.5.4 までは deleteByPattern で
		 * {@code jimble:sql_cache:*} と {@code jimble:sql_cache_tag:*} を消していたので、
		 * 生 SQL で更新するたびに <b>Redis の全部のキー</b>（セッションなども）を走査していた。
		 */
		drainScored(ALL_KEYS, PREFIX);
		drainScored(ALL_TAGS, "");

		/*
		 * <b>前の版が入れたものは、覚えておく集合に入っていない。</b>このプロセスで最初に全部消すときだけ、
		 * これまでどおり走査して消す（上げたあとに古い結果が残らない）
		 */
		if (legacySwept.compareAndSet(false, true)) {
			RedisClient.client().getKeys().deleteByPattern(PREFIX + "*");
			RedisClient.client().getKeys().deleteByPattern(TAG_PREFIX + "*");
		}

	}

	/**
	 * 順序つきの集合から取り出して、その名前のキーを消す
	 *
	 * @param set		集合の名前
	 * @param prefix	取り出した名前の前に付けるもの
	 */
	private static void drainScored (String set, String prefix) {

		RScoredSortedSet<String> names = RedisClient.client().getScoredSortedSet(set, StringCodec.INSTANCE);

		while (true) {

			Collection<String> taken = names.pollFirst(DRAIN_SIZE);

			if (taken == null || taken.isEmpty()) {
				return;
			}

			RedisClient.client().getKeys().delete(taken.stream().map(name -> prefix + name).toArray(String[]::new));

			if (taken.size() < DRAIN_SIZE) {
				return;
			}

		}

	}

}
