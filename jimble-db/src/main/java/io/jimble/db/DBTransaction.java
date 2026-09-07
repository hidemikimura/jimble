package io.jimble.db;

import io.jimble.core.context.Context;
import io.jimble.util.exception.CodeException;
import io.jimble.util.log.Log;

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
 *       実行（{@link Context}）の終わりに拾うようにした（要件 F-D-16）</li>
 * </ol>
 */
public class DBTransaction implements Closeable, AutoCloseable {

	/* DB */
	private final DB db;

	/* トランザクション中判定 */
	private final boolean isTransactional;

	/* 実行の終わりに拾ってもらうための登録（要件 F-D-16） */
	private Context.CloseTask closeTask;

	/**
	 * コンストラクタ
	 *
	 * @param db    DB
	 */
	public DBTransaction(DB db) {

		this.db = db;
		this.isTransactional = db.isTransaction();

	}

	/**
	 * トランザクションを開始する
	 * 他で開始済みの場合は処理しない
	 *
	 * @throws CodeException    エラー
	 */
	public void beginTransaction () throws CodeException {

		if (isTransactional) {
			return;
		}

		try {
			db.beginTransaction();
		} catch (Exception ex) {
			throw new CodeException("DB_001", "トランザクションの開始に失敗しました。");
		}

		register();

	}

	/**
	 * トランザクションをロールバックする
	 * 他で開始済みの場合は処理しない
	 *
	 * @throws CodeException    エラー
	 */
	public void rollback () throws CodeException {

		if (isTransactional) {
			return;
		}

		try {
			db.rollback();
		} catch (Exception ex) {
			throw new CodeException("DB_002", "トランザクションのロールバックに失敗しました。");
		}

	}

	/**
	 * トランザクションをロールバックする
	 * 他で開始済みの場合は処理しない
	 *
	 * @throws CodeException    エラー
	 */
	public void rollbackEndTransaction () throws CodeException {

		if (isTransactional) {
			return;
		}

		try {
			db.rollbackEndTransaction();
		} catch (Exception ex) {
			throw new CodeException("DB_002", "トランザクションのロールバックに失敗しました。");
		} finally {
			unregister();
		}

	}

	/**
	 * トランザクションをコミットする
	 * 他で開始済みの場合は処理しない
	 *
	 * @throws CodeException    エラー
	 */
	public void commit () throws CodeException {

		if (isTransactional) {
			return;
		}

		try {
			// 終わらせない。終わらせるのは commitEndTransaction()
			db.commit();
		} catch (Exception ex) {
			throw new CodeException("DB_003", "トランザクションのコミットに失敗しました。");
		}

	}

	/**
	 * トランザクションをコミットする
	 * 他で開始済みの場合は処理しない
	 *
	 * @throws CodeException    エラー
	 */
	public void commitEndTransaction () throws CodeException {

		if (isTransactional) {
			return;
		}

		try {
			db.commitEndTransaction();
		} catch (Exception ex) {
			throw new CodeException("DB_003", "トランザクションのコミットに失敗しました。");
		} finally {
			unregister();
		}

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 開いたままなら {@link DB#close()} がロールバックする。
	 * </p>
	 */
	@Override
	public void close() throws IOException {

		if (isTransactional) {
			return;
		}

		try {
			db.close();
		} finally {
			unregister();
		}

	}

	// region 畳み忘れを拾う（要件 F-D-16）

	/**
	 * 実行の終わりに拾ってもらう
	 */
	private void register () {

		if (closeTask != null || !Context.isBound()) {
			return;
		}

		closeTask = () -> {

			if (!db.isTransaction()) {
				return;
			}

			/*
			 * ここへ来たということは、コミットもロールバックもされずに
			 * 実行が終わったということである。
			 * 黙って戻すと「入ったつもりが入っていない」が残るので、
			 * エラーログを出す（要件 F-D-16）。
			 */
			Log.error("コミットもロールバックもされていないトランザクションが残っていました。ロールバックします");

			db.rollbackEndTransaction();

		};

		Context.current().onClose(closeTask);

	}

	/**
	 * 拾ってもらうのをやめる
	 */
	private void unregister () {

		if (closeTask == null || !Context.isBound()) {
			closeTask = null;
			return;
		}

		Context.current().removeCloseTask(closeTask);
		closeTask = null;

	}

	// endregion

	/**
	 * トランザクション
	 *
	 * @param consumer
	 * @throws Exception
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
	 * @param <T>
	 */
	@FunctionalInterface
	public interface TransactionConsumer<T> {

		void apply(T t) throws Exception;

	}

}
