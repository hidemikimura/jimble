package io.jimble.db.sql;

import io.jimble.util.data.definition.ITable;

/**
 * SQLビルダー
 */
public class SQL {

	/* バージョン */
	public static final long VERSION = 1;

	/**
	 * SELECT文SQLビルダーを作成する
	 *
	 * @param select    Select句
	 * @return	SQLビルダー
	 */
	public static SelectBuilder select (Object...select) {

		return new SelectBuilder(select);

	}

	/**
	 * INSERT文SQLビルダーを作成する
	 *
	 * @return	SQLビルダー
	 */
	public static InsertBuilder insert (ITable table) {

		return new InsertBuilder(table);

	}

	/**
	 * UPDATE文SQLビルダーを作成する
	 *
	 * @return	SQLビルダー
	 */
	public static UpdateBuilder update (ITable table) {

		return new UpdateBuilder(table);

	}

	/**
	 * DELETE文SQLビルダーを作成する
	 *
	 * @return	SQLビルダー
	 */
	public static DeleteBuilder delete (ITable table) {

		return new DeleteBuilder(table);

	}

}
