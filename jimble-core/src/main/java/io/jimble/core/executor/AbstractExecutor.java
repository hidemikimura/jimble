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
	 * キャンセルする（ここで抜ける）
	 *
	 * <p>
	 * <b>呼んだところで {@link #execute(Context)} を抜ける</b>（2.0。要件 D-196）。
	 * 残りの Executor が破棄され {@link #onCancel(Context)} が呼ばれる。
	 * 1.x は印を立てるだけで、<b>あとの行もそのまま走っていた</b>——{@code cancel(); db.insert(...);} の insert が入った。
	 * </p>
	 *
	 * @throws ExecutorCanceled	いつも（枠組みが受け止める）
	 */
	protected final void cancel () {

		this.canceled = true;
		throw new ExecutorCanceled(this);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isCanceled () {

		return canceled;

	}

}
