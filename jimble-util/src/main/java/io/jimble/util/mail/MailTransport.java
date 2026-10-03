package io.jimble.util.mail;

/**
 * メールの送り先（D-263）
 *
 * <p>
 * jimble が持っているのは SMTP（{@link SmtpTransport}）、送らずにログに出すもの（{@link LogTransport}）、
 * メモリに貯めるもの（{@link MemoryTransport}）の3つ。どれを使うかは設定の {@code mail.transport} で決める。
 * </p>
 *
 * <p>
 * HTTP の API で送るサービスに直に送るなら、これを実装して {@link Mailer#use(MailTransport)} で差し替える。
 * {@link MailMessage#toMime()} で生のメールにできる（生のメールを受け付ける API ならそのまま渡せる）。
 * </p>
 */
@FunctionalInterface
public interface MailTransport {

	/**
	 * 送る
	 *
	 * @param message	メール（差出人は決まっている）
	 * @throws MailException	送れなかった場合
	 */
	void send (MailMessage message);

}
