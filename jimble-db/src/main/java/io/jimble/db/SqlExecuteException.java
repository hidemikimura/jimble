package io.jimble.db;

import io.jimble.util.exception.CodeException;

/**
 * SQL を実行できなかった
 *
 * <p>
 * <b>戻り値では「読めなかった」と「1件も無かった」を見分けられないので、
 * 見分けたい呼び方のためだけに用意した例外</b>である
 * （{@link DB#selectOrThrow(String, Object...)} ほか）。
 * </p>
 *
 * <p>
 * <b>既定の作法は変えていない。</b>{@link DB#select(String, Object...)} は
 * これまでどおり {@code null} を返し、{@link DB#isError()} で見分ける。
 * 投げるのは<b>こちらを選んだときだけ</b>である。
 * </p>
 *
 * <p>
 * 組み立てられなかったときは {@link io.jimble.db.sql.SqlBuildException} のほうである。
 * こちらは<b>組み立てたあと、DB に投げてから</b>のものを指す。
 * </p>
 */
public class SqlExecuteException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/* エラーコード（無ければ null） */
	private final String code;

	/**
	 * コンストラクタ
	 *
	 * @param message	内容
	 */
	public SqlExecuteException (String message) {

		super(message);
		this.code = null;

	}

	/**
	 * コンストラクタ
	 *
	 * @param message	内容
	 * @param cause		{@link DB#getError()} が持っていたもの
	 */
	public SqlExecuteException (String message, CodeException cause) {

		super(message, cause);
		this.code = cause == null ? null : cause.getCode();

	}

	/**
	 * エラーコード
	 *
	 * @return	コード。無ければ null
	 */
	public String getCode () {

		return code;

	}

}
