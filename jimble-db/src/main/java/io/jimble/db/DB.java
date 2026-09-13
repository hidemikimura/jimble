package io.jimble.db;

import io.jimble.util.io.IOUtil;
import io.jimble.util.data.Data;
import io.jimble.db.data.ResultSetFetcher;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.data.SelectListResponse;
import io.jimble.util.paging.Paging;
import io.jimble.db.sql.*;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.internal.sql.query.parameter.Parameter;
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

	/* エラー内容（直前の1文だけ） */
	private CodeException error = null;

	/* トランザクションを開けてから、1度でもエラーが出たか */
	private boolean errorSinceTransaction = false;

	/**
	 * エラー判定
	 *
	 * <p>
	 * <b>直前の1文についてだけ答える。</b>次の文を実行すると戻る。
	 * トランザクション全体を見たいときは {@code DBTransaction} が見ている（要件 D-155）。
	 * </p>
	 *
	 * @return	エラーの場合 = true
	 */
	public boolean isError () {

		return error != null;

	}

	/**
	 * エラーを記録する
	 *
	 * <p>
	 * <b>{@code error} を立てるのはここだけにする。</b>
	 * 直に代入すると、<b>トランザクションの持ち越しに入らない</b>——
	 * その1文だけ静かに失敗して、トランザクションはそのままコミットされる（D-155）。
	 * </p>
	 *
	 * @param value	エラー
	 */
	private void setError (CodeException value) {

		this.error = value;

		if (value != null) {
			this.errorSinceTransaction = true;
		}

	}

	/**
	 * トランザクションを開けてから、1度でもエラーが出たか
	 *
	 * <p>
	 * <b>{@link #isError()} と違って、次の文では戻らない。</b>
	 * {@code DBTransaction} がコミットしてよいかを決めるのに使う。
	 * </p>
	 *
	 * @return	出ていれば true
	 */
	boolean isErrorSinceTransaction () {

		return errorSinceTransaction;

	}

	/**
	 * 持ち越しているエラーの印を消す
	 *
	 * <p>トランザクションの開始と、{@link #rollback()} と、終了から呼ぶ。</p>
	 */
	void clearErrorSinceTransaction () {

		this.errorSinceTransaction = false;

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
		this.enableLongConnectionLog = dbSource.conf().longConnectionLog();

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

		return this.dbSource.name();

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
			if (diff >= dbSource.conf().longConnectionTime()) {
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
	 *
	 * <p>
	 * <b>1文ごとにプールへ返す。</b>だから {@code DB} を閉じ忘れても、
	 * ふつうはコネクションが残らない（{@code DBUtil.getMainDB()} を
	 * 使い捨てにする書き方は、これがあるから成り立っている）。
	 * </p>
	 *
	 * <p>
	 * <b>返さないのはトランザクション中だけ</b>で、そのときは実行の終わりに
	 * 拾ってもらうよう登録する（要件 F-D-16）。
	 * </p>
	 */
	private void closeAfterQuery () {

		if (connection == null) {
			return;
		}

		try {
			if (isTransaction()) {
				/*
				 * 握ったまま文を抜ける。
				 *
				 * <b>ここでは登録しない。</b>トランザクションを始められるのは
				 * {@code beginTransaction()} だけで、そこで登録済みである。
				 * 両方に置くと<b>ミューテーションが生き残る</b>——
				 * 片方を消しても誰も落ちないコードは、要らないコードである。
				 */
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

		// 返したので、拾ってもらう必要はもう無い
		unregisterCloseTask();

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
			this.fetchSize = dbSource.conf().fetchSize();
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
	 * <p>
	 * <b>{@code null} には2つの意味がある（D-173）。</b>
	 * 「1件も無かった」と「読めなかった」である。<b>見分けるには
	 * {@link #isError()} を見ること</b>——
	 * </p>
	 *
	 * <pre>
	 * Data row = db.select(sql, id);
	 * if (db.isError()) { ... 読めなかった ... }
	 * if (row == null)  { ... 1件も無かった ... }
	 * </pre>
	 *
	 * <p>
	 * <b>{@code selectList} とは揃っていない。</b>あちらは 0件が空リストで、
	 * 読めなかったときだけ {@code null} である。<b>同じ select 系で
	 * {@code null} の意味が違う</b>——1.0 では
	 * <b>戻り値の型を変えられない</b>ので、揃えるのは 2.0 になる。
	 * それまでは、この2行を書くのが正しい読み方である。
	 * </p>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果。1件も無いか、読めなければ null
	 */
	public Data select(String sql, Object...params) {

		try (
			ResultSetFetcher fetcher = new ResultSetFetcher()
		) {

			selectListWithFetcher(fetcher, sql, params);

			if (fetcher.isError() || this.error != null) {
				return null;
			}

			Iterator<Data> iterator = fetcher.iterator();
			if (iterator.hasNext()) {
				return iterator.next();
			}

			return null;

		} catch (Exception ex) {

			Log.error(ex);

			setError(new CodeException("DB_999", ex.getMessage()));

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

			if (fetcher.isError() || this.error != null) {
				return null;
			}

			List<Data> res = new ArrayList<>();
			for (Data row : fetcher) {
				res.add(row);
			}

			return res;

		} catch (Exception ex) {

			Log.error(ex);

			setError(new CodeException("DB_999", ex.getMessage()));

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

		List<Data> list = selectList(builder);

		long rowCount = 0;
		Data data = select(builder.rowCountSql(dialect()), builder.rowCountParams());
		if (data != null) {
			rowCount = data.getLong("cnt");
		}

		Paging paging = null;
		if (builder.paging() != null && list != null) {
			builder.paging().set(list.size(), rowCount);
			paging = builder.paging();
		}

		return new SelectListResponse(list, rowCount, paging);

	}

	/**
	 * 複数件取得する(件数付き)
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果
	 */
	public SelectListResponse selectListWithRowCount (String sql, Object...params) {

		List<Data> list = selectList(sql, params);
		long rowCount = 0;

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
				rowCount = data.getLong("cnt");
			}
		}

		return new SelectListResponse(list, rowCount, null);

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

		long rowCount = 0;
		List<Data> list;

		Data data = select(builder.rowCountSql(dialect()), builder.rowCountParams());
		if (data != null) {
			rowCount = data.getLong("cnt");
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

			list = selectList(builder);
		}

		Paging paging = null;
		if (builder.paging() != null && list != null) {
			builder.paging().set(list.size(), rowCount);
			paging = builder.paging();
		}

		return new SelectListResponse(list, rowCount, paging);

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
				_fetchSize = dbSource.conf().fetchSize();
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

			fetcher.markError();
			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

			IOUtil.close(rs, st);

		} finally {

			/*
			 * <b>ここは closeAfterQuery() を呼ばない。</b>
			 * カーソルは呼ぶ側が1行ずつ読むので、読み終わるまで
			 * ResultSet を開いておく必要がある——つまり
			 * <b>コネクションを握ったまま抜ける。</b>
			 *
			 * 返すのは呼ぶ側の {@code db.close()} である。
			 * 呼ばれなければプールから1本消えるので、
			 * 実行の終わりに拾ってもらう（要件 F-D-16）。
			 */
			registerCloseTask();

		}

	}

	// endregion


	// region 登録する

	/**
	 * 登録する
	 *
	 * <p>
	 * <b>戻り値には2つの意味がある（D-173）。</b>
	 * 採番された値が取れればその値、取れなければ<b>入った件数</b>である。
	 * だから <b>{@code 1} が「id=1 を入れた」なのか「1件入った」なのかは、
	 * この戻り値だけでは分からない</b>——
	 * <b>その表に自動採番の列があるかどうかで決まる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>採番列を1本足しただけで、戻り値の意味が変わる。</b>
	 * 件数がほしいなら {@link #insertNoReturnKey}、
	 * 採番値がほしいなら採番列のある表でこちらを使うこと。
	 * </p>
	 *
	 * <p>
	 * 失敗したときは <b>{@code -1}</b> で、理由は {@link #getError()} に入る。
	 * 1.0 では戻り値の型を変えられないので、揃えるのは 2.0 になる。
	 * </p>
	 *
	 * @param builder   InsertBuilder
	 * @return  採番された値。採番列が無ければ入った件数。失敗したら -1
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
	 * <p>
	 * <b>戻り値には2つの意味がある（D-173）。</b>
	 * 採番された値が取れればその値、取れなければ<b>入った件数</b>である。
	 * だから <b>{@code 1} が「id=1 を入れた」なのか「1件入った」なのかは、
	 * この戻り値だけでは分からない</b>——
	 * <b>その表に自動採番の列があるかどうかで決まる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>採番列を1本足しただけで、戻り値の意味が変わる。</b>
	 * 件数がほしいなら {@link #insertNoReturnKey}、
	 * 採番値がほしいなら採番列のある表でこちらを使うこと。
	 * </p>
	 *
	 * <p>
	 * 失敗したときは <b>{@code -1}</b> で、理由は {@link #getError()} に入る。
	 * 1.0 では戻り値の型を変えられないので、揃えるのは 2.0 になる。
	 * </p>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  採番された値。採番列が無ければ入った件数。失敗したら -1
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

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

			return -1;

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion


	// region 実行する

	/**
	 * SQL をそのまま実行する（DDL や、ビルダーで組めない文）
	 *
	 * <p>
	 * <b>戻り値は「成功したか」である（D-173）。</b>
	 * かつては JDBC の {@code Statement#execute()} をそのまま返していた——
	 * あれは<b>「結果セットが返ってきたか」</b>であって、成功したかではない。
	 * {@code DELETE} も {@code CREATE TABLE} も、<b>うまくいったのに false</b> が返っていた。
	 * Javadoc も1行も無かったので、<b>読む側は false を失敗と読むしかない</b>。
	 * </p>
	 *
	 * <p>
	 * 失敗したときは {@link #getError()} に理由が入る（作法は
	 * <a href="https://jimble.io/ja/principles">原則</a>のとおり、戻り値で分岐して理由をここから取る）。
	 * </p>
	 *
	 * <p>
	 * <b>結果セットが要るなら {@code select} 系を使うこと。</b>
	 * ここでは結果セットを読まずに閉じる。
	 * </p>
	 *
	 * @param sql		SQL
	 * @param params	パラメータ
	 * @return	成功した場合 = true
	 */
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

			/*
			 * <b>戻り値は捨てる。</b>{@code Statement#execute()} が返すのは
			 * 「結果セットが返ってきたか」であって、成功したかではない。
			 */
			st.execute();

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return true;

		} catch (Exception ex) {

			Log.error(ex);

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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
				setError(new CodeException("DB_998", "executeBatch: SQLが一致しません"));
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

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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
	 * <p>
	 * <b>SQL が全部同じでなければならない</b>（要件 F-D-08）。
	 * 1本の {@code PreparedStatement} にパラメータだけを積み替えるためである。
	 * 違うものが混ざっていたら {@code DB_998} を立てて null を返す。
	 * </p>
	 *
	 * <p>
	 * <b>以前は確かめていなかった。</b>先頭の SQL に全員のパラメータを流し込むので、
	 * {@code value()} の並びが違うビルダーを混ぜると
	 * <b>例外も警告も無しに値が横にずれて入っていた</b>
	 * （個数が合っていると DB も気づかない）。
	 * {@code executeBatch} は元から確かめていたので、そちらに揃えた。
	 * </p>
	 *
	 * @param builderList   InsertBuilder
	 * @return  結果（SQL が揃っていなければ null）
	 */
	public List<Long> insertBatch (List<InsertBuilder> builderList) {

		String sql = null;
		List<List<Object>> paramsList = new ArrayList<>();
		for (InsertBuilder builder : builderList) {
			String builderSql = builder.sql(dialect());
			if (sql == null) {
				sql = builderSql;
			} else if (!sql.equals(builderSql)) {
				setError(new CodeException("DB_998", "insertBatch: SQLが一致しません"));
				Log.error("insertBatch mismatch:\n" + sql + "\n" + builderSql);
				return null;
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

			setError(new CodeException("DB_999", ex.getMessage()));

			// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
			this.plannedTags = null;

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
	 * <p>
	 * <b>受け取るのは {@code List<Integer>} ではない（D-173）。</b>
	 * {@code List<Integer>} で固定すると、<b>{@code List<Long>} を受ける版を
	 * あとから足せない</b>——消去したあとの署名が同じ
	 * （{@code isBatchSuccess(List)}）になって衝突する。
	 * {@code insertBatch} が採番値を {@code Long} で返すようになった日に、
	 * <b>置く場所が無い</b>ことに気づくことになる。
	 * </p>
	 *
	 * <p>
	 * JDBC の約束では、{@code -2}（{@code SUCCESS_NO_INFO}）も成功である。
	 * </p>
	 *
	 * @param list	バッチ実行結果
	 * @return	成功の場合 = true
	 */
	public static boolean isBatchSuccess (List<? extends Number> list) {

		if (list == null || list.isEmpty()) {
			return false;
		}

		for (Number res : list) {

			if (res == null) {
				return false;
			}

			long value = res.longValue();

			if (value < 0 && value != -2) {
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
	 *
	 * <p>
	 * <b>ここからコネクションを握り続ける。</b>{@code closeAfterQuery()} は
	 * トランザクション中は返さないので、コミットもロールバックもされなければ
	 * <b>プールへ戻らない。</b>実行の終わりに拾ってもらうよう登録する（要件 F-D-16）。
	 * </p>
	 *
	 * <p>
	 * <b>1文も流さずに終わる道があるので、ここで登録する。</b>
	 * {@code closeAfterQuery()} の側だけに置くと、
	 * 開始してすぐ抜けたときに登録されない。
	 * </p>
	 */
	public void beginTransaction() throws Exception {

		if (connection == null) {
			getWriteConnection();
		}

		connection.setAutoCommit(false);

		// ここから先のエラーを持ち越す（要件 D-155）
		clearErrorSinceTransaction();

		registerCloseTask();

	}

	/**
	 * トランザクションをコミットする
	 */
	public void commit() throws Exception {

		if (connection == null) {
			return;
		}

		requireNoErrorSinceTransaction();

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
	/**
	 * エラーが出ていたらコミットさせない
	 *
	 * <p>
	 * <b>ここが無いと、部分的にコミットされる。</b>
	 * jimble の DB はエラーを戻り値で返す（原則4）ので、
	 * {@code db.update(...)} が -1 を返しても<b>処理は正常に終わったように見える</b>——
	 * そのまま commit まで進んでいた（D-155）。
	 * </p>
	 *
	 * <p>
	 * <b>自分で巻き戻してから投げる。</b>{@code endTransaction()} に任せると、
	 * あちらは {@code setAutoCommit(true)} を呼ぶだけなので、
	 * <b>JDBC の決まりで、開いていたトランザクションがコミットされてしまう</b>——
	 * 拒んだはずのものが入る。
	 * </p>
	 *
	 * @throws CodeException	トランザクションを開けてから1度でもエラーが出ていた場合
	 */
	private void requireNoErrorSinceTransaction () throws Exception {

		if (!errorSinceTransaction) {
			return;
		}

		CodeException cause = this.error;

		if (isTransaction()) {
			connection.rollback();
		}

		discardCache();

		throw new CodeException("DB_004"
			, """
			トランザクションの中でエラーが出ているので、コミットしませんでした。
			  最後のエラー: %s
			  エラーを見て続けたいなら、いったん rollback() してから書き直してください
			  （SQL → commit → SQL（失敗）→ rollback → SQL → commit と書けます）。
			""".formatted(cause == null ? "（直前の文は成功。それより前で出ています）" : cause.getMessage()));

	}

	public void rollback() throws Exception {

		if (connection == null) {
			return;
		}

		if (isTransaction()) {
			connection.rollback();
		}

		// 無かったことになるので、消す予定も捨てる（要件 F-D-28）
		discardCache();

		/*
		 * <b>エラーの持ち越しもここで畳む</b>（要件 D-156）。
		 *
		 * 巻き戻したということは、<b>呼んだ側がエラーを見て決着を付けた</b>ということである。
		 * 畳まないと、そのあと書き直して {@code commit()} しても
		 * <b>「エラーが出ている」と言って断られる</b>——
		 * <b>SQL → commit → SQL（失敗）→ rollback → SQL → commit</b> と
		 * 分岐して続ける書き方ができなくなる。
		 */
		clearErrorSinceTransaction();

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
			setError(new CodeException("DB_999", ex.getMessage()));
		} finally {
			/*
			 * コミットせずに終わった。消す予定は捨てる（要件 F-D-28）。
			 * コミット済みなら flushCache() が先に走って空になっている。
			 */
			discardCache();
			clearErrorSinceTransaction();
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

		unregisterCloseTask();

		logLongConnection();

	}

	// endregion

	// region 閉じ忘れを拾う（要件 F-D-16）

	/* 実行の終わりに拾ってもらうための登録 */
	private Context.CloseTask closeTask = null;

	/**
	 * 実行の終わりに拾ってもらう
	 *
	 * <p>
	 * <b>コネクションを握ったまま文を抜けるときにだけ登録する。</b>
	 * 作った {@code DB} を片端から登録すると、
	 * <b>1文ごとに返している大多数まで後始末の列に積まれる</b>——
	 * {@code DbSessionStore#load()} は1メソッドで3本作るし、
	 * 長いバッチのループなら際限なく増える。
	 * </p>
	 *
	 * <p>
	 * <b>コンテキストの外（起動時のマイグレーションなど）では何もしない。</b>
	 * 拾う相手がいないので、そこは呼ぶ側が閉じる。
	 * </p>
	 */
	private void registerCloseTask () {

		if (closeTask != null || connection == null || !Context.isBound()) {
			return;
		}

		closeTask = () -> {

			if (connection == null) {
				return;
			}

			/*
			 * ここへ来たということは、コネクションを握ったまま実行が終わったということである。
			 *
			 * <b>黙って閉じない。</b>直してしまうと、
			 * 「直っているので誰も直さない」まま漏れ続ける（要件 F-D-16）。
			 */
			Log.error(leakMessage());

			close();

		};

		Context.current().onClose(closeTask);

	}

	/**
	 * 拾ってもらうのをやめる
	 *
	 * <p>正しく返したものは、実行の終わりに呼ばれる必要がない。</p>
	 */
	private void unregisterCloseTask () {

		if (closeTask == null) {
			return;
		}

		if (Context.isBound()) {
			Context.current().removeCloseTask(closeTask);
		}

		closeTask = null;

	}

	/**
	 * 閉じ忘れのログ
	 *
	 * <p>
	 * <b>どちらの握り方かで文面を変える。</b>
	 * 「閉じてください」とだけ言われても、どこを直せばよいのか分からない。
	 * </p>
	 *
	 * @return	メッセージ
	 */
	private String leakMessage () {

		String name = dbSource == null ? "?" : dbSource.name();

		if (isTransaction()) {
			return "コミットもロールバックもされていないトランザクションが残っていました。"
				+ "ロールバックして閉じます: " + name;
		}

		return "閉じられていない DB が残っていました。閉じます: " + name
			+ "（selectListWithFetcher はカーソルなので、close() までコネクションを返しません）";

	}

	// endregion

}
