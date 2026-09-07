package io.jimble.db;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * DBソース
 */
public class DBSource {

	/* DBソース名 */
	public String name;

	/* データソース */
	public DataSource dataSource;

	/* DB Conf */
	public DBConf conf;

	/* 読み取り専用データソース */
	public DBSource readSource;

	/* サブデータソースマップ */
	public Map<String, DBSource> subsDbSourceMap = new HashMap<>();

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
