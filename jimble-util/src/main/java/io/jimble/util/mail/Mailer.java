package io.jimble.util.mail;

/**
 * メールを送る（D-263）
 *
 * <pre>{@code
 * Mailer.send(new MailMessage()
 *     .to("hanako@example.co.jp", "山田 花子")
 *     .subject("申請が承認されました")
 *     .text("山田 様\n\n経費の申請（No.123）が承認されました。"));
 * }</pre>
 *
 * <h2>どこから送るか</h2>
 * <p>
 * <b>リクエストの処理の中で直に送らず、MQ に積んで、MQ の {@code execute()} から送る</b>のがよい。
 * SMTP は遅く（数百ミリ秒〜数秒）、落ちることもある。MQ なら、トランザクションと一緒に積めて
 * （申請が保存されなかったのにメールだけ飛ぶ、が起きない）、落ちたらやり直せる。
 * </p>
 *
 * <h2>送り先</h2>
 * <p>
 * 設定の {@code mail.transport} で決める（{@code smtp} / {@code log} / {@code memory}）。
 * HTTP の API で送るサービスに直に送るなら、{@link MailTransport} を実装して {@link #use(MailTransport)} で差し替える。
 * </p>
 */
public final class Mailer {

	/* 差し替えた送り先（無ければ設定から） */
	private static volatile MailTransport override;

	private Mailer () {
	}

	/**
	 * 送る
	 *
	 * <p>From を決めていなければ、設定の差出人（{@code mail.from}）を入れる。</p>
	 *
	 * @param message	メール
	 * @throws MailException	送れなかった場合（{@link MailException#isTransient()} で、送り直せば通るかもしれないかが分かる）
	 */
	public static void send (MailMessage message) {

		if (message == null) {
			throw new MailException("メールがありません");
		}

		MailAddress sender = message.check(message.from() == null ? MailConf.defaultFrom() : null);
		message.fillFrom(sender);

		transport().send(message);

	}

	/**
	 * いまの送り先
	 *
	 * @return	送り先
	 */
	public static MailTransport transport () {

		MailTransport current = override;

		if (current != null) {
			return current;
		}

		return switch (MailConf.transport()) {
			case "smtp" -> new SmtpTransport(MailConf.smtp());
			case "log" -> new LogTransport();
			case "memory" -> MemoryTransport.shared();
			default -> throw new MailException("%s は smtp / log / memory のどれかにしてください: %s"
				.formatted(MailConf.KEY_TRANSPORT, MailConf.transport()));
		};

	}

	/**
	 * 送り先を差し替える（HTTP の API で送るサービス・テスト）
	 *
	 * @param transport	送り先
	 */
	public static void use (MailTransport transport) {

		override = transport;

	}

	/**
	 * 差し替えをやめて、設定の送り先に戻す
	 */
	public static void reset () {

		override = null;

	}

}
