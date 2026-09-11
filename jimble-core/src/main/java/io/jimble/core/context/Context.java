package io.jimble.core.context;

import io.jimble.core.executor.Executor;
import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 実行コンテキスト
 *
 * <p>
 * 1つの実行単位につき1つ生成する。実行単位は次の3つ。
 * </p>
 * <ul>
 *     <li>Webリクエスト</li>
 *     <li>バッチ実行</li>
 *     <li>MQメッセージ処理</li>
 * </ul>
 *
 * <p>
 * このクラスは HTTP を知らない。Web固有の要素（リクエスト・レスポンス・セッション）は
 * jimble-web の WebContext が持つ。バッチ・MQ から使うために偽のHTTPコンテキストを
 * 作ってはならない。
 * </p>
 *
 * <p>
 * ライフサイクルは 生成 → {@link #run(Runnable)} → {@link #close()}。
 * try-with-resources で使うことでクローズ漏れを防ぐ。
 * </p>
 *
 * <pre>
 * try (SomeContext context = new SomeContext()) {
 *     context.run(() -&gt; {
 *         // ここでは Context.current() が使える
 *     });
 * }
 * </pre>
 *
 * @param <SELF>	自身の型
 */
public abstract class Context<SELF extends Context<SELF>> implements AutoCloseable {

	/* 現在のコンテキスト */
	private static final ScopedValue<Context<?>> CURRENT = ScopedValue.newInstance();

	/**
	 * 現在のコンテキストがバインドされているか
	 *
	 * <p>この判定自体は例外を投げない。</p>
	 *
	 * @return	バインドされていれば true
	 */
	public static boolean isBound () {

		return CURRENT.isBound();

	}

	/**
	 * 現在のコンテキストを取得する
	 *
	 * <p>
	 * スコープ外で呼ばれた場合は例外を投げる。<b>null は返さない。</b>
	 * </p>
	 *
	 * @return	コンテキスト
	 * @throws IllegalStateException	コンテキストのスコープ外で呼ばれた場合
	 */
	public static Context<?> current () {

		if (!CURRENT.isBound()) {
			throw new IllegalStateException(
				"Context がバインドされていません。Context#run(...) の中で呼び出してください。"
					+ " バッチ・MQ から使う場合は、その実行単位の Context を生成し run(...) の中で実行します。");
		}

		return CURRENT.get();

	}

	/**
	 * 現在のコンテキストを型を指定して取得する
	 *
	 * @param type	期待する型
	 * @param <C>	期待する型
	 * @return	コンテキスト
	 * @throws IllegalStateException	スコープ外、または型が異なる場合
	 */
	public static <C extends Context<C>> C current (Class<C> type) {

		Context<?> context = current();

		if (!type.isInstance(context)) {
			throw new IllegalStateException(
				"現在の Context は %s です。%s ではありません。"
					.formatted(context.getClass().getName(), type.getName()));
		}

		return type.cast(context);

	}


	/* 実行ID */
	private final String executionId;

	/*
	 * 実行の終わりに後始末するもの（要件 F-D-16）。
	 *
	 * core は DB を知らないので、後始末そのものは登録した側が持つ。
	 * ここは「実行が終わるときに呼ぶ」という約束だけを預かる。
	 */
	private final List<CloseTask> closeTasks = new ArrayList<>();

	/* 開始日時 */
	private final Instant startedAt;

	/* 属性 */
	private final Map<String, Object> attributes = new HashMap<>();

	/* Executorキュー */
	private final Deque<Executor<SELF>> executors = new ArrayDeque<>();

	/* 実行スコープキャッシュ */
	private final ScopeCache scopeCache = new ScopeCache();

	/* クローズ済み判定 */
	private boolean closed = false;

	/**
	 * コンストラクタ
	 */
	protected Context () {

		this(ExecutionIds.generate());

	}

	/**
	 * コンストラクタ
	 *
	 * @param executionId	実行ID
	 */
	protected Context (String executionId) {

		this.executionId = Objects.requireNonNull(executionId, "executionId");
		this.startedAt = Instant.now();

	}

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * <b>実行IDを親から引き継ぐ。</b>
	 * 1本の実行の中から別の実行単位を起こしたとき
	 * （Web の内部呼び出し。要件 F-W-27）に使う。
	 * ログを実行IDで辿ると、内側で起きたことも一緒に出る。
	 * </p>
	 *
	 * @param parent	引き継ぐ元。null なら新しく採番する
	 */
	protected Context (Context<?> parent) {

		this(parent == null ? ExecutionIds.generate() : parent.executionId());

	}

	/**
	 * 自身を返す
	 *
	 * <p>実装は {@code return this;} だけでよい。</p>
	 *
	 * @return	自身
	 */
	protected abstract SELF self ();


	// region 基本情報

	/**
	 * 実行ID
	 *
	 * <p>ログの全行に含める。1実行単位を通して不変。</p>
	 *
	 * @return	実行ID
	 */
	public final String executionId () {

		return executionId;

	}

	/**
	 * 開始日時
	 *
	 * @return	開始日時
	 */
	public final Instant startedAt () {

		return startedAt;

	}

	/**
	 * 開始からの経過時間
	 *
	 * @return	経過時間
	 */
	public final Duration elapsed () {

		return Duration.between(startedAt, Instant.now());

	}

	// endregion


	// region 属性

	/**
	 * 属性を取得する
	 *
	 * @param key	キー
	 * @param <T>	値の型
	 * @return	値。無ければ null
	 */
	@SuppressWarnings("unchecked")
	public final <T> T attribute (String key) {

		return (T) attributes.get(key);

	}

	/**
	 * 属性を設定する
	 *
	 * @param key	キー
	 * @param value	値
	 * @return	自身
	 */
	public final SELF attribute (String key, Object value) {

		ensureOpen();
		attributes.put(key, value);
		return self();

	}

	// endregion


	// region Executorキュー

	/**
	 * Executorを積む
	 *
	 * <p>
	 * キューは実行中にも積める。Executor の中から次の Executor を追加してよい。
	 * </p>
	 *
	 * @param executor	Executor
	 * @return	自身
	 */
	public final SELF addExecutor (Executor<SELF> executor) {

		ensureOpen();
		Objects.requireNonNull(executor, "executor");
		executors.addLast(executor);
		return self();

	}

	/**
	 * Executorを1つ取り出す
	 *
	 * @return	Executor。無ければ null
	 */
	public final Executor<SELF> pollExecutor () {

		return executors.pollFirst();

	}

	/**
	 * 残りのExecutorを破棄する
	 *
	 * <p>キャンセル時に呼ぶ。</p>
	 */
	public final void clearExecutors () {

		executors.clear();

	}

	/**
	 * 残っているExecutorの数
	 *
	 * @return	残数
	 */
	public final int pendingExecutorCount () {

		return executors.size();

	}

	/**
	 * 実行スコープキャッシュ
	 *
	 * <p>
	 * {@link #run(Runnable)} の中からは {@link ScopeCache#current()} でも取れる。
	 * </p>
	 *
	 * @return	キャッシュ
	 */
	public final ScopeCache scopeCache () {

		return scopeCache;

	}

	// endregion


	// region SQL実行の集計

	/* SQL実行回数 */
	private long sqlExecuteCount = 0;

	/* SQL実行時間（ナノ秒） */
	private long sqlExecuteNanos = 0;

	/** スパンの名前に使う操作の長さの上限 */
	private static final int SQL_OPERATION_MAX = 16;

	/** スパンの名前に使う表の名前の長さの上限 */
	private static final int SQL_TABLE_MAX = 64;

	/** 表の名前が続く語 */
	private static final String[] SQL_TABLE_KEYWORDS = {" FROM ", " INTO ", "UPDATE ", " TABLE "};

	/**
	 * SQL実行を記録する
	 *
	 * <p>
	 * コンテキストのスコープ外で呼ばれた場合は何もしない（バッチの初期化中など）。
	 * アクセスログに出す実行回数・実行時間の元になる（要件 F-D-17 / NF-O-02）。
	 * </p>
	 *
	 * @param nanos	実行時間（ナノ秒）
	 */
	public static void recordSqlExecution (long nanos) {

		recordSqlExecution(nanos, null);

	}

	/**
	 * SQL実行を記録する
	 *
	 * <p>
	 * コンテキストのスコープ外で呼ばれた場合は、回数と時間は数えない（バッチの初期化中など）。
	 * <b>トレース（要件 NF-O-05）はスコープ外でも残す。</b>
	 * </p>
	 *
	 * <p>
	 * <b>スパンは「終わってから」作る。</b>SQL を実行しているところは
	 * {@code DB} の中に 10 か所あり、そのすべてを {@code try} で囲むと
	 * <b>いちばん熱い経路に手を入れる</b>ことになる。実行時間はすでに測ってあるので、
	 * その分だけさかのぼった区間として1つ残せば同じ絵が描ける。
	 * </p>
	 *
	 * @param nanos	実行時間（ナノ秒）
	 * @param sql	実行した SQL。null なら残さない
	 */
	public static void recordSqlExecution (long nanos, String sql) {

		if (CURRENT.isBound()) {

			Context<?> context = CURRENT.get();
			synchronized (context) {
				context.sqlExecuteCount++;
				context.sqlExecuteNanos += nanos;
			}

		}

		if (sql == null || !Tracing.enabled()) {
			return;
		}

		try (Span span = Tracing.past(sqlSpanName(sql), SpanKind.client, nanos)) {
			span.attribute("db.statement", sql);
		}

	}

	/**
	 * SQL のスパンの名前
	 *
	 * <p>
	 * <b>SQL 文をそのまま名前にしない。</b>{@code WHERE id = 1} と {@code WHERE id = 2} が
	 * 別の名前になると、トレースを見る道具の側で<b>種類が無限に増える</b>
	 * （メトリクスの名前で同じ問題を扱った。D-124）。
	 * 「操作＋表」までにして、全文は {@code db.statement} に入れる。
	 * </p>
	 *
	 * @param sql SQL
	 * @return 名前（{@code SELECT post} など）
	 */
	private static String sqlSpanName (String sql) {

		String trimmed = sql.stripLeading();

		int space = indexOfWhitespace(trimmed, 0);
		String operation = space < 0 ? trimmed : trimmed.substring(0, space);

		if (operation.length() > SQL_OPERATION_MAX) {
			operation = operation.substring(0, SQL_OPERATION_MAX);
		}

		String table = sqlTable(trimmed);

		return table == null ? operation : operation + " " + table;

	}

	/**
	 * SQL から表の名前を拾う
	 *
	 * <p>
	 * {@code FROM} / {@code INTO} / {@code UPDATE} の次の語を見るだけである。
	 * <b>当たらないことがある</b>（副問い合わせ、結合、方言）が、
	 * そのときは操作だけの名前になる。<b>名前を良くするために SQL を解析しない</b>——
	 * 解析器を1つ抱えるほどの値打ちは無い。
	 * </p>
	 *
	 * @param sql ならした SQL
	 * @return 表の名前。分からなければ null
	 */
	private static String sqlTable (String sql) {

		String upper = sql.toUpperCase(java.util.Locale.ROOT);

		for (String keyword : SQL_TABLE_KEYWORDS) {

			int at = upper.indexOf(keyword);

			if (at < 0) {
				continue;
			}

			int from = at + keyword.length();
			int to = indexOfWhitespaceOrPunctuation(sql, from);
			String table = (to < 0 ? sql.substring(from) : sql.substring(from, to)).trim();

			// 引用符（`post` / "post"）を外す
			table = table.replace("`", "").replace("\"", "");

			if (!table.isEmpty() && table.length() <= SQL_TABLE_MAX) {
				return table;
			}

		}

		return null;

	}

	/**
	 * 空白の位置
	 *
	 * @param value	文字列
	 * @param from	探し始める位置
	 * @return 位置。無ければ -1
	 */
	private static int indexOfWhitespace (String value, int from) {

		for (int i = from; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i))) {
				return i;
			}
		}

		return -1;

	}

	/**
	 * 空白か区切り記号の位置
	 *
	 * @param value	文字列
	 * @param from	探し始める位置
	 * @return 位置。無ければ -1
	 */
	private static int indexOfWhitespaceOrPunctuation (String value, int from) {

		for (int i = from; i < value.length(); i++) {

			char c = value.charAt(i);

			if (Character.isWhitespace(c) || c == '(' || c == ')' || c == ',' || c == ';') {
				return i;
			}

		}

		return -1;

	}

	/**
	 * SQL実行回数
	 *
	 * @return	実行回数
	 */
	public final long sqlExecuteCount () {

		synchronized (this) {
			return sqlExecuteCount;
		}

	}

	/**
	 * SQL実行時間
	 *
	 * @return	実行時間
	 */
	public final Duration sqlExecuteTime () {

		synchronized (this) {
			return Duration.ofNanos(sqlExecuteNanos);
		}

	}

	// endregion


	// region スコープ

	/**
	 * ScopedValueをバインドして処理を実行する
	 *
	 * @param body	処理
	 */
	public final void run (Runnable body) {

		ensureOpen();
		Objects.requireNonNull(body, "body");
		scopedValues().run(body);

	}

	/**
	 * バインドするScopedValueを組み立てる
	 *
	 * <p>
	 * サブクラスは {@code super.scopedValues().where(KEY, value)} の形で追加する。
	 * 操作情報・stickyコネクションは後続のマイルストーンでここに乗る。
	 * </p>
	 *
	 * @return	Carrier
	 */
	protected ScopedValue.Carrier scopedValues () {

		return ScopedValue
			.where(CURRENT, this)
			.where(ScopeCache.SCOPED, scopeCache);

	}

	// endregion


	// region 実行の終わりの後始末（要件 F-D-16）

	/**
	 * 実行の終わりに呼ぶものを登録する
	 *
	 * <p>
	 * <b>登録した順の逆から呼ぶ</b>（あとから始めたものを先に畳む）。
	 * 1つが例外を投げても、残りは呼ぶ。
	 * </p>
	 *
	 * <p>
	 * {@code jimble-core} は DB も HTTP も知らないので、
	 * 「未コミットのトランザクションを戻す」のような後始末は
	 * <b>それを知っている側（{@code jimble-db}）が登録する。</b>
	 * static な登録表を持つと誰が積んだのか辿れなくなるので、
	 * <b>Context のインスタンスに持たせる。</b>
	 * </p>
	 *
	 * @param task	後始末
	 */
	public final void onClose (CloseTask task) {

		Objects.requireNonNull(task, "task");

		synchronized (closeTasks) {
			closeTasks.add(task);
		}

	}

	/**
	 * 登録を外す
	 *
	 * <p>正しく畳まれたものは、実行の終わりに呼ばれる必要がない。</p>
	 *
	 * @param task	後始末
	 */
	public final void removeCloseTask (CloseTask task) {

		synchronized (closeTasks) {
			closeTasks.remove(task);
		}

	}

	/**
	 * 積まれている後始末の数
	 *
	 * <p>
	 * <b>正しく畳まれていれば 0 である。</b>
	 * 後始末は「畳み忘れたときだけ呼ばれるもの」なので、
	 * ここが実行の途中で増え続けるなら、登録した側が外し忘れている。
	 * </p>
	 *
	 * @return	数
	 */
	public final int pendingCloseTaskCount () {

		synchronized (closeTasks) {
			return closeTasks.size();
		}

	}

	/**
	 * 後始末を実行する
	 */
	private void runCloseTasks () {

		List<CloseTask> tasks;

		synchronized (closeTasks) {
			tasks = new ArrayList<>(closeTasks);
			closeTasks.clear();
		}

		// あとから始めたものを先に畳む
		for (int i = tasks.size() - 1; i >= 0; i--) {

			try {
				tasks.get(i).close();
			} catch (Throwable cause) {
				/*
				 * 1つ失敗しても残りは畳む。
				 * ここで抜けると、後ろに積んだものが漏れる。
				 */
				failedCloseTask(cause);
			}

		}

	}

	/**
	 * 後始末が失敗した（継承用）
	 *
	 * <p>core はログを持たないので、出したい側が上書きする。</p>
	 *
	 * @param cause	原因
	 */
	protected void failedCloseTask (Throwable cause) {

	}

	/**
	 * 実行の終わりに呼ばれるもの
	 */
	@FunctionalInterface
	public interface CloseTask {

		/**
		 * 畳む
		 *
		 * @throws Exception	畳めなかった場合
		 */
		void close () throws Exception;

	}

	// endregion


	// region ライフサイクル

	/**
	 * クローズする
	 *
	 * <p>
	 * 複数回呼ばれても後始末は1回だけ行う。
	 * {@link #doClose()} は ScopedValue をバインドした状態で実行する。
	 * </p>
	 */
	@Override
	public final void close () {

		if (closed) {
			return;
		}
		closed = true;

		try {
			// doClose の中からも Context.current() が使えるようにスコープを張る。
			// アクセスログが実行IDと SQL 集計を読むため
			scopedValues().run(() -> {
				/*
				 * 畳み忘れたものを先に畳む（要件 F-D-16）。
				 * アクセスログより前に置く。ログを出したあとで
				 * ロールバックのエラーが出ると、順番が読めなくなる。
				 */
				runCloseTasks();
				doClose();
			});
		} finally {
			executors.clear();
			scopeCache.clear();
		}

	}

	/**
	 * 後始末（継承用）
	 *
	 * <p>
	 * 未コミットトランザクションのロールバック、DB接続の返却、アクセスログの出力など。
	 * 例外を投げてもクローズ済みの状態は変わらない。
	 * </p>
	 */
	protected void doClose () {

	}

	/**
	 * クローズ済みか
	 *
	 * @return	クローズ済みなら true
	 */
	public final boolean isClosed () {

		return closed;

	}

	/**
	 * クローズされていないことを確認する
	 */
	private void ensureOpen () {

		if (closed) {
			throw new IllegalStateException("Context はすでにクローズされています。executionId=" + executionId);
		}

	}

	// endregion


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "%s(%s)".formatted(getClass().getSimpleName(), executionId);

	}

}
