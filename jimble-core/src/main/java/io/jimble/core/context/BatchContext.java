package io.jimble.core.context;

import io.jimble.core.lifecycle.CancelOrderNotify;

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

	/* 中断指示（このコンテキストに直に来たぶん） */
	private volatile boolean cancelOrdered = false;

	/* 中断の指示元。枠組みが繋ぐ */
	private volatile CancelOrderNotify cancelNotify = null;

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
	 * 中断の指示元を繋ぐ（枠組みが呼ぶ）
	 *
	 * <p>
	 * <b>アプリからは呼ばない。</b>{@code AbstractBatch} が自分を渡す。
	 * </p>
	 *
	 * <p>
	 * <b>これが無いあいだ、{@link #isCancelOrdered()} は永久に false を返していた</b>（D-155）。
	 * 中断の判定は {@code AbstractBatch#isCancelOrder()}（履歴の {@code cancel_status} を
	 * 定期的に見る）にあり、こちらには何も繋がっていなかった——
	 * <b>この Javadoc のとおりにループを書いたバッチは、中断ボタンを押しても止まらなかった</b>。
	 * </p>
	 *
	 * @param notify	指示元。{@code null} で外す
	 */
	public void cancelNotify (CancelOrderNotify notify) {

		this.cancelNotify = notify;

	}

	/**
	 * 中断が指示されているか
	 *
	 * <p>
	 * 長時間バッチはループの中でこれを定期的に確認し、安全に止める（要件 F-B-06）。
	 * </p>
	 *
	 * <pre>
	 * for (Data row : rows) {
	 *     if (context.isCancelOrdered()) { break; }   // ここで抜ける
	 *     ...
	 * }
	 * </pre>
	 *
	 * <p>
	 * <b>DB を引くことがある。</b>指示元（{@code AbstractBatch}）は
	 * {@code batch.cancel_check_seconds} に1回だけ履歴を見にいくので、
	 * <b>ループの中で毎回呼んでよい</b>（間隔の内側では前回の答えを返す）。
	 * </p>
	 *
	 * @return	指示されていれば true
	 */
	public boolean isCancelOrdered () {

		if (cancelOrdered) {
			return true;
		}

		CancelOrderNotify notify = cancelNotify;

		return notify != null && notify.isCancelOrder();

	}

	/**
	 * 中断を指示する
	 *
	 * <p>別スレッドから呼ばれる想定。</p>
	 *
	 * <p>
	 * <b>指示元にも伝える。</b>伝えないと、
	 * バッチはループを抜けても<b>「完了」として履歴に残る</b>——
	 * 止めたのに止めた形跡が残らない。
	 * </p>
	 */
	public void orderCancel () {

		this.cancelOrdered = true;

		CancelOrderNotify notify = cancelNotify;

		if (notify != null) {
			notify.doCancel();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "BatchContext(%s, %s)".formatted(batchName, executionId());

	}

}
