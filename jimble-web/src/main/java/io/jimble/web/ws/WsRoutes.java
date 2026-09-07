package io.jimble.web.ws;

import io.jimble.web.router.AttributeKey;

import java.util.function.Supplier;

/**
 * WebSocket のルートの持ち方（要件 F-W-22 / F-R-12 / F-R-13）
 *
 * <p>
 * <b>helidon では WebSocket は別のルーティングになる。</b>
 * アップグレードは {@code WsUpgradeProvider} が HTTP のルーティングより前で横取りし、
 * 登録先も {@code WsRouting}（{@code WebServerConfig.addNamedRouting}）である。
 * つまり <b>{@code routing.any()} には来ない。</b>
 * </p>
 *
 * <p>
 * そのまま helidon に直接登録すると、<b>ルートの置き場所が2つに分かれる。</b>
 * 起動時の一覧（要件 F-R-12）にも重複の検出（要件 F-R-13）にも乗らず、
 * 「{@code /chat} を両方に書いてしまった」に気づけない。
 * </p>
 *
 * <p>
 * そこで <b>jimble のルートツリーに擬似メソッド {@code WS} として載せる。</b>
 * HTTP のリクエストに {@code WS} というメソッドは来ないので、
 * マッチングとぶつかることはない。一覧・重複検出・{@code path()} のネスト・
 * {@code install()} の取り込みが、そのまま効く。
 * </p>
 */
public final class WsRoutes {

	/** 擬似メソッド名 */
	public static final String METHOD = "WS";

	/** ルートに載せる処理の作り手 */
	public static final AttributeKey<Supplier<WsHandler>> HANDLER =
		new AttributeKey<>("ws_handler", null);

	private WsRoutes () {}

}
