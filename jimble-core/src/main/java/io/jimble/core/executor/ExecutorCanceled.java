package io.jimble.core.executor;

/**
 * {@link AbstractExecutor#cancel()} で処理を抜ける（制御用。要件 D-196）
 *
 * <p>
 * 枠組みが受け止めて、残りの Executor を破棄し {@link Executor#onCancel} を呼ぶ——{@code HttpException} と同じ作り。
 * <b>アプリで受け止めないこと</b>（{@code catch (Exception e)} で握りつぶしても、キャンセルの印は立ったまま残るので、
 * {@code execute} から戻ったところで打ち切られる）。スタックトレースは取らない。
 * </p>
 *
 * @since 2.0.0
 */
public final class ExecutorCanceled extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/* 抜けた Executor */
	private final transient Executor<?> executor;

	/**
	 * コンストラクタ
	 *
	 * @param executor	抜けた Executor
	 */
	ExecutorCanceled (Executor<?> executor) {

		super("Executor がキャンセルしました: " + executor.getClass().getName(), null, false, false);
		this.executor = executor;

	}

	/**
	 * 抜けた Executor
	 *
	 * @return	Executor
	 */
	public Executor<?> executor () {

		return executor;

	}

}
