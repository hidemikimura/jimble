package io.jimble.util.mail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * メールを MIME の形にする（D-263）
 *
 * <ul>
 *   <li>文字は UTF-8 だけ。件名と名前の日本語は RFC 2047（{@code =?UTF-8?B?...?=}）、本文は base64</li>
 *   <li>テキストと HTML の両方があれば {@code multipart/alternative}、添付があれば {@code multipart/mixed} で包む</li>
 *   <li>添付ファイルの日本語の名前は RFC 2231（{@code filename*=UTF-8''...}）と RFC 2047 の両方で書く（古いメールソフト向け）</li>
 *   <li>改行は CRLF。どの行も 998 バイトを超えない</li>
 * </ul>
 */
final class MimeWriter {

	private static final String CRLF = "\r\n";

	/* 1つの encoded-word に入れる UTF-8 のバイト数の上限（base64 で 60 文字。全体で 75 文字以内に収める） */
	private static final int ENCODED_WORD_BYTES = 45;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Base64.Encoder MIME_BASE64 = Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII));

	private MimeWriter () {
	}

	/**
	 * 書く
	 *
	 * @param message	メール（差出人が決まっていること）
	 * @param sender	差出人
	 * @return	MIME の形のメール（CRLF）
	 */
	static byte[] write (MailMessage message, MailAddress sender) {

		StringBuilder head = new StringBuilder();

		header(head, "Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
		header(head, "Message-ID", "<%s@%s>".formatted(random(16), sender.address().substring(sender.address().lastIndexOf('@') + 1)));
		header(head, "From", addresses(List.of(sender)));

		if (!message.to().isEmpty()) {
			header(head, "To", addresses(message.to()));
		}

		if (!message.cc().isEmpty()) {
			header(head, "Cc", addresses(message.cc()));
		}

		// Bcc は書かない（宛先にだけ入れる）

		if (!message.replyTo().isEmpty()) {
			header(head, "Reply-To", addresses(message.replyTo()));
		}

		header(head, "Subject", text(message.subject(), "Subject: ".length()));
		header(head, "MIME-Version", "1.0");

		for (Map.Entry<String, String> extra : message.headers().entrySet()) {
			header(head, extra.getKey(), extra.getValue());
		}

		String body = message.attachments().isEmpty() ? content(message) : mixed(message);

		return (head + body).getBytes(StandardCharsets.US_ASCII);

	}

	// region 本文

	/**
	 * 本文（テキストか HTML か、その両方）
	 */
	private static String content (MailMessage message) {

		if (message.text() != null && message.html() != null) {

			String boundary = boundary();

			return "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"" + CRLF + CRLF
				+ "--" + boundary + CRLF + textPart(message.text(), "text/plain")
				+ "--" + boundary + CRLF + textPart(message.html(), "text/html")
				+ "--" + boundary + "--" + CRLF;

		}

		return message.text() != null ? textPart(message.text(), "text/plain") : textPart(message.html(), "text/html");

	}

	/**
	 * 添付つき
	 */
	private static String mixed (MailMessage message) {

		String boundary = boundary();
		StringBuilder out = new StringBuilder();

		out.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append('"').append(CRLF).append(CRLF);
		out.append("--").append(boundary).append(CRLF).append(content(message));

		for (MailAttachment attachment : message.attachments()) {

			String name = attachment.fileName();

			out.append("--").append(boundary).append(CRLF);
			out.append("Content-Type: ").append(attachment.contentType()).append("; name=").append(quotedOrEncoded(name)).append(CRLF);
			out.append("Content-Disposition: attachment;").append(CRLF)
				.append(" filename*=UTF-8''").append(percent(name)).append(';').append(CRLF)
				.append(" filename=").append(quotedOrEncoded(name)).append(CRLF);
			out.append("Content-Transfer-Encoding: base64").append(CRLF).append(CRLF);
			out.append(base64(attachment.content()));

		}

		out.append("--").append(boundary).append("--").append(CRLF);

		return out.toString();

	}

	/**
	 * テキストの部分（UTF-8・base64。改行は CRLF にそろえる）
	 */
	private static String textPart (String value, String type) {

		String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", CRLF);

		return "Content-Type: " + type + "; charset=UTF-8" + CRLF
			+ "Content-Transfer-Encoding: base64" + CRLF + CRLF
			+ base64(normalized.getBytes(StandardCharsets.UTF_8));

	}

	private static String base64 (byte[] bytes) {

		String encoded = MIME_BASE64.encodeToString(bytes);

		return encoded.isEmpty() ? "" : encoded + CRLF;

	}

	// endregion

	// region ヘッダ

	private static void header (StringBuilder out, String name, String value) {

		out.append(name).append(": ").append(value).append(CRLF);

	}

	/**
	 * アドレスを並べる（1つずつ折り返す）
	 */
	private static String addresses (List<MailAddress> list) {

		List<String> parts = new ArrayList<>();

		for (MailAddress address : list) {
			parts.add(address.name().isEmpty()
				? address.address()
				: "%s <%s>".formatted(phrase(address.name()), address.address()));
		}

		return String.join("," + CRLF + " ", parts);

	}

	/**
	 * 表示する名前（ASCII なら引用符で囲み、そうでなければ RFC 2047）
	 */
	private static String phrase (String name) {

		if (isPlainAscii(name)) {
			return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}

		return encodedWords(name);

	}

	/**
	 * ヘッダの文（件名）。ASCII で短ければそのまま、そうでなければ RFC 2047
	 */
	static String text (String value, int prefix) {

		if (value.isEmpty()) {
			return "";
		}

		if (isPlainAscii(value) && !value.contains("=?") && prefix + value.length() <= 78) {
			return value;
		}

		return encodedWords(value);

	}

	/**
	 * 添付ファイルの名前（ASCII なら引用符、そうでなければ RFC 2047 を引用符で囲む）
	 */
	private static String quotedOrEncoded (String value) {

		if (isPlainAscii(value)) {
			return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}

		return "\"" + encodedWords(value).replace(CRLF + " ", " ") + "\"";

	}

	/**
	 * RFC 2047 の B 符号化（文字の途中で切らない。1語は 75 文字以内）
	 */
	static String encodedWords (String value) {

		List<String> words = new ArrayList<>();
		ByteArrayOutputStream chunk = new ByteArrayOutputStream();

		for (int i = 0; i < value.length(); ) {

			int codePoint = value.codePointAt(i);
			byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);

			if (chunk.size() + bytes.length > ENCODED_WORD_BYTES) {
				words.add(word(chunk.toByteArray()));
				chunk.reset();
			}

			chunk.writeBytes(bytes);
			i += Character.charCount(codePoint);

		}

		if (chunk.size() > 0) {
			words.add(word(chunk.toByteArray()));
		}

		return String.join(CRLF + " ", words);

	}

	private static String word (byte[] bytes) {

		return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(bytes) + "?=";

	}

	/**
	 * RFC 2231 の値（UTF-8 を % で）
	 */
	private static String percent (String value) {

		StringBuilder out = new StringBuilder();

		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xff;
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "!#$&+-.^_`|~".indexOf(c) >= 0) {
				out.append((char) c);
			} else {
				out.append('%').append(HexFormat.of().withUpperCase().toHexDigits((byte) c));
			}
		}

		return out.toString();

	}

	/**
	 * 表示できる ASCII だけか（制御文字を含まない）
	 */
	private static boolean isPlainAscii (String value) {

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < 0x20 || c > 0x7e) {
				return false;
			}
		}

		return true;

	}

	// endregion

	private static String boundary () {

		// base64 の文字に無い「_」と「=」の並びを入れる（本文の中に現れない）
		return "=_jimble_" + random(12);

	}

	private static String random (int bytes) {

		byte[] value = new byte[bytes];
		RANDOM.nextBytes(value);

		return HexFormat.of().formatHex(value);

	}

}
