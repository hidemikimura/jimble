package io.jimble.web.proxy;

import io.jimble.util.conf.Conf;

import java.time.Duration;

/**
 * リバースプロキシの設定
 *
 * <pre>
 * proxy {
 *   connect_timeout_ms = 5000    # 転送先に繋ぐまでの上限（ミリ秒）
 *   request_timeout_ms = 30000   # 応答を待つ上限（ミリ秒）
 * }
 * </pre>
 *
 * <p>
 * <b>移送元にはタイムアウトが無かった。</b>転送先が応答しないと、
 * そのリクエストを処理しているスレッドが解放されないまま残る。
 * </p>
 */
public final class ProxyConf {

	/** 設定キー：接続の上限（ミリ秒） */
	public static final String KEY_CONNECT_TIMEOUT_MS = "proxy.connect_timeout_ms";

	/** 設定キー：応答待ちの上限（ミリ秒） */
	public static final String KEY_REQUEST_TIMEOUT_MS = "proxy.request_timeout_ms";

	/** 既定の接続の上限（ミリ秒） */
	public static final long DEFAULT_CONNECT_TIMEOUT_MS = 5000;

	/** 既定の応答待ちの上限（ミリ秒） */
	public static final long DEFAULT_REQUEST_TIMEOUT_MS = 30000;

	private ProxyConf () {}

	/**
	 * 接続の上限
	 *
	 * @return	上限
	 */
	public static Duration connectTimeout () {

		return Duration.ofMillis(Conf.conf().getLong(KEY_CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS));

	}

	/**
	 * 応答待ちの上限
	 *
	 * @return	上限
	 */
	public static Duration requestTimeout () {

		return Duration.ofMillis(Conf.conf().getLong(KEY_REQUEST_TIMEOUT_MS, DEFAULT_REQUEST_TIMEOUT_MS));

	}

}
