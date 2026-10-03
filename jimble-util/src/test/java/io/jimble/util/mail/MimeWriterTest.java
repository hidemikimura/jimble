package io.jimble.util.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MIME の形（D-263）
 */
class MimeWriterTest {

	private static final Pattern WORD = Pattern.compile("=\\?UTF-8\\?B\\?([A-Za-z0-9+/=]+)\\?=");

	/**
	 * RFC 2047 の語を解く（語と語の間の折り返しと空白は捨てる）
	 */
	static String decodeWords (String value) {

		// 語と語の間の折り返しは、語をつなげて解く（RFC 2047 の 6.2）
		String joined = value.replace("?=\r\n =?", "?==?");
		Matcher m = WORD.matcher(joined);
		StringBuilder out = new StringBuilder();
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		int last = 0;

		while (m.find()) {
			if (m.start() > last) {
				out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
				bytes.reset();
				out.append(joined, last, m.start());
			}
			bytes.writeBytes(Base64.getDecoder().decode(m.group(1)));
			last = m.end();
		}

		out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
		out.append(joined.substring(last));

		return out.toString();

	}

	private static String mime (MailMessage message) {

		return new String(message.toMime(), StandardCharsets.US_ASCII);

	}

	/**
	 * ヘッダの値（折り返しを戻す）
	 */
	private static String header (String mime, String name) {

		String head = mime.substring(0, mime.indexOf("\r\n\r\n")).replace("\r\n ", "\u0000");
		for (String line : head.split("\r\n")) {
			if (line.startsWith(name + ": ")) {
				return line.substring(name.length() + 2).replace("\u0000", "\r\n ");
			}
		}
		return null;

	}

	@Test
	@DisplayName("テキストだけ：件名と名前の日本語は RFC 2047、本文は UTF-8 の base64（改行は CRLF）。Bcc は書かない")
	void textOnly () {

		String mime = mime(new MailMessage()
			.from("noreply@example.com", "承認ワークフロー")
			.to("hanako@example.co.jp", "山田 花子")
			.to("taro@example.co.jp")
			.bcc("audit@example.com")
			.subject("申請が承認されました")
			.text("山田 様\n\n承認されました。"));

		assertEquals("申請が承認されました", decodeWords(header(mime, "Subject")));
		assertEquals("承認ワークフロー <noreply@example.com>", decodeWords(header(mime, "From")));
		assertTrue(header(mime, "To").contains("<hanako@example.co.jp>,\r\n taro@example.co.jp"), header(mime, "To"));
		assertEquals(null, header(mime, "Bcc"));
		assertFalse(mime.contains("audit@example.com"));
		assertTrue(header(mime, "Message-ID").matches("<[0-9a-f]{32}@example\\.com>"));
		assertEquals("1.0", header(mime, "MIME-Version"));
		assertEquals("text/plain; charset=UTF-8", header(mime, "Content-Type"));

		String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
		assertEquals("山田 様\r\n\r\n承認されました。", new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8));

	}

	@Test
	@DisplayName("ASCII の短い件名と名前はそのまま（名前は引用符で囲む）。長い日本語の件名は 75 文字以内の語に分け、文字の途中で切らない")
	void headerEncoding () {

		String ascii = mime(new MailMessage().from("noreply@example.com", "Approval \"WF\"").to("a@example.com")
			.subject("Your request was approved").text("x"));

		assertEquals("Your request was approved", header(ascii, "Subject"));
		assertEquals("\"Approval \\\"WF\\\"\" <noreply@example.com>", header(ascii, "From"));

		String subject = "経費の申請（No.123）が承認されました。内容をご確認のうえ、期日までに精算の手続きをしてください🙏";
		String mime = mime(new MailMessage().from("noreply@example.com").to("a@example.com").subject(subject).text("x"));

		String value = header(mime, "Subject");
		assertEquals(subject, decodeWords(value));
		for (String word : value.split("\r\n ")) {
			assertTrue(word.length() <= 75, word);
		}

		for (String line : mime.split("\r\n")) {
			assertTrue(line.length() <= 998);
		}

	}

	@Test
	@DisplayName("テキストと HTML：multipart/alternative。添付：multipart/mixed で、日本語のファイル名は RFC 2231 と RFC 2047")
	void multipart () {

		byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII);

		String mime = mime(new MailMessage().from("noreply@example.com").to("a@example.com").subject("請求書")
			.text("本文").html("<p>本文</p>")
			.attach("請求書 2026年10月.pdf", pdf, "application/pdf"));

		String mixed = header(mime, "Content-Type");
		assertTrue(mixed.startsWith("multipart/mixed; boundary=\"=_jimble_"), mixed);
		String boundary = mixed.substring(mixed.indexOf('"') + 1, mixed.lastIndexOf('"'));

		assertTrue(mime.contains("--" + boundary + "\r\nContent-Type: multipart/alternative; boundary=\"=_jimble_"));
		assertTrue(mime.contains("Content-Type: text/plain; charset=UTF-8"));
		assertTrue(mime.contains("Content-Type: text/html; charset=UTF-8"));
		assertTrue(mime.contains(" filename*=UTF-8''%E8%AB%8B%E6%B1%82%E6%9B%B8%202026%E5%B9%B410%E6%9C%88.pdf;"), mime);
		assertTrue(mime.endsWith("--" + boundary + "--\r\n"));

		Matcher name = Pattern.compile(" filename=\"([^\"]+)\"").matcher(mime);
		assertTrue(name.find());
		assertEquals("請求書 2026年10月.pdf", decodeWords(name.group(1)));

		int start = mime.indexOf("Content-Transfer-Encoding: base64\r\n\r\n", mime.indexOf("application/pdf")) + 37;
		String encoded = mime.substring(start, mime.indexOf("\r\n--" + boundary, start));
		assertEquals("%PDF-1.4 fake", new String(Base64.getDecoder().decode(encoded.replace("\r\n", "")), StandardCharsets.US_ASCII));

	}

	@Test
	@DisplayName("足したヘッダはそのまま書く")
	void extraHeaders () {

		String mime = mime(new MailMessage().from("noreply@example.com").to("a@example.com").subject("x").text("x")
			.header("List-Unsubscribe", "<mailto:unsubscribe@example.com>"));

		assertEquals("<mailto:unsubscribe@example.com>", header(mime, "List-Unsubscribe"));

	}

}
