package io.jimble.db;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * DBソース
 */
public class DBSource {

	/* DBソース名 */
	public String name;

	/* 親DBソース */
	public DBSource parent = null;

	/* データソース */
	public DataSource dataSource;

	/* DB Conf */
	public DBConf conf;

	/* 読み取り専用データソース */
	public DBSource readSource;

	/* サブデータソースマップ */
	public Map<String, DBSource> subsDbSourceMap = new HashMap<>();

	/* SQL の方言（要件 F-D-30） */
	private Dialect dialect = null;

	/**
	 * SQL の方言
	 *
	 * <p>
	 * {@code db.<name>.product} で決まる（既定は mysql）。
	 * <b>{@link DB} がこれを SQL ビルダーに渡す</b>ので、
	 * アプリのコードは製品を知らなくてよい。
	 * </p>
	 *
	 * @return	方言
	 */
	public Dialect dialect () {

		Dialect current = dialect;

		if (current == null) {
			current = Dialects.of(conf == null ? null : conf.product);
			dialect = current;
		}

		return current;

	}

	/**
	 * 読み取り専用データソースを取得する
	 *
	 * @return	読み取り専用データソース
	 */
	public DataSource getReadDataSource() {

		if (readSource != null && readSource.dataSource != null) {
			return readSource.dataSource;
		}

		return dataSource;

	}

	/**
	 * 読み書き可能データソースを取得する
	 *
	 * @return	読み書き可能データソース
	 */
	public DataSource getWriteDataSource() {

		return dataSource;

	}

	/**
	 * サブDBソースを取得する
	 *
	 * @param name	名前
	 * @return	サブDBソース
	 */
	public DBSource getSubDBSource (String name) {

		if (parent != null) {
			return parent.getSubDBSource(name);
		}

		return subsDbSourceMap.get(name);

	}

	/**
	 * 読み取り専用データソース存在判定
	 *
	 * @return	読み取り専用データソースが存在する場合 = true
	 */
	public boolean hasReadDataSource () {

		return readSource != null;

	}

}
