package io.jimble.db;

import io.jimble.util.io.IOUtil;
import io.jimble.util.data.Data;
import io.jimble.db.data.ResultSetFetcher;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.data.SelectListResponse;
import io.jimble.db.sql.*;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.query.parameter.Parameter;
import io.jimble.db.sqlcache.SqlCache;
import io.jimble.db.sqlcache.SqlCacheConf;
import io.jimble.db.sqlcache.SqlCacheTags;
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

	/**
	 * この接続先の SQL 方言（要件 F-D-30）
	 *
	 * <p>
	 * <b>SQL ビルダーはこれを受け取って組み立てる。</b>
	 * {@code db.<name>.product} で決まり、書かなければ mysql。
	 * </p>
	 *
	 * @return	方言
	 */
	public Dialect dialect () {

		return dbSource == null ? Dialects.defaultDialect() : dbSource.dialect();

	}

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

		/*
		 * <b>null を黙って受けない。</b>受けると、最初に使ったところで
		 * {@code NullPointerException} になり、<b>DB のことを何も言わない例外で落ちる</b>
		 * （要件 F-X-05 / D-130）。
		 */
		if (dbSource == null) {
			throw new IllegalStateException(
				"DB のデータソースがありません（DBUtil.load が失敗している可能性があります）");
		}

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


	// region SQL結果キャッシュ（要件 F-D-28）

	/* この更新で消すもの。null は「読めなかった」＝全部消す */
	private Set<String> plannedTags = null;

	/* SQL結果キャッシュを使わない */
	private boolean sqlCacheDisabled = false;

	/**
	 * この {@link DB} では SQL 結果キャッシュを使わない（要件 F-D-28）
	 *
	 * <p>
	 * <b>キャッシュの置き場（{@code sql_cache.store = "db"}）が自分で使う。</b>
	 * 置き場が {@code db_cache} を書き換えるたびにキャッシュを消しにいくと、
	 * <b>消す処理がまた書き込みを起こして際限なく回る</b>。
	 * </p>
	 *
	 * @return	自分
	 */
	public DB withoutSqlCache () {

		this.sqlCacheDisabled = true;

		return this;

	}

	/* トランザクション中に溜めた、消すもの（使うまで作らない） */
	private Set<String> pendingTags = null;

	/* トランザクション中に「全部消す」が出たか */
	private boolean pendingAll = false;

	/**
	 * この更新で消すものを決めておく
	 *
	 * <p>
	 * ビルダー版の {@code insert} / {@code update} / {@code delete} が、
	 * 生 SQL 版に降りる<b>直前</b>に置く。生 SQL 版から直接呼ばれたときは
	 * 何も置かれていないので「読めなかった」扱いになる。
	 * </p>
	 *
	 * @param tags	タグ
	 */
	private void plan (Set<String> tags) {

		this.plannedTags = tags;

	}

	/**
	 * SQL 結果キャッシュを使うか
	 *
	 * <p>
	 * <b>更新のたびに見るので、いちばん安い形にしてある。</b>
	 * {@link SqlCacheConf#enabled()} は設定のインスタンスが入れ替わったときだけ
	 * 読み直す（普段は volatile の読み取りと参照の比較だけ）。
	 * </p>
	 *
	 * @return	使うなら true
	 */
	private boolean isSqlCacheEnabled () {

		return !sqlCacheDisabled && SqlCacheConf.enabled();

	}

	/**
	 * キャッシュを消す
	 *
	 * <p>
	 * 更新が成功した直後に呼ぶ。<b>トランザクション中は溜めておいてコミットで消す</b>
	 * （ロールバックしたら消さない）。
	 * </p>
	 */
	private void invalidateCache () {

		// いちばん安い判定を先に置く（切っていれば1行も走らない）
		if (!isSqlCacheEnabled()) {
			this.plannedTags = null;
			return;
		}

		Set<String> tags = this.plannedTags;
		this.plannedTags = null;

		boolean all = tags == null;

		if (isTransaction()) {

			if (all) {
				pendingAll = true;
			} else {
				if (pendingTags == null) {
					pendingTags = new LinkedHashSet<>();
				}
				pendingTags.addAll(tags);
			}

			return;

		}

		if (all) {
			/*
			 * 生 SQL の更新。<b>どの行に当たるか読めない。</b>
			 * 古いデータを返すより、全部引き直させるほうがよい。
			 */
			SqlCache.clear();
			return;
		}

		SqlCache.invalidate(tags);

	}

	/**
	 * 溜めておいた削除を実行する（コミット時）
	 */
	private void flushCache () {

		if (!pendingAll && pendingTags == null) {
			return;
		}

		boolean all = pendingAll;
		Set<String> tags = pendingTags == null ? Set.of() : Set.copyOf(pendingTags);

		pendingAll = false;
		pendingTags = null;

		if (!isSqlCacheEnabled() || (!all && tags.isEmpty())) {
			return;
		}

		if (all) {
			SqlCache.clear();
			return;
		}

		SqlCache.invalidate(tags);

	}

	/**
	 * 溜めておいた削除を捨てる（ロールバック時）
	 */
	private void discardCache () {

		pendingAll = false;
		pendingTags = null;

	}

	/**
	 * キャッシュを見てから1件取得する（要件 F-D-28）
	 *
	 * <pre>
	 * Data customer = db.selectCached(
	 *     SQL.select()
	 *         .from(Customer.instance())
	 *         .inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
	 *         .where(Customer.id.eq(1)));
	 * </pre>
	 *
	 * <p>
	 * <b>更新があれば自動で消える。</b>消し方は
	 * {@link io.jimble.db.sqlcache.SqlCacheTags} を参照。
	 * </p>
	 *
	 * @param builder	SelectBuilder
	 * @return	結果
	 */
	public Data selectCached (SelectBuilder builder) {

		List<Data> rows = selectListCached(builder);

		return rows == null || rows.isEmpty() ? null : rows.getFirst();

	}

	/**
	 * キャッシュを見てから複数件取得する（要件 F-D-28）
	 *
	 * <p>
	 * <b>トランザクションの中では素通しで引く。</b>
	 * まだ確定していない値をキャッシュに残さないためである。
	 * </p>
	 *
	 * @param builder	SelectBuilder
	 * @return	結果
	 */
	public List<Data> selectListCached (SelectBuilder builder) {

		/*
		 * 使わない場面：
		 * - トランザクションの中（まだ確定していない値を残さない）
		 * - 直前に書き込んだ直後（要件 F-D-19。レプリカが追いつく前の値を
		 *   <b>台をまたいで共有するキャッシュに焼き付けない</b>）
		 */
		if (!SqlCacheConf.enabled()) {
			// 「書いたのに効かない」を黙って通さない（初回だけ）
			SqlCache.warnDisabled();
			return selectList(builder);
		}

		if (sqlCacheDisabled || isTransaction() || DBSticky.sticky()) {
			return selectList(builder);
		}

		String sql = builder.sql(dialect());
		List<Object> params = builder.params();

		String key = SqlCache.key(sql, params);

		List<Data> cached = SqlCache.get(key);

		if (cached != null) {
			return cached;
		}

		List<Data> rows = selectList(sql, params);

		if (rows == null) {
			return null;
		}

		SqlCache.put(key, SqlCacheTags.of(getDBName(), builder, rows), rows);

		return rows;

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

		return select(builder.sql(dialect()), builder.params());

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

		return selectList(builder.sql(dialect()), builder.params());

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
		List<Data> _list = selectList(builder.simpleSql(dialect()), builder.params());
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

		Data data = select(builder.rowCountSql(dialect()), builder.rowCountParams());
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

			/*
			 * ORDER も LIMIT も無い SQL では lastIndexOf が -1 を返し、
			 * Math.min(-1, -1) で <b>末尾が -1 になって例外</b>になっていた。
			 * 見つからなかったものは「末尾まで」として扱う。
			 */
			int end = sql.length();
			if (indexOrderBy >= 0) {
				end = Math.min(end, indexOrderBy);
			}
			if (indexLimit >= 0) {
				end = Math.min(end, indexLimit);
			}

			StringBuilder sb = new StringBuilder();
			sb.append("SELECT COUNT(__count_table.cnt) AS cnt");
			sb.append(" FROM (");
			sb.append("SELECT 1 AS cnt ");
			sb.append(sql, indexFrom, end);
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

		Data data = select(builder.rowCountSql(dialect()), builder.rowCountParams());
		if (data != null) {
			res.rowCount = data.getLong("cnt");
		}

		{
			// メインテーブルのPK列のみ取得する
			List<Data> _list = selectList(builder.simpleSql(dialect()), builder.params());
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

		selectListWithFetcher(fetcher, builder.sql(dialect()), builder.params());

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

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// 結果を保持する
			// 列の型は接続先の製品で見分ける（要件 F-D-30）
			fetcher.dialect(dialect());
			fetcher.load(st, rs);

		} catch (Exception ex) {

			Log.error(ex);

			fetcher.isError = true;
			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

		// 先に組み立てる。ここで例外が出ても「消す予定」を持ち越さない（要件 F-D-28）
		String sql = builder.sql(dialect());
		List<Object> params = builder.params();

		if (isSqlCacheEnabled()) {
			/*
			 * <b>切っているときは組み立てすらしない。</b>
			 * タグを作るには WHERE を読んでテーブルのキーを引く必要があり、
			 * 使っていないアプリがすべての更新でそれを払うことになる（D-95）。
			 */
			plan(SqlCacheTags.of(getDBName(), builder));
		}

		return insert(sql, params);

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

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			if (count > 0) {

				rs = st.getGeneratedKeys();
				if (rs == null || !rs.next()) {
					return count;
				}

				/*
				 * 採番値が取れなければ件数を返す（要件 F-D-30）。
				 *
				 * MySQL は採番していなければ getGeneratedKeys が空になるので
				 * 上の return に落ちるが、<b>PostgreSQL は行を丸ごと返す</b>ので
				 * 空にならない。取れなかったことを 0 で示してもらい、
				 * ここで MySQL と同じ「件数」に揃える。
				 */
				long key = dialect().generatedKey(rs);

				return key > 0 ? key : count;

			}

			return count;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

		String sql = builder.sql(dialect());
		List<Object> params = builder.params();

		if (isSqlCacheEnabled()) {
			/*
			 * <b>切っているときは組み立てすらしない。</b>
			 * タグを作るには WHERE を読んでテーブルのキーを引く必要があり、
			 * 使っていないアプリがすべての更新でそれを払うことになる（D-95）。
			 */
			plan(SqlCacheTags.of(getDBName(), builder));
		}

		return insert(sql, params);

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

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

		String sql = builder.sql(dialect());
		List<Object> params = builder.params();

		if (isSqlCacheEnabled()) {
			/*
			 * <b>切っているときは組み立てすらしない。</b>
			 * タグを作るには WHERE を読んでテーブルのキーを引く必要があり、
			 * 使っていないアプリがすべての更新でそれを払うことになる（D-95）。
			 */
			plan(SqlCacheTags.of(getDBName(), builder));
		}

		return update(sql, params);

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

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

		String sql = builder.sql(dialect());
		List<Object> params = builder.params();

		if (isSqlCacheEnabled()) {
			/*
			 * <b>切っているときは組み立てすらしない。</b>
			 * タグを作るには WHERE を読んでテーブルのキーを引く必要があり、
			 * 使っていないアプリがすべての更新でそれを払うことになる（D-95）。
			 */
			plan(SqlCacheTags.of(getDBName(), builder));
		}

		return delete(sql, params);

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

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return result;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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
			String builderSql = builder.sql(dialect());
			if (sql == null) {
				sql = builderSql;
			} else if (!sql.equals(builderSql)) {
				this.error = new CodeException("DB_998", "executeBatch: SQLが一致しません");
				Log.error("executeBatch mismatch:\n" + sql + "\n" + builderSql);
				return null;
			}
			paramsList.add(builder.params());
		}

		// SQL結果キャッシュを消す（要件 F-D-28）。SQL が同じなので、消す先も同じ
		if (isSqlCacheEnabled()) {
			plan(SqlCacheTags.of(getDBName(), builderList));
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

					Context.recordSqlExecution(System.nanoTime() - start, sql);

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

				Context.recordSqlExecution(System.nanoTime() - start, sql);

				if (!isBatchSuccess(temp)) {
					throw new Exception("failed execute batch");
				}
				for (int r : temp) {
					res.add(r);
				}

			}

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return res;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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
				sql = builder.sql(dialect());
			}
			paramsList.add(builder.params());
		}

		// SQL結果キャッシュを消す（要件 F-D-28）
		if (isSqlCacheEnabled()) {
			plan(SqlCacheTags.of(getDBName(), builderList));
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

					Context.recordSqlExecution(System.nanoTime() - start, sql);

					if (!isBatchSuccess(temp)) {
						throw new Exception("failed execute batch");
					}
					rs = st.getGeneratedKeys();
					while (rs.next()) {
						res.add(dialect().generatedKey(rs));
					}
					IOUtil.close(rs);

				}

			}

			if (batchCount > 0) {

				long start = System.nanoTime();

				int[] temp = st.executeBatch();

				Context.recordSqlExecution(System.nanoTime() - start, sql);

				if (!isBatchSuccess(temp)) {
					throw new Exception("failed execute batch");
				}
				rs = st.getGeneratedKeys();
				while (rs.next()) {
					res.add(dialect().generatedKey(rs));
				}
				IOUtil.close(rs);

			}

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return res;

		} catch (Exception ex) {

			Log.error(ex);

			this.error = new CodeException("DB_999", ex.getMessage());

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

			} else if (param instanceof Boolean value) {

				// MySQL は tinyint(1) なので 1/0、PostgreSQL は本物の boolean（要件 F-D-30）
				dialect().bindBoolean(st, index, value);

			} else if (param instanceof Data data) {

				// PostgreSQL の json / jsonb は文字列をそのまま受け取れない（要件 F-D-30）
				dialect().bindJson(st, index, data.getJsonString());

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

		/*
		 * SQL結果キャッシュを消す（要件 F-D-28）。
		 *
		 * <b>確定してから消す。</b>更新した直後に消すと、
		 * ロールバックしたときに消さなくてよいものまで消えるうえ、
		 * <b>コミット前の SELECT が未確定の値をキャッシュに入れてしまう。</b>
		 */
		flushCache();

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

		// 無かったことになるので、消す予定も捨てる（要件 F-D-28）
		discardCache();

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
			/*
			 * コミットせずに終わった。消す予定は捨てる（要件 F-D-28）。
			 * コミット済みなら flushCache() が先に走って空になっている。
			 */
			discardCache();
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
