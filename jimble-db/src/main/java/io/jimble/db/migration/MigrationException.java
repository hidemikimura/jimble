package io.jimble.db.migration;

/**
 * マイグレーション失敗
 *
 * <p>
 * <b>この例外は握りつぶさない。</b>起動時マイグレーションが失敗したらアプリを起動させない
 * （要件 F-G-16）。中途半端なスキーマのままリクエストを受け付けるほうが危険なため。
 * </p>
 */
public class MigrationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 */
	public MigrationException (String message) {

		super(message);

	}

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		原因
	 */
	public MigrationException (String message, Throwable cause) {

		super(message, cause);

	}

}
