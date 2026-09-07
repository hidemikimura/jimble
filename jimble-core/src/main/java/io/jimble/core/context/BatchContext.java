package io.jimble.core.context;

import java.util.Objects;

/**
 * バッチ実行のコンテキスト
 *
 * <p>
 * <b>HTTP の偽物を作らずに成立する。</b>
 * {@code jooby_base} はバッチから {@code AppContext} を作るために
 * jooby の {@code Context} を全実装した偽物（607行）を用意していたが、
 * jimble ではその必要がない（要件 F-C-12）。
 * </p>
 *
 * <pre>
 * try (BatchContext context = new BatchContext("RSSフィード取得")) {
 *     context.run(() -&gt; {
 *         // Web の UseCase と同じ Domain 層を呼べる
 *     });
 * }
 * </pre>
 */
public final class BatchContext extends Context<BatchContext> {

	/* バッチ名 */
	private final String batchName;

	/* 中断指示 */
	private volatile boolean cancelOrdered = false;

	/**
	 * コンストラクタ
	 *
	 * @param batchName	バッチ名
	 */
	public BatchContext (String batchName) {

		this.batchName = Objects.requireNonNull(batchName, "batchName");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BatchContext self () {

		return this;

	}

	/**
	 * バッチ名
	 *
	 * @return	バッチ名
	 */
	public String batchName () {

		return batchName;

	}

	/**
	 * 中断が指示されているか
	 *
	 * <p>
	 * 長時間バッチはループの中でこれを定期的に確認し、安全に止める（要件 F-B-06）。
	 * </p>
	 *
	 * @return	指示されていれば true
	 */
	public boolean isCancelOrdered () {

		return cancelOrdered;

	}

	/**
	 * 中断を指示する
	 *
	 * <p>別スレッドから呼ばれる想定。</p>
	 */
	public void orderCancel () {

		this.cancelOrdered = true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "BatchContext(%s, %s)".formatted(batchName, executionId());

	}

}
