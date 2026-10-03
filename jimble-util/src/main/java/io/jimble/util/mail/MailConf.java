package io.jimble.util.mail;

import io.jimble.util.conf.Conf;

import java.time.Duration;
import java.util.Locale;

/**
 * メールの設定（D-263）
 *
 * <pre>
 * mail {
 *     transport = "smtp"                   # smtp | log | memory
 *     from      = "noreply@example.com"    # From を決めないメールの差出人
 *     from_name = "承認ワークフロー"
 *
 *     smtp {
 *         host            = "smtp.example.com"
 *         port            = 587             # 書かなければ security から（starttls 587 / tls 465 / none 25）
 *         security        = "starttls"      # starttls | tls | none
 *         username        = ""
 *         password        = ""
 *         password        = ${?SMTP_PASSWORD}
 *         connect_timeout = 10s
 *         timeout         = 30s             # 1回の読み書きの待ち
 *         helo            = ""              # EHLO で名乗る名前（空ならこの機械の名前）
 *         envelope_from   = ""              # 届かなかったときの知らせの宛先（空なら From）
 *     }
 * }
 * </pre>
 *
 * <p>
 * <b>{@code transport} の既定は {@code smtp}</b>。{@code mail.smtp.host} を書かずに送ると例外にする
 * （送ったつもりで黙って届かない、を起こさない）。手元では {@code application.local.conf} に
 * {@code mail.transport = "log"} と書く。
 * </p>
 */
public final class MailConf {

	/** 送り先 */
	public static final String KEY_TRANSPORT = "mail.transport";

	/** 既定の差出人 */
	public static final String KEY_FROM = "mail.from";

	/** 既定の差出人の名前 */
	public static final String KEY_FROM_NAME = "mail.from_name";

	/** SMTP のホスト */
	public static final String KEY_SMTP_HOST = "mail.smtp.host";

	/** SMTP のポート */
	public static final String KEY_SMTP_PORT = "mail.smtp.port";

	/** 暗号化 */
	public static final String KEY_SMTP_SECURITY = "mail.smtp.security";

	/** 利用者名 */
	public static final String KEY_SMTP_USERNAME = "mail.smtp.username";

	/** パスワード */
	public static final String KEY_SMTP_PASSWORD = "mail.smtp.password";

	/** 繋ぐまでの待ち */
	public static final String KEY_SMTP_CONNECT_TIMEOUT = "mail.smtp.connect_timeout";

	/** 読み書きの待ち */
	public static final String KEY_SMTP_TIMEOUT = "mail.smtp.timeout";

	/** EHLO で名乗る名前 */
	public static final String KEY_SMTP_HELO = "mail.smtp.helo";

	/** エンベロープの差出人 */
	public static final String KEY_SMTP_ENVELOPE_FROM = "mail.smtp.envelope_from";

	private MailConf () {
	}

	/**
	 * 送り先の名前
	 *
	 * @return	{@code smtp} / {@code log} / {@code memory}
	 */
	public static String transport () {

		return Conf.conf().getString(KEY_TRANSPORT, "smtp").trim().toLowerCase(Locale.ROOT);

	}

	/**
	 * From を決めないメールの差出人
	 *
	 * @return	差出人（書いていなければ null）
	 */
	public static MailAddress defaultFrom () {

		String from = Conf.conf().getString(KEY_FROM, "").trim();

		if (from.isEmpty()) {
			return null;
		}

		try {
			return MailAddress.of(from, Conf.conf().getString(KEY_FROM_NAME, ""));
		} catch (MailException ex) {
			throw new MailException("%s の書き方が違います: %s".formatted(KEY_FROM, ex.getMessage()), ex);
		}

	}

	/**
	 * SMTP の設定
	 *
	 * @return	設定
	 * @throws MailException	host を書いていない・security が違う・暗号化せずに認証しようとしている場合
	 */
	public static SmtpTransport.Settings smtp () {

		String host = Conf.conf().getString(KEY_SMTP_HOST, "").trim();

		if (host.isEmpty()) {
			throw new MailException(("メールを送るには %s が要ります（SMTP のホスト）。"
				+ "手元で送らずに確かめるなら application.local.conf に mail.transport = \"log\" と書いてください").formatted(KEY_SMTP_HOST));
		}

		SmtpTransport.Security security = SmtpTransport.Security.of(Conf.conf().getString(KEY_SMTP_SECURITY, "starttls"));

		String envelopeFrom = Conf.conf().getString(KEY_SMTP_ENVELOPE_FROM, "").trim();

		return new SmtpTransport.Settings(
			host
			, Conf.conf().getInt(KEY_SMTP_PORT, security.defaultPort())
			, security
			, Conf.conf().getString(KEY_SMTP_USERNAME, "")
			, Conf.conf().getString(KEY_SMTP_PASSWORD, "")
			, Conf.conf().getDuration(KEY_SMTP_CONNECT_TIMEOUT, Duration.ofSeconds(10))
			, Conf.conf().getDuration(KEY_SMTP_TIMEOUT, Duration.ofSeconds(30))
			, Conf.conf().getString(KEY_SMTP_HELO, "").trim()
			, envelopeFrom.isEmpty() ? null : MailAddress.of(envelopeFrom));

	}

}
