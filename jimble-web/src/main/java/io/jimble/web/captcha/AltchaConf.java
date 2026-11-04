package io.jimble.web.captcha;

import io.jimble.util.conf.ConfValue;

import java.time.Duration;

/**
 * ALTCHA の設定
 *
 * <pre>
 * altcha {
 *   secret      = ""      # 問題に署名する鍵（32 文字以上。環境変数から読む）
 *   cost        = 5000    # 1 回の計算の重さ（PBKDF2 の繰り返し回数）
 *   counter_max = 10000   # 答えの数の上限（counter_max / 2 〜 counter_max から選ぶ）
 *   expires     = 10m     # 問題の期限
 * }
 * </pre>
 *
 * <p>
 * ブラウザがする計算は、平均でおよそ {@code cost × counter_max × 3/4} 回の HMAC-SHA256 である（答えを 0 から順に探すため）。
 * 既定のままで、10 コアの Mac の Chrome で答えが上限近く（9753）のとき 3.1 秒だった（2.7.13 で測った）。
 * 古いスマートフォンはその数倍かかる。重くするほどボットは嫌がるが、人も待つ。既定は altcha-lib の例と同じ。
 * </p>
 */
public final class AltchaConf {

	/** 設定キー：鍵 */
	public static final String KEY_SECRET = "altcha.secret";

	/** 設定キー：1 回の計算の重さ */
	public static final String KEY_COST = "altcha.cost";

	/** 設定キー：答えの数の上限 */
	public static final String KEY_COUNTER_MAX = "altcha.counter_max";

	/** 設定キー：問題の期限 */
	public static final String KEY_EXPIRES = "altcha.expires";

	/** 鍵の最短の長さ */
	public static final int MIN_SECRET_LENGTH = 32;

	/** 既定の 1 回の計算の重さ */
	public static final int DEFAULT_COST = 5000;

	/** 既定の答えの数の上限 */
	public static final int DEFAULT_COUNTER_MAX = 10000;

	/**
	 * 既定の問題の期限
	 *
	 * <p>ウィジェットはページを開いたときではなく、送る前（触ったとき）に問題を取りに来るので、長くしなくてよい。</p>
	 */
	public static final Duration DEFAULT_EXPIRES = Duration.ofMinutes(10);

	/* 要求のたびに読むので、読み込んだ設定 1 つにつき 1 回だけ読む */
	private static final ConfValue<String> SECRET = ConfValue.of(c -> c.getString(KEY_SECRET, "").trim());

	private static final ConfValue<Integer> COST = ConfValue.of(c -> c.getInt(KEY_COST, DEFAULT_COST));

	private static final ConfValue<Integer> COUNTER_MAX = ConfValue.of(c -> c.getInt(KEY_COUNTER_MAX, DEFAULT_COUNTER_MAX));

	private static final ConfValue<Duration> EXPIRES = ConfValue.of(c -> c.getDuration(KEY_EXPIRES, DEFAULT_EXPIRES));

	private AltchaConf () {}

	/**
	 * 鍵
	 *
	 * @return	鍵
	 * @throws IllegalStateException	書いていない・短い場合
	 */
	public static String secret () {

		String secret = SECRET.get();

		if (secret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException(
				("%s を %d 文字以上で書いてください（いまは %d 文字）。問題の署名に使うので、環境変数から読んでください: "
					+ "%s = ${?ALTCHA_SECRET}").formatted(KEY_SECRET, MIN_SECRET_LENGTH, secret.length(), KEY_SECRET));
		}

		return secret;

	}

	/**
	 * 1 回の計算の重さ
	 *
	 * @return	PBKDF2 の繰り返し回数
	 */
	public static int cost () {

		return positive(KEY_COST, COST.get(), 1);

	}

	/**
	 * 答えの数の上限
	 *
	 * @return	上限
	 */
	public static int counterMax () {

		return positive(KEY_COUNTER_MAX, COUNTER_MAX.get(), 2);

	}

	/**
	 * 問題の期限
	 *
	 * @return	期限
	 */
	public static Duration expires () {

		Duration expires = EXPIRES.get();

		if (expires.isNegative() || expires.isZero()) {
			throw new IllegalStateException("%s は 0 より長くしてください（いまは %s）".formatted(KEY_EXPIRES, expires));
		}

		return expires;

	}

	private static int positive (String key, int value, int min) {

		if (value < min) {
			throw new IllegalStateException("%s は %d 以上にしてください（いまは %d）".formatted(key, min, value));
		}

		return value;

	}

}
