package io.jimble.db;

import io.jimble.core.context.Context;
import io.jimble.util.exception.CodeException;

import java.io.Closeable;
import java.io.IOException;

/**
 * DBトランザクション
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>{@link #commit()} がトランザクションを終わらせていた。</b>
 *       中で {@code db.commitEndTransaction()} を呼んでおり、
 *       {@link #commitEndTransaction()} とまったく同じ動きだった。
 *       {@link #rollback()} は終わらせない（{@code db.rollback()}）ので、
 *       <b>コミットとロールバックで揃っていない。</b>
 *       「途中まで確定させて続ける」つもりで {@code commit()} を呼ぶと、
 *       <b>そこから先は自動コミットになる</b>（例外は出ない）</li>
 *   <li><b>畳み忘れを拾う先が無かった。</b>
 *       try-with-resources を使わずに {@code beginTransaction()} して
 *       途中で return すると、<b>ロールバックもされず接続もプールへ戻らない。</b>
 *       実行（{@link Context}）の終わりに拾うようにした（要件 F-D-16）。
 *       <b>拾うのは {@code DB} の側である。</b>ここに置くと
 *       <b>{@code db.beginTransaction()} を直に呼んだときに拾えない</b>ので、
 *       コネクションを握る当人（{@code DB}）へ下ろした</li>
 * </ol>
 *
 * @deprecated {@link DB#transaction(DB.TxBody)} / {@link DB#begin()}（{@link Tx}）を使う。
 *             検査例外を投げず、{@code Tx.commit()} は確定して終わる。2.0 で消す（要件 D-192）
 */
@Deprecated(since = "1.5.0", forRemoval = true)
public class DBTransaction implements Closeable, AutoCloseable {

	/* DB */
	private final DB db;

	/*
	 * 外のトランザクションに合流しているか。
	 *
	 * <b>作ったときと、beginTransaction() のときの両方で見る</b>（要件 D-190）。
	 * 1.4 までは作った瞬間だけを見ていたので、作ってから外が始まると、
	 * こちらの commitEndTransaction() が<b>外のトランザクションを終わらせていた</b>。
	 */
	private boolean joined;

	/* 合流したあと、決着（commit / rollback）を付けたか */
	private boolean joinedBegun = false;

	private boolean joinedSettled = false;

	/**
	 * コンストラクタ
	 *
	 * @param db    DB
	 */
	public DBTransaction(DB db) {

		this.db = db;
		this.joined = db.isTransaction();

	}

	/**
	 * トランザクションを開始する
	 *
	 * <p>
	 * <b>外で始まっていれば、それに合流する</b>（新しくは始めない）。
	 * 合流した側の {@link #commit()} は何もしない——確定させるのは外である。
	 * 合流した側の {@link #rollback()} と、commit せずに {@link #close()} したときは、
	 * <b>外を巻き戻し専用にする</b>。外の commit は DB_005 で断られる（要件 D-190）。
	 * </p>
	 *
	 * @throws CodeException    エラー
	 */
	public void beginTransaction () throws CodeException {

		if (!joined && db.isTransaction()) {
			joined = true;
		}

		if (joined) {
			joinedBegun = true;
			return;
		}

		try {
			// 実行の終わりに拾ってもらう登録は DB の側で行う（要件 F-D-16）
			db.txBegin();
		} catch (Exception ex) {
			throw new CodeException("DB_001", "トランザクションの開始に失敗しました: " + ex.getMessage(), ex);
		}

	}

	/**
	 * トランザクションをロールバックする（終わらせない）
	 *
	 * <p>合流している場合は、外を巻き戻し専用にする。</p>
	 *
	 * @throws CodeException    エラー
	 */
	public void rollback () throws CodeException {

		if (joined) {
			markJoinedRollback("中の DBTransaction が rollback() しました");
			return;
		}

		try {
			db.txRollback();
		} catch (Exception ex) {
			throw new CodeException("DB_002", "トランザクションのロールバックに失敗しました: " + ex.getMessage(), ex);
		}

	}

	/**
	 * トランザクションをロールバックして終わらせる
	 *
	 * <p>合流している場合は、外を巻き戻し専用にする。</p>
	 *
	 * @throws CodeException    エラー
	 */
	public void rollbackEndTransaction () throws CodeException {

		if (joined) {
			markJoinedRollback("中の DBTransaction が rollbackEndTransaction() しました");
			return;
		}

		try {
			db.txRollbackEnd();
		} catch (Exception ex) {
			throw new CodeException("DB_002", "トランザクションのロールバックに失敗しました: " + ex.getMessage(), ex);
		}

	}

	/**
	 * トランザクションをコミットする（終わらせない）
	 *
	 * <p>
	 * <b>終わらせない。</b>このあとに書いた分は、{@link #close()} で巻き戻る。
	 * 終わらせるのは {@link #commitEndTransaction()}。
	 * 合流している場合は何もしない（確定させるのは外）。
	 * </p>
	 *
	 * @throws CodeException    エラー
	 */
	public void commit () throws CodeException {

		if (joined) {
			joinedSettled = true;
			return;
		}

		try {
			// 終わらせない。終わらせるのは commitEndTransaction()
			db.txCommit();
		} catch (CodeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new CodeException("DB_003", "トランザクションのコミットに失敗しました: " + ex.getMessage(), ex);
		}

	}

	/**
	 * トランザクションをコミットして終わらせる
	 *
	 * <p>合流している場合は何もしない（確定させるのは外）。</p>
	 *
	 * @throws CodeException    エラー
	 */
	public void commitEndTransaction () throws CodeException {

		if (joined) {
			joinedSettled = true;
			return;
		}

		try {
			db.txCommitEnd();
		} catch (CodeException ex) {
			/*
			 * <b>包み直さない。</b>「エラーが出ているのでコミットしなかった」（DB_004 / DB_005）は
			 * <b>理由と直し方まで書いてある</b>ので、DB_003 で潰すと何も分からなくなる。
			 */
			throw ex;
		} catch (Exception ex) {
			throw new CodeException("DB_003", "トランザクションのコミットに失敗しました: " + ex.getMessage(), ex);
		}

	}

	private void markJoinedRollback (String reason) {

		joinedSettled = true;
		db.markRollbackOnly(reason);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 開いたままなら {@link DB#close()} がロールバックする。
	 * <b>合流している場合は、commit せずに閉じたら外を巻き戻し専用にする</b>
	 * （単独のときに「commit せずに閉じたら巻き戻る」のと揃える）。
	 * </p>
	 */
	@Override
	public void close() throws IOException {

		if (joined) {
			if (joinedBegun && !joinedSettled) {
				markJoinedRollback("中の DBTransaction が commit せずに閉じられました（例外で抜けた可能性があります）");
			}
			return;
		}

		db.close();

	}


	/**
	 * トランザクション
	 *
	 * <p>
	 * 中が例外なく終われば commit して終わらせ、例外が出れば巻き戻して投げ直す。
	 * 外で始まっていれば合流する（中で例外が出たら、外は巻き戻し専用になる）。
	 * </p>
	 *
	 * @param db		DB
	 * @param consumer	中身
	 * @throws Exception	中身の例外、またはコミットの失敗
	 */
	public static void transaction (DB db, TransactionConsumer<DBTransaction> consumer) throws Exception {

		try (
			DBTransaction transaction = new DBTransaction(db)
		) {

			transaction.beginTransaction();

			consumer.apply(transaction);

			transaction.commitEndTransaction();

		}

	}

	/**
	 * トランザクション内処理
	 *
	 * @param <T>	渡すもの
	 */
	@FunctionalInterface
	public interface TransactionConsumer<T> {

		/**
		 * 実行する
		 *
		 * @param t	渡すもの
		 * @throws Exception	エラー
		 */
		void apply(T t) throws Exception;

	}

}
