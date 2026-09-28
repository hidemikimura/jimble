package io.jimble.db;

import io.jimble.util.exception.CodeException;

/**
 * トランザクションを確定・巻き戻しできなかった（要件 D-191）
 *
 * <p>
 * {@link Tx} と {@link DB#transaction(DB.TxBody)} が投げる。<b>非検査例外</b>なので、
 * ラムダの中でも {@code throws} を書かずに使える。
 * </p>
 *
 * <p>
 * {@link #getCode()} は、元になった {@link CodeException} のコードである——
 * {@code DB_004}（中でエラーが出たのでコミットしなかった）、
 * {@code DB_005}（合流した中が巻き戻しを求めた）など。
 * </p>
 *
 * @since 1.5.0
 */
public class TransactionException extends SqlExecuteException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	内容
	 * @param cause		元の例外
	 */
	public TransactionException (String message, CodeException cause) {

		super(message, cause);

	}

}
