package io.jimble.batch;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 *   <li><b>途切れたら確かめ直す</b>（2.7.18）。打てなかった・打った行が足りなかった・スレッドが止まっていた
 *       （{@code batch.alive} の半分より間が空いた）バッチは、次に DB に届いたときに
 *       同時実行数を数え直す（{@link BatchExecuteInfo#readmit}）。途切れているあいだに 2 本目が起動していれば、こちらが退く。
 *       確かめ直さないと、共通の DB が止まるたびに同じバッチが 1 本ずつ増えた</li>
 * </ul>
 */
final class BatchHeartbeats {

	/** 1本の UPDATE に並べる数の上限（IN の中が長くなりすぎないように） */
	static final int CHUNK = 500;

	/** スレッドの名前 */
	static final String THREAD_NAME = "jimble-batch-heartbeat";

	/* 打つ相手（uid → 確かめ直す処理） */
	private static final Map<String, Runnable> BEATS = new ConcurrentHashMap<>();

	/* 心拍が途切れたかもしれないので、確かめ直す相手 */
	private static final Set<String> SUSPECT = ConcurrentHashMap.newKeySet();

	/* 待ちを解く印 */
	private static final Object WAKE = new Object();

	/* いまのスレッド（動いていなければ null） */
	private static Thread thread = null;

	private BatchHeartbeats () {
	}

	/**
	 * 打ち始める
	 *
	 * @param uid		実行情報の uid
	 * @param readmit	心拍が途切れたあとに確かめ直す処理（DB に届かなければ例外を投げる。次の心拍でもう一度呼ぶ）
	 */
	static void add (String uid, Runnable readmit) {

		BEATS.put(uid, readmit);

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

		BEATS.remove(uid);
		SUSPECT.remove(uid);

		if (BEATS.isEmpty()) {
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

		return BEATS.size();

	}

	/**
	 * 心拍のループ
	 */
	private static void loop () {

		long lastReached = System.nanoTime();

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
				if (BEATS.isEmpty() || Thread.currentThread().isInterrupted()) {
					thread = null;
					return;
				}
			}

			List<String> uids = new ArrayList<>(BEATS.keySet());

			// 打てていても、間が空いていれば行は古いと見なされたかもしれない（GC やマシンの一時停止）
			if (System.nanoTime() - lastReached > BatchConf.alive().dividedBy(2).toNanos()) {
				SUSPECT.addAll(uids);
			}

			Beat result = beat(uids);

			SUSPECT.addAll(result.missed());

			// DB に届かなかった回は確かめ直さない（止まっているあいだに、バッチの数だけ接続を待たない）
			if (result.reached()) {
				lastReached = System.nanoTime();
				readmitSuspects();
			}

		}

	}

	/**
	 * 1回打った結果
	 *
	 * @param reached	DB に届いた（1つでも UPDATE が通った）
	 * @param missed	打てなかった・打った行が足りなかった相手
	 */
	record Beat(boolean reached, List<String> missed) {
	}

	/**
	 * 1回打つ
	 *
	 * @param uids	打つ相手
	 * @return	結果
	 */
	static Beat beat (List<String> uids) {

		boolean reached = false;
		List<String> missed = new ArrayList<>();

		for (int from = 0; from < uids.size(); from += CHUNK) {

			List<String> chunk = uids.subList(from, Math.min(uids.size(), from + CHUNK));

			/*
			 * <b>失敗しても次の間隔で打ち直す</b>（2.0 から DB の失敗は例外なので、受け止めないとスレッドが終わる）。
			 */
			try {

				DB db = DBUtil.getMainDB();
				int updated = db.update("UPDATE batch_execute_info SET updated_at = NOW() WHERE uid IN (%s)"
					.formatted(String.join(", ", Collections.nCopies(chunk.size(), "?")))
					, chunk.toArray());

				reached = true;

				// 足りなければ、どれかの行が消されている（どれかは確かめ直しで分かる）
				if (updated < chunk.size()) {
					missed.addAll(chunk);
				}

			} catch (RuntimeException ex) {
				Log.error(ex, "バッチの心拍を打てませんでした。次の間隔で打ち直します: %d 件".formatted(chunk.size()));
				missed.addAll(chunk);
			}

		}

		return new Beat(reached, List.copyOf(missed));

	}

	/**
	 * 途切れたかもしれない相手を確かめ直す
	 */
	private static void readmitSuspects () {

		for (String uid : new ArrayList<>(SUSPECT)) {

			Runnable readmit = BEATS.get(uid);

			if (readmit == null) {
				SUSPECT.remove(uid);
				continue;
			}

			try {

				readmit.run();
				SUSPECT.remove(uid);

				/*
				 * <b>確かめ直しているあいだにバッチが終わっていれば、入れ直した行を消す</b>。
				 * バッチは一覧から外して（remove）から行を消すので、ここで外れていなければ、行を消すのはバッチのほうが後になる
				 */
				if (!BEATS.containsKey(uid)) {
					DBUtil.getMainDB().delete("DELETE FROM batch_execute_info WHERE uid = ?", uid);
				}

			} catch (RuntimeException ex) {
				Log.error(ex, "心拍が途切れたバッチの同時実行数を確かめ直せませんでした。次の間隔でもう一度確かめます: uid=%s".formatted(uid));
			}

		}

	}

}
