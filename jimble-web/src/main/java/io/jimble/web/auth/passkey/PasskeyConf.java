package io.jimble.web.auth.passkey;

import io.jimble.util.conf.Conf;

import java.time.Duration;
import java.util.List;

/**
 * パスキーの設定（D-261）
 *
 * <pre>
 * auth {
 *     passkey {
 *         enabled = true
 *         rp_id   = "example.com"              # パスキーを結びつけるドメイン（必須）。あとから変えると、登録したパスキーが全部使えなくなる
 *         rp_name = "承認ワークフロー"          # 登録のときにブラウザが出す名前（空なら rp_id）
 *         origins = ["https://example.com"]    # 受け付けるオリジン（空なら https:// + rp_id）
 *         timeout = 5m                          # 始めてから終えるまで
 *     }
 * }
 * </pre>
 *
 * <p>
 * クライアントごとにドメインが違う SaaS では、設定ではなく {@link PasskeyRp} を作って渡す（D-262）。
 * </p>
 *
 * <p>
 * <b>{@code rp_id} はドメインそのもの</b>（スキームもポートも付けない）。サブドメインで動かすときに親のドメインを書くと、
 * 兄弟のサブドメインでも同じパスキーが使える。手元で試すなら {@code rp_id = "localhost"}、
 * {@code origins = ["http://localhost:9000"]}（ブラウザは localhost だけ http を許す）。
 * </p>
 */
public final class PasskeyConf {

	/** 使うか */
	public static final String KEY_ENABLED = "auth.passkey.enabled";

	/** パスキーを結びつけるドメイン */
	public static final String KEY_RP_ID = "auth.passkey.rp_id";

	/** 登録のときに出す名前 */
	public static final String KEY_RP_NAME = "auth.passkey.rp_name";

	/** 受け付けるオリジン */
	public static final String KEY_ORIGINS = "auth.passkey.origins";

	/** 始めてから終えるまで */
	public static final String KEY_TIMEOUT = "auth.passkey.timeout";

	/** 既定の、始めてから終えるまで */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

	private PasskeyConf () {
	}

	/**
	 * 使うか
	 *
	 * @return	使うなら true
	 */
	public static boolean enabled () {

		return Conf.conf().getBoolean(KEY_ENABLED, true);

	}

	/**
	 * 設定から作ったサイト
	 *
	 * <p>クライアントごとにドメインが違うなら、設定ではなく {@link PasskeyRp#of} で作って渡す。</p>
	 *
	 * @return	サイト
	 * @throws IllegalStateException	{@code auth.passkey.rp_id} を書いていない・形が違う場合
	 */
	public static PasskeyRp rp () {

		String rpId = Conf.conf().getString(KEY_RP_ID, "").trim();

		if (rpId.isEmpty()) {
			throw new IllegalStateException(
				"パスキーには %s が要ります（パスキーを結びつけるドメイン。例 \"example.com\"、手元なら \"localhost\"）。"
					.formatted(KEY_RP_ID)
					+ "クライアントごとにドメインが違うなら、PasskeyRp.of(...) を作って渡してください");
		}

		List<String> origins = Conf.conf().getStringListOptional(KEY_ORIGINS);

		try {
			return new PasskeyRp(rpId, Conf.conf().getString(KEY_RP_NAME, ""), origins == null ? List.of() : origins);
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("%s / %s の書き方が違います: %s".formatted(KEY_RP_ID, KEY_ORIGINS, ex.getMessage()), ex);
		}

	}

	/**
	 * 始めてから終えるまで
	 *
	 * @return	時間
	 */
	public static Duration timeout () {

		return Conf.atLeast(Conf.conf().getDuration(KEY_TIMEOUT, DEFAULT_TIMEOUT), Duration.ofSeconds(30));

	}

}
