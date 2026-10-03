package io.jimble.util.mail;

/**
 * メールを送れなかった（D-263）
 *
 * <p>
 * <b>しばらくして送り直せば通るかもしれないか</b>を {@link #isTransient()} で分ける。
 * SMTP の 4xx（相手が混んでいる・一時的に断っている）と、繋がらない・途中で切れた、は一時的。
 * 5xx（宛先が無い・認証が通らない）と、メッセージの形が違う、は送り直しても同じ。
 * </p>
 *
 * <pre>{@code
 * // MQ から送るなら、一時的なものだけやり直す
 * try {
 *     Mailer.send(message);
 *     return MqStatus.completed;
 * } catch (MailException ex) {
 *     if (ex.isTransient()) throw ex;          // maxRetry() までやり直す
 *     return MqStatus.dead;                    // やり直しても同じ
 * }
 * }</pre>
 */
public class MailException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/* SMTP の応答コード（無ければ 0） */
	private final int replyCode;

	/* 一時的か */
	private final boolean transientFailure;

	/**
	 * 送り直しても同じもの（形の誤り・設定の誤り）
	 *
	 * @param message	何が起きたか
	 */
	public MailException (String message) {

		this(message, 0, false, null);

	}

	/**
	 * 送り直しても同じもの（形の誤り・設定の誤り）
	 *
	 * @param message	何が起きたか
	 * @param cause		元の例外
	 */
	public MailException (String message, Throwable cause) {

		this(message, 0, false, cause);

	}

	/**
	 * 作る
	 *
	 * @param message			何が起きたか
	 * @param replyCode			SMTP の応答コード（無ければ 0）
	 * @param transientFailure	しばらくして送り直せば通るかもしれないか
	 * @param cause				元の例外（無ければ null）
	 */
	public MailException (String message, int replyCode, boolean transientFailure, Throwable cause) {

		super(message, cause);

		this.replyCode = replyCode;
		this.transientFailure = transientFailure;

	}

	/**
	 * SMTP の応答コード
	 *
	 * @return	応答コード（SMTP の応答で断られたのでなければ 0）
	 */
	public int replyCode () {

		return replyCode;

	}

	/**
	 * しばらくして送り直せば通るかもしれないか
	 *
	 * @return	SMTP の 4xx、繋がらない・途中で切れた、なら true
	 */
	public boolean isTransient () {

		return transientFailure;

	}

}
