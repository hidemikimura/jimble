package io.jimble.web.router;

import io.jimble.core.executor.Executor;
import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * ルート
 *
 * <p>
 * 処理の実体は次のどちらか一方。
 * </p>
 * <ul>
 *     <li>{@link Handler} — ラムダで直接処理する</li>
 *     <li>{@link Executor} の {@link Supplier} 列 — 登録順に実行する（通常こちら）</li>
 * </ul>
 */
public final class Route {

	/* メソッド */
	private final String method;

	/* 登録パターン */
	private final String pattern;

	/* ハンドラ */
	private final Handler handler;

	/* Executor生成 */
	private final List<Supplier<Executor<WebContext>>> executorSuppliers;

	/* 属性 */
	private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();

	/* タグ */
	private final List<String> tags = new ArrayList<>();

	/* 書かれた場所（フックのスコープ。要件 D-69） */
	private Scope scope;

	/* 確定した before（外側 → 内側） */
	private List<Handler> beforeHooks = List.of();

	/* 確定した after（内側 → 外側） */
	private List<Handler> afterHooks = List.of();

	/* 確定した error（内側 → 外側） */
	private List<ErrorHandler> errorHooks = List.of();

	/*
	 * 表に出すときの名前（要件 D-167）。
	 *
	 * <b>作るのは1回だけ。</b>メトリクスの名前もトレースの区間名も
	 * 「メソッド + パターン」で、<b>登録した時点で決まっている</b>——
	 * リクエストごとに組み立てると、それだけで<b>数える処理の全部より重い</b>
	 * （実測 1039 byte / 644ns）。
	 */
	private final String label;

	/* メトリクスの名前（要件 D-167） */
	private final String metricName;

	/**
	 * コンストラクタ
	 *
	 * @param method				メソッド
	 * @param pattern				登録パターン
	 * @param handler				ハンドラ
	 * @param executorSuppliers		Executor生成
	 */
	Route (String method, String pattern, Handler handler, List<Supplier<Executor<WebContext>>> executorSuppliers) {

		this.method = Objects.requireNonNull(method, "method");
		this.pattern = Objects.requireNonNull(pattern, "pattern");
		this.handler = handler;
		this.executorSuppliers = executorSuppliers == null ? List.of() : List.copyOf(executorSuppliers);

		this.label = this.method + " " + this.pattern;
		this.metricName = "http." + this.label;

	}

	/**
	 * 表に出すときの名前（要件 D-167）
	 *
	 * <p>
	 * {@code "GET /posts/{id}"}。<b>トレースの区間名</b>とログに使う。
	 * </p>
	 *
	 * <p>
	 * <b>生のパスは使わない。</b>{@code /posts/1} {@code /posts/2} … と
	 * <b>種類が無限に増える</b>ので、見る道具の側が壊れる（要件 D-124）。
	 * </p>
	 *
	 * <p><b>登録したときに1回だけ作る。</b>リクエストごとに組み立てない。</p>
	 *
	 * @return	名前
	 */
	public String label () {

		return label;

	}

	/**
	 * メトリクスの名前（要件 D-167）
	 *
	 * <p>{@code "http.GET /posts/{id}"}。<b>登録したときに1回だけ作る。</b></p>
	 *
	 * @return	名前
	 */
	public String metricName () {

		return metricName;

	}

	/**
	 * メソッド
	 *
	 * @return	メソッド
	 */
	public String method () {

		return method;

	}

	/**
	 * 登録パターン
	 *
	 * <p>絶対パスではない。絶対パスは {@link Router#routes()} が返す。</p>
	 *
	 * @return	登録パターン
	 */
	public String pattern () {

		return pattern;

	}

	/**
	 * ハンドラ
	 *
	 * @return	ハンドラ。Executor形式なら null
	 */
	public Handler handler () {

		return handler;

	}

	/**
	 * Executor生成
	 *
	 * @return	Executor生成。ハンドラ形式なら空
	 */
	public List<Supplier<Executor<WebContext>>> executorSuppliers () {

		return executorSuppliers;

	}

	/**
	 * 属性を設定する
	 *
	 * @param key	キー
	 * @param value	値
	 * @param <T>	値の型
	 * @return	自身
	 */
	public <T> Route attribute (AttributeKey<T> key, T value) {

		Objects.requireNonNull(key, "key");
		attributes.put(key, value);
		return this;

	}

	/**
	 * 属性を取得する
	 *
	 * @param key	キー
	 * @param <T>	値の型
	 * @return	値。未設定ならキーの既定値
	 */
	@SuppressWarnings("unchecked")
	public <T> T attribute (AttributeKey<T> key) {

		Objects.requireNonNull(key, "key");

		if (!attributes.containsKey(key)) {
			return key.defaultValue();
		}

		return (T) attributes.get(key);

	}

	/**
	 * このルートに付いている属性のキー（要件 D-157）
	 *
	 * @return	キー
	 */
	java.util.Collection<AttributeKey<?>> attributeKeys () {

		return attributes.keySet();

	}

	/**
	 * タグを追加する
	 *
	 * @param values	タグ
	 * @return	自身
	 */
	public Route tag (String... values) {

		tags.addAll(List.of(values));
		return this;

	}

	/**
	 * タグ
	 *
	 * @return	タグ
	 */
	public List<String> tags () {

		return List.copyOf(tags);

	}


	// region フック（要件 D-69）

	/**
	 * 書かれた場所を記録する
	 *
	 * @param scope	スコープ
	 */
	void scope (Scope scope) {

		this.scope = scope;

	}

	/**
	 * フックを確定する
	 *
	 * <p>
	 * <b>起動時に1度だけ呼ぶ。</b>リクエストのたびに親を辿って集め直さない。
	 * </p>
	 */
	void seal () {

		if (scope == null) {
			return;
		}

		beforeHooks = scope.resolveBefores();
		afterHooks = scope.resolveAfters();
		errorHooks = scope.resolveErrors();

		/*
		 * ブロックに書かれた属性を引き継ぐ（要件 F-R-26）。
		 *
		 * <b>ルートが自分で持っていればそのまま</b>——上書きしない。
		 * 書いていなければ、書かれたブロックのものを引き継ぐ（内側が勝つ）。
		 *
		 * <b>起動時に1回だけ配る</b>ので、リクエストのたびに親を辿らない（要件 F-R-10）。
		 * 流量制限（F-R-22）も、いまはこの仕組みの上に乗っているだけである。
		 */
		for (AttributeKey<?> key : scope.resolveAttributeKeys()) {

			if (attributes.containsKey(key)) {
				continue;
			}

			Object resolved = scope.resolveAttribute(key);

			if (resolved != null) {
				attributes.put(key, resolved);
			}

		}

	}

	/**
	 * before（外側 → 内側）
	 *
	 * @return	before
	 */
	List<Handler> beforeHooks () {

		return beforeHooks;

	}

	/**
	 * after（内側 → 外側）
	 *
	 * @return	after
	 */
	List<Handler> afterHooks () {

		return afterHooks;

	}

	/**
	 * error（内側 → 外側）
	 *
	 * @return	error
	 */
	List<ErrorHandler> errorHooks () {

		return errorHooks;

	}

	// endregion


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return method + " " + pattern;

	}

}
