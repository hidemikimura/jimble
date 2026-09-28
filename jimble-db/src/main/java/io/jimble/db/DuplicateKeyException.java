package io.jimble.db;

import io.jimble.util.exception.CodeException;

/**
 * 一意制約に当たった（要件 D-191）
 *
 * <p>
 * <b>DB のエラーのうち、呼んだ側がいちばん分岐したいもの</b>なので、型を分けた。
 * 2.0 では DB を触るメソッドすべてが投げうる（{@link SqlExecuteException} の子なので、
 * {@code catch (SqlExecuteException e)} にもそのまま入る）。
 * </p>
 *
 * <pre>
 * try {
 *     long id = db.insertKey(SQL.insert(User.instance()).value(User.email, email));
 * } catch (DuplicateKeyException e) {
 *     return 「そのメールアドレスは使われています」;
 * }
 * </pre>
 *
 * <p>
 * 見分け方は、PostgreSQL は SQLSTATE {@code 23505}、MySQL はエラーコード {@code 1062}。
 * 1.5 の {@code isDuplicateKeyError()} は、失敗が例外になったので 2.0 で消した。
 * トランザクションの中で受け止めて続けても、その Tx の commit は断られる（{@code DB_004}）。
 * </p>
 *
 * @since 1.5.0
 */
public class DuplicateKeyException extends SqlExecuteException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	内容
	 * @param cause		元の例外
	 */
	public DuplicateKeyException (String message, CodeException cause) {

		super(message, cause);

	}

}
