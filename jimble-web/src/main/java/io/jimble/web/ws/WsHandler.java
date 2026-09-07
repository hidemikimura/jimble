package io.jimble.web.ws;

import io.jimble.web.ws.context.WsContext;

/**
 * WebSocket の処理（要件 F-W-22）
 *
 * <pre>
 * public class ChatHandler implements WsHandler {
 *
 *     &#64;Override
 *     public void onMessage (WsContext context, String message) {
 *         context.session().send("echo: " + message);
 *     }
 *
 * }
 * </pre>
 *
 * <pre>
 * // ルート定義
 * ws("/chat", ChatHandler::new);
 * </pre>
 *
 * <h2>Context はメッセージごとに1つ</h2>
 *
 * <p>
 * <b>接続ごとではない</b>（D-56）。MQ のメッセージ1件と同じ扱いである。
 * 接続ごとにすると、何時間も生きる Context が
 * DB 接続やトランザクションを握ったままになる。
 * </p>
 *
 * <p>
 * 接続に紐づけたいものは {@link WsSession#attributes()} に置く。
 * </p>
 *
 * <h2>全員に配信したい場合</h2>
 *
 * <p>
 * <b>jimble は接続の一覧を持たない。</b>持った瞬間に、
 * 複数インスタンス構成で「自分のインスタンスに繋いでいる人にしか届かない」ものになる。
 * それは配信の仕組みとしては間違っているのに、
 * <b>1台で動かしているうちは正しく見えてしまう。</b>
 * </p>
 *
 * <p>
 * 配信が要るなら、MQ（要件 F-M-03）か Redis の Pub/Sub をアプリが挟むこと。
 * </p>
 */
public interface WsHandler {

	/**
	 * 繋がった
	 *
	 * @param context	コンテキスト
	 * @throws Exception	失敗した場合
	 */
	default void onOpen (WsContext context) throws Exception {}

	/**
	 * 文字が届いた
	 *
	 * @param context	コンテキスト
	 * @param message	中身
	 * @throws Exception	失敗した場合
	 */
	default void onMessage (WsContext context, String message) throws Exception {}

	/**
	 * バイト列が届いた
	 *
	 * @param context	コンテキスト
	 * @param message	中身
	 * @throws Exception	失敗した場合
	 */
	default void onBinary (WsContext context, byte[] message) throws Exception {}

	/**
	 * 切れた
	 *
	 * @param context	コンテキスト
	 * @param code		終了コード
	 * @param reason	理由
	 * @throws Exception	失敗した場合
	 */
	default void onClose (WsContext context, int code, String reason) throws Exception {}

	/**
	 * 例外が出た
	 *
	 * <p>既定では何もしない（フレームワークがログに出す）。</p>
	 *
	 * @param context	コンテキスト
	 * @param cause		原因
	 */
	default void onError (WsContext context, Throwable cause) {}

	/**
	 * アップグレードを受けるか（要件 F-W-22）
	 *
	 * <p>
	 * <b>認証はここで通す。</b>Cookie が読めるのはこの時点である。
	 * false を返すと <b>403 で断る</b>（繋がってから切るのではない）。
	 * </p>
	 *
	 * @param session	接続
	 * @return	受ける場合 = true
	 */
	default boolean onUpgrade (WsSession session) {

		return true;

	}

}
