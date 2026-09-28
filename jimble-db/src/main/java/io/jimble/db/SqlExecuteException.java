package io.jimble.db;

import io.jimble.util.exception.CodeException;

/**
 * SQL を実行できなかった
 *
 * <p>
 * <b>2.0 で、DB の失敗はすべてこの例外になった</b>（要件 D-193）。1.x は {@code null} / {@code -1} / {@code false} を返し、
 * {@code isError()} で見分ける作法だったので、「1件も無かった」と「読めなかった」が見分けられなかった。
 * 0件は空（{@code Optional.empty()} / 空リスト / 件数 0）で返る。
 * </p>
 *
 * <p>
 * 分岐したい失敗はたいてい一意制約だけなので、それは子の {@link DuplicateKeyException} で受ける。
 * それ以外は書かなければ上まで飛んで 500 になり、トランザクションは巻き戻る。
 * 元の例外は {@code getCause()}（{@link CodeException}）のさらに cause に残る。
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
	 * @param cause		元のエラー（コードつき。そのさらに cause が JDBC の例外）
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
