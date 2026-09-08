package io.jimble.db.dialect;

/**
 * その製品では書けない（要件 F-D-30）
 *
 * <p>
 * <b>SQL を組み立てた時点で投げる。</b>
 * 壊れた SQL を DB に投げて実行時に落ちるより、
 * <b>組み立てた瞬間に落ちるほうが原因に辿り着ける</b>（D-86 / D-89 と同じ考え方）。
 * </p>
 */
public class DialectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	内容
	 */
	public DialectException (String message) {

		super(message);

	}

}
