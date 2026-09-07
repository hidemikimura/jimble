package io.jimble.web.ws;

import io.jimble.core.context.Context;
import io.jimble.util.data.Data;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.ws.context.WsContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket を実サーバー・実クライアントで確かめる（要件 F-W-22）
 *
 * <p>
 * クライアントは JDK の {@link WebSocket}。外部の依存を足さずに書ける。
 * </p>
 */
class WsIntegrationTest {

	// region テスト用の処理

	/** 実行ID を集める（Context がメッセージごとかを見るため） */
	static final List<String> executionIds = new ArrayList<>();

	/** 接続ごとのインスタンスを数える */
	static final Set<Integer> handlerInstances = ConcurrentHashMap.newKeySet();

	/** onOpen が呼ばれた */
	static final AtomicReference<CountDownLatch> opened = new AtomicReference<>(new CountDownLatch(1));

	/** onClose が呼ばれた */
	static final AtomicReference<CountDownLatch> closed = new AtomicReference<>(new CountDownLatch(1));

	/** 閉じたときの理由 */
	static final AtomicReference<String> closeReason = new AtomicReference<>();

	/**
	 * おうむ返し
	 */
	public static final class EchoHandler implements WsHandler {

		@Override
		public void onOpen (WsContext context) {

			handlerInstances.add(System.identityHashCode(this));

			// 接続に紐づくものは WsSession に置く
			context.session().attributes().put("count", 0);

			context.session().send("hello");
			opened.get().countDown();

		}

		@Override
		public void onMessage (WsContext context, String message) {

			synchronized (executionIds) {
				executionIds.add(context.executionId());
			}

			int count = (int) context.session().attributes().get("count") + 1;
			context.session().attributes().put("count", count);

			context.session().send(new Data()
				.putData("echo", message)
				.putData("count", count));

		}

		@Override
		public void onClose (WsContext context, int code, String reason) {

			closeReason.set(reason);
			closed.get().countDown();

		}

	}

	/**
	 * 認証つき
	 */
	public static final class GuardedHandler implements WsHandler {

		@Override
		public boolean onUpgrade (WsSession session) {

			// Cookie が読めるのはここだけ
			return "secret".equals(session.cookie("token"));

		}

		@Override
		public void onMessage (WsContext context, String message) {

			context.session().send("ok");

		}

	}

	/**
	 * 落ちる
	 */
	public static final class BrokenHandler implements WsHandler {

		@Override
		public void onMessage (WsContext context, String message) {

			throw new IllegalStateException("わざと落とす");

		}

	}

	/** テスト用アプリケーション */
	static final class WsApp extends JimbleApp {

		{
			get("/health_check", context -> context.response().send());

			ws("/echo", EchoHandler::new);
			ws("/guarded", GuardedHandler::new);
			ws("/broken", BrokenHandler::new);

			path("/room", () -> ws("/lobby", EchoHandler::new));
		}

	}

	// endregion

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		server = JimbleServer.start(new WsApp(), 0);
		client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

	}

	// region ルート表（要件 F-R-12 / F-R-13）

	@Test
	@DisplayName("WebSocket も同じルート表に載る")
	void routesInSameTable () {

		WsApp app = new WsApp();

		List<String> ws = app.router().routes().stream()
			.filter(info -> WsRoutes.METHOD.equals(info.method()))
			.map(info -> info.path())
			.toList();

		/*
		 * helidon では WebSocket は別のルーティングになるが、
		 * jimble のルート表には載せてある。
		 * 載せないと、起動時の一覧にも重複の検出にも乗らない。
		 */
		assertTrue(ws.contains("/echo"), ws.toString());
		assertTrue(ws.contains("/room/lobby"), "path() のネストが効いていない: " + ws);

	}

	@Test
	@DisplayName("同じパスに2つ登録したら止まる")
	void duplicate () {

		assertThrows(Exception.class, () -> new JimbleApp() {
			{
				ws("/dup", EchoHandler::new);
				ws("/dup", EchoHandler::new);
			}
		});

	}

	// endregion

	// region 疎通

	@Test
	@DisplayName("繋いで送って受け取れる")
	void echo () throws Exception {

		opened.set(new CountDownLatch(1));

		Recorder recorder = new Recorder(3);
		WebSocket ws = connect("/echo", recorder);

		assertTrue(opened.get().await(5, TimeUnit.SECONDS), "onOpen が来ていない");

		/*
		 * JDK の WebSocket は、前の送信が終わる前に次を送ってはいけない
		 * （終わっていないと IllegalStateException になり、送信は捨てられる）。
		 * join() せずに2つ続けて送ると、たまに2つめが届かない。
		 */
		ws.sendText("あ", true).join();
		ws.sendText("い", true).join();

		assertTrue(recorder.latch.await(5, TimeUnit.SECONDS), recorder.messages.toString());

		assertEquals("hello", recorder.messages.get(0));

		Data first = Data.fromJsonString(recorder.messages.get(1));
		assertEquals("あ", first.getString("echo"));
		assertEquals(1, first.getInt("count"));

		Data second = Data.fromJsonString(recorder.messages.get(2));
		assertEquals("い", second.getString("echo"));

		// 接続に紐づく値は次のメッセージでも残る（WsSession に置いているから）
		assertEquals(2, second.getInt("count"));

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "おわり").join();

	}

	@Test
	@DisplayName("Context はメッセージごとに別（接続ごとではない）")
	void contextPerMessage () throws Exception {

		synchronized (executionIds) {
			executionIds.clear();
		}

		opened.set(new CountDownLatch(1));

		Recorder recorder = new Recorder(3);
		WebSocket ws = connect("/echo", recorder);

		opened.get().await(5, TimeUnit.SECONDS);

		// 前の送信が終わってから次を送る（echo() のコメント参照）
		ws.sendText("1", true).join();
		ws.sendText("2", true).join();

		recorder.latch.await(5, TimeUnit.SECONDS);

		synchronized (executionIds) {

			assertEquals(2, executionIds.size(), executionIds.toString());

			/*
			 * 接続ごとに1つだと、実行IDが同じになる。
			 * 何時間も生きる Context が DB 接続を握り続けるのを避けるため、
			 * メッセージごとに作って捨てる（D-56）。
			 */
			assertNotEquals(executionIds.get(0), executionIds.get(1)
				, "Context が接続ごとになっている");

		}

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

	}

	@Test
	@DisplayName("ネストしたパスでも繋がる")
	void nested () throws Exception {

		opened.set(new CountDownLatch(1));

		Recorder recorder = new Recorder(1);
		WebSocket ws = connect("/room/lobby", recorder);

		assertTrue(recorder.latch.await(5, TimeUnit.SECONDS));
		assertEquals("hello", recorder.messages.get(0));

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

	}

	@Test
	@DisplayName("閉じたら onClose が来る")
	void close () throws Exception {

		opened.set(new CountDownLatch(1));
		closed.set(new CountDownLatch(1));

		Recorder recorder = new Recorder(1);
		WebSocket ws = connect("/echo", recorder);

		recorder.latch.await(5, TimeUnit.SECONDS);

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "さようなら").join();

		assertTrue(closed.get().await(5, TimeUnit.SECONDS), "onClose が来ていない");
		assertEquals("さようなら", closeReason.get());

	}

	// endregion

	// region アップグレードと例外

	@Test
	@DisplayName("アップグレードを断れる（Cookie で認証）")
	void upgradeRejected () {

		Recorder recorder = new Recorder(1);

		// Cookie が無い
		Exception ex = assertThrows(Exception.class, () -> client.newWebSocketBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/guarded"), recorder)
			.join());

		/*
		 * 繋がってから切るのではなく、握手の時点で断る。
		 * 繋がってしまうと、クライアントは「一度は通った」と思って繋ぎ直しにくる。
		 */
		Throwable cause = ex.getCause() == null ? ex : ex.getCause();

		assertInstanceOf(WebSocketHandshakeException.class, cause, String.valueOf(cause));

		int status = ((WebSocketHandshakeException) cause).getResponse().statusCode();

		assertTrue(status == 403 || status == 400, "握手が通ってしまった: " + status);

	}

	@Test
	@DisplayName("Cookie があれば通る")
	void upgradeAccepted () throws Exception {

		Recorder recorder = new Recorder(1);

		WebSocket ws = client.newWebSocketBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.header("Cookie", "token=secret")
			.buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/guarded"), recorder)
			.join();

		ws.sendText("x", true);

		assertTrue(recorder.latch.await(5, TimeUnit.SECONDS));
		assertEquals("ok", recorder.messages.get(0));

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

	}

	@Test
	@DisplayName("処理が落ちても接続は死なない")
	void handlerThrows () throws Exception {

		Recorder recorder = new Recorder(1);

		WebSocket ws = connect("/broken", recorder);

		ws.sendText("落ちろ", true);

		/*
		 * 1件が落ちても、次のメッセージは受けられること。
		 * 落ちた時点で接続ごと死ぬと、原因が分からないまま切れる。
		 */
		Thread.sleep(500);

		ws.sendText("まだ生きているか", true);
		Thread.sleep(500);

		assertFalse(recorder.failed, "接続が死んでいる");

		ws.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

	}

	// endregion

	// region 小物

	/**
	 * 繋ぐ
	 *
	 * @param path		パス
	 * @param listener	受け取り
	 * @return	接続
	 */
	private static WebSocket connect (String path, WebSocket.Listener listener) {

		return client.newWebSocketBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.buildAsync(URI.create("ws://127.0.0.1:" + server.port() + path), listener)
			.join();

	}

	/**
	 * 届いたものを記録する
	 */
	private static final class Recorder implements WebSocket.Listener {

		/* 届いたもの */
		final List<String> messages = new ArrayList<>();

		/* 待ち */
		final CountDownLatch latch;

		/* 落ちたか */
		volatile boolean failed;

		/* 分割フレームの組み立て */
		private final StringBuilder buffer = new StringBuilder();

		/**
		 * コンストラクタ
		 *
		 * @param expected	待つ件数
		 */
		Recorder (int expected) {

			this.latch = new CountDownLatch(expected);

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public CompletionStage<?> onText (WebSocket webSocket, CharSequence data, boolean last) {

			buffer.append(data);

			if (last) {
				messages.add(buffer.toString());
				buffer.setLength(0);
				latch.countDown();
			}

			webSocket.request(1);

			return null;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onError (WebSocket webSocket, Throwable error) {

			failed = true;

		}

	}

	// endregion

}
