package io.jimble.web.auth.oidc;

import io.jimble.util.data.Data;

/**
 * OIDC のプロバイダが名乗った利用者（要件 F-W-31）
 *
 * <p>
 * <b>検証を通ったものだけがここに入る。</b>
 * 署名・{@code iss} / {@code aud} / {@code exp} / {@code nonce} は
 * {@link IdToken} が見終わっている。
 * </p>
 *
 * <p>
 * <b>これは「誰か」であって「入ってよいか」ではない。</b>
 * アプリの利用者に結び付けるのは、{@link Oidc#callback} に渡す関数の仕事である。
 * </p>
 *
 * @param provider		設定に書いた名前（{@code google} など）
 * @param subject		プロバイダの中での ID（{@code sub}）。<b>ここが利用者の識別子</b>
 * @param email			メール。<b>プロバイダが返さないこともある</b>
 * @param emailVerified	そのメールをプロバイダが検証したか
 * @param name			表示名
 * @param claims		ID トークンの中身すべて
 */
public record OidcUser(
	String provider
	, String subject
	, String email
	, boolean emailVerified
	, String name
	, Data claims
) {

	/**
	 * 一意に引くための鍵
	 *
	 * <p>
	 * <b>{@code sub} だけで引かない。</b>
	 * {@code sub} が一意なのは<b>そのプロバイダの中だけ</b>で、
	 * 別のプロバイダが同じ文字列を返さない保証はない——
	 * 混ぜて持つと、<b>Google の 12345 と社内 IdP の 12345 が同じ人になる</b>。
	 * </p>
	 *
	 * @return	{@code provider:subject}
	 */
	public String key () {

		return provider + ":" + subject;

	}

}
