package io.jimble.db;

import io.jimble.util.exception.CodeException;

/**
 * 一意制約に当たった（要件 D-191）
 *
 * <p>
 * <b>DB のエラーのうち、呼んだ側がいちばん分岐したいもの</b>なので、型を分けた。
 * {@code ...OrThrow} 系と {@code insertKey} が投げる（{@link SqlExecuteException} の子なので、
 * これまでの {@code catch (SqlExecuteException e)} にもそのまま入る）。
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
 * 戻り値で見る書き方（{@code db.insert(...) < 0}）のときは {@link DB#isDuplicateKeyError()} で見分けられる。
 * 見分け方は、PostgreSQL は SQLSTATE {@code 23505}、MySQL はエラーコード {@code 1062}。
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
