package io.jimble.mq;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.log.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 処理中の行の {@code updated_at} を延ばし続ける（プロセスで1本。{@link MqExecutor#keepAlive()}）
 *
 * <p>
 * {@code mq.stale} より長くかかる処理を、<b>まだ動いているのに戻さない</b>ためのもの。
 * {@code mq.stale} の 1/3 ごとに、延ばしている行をデータソースとキューごとに
 * <b>1本の {@code UPDATE ... WHERE id IN (...)}</b> でまとめて打つ。
 * </p>
 *
 * <ul>
 *   <li><b>プロセスが落ちれば打たれなくなる</b>ので、迷子の戻し（{@code mq.stale}）はそのまま効く。
 *       {@code mq.stale} を長くするのと違い、落ちたときに戻るまでの時間は延びない</li>
 *   <li><b>要るときだけ動く。</b>延ばしている行がなくなればスレッドは終わり、次の {@link #add} で立ち直る</li>
 *   <li><b>失敗してもループを続ける。</b>次の間隔で打ち直す</li>
 *   <li><b>止めるときに割り込まない</b>（JDBC の実行中に割り込むと、その接続が捨てられる）。
 *       {@link #remove} は一覧から外すだけ。外したあとの UPDATE は、もう running でない行には当たらない</li>
 * </ul>
 *
 * <p>
 * 仕組みは {@code BatchHeartbeats}（バッチの心拍）と同じ。
 * </p>
 */
final class MqHeartbeats {

	/** 1本の UPDATE に並べる数の上限（IN の中が長くなりすぎないように） */
	static final int CHUNK = 500;

	/** スレッドの名前 */
	static final String THREAD_NAME = "jimble-mq-keep-alive";

	/** 打つ間隔の下限 */
	static final Duration MIN_INTERVAL = Duration.ofMillis(100);

	/**
	 * 打つ先（データソースとキュー）
	 *
	 * @param dbName	データソースの名前
	 * @param queueName	キューのテーブル名
	 */
	record Target (String dbName, String queueName) {}

	/* 打つ相手（打つ先ごとの行の id） */
	private static final Map<Target, Set<Long>> IDS = new ConcurrentHashMap<>();

	/* 待ちを解く印 */
	private static final Object WAKE = new Object();

	/* いまのスレッド（動いていなければ null） */
	private static Thread thread = null;

	private MqHeartbeats () {
	}

	/**
	 * 延ばし始める
	 *
	 * @param target	打つ先
	 * @param id		行の id
	 */
	static void add (Target target, long id) {

		IDS.computeIfAbsent(target, key -> ConcurrentHashMap.newKeySet()).add(id);

		synchronized (MqHeartbeats.class) {

			if (thread == null || !thread.isAlive()) {
				thread = Thread.ofPlatform().daemon().name(THREAD_NAME).start(MqHeartbeats::loop);
			}

		}

	}

	/**
	 * 延ばすのをやめる
	 *
	 * @param target	打つ先
	 * @param id		行の id
	 */
	static void remove (Target target, long id) {

		Set<Long> ids = IDS.get(target);

		if (ids != null) {
			ids.remove(id);
		}

		if (size() == 0) {
			synchronized (WAKE) {
				WAKE.notifyAll();
			}
		}

	}

	/**
	 * 延ばしている数（テストから）
	 *
	 * @return	数
	 */
	static int size () {

		return IDS.values().stream().mapToInt(Set::size).sum();

	}

	/**
	 * 打つ間隔（{@code mq.stale} の 1/3。3 回打ち損ねるまでは戻されない）
	 *
	 * @return	間隔
	 */
	static Duration interval () {

		Duration third = MqConf.stale().dividedBy(3);

		return third.compareTo(MIN_INTERVAL) < 0 ? MIN_INTERVAL : third;

	}

	/**
	 * ループ
	 */
	private static void loop () {

		while (true) {

			synchronized (WAKE) {
				try {
					WAKE.wait(interval().toMillis());
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}

			/*
			 * <b>誰もいなければ終わる</b>（次の add で立ち直る）。
			 * 終わる判定と立ち直る判定を同じ鍵の中で行う——でないと、
			 * 「終わると決めた直後に add された行」を誰も打たなくなる。
			 */
			synchronized (MqHeartbeats.class) {
				if (size() == 0 || Thread.currentThread().isInterrupted()) {
					thread = null;
					return;
				}
			}

			IDS.forEach((target, ids) -> beat(target, new ArrayList<>(ids)));

		}

	}

	/**
	 * 1回打つ
	 *
	 * @param target	打つ先
	 * @param ids		行の id
	 */
	static void beat (Target target, List<Long> ids) {

		for (int from = 0; from < ids.size(); from += CHUNK) {

			List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + CHUNK));

			List<Object> params = new ArrayList<>(chunk.size() + 1);
			params.add(MqStatus.running.name());
			params.addAll(chunk);

			/*
			 * <b>running の行だけを延ばす。</b>終わった行（消した・dead にした）や、
			 * 戻された行（waiting）には当たらない。失敗しても次の間隔で打ち直す
			 */
			try {
				DB db = DBUtil.getDB(target.dbName());
				db.update("UPDATE %s SET updated_at = NOW() WHERE status = ? AND id IN (%s)"
						.formatted(db.dialect().identifier(target.queueName()), String.join(", ", Collections.nCopies(chunk.size(), "?")))
					, params.toArray());
			} catch (RuntimeException ex) {
				Log.error(ex, "MQ の処理中の印を延ばせませんでした。次の間隔で打ち直します: %s / %s / %d 件"
					.formatted(target.dbName(), target.queueName(), chunk.size()));
			}

		}

	}

}
