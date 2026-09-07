package io.jimble.core.lifecycle;

/**
 * 中断指示の通知（要件 F-B-06 / F-E-03）
 *
 * <p>
 * バッチも MQ も「外から止める」を同じ形で扱う。
 * <b>バッチが止まれば、その中で回っている MQ のワーカーも止まる。</b>
 * </p>
 *
 * <p>
 * 移送元ではこれが {@code lib.base.batch} に置かれており、
 * <b>MQ がバッチを見に行く形</b>になっていた。
 * jimble の依存は一方向（{@code batch / mq → db → util → core}）なので、
 * 共通の型は {@code core} に置く。
 * </p>
 */
public interface CancelOrderNotify {

	/**
	 * 中断が指示されているか
	 *
	 * @return	指示されている場合 = true
	 */
	boolean isCancelOrder ();

	/**
	 * 中断を指示する
	 */
	void doCancel ();

}
