package io.jimble.web.server;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jimble.core.lifecycle.Shutdown;
import io.jimble.util.conf.Conf;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.jimble.util.log.Log;
import io.jimble.web.internal.ws.WsBridge;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.upload.UploadConf;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.RouteInfo;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * サーバー
 *
 * <p>
 * helidon-webserver を起動し、{@code routing.any()} で受けた全リクエストを
 * {@link Dispatcher} に渡す。<b>helidon のルーティング機能は使わない。</b>
 * </p>
 */
public final class JimbleServer {

	/** 止める途中に来たリクエストへ返すステータス */
	public static final int UNAVAILABLE_STATUS_CODE = 503;

	/* 処理中のリクエストを待つときの様子見の間隔（ミリ秒） */
	private static final long DRAIN_POLL_MILLIS = 50;

	/**
	 * 1つのサーバーの状態（要件 D-91）
	 *
	 * <p>
	 * <b>サーバーごとに持つ。</b>static にすると、
	 * 1つ止めただけで<b>同じ JVM の別のサーバーまで断ってしまう</b>
	 * （テストや、開発中に2つ立てているとき）。
	 * </p>
	 */
	private static final class State {

		/* 処理中のリクエストの数 */
		private final java.util.concurrent.atomic.AtomicInteger inFlight
			= new java.util.concurrent.atomic.AtomicInteger();

		/* 新しいリクエストを断つか */
		private final java.util.concurrent.atomic.AtomicBoolean draining
			= new java.util.concurrent.atomic.AtomicBoolean(false);

	}

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

	/**
	 * この JVM で起動したサーバー（要件 D-77）
	 *
	 * <p>
	 * 普通のアプリでは1つしか入らない。持っているのは
	 * <b>同じ JVM のまま入れ替える</b>開発用のホットリロードのためである
	 * （{@code jimbleRun}）。プロセスを殺せないので、
	 * <b>誰かが「全部止めろ」と言えないと止まらない。</b>
	 * </p>
	 */
	private static final List<JimbleServer> STARTED = new CopyOnWriteArrayList<>();

	/* helidon サーバー */
	private final WebServer server;

	/* このサーバーの状態（要件 D-91） */
	private final State state;

	/**
	 * helidon が実際に持っている設定（要件 D-165）
	 *
	 * <p>
	 * <b>公開しない。</b>ここを見たいのは<b>「設定が helidon まで届いているか」</b>を
	 * 確かめるときだけで、アプリから触るものではない
	 * （触れるようにすると、<b>helidon の型が jimble の公開 API に混ざる</b>）。
	 * </p>
	 *
	 * @return	設定
	 */
	WebServerConfig effectiveConfig () {

		return server.prototype();

	}

	/**
	 * コンストラクタ
	 *
	 * @param server	helidon サーバー
	 * @param state		状態
	 */
	private JimbleServer (WebServer server, State state) {

		this.server = server;
		this.state = state;

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

		/*
		 * 立てるからには受ける（要件 D-91）。
		 *
		 * ホットリロードでは Shutdown.runAll() のあとに立て直すので、
		 * 「止め始めた」を持ち越すと新しいアプリが最初から
		 * ヘルスチェックを落としたままになる。
		 */
		Shutdown.reset();

		/*
		 * <b>ポートを掴む前に見る（要件 D-165）。</b>
		 *
		 * 掴んでから落ちると、<b>ホットリロードで前のアプリが残ったまま</b>
		 * 立て直せなくなる。設定だけで判る間違いは、<b>触る前に落とす</b>。
		 */
		checkBacklog();
		checkWriteQueue();

		State state = new State();

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
			.idleConnectionTimeout(ServerConf.idleTimeout())
			/*
			 * 受け付けと書き出しの調整（要件 F-H-01 / D-165）。
			 *
			 * backlog は「受け付けが追いつかないあいだ OS が持ってくれる接続の数」で、
			 * 超えた分は OS が断つ——<b>アプリまで届かないのでログに1行も出ない</b>。
			 *
			 * write_queue_length は応答を書き出す列の長さ。0 か 1 なら列を作らない。
			 * smart_async_writes は「列があるとき、空いていればその場で書く」の切り替えで、
			 * <b>列が無ければ helidon はこの値を読まない</b>（下の checkWriteQueue が見張っている）。
			 */
			.backlog(ServerConf.backlog())
			.writeQueueLength(ServerConf.writeQueueLength())
			.smartAsyncWrites(ServerConf.smartAsyncWrites())
			// 応答の圧縮（要件 F-H-03）
			.contentEncoding(encoding -> encoding.contentEncodingsDiscoverServices(ServerConf.compression()))
			/*
			 * ヘッダ全体の上限（要件 NF-S-05 / D-86）。
			 *
			 * 設定キーは前からあったが、helidon に渡していなかった。
			 * 書いても効かない設定は、書いた側から見ると
			 * 「効いているのに破られた」と区別がつかない。
			 */
			.addProtocol(Http1Config.builder()
				.maxHeadersSize(ServerConf.maxHeaderSize())
				.build())
			.routing(routing -> routing.any((request, response) -> handle(dispatcher, state, request, response)));

		/*
		 * 待ち受けるアドレス（要件 F-H-06 / D-86）。
		 *
		 * 空なら全部のアドレスで待つ（いままでどおり）。
		 * 手元で動かす MCP サーバーのように外に出してはいけないものは
		 * server.host = "127.0.0.1" で閉じる。
		 */
		if (!ServerConf.host().isEmpty()) {
			builder.host(ServerConf.host());
		}

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

		WebServer server = builder.build();

		JimbleServer started = new JimbleServer(server, state);
		STARTED.add(started);

		/*
		 * <b>待ち受けを始める前に預ける</b>（要件 D-77 / D-110）。
		 *
		 * 待ち受けを始めてから預けるまでのあいだに
		 * jimbleRun が {@code Shutdown.runAll()} を呼ぶと、
		 * <b>預かっているものがまだ無いので何も止まらない。</b>
		 * 古いアプリがポートを握ったまま残り、入れ替えたほうが立ち上がれなくなる。
		 * ログ2行を挟んでいたので、その隙間は実測できる長さだった。
		 *
		 * 名前に番号を入れない。<b>番号が決まるのは start のあと</b>で、
		 * ポート 0（空いているところを使う）では 0 になってしまう。
		 */
		Shutdown.add("サーバー", started::stop);

		server.start();

		Log.info("jimble を起動しました: http://%s:%d".formatted(
			ServerConf.host().isEmpty() ? "localhost" : ServerConf.host(), server.port()));
		Log.info("サーバー設定: 待受=%s / 本文上限=%dbyte / ヘッダ上限=%dbyte / アイドル=%d秒 / 圧縮=%s / プロキシ信頼=%s".formatted(
			ServerConf.host().isEmpty() ? "全部" : ServerConf.host()
			, ServerConf.maxRequestSize()
			, ServerConf.maxHeaderSize()
			, ServerConf.idleTimeout().toSeconds()
			, ServerConf.compression()
			, ServerConf.trustProxy()));

		/*
		 * <b>効いている値をそのまま出す（要件 D-165）。</b>
		 * 書き出しは<b>列があるかどうかで道が変わる</b>ので、
		 * 「賢く書く」が実際に選ばれたのかを<b>ログで見分けられる</b>ようにしておく。
		 */
		/*
		 * <b>設定ではなく、helidon が実際に持っている値を出す（要件 D-165）。</b>
		 * 設定のほうを出すと、<b>渡し忘れても正しく見える</b>——
		 * D-86 で見つけた {@code server.max_header_size} が、まさにその形だった。
		 */
		WebServerConfig effective = server.prototype();

		Log.info("サーバー設定（接続）: 接続待ち=%d / 書き出し列=%s".formatted(
			effective.backlog()
			, effective.writeQueueLength() < ServerConf.MIN_WRITE_QUEUE_LENGTH
				? "作らない（その場で書く）"
				: "%d（%s）".formatted(effective.writeQueueLength()
					, effective.smartAsyncWrites() ? "空いていればその場で書く" : "いつも列に積む")));

		warnSecureCookieInLocal();
		checkUploadLimits();

		/*
		 * SIGTERM で全部止める（要件 D-91）。
		 *
		 * コンテナは SIGTERM を送って待つ。受け取らずに死ぬと、
		 * 処理中のリクエストが途中で切れる。
		 * ホットリロード（jimbleRun）のときは付けない。
		 */
		Shutdown.installJvmHook();

		return started;

	}

	/**
	 * リクエストを処理する
	 *
	 * @param dispatcher	ディスパッチャ
	 * @param request		helidon リクエスト
	 * @param response		helidon レスポンス
	 */
	private static void handle (Dispatcher dispatcher, State state, ServerRequest request, ServerResponse response) {

		/*
		 * 止める途中で来たものは断る（要件 D-91）。
		 *
		 * ここで受けてしまうと、待つ相手が減らない。
		 * ロードバランサから外れるまでの猶予は
		 * server.shutdown_grace_seconds のほうで取る。
		 */
		if (state.draining.get() || Shutdown.isDraining()) {
			response.status(UNAVAILABLE_STATUS_CODE).send();
			return;
		}

		state.inFlight.incrementAndGet();

		try (WebContext context = new WebContext(new HelidonRequestSource(request), new HelidonResponseSink(response))) {
			// try-with-resources でクローズ漏れを構造的に防ぐ（要件 F-C-06）
			dispatcher.dispatch(context);
		} finally {
			state.inFlight.decrementAndGet();
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
	 * 停止する（要件 D-91）
	 *
	 * <p>順番がある。<b>いきなり止めない。</b></p>
	 *
	 * <ol>
	 *   <li>「止め始めた」ことにする。<b>ヘルスチェックだけ</b>が落ちる</li>
	 *   <li>{@code server.shutdown_grace_seconds} 待つ
	 *       （ロードバランサがこの台を外すまで、普通に応え続ける）</li>
	 *   <li>新しいリクエストを断つ（503）</li>
	 *   <li>処理中のものが終わるのを {@code server.shutdown_timeout_seconds} まで待つ</li>
	 *   <li>サーバーを止める</li>
	 * </ol>
	 */
	public void stop () {

		STARTED.remove(this);

		/*
		 * まだ待ち受けていないなら、待つものも断つものも無い（D-110）。
		 * 止め方は<b>待ち受けを始める前に</b>預けてあるので、ここに来ることがある。
		 * 抜けないと、猶予（shutdown_grace_seconds）のぶんだけ黙って寝てしまう。
		 */
		if (!server.isRunning()) {
			return;
		}

		Shutdown.markStopping();

		sleepSeconds(ServerConf.shutdownGrace().toSeconds());

		/*
		 * 断つのは「このサーバー」だけにする。
		 * Shutdown.markDraining() は全体に効くので、
		 * 同じ JVM に2つ立っているときに巻き添えになる。
		 */
		state.draining.set(true);

		drain(state);

		server.stop();
		Log.info("jimble を停止しました");

	}

	/**
	 * 処理中のリクエストが終わるのを待つ（要件 D-91）
	 */
	private static void drain (State state) {

		int remaining = state.inFlight.get();

		if (remaining <= 0) {
			return;
		}

		long timeoutMillis = ServerConf.shutdownTimeout().toMillis();
		long until = System.currentTimeMillis() + timeoutMillis;

		Log.info("処理中のリクエストを待ちます: %d 件（最大 %d 秒）"
			.formatted(remaining, ServerConf.shutdownTimeout().toSeconds()));

		while (state.inFlight.get() > 0 && System.currentTimeMillis() < until) {

			try {
				Thread.sleep(DRAIN_POLL_MILLIS);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				break;
			}

		}

		remaining = state.inFlight.get();

		if (remaining > 0) {
			// 待ちきれなかったことは黙らない。切られた相手がいる
			Log.warn("処理中のリクエストが %d 件残ったまま停止します（server.shutdown_timeout = %d 秒）"
				.formatted(remaining, ServerConf.shutdownTimeout().toSeconds()));
		}

	}

	/**
	 * 待つ
	 *
	 * @param seconds	秒
	 */
	private static void sleepSeconds (long seconds) {

		if (seconds <= 0) {
			return;
		}

		Log.info("ヘルスチェックを落として %d 秒待ちます（ロードバランサが外すのを待つ）".formatted(seconds));

		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}

	}

	/**
	 * 処理中のリクエストの数（テスト用）
	 *
	 * @return	件数
	 */
	int inFlight () {

		return state.inFlight.get();

	}

	/**
	 * この JVM で起動したサーバーを全部止める（要件 D-77）
	 *
	 * <p>
	 * <b>同じ JVM のままアプリを入れ替える開発用</b>の入口である
	 * （{@code jimbleRun}）。本番では使わない。プロセスが終われば止まる。
	 * </p>
	 *
	 * @return	止めた数
	 */
	public static int stopAll () {

		int count = 0;

		for (JimbleServer started : List.copyOf(STARTED)) {

			try {
				started.stop();
				count++;
			} catch (Exception ex) {
				Log.error(ex, "サーバーを止められませんでした");
			}

		}

		STARTED.clear();

		return count;

	}

	/**
	 * 列が無いのに「賢く書く」と書いていないか（要件 D-165）
	 *
	 * <p>
	 * <b>helidon は {@code writeQueueLength <= 1} のとき
	 * {@code smartAsyncWrites} を読まない。</b>
	 * 列を作らない道（{@code SocketWriterDirect}）に入るので、
	 * <b>その場で書くしかない</b>ためである。
	 * </p>
	 *
	 * <p>
	 * <b>黙って無視しない。</b>「書いたのに効かない設定」は、
	 * 書いた側から見ると<b>効いているのに速くならない</b>としか見えず、
	 * <b>原因を設定ファイルの外に探しに行く</b>ことになる（要件 D-86 と同じ形）。
	 * </p>
	 */
	private static void checkWriteQueue () {

		if (!ServerConf.smartAsyncWrites()) {
			return;
		}

		int length = ServerConf.writeQueueLength();

		if (length >= ServerConf.MIN_WRITE_QUEUE_LENGTH) {
			return;
		}

		/*
		 * <b>黙って列を作らない。</b>作ると、
		 * 今度は<b>書いていない長さで動く</b>ことになる。
		 */
		throw new IllegalStateException(
			("server.smart_async_writes = true ですが、server.write_queue_length が %d です。"
				+ "列が無いと（%d 以下）helidon はこの設定を読まないので、何も変わりません。"
				+ "server.write_queue_length を %d 以上にするか、"
				+ "server.smart_async_writes を外してください")
				.formatted(length, ServerConf.MIN_WRITE_QUEUE_LENGTH - 1
					, ServerConf.MIN_WRITE_QUEUE_LENGTH));

	}

	/**
	 * 接続待ちの数が正気か（要件 D-165）
	 *
	 * <p>
	 * <b>0 以下は helidon が受け取らない</b>（{@code IllegalArgumentException}）。
	 * そちらでも落ちるが、<b>出るのは helidon の言葉</b>で、
	 * どの設定キーのことか書いていない。
	 * </p>
	 */
	private static void checkBacklog () {

		int backlog = ServerConf.backlog();

		if (backlog > 0) {
			return;
		}

		throw new IllegalStateException(
			("server.backlog は 1 以上にしてください（いまは %d）。"
				+ "既定は %d です").formatted(backlog, ServerConf.DEFAULT_BACKLOG));

	}

	/**
	 * アップロードの上限が本文の上限を超えていたら落とす（要件 D-159 / F-X-05）
	 *
	 * <p>
	 * <b>{@code upload.max_total_size} は {@code server.max_request_size} に頭を押さえられている。</b>
	 * 本文は helidon が先に切るので、<b>アップロード側の上限には決して届かない</b>。
	 * </p>
	 *
	 * <p>
	 * <b>既定のままでも矛盾していた</b>——合計 50MB に対して本文 10MB である。
	 * 「50MB まで上げられる」と読んで 50MB のファイルを送ると、
	 * <b>upload のエラーではない別の失敗</b>が返る。
	 * </p>
	 */
	private static void checkUploadLimits () {

		long total = UploadConf.maxTotalSize();
		long request = ServerConf.maxRequestSize();

		if (total <= request) {
			return;
		}

		/*
		 * <b>既定のままでも矛盾していた</b>（合計 50MB に対して本文 10MB）。
		 * 先に helidon が切るので、<b>upload の上限には決して届かない</b>——
		 * しかも出るのは upload のエラーではなく<b>別の失敗</b>なので、
		 * 設定を読み直しても原因が見つからない。
		 *
		 * <b>黙って引き上げない。</b>引き上げると、
		 * 今度は<b>書いていない上限で通る</b>ことになる。
		 */
		throw new IllegalStateException(
			("upload.max_total_size（%d バイト）が server.max_request_size（%d バイト）を超えています。"
				+ "先にサーバー側で切られるので、この上限には届きません。"
				+ "server.max_request_size を上げるか、upload.max_total_size を下げてください")
				.formatted(total, request));

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
