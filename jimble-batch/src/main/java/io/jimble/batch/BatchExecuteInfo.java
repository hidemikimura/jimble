package io.jimble.batch;

import io.jimble.db.DB;
import io.jimble.db.Tx;
import io.jimble.db.lock.DBLock;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.function.Supplier;

/**
 * 実行情報（{@code batch_execute_info}）と同時実行数の判定（要件 F-B-05）
 *
 * <p>
 * 起動するときの判定（{@link #register}）と、心拍が途切れたあとの判定（{@link #readmit}）を、
 * <b>同じロックの中で同じように数える</b>。どちらも「自分以外で生きている行」を数えるので、
 * 並んで判定しても同時実行数を超えない。
 * </p>
 */
final class BatchExecuteInfo {

	/** 同時実行数を数えるロックの名前の頭 */
	static final String LOCK_PREFIX = "batch_ace_";

	private BatchExecuteInfo () {
	}

	/**
	 * 実行情報を登録する（同時実行数の判定つき）
	 *
	 * @param db			DB
	 * @param args			引数
	 * @param className		クラス名
	 * @param allowCount	同時実行可能数（0 以下なら上限なし）
	 * @return	登録できた場合 = true
	 */
	static boolean register (DB db, BatchArgs args, String className, int allowCount) {

		if (allowCount <= 0) {
			insert(db, args, className);
			return true;
		}

		try {

			/*
			 * <b>ほかの台に譲らない</b>（2.7.14）。2.7.13 までは、ここで「いちばん抱えているバッチが少ない台か」（isMyTurn）を見て、
			 * そうでなければ飛ばしていた。cron の回は DbScheduler.claimFire で1台だけが取るので、譲った相手はその回を走らせず、
			 * <b>その回はどの台でも走らなかった</b>。空いている台に寄せるのは、回を取りにいく前に忙しい台が少し待つことで行う
			 * （DbScheduler の scheduler.claim_stagger）
			 */
			return underLock(db, className, () -> {

				if (othersAlive(db, className, args.uid()) >= allowCount) {
					return false;
				}

				insert(db, args, className);
				return true;

			});

		} catch (Exception ex) {

			Log.error(ex, "同時実行数の判定に失敗しました: %s".formatted(className));
			return false;

		}

	}

	/**
	 * 心拍が途切れたあとで、まだ数に入っていてよいかを確かめ直す（2.7.18）
	 *
	 * <p>
	 * <b>2.7.17 までは確かめ直さなかった。</b>共通の DB が止まると心拍が打てず、戻った直後の回が心拍より先に走ると、
	 * 動いているバッチの行は古いと見なされ（30 秒を超えていれば {@link BatchExecutor#cleanupExecuteInfo()} に消され）、
	 * 2 本目が起動した。1 本目の心拍は古い行を生き返らせるか、消えた行に 0 件当たるだけで、
	 * <b>止まるたびに 1 本ずつ増えた</b>（k 回で k+1 本。プロセスを起動し直すまで残る）。
	 * </p>
	 *
	 * <p>
	 * 起動するときと同じロックの中で、自分以外で生きている行を数える。
	 * 同時実行数に達していれば<b>自分の行を消して退く</b>（呼び出し側が中断の指示を立てる）。
	 * 達していなければ、自分の行を打ち直す（消されていれば入れ直す）。
	 * </p>
	 *
	 * @param db			DB
	 * @param args			引数
	 * @param className		クラス名
	 * @param allowCount	同時実行可能数（0 以下なら上限なし）
	 * @return	残ってよい場合 = true、退く場合 = false
	 * @throws io.jimble.db.SqlExecuteException	DB に届かなかったとき（次の心拍でもう一度確かめる）
	 */
	static boolean readmit (DB db, BatchArgs args, String className, int allowCount) {

		if (allowCount <= 0) {
			touchOrInsert(db, args, className);
			return true;
		}

		return underLock(db, className, () -> {

			if (othersAlive(db, className, args.uid()) >= allowCount) {
				db.delete("DELETE FROM batch_execute_info WHERE uid = ?", args.uid());
				return false;
			}

			touchOrInsert(db, args, className);
			return true;

		});

	}

	/**
	 * 同時実行数のロックの中で動かす（数えてから書くまでを1つにする）
	 *
	 * @param db			DB
	 * @param className		クラス名
	 * @param body			中身
	 * @param <T>			戻り値の型
	 * @return	中身の戻り値
	 */
	private static <T> T underLock (DB db, String className, Supplier<T> body) {

		String lockKey = LOCK_PREFIX + className;

		DBLock.create(db, lockKey);

		try (Tx tx = db.begin()) {

			DBLock.lock(db, lockKey);

			T result = body.get();

			tx.commit();

			return result;

		}

	}

	/**
	 * 自分以外で生きている数
	 *
	 * @param db			DB
	 * @param className		クラス名
	 * @param uid			自分の uid
	 * @return	件数
	 */
	private static int othersAlive (DB db, String className, String uid) {

		Data row = db.select("""
				SELECT
					COUNT(1) AS cnt
				FROM
					batch_execute_info
				WHERE
					class_name = ?
					AND uid <> ?
					AND updated_at >= %s
			""".formatted(db.dialect().intervalFromNow("SECOND", true))
			, className
			, uid
			, BatchConf.alive().toSeconds()).orElse(null);

		return row == null ? 0 : row.getInt("cnt");

	}

	/**
	 * 自分の行を打つ。消されていれば入れ直す
	 *
	 * @param db			DB
	 * @param args			引数
	 * @param className		クラス名
	 */
	private static void touchOrInsert (DB db, BatchArgs args, String className) {

		if (db.update("UPDATE batch_execute_info SET updated_at = NOW() WHERE uid = ?", args.uid()) > 0) {
			return;
		}

		// 0 件でも行がある場合がある（MySQL で、変わらなかった行を数えない接続の設定のとき）
		if (db.select("SELECT uid FROM batch_execute_info WHERE uid = ?", args.uid()).isEmpty()) {
			insert(db, args, className);
		}

	}

	/**
	 * 実行情報を入れる
	 *
	 * @param db			DB
	 * @param args			引数
	 * @param className		クラス名
	 * @throws io.jimble.db.SqlExecuteException	入れられなかったとき
	 */
	private static void insert (DB db, BatchArgs args, String className) {

		db.insert("""
				INSERT INTO batch_execute_info (
					uid, scheduler_id, class_name, created_at, updated_at
				) VALUES (
					?, ?, ?, NOW(), NOW()
				)
			"""
			, args.uid()
			, args.schedulerId()
			, className);

	}

}
