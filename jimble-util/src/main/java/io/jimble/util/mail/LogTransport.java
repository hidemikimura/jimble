package io.jimble.util.mail;

import io.jimble.util.log.Log;

import java.util.stream.Collectors;

/**
 * 送らずにログに出す（手元用。D-263）
 *
 * <p>
 * {@code conf/application.local.conf} に {@code mail.transport = "log"} と書くと、手元で動かしてもメールは飛ばず、
 * 宛先・件名・本文がログに出る。<b>本番の設定に書かないこと</b>（メールが黙って届かなくなる）。
 * そのため、使うたびに WARN で知らせる。
 * </p>
 */
public final class LogTransport implements MailTransport {

	/** 本文をログに出す上限（文字） */
	private static final int MAX_BODY = 2000;

	@Override
	public void send (MailMessage message) {

		String body = message.text() != null ? message.text() : message.html();

		if (body.length() > MAX_BODY) {
			body = body.substring(0, MAX_BODY) + "…（以下略）";
		}

		Log.warn("""
			メールを送らずにログに出しました（mail.transport = "log"。本番では smtp にすること）
			  From: %s
			  To: %s
			  Cc: %s
			  Bcc: %s
			  Subject: %s
			  添付: %s
			%s""".formatted(
				message.from()
				, join(message.to()), join(message.cc()), join(message.bcc())
				, message.subject()
				, message.attachments().stream().map(MailAttachment::fileName).collect(Collectors.joining(", "))
				, body));

	}

	private static String join (java.util.List<MailAddress> list) {

		return list.stream().map(MailAddress::toString).collect(Collectors.joining(", "));

	}

}
