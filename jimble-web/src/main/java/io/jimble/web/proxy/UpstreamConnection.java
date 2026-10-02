package io.jimble.web.proxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * 転送先への1回の転送（HTTP/1.1 をソケットで直に話す。要件 F-R-14）
 *
 * <h2>なぜ {@code HttpClient} を使わないのか</h2>
 * <p>
 * <b>{@code Host} を決められないため。</b>{@code java.net.http.HttpClient} は {@code Host} を「制限ヘッダ」として拒み、
 * 転送先の URL から自分で付け直す。nginx の {@code proxy_set_header Host $http_host} に当たる
 * {@link ReverseProxy#preserveHost()} が作れない。制限を外す {@code jdk.httpclient.allowRestrictedHeaders} は
 * クラスを読んだときに1度だけ読まれるシステムプロパティで、ほかのライブラリが先に {@code HttpClient} を使っていれば効かない。
 * </p>
 *
 * <h2>やること</h2>
 * <ul>
 *   <li><b>1回ごとに繋いで、{@code Connection: close} で切る</b>（nginx の既定と同じ。転送先との keepalive は持たない）</li>
 *   <li>{@code https} の転送先には TLS をかぶせる（証明書のホスト名を確かめる。SNI も送る）</li>
 *   <li>本文は、元に {@code Content-Length} があればその長さで、無ければ chunked で流す（読み切らない）</li>
 *   <li>応答は {@code Content-Length} / {@code chunked} / 切れるまで、のどれでも読む。1xx（103 など）は読み捨てる</li>
 * </ul>
 *
 * <h2>待ちの上限（D-258）</h2>
 * <ul>
 *   <li><b>送るあいだ</b>：{@code request_timeout} のあいだ1バイトも進まなければ切る（書き込みには、ソケットのタイムアウトが効かない）</li>
 *   <li><b>送り終えてから、応答のヘッダが届くまで</b>：全体で {@code request_timeout}</li>
 *   <li><b>応答の本文</b>：1回の読み込みごとに {@code request_timeout}。全体は {@code body_timeout}（既定 0 ＝ 上限なし）</li>
 * </ul>
 * <p>
 * かつては「1回の読み込みごと」だけだったので、転送先が 29 秒ごとに1バイトずつ返すと、いつまでも終わらなかった。
 * 本文の全体を既定で切らないのは、大きなダウンロードや SSE を途中で切らないためである。
 * </p>
 *
 * <p>
 * jimbleRun の開発用プロキシ（gradle-plugin の {@code AppConnection}）と同じ作りである。
 * モジュールが違う（gradle-plugin は jimble-web に依存しない）ので、写してある。
 * </p>
 */
final class UpstreamConnection implements Closeable {

	/** ヘッダの行の上限（転送先が壊れた応答を返しても、メモリを食い潰さない） */
	private static final int MAX_LINE = 64 * 1024;

	/** ヘッダの数の上限 */
	private static final int MAX_HEADERS = 500;

	/* 接続 */
	private final Socket socket;

	/* 状態コード */
	private final int status;

	/* 応答のヘッダ（名前は受け取ったまま。同じ名前が複数あれば並べる） */
	private final Map<String, List<String>> headers;

	/* 応答の本文 */
	private final InputStream body;

	/* 期限の見張り */
	private final Watchdog watchdog;

	private UpstreamConnection (Socket socket, int status, Map<String, List<String>> headers, InputStream body, Watchdog watchdog) {

		this.socket = socket;
		this.status = status;
		this.headers = headers;
		this.body = body;
		this.watchdog = watchdog;

	}

	/**
	 * 送って、応答のヘッダまで読む
	 *
	 * @param secure			https の場合 = true（TLS をかぶせる）
	 * @param host				繋ぐ先
	 * @param port				繋ぐ先のポート
	 * @param connectTimeout	繋ぐまでの待ち
	 * @param readTimeout		送るときに進まない待ち・ヘッダが届くまでの全体の待ち・本文の1回の読み込みの待ち
	 * @param bodyTimeout		本文の全体の待ち（0 なら上限なし）
	 * @param method			メソッド
	 * @param target			パスとクエリ（{@code /a?b=c}）
	 * @param requestHeaders	送るヘッダ（hop-by-hop は呼ぶ側で落としておく。{@code Host} は含めてよい）
	 * @param requestBody		本文（無ければ {@code null}）
	 * @param contentLength		本文の長さ。分からなければ -1（chunked で送る）
	 * @return	応答（本文はまだ読んでいない。閉じること）
	 * @throws IOException	送れない・読めない場合
	 */
	static UpstreamConnection send (boolean secure, String host, int port, Duration connectTimeout, Duration readTimeout
		, Duration bodyTimeout, String method, String target, Map<String, List<String>> requestHeaders
		, InputStream requestBody, long contentLength) throws IOException {

		Socket socket = new Socket();
		Watchdog watchdog = null;

		try {

			socket.connect(new InetSocketAddress(host, port), (int) connectTimeout.toMillis());
			socket.setSoTimeout((int) readTimeout.toMillis());

			if (secure) {
				socket = tls(socket, host, port);
			}

			watchdog = new Watchdog(socket);

			// 送るあいだ：進まなければ切る
			watchdog.idle(readTimeout);

			Watchdog progress = watchdog;
			OutputStream out = new BufferedOutputStream(new java.io.FilterOutputStream(socket.getOutputStream()) {
				@Override
				public void write (int b) throws IOException {
					out.write(b);
					progress.progress();
				}

				@Override
				public void write (byte[] b, int off, int len) throws IOException {
					out.write(b, off, len);
					progress.progress();
				}
			});

			writeRequest(out, method, target, requestHeaders, requestBody, contentLength);

			// 送り終えた。ヘッダが届くまでの全体の期限
			watchdog.idle(Duration.ZERO);
			watchdog.deadline(readTimeout, "応答のヘッダが request_timeout までに届きませんでした");

			InputStream in = new BufferedInputStream(socket.getInputStream());

			UpstreamConnection response = readResponse(socket, in, method, watchdog);

			// 本文：全体の期限（0 なら上限なし）。1回の読み込みごとの待ちはソケットのタイムアウトが見る
			watchdog.deadline(bodyTimeout, "応答の本文が body_timeout までに終わりませんでした");

			return response;

		} catch (IOException | RuntimeException ex) {

			socket.close();

			if (watchdog != null) {
				watchdog.close();
				if (watchdog.fired() != null) {
					throw new java.net.SocketTimeoutException(watchdog.fired());
				}
			}

			throw ex;

		}

	}

	/**
	 * 状態コード
	 *
	 * @return	状態コード
	 */
	int status () {

		return status;

	}

	/**
	 * 応答のヘッダ
	 *
	 * @return	ヘッダ
	 */
	Map<String, List<String>> headers () {

		return headers;

	}

	/**
	 * 応答の本文（{@code chunked} は解いてある）
	 *
	 * @return	本文
	 */
	InputStream body () {

		return body;

	}

	/**
	 * 期限で切ったときの理由
	 *
	 * @return	理由（切っていなければ null）
	 */
	String timedOut () {

		return watchdog.fired();

	}

	@Override
	public void close () throws IOException {

		watchdog.close();
		socket.close();

	}

	/**
	 * 期限が来たらソケットを閉じる見張り（D-258）
	 *
	 * <p>
	 * 1回の転送に1本の仮想スレッドを使う。閉じれば、読み書きしているほうは例外で抜ける。
	 * </p>
	 */
	static final class Watchdog implements Closeable {

		/* 見張るソケット */
		private final Socket socket;

		/* 見張りのスレッド */
		private final Thread thread;

		/* 進まない待ちの上限（ナノ秒。0 なら見ない） */
		private volatile long idleNanos = 0;

		/* 最後に進んだ時刻 */
		private volatile long lastProgress = System.nanoTime();

		/* 全体の期限（System.nanoTime の時刻） */
		private volatile long deadline = 0;

		/* 全体の期限があるか */
		private volatile boolean hasDeadline = false;

		/* 全体の期限で切ったときの理由 */
		private volatile String deadlineReason = "";

		/* 閉じたか */
		private volatile boolean closed = false;

		/* 切った理由（切っていなければ null） */
		private volatile String fired = null;

		Watchdog (Socket socket) {

			this.socket = socket;
			this.thread = Thread.ofVirtual().name("jimble-proxy-watchdog").start(this::run);

		}

		/**
		 * 進んだ
		 */
		void progress () {

			lastProgress = System.nanoTime();

		}

		/**
		 * 進まない待ちの上限を決める
		 *
		 * @param idle	上限（0 なら見ない）
		 */
		void idle (Duration idle) {

			lastProgress = System.nanoTime();
			idleNanos = idle == null || idle.isNegative() ? 0 : idle.toNanos();
			LockSupport.unpark(thread);

		}

		/**
		 * いまから数えた全体の期限を決める
		 *
		 * @param timeout	期限（0 なら上限なし）
		 * @param reason	期限で切ったときの理由
		 */
		void deadline (Duration timeout, String reason) {

			deadlineReason = reason;

			if (timeout == null || timeout.isZero() || timeout.isNegative()) {
				hasDeadline = false;
			} else {
				deadline = System.nanoTime() + timeout.toNanos();
				hasDeadline = true;
			}

			LockSupport.unpark(thread);

		}

		/**
		 * 切った理由
		 *
		 * @return	理由（切っていなければ null）
		 */
		String fired () {

			return fired;

		}

		@Override
		public void close () {

			closed = true;
			LockSupport.unpark(thread);

		}

		private void run () {

			while (!closed) {

				long now = System.nanoTime();
				long wait = Long.MAX_VALUE;

				if (hasDeadline) {
					long left = deadline - now;
					if (left <= 0) {
						fire(deadlineReason);
						return;
					}
					wait = left;
				}

				long idle = idleNanos;

				if (idle > 0) {
					long left = lastProgress + idle - now;
					if (left <= 0) {
						fire("リクエストの本文を送るのが、request_timeout のあいだ進みませんでした");
						return;
					}
					wait = Math.min(wait, left);
				}

				if (wait == Long.MAX_VALUE) {
					LockSupport.park(this);
				} else {
					LockSupport.parkNanos(this, wait);
				}

			}

		}

		private void fire (String reason) {

			if (closed) {
				return;
			}

			fired = reason;

			try {
				socket.close();
			} catch (IOException ignore) {
				// 閉じられなくても、もう使わない
			}

		}

	}

	/**
	 * TLS をかぶせる（証明書のホスト名を確かめ、SNI を送る）
	 *
	 * @param plain	繋いだソケット
	 * @param host	転送先のホスト
	 * @param port	転送先のポート
	 * @return	TLS のソケット
	 * @throws IOException	握手に失敗した場合
	 */
	private static Socket tls (Socket plain, String host, int port) throws IOException {

		javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket)
			((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);

		javax.net.ssl.SSLParameters parameters = ssl.getSSLParameters();
		// 証明書が転送先のホスト名のものかを確かめる（確かめないと、途中の誰にでも中身を読まれる）
		parameters.setEndpointIdentificationAlgorithm("HTTPS");
		ssl.setSSLParameters(parameters);

		ssl.startHandshake();

		return ssl;

	}

	// region 送る

	/**
	 * メソッドやヘッダ名に使える字だけか（RFC 9110 の token）
	 */
	private static void requireToken (String value, String what) {

		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(what + "が空です");
		}

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
				|| "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
			if (!ok) {
				throw new IllegalArgumentException(what + "に使えない字があります");
			}
		}

	}

	/**
	 * 要求行のパスに、空白・制御文字・# が無いか
	 */
	private static void requireTarget (String target) {

		if (target == null || target.isEmpty() || target.charAt(0) != '/') {
			throw new IllegalArgumentException("転送先のパスが / で始まっていません");
		}

		for (int i = 0; i < target.length(); i++) {
			char c = target.charAt(i);
			if (c <= 0x20 || c == 0x7f || c == '#') {
				throw new IllegalArgumentException("転送先のパスに使えない字があります");
			}
		}

	}

	/**
	 * ヘッダの値に CR / LF / NUL が無いか
	 */
	private static void requireHeaderValue (String name, String value) {

		if (value == null) {
			return;
		}

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\r' || c == '\n' || c == 0) {
				throw new IllegalArgumentException("ヘッダ " + name + " の値に改行があります");
			}
		}

	}

	/**
	 * リクエストを書く
	 */
	private static void writeRequest (OutputStream out, String method, String target
		, Map<String, List<String>> requestHeaders, InputStream requestBody, long contentLength) throws IOException {

		/*
		 * <b>書く前に、要求行とヘッダを確かめる</b>（D-203）。ここはソケットに直に書くので、
		 * 改行が混ざれば<b>そのまま2本目のリクエスト</b>になる。呼ぶ側で防いでいても、最後にもう一度見る
		 */
		requireToken(method, "メソッド");
		requireTarget(target);

		StringBuilder head = new StringBuilder();
		head.append(method).append(' ').append(target).append(" HTTP/1.1\r\n");

		for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
			requireToken(entry.getKey(), "ヘッダ名");
			for (String value : entry.getValue()) {
				requireHeaderValue(entry.getKey(), value);
				head.append(entry.getKey()).append(": ").append(value).append("\r\n");
			}
		}

		if (requestBody != null) {
			if (contentLength >= 0) {
				head.append("Content-Length: ").append(contentLength).append("\r\n");
			} else {
				head.append("Transfer-Encoding: chunked\r\n");
			}
		}

		head.append("Connection: close\r\n\r\n");

		out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));

		if (requestBody != null) {
			if (contentLength >= 0) {
				copy(requestBody, out, contentLength);
			} else {
				writeChunked(requestBody, out);
			}
		}

		out.flush();

	}

	/**
	 * 決まった長さだけ流す
	 */
	private static void copy (InputStream in, OutputStream out, long length) throws IOException {

		byte[] buffer = new byte[8192];
		long remaining = length;

		while (remaining > 0) {

			int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));

			if (read < 0) {
				throw new EOFException("本文が Content-Length より短く終わりました（あと %d バイト）".formatted(remaining));
			}

			out.write(buffer, 0, read);
			remaining -= read;

		}

	}

	/**
	 * chunked で流す
	 */
	private static void writeChunked (InputStream in, OutputStream out) throws IOException {

		byte[] buffer = new byte[8192];

		for (int read; (read = in.read(buffer)) >= 0; ) {

			if (read == 0) {
				continue;
			}

			out.write((Integer.toHexString(read) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
			out.write(buffer, 0, read);
			out.write(CRLF);

		}

		out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));

	}

	/** 改行 */
	private static final byte[] CRLF = { '\r', '\n' };

	// endregion

	// region 読む

	/**
	 * 応答のヘッダまで読む
	 */
	private static UpstreamConnection readResponse (Socket socket, InputStream in, String method, Watchdog watchdog) throws IOException {

		while (true) {

			int status = parseStatus(readLine(in));
			Map<String, List<String>> headers = readHeaders(in);

			/*
			 * <b>1xx は読み捨てて、本当の応答を待つ</b>（103 Early Hints など）。
			 * Expect は送っていないので 100 はふつう来ない。101 は昇格なので、ここでは扱わない（本文なしで返す）。
			 */
			if (status >= 100 && status < 200 && status != 101) {
				continue;
			}

			return new UpstreamConnection(socket, status, headers, bodyOf(in, method, status, headers), watchdog);

		}

	}

	/**
	 * 本文の読み方を決める
	 */
	private static InputStream bodyOf (InputStream in, String method, int status, Map<String, List<String>> headers) {

		if ("HEAD".equals(method) || status < 200 || status == 204 || status == 304) {
			return InputStream.nullInputStream();
		}

		String transferEncoding = first(headers, "transfer-encoding");

		if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
			return new ChunkedInputStream(in);
		}

		String contentLength = first(headers, "content-length");

		if (contentLength != null) {
			return new BoundedInputStream(in, Long.parseLong(contentLength.trim()));
		}

		// 長さが無い：切れるまで（Connection: close で頼んでいる）
		return in;

	}

	/**
	 * 状態行から状態コードを取り出す（{@code HTTP/1.1 200 OK}）
	 */
	private static int parseStatus (String line) throws IOException {

		String[] parts = line.split(" ", 3);

		if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
			throw new IOException("転送先の応答が HTTP ではありません: " + line);
		}

		try {
			return Integer.parseInt(parts[1]);
		} catch (NumberFormatException ex) {
			throw new IOException("転送先の応答の状態コードが読めません: " + line, ex);
		}

	}

	/**
	 * ヘッダを空行まで読む
	 */
	private static Map<String, List<String>> readHeaders (InputStream in) throws IOException {

		Map<String, List<String>> headers = new LinkedHashMap<>();

		for (String line; !(line = readLine(in)).isEmpty(); ) {

			if (headers.size() >= MAX_HEADERS) {
				throw new IOException("転送先の応答のヘッダが多すぎます");
			}

			int colon = line.indexOf(':');

			if (colon <= 0) {
				throw new IOException("転送先の応答のヘッダが読めません: " + line);
			}

			headers.computeIfAbsent(line.substring(0, colon).trim(), k -> new ArrayList<>())
				.add(line.substring(colon + 1).trim());

		}

		return headers;

	}

	/**
	 * 名前（大文字小文字を問わない）で最初の値
	 */
	private static String first (Map<String, List<String>> headers, String name) {

		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
				return entry.getValue().get(0);
			}
		}

		return null;

	}

	/**
	 * 1行読む（CRLF か LF まで。改行は含めない）
	 */
	static String readLine (InputStream in) throws IOException {

		ByteArrayOutputStream line = new ByteArrayOutputStream();

		for (int c; (c = in.read()) != '\n'; ) {

			if (c < 0) {
				throw new EOFException("転送先が応答の途中で接続を切りました");
			}

			if (line.size() >= MAX_LINE) {
				throw new IOException("転送先の応答の1行が長すぎます");
			}

			line.write(c);

		}

		byte[] bytes = line.toByteArray();
		int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;

		return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);

	}

	// endregion

	// region 本文の読み方

	/**
	 * 決まった長さで終わる本文
	 */
	private static final class BoundedInputStream extends FilterInputStream {

		/* 残り */
		private long remaining;

		BoundedInputStream (InputStream in, long length) {

			super(in);
			this.remaining = length;

		}

		@Override
		public int read () throws IOException {

			if (remaining <= 0) {
				return -1;
			}

			int c = in.read();

			if (c >= 0) {
				remaining--;
			}

			return c;

		}

		@Override
		public int read (byte[] b, int off, int len) throws IOException {

			if (remaining <= 0) {
				return -1;
			}

			int read = in.read(b, off, (int) Math.min(len, remaining));

			if (read > 0) {
				remaining -= read;
			}

			return read;

		}

	}

	/**
	 * chunked を解いた本文
	 */
	static final class ChunkedInputStream extends FilterInputStream {

		/* いまの塊の残り */
		private long remaining = 0;

		/* 終わったか */
		private boolean done = false;

		ChunkedInputStream (InputStream in) {

			super(in);

		}

		@Override
		public int read () throws IOException {

			byte[] one = new byte[1];

			return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;

		}

		@Override
		public int read (byte[] b, int off, int len) throws IOException {

			if (done) {
				return -1;
			}

			if (remaining == 0 && !nextChunk()) {
				return -1;
			}

			int read = in.read(b, off, (int) Math.min(len, remaining));

			if (read < 0) {
				throw new EOFException("転送先が chunked の本文の途中で接続を切りました");
			}

			remaining -= read;

			if (remaining == 0) {
				// 塊のあとの CRLF
				readLine(in);
			}

			return read;

		}

		/**
		 * 次の塊の大きさを読む
		 *
		 * @return	まだある場合 = true
		 */
		private boolean nextChunk () throws IOException {

			String line = readLine(in);
			int semicolon = line.indexOf(';');
			String size = (semicolon < 0 ? line : line.substring(0, semicolon)).trim();

			try {
				remaining = Long.parseLong(size, 16);
			} catch (NumberFormatException ex) {
				throw new IOException("転送先の chunked の大きさが読めません: " + line, ex);
			}

			if (remaining == 0) {
				// トレーラーを読み捨てる
				while (!readLine(in).isEmpty()) {
					// 何もしない
				}
				done = true;
				return false;
			}

			return true;

		}

	}

	// endregion

}
