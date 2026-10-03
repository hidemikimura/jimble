package io.jimble.util.mail;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SMTP で送る（D-263。テスト用の SMTP サーバーと話す）
 */
class SmtpTransportTest {

	/* サーバーの鍵と証明書（CN=localhost、SAN=localhost） */
	private static SSLContext serverSsl;

	/* その証明書だけを信じるクライアント */
	private static SSLContext clientSsl;

	@BeforeAll
	static void certificate (@TempDir Path dir) throws Exception {

		Path keystore = dir.resolve("smtp.p12");
		String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();

		Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "smtp", "-keyalg", "EC", "-groupname", "secp256r1"
			, "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "2", "-storetype", "PKCS12"
			, "-storepass", "changeit", "-keystore", keystore.toString()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.waitFor(), output);

		KeyStore store = KeyStore.getInstance("PKCS12");
		try (FileInputStream in = new FileInputStream(keystore.toFile())) {
			store.load(in, "changeit".toCharArray());
		}

		KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keys.init(store, "changeit".toCharArray());
		serverSsl = SSLContext.getInstance("TLS");
		serverSsl.init(keys.getKeyManagers(), null, null);

		TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trust.init(store);
		clientSsl = SSLContext.getInstance("TLS");
		clientSsl.init(null, trust.getTrustManagers(), null);

	}

	private static SmtpTransport transport (String host, int port, SmtpTransport.Security security, String username, String password) {

		return new SmtpTransport(new SmtpTransport.Settings(host, port, security, username, password
			, Duration.ofSeconds(5), Duration.ofSeconds(5), "client.test", null), clientSsl.getSocketFactory());

	}

	private static MailMessage message () {

		return new MailMessage()
			.from("noreply@example.com", "承認ワークフロー")
			.to("hanako@example.co.jp", "山田 花子")
			.cc("taro@example.co.jp")
			.bcc("audit@example.com")
			.subject("申請が承認されました")
			.text("承認されました。");

	}

	@Test
	@DisplayName("暗号化なし（手元用）：EHLO・MAIL FROM・RCPT TO（To・Cc・Bcc）・DATA・QUIT の順で送り、本文は MIME の形")
	void plain () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, false)) {

			MailMessage message = message();
			transport("localhost", server.port(), SmtpTransport.Security.NONE, "", "").send(message);
			server.await();

			assertEquals(List.of("EHLO client.test", "MAIL FROM:<noreply@example.com>", "RCPT TO:<hanako@example.co.jp>"
				, "RCPT TO:<taro@example.co.jp>", "RCPT TO:<audit@example.com>", "DATA", "QUIT"), server.commands);

			String data = new String(server.data, StandardCharsets.US_ASCII);
			assertTrue(data.contains("Subject: =?UTF-8?B?"), data);
			assertFalse(data.contains("audit@example.com"), "Bcc がヘッダに出ている");

		}

	}

	@Test
	@DisplayName("STARTTLS：暗号化してから名乗り直し、そのあとで認証する（PLAIN）。証明書のホスト名を確かめる")
	void startTls () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, false)) {

			server.extensions = List.of("STARTTLS", "AUTH LOGIN PLAIN");

			transport("localhost", server.port(), SmtpTransport.Security.STARTTLS, "user@example.com", "p@ss 日本").send(message());
			server.await();

			assertEquals(List.of("EHLO client.test", "STARTTLS", "EHLO client.test", "AUTH PLAIN"), server.commands.subList(0, 4));
			assertEquals("user@example.com", server.username);
			assertEquals("p@ss 日本", server.password);
			assertTrue(server.authenticatedOverTls, "暗号化する前に認証している");
			assertTrue(server.data != null);

		}

	}

	@Test
	@DisplayName("STARTTLS を選んだのに相手が対応していなければ、暗号化せずに続けず断る（認証も送らない）")
	void startTlsRequired () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, false)) {

			server.extensions = List.of("AUTH PLAIN");

			MailException ex = assertThrows(MailException.class, () -> transport("localhost", server.port()
				, SmtpTransport.Security.STARTTLS, "user@example.com", "secret").send(message()));
			server.await();

			assertTrue(ex.getMessage().contains("STARTTLS"), ex.getMessage());
			assertEquals(List.of("EHLO client.test"), server.commands);
			assertEquals(null, server.password);

		}

	}

	@Test
	@DisplayName("証明書のホスト名が違えば（127.0.0.1 で繋ぐ）、送らない")
	void hostnameVerification () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, true)) {

			assertThrows(MailException.class, () -> transport("127.0.0.1", server.port(), SmtpTransport.Security.TLS, "", "")
				.send(message()));
			server.await();

			assertTrue(server.commands.isEmpty());

		}

	}

	@Test
	@DisplayName("最初から TLS（465）。AUTH LOGIN しか無ければ LOGIN で認証する")
	void implicitTlsWithLogin () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, true)) {

			server.extensions = List.of("AUTH LOGIN");

			transport("localhost", server.port(), SmtpTransport.Security.TLS, "user@example.com", "secret").send(message());
			server.await();

			assertEquals("AUTH LOGIN", server.commands.get(1));
			assertEquals("secret", server.password);
			assertTrue(server.authenticatedOverTls);

		}

	}

	@Test
	@DisplayName("認証が通らなければ（535）、送り直しても同じ例外。パスワードはメッセージに出さない")
	void authFailure () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, true)) {

			server.extensions = List.of("AUTH PLAIN");
			server.authReply = "535 5.7.8 bad credentials";

			MailException ex = assertThrows(MailException.class, () -> transport("localhost", server.port()
				, SmtpTransport.Security.TLS, "user@example.com", "very-secret").send(message()));
			server.await();

			assertEquals(535, ex.replyCode());
			assertFalse(ex.isTransient());
			assertFalse(ex.getMessage().contains("very-secret"), ex.getMessage());
			assertFalse(server.commands.contains("DATA"));

		}

	}

	@Test
	@DisplayName("宛先を1つでも断られたら、誰にも送らない。550 なら送り直しても同じ、451 なら一時的")
	void rejectedRecipient () throws Exception {

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, false)) {

			server.rcptReplies.put("taro@example.co.jp", "550 5.1.1 no such user");

			MailException ex = assertThrows(MailException.class, () -> transport("localhost", server.port()
				, SmtpTransport.Security.NONE, "", "").send(message()));
			server.await();

			assertFalse(ex.isTransient());
			assertTrue(ex.getMessage().contains("taro@example.co.jp"), ex.getMessage());
			assertFalse(server.commands.contains("DATA"), "断られたのに本文を送った");
			assertTrue(server.commands.contains("RSET"));

		}

		try (FakeSmtpServer server = new FakeSmtpServer(serverSsl, false)) {

			server.rcptReplies.put("taro@example.co.jp", "451 4.3.0 try later");

			MailException ex = assertThrows(MailException.class, () -> transport("localhost", server.port()
				, SmtpTransport.Security.NONE, "", "").send(message()));
			server.await();

			assertTrue(ex.isTransient());

		}

	}

	@Test
	@DisplayName("繋がらなければ一時的な例外。暗号化しない接続で認証しようとしたら、設定の時点で断る")
	void connectionAndSettings () {

		MailException ex = assertThrows(MailException.class, () -> transport("localhost", 1, SmtpTransport.Security.NONE, "", "")
			.send(message()));
		assertTrue(ex.isTransient());

		assertThrows(MailException.class, () -> new SmtpTransport.Settings("localhost", 25, SmtpTransport.Security.NONE
			, "user", "secret", Duration.ofSeconds(1), Duration.ofSeconds(1), "", null));
		assertThrows(MailException.class, () -> SmtpTransport.Security.of("ssl3"));
		assertFalse(new SmtpTransport.Settings("smtp.example.com", 587, SmtpTransport.Security.STARTTLS
			, "user", "very-secret", Duration.ofSeconds(1), Duration.ofSeconds(1), "", null).toString().contains("very-secret"));

	}

	@Test
	@DisplayName("行頭の「.」を重ね、最後に「.」だけの行を付ける")
	void dotStuffing () throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SmtpTransport.writeDotStuffed(out, ".a\r\nb\r\n..c\r\nd".getBytes(StandardCharsets.US_ASCII));

		assertArrayEquals("..a\r\nb\r\n...c\r\nd\r\n.\r\n".getBytes(StandardCharsets.US_ASCII), out.toByteArray());

	}

}
