package io.jimble.util.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 送るメールの形を確かめる（D-263）
 */
class MailMessageTest {

	@Test
	@DisplayName("アドレス：ドメインを小文字・Punycode にする。名前の前後の空白を落とす")
	void normalizesAddress () {

		assertEquals("hanako@example.co.jp", MailAddress.of("hanako@EXAMPLE.co.jp").address());
		assertEquals("info@" + java.net.IDN.toASCII("例え.テスト"), MailAddress.of("info@例え.テスト").address());
		assertTrue(MailAddress.of("info@例え.テスト").address().startsWith("info@xn--"));
		assertEquals("山田 花子", MailAddress.of("hanako@example.co.jp", "  山田 花子 ").name());

	}

	@Test
	@DisplayName("アドレス：改行・山括弧・空白・日本語のローカル部・@ の無いものは断る（ヘッダの差し込みを止める）")
	void rejectsAddress () {

		for (String bad : List.of("hanako@example.co.jp\r\nBcc: evil@example.com", "<hanako@example.co.jp>", "han ako@example.co.jp"
			, "花子@example.co.jp", "hanako", "@example.co.jp", "hanako@", "a..b@example.com", "hanako@example")) {
			assertThrows(MailException.class, () -> MailAddress.of(bad), bad);
		}

		assertThrows(MailException.class, () -> MailAddress.of("hanako@example.co.jp", "山田\r\nBcc: evil@example.com"));

	}

	@Test
	@DisplayName("件名・ヘッダ：改行は断る。jimble が書くヘッダは決めさせない")
	void rejectsHeaders () {

		MailMessage message = new MailMessage();

		assertThrows(MailException.class, () -> message.subject("件名\r\nBcc: evil@example.com"));
		assertThrows(MailException.class, () -> message.header("X-Test", "a\r\nBcc: evil@example.com"));
		assertThrows(MailException.class, () -> message.header("Bcc", "evil@example.com"));
		assertThrows(MailException.class, () -> message.header("Content-Type", "text/html"));
		assertThrows(MailException.class, () -> message.header("X Test", "a"));
		assertThrows(MailException.class, () -> message.header("X-Test", "日本語"));

		message.header("List-Unsubscribe", "<mailto:unsubscribe@example.com>");
		assertEquals("<mailto:unsubscribe@example.com>", message.headers().get("List-Unsubscribe"));

	}

	@Test
	@DisplayName("送る相手は To・Cc・Bcc を合わせ、重なりを除く。差出人・宛先・本文が無ければ断る")
	void recipientsAndCheck () {

		MailMessage message = new MailMessage()
			.to("a@example.com").cc("b@example.com").bcc("A@example.com").bcc("c@example.com");

		assertEquals(List.of("a@example.com", "b@example.com", "c@example.com")
			, message.recipients().stream().map(MailAddress::address).toList());

		assertThrows(MailException.class, () -> message.check(null), "差出人");
		message.from("noreply@example.com");
		assertThrows(MailException.class, () -> message.check(null), "本文");
		message.text("本文");
		message.check(null);

		assertThrows(MailException.class, () -> new MailMessage().from("noreply@example.com").text("x").check(null), "宛先");

	}

	@Test
	@DisplayName("添付：名前が無い・改行・種類の形が違うものは断る。中身は写しを持つ")
	void attachment () {

		assertThrows(MailException.class, () -> new MailAttachment("", new byte[0], "text/plain"));
		assertThrows(MailException.class, () -> new MailAttachment("a\r\n.txt", new byte[0], "text/plain"));
		assertThrows(MailException.class, () -> new MailAttachment("a.txt", new byte[0], "text/plain; charset=x\r\nX: y"));

		byte[] content = { 1, 2, 3 };
		MailAttachment attachment = new MailAttachment("a.bin", content, null);
		content[0] = 9;

		assertEquals(1, attachment.content()[0]);
		assertEquals("application/octet-stream", attachment.contentType());

	}

}
