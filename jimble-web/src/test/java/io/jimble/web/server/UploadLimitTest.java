package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.upload.UploadConf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * アップロードの上限が本文の上限を超えていたら落とすか（要件 D-159）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>既定のままでも矛盾していた</b>——合計 50MiB に対して本文 10MiB である。
 * 本文は helidon が先に切るので、<b>アップロード側の上限には決して届かない</b>。
 * </p>
 *
 * <p>
 * <b>しかも出るのは upload のエラーではない。</b>
 * 「50MB まで上げられる」と読んで 50MB のファイルを送ると、
 * <b>別の失敗</b>が返ってくるので、設定を読み直しても原因が見つからない。
 * </p>
 */
class UploadLimitTest {

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	@Test
	@DisplayName("D-159 既定どうしは矛盾していない")
	void defaultsAgree () {

		conf("");

		/*
		 * <b>ここが崩れると、すべてのアプリが起動しなくなる。</b>
		 * 0.6.x までは 50MiB と 10MiB で食い違っていた。
		 */
		assertEquals(ServerConf.DEFAULT_MAX_REQUEST_SIZE, UploadConf.DEFAULT_MAX_TOTAL_SIZE
			, "既定が食い違っています");

		assertTrue(UploadConf.maxTotalSize() <= ServerConf.maxRequestSize());

	}

	@Test
	@DisplayName("D-159 合計上限が本文の上限を超えていたら起動しない")
	void contradictionFailsAtStartup () {

		conf("""
			server { max_request_size = 1MiB }
			upload { max_total_size = 50MiB }
			""");

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> JimbleServer.start(new JimbleApp() { }, 0)
			, "届かない上限を黙って通しています");

		assertTrue(thrown.getMessage().contains("upload.max_total_size"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("server.max_request_size"), thrown.getMessage());

	}

	@Test
	@DisplayName("両方上げれば通る")
	void raisingBothIsFine () {

		conf("""
			server { max_request_size = 50MiB }
			upload { max_total_size = 50MiB }
			""");

		JimbleServer server = JimbleServer.start(new JimbleApp() { }, 0);

		try {
			assertTrue(server.port() > 0);
		} finally {
			server.stop();
		}

	}

	// region ここで固定していないこと

	/*
	 * - <b>1ファイルの上限（{@code upload.max_file_size}）</b>は見ていない。
	 *   合計より小さいのが普通で、大きくしても<b>合計で止まる</b>ので事故にならない
	 * - <b>黙って引き上げる道</b>は採らなかった。引き上げると、
	 *   今度は<b>書いていない上限で通る</b>ことになる
	 */

	// endregion

}
