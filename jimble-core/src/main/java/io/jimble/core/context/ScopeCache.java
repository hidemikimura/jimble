package io.jimble.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 実行スコープキャッシュ
 *
 * <p>
 * 1つの実行単位（Webリクエスト / バッチ実行 / MQメッセージ）の中で
 * 何度も使うデータを保持し、再取得を防ぐ。
 * </p>
 *
 * <p>
 * <b>結果が {@code null} でも「取得済み」として記憶する。</b>
 * これが無いと「存在しないデータ」を毎回取りに行ってしまう。
 * </p>
 *
 * <pre>
 * Data shop = ScopeCache.current().get("shop:" + shopId, () -&gt; ShopDomain.get(shopId));
 * </pre>
 *
 * <p>
 * {@code jooby_base} の {@code RequestScopeCache} 相当。ただし Web に限らないため名前を変えている。
 * </p>
 */
public final class ScopeCache {

	/* 現在のキャッシュ */
	static final ScopedValue<ScopeCache> SCOPED = ScopedValue.newInstance();

	/* null を記憶するための番人 */
	private static final Object NULL = new Object();

	/* 値 */
	private final Map<String, Object> values = new HashMap<>();

	/**
	 * コンストラクタ
	 */
	ScopeCache () {

	}

	/**
	 * 現在のキャッシュを取得する
	 *
	 * @return	キャッシュ
	 * @throws IllegalStateException	コンテキストのスコープ外で呼ばれた場合
	 */
	public static ScopeCache current () {

		if (!SCOPED.isBound()) {
			throw new IllegalStateException(
				"ScopeCache がバインドされていません。Context#run(...) の中で呼び出してください。");
		}

		return SCOPED.get();

	}

	/**
	 * 取得する（無ければ読み込む）
	 *
	 * <p>読み込み結果が null でも記憶する。</p>
	 *
	 * @param key		キー
	 * @param loader	読み込み処理
	 * @param <T>		値の型
	 * @return	値
	 */
	@SuppressWarnings("unchecked")
	public <T> T get (String key, Supplier<T> loader) {

		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(loader, "loader");

		Object cached = values.get(key);
		if (cached != null) {
			return cached == NULL ? null : (T) cached;
		}

		T value = loader.get();
		values.put(key, value == null ? NULL : value);
		return value;

	}

	/**
	 * 記憶しているか
	 *
	 * @param key	キー
	 * @return	記憶していれば true
	 */
	public boolean contains (String key) {

		return values.containsKey(key);

	}

	/**
	 * 破棄する
	 *
	 * <p>元データを更新した後に呼ぶ。</p>
	 *
	 * @param key	キー
	 */
	public void remove (String key) {

		values.remove(key);

	}

	/**
	 * すべて破棄する
	 */
	public void clear () {

		values.clear();

	}

	/**
	 * 記憶している件数
	 *
	 * @return	件数
	 */
	public int size () {

		return values.size();

	}

}
