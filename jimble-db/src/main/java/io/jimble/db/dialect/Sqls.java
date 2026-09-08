package io.jimble.db.dialect;

import java.util.List;

/**
 * 生 SQL を製品ごとに書き分ける小道具（要件 F-D-30）
 *
 * <p>
 * <b>フレームワークが自分の内部テーブルに投げる SQL 用。</b>
 * アプリは {@code SQL.insert(...)} を使うので、こちらは要らない。
 * </p>
 *
 * <pre>
 * db.execute("INSERT INTO db_value (value_key, value) VALUES (?, ?)"
 *     + Sqls.upsert(db.dialect(), List.of("value_key"), "value"), key, value);
 * </pre>
 */
public final class Sqls {

	/**
	 * コンストラクタ
	 */
	private Sqls () {

	}

	/**
	 * 重複したら更新する句
	 *
	 * <p>
	 * MySQL は {@code ON DUPLICATE KEY UPDATE c = VALUES(c)}、
	 * PostgreSQL は {@code ON CONFLICT (k) DO UPDATE SET c = EXCLUDED.c}。
	 * </p>
	 *
	 * @param dialect	方言
	 * @param keys		重複を見る列（PostgreSQL で要る）
	 * @param columns	更新する列
	 * @return	句
	 */
	public static String upsert (Dialect dialect, List<String> keys, String...columns) {

		StringBuilder sb = new StringBuilder(dialect.onDuplicateKeyUpdate(keys));

		for (int i = 0; i < columns.length; i++) {

			if (i > 0) {
				sb.append(", ");
			}

			dialect.identifier(sb, columns[i]);
			sb.append(" = ");
			dialect.insertedValue(sb, null, columns[i]);

		}

		return sb.toString();

	}

	/**
	 * 重複を無視する INSERT の始まり
	 *
	 * @param dialect	方言
	 * @param table		テーブル名
	 * @return	{@code INSERT IGNORE INTO x} または {@code INSERT INTO x}
	 */
	public static String insertIgnoreInto (Dialect dialect, String table) {

		StringBuilder sb = new StringBuilder("INSERT ");
		sb.append(dialect.insertIgnorePrefix());
		sb.append("INTO ");
		dialect.identifier(sb, table);

		return sb.toString();

	}

	/**
	 * 重複を無視する INSERT の終わり
	 *
	 * <p>
	 * <b>MySQL では空、PostgreSQL では {@code ON CONFLICT DO NOTHING}。</b>
	 * 場所が前と後ろで違うので、両方を書く必要がある。
	 * </p>
	 *
	 * @param dialect	方言
	 * @return	句
	 */
	public static String insertIgnoreTail (Dialect dialect) {

		return dialect.insertIgnoreSuffix();

	}

}
