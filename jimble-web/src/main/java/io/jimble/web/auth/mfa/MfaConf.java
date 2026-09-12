package io.jimble.web.auth.mfa;

import java.time.Duration;
import io.jimble.util.conf.Conf;

/**
 * 二要素認証の設定（要件 F-W-32）
 *
 * <pre>
 * auth {
 *     mfa {
 *         enabled         = true
 *         issuer          = "承認ワークフロー"   # 認証アプリに出る名前
 *         digits          = 6
 *         period          = 30      # 秒。1つのコードが生きる長さ
 *         window          = 1       # 前後いくつの窓まで許すか
 *         recovery_codes  = 10      # 有効にするときに出す回復コードの数
 *         pending         = 5m      # パスワードのあと、コードを入れるまでの猶予
 *
 *         # 秘密鍵の暗号化に要る（無いと有効化を断る）
 *         secret_key      = ${?MFA_SECRET_KEY}
 *     }
 * }
 * </pre>
 *
 * <h2>{@code cipher.key} は使わない</h2>
 * <p>
 * <b>{@code cipher.key} を流用してはならない。</b>
 * {@code hash.password.encrypt} の既定は
 * <b>「{@code cipher.key} が設定されていれば true」</b>である
 * （移送してきたアプリの保存済みハッシュが暗号化されているため）。
 * </p>
 * <p>
 * そのため、<b>{@code cipher.key} を使っていなかったアプリが
 * 二要素認証のためにこれを設定すると、保存済みのパスワードハッシュが
 * 「暗号化済み」として読まれ、全員がログインできなくなる</b>。
 * 返るのは「IDかパスワードが違います」だけなので、原因に辿り着けない。
 * <b>鍵を分けてあるのはこのためである</b>（D-154）。
 * </p>
 */
public final class MfaConf {

	/** 使うか */
	public static final String KEY_ENABLED = "auth.mfa.enabled";

	/** 認証アプリに出る名前 */
	public static final String KEY_ISSUER = "auth.mfa.issuer";

	/** 桁数 */
	public static final String KEY_DIGITS = "auth.mfa.digits";

	/** 1つの窓の長さ（秒） */
	public static final String KEY_PERIOD = "auth.mfa.period";

	/** 前後いくつの窓まで許すか */
	public static final String KEY_WINDOW = "auth.mfa.window";

	/** 回復コードの数 */
	public static final String KEY_RECOVERY_CODES = "auth.mfa.recovery_codes";

	/** コードを入れるまでの猶予 */
	public static final String KEY_PENDING = "auth.mfa.pending";

	/** 秘密鍵を暗号化する鍵 */
	public static final String KEY_SECRET_KEY = "auth.mfa.secret_key";

	private MfaConf () {
	}

	/**
	 * 使うか
	 *
	 * @return	使う場合 = true
	 */
	public static boolean enabled () {

		return Conf.conf().getBoolean(KEY_ENABLED, true);

	}

	/**
	 * 認証アプリに出る名前
	 *
	 * @return	名前
	 */
	public static String issuer () {

		String issuer = Conf.conf().getString(KEY_ISSUER, "");

		return issuer.isEmpty() ? "jimble" : issuer;

	}

	/**
	 * 桁数
	 *
	 * <p><b>6 から変えないほうがよい。</b>認証アプリの多くは6桁しか出せない。</p>
	 *
	 * @return	桁数
	 */
	public static int digits () {

		return Math.clamp(Conf.conf().getInt(KEY_DIGITS, 6), 6, 8);

	}

	/**
	 * 1つの窓の長さ（秒）
	 *
	 * @return	秒
	 */
	public static int period () {

		return Math.max(1, Conf.conf().getInt(KEY_PERIOD, 30));

	}

	/**
	 * 前後いくつの窓まで許すか
	 *
	 * <p>
	 * <b>広げすぎない。</b>1つ増やすたびに<b>総当たりで当たる確率も増える</b>——
	 * 窓を10にすると、6桁の当たりが21通りぶん出ることになる。
	 * </p>
	 *
	 * @return	窓の数
	 */
	public static int window () {

		return Math.clamp(Conf.conf().getInt(KEY_WINDOW, 1), 0, 10);

	}

	/**
	 * 回復コードの数
	 *
	 * @return	数
	 */
	public static int recoveryCodes () {

		return Math.clamp(Conf.conf().getInt(KEY_RECOVERY_CODES, 10), 1, 50);

	}

	/**
	 * コードを入れるまでの猶予
	 *
	 * <p>
	 * <b>長くしない。</b>この間、<b>パスワードだけ通った状態</b>がセッションに残る。
	 * </p>
	 *
	 * @return	猶予
	 */
	public static Duration pending () {

		return Conf.clamp(Conf.conf().getDuration(KEY_PENDING, Duration.ofMinutes(5))
			, Duration.ofSeconds(30), Duration.ofMinutes(30));

	}

	/**
	 * 秘密鍵を暗号化する鍵
	 *
	 * <p>
	 * <b>{@code cipher.key} とは別の鍵である。</b>理由はクラスの説明に書いてある。
	 * 長さの決まりは無い（{@code Aead} が 32 バイトに畳む）が、
	 * <b>推測できない長さにすること</b>。
	 * </p>
	 *
	 * @return	鍵。設定されていなければ空
	 */
	public static String secretKey () {

		return Conf.conf().getString(KEY_SECRET_KEY, "");

	}

}
