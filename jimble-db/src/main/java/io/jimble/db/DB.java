package io.jimble.db;

import io.jimble.util.annotation.CheckReturnValue;

import io.jimble.util.internal.Docs;
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

	// region 実行エラー（要件 D-193）

	/*
	 * 直前の失敗（トランザクションの DB_004 の説明に出す）。
	 *
	 * 2.0 で<b>失敗はすべて例外</b>になったので、呼ぶ側がこれを見ることはもう無い
	 * （1.x の {@code isError()} / {@code getError()} は消した）。
	 */
	private CodeException lastError = null;

	/* トランザクションを開けてから、1度でもエラーが出たか */
	private boolean errorSinceTransaction = false;

	/*
	 * 直前の insert が「採番値」を返したか。
	 *
	 * <b>JDBC の戻りだけでは、採番値なのか件数なのか分からない</b>（要件 F-D-30）。
	 * {@link #insertKey(String, Object...)} が「採番されなかった」を見分けるのに使う。
	 */
	private boolean insertReturnedKey = false;

	/**
	 * 失敗を例外にする（要件 D-193）
	 *
	 * <p>
	 * <b>例外を作るのはここだけにする。</b>ここを通らずに投げると、
	 * <b>トランザクションの持ち越しに入らない</b>——例外を受け止めて続けた文が
	 * そのままコミットされる（D-155）。
	 * </p>
	 *
	 * <p>
	 * 一意制約の違反（PostgreSQL の SQLSTATE 23505、MySQL の 1062）は {@link DuplicateKeyException}、
	 * それ以外は {@link SqlExecuteException}。元の例外は cause に残す（{@code getCause().getCause()}）。
	 * </p>
	 *
	 * @param what	何をしていたか（SELECT など）
	 * @param ex	元の例外
	 * @return	投げる例外
	 */
	private SqlExecuteException fail (String what, Exception ex) {

		// 失敗した更新の「消す予定」を次の呼び出しに持ち越さない（要件 F-D-28）
		this.plannedTags = null;

		CodeException error = ex instanceof CodeException code ? code : new CodeException("DB_999", ex.getMessage(), ex);

		this.lastError = error;
		this.errorSinceTransaction = true;

		if (isDuplicateKey(error)) {
			return new DuplicateKeyException(
				"%s が一意制約に当たりました: %s".formatted(what, ex.getMessage()), error);
		}

		return new SqlExecuteException(
			"%s を実行できませんでした: %s".formatted(what, ex.getMessage()), error);

	}

	/**
	 * 一意制約の違反か（PostgreSQL は SQLSTATE 23505、MySQL はエラーコード 1062）
	 *
	 * @param error	エラー
	 * @return	違反なら true
	 */
	static boolean isDuplicateKey (CodeException error) {

		if (error == null) {
			return false;
		}

		for (Throwable t = error.getCause(); t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				for (SQLException e = sql; e != null; e = e.getNextException()) {
					if ("23505".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
						return true;
					}
				}
			}
			if (t.getCause() == t) {
				break;
			}
		}

		return false;

	}

	/**
	 * トランザクションを開けてから、1度でもエラーが出たか
	 *
	 * <p>
	 * 例外を受け止めて続けても、<b>{@link Tx#commit()} はこれを見て断る</b>（DB_004）。
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
	 * <p>トランザクションの開始と、巻き戻しと、終了から呼ぶ。</p>
	 */
	void clearErrorSinceTransaction () {

		this.errorSinceTransaction = false;
		this.rollbackOnlyReason = null;

	}

	/*
	 * 中に合流したトランザクションが「巻き戻したい」と言った理由（要件 D-190）。
	 * 立っていれば、外の commit は断る。
	 */
	private String rollbackOnlyReason = null;

	/**
	 * 巻き戻し専用にする
	 *
	 * <p>
	 * 外のトランザクションに合流した {@link Tx} が {@code rollback()} したとき、
	 * または commit せずに閉じたときに呼ぶ。<b>外の {@link Tx#commit()} は DB_005 で断る</b>。
	 * </p>
	 *
	 * @param reason	理由（エラーに出す）
	 */
	void markRollbackOnly (String reason) {

		if (this.rollbackOnlyReason == null) {
			this.rollbackOnlyReason = reason;
		}

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
	private void getReadConnection() throws SQLException {

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

		} catch (SQLException ex) {

			throw ex;

		} catch (Exception ex) {

			throw new SQLException("コネクションを取れませんでした: " + ex.getMessage(), ex);

		}

	}

	/**
	 * 書き込みコネクションを取得する
	 */
	private void getWriteConnection() throws SQLException {

		if (connection != null) {
			return;
		}

		try {
			connection = dbSource.getWriteDataSource().getConnection();
			connectionStart = System.currentTimeMillis();
		} catch (SQLException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new SQLException("コネクションを取れませんでした: " + ex.getMessage(), ex);
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
	 * Optional&lt;Data&gt; customer = db.selectCached(
	 *     SQL.select()
	 *         .from(Customer.instance())
	 *         .inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
	 *         .where(Customer.id.eq(1)));
	 * </pre>
	 *
	 * <p>
	 * <b>更新があれば自動で消える。</b>消し方は
	 * {@link io.jimble.db.sqlcache.SqlCacheTags} を参照。
	 * 戻り値と失敗の扱いは {@link #select(SelectBuilder)} と同じ（2.0。要件 D-193）。
	 * </p>
	 *
	 * @param builder	SelectBuilder
	 * @return	結果。1件も無ければ空
	 * @throws SqlExecuteException	読めなかったとき
	 */
	@CheckReturnValue
	public Optional<Data> selectCached (SelectBuilder builder) {

		List<Data> rows = selectListCached(builder);

		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());

	}

	/**
	 * キャッシュを見てから複数件取得する（要件 F-D-28）
	 *
	 * <p>
	 * <b>トランザクションの中では素通しで引く。</b>
	 * まだ確定していない値をキャッシュに残さないためである。
	 * 戻り値と失敗の扱いは {@link #selectList(SelectBuilder)} と同じ（2.0。要件 D-193）。
	 * </p>
	 *
	 * @param builder	SelectBuilder
	 * @return	結果。0件なら空リスト（null は返さない）
	 * @throws SqlExecuteException	読めなかったとき
	 */
	@CheckReturnValue
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

		// 読めなければここで投げる。失敗はキャッシュに入らない
		List<Data> rows = selectList(sql, params);

		SqlCache.put(key, SqlCacheTags.of(getDBName(), builder, rows), rows);

		return rows;

	}

	// endregion


	// region 1件取得する

	/**
	 * 1件取得する
	 *
	 * @param builder   SelectBuilder
	 * @return  結果。1件も無ければ空
	 * @throws SqlExecuteException  読めなかったとき
	 * @see #select(String, Object...)
	 */
	@CheckReturnValue
	public Optional<Data> select (SelectBuilder builder) {

		return select(builder.sql(dialect()), builder.params());

	}

	/**
	 * 1件取得する
	 *
	 * <pre>
	 * Data user = db.select(sql, id).orElseThrow(() -&gt; new HttpException(404));
	 * </pre>
	 *
	 * <p>
	 * <b>「1件も無かった」は空の {@link Optional}、「読めなかった」は例外</b>（2.0。要件 D-193）。
	 * 1.x は両方を {@code null} で返していたので、{@code if (user == null)} と書くと
	 * <b>DB が読めなかった日に「そんな利用者はいません」と答えていた</b>。
	 * 型を変えたので、{@code Data row = db.select(...)} はコンパイルが通らない——書き換え漏れが残らない。
	 * </p>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果。1件も無ければ空
	 * @throws SqlExecuteException  読めなかったとき
	 */
	@CheckReturnValue
	public Optional<Data> select (String sql, Object...params) {

		try (
			ResultSetFetcher fetcher = new ResultSetFetcher()
		) {

			selectListWithFetcher(fetcher, sql, params);

			Iterator<Data> iterator = fetcher.iterator();
			if (iterator.hasNext()) {
				return Optional.of(iterator.next());
			}

			return Optional.empty();

		} catch (SqlExecuteException ex) {

			throw ex;

		} catch (Exception ex) {

			throw fail("SELECT", ex);

		} finally {

			closeAfterQuery();

		}

	}

	/**
	 * 1件取得する（読めなければ投げる）
	 *
	 * @param builder   SelectBuilder
	 * @return  結果。1件も無ければ null
	 * @throws SqlExecuteException  読めなかったとき
	 * @deprecated 2.0 で {@link #select(SelectBuilder)} が同じ意味（読めなければ投げる）になった。
	 *             {@code select(builder).orElse(null)} と同じ。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@CheckReturnValue
	public Data selectOrThrow (SelectBuilder builder) {

		return select(builder).orElse(null);

	}

	/**
	 * 1件取得する（読めなければ投げる）
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果。<b>1件も無ければ null</b>
	 * @throws SqlExecuteException  読めなかったとき
	 * @deprecated 2.0 で {@link #select(String, Object...)} が同じ意味（読めなければ投げる）になった。
	 *             {@code select(sql, params).orElse(null)} と同じ。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@CheckReturnValue
	public Data selectOrThrow (String sql, Object...params) {

		return select(sql, params).orElse(null);

	}

	// endregion

	// region 複数件取得する

	/**
	 * 複数件取得する
	 *
	 * @param builder   SelectBuilder
	 * @return  結果。0件なら空リスト（null は返さない）
	 * @throws SqlExecuteException  読めなかったとき
	 */
	@CheckReturnValue
	public List<Data> selectList (SelectBuilder builder) {

		return selectList(builder.sql(dialect()), builder.params());

	}

	/**
	 * 複数件取得する
	 *
	 * <p>
	 * <b>0件は空リスト、読めなかったときは例外</b>（2.0。要件 D-193）。
	 * 1.x は読めなかったときに {@code null} を返していた。
	 * </p>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果。0件なら空リスト（null は返さない）
	 * @throws SqlExecuteException  読めなかったとき
	 */
	@CheckReturnValue
	public List<Data> selectList (String sql, Object...params) {

		try (
			ResultSetFetcher fetcher = new ResultSetFetcher()
		) {

			selectListWithFetcher(fetcher, sql, params);

			List<Data> res = new ArrayList<>();
			for (Data row : fetcher) {
				res.add(row);
			}

			return res;

		} catch (SqlExecuteException ex) {

			throw ex;

		} catch (Exception ex) {

			throw fail("SELECT", ex);

		} finally {

			closeAfterQuery();

		}

	}

	/**
	 * 複数件取得する（読めなければ投げる）
	 *
	 * @param builder   SelectBuilder
	 * @return  結果。0件なら空リスト
	 * @throws SqlExecuteException  読めなかったとき
	 * @deprecated 2.0 で {@link #selectList(SelectBuilder)} が同じ意味になった。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@CheckReturnValue
	public List<Data> selectListOrThrow (SelectBuilder builder) {

		return selectList(builder);

	}

	/**
	 * 複数件取得する（読めなければ投げる）
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  結果。<b>0件なら空リスト</b>
	 * @throws SqlExecuteException  読めなかったとき
	 * @deprecated 2.0 で {@link #selectList(String, Object...)} が同じ意味になった。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@CheckReturnValue
	public List<Data> selectListOrThrow (String sql, Object...params) {

		return selectList(sql, params);

	}

	// endregion

	// region 複数件取得する（大量件数テーブル）

	/**
	 * 複数件取得する（大量件数テーブル）
	 *
	 * @param builder   SelectBuilder
	 * @return  結果。0件なら空リスト
	 * @throws SqlExecuteException  読めなかったとき
	 */
	@CheckReturnValue
	public List<Data> selectListPerformance (SelectBuilder builder) {

		// メインテーブルのPK列のみ取得する
		List<Data> _list = selectList(builder.simpleSql(dialect()), builder.params());

		return selectListByPk(builder, _list);

	}

	/**
	 * PK の一覧で読み直す
	 *
	 * @param builder	元の builder（書き換えない）
	 * @param pkRows	PK列だけの行
	 * @return	結果
	 */
	private List<Data> selectListByPk (SelectBuilder builder, List<Data> pkRows) {

		// PK列を条件にする
		Column pkColumn = builder.mainTablePkColumn();

		List<Long> idTable = new ArrayList<>();
		for (Data data : pkRows) {
			idTable.add(data.getLong(pkColumn));
		}

		/*
		 * 写しに組み直す（要件 D-190）。1.4 までは<b>渡された builder をそのまま書き換えていた</b>ので、
		 * 呼んだあとに同じ builder で件数を数えたり次のページを読んだりすると、
		 * WHERE が「今回の id の IN」にすり替わっていた。
		 */
		SelectBuilder byPk = builder.copy();
		byPk.clearWhere();
		byPk.clearHaving();
		byPk.offset(-1);
		byPk.limit(-1);
		byPk.where(
			pkColumn.in(idTable)
		);

		return selectList(byPk);

	}

	// endregion

	// region 複数件取得する(件数付き)

	/**
	 * 件数を読む
	 *
	 * @param sql		件数の SQL
	 * @param params	パラメータ
	 * @return	件数
	 */
	private long selectRowCount (String sql, List<Object> params) {

		return select(sql, params).map(row -> row.getLong("cnt")).orElse(0L);

	}

	/**
	 * 複数件取得する(件数付き)
	 *
	 * @param builder   SelectBuilder
	 * @return  結果（{@code list()} は null にならない）
	 * @throws SqlExecuteException  一覧か件数を読めなかったとき
	 */
	@CheckReturnValue
	public SelectListResponse selectListWithRowCount (SelectBuilder builder) {

		List<Data> list = selectList(builder);

		long rowCount = selectRowCount(builder.rowCountSql(dialect()), builder.rowCountParams());

		Paging paging = null;
		if (builder.paging() != null) {
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
	 * @return  結果（{@code list()} は null にならない）
	 * @throws SqlExecuteException  一覧か件数を読めなかったとき、SQL に FROM が無いとき
	 */
	@CheckReturnValue
	public SelectListResponse selectListWithRowCount (String sql, Object...params) {

		/*
		 * 語として探す（要件 D-190）。1.4 までは indexOf("FROM") だったので、
		 * {@code from_date} のような列名や {@code order_no} に当たって、<b>黙って違う件数</b>を数えていた。
		 */
		int indexOrderBy = lastKeyword(sql, "ORDER\\s+BY");
		int indexLimit = lastKeyword(sql, "LIMIT");
		int indexFrom = firstKeyword(sql, "FROM");

		List<Data> list = selectList(sql, params);

		if (indexFrom < 0) {
			throw fail("SELECT", new CodeException("DB_999", "selectListWithRowCount: FROM が見つかりません: " + sql));
		}

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

		/*
		 * 捨てた部分の ? の分だけ、<b>前と後ろの両方から</b>パラメータを捨てる（要件 D-190）。
		 * 1.4 までは後ろからしか捨てなかったので、{@code SELECT ? AS tag, ... WHERE x = ?} では
		 * <b>WHERE に SELECT 句の値が入り、黙って違う件数</b>になっていた。
		 */
		int leading = countPlaceholders(sql, 0, indexFrom);
		List<Object> newParams;
		if (params == null) {
			newParams = new ArrayList<>();
		} else {
			newParams = new ArrayList<>(Parameter.flatten(new SQLParameterList(params)));
			for (int i = 0; i < leading && !newParams.isEmpty(); i++) {
				newParams.removeFirst();
			}
			while (newParams.size() > paramCount) {
				newParams.removeLast();
			}
		}

		long rowCount = selectRowCount(sb.toString(), newParams);

		return new SelectListResponse(list, rowCount, null);

	}

	private static final java.util.Map<String, java.util.regex.Pattern> KEYWORDS = new java.util.concurrent.ConcurrentHashMap<>();

	private static java.util.regex.Matcher keyword (String sql, String word) {

		return KEYWORDS.computeIfAbsent(word,
			w -> java.util.regex.Pattern.compile("(?i)(?<![A-Za-z0-9_`\"])" + w + "(?![A-Za-z0-9_`\"])")).matcher(sql);

	}

	private static int countPlaceholders (String sql, int from, int to) {

		int count = 0;
		for (int i = from; i < to; i++) {
			if (sql.charAt(i) == '?') {
				count++;
			}
		}
		return count;

	}

	private static int firstKeyword (String sql, String word) {

		java.util.regex.Matcher m = keyword(sql, word);
		return m.find() ? m.start() : -1;

	}

	private static int lastKeyword (String sql, String word) {

		java.util.regex.Matcher m = keyword(sql, word);
		int last = -1;
		while (m.find()) {
			last = m.start();
		}
		return last;

	}

	// endregion

	// region 複数件取得する(大量件数テーブル、件数付き)

	/**
	 * 複数件取得する(件数付き)
	 *
	 * @param builder   SelectBuilder
	 * @return  結果（{@code list()} は null にならない）
	 * @throws SqlExecuteException  一覧か件数を読めなかったとき
	 */
	@CheckReturnValue
	public SelectListResponse selectListWithRowCountPerformance (SelectBuilder builder) {

		long rowCount = selectRowCount(builder.rowCountSql(dialect()), builder.rowCountParams());

		// メインテーブルのPK列のみ取得する（渡された builder は書き換えない。要件 D-190）
		List<Data> list = selectListByPk(builder, selectList(builder.simpleSql(dialect()), builder.params()));

		Paging paging = null;
		if (builder.paging() != null) {
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
	 * @throws SqlExecuteException	読めなかったとき
	 */
	public void selectListWithFetcher (ResultSetFetcher fetcher, SelectBuilder builder) {

		selectListWithFetcher(fetcher, builder.sql(dialect()), builder.params());

	}

	/**
	 * 逐次取得で取得する
	 *
	 * <p>
	 * <b>読めなかったときは例外</b>（2.0。要件 D-193）。そのときはコネクションも返してから投げる。
	 * 読めたときはカーソルを開いたまま返すので、読み終わったら {@code db.close()} すること。
	 * </p>
	 *
	 * @param fetcher	ResultSetFetcher
	 * @param sql		SQL
	 * @param params	パラメータ
	 * @throws SqlExecuteException	読めなかったとき
	 */
	public void selectListWithFetcher (ResultSetFetcher fetcher, String sql, Object...params) {

		// SQLを実行する
		PreparedStatement st = null;
		ResultSet rs = null;
		try {

			/*
			 * コネクションは try の中で取る（要件 D-190）。
			 * 外で取っていたころは、取れなくてもログだけ出して先へ進み、
			 * <b>エラーが「connection is null」の NPE になっていた</b>——プールの満杯も接続拒否も同じ顔だった。
			 */
			getReadConnection();

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

			fetcher.markError();

			IOUtil.close(rs, st);

			// 読めなかったのでカーソルは無い。コネクションを返してから投げる（トランザクション中は握ったまま）
			closeAfterQuery();

			throw fail("SELECT", ex);

		} finally {

			/*
			 * <b>読めたときは closeAfterQuery() を呼ばない。</b>
			 * カーソルは呼ぶ側が1行ずつ読むので、読み終わるまで
			 * ResultSet を開いておく必要がある——つまり
			 * <b>コネクションを握ったまま抜ける。</b>
			 *
			 * 返すのは呼ぶ側の {@code db.close()} である。
			 * 呼ばれなければプールから1本消えるので、
			 * 実行の終わりに拾ってもらう（要件 F-D-16）。
			 * 失敗したときは上で返しているので、ここは何もしない（connection が null）。
			 */
			registerCloseTask();

		}

	}

	// endregion


	// region 登録する

	/**
	 * 登録する
	 *
	 * @param builder   InsertBuilder
	 * @throws SqlExecuteException  入らなかったとき（一意制約なら {@link DuplicateKeyException}）
	 * @see #insert(String, Object...)
	 */
	public void insert (InsertBuilder builder) {

		insertRaw(builder);

	}

	/**
	 * 登録する
	 *
	 * <p>
	 * <b>値を返さない</b>（2.0。要件 D-193）。1.x は「採番値が取れればその値、取れなければ入った件数」を返していたので、
	 * 返ってきた {@code 1} が「id=1」なのか「1件」なのかは<b>その表に採番列があるかどうかで決まっていた</b>。
	 * </p>
	 *
	 * <ul>
	 *   <li>採番値が欲しい → {@link #insertKey(String, Object...)}（採番されなければ例外）</li>
	 *   <li>件数が欲しい（{@code INSERT ... SELECT} など）→ {@link #execute(String, Object...)}</li>
	 * </ul>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @throws SqlExecuteException  入らなかったとき（一意制約なら {@link DuplicateKeyException}）
	 */
	public void insert (String sql, Object...params) {

		insertRaw(sql, params);

	}

	/**
	 * 登録して、採番された値を返す
	 *
	 * @param builder   InsertBuilder
	 * @return  採番された値
	 * @throws SqlExecuteException  入らなかったとき、採番値が返らなかったとき
	 */
	public long insertKey (InsertBuilder builder) {

		return requireKey(insertRaw(builder));

	}

	/**
	 * 登録して、採番された値を返す
	 *
	 * <p>
	 * <b>採番値以外を返さない。</b>採番されなかったら投げる——
	 * 採番列の無い表に入れているなら {@link #insert(String, Object...)} が正しい。
	 * </p>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  採番された値
	 * @throws SqlExecuteException  入らなかったとき、採番値が返らなかったとき
	 */
	public long insertKey (String sql, Object...params) {

		return requireKey(insertRaw(sql, params));

	}

	/**
	 * 採番値が返っていなければ投げる
	 *
	 * @param value	{@link #insertRaw} の戻り値
	 * @return	採番された値
	 */
	private long requireKey (long value) {

		if (!insertReturnedKey) {
			throw new SqlExecuteException(
				"採番された値が返りませんでした"
					+ "（採番列の無い表に入れているなら insert を使ってください）");
		}

		return value;

	}

	/**
	 * 登録する（件数を返す）
	 *
	 * @param builder   InsertBuilder
	 * @return  入った件数
	 * @throws SqlExecuteException  入らなかったとき
	 * @deprecated 2.0 で {@link #insert(InsertBuilder)} が値を返さなくなり、1.x の2義（採番値か件数か）が無くなった。
	 *             件数は1件の INSERT なら 1 なので {@code insert} を使う。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@CheckReturnValue
	public long insertNoReturnKey (InsertBuilder builder) {

		String sql = builder.sql(dialect());
		List<Object> params = builder.params();

		if (isSqlCacheEnabled()) {
			plan(SqlCacheTags.of(getDBName(), builder));
		}

		return executeUpdate("INSERT", sql, params);

	}

	/**
	 * 登録する（件数を返す）
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  入った件数
	 * @throws SqlExecuteException  入らなかったとき
	 * @deprecated 2.0 で {@link #insert(String, Object...)} が値を返さなくなった。
	 *             件数が要るなら {@link #execute(String, Object...)}。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@CheckReturnValue
	public long insertNoReturnKey (String sql, Object...params) {

		return executeUpdate("INSERT", sql, params);

	}

	/**
	 * 登録する（中身。消す予定を置いてから生 SQL 版に降りる）
	 *
	 * @param builder	InsertBuilder
	 * @return	採番値か件数（{@link #insertReturnedKey} で見分ける）
	 */
	private long insertRaw (InsertBuilder builder) {

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

		return insertRaw(sql, params);

	}

	/**
	 * 登録する（中身）
	 *
	 * @param sql		SQL
	 * @param params	パラメータ
	 * @return	採番値か件数（{@link #insertReturnedKey} で見分ける）
	 */
	private long insertRaw (String sql, Object...params) {

		this.insertReturnedKey = false;

		// SQLを実行する
		PreparedStatement st = null;
		ResultSet rs = null;
		try {

			getWriteConnection();

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

				if (key > 0) {
					this.insertReturnedKey = true;
					return key;
				}

				return count;

			}

			return count;

		} catch (Exception ex) {

			throw fail("INSERT", ex);

		} finally {

			IOUtil.close(rs, st);
			closeAfterQuery();

		}

	}

	// endregion

	// region 更新する

	/**
	 * 更新する
	 *
	 * @param builder   UpdateBuilder
	 * @return  更新した件数
	 * @throws SqlExecuteException  失敗したとき
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
	 * <p>
	 * <b>失敗は例外</b>（2.0。要件 D-193）。1.x は {@code -1} を返していたので、
	 * 文として {@code db.update(...);} と書くと失敗が消えていた。
	 * 戻り値は当たった件数なので、捨ててよい。
	 * </p>
	 *
	 * @param sql       SQL
	 * @param params    パラメータ
	 * @return  更新した件数
	 * @throws SqlExecuteException  失敗したとき
	 */
	public int update (String sql, Object...params) {

		return executeUpdate("UPDATE", sql, params);

	}

	// endregion

	// region 削除する

	/**
	 * 削除する
	 *
	 * @param builder   DeleteBuilder
	 * @return  削除した件数
	 * @throws SqlExecuteException  失敗したとき
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
	 * @return  削除した件数
	 * @throws SqlExecuteException  失敗したとき
	 */
	public int delete (String sql, Object...params) {

		return executeUpdate("DELETE", sql, params);

	}

	/**
	 * 更新系の1文を流す（中身）
	 *
	 * @param what		何をしていたか
	 * @param sql		SQL
	 * @param params	パラメータ
	 * @return	件数
	 */
	private int executeUpdate (String what, String sql, Object...params) {

		// SQLを実行する
		PreparedStatement st = null;
		try {

			/*
			 * コネクションは try の中で取る（要件 D-190）。
			 * 外で取っていたころは、取れなくてもログだけ出して先へ進み、
			 * <b>エラーが「connection is null」の NPE になっていた</b>——プールの満杯も接続拒否も同じ顔だった。
			 */
			getWriteConnection();

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

			throw fail(what, ex);

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
	 * <b>戻り値は当たった件数</b>、<b>失敗は例外</b>（2.0。要件 D-193）。
	 * DDL と、結果セットを返す文は 0 を返す（結果セットは読まずに閉じる。要るなら {@code select} 系を使う）。
	 * </p>
	 *
	 * <p>
	 * 1.x は「成功したか」の {@code boolean} を返していたので、{@code if (!db.execute(...))} はコンパイルが通らなくなる。
	 * </p>
	 *
	 * @param sql		SQL
	 * @param params	パラメータ
	 * @return	当たった件数（DDL と結果セットを返す文は 0）
	 * @throws SqlExecuteException  失敗したとき
	 */
	public int execute (String sql, Object...params) {

		// SQLを実行する
		PreparedStatement st = null;
		try {

			getWriteConnection();

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			// SQLパラメータを設定する
			setParameters(st, new SQLParameterList(params));

			long start = System.nanoTime();

			/*
			 * {@code Statement#execute()} が返すのは「結果セットが返ってきたか」であって、成功したかではない。
			 * 結果セットでなければ件数を読む。
			 */
			boolean resultSet = st.execute();
			int count = resultSet ? 0 : Math.max(st.getUpdateCount(), 0);

			Context.recordSqlExecution(System.nanoTime() - start, sql);

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return count;

		} catch (Exception ex) {

			throw fail("SQL", ex);

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	// endregion


	// region バッチ実行

	/**
	 * 束ねた SQL が全部同じか確かめる
	 *
	 * @param what			呼んだメソッド
	 * @param builderList	ビルダー
	 * @param paramsList	パラメータを詰める先
	 * @return	SQL
	 */
	private String sameSql (String what, List<? extends IBuilder> builderList, List<List<Object>> paramsList) {

		String sql = null;
		for (IBuilder builder : builderList) {
			String builderSql = builder.sql(dialect());
			if (sql == null) {
				sql = builderSql;
			} else if (!sql.equals(builderSql)) {
				throw fail(what, new CodeException("DB_998",
					what + ": SQL が一致しません（束ねられるのは同じ形の文だけです）\n  " + sql + "\n  " + builderSql));
			}
			paramsList.add(builder.params());
		}
		return sql;

	}

	/**
	 * 登録バッチ実行
	 *
	 * <p>
	 * <b>SQL が全部同じでなければならない</b>（要件 F-D-08）。違うものが混ざっていたら {@code DB_998} の例外。
	 * 空の一覧は何もせず空リストを返す（2.0。1.x は {@code null}）。
	 * </p>
	 *
	 * @param builderList   ビルダー
	 * @return  1文ずつの件数
	 * @throws SqlExecuteException  失敗したとき
	 */
	public List<Integer> executeBatch (List<IBuilder> builderList) {

		List<List<Object>> paramsList = new ArrayList<>();
		String sql = sameSql("executeBatch", builderList, paramsList);

		// SQL結果キャッシュを消す（要件 F-D-28）。SQL が同じなので、消す先も同じ
		if (isSqlCacheEnabled()) {
			plan(SqlCacheTags.of(getDBName(), builderList));
		}

		return executeBatch(sql, paramsList);

	}

	/**
	 * バッチ実行
	 *
	 * @param sql           SQL
	 * @param paramsList    パラメータ一覧（空なら何もしない）
	 * @return  1文ずつの件数
	 * @throws SqlExecuteException  失敗したとき
	 */
	public List<Integer> executeBatch (String sql, List<List<Object>> paramsList) {

		List<Integer> res = new ArrayList<>();

		if (paramsList == null || paramsList.isEmpty()) {
			this.plannedTags = null;
			return res;
		}

		// SQLを実行する
		PreparedStatement st = null;
		try {

			getWriteConnection();

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql);

			int batchCount = 0;
			for (List<Object> params : paramsList) {

				setParameters(st, params);

				st.addBatch();

				batchCount++;

				if (batchCount == batchExecuteLimit) {
					batchCount = 0;
					for (int r : runBatch(st, sql)) {
						res.add(r);
					}
				}

			}

			if (batchCount > 0) {
				for (int r : runBatch(st, sql)) {
					res.add(r);
				}
			}

			// DB sticky
			DBSticky.updated();

			// SQL結果キャッシュを消す（要件 F-D-28）
			invalidateCache();

			return res;

		} catch (Exception ex) {

			throw fail("executeBatch", ex);

		} finally {

			IOUtil.close(st);
			closeAfterQuery();

		}

	}

	/**
	 * 積んだ分を流す
	 *
	 * @param st	ステートメント
	 * @param sql	SQL（記録用）
	 * @return	結果
	 */
	private static int[] runBatch (PreparedStatement st, String sql) throws SQLException {

		long start = System.nanoTime();

		int[] result = st.executeBatch();

		Context.recordSqlExecution(System.nanoTime() - start, sql);

		// JDBC の約束では -2（SUCCESS_NO_INFO）も成功。-3（EXECUTE_FAILED）は失敗
		for (int r : result) {
			if (r < 0 && r != Statement.SUCCESS_NO_INFO) {
				throw new SQLException("バッチの中に失敗した文があります（結果 " + r + "）");
			}
		}

		return result;

	}

	// endregion

	// region 登録バッチ実行（自動採番値取得）

	/**
	 * 登録バッチ実行（自動採番値取得）
	 *
	 * <p>
	 * <b>SQL が全部同じでなければならない</b>（要件 F-D-08）。
	 * 1本の {@code PreparedStatement} にパラメータだけを積み替えるためである。
	 * 違うものが混ざっていたら {@code DB_998} の例外。空の一覧は空リストを返す。
	 * </p>
	 *
	 * @param builderList   InsertBuilder
	 * @return  採番値
	 * @throws SqlExecuteException  失敗したとき
	 */
	public List<Long> insertBatch (List<InsertBuilder> builderList) {

		List<List<Object>> paramsList = new ArrayList<>();
		String sql = sameSql("insertBatch", builderList, paramsList);

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
	 * @param paramsList    パラメータ一覧（空なら何もしない）
	 * @return  採番値
	 * @throws SqlExecuteException  失敗したとき
	 */
	public List<Long> insertBatch (String sql, List<List<Object>> paramsList) {

		List<Long> res = new ArrayList<>();

		if (paramsList == null || paramsList.isEmpty()) {
			this.plannedTags = null;
			return res;
		}

		// SQLを実行する
		PreparedStatement st = null;
		ResultSet rs = null;
		try {

			getWriteConnection();

			// SQLステートメントを作成する
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

			int batchCount = 0;
			for (List<Object> params : paramsList) {

				setParameters(st, params);

				st.addBatch();

				batchCount++;

				if (batchCount == batchExecuteLimit) {

					batchCount = 0;

					runBatch(st, sql);

					rs = st.getGeneratedKeys();
					while (rs.next()) {
						res.add(dialect().generatedKey(rs));
					}
					IOUtil.close(rs);

				}

			}

			if (batchCount > 0) {

				runBatch(st, sql);

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

			throw fail("insertBatch", ex);

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
	 * JDBC の約束では、{@code -2}（{@code SUCCESS_NO_INFO}）も成功である。
	 * <b>空の一覧は成功</b>（2.0 は空の入力に空リストを返す）。{@code null} は失敗。
	 * </p>
	 *
	 * @param list	バッチ実行結果
	 * @return	成功の場合 = true
	 * @deprecated 2.0 で {@code executeBatch} / {@code insertBatch} は失敗を例外で知らせるので、見る必要が無くなった。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	public static boolean isBatchSuccess (List<? extends Number> list) {

		if (list == null) {
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
	 * @deprecated 2.0 で失敗は例外になった。2.x で消す（要件 D-193）
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	public static boolean isBatchSuccess (int...resArray) {

		if (resArray == null) {
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

	/*
	 * トランザクションを始める（Tx から呼ぶ）。
	 *
	 * <b>ここからコネクションを握り続ける。</b>closeAfterQuery() は
	 * トランザクション中は返さないので、実行の終わりに拾ってもらうよう登録する（要件 F-D-16）。
	 * 1文も流さずに終わる道があるので、ここで登録する。
	 *
	 * 1.x の公開メソッド beginTransaction / commit / commitEndTransaction / rollback /
	 * rollbackEndTransaction / endTransaction と DBTransaction は 2.0 で消した（要件 D-193）。
	 * 中身はここに残り、Tx だけが使う。
	 */
	void txBegin () throws SQLException {

		if (connection == null) {
			getWriteConnection();
		}

		connection.setAutoCommit(false);

		// ここから先のエラーを持ち越す（要件 D-155）
		clearErrorSinceTransaction();

		registerCloseTask();

	}

	// region トランザクション（2.0 の形。要件 D-191）

	/**
	 * トランザクションを始める
	 *
	 * <pre>
	 * try (Tx tx = db.begin()) {
	 *     db.update(...);
	 *     tx.commit();       // 確定して終わる。呼ばずに抜けたら巻き戻す
	 * }
	 * </pre>
	 *
	 * <p>
	 * <b>すでに始まっていれば、外に合流する</b>（{@link Tx} の「入れ子」）。
	 * 検査例外を投げない。
	 * </p>
	 *
	 * @return	トランザクション
	 * @throws TransactionException	始められなかった
	 * @since 1.5.0
	 */
	@CheckReturnValue
	public Tx begin () {

		if (isTransaction()) {
			return new Tx(this, true);
		}

		try {
			txBegin();
		} catch (Exception ex) {
			throw new TransactionException("トランザクションの開始に失敗しました: " + ex.getMessage(),
				new CodeException("DB_001", ex.getMessage(), ex));
		}

		return new Tx(this, false);

	}

	/**
	 * トランザクションの中で走らせる
	 *
	 * <pre>
	 * db.transaction(tx -&gt; {
	 *     db.update(...);
	 *     db.insert(...);
	 * });                    // 例外なく終われば確定、例外が出れば巻き戻して投げ直す
	 * </pre>
	 *
	 * <p>
	 * 中で {@code tx.rollback()} / {@code tx.commit()} を自分で呼んでもよい（そのときは何もしない）。
	 * すでに始まっていれば外に合流する——中で例外が出たら外は巻き戻し専用になる。
	 * </p>
	 *
	 * <p>
	 * 中身は検査例外を投げてもよい。<b>非検査例外はそのまま、検査例外は {@link TransactionException} に包んで</b>投げ直す
	 * （元の例外は {@code getCause().getCause()}）。
	 * </p>
	 *
	 * @param body	中身
	 * @throws TransactionException	確定できなかった（{@code DB_004} / {@code DB_005} など）、または中身の検査例外
	 * @since 1.5.0
	 */
	public void transaction (TxBody body) {

		transactionResult(tx -> {
			body.run(tx);
			return null;
		});

	}

	/**
	 * トランザクションの中で走らせ、値を返す
	 *
	 * <p>
	 * 名前を {@code transaction} と分けているのは、{@code tx -> db.update(...)} のような式のラムダが
	 * 「値を返す」とも「返さない」とも読めて、<b>同じ名前だと呼び分けられない</b>からである。
	 * </p>
	 *
	 * @param body	中身
	 * @param <T>	返す値の型
	 * @return	中身が返した値
	 * @throws TransactionException	確定できなかった、または中身の検査例外
	 * @since 1.5.0
	 */
	public <T> T transactionResult (TxFunction<T> body) {

		java.util.Objects.requireNonNull(body, "body");

		try (Tx tx = begin()) {

			T result;
			try {
				result = body.apply(tx);
			} catch (RuntimeException | Error ex) {
				throw ex;
			} catch (Exception ex) {
				throw new TransactionException("トランザクションの中で失敗しました: " + ex.getMessage(),
					new CodeException("DB_006", ex.getMessage(), ex));
			}

			if (!tx.isFinished()) {
				tx.commit();
			}

			return result;

		}

	}

	/**
	 * トランザクションの中身
	 *
	 * @since 1.5.0
	 */
	@FunctionalInterface
	public interface TxBody {

		/**
		 * 走らせる
		 *
		 * @param tx	トランザクション
		 * @throws Exception	中身の例外（巻き戻して投げ直す）
		 */
		void run (Tx tx) throws Exception;

	}

	/**
	 * 値を返すトランザクションの中身
	 *
	 * @param <T>	返す値の型
	 * @since 1.5.0
	 */
	@FunctionalInterface
	public interface TxFunction<T> {

		/**
		 * 走らせる
		 *
		 * @param tx	トランザクション
		 * @return	値
		 * @throws Exception	中身の例外（巻き戻して投げ直す）
		 */
		T apply (Tx tx) throws Exception;

	}

	// endregion

	/*
	 * 確定する。終わらせない（Tx#checkpoint と、txCommitEnd から呼ぶ）
	 */
	void txCommit () throws SQLException {

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

	/*
	 * 確定して終わる（Tx#commit から呼ぶ）
	 */
	void txCommitEnd () throws SQLException {

		try {
			txCommit();
		} finally {
			txEnd();
		}

	}

	/**
	 * エラーが出ていたらコミットさせない
	 *
	 * <p>
	 * <b>ここが無いと、部分的にコミットされる。</b>
	 * 失敗した文の例外を受け止めて（{@code catch (DuplicateKeyException e)} など）続けたとき、
	 * そのまま commit まで進むと、<b>失敗した文の前後だけが入る</b>（D-155）。
	 * </p>
	 *
	 * <p>
	 * <b>自分で巻き戻してから投げる。</b>終了処理に任せると、
	 * あちらは {@code setAutoCommit(true)} を呼ぶだけなので、
	 * <b>JDBC の決まりで、開いていたトランザクションがコミットされてしまう</b>——
	 * 拒んだはずのものが入る。
	 * </p>
	 *
	 * @throws CodeException	トランザクションを開けてから1度でもエラーが出ていた場合
	 */
	private void requireNoErrorSinceTransaction () throws SQLException {

		if (rollbackOnlyReason != null) {

			String reason = rollbackOnlyReason;

			if (isTransaction()) {
				connection.rollback();
			}
			discardCache();
			clearErrorSinceTransaction();

			throw new CodeException("DB_005"
				, """
				中のトランザクションが巻き戻しを求めたので、コミットしませんでした（全部巻き戻しました）。
				  理由: %s
				  中で rollback() した、または commit せずに閉じた Tx があります。
				  詳しく: %s
				""".formatted(reason, Docs.url("transaction")));

		}

		if (!errorSinceTransaction) {
			return;
		}

		CodeException cause = this.lastError;

		if (isTransaction()) {
			connection.rollback();
		}

		discardCache();

		throw new CodeException("DB_004"
			, """
			トランザクションの中で SQL が失敗しているので、コミットしませんでした（全部巻き戻しました）。
			  失敗: %s
			  例外を受け止めて続けたいなら、その Tx はいったん巻き戻して、新しい Tx で書き直してください。
			  詳しく: %s
			""".formatted(cause == null ? "（不明）" : cause.getMessage(), Docs.url("transaction")));

	}

	/*
	 * 巻き戻す。終わらせない（txRollbackEnd と close から呼ぶ）
	 */
	void txRollback () throws SQLException {

		if (connection == null) {
			return;
		}

		if (isTransaction()) {
			connection.rollback();
		}

		// 無かったことになるので、消す予定も捨てる（要件 F-D-28）
		discardCache();

		// エラーの持ち越しもここで畳む（要件 D-156）。巻き戻したので、もう持ち越すものは無い
		clearErrorSinceTransaction();

	}

	/*
	 * 巻き戻して終わる（Tx#rollback / Tx#close から呼ぶ）
	 */
	void txRollbackEnd () throws SQLException {

		try {
			txRollback();
		} finally {
			txEnd();
		}

	}

	/*
	 * 終わる（自動コミットに戻してコネクションを返す）
	 *
	 * <b>ここで起きたエラーだけを投げる</b>（要件 D-190）。
	 * 1.4 までは最後に直前の文のエラーをそのまま投げていたので、失敗した文のあと<b>巻き戻しに成功しても</b>、
	 * その文の古いエラーがここから出ていた。
	 */
	void txEnd () throws SQLException {

		if (connection == null) {
			return;
		}

		SQLException failure = null;

		try {
			if (isTransaction()) {
				connection.setAutoCommit(true);
			}
		} catch (SQLException ex) {
			failure = ex;
		} finally {
			/*
			 * コミットせずに終わった。消す予定は捨てる（要件 F-D-28）。
			 * コミット済みなら flushCache() が先に走って空になっている。
			 */
			discardCache();
			clearErrorSinceTransaction();
			close();
		}

		if (failure != null) {
			throw failure;
		}

	}

	// endregion

	// region Closeable

	/**
	 * 閉じる（コネクションをプールへ返す）
	 *
	 * <p>
	 * トランザクションの途中なら巻き戻す。<b>検査例外を投げない</b>（2.0。1.x は {@code IOException}）。
	 * 閉じるときの失敗はログに出す。
	 * </p>
	 */
	@Override
	public void close () {

		if (connection == null) {
			return;
		}

		try {
			if (isTransaction()) {
				txRollback();
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
				+ "ロールバックして閉じます: " + name + Docs.see("transaction");
		}

		return "閉じられていない DB が残っていました。閉じます: " + name
			+ "（selectListWithFetcher はカーソルなので、close() までコネクションを返しません）";

	}

	// endregion

}
