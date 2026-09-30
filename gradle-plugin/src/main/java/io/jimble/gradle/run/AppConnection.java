package io.jimble.gradle.run;

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

/**
 * アプリへの1回の転送（HTTP/1.1 をソケットで直に話す）
 *
 * <h2>なぜ {@code HttpClient} を使わないのか</h2>
 * <p>
 * <b>{@code Host} を引き継げないため。</b>{@code java.net.http.HttpClient} は {@code Host} を
 * 「制限ヘッダ」として拒み、接続先（{@code 127.0.0.1:9100}）から自分で付け直す。
 * するとアプリには<b>ブラウザが叩いたホスト（{@code localhost:9000} や {@code tenant1.localhost:9000}）が届かない</b>——
 * ホストで振り分けるアプリ・絶対 URL を組むアプリ・Origin を照らすアプリが、開発のときだけ食い違う。
 * </p>
 *
 * <p>
 * 制限を外す {@code jdk.httpclient.allowRestrictedHeaders} は<b>クラスを読んだときに1度だけ</b>読まれる
 * システムプロパティで、{@code jimbleRun} は Gradle と同じ JVM で動く（D-77）。Gradle やほかのプラグインが
 * 先に {@code HttpClient} を使っていれば効かないうえ、<b>Gradle のプロセス全体に効いてしまう</b>。
 * </p>
 *
 * <h2>やること</h2>
 * <ul>
 *   <li><b>1回ごとに繋いで、{@code Connection: close} で切る。</b>開発用なので接続の使い回しはしない
 *       （再起動のたびに古い接続を捨てる手間が要らない）</li>
 *   <li>本文は、元に {@code Content-Length} があればその長さで、{@code chunked} なら chunked で流す（読み切らない）</li>
 *   <li>応答は {@code Content-Length} / {@code chunked} / 切れるまで、のどれでも読む。1xx（103 など）は読み捨てる</li>
 * </ul>
 */
final class AppConnection implements Closeable {

	/** ヘッダの行の上限（アプリが壊れた応答を返しても、メモリを食い潰さない） */
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

	private AppConnection (Socket socket, int status, Map<String, List<String>> headers, InputStream body) {

		this.socket = socket;
		this.status = status;
		this.headers = headers;
		this.body = body;

	}

	/**
	 * 送って、応答のヘッダまで読む
	 *
	 * @param host				繋ぐ先
	 * @param port				繋ぐ先のポート
	 * @param connectTimeout	繋ぐまでの待ち
	 * @param readTimeout		読むときの待ち（作り直しを待つので長め）
	 * @param method			メソッド
	 * @param target			パスとクエリ（{@code /a?b=c}）
	 * @param requestHeaders	送るヘッダ（hop-by-hop は呼ぶ側で落としておく。{@code Host} は含めてよい）
	 * @param requestBody		本文（無ければ {@code null}）
	 * @param contentLength		本文の長さ。分からなければ -1（chunked で送る）
	 * @return	応答（本文はまだ読んでいない。閉じること）
	 * @throws IOException	送れない・読めない場合
	 */
	static AppConnection send (String host, int port, Duration connectTimeout, Duration readTimeout
		, String method, String target, Map<String, List<String>> requestHeaders
		, InputStream requestBody, long contentLength) throws IOException {

		Socket socket = new Socket();

		try {

			socket.connect(new InetSocketAddress(host, port), (int) connectTimeout.toMillis());
			socket.setSoTimeout((int) readTimeout.toMillis());

			OutputStream out = new BufferedOutputStream(socket.getOutputStream());

			writeRequest(out, method, target, requestHeaders, requestBody, contentLength);

			InputStream in = new BufferedInputStream(socket.getInputStream());

			return readResponse(socket, in, method);

		} catch (IOException | RuntimeException ex) {
			socket.close();
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

	@Override
	public void close () throws IOException {

		socket.close();

	}

	// region 送る

	/**
	 * リクエストを書く
	 */
	private static void writeRequest (OutputStream out, String method, String target
		, Map<String, List<String>> requestHeaders, InputStream requestBody, long contentLength) throws IOException {

		StringBuilder head = new StringBuilder();
		head.append(method).append(' ').append(target).append(" HTTP/1.1\r\n");

		for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
			for (String value : entry.getValue()) {
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
	private static AppConnection readResponse (Socket socket, InputStream in, String method) throws IOException {

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

			return new AppConnection(socket, status, headers, bodyOf(in, method, status, headers));

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
			throw new IOException("アプリの応答が HTTP ではありません: " + line);
		}

		try {
			return Integer.parseInt(parts[1]);
		} catch (NumberFormatException ex) {
			throw new IOException("アプリの応答の状態コードが読めません: " + line, ex);
		}

	}

	/**
	 * ヘッダを空行まで読む
	 */
	private static Map<String, List<String>> readHeaders (InputStream in) throws IOException {

		Map<String, List<String>> headers = new LinkedHashMap<>();

		for (String line; !(line = readLine(in)).isEmpty(); ) {

			if (headers.size() >= MAX_HEADERS) {
				throw new IOException("アプリの応答のヘッダが多すぎます");
			}

			int colon = line.indexOf(':');

			if (colon <= 0) {
				throw new IOException("アプリの応答のヘッダが読めません: " + line);
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
				throw new EOFException("アプリが応答の途中で接続を切りました");
			}

			if (line.size() >= MAX_LINE) {
				throw new IOException("アプリの応答の1行が長すぎます");
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
				throw new EOFException("アプリが chunked の本文の途中で接続を切りました");
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
				throw new IOException("アプリの chunked の大きさが読めません: " + line, ex);
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
