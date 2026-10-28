package io.jimble.mq;

import io.jimble.db.redis.RedisClient;
import io.jimble.util.log.Log;

import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 「このデータソースのこのキューに仕事がある」という印（{@code mq.hint}。2.7.8）
 *
 * <p>
 * データソースを何十も並べると、取り出し役は空のデータソースまで毎周見に行く（暇でも、データソースの数に比例して SELECT が出る）。
 * 積んだときに印を付けておき、取り出し役は<b>印の付いたデータソースだけ</b>をすぐ見に行く。
 * 全部を回るのは {@code mq.sweep_interval} ごとだけにする。
 * </p>
 *
 * <ul>
 * <li>{@code memory}：同じプロセスの中だけ。積んだら、そのプロセスの取り出し役をすぐ起こす</li>
 * <li>{@code redis}：それに加えて Redis の集合に入れる。別のプロセス（Web で積み、バッチで取り出す）でも効く</li>
 * </ul>
 *
 * <p>
 * <b>印は「見に行く順を早める」ためのもの</b>で、無くても取りこぼさない（落ちても、全体を回る周で拾う）。
 * だから、Redis が止まっていても、積む側も取り出す側も失敗させない（ログを出すだけ）。
 * </p>
 */
final class MqHints {

	/** 印を見てから、そのデータソースを見続ける時間（積んだ側のトランザクションがまだ確定していなくても、取りこぼさない） */
	static final Duration WATCH = Duration.ofSeconds(5);

	/** 同じ印を Redis に送り直すまでの間（積むたびに Redis へ行かない） */
	static final Duration RESEND = Duration.ofSeconds(1);

	/** Redis が使えないと言うログの間 */
	private static final Duration WARN_INTERVAL = Duration.ofMinutes(1);

	/** Redis の集合の名前の頭 */
	static final String KEY_PREFIX = "jimble:mq:hint:";

	/** 1回に Redis から取る印の数の上限 */
	private static final int TAKE_LIMIT = 1000;

	/* キュー名 → データソース名 → 見に行ってよい時刻（ミリ秒） */
	private static final Map<String, Map<String, Long>> DUE = new ConcurrentHashMap<>();

	/* キュー名 → このプロセスの取り出し役を起こすもの */
	private static final Map<String, Set<Semaphore>> WAKES = new ConcurrentHashMap<>();

	/* 「キュー名 / データソース名」 → Redis に送った時刻（ミリ秒） */
	private static final Map<String, Long> SENT = new ConcurrentHashMap<>();

	/* Redis が使えないと最後に言った時刻（ミリ秒） */
	private static final AtomicLong WARNED = new AtomicLong();

	private MqHints () {}

	/**
	 * いま仕事がある印を付ける（積んだとき）
	 *
	 * @param queueName		キュー名
	 * @param dataSource	データソース名
	 */
	static void mark (String queueName, String dataSource) {

		markAt(queueName, dataSource, System.currentTimeMillis());

	}

	/**
	 * その時刻から仕事がある印を付ける（時刻を指定して積んだとき・リトライ）
	 *
	 * <p>
	 * 先の時刻の印はこのプロセスの中だけに持つ（Redis には送らない。ほかのプロセスは全体を回る周で拾う）。
	 * </p>
	 *
	 * @param queueName		キュー名
	 * @param dataSource	データソース名
	 * @param dueMillis		見に行ってよい時刻（ミリ秒）
	 */
	static void markAt (String queueName, String dataSource, long dueMillis) {

		MqConf.Hint hint = MqConf.hint();

		if (hint == MqConf.Hint.off) {
			return;
		}

		DUE.computeIfAbsent(queueName, key -> new ConcurrentHashMap<>()).merge(dataSource, dueMillis, Math::min);

		long now = System.currentTimeMillis();

		if (dueMillis > now) {
			return;
		}

		wake(queueName);

		if (hint == MqConf.Hint.redis) {
			sendRemote(queueName, dataSource, now);
		}

	}

	/**
	 * Redis に印を入れる（同じ印は {@link #RESEND} の間に1回だけ）
	 *
	 * @param queueName		キュー名
	 * @param dataSource	データソース名
	 * @param now			いま（ミリ秒）
	 */
	static void sendRemote (String queueName, String dataSource, long now) {

		String sentKey = queueName + "/" + dataSource;
		Long sent = SENT.get(sentKey);

		if (sent != null && now - sent < RESEND.toMillis()) {
			return;
		}

		SENT.put(sentKey, now);

		try {
			RedisClient.client().<String>getSet(KEY_PREFIX + queueName, StringCodec.INSTANCE).add(dataSource);
		} catch (RuntimeException ex) {
			// 印は無くても取りこぼさない。積むのは止めない
			SENT.remove(sentKey);
			warn("MQ の印を Redis に入れられませんでした（全体を回る周で拾います）: %s".formatted(queueName), ex);
		}

	}

	/**
	 * 見に行ってよくなった印を取る
	 *
	 * @param queueName	キュー名
	 * @param now		いま（ミリ秒）
	 * @param remote	Redis からも取る場合 = true
	 * @return	データソース名
	 */
	static Set<String> take (String queueName, long now, boolean remote) {

		Set<String> taken = new HashSet<>();

		Map<String, Long> due = DUE.get(queueName);
		if (due != null) {
			due.forEach((dataSource, at) -> {
				if (at <= now && due.remove(dataSource, at)) {
					taken.add(dataSource);
				}
			});
		}

		if (remote) {
			try {
				taken.addAll(RedisClient.client().<String>getSet(KEY_PREFIX + queueName, StringCodec.INSTANCE).removeRandom(TAKE_LIMIT));
			} catch (RuntimeException ex) {
				warn("MQ の印を Redis から読めませんでした（全体を回る周で拾います）: %s".formatted(queueName), ex);
			}
		}

		return taken;

	}

	/**
	 * このプロセスの取り出し役を登録する（印が付いたら起こす）
	 *
	 * @param queueName	キュー名
	 * @param wake		起こすもの
	 */
	static void register (String queueName, Semaphore wake) {

		WAKES.computeIfAbsent(queueName, key -> ConcurrentHashMap.newKeySet()).add(wake);

	}

	/**
	 * 登録を外す
	 *
	 * @param queueName	キュー名
	 * @param wake		起こすもの
	 */
	static void unregister (String queueName, Semaphore wake) {

		Set<Semaphore> wakes = WAKES.get(queueName);
		if (wakes != null) {
			wakes.remove(wake);
		}

	}

	/**
	 * 印をすべて消す（テスト用）
	 */
	static void clear () {

		DUE.clear();
		SENT.clear();

	}

	private static void wake (String queueName) {

		Set<Semaphore> wakes = WAKES.get(queueName);
		if (wakes != null) {
			wakes.forEach(Semaphore::release);
		}

	}

	private static void warn (String message, RuntimeException ex) {

		long now = System.currentTimeMillis();
		long last = WARNED.get();

		if (now - last >= WARN_INTERVAL.toMillis() && WARNED.compareAndSet(last, now)) {
			Log.warn(message + " " + ex.getMessage());
		}

	}

}
