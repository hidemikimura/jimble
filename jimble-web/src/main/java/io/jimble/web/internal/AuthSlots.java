package io.jimble.web.internal;

import io.jimble.web.context.WebContext;

/**
 * ログインの種別（{@code Auth.REALM}）ごとの、セッションの中の置き場所（要件 D-185）
 *
 * <p>
 * <b>内部の都合でしかない。</b>{@code Auth}（{@code io.jimble.web.auth}）と
 * {@code Mfa}（{@code io.jimble.web.auth.mfa}）がパッケージをまたいで同じ鍵の形を使うので、
 * 両方から見える所に置いている。公開 API にすると、<b>セッションの鍵の形が 2.0 まで固まる</b>
 * （1.0 の約束。要件 NF-C-03）。
 * </p>
 */
public final class AuthSlots {

	/** セッションに入れる鍵：二要素認証の途中の利用者 ID */
	public static final String MFA_PENDING_ID = "__mfa_pending_id";

	/** セッションに入れる鍵：二要素認証の途中の表示名 */
	public static final String MFA_PENDING_NAME = "__mfa_pending_name";

	/** セッションに入れる鍵：二要素認証の途中の役割 */
	public static final String MFA_PENDING_ROLE = "__mfa_pending_role";

	/** セッションに入れる鍵：二要素認証をいつ始めたか */
	public static final String MFA_PENDING_AT = "__mfa_pending_at";

	/** セッションに入れる鍵：二要素認証の途中の人の、ID の名前空間（D-182） */
	public static final String MFA_PENDING_REALM = "__mfa_pending_realm";

	/* 二要素認証の途中の状態の鍵すべて */
	private static final String[] MFA_PENDING_KEYS = {
		MFA_PENDING_ID, MFA_PENDING_NAME, MFA_PENDING_ROLE, MFA_PENDING_AT, MFA_PENDING_REALM };

	private AuthSlots () {
	}

	/**
	 * 種別つきのセッションの鍵
	 *
	 * <p>
	 * <b>種別なしなら鍵そのまま。</b>上げる前のセッションがそのまま読める。
	 * </p>
	 *
	 * @param key	鍵
	 * @param realm	種別。種別なしなら空文字
	 * @return	セッションの鍵
	 */
	public static String key (String key, String realm) {

		return realm.isEmpty() ? key : key + "@" + realm;

	}

	/**
	 * その種別の、二要素認証の途中の状態を消す（保存はしない。{@code Auth.logout} から呼ぶ）
	 *
	 * @param context	コンテキスト
	 * @param realm		ログインの種別
	 */
	public static void clearMfaPending (WebContext context, String realm) {

		for (String name : MFA_PENDING_KEYS) {
			context.session().remove(key(name, realm));
		}

	}

}
