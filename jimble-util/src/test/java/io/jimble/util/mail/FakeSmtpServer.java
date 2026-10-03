package io.jimble.util.mail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * テスト用の SMTP サーバー（1回だけ受ける）
 */
final class FakeSmtpServer implements AutoCloseable {

	/* 受け取ったコマンド（AUTH の中身は解いて入れる） */
	final List<String> commands = Collections.synchronizedList(new ArrayList<>());

	/* 受け取った本文（「.」の重ねを戻したもの） */
	volatile byte[] data;

	/* AUTH で受け取った利用者名とパスワード */
	volatile String username;
	volatile String password;

	/* TLS の上で認証されたか */
	volatile boolean authenticatedOverTls;

	/* 宛先ごとの答え（無ければ 250） */
	final Map<String, String> rcptReplies = new ConcurrentHashMap<>();

	/* 対応を名乗る拡張 */
	volatile List<String> extensions = List.of("PIPELINING", "8BITMIME");

	/* 認証の答え */
	volatile String authReply = "235 2.7.0 ok";

	/* 最初から TLS（SMTPS） */
	private final boolean implicitTls;

	private final SSLContext ssl;

	private final ServerSocket server;

	private final Thread thread;

	FakeSmtpServer (SSLContext ssl, boolean implicitTls) throws Exception {

		this.ssl = ssl;
		this.implicitTls = implicitTls;
		this.server = implicitTls
			? ssl.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress())
			: new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		this.thread = Thread.ofVirtual().start(this::serve);

	}

	int port () {

		return server.getLocalPort();

	}

	private void serve () {

		try (Socket accepted = server.accept()) {

			Socket socket = accepted;
			boolean tls = implicitTls;
			BufferedReader in = reader(socket);
			OutputStream out = socket.getOutputStream();

			reply(out, "220 fake.smtp ESMTP");

			while (true) {

				String line = in.readLine();

				if (line == null) {
					return;
				}

				String upper = line.toUpperCase();

				if (upper.startsWith("AUTH PLAIN ")) {
					String[] parts = new String(Base64.getDecoder().decode(line.substring(11)), StandardCharsets.UTF_8).split("\0", -1);
					username = parts[1];
					password = parts[2];
					authenticatedOverTls = tls;
					commands.add("AUTH PLAIN");
					reply(out, authReply);
					continue;
				}

				commands.add(line);

				if (upper.startsWith("EHLO")) {
					List<String> lines = new ArrayList<>();
					lines.add("fake.smtp hello");
					lines.addAll(tls ? extensions.stream().filter(e -> !e.equals("STARTTLS")).toList() : extensions);
					for (int i = 0; i < lines.size(); i++) {
						reply(out, "250" + (i == lines.size() - 1 ? " " : "-") + lines.get(i));
					}
				} else if (upper.equals("STARTTLS")) {
					reply(out, "220 2.0.0 go ahead");
					SSLSocket secured = (SSLSocket) ssl.getSocketFactory().createSocket(socket, null, socket.getPort(), false);
					secured.setUseClientMode(false);
					secured.startHandshake();
					socket = secured;
					tls = true;
					in = reader(socket);
					out = socket.getOutputStream();
				} else if (upper.equals("AUTH LOGIN")) {
					reply(out, "334 VXNlcm5hbWU6");
					username = new String(Base64.getDecoder().decode(in.readLine()), StandardCharsets.UTF_8);
					reply(out, "334 UGFzc3dvcmQ6");
					password = new String(Base64.getDecoder().decode(in.readLine()), StandardCharsets.UTF_8);
					authenticatedOverTls = tls;
					reply(out, authReply);
				} else if (upper.startsWith("MAIL FROM:")) {
					reply(out, "250 2.1.0 ok");
				} else if (upper.startsWith("RCPT TO:")) {
					String address = line.substring(line.indexOf('<') + 1, line.indexOf('>'));
					reply(out, rcptReplies.getOrDefault(address, "250 2.1.5 ok"));
				} else if (upper.equals("DATA")) {
					reply(out, "354 end with .");
					data = readData(socket.getInputStream(), in);
					reply(out, "250 2.0.0 queued");
				} else if (upper.equals("RSET")) {
					reply(out, "250 ok");
				} else if (upper.equals("QUIT")) {
					reply(out, "221 bye");
					return;
				} else {
					reply(out, "502 unknown");
				}

			}

		} catch (Exception ignore) {
			// クライアントが切った・TLS が合わなかった
		}

	}

	private static BufferedReader reader (Socket socket) throws Exception {

		return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

	}

	/**
	 * 「.」だけの行まで読み、行頭の「..」を「.」に戻す
	 */
	private static byte[] readData (InputStream raw, BufferedReader in) throws Exception {

		ByteArrayOutputStream body = new ByteArrayOutputStream();

		for (String line; (line = in.readLine()) != null; ) {
			if (line.equals(".")) {
				break;
			}
			body.writeBytes((line.startsWith("..") ? line.substring(1) : line).getBytes(StandardCharsets.UTF_8));
			body.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
		}

		return body.toByteArray();

	}

	private static void reply (OutputStream out, String line) throws Exception {

		out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.flush();

	}

	/**
	 * やり取りが終わるのを待つ（クライアントが QUIT するか切るまで）
	 */
	void await () {

		try {
			thread.join(5000);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	@Override
	public void close () {

		try {
			server.close();
		} catch (java.io.IOException ignore) {
			// もう閉じている
		}

		await();

	}

}
