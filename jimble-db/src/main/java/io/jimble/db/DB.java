package io.jimble.db;

import io.jimble.util.io.IOUtil;
import io.jimble.util.data.Data;
import io.jimble.db.data.ResultSetFetcher;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.data.SelectListResponse;
import io.jimble.db.sql.*;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.query.parameter.Parameter;
import io.jimble.util.exception.CodeException;
import io.jimble.core.context.Context;
import io.jimble.util.log.Log;

import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * DBクラス
 */
public class DB implements Closeable, AutoCloseable {

	/* 一度のバッチ実行最大件数 */
	private static final int batchExecuteLimit = 1000;

	/* DBソース */
	private DBSource dbSource = null;

	/* 書き込み用コネクション利用 */
	private boolean useWriteConnectionOnly = false;

	/**
	 * 書き込み用コネクション利用に設定する
	 *
	 * @return	DB
	 */
	public DB setUseWriteConnectionOnly () {

		useWriteConnectionOnly = true;
		return this;

	}

	// region 実行エラー

	/* エラー内容 */
	private CodeException error = null;

	/**
	 * エラー判定
	 *
	 * @return	エラーの場合 = true
	 */
	public boolean isError () {

		return error != null;

	}

	public CodeException getError () {

		return error;

	}

	// endregion

	/**
	 * コンストラクタ
	 *
	 * @param dbSource	DBSource
	 */
	public DB(DBSource dbSource) {

		this.dbSource = dbSource;
		this.enableLongConnectionLog = dbSource.conf.longConnectionLog;

	}

	/**
	 * 新規DBを作成する
	 *
	 * @return  DB
	 */
	public DB newDB () {

		return new DB(dbSource);

	}

	/**
	 * 新規書き込みコネクション利用DBを作成する
	 *
	 * @return	書き込みコネクション利用DB
	 */
	public DB newWriteDB () {

		return newDB().setUseWriteConnectionOnly();

	}

	/**
	 * 新規サブDBを作成する
	 *
	 * @param name	サブDB名
	 * @return	DB
	 */
	public DB newSubDB (String name) {

		return new DB(dbSource.getSubDBSource(name));

	}

	/**
	 * 新規書き込みコネクション利用サブDBを作成する
	 *
	 * @param name	サブDB名
	 * @return	DB
	 */
	public DB newSubWriteDB (String name) {

		return newSubDB(name).setUseWriteConnectionOnly();

	}

	/**
	 * DB名を取得する
	 *
	 * @return  DB名
	 */
	public String getDBName () {

		return this.dbSource.name;

	}

	// region コネクション

	/* コネクション開始時刻 */
	private long connectionStart = 0;

	/* ロングコネクションログ出力判定 */
	private boolean enableLongConnectionLog = false;

	/**
	 * ロングコネクションログ出力判定
	 *
	 * @param enable	有効にする場合 = true
	 */
	public void setEnableLongConnectionLog (boolean enable) {

		this.enableLongConnectionLog = enable;

	}

	/**
	 * ロングコネクションログ出力
	 */
	private void logLongConnection () {

		if (enableLongConnectionLog && connectionStart > 0) {
			long diff = System.currentTimeMillis() - connectionStart;
			if (diff >= dbSource.conf.longConnectionTime) {
				Log.error("DB long connection: " + diff + "ms");
			}
		}
		connectionStart = 0;

	}

	/* DBコネクション. */
	private Connection connection = null;

	/**
	 * 読み取りコネクションを取得する
	 */
	private void getReadConnection() {

		if (connection != null) {
			return;
		}

		try {

			if (useWriteConnectionOnly) {
				getWriteConnection();
				return;
			}

			if (dbSource.hasReadDataSource() && DBSticky.sticky()) {
				getWriteConnection();
				return;
			}

			connection = dbSource.getReadDataSource().getConnection();
			connectionStart = System.currentTimeMillis();

		} catch (Exception ex) {

			Log.error(ex);

		}

	}

	/**
	 * 書き込みコネクションを取得する
	 */
	private void getWriteConnection() {

		if (connection != null) {
			return;
		}

		try {
			connection = dbSource.getWriteDataSource().getConnection();
			connectionStart = System.currentTimeMillis();
		} catch (Exception ex) {
			Log.error(ex);
		}

	}

	/**
	 * クエリ後の自動コネクションクローズ処理
	 */
	private void closeAfterQuery () {

		if (connection == null) {
			return;
		}

		try {
			if (isTransaction()) {
				return;
			}
		} catch (Exception ignore) {}

		try {
			connection.close();
		} catch (Exception ex) {
			Log.error(ex);
		} finally {
			connection = null;
		}

		logLongConnection();

	}

	// endregion


	// region フェッチサイズ

	/* フェッチ判定 */
	private boolean fetchSizeLimit = false;

	/* フェッチサイズ */
	private int fetchSize;

	/**
	 * フェッチサイズ制限を設定する.
	 * ※フェッチサイズを制限した場合、ResultSetをcloseするまで同一コネクション上で更新はできない.
	 *
	 * @param limit	制限する場合 = true
	 */
	public DB setFetchSizeLimit (boolean limit) {

		this.fetchSizeLimit = limit;
		if (this.fetchSizeLimit) {
			this.fetchSize = dbSource.conf.fetchSize;
			if (this.fetchSize <= 0) {
				this.fetchSize = Integer.MIN_VALUE;
			}
		}

		return this;

	}

	// endregion

	// region スクロール

	/* スクロール. */
	private boolean scrollEnable = false;

	/**
	 * ResultSetのスクロール許可を設定する.
	 *
	 * @param enable	許可する場合 = true
	 */
	public DB setScrollEnable (boolean enable) {

		scrollEnable = enable;
		return setFetchSizeLimit(true);

	}

	// endregion


	// region 1件取得する

	/**
	 * 1件取得する
	 *
	 * @param builder   SelectBuilder
	 * @return  結果
	 */
	public Data select(SelectBuilder builder) {

		return select(builder.sql(), builder.params());

	}

	/**
	 * 1件取得する
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public Data select(String sql, Object...params) {

		try (
			ResultSetFetcher fetcher = new ResultSetFetcher()
		) {

			selectListWithFetcher(fetcher, sql, params);

			if (fetcher.isError || this.error != null) {
				return null;
			}

			Iterator<Data> iterator = fetcher.iterator();
			if (iterator.hasNext()) {
				return iterator.next();
			}

			return null;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			return null;

		} finally {

			closeAfterQuery();

		}

	}

	// endregion

	// region 複数件取得する

	/**
	 * 複数件取得する
	 *
	 * @param builder   SelectBuilder
	 * @return  結果
	 */
	public List<Data> selectList(SelectBuilder builder) {

		return selectList(builder.sql(), builder.params());

	}

	/**
	 * 複数件取得する
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public List<Data> selectList(String sql, Object...params) {

		try (
			ResultSetFetcher fetcher = new ResultSetFetcher()
		) {

			selectListWithFetcher(fetcher, sql, params);

			if (fetcher.isError || this.error != null) {
				return null;
			}

			List<Data> res = new ArrayList<>();
			for (Data row : fetcher) {
				res.add(row);
			}

			return res;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			return null;

		} finally {

			closeAfterQuery();

		}

	}

	// endregion

	// region 複数件取得する（大量件数テーブル）

	/**
	 * 複数件取得する（大量件数テーブル）
	 *
	 * @param builder   SelectBuilder
	 * @return  結果
	 */
	public List<Data> selectListPerformance (SelectBuilder builder) {

		// メインテーブルのPK列のみ取得する
		List<Data> _list = selectList(builder.simpleSql(), builder.params());
		if (_list == null) {
			return null;
		}

		// PK列を条件にする
		Column pkColumn = builder.mainTablePkColumn();

		List<Long> idTable = new ArrayList<>();
		for (Data data : _list) {
			idTable.add(data.getLong(pkColumn));
		}

		builder.clearWhere();
		builder.clearHaving();
		builder.offset(-1);
		builder.limit(-1);
		builder.where(
			pkColumn.in(idTable)
		);

		return selectList(builder);

	}

	// endregion

	// region 複数件取得する(件数付き)

	/**
	 * 複数件取得する(件数付き)
	 *
	 * @param builder   SelectBuilder
	 * @return  結果
	 */
	public SelectListResponse selectListWithRowCount (SelectBuilder builder) {

		SelectListResponse res = new SelectListResponse();

		res.list = selectList(builder);

		Data data = select(builder.rowCountSql(), builder.rowCountParams());
		if (data != null) {
			res.rowCount = data.getLong("cnt");
		}

		if (builder.paging() != null && res.list != null) {
			builder.paging().set(res.list.size(), res.rowCount);
			res.paging = builder.paging();
		}

		return res;

	}

	/**
	 * 複数件取得する(件数付き)
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public SelectListResponse selectListWithRowCount (String sql, Object...params) {

		SelectListResponse res = new SelectListResponse();

		res.list = selectList(sql, params);

		{
			String _sql = sql.toUpperCase();
			int indexOrderBy = _sql.lastIndexOf("ORDER");
			int indexLimit = _sql.lastIndexOf("LIMIT");
			int indexFrom = _sql.indexOf("FROM");

			StringBuilder sb = new StringBuilder();
			sb.append("SELECT COUNT(__count_table.cnt) AS cnt");
			sb.append(" FROM (");
			sb.append("SELECT 1 AS cnt ");
			sb.append(sql, indexFrom, Math.min(indexLimit, indexOrderBy));
			sb.append(") __count_table");

			int paramCount = 0;
			for (char c : sb.toString().toCharArray()) {
				if (c == '?') {
					paramCount++;
				}
			}

			List<Object> newParams;
			if (params == null) {
				newParams = new ArrayList<>();
			} else {
				newParams = Parameter.flatten(new SQLParameterList(params));
				while (newParams.size() > paramCount) {
					newParams.removeLast();
				}
			}

			Data data = select(sb.toString(), newParams);
			if (data != null) {
				res.rowCount = data.getLong("cnt");
			}
		}

		return res;

	}

	// endregion

	// region 複数件取得する(大量件数テーブル、件数付き)

	/**
	 * 複数件取得する(件数付き)
	 *
	 * @param builder   SelectBuilder
	 * @return  結果
	 */
	public SelectListResponse selectListWithRowCountPerformance (SelectBuilder builder) {

		SelectListResponse res = new SelectListResponse();

		Data data = select(builder.rowCountSql(), builder.rowCountParams());
		if (data != null) {
			res.rowCount = data.getLong("cnt");
		}

		{
			// メインテーブルのPK列のみ取得する
			List<Data> _list = selectList(builder.simpleSql(), builder.params());
			if (_list == null) {
				return null;
			}

			// PK列を条件にする
			Column pkColumn = builder.mainTablePkColumn();

			List<Long> idTable = new ArrayList<>();
			for (Data d : _list) {
				idTable.add(d.getLong(pkColumn));
			}

			builder.clearWhere();
			builder.clearHaving();
			builder.offset(-1);
			builder.limit(-1);
			builder.where(
				pkColumn.in(idTable)
			);

			res.list = selectList(builder);
		}

		if (builder.paging() != null && res.list != null) {
			builder.paging().set(res.list.size(), res.rowCount);
			res.paging = builder.paging();
		}

		return res;

	}

	// endregion

	// region 逐次取得で取得する

	/**
	 * 逐次取得で取得する
	 *
	 * @param fetcher	ResultSetFetcher
	 * @param builder	SelectBuilder
	 */
	public void selectListWithFetcher(ResultSetFetcher fetcher, SelectBuilder builder) {

		selectListWithFetcher(fetcher, builder.sql(), builder.params());

	}

	/**
	 * 逐次取得で取得する
	 *
	 * @param fetcher	ResultSetFetcher
	 * @param sql		SQL
	 * @param params	パラメータ
	 */
	public void selectListWithFetcher(ResultSetFetcher fetcher, String sql, Object...params) {

		this.error = null;

		// 読み取りコネクションを取得する
		getReadConnection();

		// SQLを実行する
		PreparedStatement st = null;
		ResultSet rs = null;
		try {

			// SQLステートメントを作成する
			if (fetchSizeLimit) {
				// フェッチサイズを制限する場合
				if (scrollEnable) {
					// スクロールを許可する場合
					st = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
				} else {
					// スクロールを許可しない場合
					st = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				}
				st.setFetchSize(this.fetchSize);
			} else {
				// フェッチサイズを制限しない場合
				int _fetchSize = 0;
				_fetchSize = dbSource.conf.fetchSize;
				if (_fetchSize <= 0) {
					_fetchSize = Integer.MIN_VALUE;
				}
				st = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				st.setFetchSize(_fetchSize);
			}

			long start = System.nanoTime();

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			// SQLを実行する
			rs = st.executeQuery();

			Context.recordSqlExecution(System.nanoTime() - start);

			// 結果を保持する
			fetcher.load(st, rs);

		} catch (Exception ex) {

			Log.error(ex);

			fetcher.isError = true;
			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			IOUtil.close(rs, st);

		}

	}

	// endregion


	// region 登録する

	/**
	 * 登録する
	 *
	 * @param builder   InsertBuilder
	 * @return  結果
	 */
	public long insert (InsertBuilder builder) {

		return insert(builder.sql(), builder.params());

	}

	/**
	 * 登録する
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public long insert (String sql, Object...params) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		ResultSet rs = null;
		try {

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

			long start = System.nanoTime();

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			// SQLを実行する
			int count = st.executeUpdate();

			Context.recordSqlExecution(System.nanoTime() - start);

			// DB sticky
			DBSticky.updated();

			if (count > 0) {
				rs = st.getGeneratedKeys();
				if (rs == null || !rs.next()) {
					return count;
				}
				return rs.getLong(1);
			}

			return count;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return -1;

		} finally {

			IOUtil.close(rs, st);
			closeAfterQuery();

		}

	}

	/**
	 * 登録する
	 *
	 * @param builder   InsertBuilder
	 * @return  結果
	 */
	public long insertNoReturnKey (InsertBuilder builder) {

		return insert(builder.sql(), builder.params());

	}

	/**
	 * 登録する
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public long insertNoReturnKey (String sql, Object...params) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		try {

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			long start = System.nanoTime();

			// SQLを実行する
			long result = st.executeUpdate();

			Context.recordSqlExecution(System.nanoTime() - start);

			// DB sticky
			DBSticky.updated();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return -1;

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion

	// region 更新する

	/**
	 * 更新する
	 *
	 * @param builder   UpdateBuilder
	 * @return  結果
	 */
	public int update (UpdateBuilder builder) {

		return update(builder.sql(), builder.params());

	}

	/**
	 * 更新する
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public int update (String sql, Object...params) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		try {

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			long start = System.nanoTime();

			// SQLを実行する
			int result = st.executeUpdate();

			Context.recordSqlExecution(System.nanoTime() - start);

			// DB sticky
			DBSticky.updated();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return -1;

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion

	// region 削除する

	/**
	 * 削除する
	 *
	 * @param builder   DeleteBuilder
	 * @return  結果
	 */
	public int delete (DeleteBuilder builder) {

		return delete(builder.sql(), builder.params());

	}

	/**
	 * 削除する
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public int delete (String sql, Object...params) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		try {

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			long start = System.nanoTime();

			// SQLを実行する
			int result = st.executeUpdate();

			Context.recordSqlExecution(System.nanoTime() - start);

			// DB sticky
			DBSticky.updated();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return -1;

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion


	// region 実行する

	public boolean execute (String sql, Object...params) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		try {

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			long start = System.nanoTime();

			// SQLを実行する
			boolean result = st.execute();

			Context.recordSqlExecution(System.nanoTime() - start);

			// DB sticky
			DBSticky.updated();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return false;

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion


	// region バッチ実行

	/**
	 * 登録バッチ実行（自動採番値取得）
	 *
	 * @param builderList   InsertBuilder
	 * @return  結果
	 */
	public List<Integer> executeBatch (List<IBuilder> builderList) {

		String sql = null;
		List<List<Object>> paramsList = new ArrayList<>();
		for (IBuilder builder : builderList) {
			String builderSql = builder.sql();
			if (sql == null) {
				sql = builderSql;
			} else if (!sql.equals(builderSql)) {
				this.error = new CodeException("DB_998", "executeBatch: SQLが一致しません");
				Log.error("executeBatch mismatch:\n" + sql + "\n" + builderSql);
				return null;
			}
			paramsList.add(builder.params());
		}

		return executeBatch(sql, paramsList);

	}

	/**
	 * 登録バッチ実行（自動採番値取得）
	 *
	 * @param sql           SQL
	 * @param paramsList    パラメータ一覧
	 * @return  結果
	 */
	public List<Integer> executeBatch (String sql, List<List<Object>> paramsList) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		try {

			List<Integer> res = new ArrayList<>();

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			int batchCount = 0;
			for (List<Object> params : paramsList) {

				setParameters(st, params);

				st.addBatch();

				batchCount++;

				if (batchCount == batchExecuteLimit) {

					batchCount = 0;

					long start = System.nanoTime();

					int[] temp = st.executeBatch();

					Context.recordSqlExecution(System.nanoTime() - start);

					if (!isBatchSuccess(temp)) {
						throw new Exception("failed execute batch");
					}
					for (int r : temp) {
						res.add(r);
					}

				}

			}

			if (batchCount > 0) {

				long start = System.nanoTime();

				int[] temp = st.executeBatch();

				Context.recordSqlExecution(System.nanoTime() - start);

				if (!isBatchSuccess(temp)) {
					throw new Exception("failed execute batch");
				}
				for (int r : temp) {
					res.add(r);
				}

			}

			// DB sticky
			DBSticky.updated();

			return res;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return null;

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion

	// region 登録バッチ実行（自動採番値取得）

	/**
	 * 登録バッチ実行（自動採番値取得）
	 *
	 * @param builderList   InsertBuilder
	 * @return  結果
	 */
	public List<Long> insertBatch (List<InsertBuilder> builderList) {

		String sql = null;
		List<List<Object>> paramsList = new ArrayList<>();
		for (InsertBuilder builder : builderList) {
			if (sql == null) {
				sql = builder.sql();
			}
			paramsList.add(builder.params());
		}

		return insertBatch(sql, paramsList);

	}

	/**
	 * 登録バッチ実行（自動採番値取得）
	 *
	 * @param sql           SQL
	 * @param paramsList    パラメータ一覧
	 * @return  結果
	 */
	public List<Long> insertBatch (String sql, List<List<Object>> paramsList) {

		this.error = null;

		// 書き込みコネクションを取得する
		getWriteConnection();

		// SQLを実行する
		PreparedStatement st = null;
		ResultSet rs = null;
		try {

			List<Long> res = new ArrayList<>();

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

			int batchCount = 0;
			for (List<Object> params : paramsList) {

				setParameters(st, params);

				st.addBatch();

				batchCount++;

				if (batchCount == batchExecuteLimit) {

					batchCount = 0;

					long start = System.nanoTime();

					int[] temp = st.executeBatch();

					Context.recordSqlExecution(System.nanoTime() - start);

					if (!isBatchSuccess(temp)) {
						throw new Exception("failed execute batch");
					}
					rs = st.getGeneratedKeys();
					while (rs.next()) {
						res.add(rs.getLong(1));
					}
					IOUtil.close(rs);

				}

			}

			if (batchCount > 0) {

				long start = System.nanoTime();

				int[] temp = st.executeBatch();

				Context.recordSqlExecution(System.nanoTime() - start);

				if (!isBatchSuccess(temp)) {
					throw new Exception("failed execute batch");
				}
				rs = st.getGeneratedKeys();
				while (rs.next()) {
					res.add(rs.getLong(1));
				}
				IOUtil.close(rs);

			}

			// DB sticky
			DBSticky.updated();

			return res;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());
			try {
				rollback();
			} catch (Exception ignore) {}

			return null;

		} finally {

			IOUtil.close(rs, st);
			closeAfterQuery();

		}

	}

	// endregion

	// region バッチ実行結果成功判定

	/**
	 * バッチ実行結果成功判定
	 *
	 * @param list	バッチ実行結果
	 * @return	成功の場合 = true
	 */
	public static boolean isBatchSuccess (List<Integer> list) {

		if (list == null || list.isEmpty()) {
			return false;
		}

		for (int res : list) {
			if (res < 0 && res != -2) {
				return false;
			}
		}

		return true;

	}

	/**
	 * バッチ実行結果成功判定
	 *
	 * @param resArray	バッチ実行結果
	 * @return	成功の場合 = true
	 */
	public static boolean isBatchSuccess (int...resArray) {

		if (resArray == null || resArray.length == 0) {
			return false;
		}

		for (int res : resArray) {
			if (res < 0 && res != -2) {
				return false;
			}
		}

		return true;

	}

	// endregion


	// region パラメータを設定する

	/**
	 * パラメータを設定する
	 *
	 * @param st		ステートメント
	 * @param _params	パラメータ
	 */
	private void setParameters (PreparedStatement st, List<?> _params) throws Exception {

		if (_params == null || _params.isEmpty()) {
			return;
		}

		List<Object> params = Parameter.flatten(_params);
		for (int i = 0; i < params.size(); i++) {

			Object param = params.get(i);

			int index = i + 1;

			if (param == null) {

				st.setObject(index, null);

			} else if (param instanceof Date date) {

				Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				calendar.set(Calendar.MILLISECOND, 0);

				st.setTimestamp(index, new Timestamp(calendar.getTime().getTime()));

			} else if (param instanceof Boolean) {

				st.setObject(index, ((Boolean) param) ? 1 : 0);

			} else if (param instanceof Data data) {

				st.setObject(index, data.getJsonString());

			} else if (param.getClass().isEnum()) {

				try {
					st.setObject(index, ((Enum<?>) param).name());
				} catch (Exception ex) {
					st.setObject(index, param.toString());
				}

			} else if (param instanceof File file) {

				if (!file.exists() || !file.isFile()) {
					st.setObject(index, null);
				} else {
					st.setObject(index, Files.readAllBytes(file.toPath()));
				}

			} else if (param instanceof ByteArrayOutputStream os) {

				st.setObject(index, os.toByteArray());

			} else if (param instanceof ByteArrayInputStream is) {

				st.setObject(index, is.readAllBytes());

			} else if (param instanceof InputStream is) {

				try (
					ByteArrayOutputStream os = new ByteArrayOutputStream();
				) {

					byte[] buffer = new byte[4096];
					int len;
					while ((len = is.read(buffer)) != -1) {
						os.write(buffer, 0, len);
					}

					st.setObject(index, os.toByteArray());

				} catch (IOException e) {

					st.setObject(index, null);

				}

			} else {

				st.setObject(index, param);

			}

		}

	}

	// endregion


	// region トランザクション

	/**
	 * トランザクション中か判定する
	 *
	 * @return  トランザクション中の場合 = true
	 */
	public boolean isTransaction () {

		if (connection == null) {
			return false;
		}

		try {
			return !connection.getAutoCommit();
		} catch (Exception ignore) {
			return false;
		}

	}

	/**
	 * トランザクションを開始する
	 */
	public void beginTransaction() throws Exception {

		if (connection == null) {
			getWriteConnection();
		}

		connection.setAutoCommit(false);

	}

	/**
	 * トランザクションをコミットする
	 */
	public void commit() throws Exception {

		if (connection == null) {
			return;
		}

		if (isTransaction()) {
			connection.commit();
		}

	}

	/**
	 * トランザクションをコミットし終了する
	 */
	public void commitEndTransaction() throws Exception {

		try {
			commit();
		} finally {
			endTransaction();
		}

	}

	/**
	 * トランザクションをロールバックする
	 */
	public void rollback() throws Exception {

		if (connection == null) {
			return;
		}

		if (isTransaction()) {
			connection.rollback();
		}

	}

	/**
	 * トランザクションをロールバックし終了する
	 */
	public void rollbackEndTransaction() throws Exception {

		try {
			rollback();
		} finally {
			endTransaction();
		}

	}

	/**
	 * トランザクションを終了する
	 */
	public void endTransaction() throws Exception {

		if (connection == null) {
			return;
		}

		try {
			if (isTransaction()) {
				connection.setAutoCommit(true);
			}
		} catch (Exception ex) {
			this.error = new CodeException("DB_999", ex.getMessage());
		} finally {
			close();
			if (this.error != null) {
				throw this.error;
			}
		}

	}

	// endregion

	// region Closeable

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {

		if (connection == null) {
			return;
		}

		try {
			if (isTransaction()) {
				rollback();
				connection.setAutoCommit(true);
			}
		} catch (Exception ex) {
			Log.error(ex);
		}

		try {
			connection.close();
		} catch (Exception ex) {
			Log.error(ex);
		} finally {
			connection = null;
		}

		logLongConnection();

	}

	// endregion

}
