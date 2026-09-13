package io.jimble.web.server;

import java.time.Duration;
import io.jimble.util.conf.Conf;
import io.jimble.util.conf.ConfFlag;

/**
 * HTTP サーバーの設定（要件 F-H-01〜F-H-05）
 *
 * <pre>
 * server {
 *   host                 = ""         # 待ち受けるアドレス。空なら全部
 *   port                 = 9000
 *   max_request_size     = 10MiB       # リクエスト本文の上限
 *   max_header_size      = 16KiB       # ヘッダ全体の上限
 *   idle_timeout         = 60s
 *   trust_proxy          = false      # X-Forwarded-* を信じるか
 *   compression          = true       # 応答を gzip で返すか
 *   bot_access_log       = true       # ボットのアクセスログを分けるか
 *   backlog              = 1024       # OS が持ってくれる接続待ちの数
 *   write_queue_length   = 0          # 応答を書き出す列の長さ。0/1 は「列を作らない」
 *   smart_async_writes   = false      # 列があるとき、混み具合で書き方を切り替えるか
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

	/** 設定キー：アイドルタイムアウト */
	public static final String KEY_IDLE_TIMEOUT = "server.idle_timeout";

	/** 設定キー：プロキシヘッダを信じるか */
	public static final String KEY_TRUST_PROXY = "server.trust_proxy";

	/** 設定キー：応答を圧縮するか */
	public static final String KEY_COMPRESSION = "server.compression";

	/** 設定キー：ボットのアクセスログを分けるか */
	public static final String KEY_BOT_ACCESS_LOG = "server.bot_access_log";

	/** 設定の鍵：アクセスログを出すか */
	public static final String KEY_ACCESS_LOG = "server.access_log";

	/** 設定の鍵：到達不能ルートを例外にするか */
	public static final String KEY_STRICT_ROUTES = "server.strict_routes";

	/** 設定キー：接続待ちの数（listen backlog） */
	public static final String KEY_BACKLOG = "server.backlog";

	/** 設定キー：応答を書き出す列の長さ */
	public static final String KEY_WRITE_QUEUE_LENGTH = "server.write_queue_length";

	/** 設定キー：混み具合で書き方を切り替えるか */
	public static final String KEY_SMART_ASYNC_WRITES = "server.smart_async_writes";

	/** 設定キー：止め始めてから新規を断つまでの猶予 */
	public static final String KEY_SHUTDOWN_GRACE = "server.shutdown_grace";

	/** 設定キー：処理中のリクエストを待つ上限 */
	public static final String KEY_SHUTDOWN_TIMEOUT = "server.shutdown_timeout";

	/** 既定の猶予 */
	public static final Duration DEFAULT_SHUTDOWN_GRACE = Duration.ZERO;

	/** 既定の待つ上限 */
	public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

	/** システムプロパティ：ポート */
	public static final String PROPERTY_PORT = "jimble.server.port";

	/** 既定のポート */
	public static final int DEFAULT_PORT = 9000;

	/** 既定のリクエスト本文の上限（10MiB。要件 NF-S-05） */
	public static final long DEFAULT_MAX_REQUEST_SIZE = 10L * 1024 * 1024;

	/** 既定のヘッダ全体の上限（16KiB。要件 NF-S-05） */
	public static final long DEFAULT_MAX_HEADER_SIZE = 16L * 1024;

	/** 既定のアイドルタイムアウト */
	public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(60);

	/** 既定の接続待ちの数（helidon 4.5.4 と同じ） */
	public static final int DEFAULT_BACKLOG = 1024;

	/** 既定の書き出し列の長さ（helidon 4.5.4 と同じ。0 = 列を作らない） */
	public static final int DEFAULT_WRITE_QUEUE_LENGTH = 0;

	/**
	 * 列を作るとみなす最小の長さ
	 *
	 * <p>
	 * <b>helidon は {@code writeQueueLength <= 1} のとき列を作らない</b>
	 * （{@code SocketWriterDirect} になる）。<b>1 は「列がある」ではない。</b>
	 * </p>
	 */
	public static final int MIN_WRITE_QUEUE_LENGTH = 2;

	/**
	 * リクエストごとに読むもの（要件 D-167）
	 *
	 * <p>
	 * <b>{@code Conf.conf().getBoolean(...)} は1回 143 byte / 150ns かかる。</b>
	 * <b>出す・出さないに関わらず、確かめる費用は毎回かかる</b>ので、
	 * 覚えておく（設定が入れ替わったら読み直す）。
	 * </p>
	 */
	private static final ConfFlag ACCESS_LOG = ConfFlag.of(KEY_ACCESS_LOG, true);

	/** リクエストごとに読むもの（要件 D-167） */
	private static final ConfFlag BOT_ACCESS_LOG = ConfFlag.of(KEY_BOT_ACCESS_LOG, true);

	/** リクエストごとに読むもの（要件 D-167） */
	private static final ConfFlag TRUST_PROXY = ConfFlag.of(KEY_TRUST_PROXY, false);

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

		return Conf.conf().getBytes(KEY_MAX_REQUEST_SIZE, DEFAULT_MAX_REQUEST_SIZE);

	}

	/**
	 * ヘッダ全体の上限（バイト）
	 *
	 * @return	バイト数
	 */
	public static int maxHeaderSize () {

		return (int) Conf.conf().getBytes(KEY_MAX_HEADER_SIZE, DEFAULT_MAX_HEADER_SIZE);

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
	 * @return	猶予
	 */
	public static Duration shutdownGrace () {

		return Conf.conf().getDuration(KEY_SHUTDOWN_GRACE, DEFAULT_SHUTDOWN_GRACE);

	}

	/**
	 * 処理中のリクエストを待つ上限（要件 D-91）
	 *
	 * @return	上限
	 */
	public static Duration shutdownTimeout () {

		return Conf.conf().getDuration(KEY_SHUTDOWN_TIMEOUT, DEFAULT_SHUTDOWN_TIMEOUT);

	}

	/**
	 * アイドルタイムアウト
	 *
	 * @return	時間
	 */
	public static Duration idleTimeout () {

		return Conf.conf().getDuration(KEY_IDLE_TIMEOUT, DEFAULT_IDLE_TIMEOUT);

	}

	/**
	 * 接続待ちの数（要件 F-H-01 / D-165）
	 *
	 * <p>
	 * <b>受け付けが追いつかないあいだ、OS が代わりに持ってくれる接続の数である。</b>
	 * ここを超えると、OS は<b>繋ぎに来た相手を断る</b>（接続拒否）——
	 * <b>アプリまで届かないので、ログには1行も出ない。</b>
	 * </p>
	 *
	 * <p>
	 * 上げるのは<b>短時間にどっと来る</b>使い方（起動直後・キャンペーン・再接続の集中）で、
	 * <b>捌く速さは変わらない</b>。詰まりを待たせるだけである。
	 * </p>
	 *
	 * <p>
	 * <b>OS 側の上限にも頭を押さえられる</b>（Linux の {@code somaxconn}）。
	 * ここを大きくしても、そちらが小さければそちらで切られる。
	 * </p>
	 *
	 * @return	接続待ちの数
	 */
	public static int backlog () {

		return (int) Conf.conf().getLong(KEY_BACKLOG, DEFAULT_BACKLOG);

	}

	/**
	 * 応答を書き出す列の長さ（要件 F-H-01 / D-165）
	 *
	 * <p>
	 * <b>0 か 1 なら列を作らない</b>——応答はその場で書き切る（既定）。
	 * 2 以上にすると<b>別のスレッドが書き出す</b>ようになり、
	 * 遅い相手に書いているあいだ、処理のほうが先に進める。
	 * </p>
	 *
	 * <p>
	 * <b>ただし列に積んだ分はメモリに載る。</b>
	 * 相手が受け取らないと積み上がるので、<b>大きくすれば速くなるものではない</b>。
	 * </p>
	 *
	 * @return	列の長さ（0 なら列を作らない）
	 */
	public static int writeQueueLength () {

		return (int) Conf.conf().getLong(KEY_WRITE_QUEUE_LENGTH, DEFAULT_WRITE_QUEUE_LENGTH);

	}

	/**
	 * 混み具合で書き方を切り替えるか（要件 F-H-01 / D-165）
	 *
	 * <p>
	 * 列があるとき、helidon は<b>いつも列に積む</b>（既定）か、
	 * <b>空いていればその場で書き、混んできたら列に積む</b>かを選べる。
	 * 後者が「賢い」ほうで、<b>空いているときの往復が1つ減る</b>。
	 * </p>
	 *
	 * <h4>これは単独では効かない</h4>
	 * <p>
	 * <b>{@link #writeQueueLength()} が 2 以上でなければ、helidon はこの値を読まない。</b>
	 * 列が無いときは、そもそも「その場で書く」しかないからである。
	 * </p>
	 *
	 * <p>
	 * <b>書いても効かない設定にはしない</b>ので、
	 * 列が無いのに {@code true} と書いてあったら<b>起動時に落とす</b>
	 * （{@code JimbleServer} が見ている）。
	 * </p>
	 *
	 * @return	切り替える場合 = true
	 */
	public static boolean smartAsyncWrites () {

		return Conf.conf().getBoolean(KEY_SMART_ASYNC_WRITES, false);

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

		return TRUST_PROXY.get();

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

		return BOT_ACCESS_LOG.get();

	}

	/**
	 * アクセスログを出すか（要件 NF-O-02 / D-130）
	 *
	 * <p>
	 * <b>既定は true。</b>1リクエスト1行は、あとから何が起きたかを追うための最後の綱である。
	 * </p>
	 *
	 * <p>
	 * <b>切れるようにしてあるのは、これが1リクエストの中でいちばん大きいから</b>である
	 * （割り当ての約 3 割。{@code RequestBench} が内訳を出している）。
	 * 前段でアクセスログを取っていて二重になっている場合や、
	 * 毎秒数万本を捌く口では、切る判断がありうる。
	 * </p>
	 *
	 * <p>
	 * <b>切ると、アクセスログだけでなく、それを組み立てるための仕事も止まる</b>
	 * （ボットかどうかの判定＝ユーザーエージェントの解析を含む）。
	 * メトリクス（要件 NF-O-04）とトレース（NF-O-05）は<b>切っても出る</b>。
	 * </p>
	 *
	 * @return	出す場合 = true
	 */
	public static boolean accessLog () {

		return ACCESS_LOG.get();

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
