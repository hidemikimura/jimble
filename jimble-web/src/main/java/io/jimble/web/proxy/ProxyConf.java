package io.jimble.web.proxy;

import io.jimble.util.conf.Conf;

import java.time.Duration;

/**
 * リバースプロキシの設定
 *
 * <pre>
 * proxy {
 *   connect_timeout = 5s     # 転送先に繋ぐまでの上限
 *   request_timeout = 30s    # 送るときに進まない待ち・応答のヘッダが届くまで・本文の1回の読み込みの上限
 *   body_timeout    = 0s     # 応答の本文の全体の上限（0 なら上限なし）
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

	/**
	 * 設定キー：応答の本文の全体の上限（D-258。既定 0 ＝ 上限なし）
	 *
	 * <p>
	 * 既定で切らないのは、大きなダウンロードや SSE を途中で切らないため。
	 * 1回の読み込みごとの待ちは {@link #KEY_REQUEST_TIMEOUT} が見る。
	 * </p>
	 */
	public static final String KEY_BODY_TIMEOUT = "proxy.body_timeout";

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
	 * <p>
	 * 送るあいだに進まない待ち、送り終えてから応答のヘッダが届くまでの全体の待ち、
	 * 本文の1回の読み込みの待ち、の3つに使う（D-258）。
	 * </p>
	 *
	 * @return	上限
	 */
	public static Duration requestTimeout () {

		return Conf.conf().getDuration(KEY_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

	}

	/**
	 * 応答の本文の全体の上限
	 *
	 * @return	上限（0 なら上限なし）
	 */
	public static Duration bodyTimeout () {

		return Conf.conf().getDuration(KEY_BODY_TIMEOUT, Duration.ZERO);

	}

}
