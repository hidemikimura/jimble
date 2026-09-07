package io.jimble.core.executor;

import io.jimble.core.context.Context;

/**
 * 処理実行の基底クラス
 *
 * <p>
 * キャンセル状態の保持だけを行う。実処理は {@link #execute(Context)} に書く。
 * </p>
 *
 * @param <C>	コンテキストの型
 */
public abstract class AbstractExecutor<C extends Context<C>> implements Executor<C> {

	/* キャンセル判定 */
	private boolean canceled = false;

	/**
	 * キャンセルする
	 *
	 * <p>
	 * 呼んだ時点では処理は止まらない。{@link #execute(Context)} から戻った後に、
	 * 残りのExecutorが破棄され {@link #onCancel(Context)} が呼ばれる。
	 * </p>
	 */
	protected final void cancel () {

		this.canceled = true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isCanceled () {

		return canceled;

	}

}
