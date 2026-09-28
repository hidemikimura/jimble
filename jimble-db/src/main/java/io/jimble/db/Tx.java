package io.jimble.db;

import io.jimble.util.exception.CodeException;

/**
 * トランザクション（要件 D-191。2.0 の形）
 *
 * <pre>
 * try (Tx tx = db.begin()) {
 *     db.update(...);
 *     db.insert(...);
 *     tx.commit();            // 確定して終わる
 * }                           // commit せずに抜けたら巻き戻す（例外で抜けたときも）
 * </pre>
 *
 * <p>
 * ふつうは {@link DB#transaction(DB.TxBody)} のほうが短い。
 * </p>
 *
 * <h2>1.x の {@code DBTransaction} との違い（2.0 で消した）</h2>
 * <ul>
 *   <li><b>{@link #commit()} は終わらせる。</b>終わらせない確定は {@link #checkpoint()} という別の名前にした——
 *       {@code DBTransaction.commit()} は終わらせないので、そのあとに書いた分が {@code close()} で黙って巻き戻っていた</li>
 *   <li><b>検査例外を投げない。</b>失敗は {@link TransactionException}（非検査）</li>
 *   <li><b>2度目の {@code commit()} / {@code rollback()} は例外。</b>終わったものを確定したつもりにならない</li>
 * </ul>
 *
 * <h2>入れ子（合流）</h2>
 * <p>
 * すでにトランザクションが始まっている DB で {@link DB#begin()} すると、<b>新しくは始めず、外に合流する</b>。
 * 合流した側の {@link #commit()} と {@link #checkpoint()} は何もしない（確定させるのは外）。
 * {@link #rollback()} と、commit せずに閉じたときは、<b>外を巻き戻し専用にする</b>——
 * 外の commit は {@code DB_005} の {@link TransactionException} になり、全部が巻き戻る。
 * </p>
 *
 * @since 1.5.0
 */
public final class Tx implements AutoCloseable {

	private final DB db;

	private final boolean joined;

	private boolean finished = false;

	/**
	 * コンストラクタ（{@link DB#begin()} から）
	 *
	 * @param db		DB
	 * @param joined	外に合流したか
	 */
	Tx (DB db, boolean joined) {

		this.db = db;
		this.joined = joined;

	}

	/**
	 * 外のトランザクションに合流しているか
	 *
	 * @return	合流していれば true
	 */
	public boolean isJoined () {

		return joined;

	}

	/**
	 * 終わったか（commit か rollback をしたか）
	 *
	 * @return	終わっていれば true
	 */
	public boolean isFinished () {

		return finished;

	}

	/**
	 * 確定して終わる
	 *
	 * <p>
	 * 中で1度でもエラーが出ていたら確定せず、巻き戻して {@code DB_004} の
	 * {@link TransactionException} を投げる（中で SQL の失敗を catch して続けていても）。
	 * 合流している場合は何もしない。
	 * </p>
	 *
	 * @throws TransactionException	確定できなかった
	 * @throws IllegalStateException	もう終わっている
	 */
	public void commit () {

		requireOpen("commit()");
		finished = true;

		if (joined) {
			return;
		}

		try {
			db.txCommitEnd();
		} catch (CodeException ex) {
			throw new TransactionException(ex.getMessage(), ex);
		} catch (Exception ex) {
			throw new TransactionException("トランザクションのコミットに失敗しました: " + ex.getMessage(),
				new CodeException("DB_003", ex.getMessage(), ex));
		}

	}

	/**
	 * ここまでを確定して、続ける
	 *
	 * <p>
	 * <b>終わらせない。</b>このあとに書いた分は、{@link #commit()} しなければ巻き戻る。
	 * 合流している場合は何もしない（外を途中まで確定させることはできない）。
	 * </p>
	 *
	 * @throws TransactionException	確定できなかった
	 * @throws IllegalStateException	もう終わっている
	 */
	public void checkpoint () {

		requireOpen("checkpoint()");

		if (joined) {
			return;
		}

		try {
			db.txCommit();
		} catch (CodeException ex) {
			// DB_004 は DB の側で巻き戻し済み。トランザクションは続いている
			throw new TransactionException(ex.getMessage(), ex);
		} catch (Exception ex) {
			throw new TransactionException("トランザクションのコミットに失敗しました: " + ex.getMessage(),
				new CodeException("DB_003", ex.getMessage(), ex));
		}

	}

	/**
	 * 巻き戻して終わる
	 *
	 * <p>合流している場合は、外を巻き戻し専用にする。</p>
	 *
	 * @throws TransactionException	巻き戻せなかった
	 * @throws IllegalStateException	もう終わっている
	 */
	public void rollback () {

		requireOpen("rollback()");
		finished = true;

		if (joined) {
			db.markRollbackOnly("中の Tx が rollback() しました");
			return;
		}

		try {
			db.txRollbackEnd();
		} catch (Exception ex) {
			throw new TransactionException("トランザクションのロールバックに失敗しました: " + ex.getMessage(),
				new CodeException("DB_002", ex.getMessage(), ex));
		}

	}

	/**
	 * 閉じる
	 *
	 * <p>
	 * <b>終わっていなければ巻き戻す</b>（合流している場合は、外を巻き戻し専用にする）。
	 * 例外で抜けたときもここを通るので、書き忘れても半分だけ残ることはない。
	 * </p>
	 *
	 * @throws TransactionException	巻き戻せなかった
	 */
	@Override
	public void close () {

		if (finished) {
			return;
		}

		finished = true;

		if (joined) {
			db.markRollbackOnly("中の Tx が commit せずに閉じられました（例外で抜けた可能性があります）");
			return;
		}

		try {
			db.txRollbackEnd();
		} catch (Exception ex) {
			throw new TransactionException("トランザクションのロールバックに失敗しました: " + ex.getMessage(),
				new CodeException("DB_002", ex.getMessage(), ex));
		}

	}

	private void requireOpen (String what) {

		if (finished) {
			throw new IllegalStateException(
				"このトランザクションはもう終わっています（" + what + " は1度だけです。"
					+ "続けて確定したいなら checkpoint() を使ってください）");
		}

	}

}
