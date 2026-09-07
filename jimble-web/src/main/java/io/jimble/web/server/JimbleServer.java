package io.jimble.web.server;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jimble.util.conf.Conf;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.jimble.util.log.Log;
import io.jimble.web.ws.WsBridge;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.RouteInfo;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * サーバー
 *
 * <p>
 * helidon-webserver を起動し、{@code routing.any()} で受けた全リクエストを
 * {@link Dispatcher} に渡す。<b>helidon のルーティング機能は使わない。</b>
 * </p>
 */
public final class JimbleServer {

	/**
	 * ポート番号のシステムプロパティ
	 *
	 * @deprecated {@link ServerConf#PROPERTY_PORT} を使う
	 */
	@Deprecated
	public static final String PROPERTY_PORT = ServerConf.PROPERTY_PORT;

	/**
	 * 既定のポート番号
	 *
	 * @deprecated {@link ServerConf#DEFAULT_PORT} を使う
	 */
	@Deprecated
	public static final int DEFAULT_PORT = ServerConf.DEFAULT_PORT;

	/* helidon サーバー */
	private final WebServer server;

	/**
	 * コンストラクタ
	 *
	 * @param server	helidon サーバー
	 */
	private JimbleServer (WebServer server) {

		this.server = server;

	}

	/**
	 * 起動する
	 *
	 * <p>
	 * ポート番号はシステムプロパティ {@code jimble.server.port} &gt;
	 * 設定 {@code server.port} &gt; 既定値 の順で決まる（{@link ServerConf}）。
	 * </p>
	 *
	 * @param app	アプリケーション
	 * @return	サーバー
	 */
	public static JimbleServer start (JimbleApp app) {

		return start(app, ServerConf.port());

	}

	/**
	 * 起動する
	 *
	 * @param app	アプリケーション
	 * @param port	ポート番号（0 で空きポートの自動割当）
	 * @return	サーバー
	 */
	public static JimbleServer start (JimbleApp app, int port) {

		Objects.requireNonNull(app, "app");

		checkStandardOutputEncoding();

		// 今どの実装が有効かを出す（要件 F-U-11）
		StartupReport.log();

		// ルートツリーの構築は起動時に1回だけ（要件 F-R-10）
		Dispatcher dispatcher = new Dispatcher(app);
		logRoutes(dispatcher);

		/*
		 * ポート以外も設定から読む（要件 F-H-01 / F-H-03）。
		 * 移送元はここが jooby / Jetty 側の設定に散っていて、
		 * アプリからは何が効いているのか分からなかった。
		 */
		WebServerConfig.Builder builder = WebServer.builder()
			.port(port)
			// リクエスト本文の上限（要件 F-H-01 / NF-S-05）
			.maxPayloadSize(ServerConf.maxRequestSize())
			// アイドルタイムアウト（要件 F-H-01）
			.idleConnectionTimeout(Duration.ofSeconds(ServerConf.idleTimeoutSeconds()))
			// 応答の圧縮（要件 F-H-03）
			.contentEncoding(encoding -> encoding.contentEncodingsDiscoverServices(ServerConf.compression()))
			.routing(routing -> routing.any((request, response) -> handle(dispatcher, request, response)));

		/*
		 * WebSocket（要件 F-W-22）。
		 *
		 * helidon はアップグレードを HTTP のルーティングより前で横取りするので、
		 * routing.any() には来ない。別のルーティングとして足す。
		 * ルート表そのものは jimble 側にあるので、
		 * 起動時の一覧にも重複の検出にも乗っている（WsRoutes 参照）。
		 */
		WsRouting.Builder wsRouting = WsBridge.build(app.router().routes());

		if (wsRouting != null) {
			builder.addRouting(wsRouting);
		}

		WebServer server = builder.build().start();

		Log.info("jimble を起動しました: http://localhost:%d".formatted(server.port()));
		Log.info("サーバー設定: 本文上限=%dbyte / アイドル=%d秒 / 圧縮=%s / プロキシ信頼=%s".formatted(
			ServerConf.maxRequestSize()
			, ServerConf.idleTimeoutSeconds()
			, ServerConf.compression()
			, ServerConf.trustProxy()));

		warnSecureCookieInLocal();

		return new JimbleServer(server);

	}

	/**
	 * リクエストを処理する
	 *
	 * @param dispatcher	ディスパッチャ
	 * @param request		helidon リクエスト
	 * @param response		helidon レスポンス
	 */
	private static void handle (Dispatcher dispatcher, ServerRequest request, ServerResponse response) {

		// try-with-resources でクローズ漏れを構造的に防ぐ（要件 F-C-06）
		try (WebContext context = new WebContext(new HelidonRequestSource(request), new HelidonResponseSink(response))) {
			dispatcher.dispatch(context);
		}

	}

	/**
	 * ルート一覧をログに出す
	 *
	 * @param dispatcher	ディスパッチャ
	 */
	private static void logRoutes (Dispatcher dispatcher) {

		for (RouteInfo info : dispatcher.router().routes()) {
			Log.info("route: " + info);
		}

	}

	/**
	 * 標準出力の文字コードを確認する
	 *
	 * <p>
	 * Java 18 以降でも {@code System.out} はコンソールの文字コードに従うため、
	 * {@code LANG} が未設定のコンテナでは日本語のログが化ける（要件 F-U-12）。
	 * </p>
	 */
	private static void checkStandardOutputEncoding () {

		if (!StandardCharsets.UTF_8.equals(System.out.charset())) {
			Log.warn(("標準出力の文字コードが %s です。日本語のログが化ける可能性があります。"
					+ " 起動オプションに -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 を追加してください。")
				.formatted(System.out.charset()));
		}

	}

	/**
	 * 実際に待ち受けているポート番号
	 *
	 * @return	ポート番号
	 */
	public int port () {

		return server.port();

	}

	/**
	 * 停止する
	 */
	public void stop () {

		server.stop();
		Log.info("jimble を停止しました");

	}

	/**
	 * ローカルで Cookie が届かない設定になっていたら言う（要件 F-X-05）
	 *
	 * <p>
	 * {@code cookie.secure} の既定は {@code true} である（要件 NF-S-04。安全側が既定）。
	 * ところが<b>ローカルは http なので、ブラウザは Secure な Cookie を送り返さない。</b>
	 * セッションも CSRF も Flash も「例外も出ないのに効かない」形になり、
	 * 原因が Cookie の設定にあるとは思い至りにくい。
	 * </p>
	 *
	 * <p>
	 * 既定は変えない（本番で secure が外れているほうが危ない）。
	 * <b>気づけるようにするだけ</b>にする。
	 * </p>
	 */
	private static void warnSecureCookieInLocal () {

		if (!Conf.DEFAULT_ENV.equals(Conf.env())) {
			return;
		}

		if (!CookieConf.secure()) {
			return;
		}

		Log.warn("""
			cookie.secure = true のままです（env=local）。
			ローカルは http なので、ブラウザは Cookie を送り返しません。
			セッション・CSRF・Flash が黙って効かなくなります。
			application.conf に次を足してください。
			  cookie { secure = false }""");

	}

}
