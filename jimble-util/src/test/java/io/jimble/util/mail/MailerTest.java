package io.jimble.util.mail;

import io.jimble.util.conf.Conf;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 送り先の選び方と、既定の差出人（D-263）
 */
class MailerTest {

	private Config original;

	@AfterEach
	void reset () {

		Mailer.reset();
		MemoryTransport.shared().clear();

		if (original != null) {
			Conf.replace(original);
		}

	}

	private void conf (String hocon) {

		Conf.reload();
		original = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(original));

	}

	@Test
	@DisplayName("From を決めなければ設定の差出人を入れる。memory なら共有の入れ物に貯まる")
	void defaultFrom () {

		conf("""
			mail {
				transport = "memory"
				from      = "noreply@example.com"
				from_name = "承認ワークフロー"
			}
			""");

		Mailer.send(new MailMessage().to("hanako@example.co.jp").subject("件名").text("本文"));

		MailMessage sent = MemoryTransport.shared().sent().get(0);
		assertEquals("noreply@example.com", sent.from().address());
		assertEquals("承認ワークフロー", sent.from().name());

	}

	@Test
	@DisplayName("smtp なのに host を書いていなければ、送ったつもりで黙らず例外にする（log の案内つき）")
	void smtpWithoutHost () {

		conf("mail.transport = \"smtp\"\nmail.from = \"noreply@example.com\"");

		MailException ex = assertThrows(MailException.class
			, () -> Mailer.send(new MailMessage().to("hanako@example.co.jp").subject("件名").text("本文")));

		assertTrue(ex.getMessage().contains("mail.smtp.host"), ex.getMessage());
		assertTrue(ex.getMessage().contains("log"), ex.getMessage());

	}

	@Test
	@DisplayName("差し替えた送り先を使い、reset で設定に戻る。知らない transport は断る")
	void useAndReset () {

		conf("mail.transport = \"log\"");

		MemoryTransport memory = new MemoryTransport();
		Mailer.use(memory);

		Mailer.send(new MailMessage().from("noreply@example.com").to("hanako@example.co.jp").subject("件名").text("本文"));
		assertEquals(1, memory.sent().size());

		Mailer.reset();
		assertInstanceOf(LogTransport.class, Mailer.transport());

		// log でも送れる（WARN が出るだけ）
		Mailer.send(new MailMessage().from("noreply@example.com").to("hanako@example.co.jp").subject("件名").text("本文"));

		conf("mail.transport = \"sendmail\"");
		assertThrows(MailException.class, Mailer::transport);

	}

}
