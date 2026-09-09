package io.jimble.db.dialect;

import io.jimble.util.log.Log;

/**
 * 方言を選ぶ（要件 F-D-30）
 */
public final class Dialects {

	/** 設定キー：DB 製品 */
	public static final String KEY_PRODUCT = "product";

	/** 既定の製品 */
	public static final String DEFAULT_PRODUCT = MySqlDialect.NAME;

	/* 既定の方言（データソースが分からないところで使う） */
	private static volatile Dialect defaultDialect = MySqlDialect.INSTANCE;

	/**
	 * コンストラクタ
	 */
	private Dialects () {

	}

	/**
	 * 名前から方言を選ぶ
	 *
	 * <p>
	 * <b>知らない名前は落とす</b>（要件 F-X-05）。
	 * 黙って MySQL に倒すと、PostgreSQL のつもりで書いたのに
	 * バッククォートの SQL が飛んで、原因が読めない構文エラーになる。
	 * </p>
	 *
	 * @param product	製品名。空なら既定（mysql）
	 * @return	方言
	 */
	public static Dialect of (String product) {

		String name = product == null ? "" : product.trim().toLowerCase();

		if (name.isEmpty()) {
			return MySqlDialect.INSTANCE;
		}

		Dialect dialect = find(name);

		if (dialect == null) {
			throw new DialectException(
				"db.<name>.product に使えない値です: %s（mysql / mariadb / postgresql / postgres / pgsql）"
					.formatted(product));
		}

		return dialect;

	}

	/**
	 * 製品名として読めるか
	 *
	 * <p>
	 * <b>別名も同じ答えに畳む。</b>{@code mariadb} は {@code mysql}、
	 * {@code postgres} は {@code postgresql} になる。
	 * マイグレーション SQL のファイル名（{@code 001_x.mariadb.sql}）を
	 * 見分けるのに使う（要件 F-G-20）。<b>設定に書ける名前とここが食い違うと、
	 * 書いたのに流れない SQL ができる</b>ので、{@link #of(String)} と同じ表を通す。
	 * </p>
	 *
	 * @param value	名前
	 * @return	正規の方言名（{@code mysql} / {@code postgresql}）。製品名でなければ null
	 */
	public static String productNameOrNull (String value) {

		if (value == null || value.isEmpty()) {
			return null;
		}

		Dialect dialect = find(value.trim().toLowerCase());

		return dialect == null ? null : dialect.name();

	}

	/**
	 * 名前から方言を引く
	 *
	 * @param name	小文字にした名前
	 * @return	方言（無ければ null）
	 */
	private static Dialect find (String name) {

		return switch (name) {
			case MySqlDialect.NAME, "mariadb" -> MySqlDialect.INSTANCE;
			case PostgreSqlDialect.NAME, "postgres", "pgsql" -> PostgreSqlDialect.INSTANCE;
			default -> null;
		};

	}

	/**
	 * 既定の方言
	 *
	 * <p>
	 * データソースが分からないところ（{@code builder.sql()} を直接呼ぶ、
	 * 起動前のテストなど）で使う。<b>主データソースの製品</b>になる。
	 * </p>
	 *
	 * @return	方言
	 */
	public static Dialect defaultDialect () {

		return defaultDialect;

	}

	/**
	 * 既定の方言を決める
	 *
	 * <p>フレームワーク内部から、主データソースを読んだときに呼ぶ。</p>
	 *
	 * @param dialect	方言
	 */
	public static void defaultDialect (Dialect dialect) {

		if (dialect == null) {
			return;
		}

		if (defaultDialect != dialect) {
			Log.debug("既定の SQL 方言: " + dialect.name());
		}

		defaultDialect = dialect;

	}

	/**
	 * 既定に戻す（テスト用）
	 */
	public static void reset () {

		defaultDialect = MySqlDialect.INSTANCE;

	}

}
