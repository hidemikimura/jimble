package io.jimble.batch;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 実行中のバッチの心拍（プロセスで1本。要件 F-B-05）
 *
 * <p>
 * <b>バッチ1本ごとにスレッドを立てていた。</b>心拍のスレッドはそれぞれが定期的に DB の接続を借りるので、
 * DB の応答が遅れると<b>動いているバッチの数だけ接続が埋まる</b>。いまは1本のスレッドが、
 * 実行中のバッチ全部を <b>1本の {@code UPDATE ... WHERE uid IN (...)}</b> で打つ。
 * </p>
 *
 * <ul>
 *   <li><b>要るときだけ動く。</b>実行中のバッチがいなくなればスレッドは終わり、次の {@link #add} で立ち直る</li>
 *   <li><b>失敗してもループを続ける。</b>1回の失敗で止まると、同時実行数から外れて同じバッチがもう1本起動できる</li>
 *   <li><b>止めるときに割り込まない。</b>JDBC の実行中に割り込むと、その接続が壊れたものとしてプールから捨てられる。
 *       {@link #remove} は一覧から外すだけで、いま出ている UPDATE はそのまま終わらせる
 *       （外したあとの UPDATE は、消えた行に当たらないだけである）</li>
 * </ul>
 */
final class BatchHeartbeats {

	/** 1本の UPDATE に並べる数の上限（IN の中が長くなりすぎないように） */
	static final int CHUNK = 500;

	/** スレッドの名前 */
	static final String THREAD_NAME = "jimble-batch-heartbeat";

	/* 打つ相手 */
	private static final Set<String> UIDS = ConcurrentHashMap.newKeySet();

	/* 待ちを解く印 */
	private static final Object WAKE = new Object();

	/* いまのスレッド（動いていなければ null） */
	private static Thread thread = null;

	private BatchHeartbeats () {
	}

	/**
	 * 打ち始める
	 *
	 * @param uid	実行情報の uid
	 */
	static void add (String uid) {

		UIDS.add(uid);

		synchronized (BatchHeartbeats.class) {

			if (thread == null || !thread.isAlive()) {
				thread = Thread.ofPlatform().daemon().name(THREAD_NAME).start(BatchHeartbeats::loop);
			}

		}

	}

	/**
	 * 打つのをやめる
	 *
	 * @param uid	実行情報の uid
	 */
	static void remove (String uid) {

		UIDS.remove(uid);

		if (UIDS.isEmpty()) {
			synchronized (WAKE) {
				WAKE.notifyAll();
			}
		}

	}

	/**
	 * 打っている数（テストから）
	 *
	 * @return	数
	 */
	static int size () {

		return UIDS.size();

	}

	/**
	 * 心拍のループ
	 */
	private static void loop () {

		while (true) {

			synchronized (WAKE) {
				try {
					WAKE.wait(BatchConf.heartbeat().toMillis());
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}

			/*
			 * <b>誰もいなければ終わる</b>（次の add で立ち直る）。
			 * 終わる判定と立ち直る判定を同じ鍵の中で行う——でないと、
			 * 「終わると決めた直後に add された uid」を誰も打たなくなる。
			 */
			synchronized (BatchHeartbeats.class) {
				if (UIDS.isEmpty() || Thread.currentThread().isInterrupted()) {
					thread = null;
					return;
				}
			}

			beat(new ArrayList<>(UIDS));

		}

	}

	/**
	 * 1回打つ
	 *
	 * @param uids	打つ相手
	 */
	static void beat (List<String> uids) {

		for (int from = 0; from < uids.size(); from += CHUNK) {

			List<String> chunk = uids.subList(from, Math.min(uids.size(), from + CHUNK));

			/*
			 * <b>失敗しても次の間隔で打ち直す</b>（2.0 から DB の失敗は例外なので、受け止めないとスレッドが終わる）。
			 */
			try {
				DB db = DBUtil.getMainDB();
				db.update("UPDATE batch_execute_info SET updated_at = NOW() WHERE uid IN (%s)"
					.formatted(String.join(", ", java.util.Collections.nCopies(chunk.size(), "?")))
					, chunk.toArray());
			} catch (RuntimeException ex) {
				Log.error(ex, "バッチの心拍を打てませんでした。次の間隔で打ち直します: %d 件".formatted(chunk.size()));
			}

		}

	}

}
