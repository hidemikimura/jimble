package io.jimble.util.mail;

import io.jimble.util.log.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SMTP で送る（RFC 5321。D-263）
 *
 * <ul>
 *   <li>暗号化は STARTTLS（587）・最初から TLS（465）・なし（25。手元の MailHog などだけ）</li>
 *   <li>TLS では<b>証明書のホスト名を確かめる</b>。STARTTLS を選んだのに相手が対応していなければ、<b>暗号化せずに続けず</b>断る</li>
 *   <li>認証は AUTH PLAIN か AUTH LOGIN。<b>暗号化しない接続では認証しない</b>（パスワードが平文で流れる）</li>
 *   <li>1通ごとに繋いで、送ったら切る</li>
 * </ul>
 *
 * <p>
 * Amazon SES・SendGrid・Google Workspace などは、どれも SMTP で送れる（STARTTLS の 587 と、利用者名・パスワード）。
 * </p>
 */
public final class SmtpTransport implements MailTransport {

	/** 応答の1行の上限（相手が壊れていても、メモリを食い潰さない） */
	private static final int MAX_LINE = 8192;

	/** 1つの応答の行数の上限 */
	private static final int MAX_LINES = 200;

	/**
	 * 暗号化
	 */
	public enum Security {

		/** 平文で繋いでから STARTTLS で暗号化する（587） */
		STARTTLS(587),

		/** 最初から TLS（465。SMTPS） */
		TLS(465),

		/** 暗号化しない（25。手元のテスト用の SMTP サーバーだけ） */
		NONE(25);

		private final int defaultPort;

		Security (int defaultPort) {

			this.defaultPort = defaultPort;

		}

		/**
		 * 既定のポート
		 *
		 * @return	ポート
		 */
		public int defaultPort () {

			return defaultPort;

		}

		/**
		 * 設定の値から
		 *
		 * @param value	{@code starttls} / {@code tls} / {@code none}
		 * @return	暗号化
		 */
		public static Security of (String value) {

			return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
				case "starttls" -> STARTTLS;
				case "tls", "ssl", "smtps" -> TLS;
				case "none" -> NONE;
				default -> throw new MailException("mail.smtp.security は starttls / tls / none のどれかにしてください: " + value);
			};

		}

	}

	/**
	 * SMTP の設定
	 *
	 * @param host				ホスト
	 * @param port				ポート
	 * @param security			暗号化
	 * @param username			利用者名（空なら認証しない）
	 * @param password			パスワード
	 * @param connectTimeout	繋ぐまでの待ち
	 * @param timeout			1回の読み書きの待ち
	 * @param helo				EHLO で名乗る名前（空ならこの機械の名前）
	 * @param envelopeFrom		エンベロープの差出人（null なら From）
	 */
	public record Settings(String host, int port, Security security, String username, String password
		, Duration connectTimeout, Duration timeout, String helo, MailAddress envelopeFrom) {

		/**
		 * 作る（形を確かめる）
		 *
		 * @throws MailException	暗号化せずに認証しようとしている場合など
		 */
		public Settings {

			if (host == null || host.isBlank() || host.contains("/") || host.contains(" ")) {
				throw new MailException("SMTP のホストの形が違います: " + host);
			}

			if (port <= 0 || port > 65535) {
				throw new MailException("SMTP のポートが違います: " + port);
			}

			username = username == null ? "" : username;
			password = password == null ? "" : password;
			helo = helo == null ? "" : helo;

			if (security == Security.NONE && !username.isEmpty()) {
				throw new MailException("暗号化しない SMTP（mail.smtp.security = none）では認証できません（パスワードが平文で流れます）。starttls か tls にしてください");
			}

		}

		@Override
		public String toString () {

			// パスワードを出さない
			return "smtp://%s:%d（%s%s）".formatted(host, port, security, username.isEmpty() ? "" : "、" + username);

		}

	}

	/* 設定 */
	private final Settings settings;

	/* TLS を張るもの（テストで差し替える） */
	private final SSLSocketFactory tls;

	/**
	 * 作る
	 *
	 * @param settings	設定
	 */
	public SmtpTransport (Settings settings) {

		this(settings, (SSLSocketFactory) SSLSocketFactory.getDefault());

	}

	/**
	 * 作る（TLS を張るものを渡す。自前の CA を信じるときやテスト）
	 *
	 * @param settings	設定
	 * @param tls		TLS を張るもの
	 */
	public SmtpTransport (Settings settings, SSLSocketFactory tls) {

		this.settings = settings;
		this.tls = tls;

	}

	@Override
	public void send (MailMessage message) {

		MailAddress sender = message.check(null);
		byte[] mime = MimeWriter.write(message, sender);
		MailAddress envelopeFrom = settings.envelopeFrom() != null ? settings.envelopeFrom() : sender;
		List<MailAddress> recipients = message.recipients();

		try (Session session = new Session(connect())) {

			session.expect(220, "接続の挨拶");

			Set<String> extensions = session.ehlo(heloName());

			if (settings.security() == Security.STARTTLS) {

				if (!extensions.contains("STARTTLS")) {
					throw new MailException("SMTP サーバーが STARTTLS に対応していません（暗号化せずには続けません）: " + settings
						, 0, false, null);
				}

				session.command("STARTTLS");
				session.expect(220, "STARTTLS");
				session.upgrade(wrap(session.socket));

				// 暗号化したあとは、名乗り直して対応を聞き直す（平文のときの答えは信じない）
				extensions = session.ehlo(heloName());

			}

			if (!settings.username().isEmpty()) {
				authenticate(session, extensions);
			}

			session.command("MAIL FROM:<" + envelopeFrom.address() + ">");
			session.expect(250, "MAIL FROM");

			List<String> refused = new ArrayList<>();
			boolean anyTransient = false;

			for (MailAddress recipient : recipients) {

				session.command("RCPT TO:<" + recipient.address() + ">");
				Reply reply = session.read();

				if (reply.code() / 100 != 2) {
					refused.add(recipient.address() + "（" + reply + "）");
					anyTransient |= reply.code() / 100 == 4;
				}

			}

			/*
			 * <b>1人でも断られたら、誰にも送らない</b>。一部にだけ届くと、
			 * やり直したときに届いた人へもう一度送ることになる
			 */
			if (!refused.isEmpty()) {
				session.quietly("RSET");
				throw new MailException("宛先を断られました: " + String.join(", ", refused), 0, anyTransient, null);
			}

			session.command("DATA");
			session.expect(354, "DATA");
			session.writeData(mime);
			session.expect(250, "本文");

			session.quietly("QUIT");

		} catch (MailException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new MailException("SMTP サーバーとの通信に失敗しました: %s / %s".formatted(settings, ex.getMessage()), 0, true, ex);
		}

		Log.info("メールを送りました（宛先 %d 件）: %s".formatted(recipients.size(), settings));

	}

	// region 接続

	private Socket connect () throws IOException {

		Socket plain = new Socket();

		try {
			plain.connect(new InetSocketAddress(settings.host(), settings.port()), (int) settings.connectTimeout().toMillis());
			plain.setSoTimeout((int) settings.timeout().toMillis());
		} catch (IOException ex) {
			plain.close();
			throw ex;
		}

		return settings.security() == Security.TLS ? wrap(plain) : plain;

	}

	/**
	 * TLS をかぶせる（証明書のホスト名を確かめ、SNI を送る）
	 */
	private SSLSocket wrap (Socket plain) throws IOException {

		SSLSocket socket = (SSLSocket) tls.createSocket(plain, settings.host(), settings.port(), true);

		SSLParameters parameters = socket.getSSLParameters();
		parameters.setEndpointIdentificationAlgorithm("HTTPS");

		if (!settings.host().matches("[0-9.:]+")) {
			parameters.setServerNames(List.of(new SNIHostName(settings.host())));
		}

		socket.setSSLParameters(parameters);
		socket.startHandshake();

		return socket;

	}

	private String heloName () {

		if (!settings.helo().isEmpty()) {
			return settings.helo();
		}

		try {
			String name = InetAddress.getLocalHost().getCanonicalHostName();
			return name == null || name.isBlank() || name.contains(" ") ? "localhost" : name;
		} catch (IOException ex) {
			return "localhost";
		}

	}

	// endregion

	// region 認証

	private void authenticate (Session session, Set<String> extensions) throws IOException {

		Set<String> mechanisms = new TreeSet<>();

		for (String extension : extensions) {
			if (extension.startsWith("AUTH ") || extension.startsWith("AUTH=")) {
				for (String mechanism : extension.substring(5).trim().split("\\s+")) {
					mechanisms.add(mechanism.toUpperCase(Locale.ROOT));
				}
			}
		}

		Base64.Encoder b64 = Base64.getEncoder();

		if (mechanisms.contains("PLAIN")) {

			byte[] credentials = ("\0" + settings.username() + "\0" + settings.password()).getBytes(StandardCharsets.UTF_8);
			session.command("AUTH PLAIN " + b64.encodeToString(credentials), "AUTH PLAIN ****");

		} else if (mechanisms.contains("LOGIN")) {

			session.command("AUTH LOGIN");
			session.expect(334, "AUTH LOGIN");
			session.command(b64.encodeToString(settings.username().getBytes(StandardCharsets.UTF_8)), "****");
			session.expect(334, "AUTH LOGIN（利用者名）");
			session.command(b64.encodeToString(settings.password().getBytes(StandardCharsets.UTF_8)), "****");

		} else {
			throw new MailException("SMTP サーバーが AUTH PLAIN / LOGIN に対応していません: " + settings + " / " + mechanisms);
		}

		session.expect(235, "認証");

	}

	// endregion

	// region やり取り

	/**
	 * DATA の本文を書く（行頭の「.」を重ね、最後に「.」だけの行。RFC 5321 4.5.2）
	 *
	 * @param out	書く先
	 * @param mime	MIME の形のメール（CRLF）
	 * @throws IOException	書けない場合
	 */
	static void writeDotStuffed (OutputStream out, byte[] mime) throws IOException {

		boolean lineStart = true;

		for (byte b : mime) {

			if (lineStart && b == '.') {
				out.write('.');
			}

			out.write(b);
			lineStart = b == '\n';

		}

		if (!lineStart) {
			out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
		}

		out.write(".\r\n".getBytes(StandardCharsets.US_ASCII));

	}

	/**
	 * 応答
	 *
	 * @param code	コード
	 * @param lines	行（コードを除いたもの）
	 */
	record Reply(int code, List<String> lines) {

		@Override
		public String toString () {

			return code + " " + String.join(" / ", lines);

		}

	}

	/**
	 * 1回の接続
	 */
	private static final class Session implements AutoCloseable {

		private Socket socket;

		private InputStream in;

		private OutputStream out;

		Session (Socket socket) throws IOException {

			upgrade(socket);

		}

		void upgrade (Socket socket) throws IOException {

			this.socket = socket;
			this.in = new BufferedInputStream(socket.getInputStream());
			this.out = new BufferedOutputStream(socket.getOutputStream());

		}

		Set<String> ehlo (String name) throws IOException {

			command("EHLO " + name);
			Reply reply = expect(250, "EHLO");

			Set<String> extensions = new TreeSet<>();

			// 1行目は挨拶。2行目からが対応している拡張
			for (String line : reply.lines().subList(1, reply.lines().size())) {
				extensions.add(line.trim().toUpperCase(Locale.ROOT));
			}

			return extensions;

		}

		void command (String line) throws IOException {

			command(line, line);

		}

		/**
		 * 1行送る（改行は入れさせない）
		 *
		 * @param line		送る行
		 * @param printable	失敗したときにメッセージに出す形（パスワードを出さない）
		 */
		void command (String line, String printable) throws IOException {

			if (line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
				throw new MailException("SMTP のコマンドに改行は入れられません: " + printable);
			}

			out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();

		}

		Reply expect (int code, String what) throws IOException {

			Reply reply = read();

			if (reply.code() != code) {
				throw new MailException("SMTP サーバーに断られました（%s）: %s".formatted(what, reply)
					, reply.code(), reply.code() / 100 == 4, null);
			}

			return reply;

		}

		/**
		 * 応答を読む（{@code 250-...} が続くものは、{@code 250 ...} の行まで）
		 */
		Reply read () throws IOException {

			List<String> lines = new ArrayList<>();
			int code = -1;

			while (true) {

				String line = readLine();

				if (line.length() < 3 || !line.substring(0, 3).chars().allMatch(Character::isDigit)) {
					throw new MailException("SMTP の応答が読めません: " + line, 0, true, null);
				}

				int lineCode = Integer.parseInt(line.substring(0, 3));

				if (code >= 0 && lineCode != code) {
					throw new MailException("SMTP の応答のコードが途中で変わりました: " + line, 0, true, null);
				}

				code = lineCode;
				lines.add(line.length() > 4 ? line.substring(4) : "");

				if (lines.size() > MAX_LINES) {
					throw new MailException("SMTP の応答が長すぎます", 0, true, null);
				}

				if (line.length() < 4 || line.charAt(3) != '-') {
					return new Reply(code, lines);
				}

			}

		}

		private String readLine () throws IOException {

			ByteArrayOutputStream line = new ByteArrayOutputStream();

			for (int c; (c = in.read()) != '\n'; ) {

				if (c < 0) {
					throw new IOException("SMTP サーバーが途中で接続を切りました");
				}

				if (line.size() >= MAX_LINE) {
					throw new IOException("SMTP の応答の行が長すぎます");
				}

				if (c != '\r') {
					line.write(c);
				}

			}

			return line.toString(StandardCharsets.UTF_8);

		}

		/**
		 * 本文を送る（行頭の「.」を重ね、最後に「.」だけの行）
		 */
		void writeData (byte[] mime) throws IOException {

			writeDotStuffed(out, mime);
			out.flush();

		}

		/**
		 * 答えを気にせず送る（RSET・QUIT）
		 */
		void quietly (String line) {

			try {
				command(line);
				read();
			} catch (IOException | MailException ignore) {
				// 終わりの挨拶は、届かなくても送れたものは送れている
			}

		}

		@Override
		public void close () throws IOException {

			socket.close();

		}

	}

	// endregion

}
