package io.jimble.db.sql;

/**
 * SQL を組み立てられない（要件 F-D-07）
 *
 * <p>
 * <b>SQL を組み立てた時点で投げる。</b>
 * 壊れた SQL を DB に投げて実行時に落ちるより、
 * <b>組み立てた瞬間に落ちるほうが原因に辿り着ける</b>（D-86 / D-89 と同じ考え方）。
 * </p>
 *
 * <p>
 * 製品ごとの「これは書けない」は
 * {@code io.jimble.db.dialect.DialectException} のほうである。
 * こちらは<b>どの製品でも書けないもの</b>を指す。
 * </p>
 */
public class SqlBuildException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	内容
	 */
	public SqlBuildException (String message) {

		super(message);

	}

}
