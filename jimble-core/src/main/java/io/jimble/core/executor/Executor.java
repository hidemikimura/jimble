package io.jimble.core.executor;

import io.jimble.core.context.Context;

/**
 * 処理実行
 *
 * <p>
 * Context を受け取って処理を行う実行単位。UseCase とバリデーションがこれを継承する。
 * Context のキューに積まれ、積まれた順に実行される。
 * </p>
 *
 * <p>
 * キャンセルされると、<b>残りのExecutorは破棄され</b> {@link #onCancel(Context)} が呼ばれる。
 * </p>
 *
 * @param <C>	コンテキストの型
 */
public interface Executor<C extends Context<C>> {

	/**
	 * 実行する
	 *
	 * @param context	コンテキスト
	 * @throws Exception	処理中の例外
	 */
	void execute (C context) throws Exception;

	/**
	 * キャンセルされたか
	 *
	 * @return	キャンセルされていれば true
	 */
	default boolean isCanceled () {

		return false;

	}

	/**
	 * キャンセル時の処理
	 *
	 * <p>
	 * 残りのExecutorが破棄された後に呼ばれる。エラーレスポンスの組み立てなどを行う。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @throws Exception	処理中の例外
	 */
	default void onCancel (C context) throws Exception {

	}

}
