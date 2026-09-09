package io.jimble.web.server;

import io.jimble.util.conf.Conf;

/**
 * HTTP サーバーの設定（要件 F-H-01〜F-H-05）
 *
 * <pre>
 * server {
 *   host                 = ""         # 待ち受けるアドレス。空なら全部
 *   port                 = 9000
 *   max_request_size     = 10485760   # リクエスト本文の上限（バイト）
 *   max_header_size      = 16384      # ヘッダ全体の上限（バイト）
 *   idle_timeout_seconds = 60
 *   trust_proxy          = false      # X-Forwarded-* を信じるか
 *   compression          = true       # 応答を gzip で返すか
 *   bot_access_log       = true       # ボットのアクセスログを分けるか
 * }
 * </pre>
 *
 * <p>
 * <b>移送元はここが無かった</b>（jooby / Jetty 側の設定に散っていた）。
 * jimble では<b>ポート以外もすべて設定から読む。</b>
 * </p>
 *
 * <p>
 * ポートだけはシステムプロパティ {@code jimble.server.port} でも指定できる。
 * コンテナで「設定ファイルは触らずポートだけ変える」ことが多いため。
 * </p>
 */
public final class ServerConf {

	/** 設定キー：待ち受けるアドレス */
	public static final String KEY_HOST = "server.host";

	/** 設定キー：ポート */
	public static final String KEY_PORT = "server.port";

	/** 設定キー：リクエスト本文の上限（バイト） */
	public static final String KEY_MAX_REQUEST_SIZE = "server.max_request_size";

	/** 設定キー：ヘッダ全体の上限（バイト） */
	public static final String KEY_MAX_HEADER_SIZE = "server.max_header_size";

	/** 設定キー：アイドルタイムアウト（秒） */
	public static final String KEY_IDLE_TIMEOUT_SECONDS = "server.idle_timeout_seconds";

	/** 設定キー：プロキシヘッダを信じるか */
	public static final String KEY_TRUST_PROXY = "server.trust_proxy";

	/** 設定キー：応答を圧縮するか */
	public static final String KEY_COMPRESSION = "server.compression";

	/** 設定キー：ボットのアクセスログを分けるか */
	public static final String KEY_BOT_ACCESS_LOG = "server.bot_access_log";

	/** 設定の鍵：到達不能ルートを例外にするか */
	public static final String KEY_STRICT_ROUTES = "server.strict_routes";

	/** 設定キー：止め始めてから新規を断つまでの猶予（秒） */
	public static final String KEY_SHUTDOWN_GRACE_SECONDS = "server.shutdown_grace_seconds";

	/** 設定キー：処理中のリクエストを待つ上限（秒） */
	public static final String KEY_SHUTDOWN_TIMEOUT_SECONDS = "server.shutdown_timeout_seconds";

	/** 既定の猶予（秒） */
	public static final long DEFAULT_SHUTDOWN_GRACE_SECONDS = 0;

	/** 既定の待つ上限（秒） */
	public static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 15;

	/** システムプロパティ：ポート */
	public static final String PROPERTY_PORT = "jimble.server.port";

	/** 既定のポート */
	public static final int DEFAULT_PORT = 9000;

	/** 既定のリクエスト本文の上限（10MB。要件 NF-S-05） */
	public static final long DEFAULT_MAX_REQUEST_SIZE = 10L * 1024 * 1024;

	/** 既定のヘッダ全体の上限（16KB。要件 NF-S-05） */
	public static final long DEFAULT_MAX_HEADER_SIZE = 16L * 1024;

	/** 既定のアイドルタイムアウト（秒） */
	public static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 60;

	private ServerConf () {}

	/**
	 * 待ち受けるアドレス（要件 F-H-06）
	 *
	 * <p>
	 * <b>空なら全部のアドレスで待つ</b>（いままでどおり）。
	 * {@code "127.0.0.1"} にすると<b>そのマシンからしか繋がらない。</b>
	 * </p>
	 *
	 * <p>
	 * 手元で動かす MCP サーバーのように、<b>外から見えてはいけないもの</b>で使う。
	 * ファイアウォールに頼ると、設定を忘れたときに黙って公開される。
	 * </p>
	 *
	 * @return	アドレス（空なら全部）
	 */
	public static String host () {

		return Conf.conf().getString(KEY_HOST, "").trim();

	}

	/**
	 * ポート
	 *
	 * <p>システムプロパティ &gt; 設定 &gt; 既定値 の順で決まる。</p>
	 *
	 * @return	ポート
	 */
	public static int port () {

		Integer property = Integer.getInteger(PROPERTY_PORT);

		if (property != null) {
			return property;
		}

		return (int) Conf.conf().getLong(KEY_PORT, DEFAULT_PORT);

	}

	/**
	 * リクエスト本文の上限（バイト）
	 *
	 * @return	バイト数
	 */
	public static long maxRequestSize () {

		return Conf.conf().getLong(KEY_MAX_REQUEST_SIZE, DEFAULT_MAX_REQUEST_SIZE);

	}

	/**
	 * ヘッダ全体の上限（バイト）
	 *
	 * @return	バイト数
	 */
	public static int maxHeaderSize () {

		return (int) Conf.conf().getLong(KEY_MAX_HEADER_SIZE, DEFAULT_MAX_HEADER_SIZE);

	}

	/**
	 * 止め始めてから新しいリクエストを断つまでの猶予（秒。要件 D-91）
	 *
	 * <p>
	 * <b>ロードバランサがこの台を外すのを待つ時間。</b>
	 * この間もリクエストは普通に処理し、ヘルスチェックだけが落ちる。
	 * 既定は 0（すぐ断つ）。
	 * </p>
	 *
	 * @return	秒
	 */
	public static long shutdownGraceSeconds () {

		return Conf.conf().getLong(KEY_SHUTDOWN_GRACE_SECONDS, DEFAULT_SHUTDOWN_GRACE_SECONDS);

	}

	/**
	 * 処理中のリクエストを待つ上限（秒。要件 D-91）
	 *
	 * @return	秒
	 */
	public static long shutdownTimeoutSeconds () {

		return Conf.conf().getLong(KEY_SHUTDOWN_TIMEOUT_SECONDS, DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);

	}

	/**
	 * アイドルタイムアウト（秒）
	 *
	 * @return	秒数
	 */
	public static long idleTimeoutSeconds () {

		return Conf.conf().getLong(KEY_IDLE_TIMEOUT_SECONDS, DEFAULT_IDLE_TIMEOUT_SECONDS);

	}

	/**
	 * プロキシヘッダを信じるか（要件 F-H-02）
	 *
	 * <p>
	 * <b>既定は false。</b>信じると、クライアントが
	 * {@code X-Forwarded-For} を名乗るだけで送信元を偽れる。
	 * ロードバランサの後ろに置いたときだけ true にする。
	 * </p>
	 *
	 * @return	信じる場合 = true
	 */
	public static boolean trustProxy () {

		return Conf.conf().getBoolean(KEY_TRUST_PROXY, false);

	}

	/**
	 * 応答を圧縮するか（要件 F-H-03）
	 *
	 * @return	圧縮する場合 = true
	 */
	public static boolean compression () {

		return Conf.conf().getBoolean(KEY_COMPRESSION, true);

	}

	/**
	 * ボットのアクセスログを分けるか（要件 F-H-05）
	 *
	 * @return	分ける場合 = true
	 */
	public static boolean botAccessLog () {

		return Conf.conf().getBoolean(KEY_BOT_ACCESS_LOG, true);

	}

	/**
	 * 到達不能ルートを例外にするか（要件 F-R-13 / D-10）
	 *
	 * <p>
	 * <b>既定は false（警告だけ）。</b>いま動いているアプリを、
	 * 版を上げただけで起動しなくするわけにはいかない。
	 * </p>
	 *
	 * <p>
	 * <b>CI では true にすること。</b>警告は起動ログの何十行にも紛れるので、
	 * <b>出しただけでは誰も見ない</b>。
	 * </p>
	 *
	 * @return	例外にする場合 = true
	 */
	public static boolean strictRoutes () {

		return Conf.conf().getBoolean(KEY_STRICT_ROUTES, false);

	}

}
