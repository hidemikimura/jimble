package io.jimble.web.ws;

/**
 * WebSocket の出口（要件 F-W-22 / D-173）
 *
 * <p>
 * <b>helidon をここで止める。</b>HTTP 側は {@code RequestSource} / {@code ResponseSink} で
 * helidon を完全に隠しているのに、<b>WebSocket 側だけ穴が空いていた</b>——
 * {@link WsSession} が helidon のセッションをそのまま持ち、
 * {@code WsBridge} が helidon の {@code WsRouting.Builder} を公開パッケージから返していた。
 * </p>
 *
 * <p>
 * 実装は {@code io.jimble.web.internal.ws} にある。
 * <b>アプリが実装するものではない。</b>
 * </p>
 */
public interface WsSink {

	/**
	 * 文字を送る
	 *
	 * @param text	文字
	 * @return	送れた場合 = true
	 */
	boolean send (String text);

	/**
	 * 閉じる
	 *
	 * @param reason	理由
	 */
	void close (String reason);

}
