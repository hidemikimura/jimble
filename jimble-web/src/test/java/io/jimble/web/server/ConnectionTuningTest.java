package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接続の受け付けと書き出しの設定（要件 F-H-01 / D-165）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code smart_async_writes} は単独では効かない。</b>
 * helidon は書き出す口をこう選ぶ——
 * </p>
 *
 * <pre>
 * SocketWriter.create(executor, socket, writeQueueLength, smartAsyncWrites)
 *   writeQueueLength &lt;= 1  →  SocketWriterDirect   ← smartAsyncWrites は読まれない
 *   smartAsyncWrites        →  SmartSocketWriter
 *   それ以外                 →  SocketWriterAsync
 * </pre>
 *
 * <p>
 * 既定の {@code writeQueueLength} は <b>0</b> なので、
 * <b>{@code smart_async_writes = true} とだけ書いても何も変わらない</b>。
 * <b>書いても効かない設定</b>は、書いた側から見ると
 * <b>「効いているのに速くならない」</b>としか見えず、
 * <b>原因を設定ファイルの外に探しに行く</b>ことになる（要件 D-86 と同じ形）。
 * </p>
 *
 * <p>
 * <b>だから起動時に落とす。</b>落ちることをここで固定する。
 * </p>
 */
class ConnectionTuningTest {

	/** 何も書いていないアプリ */
	static final class BareApp extends JimbleApp {

		{
			get("/hello", context -> context.response().send("hello"));
		}

	}

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private static void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	// region 既定

	@Test
	@DisplayName("D-165 既定は helidon と同じ（接続待ち 1024 / 列は作らない）")
	void theDefaultsMatchHelidon () {

		conf("");

		/*
		 * <b>既定を変えていない。</b>
		 * 設定できるようにしただけで<b>振る舞いは今までどおり</b>である——
		 * ここが動くと、<b>上げたつもりのないアプリの振る舞いが変わる</b>。
		 */
		assertEquals(1024, ServerConf.backlog());
		assertEquals(0, ServerConf.writeQueueLength());
		assertFalse(ServerConf.smartAsyncWrites());

	}

	@Test
	@DisplayName("設定から読む")
	void theValuesComeFromTheConfig () {

		conf("server { backlog = 2048, write_queue_length = 32, smart_async_writes = true }");

		assertEquals(2048, ServerConf.backlog());
		assertEquals(32, ServerConf.writeQueueLength());
		assertTrue(ServerConf.smartAsyncWrites());

	}

	// endregion

	// region 効かない組み合わせを落とす

	@Test
	@DisplayName("D-165 列が無いのに smart_async_writes = true なら起動しない")
	void smartWritesWithoutAQueueRefusesToStart () {

		conf("server { smart_async_writes = true }");

		IllegalStateException ex = assertThrows(IllegalStateException.class
			, () -> JimbleServer.start(new BareApp(), 0));

		/*
		 * <b>どの設定キーをどうすればよいかまで言う。</b>
		 * 「設定が不正です」だけでは、<b>設定ファイルを見直しても直せない</b>。
		 */
		assertTrue(ex.getMessage().contains("server.write_queue_length"), ex.getMessage());
		assertTrue(ex.getMessage().contains("server.smart_async_writes"), ex.getMessage());

	}

	@Test
	@DisplayName("D-165 write_queue_length = 1 も「列が無い」")
	void aQueueOfOneIsNotAQueue () {

		/*
		 * <b>1 は「列がある」ではない。</b>helidon は
		 * {@code writeQueueLength <= 1} で列を作らない道に入る——
		 * <b>1 と書いた人は列があるつもりでいる</b>ので、ここで言う。
		 */
		conf("server { write_queue_length = 1, smart_async_writes = true }");

		assertThrows(IllegalStateException.class, () -> JimbleServer.start(new BareApp(), 0));

	}

	@Test
	@DisplayName("D-165 接続待ちが 0 以下なら起動しない")
	void aNonPositiveBacklogRefusesToStart () {

		/*
		 * helidon 側でも落ちるが、<b>出るのは helidon の言葉</b>で
		 * <b>どの設定キーのことか書いていない</b>。
		 */
		conf("server { backlog = 0 }");

		IllegalStateException ex = assertThrows(IllegalStateException.class
			, () -> JimbleServer.start(new BareApp(), 0));

		assertTrue(ex.getMessage().contains("server.backlog"), ex.getMessage());

	}

	@Test
	@DisplayName("D-165 落ちるのはポートを掴む前")
	void itFailsBeforeTakingThePort () throws Exception {

		/*
		 * <b>掴んでから落ちると、ホットリロードで前のアプリが残る。</b>
		 * 同じポートをすぐ後で使えることで、<b>掴んでいなかったこと</b>を見る。
		 */
		conf("server { smart_async_writes = true }");

		assertThrows(IllegalStateException.class, () -> JimbleServer.start(new BareApp(), 0));

		conf("");

		JimbleServer server = JimbleServer.start(new BareApp(), 0);

		try {
			assertEquals("hello", get(server.port(), "/hello"));
		} finally {
			server.stop();
		}

	}

	// endregion

	// region 効く組み合わせは動く

	@Test
	@DisplayName("D-165 列を開けて賢く書く設定でも、ふつうに応答する")
	void aRealQueueStillServes () throws Exception {

		conf("server { backlog = 64, write_queue_length = 32, smart_async_writes = true }");

		JimbleServer server = JimbleServer.start(new BareApp(), 0);

		try {

			/*
			 * <b>速さは測らない。</b>見たいのは
			 * <b>helidon が受け取って、応答が壊れないこと</b>だけである。
			 */
			assertEquals("hello", get(server.port(), "/hello"));
			assertEquals("hello", get(server.port(), "/hello"));

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("D-165 設定した値が helidon まで届いている")
	void theValuesReachHelidon () {

		/*
		 * <b>設定できる口を作っただけでは足りない。</b>
		 * <b>helidon に渡し忘れても、設定は読めるし起動もする</b>——
		 * D-86 で見つけた {@code server.max_header_size} が、まさにその形だった
		 * （キーもドキュメントもあったのに、どこにも渡していなかった）。
		 * <b>helidon が持っている値そのもの</b>を見る。
		 */
		conf("server { backlog = 77, write_queue_length = 16, smart_async_writes = true }");

		JimbleServer server = JimbleServer.start(new BareApp(), 0);

		try {

			assertEquals(77, server.effectiveConfig().backlog(), "backlog を渡していません");
			assertEquals(16, server.effectiveConfig().writeQueueLength(), "write_queue_length を渡していません");
			assertTrue(server.effectiveConfig().smartAsyncWrites(), "smart_async_writes を渡していません");

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("列だけ開けて smart を切っても動く")
	void aQueueWithoutSmartWritesAlsoServes () throws Exception {

		conf("server { write_queue_length = 8 }");

		JimbleServer server = JimbleServer.start(new BareApp(), 0);

		try {
			assertEquals("hello", get(server.port(), "/hello"));
		} finally {
			server.stop();
		}

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>速くなるかどうか</b>は見ていない。列を開けても<b>捌く速さは変わらない</b>——
	 *   変わるのは「遅い相手に書いているあいだ、処理が先に進めるか」で、
	 *   <b>効くかどうかは相手の回線とアプリの形による</b>
	 * - <b>{@code backlog} を超えたときに何が起きるか</b>も見ていない。
	 *   断つのは OS で、<b>アプリまで届かない</b>ので、こちらからは観測できない。
	 *   OS 側の上限（Linux の {@code somaxconn}）にも頭を押さえられる
	 * - <b>{@code write_queue_length} を大きくしたときのメモリ</b>も測っていない。
	 *   積んだ分は載るので、<b>大きくすれば速くなるものではない</b>
	 */

	// endregion

	/**
	 * 1回取ってくる
	 *
	 * @param port	ポート
	 * @param path	パス
	 * @return	本文
	 * @throws Exception	例外
	 */
	private static String get (int port, String path) throws Exception {

		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

		HttpResponse<String> response = client.send(
			HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build()
			, HttpResponse.BodyHandlers.ofString());

		return response.body();

	}

}
