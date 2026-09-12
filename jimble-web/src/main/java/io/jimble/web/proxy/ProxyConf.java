package io.jimble.web.proxy;

import io.jimble.util.conf.Conf;

import java.time.Duration;

/**
 * リバースプロキシの設定
 *
 * <pre>
 * proxy {
 *   connect_timeout = 5s     # 転送先に繋ぐまでの上限
 *   request_timeout = 30s    # 応答を待つ上限
 * }
 * </pre>
 *
 * <p>
 * <b>移送元にはタイムアウトが無かった。</b>転送先が応答しないと、
 * そのリクエストを処理しているスレッドが解放されないまま残る。
 * </p>
 */
public final class ProxyConf {

	/** 設定キー：接続の上限 */
	public static final String KEY_CONNECT_TIMEOUT = "proxy.connect_timeout";

	/** 設定キー：応答待ちの上限 */
	public static final String KEY_REQUEST_TIMEOUT = "proxy.request_timeout";

	/** 既定の接続の上限 */
	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/** 既定の応答待ちの上限 */
	public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private ProxyConf () {}

	/**
	 * 接続の上限
	 *
	 * @return	上限
	 */
	public static Duration connectTimeout () {

		return Conf.conf().getDuration(KEY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);

	}

	/**
	 * 応答待ちの上限
	 *
	 * @return	上限
	 */
	public static Duration requestTimeout () {

		return Conf.conf().getDuration(KEY_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

	}

}
