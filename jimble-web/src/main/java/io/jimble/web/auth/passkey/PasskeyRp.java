package io.jimble.web.auth.passkey;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * パスキーを結びつけるサイト（WebAuthn の Relying Party。D-262）
 *
 * <p>
 * ふつうは設定（{@code auth.passkey.*}）から作る（{@link #fromConf()}）。
 * <b>クライアントごとにドメインが違う SaaS</b> では、リクエストのたびにアプリが決めて渡す。
 * </p>
 *
 * <pre>{@code
 * // クライアントの表から引く（Host ヘッダをそのまま rp_id にしない）
 * Tenant tenant = Tenants.byHost(context.request().host());
 * PasskeyRp rp = PasskeyRp.of(tenant.domain(), tenant.name());   // オリジンは https://<domain>
 *
 * c.response().json(Passkey.loginOptions(c, rp));
 * Passkey.login(c, rp, c.request().bodyJson(), App::findPrincipal);
 * }</pre>
 *
 * <p>
 * パスキーは rp_id ごとに分けて持つ。<b>あるクライアントで登録したパスキーは、ほかのクライアントでは使えない</b>
 * （認証器も rp_id ごとに鍵を分けるので、もともと使えない。jimble も表の rp_id で確かめる）。
 * </p>
 *
 * @param id		パスキーを結びつけるドメイン（スキームもポートも付けない）
 * @param name		登録のときにブラウザが出す名前
 * @param origins	受け付けるオリジン（{@code https://example.com} の形）
 */
public record PasskeyRp(String id, String name, List<String> origins) {

	/**
	 * 作る（形を確かめる）
	 *
	 * @param id		パスキーを結びつけるドメイン
	 * @param name		登録のときにブラウザが出す名前（空なら id）
	 * @param origins	受け付けるオリジン（空なら https:// + id）
	 * @throws IllegalArgumentException	形が違う場合
	 */
	public PasskeyRp {

		id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);

		if (id.isEmpty()) {
			throw new IllegalArgumentException("パスキーの rp_id がありません（パスキーを結びつけるドメイン。例 \"example.com\"）");
		}

		if (!id.matches("[a-z0-9.-]+") || id.startsWith(".") || id.endsWith(".") || id.contains("..")) {
			throw new IllegalArgumentException(
				"パスキーの rp_id はドメインだけを書いてください（https:// やポート、パスは付けない）: " + id);
		}

		name = name == null || name.isBlank() ? id : name.strip();

		List<String> list = origins == null || origins.isEmpty()
			? List.of("https://" + id)
			: origins.stream().map(origin -> origin.trim().replaceAll("/+$", "")).toList();

		for (String origin : list) {
			checkOrigin(id, origin);
		}

		origins = list;

	}

	/**
	 * 作る（オリジンは https:// + id）
	 *
	 * @param id	パスキーを結びつけるドメイン
	 * @param name	登録のときにブラウザが出す名前
	 * @return	サイト
	 */
	public static PasskeyRp of (String id, String name) {

		return new PasskeyRp(id, name, List.of());

	}

	/**
	 * 作る
	 *
	 * @param id		パスキーを結びつけるドメイン
	 * @param name		登録のときにブラウザが出す名前
	 * @param origins	受け付けるオリジン
	 * @return	サイト
	 */
	public static PasskeyRp of (String id, String name, List<String> origins) {

		return new PasskeyRp(id, name, origins);

	}

	/**
	 * 設定（{@code auth.passkey.*}）から作る
	 *
	 * @return	サイト
	 * @throws IllegalStateException	{@code auth.passkey.rp_id} を書いていない場合
	 */
	public static PasskeyRp fromConf () {

		return PasskeyConf.rp();

	}

	/**
	 * オリジンが、この rp_id で使えるものか
	 *
	 * <p>
	 * WebAuthn は、rp_id がオリジンのホストそのものか、その親のドメインであることを求める（ブラウザも断る）。
	 * 書き間違い（別のクライアントのドメインを並べてしまう、など）を、ここで見つける。
	 * </p>
	 */
	private static void checkOrigin (String id, String origin) {

		URI uri;

		try {
			uri = URI.create(origin);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("パスキーのオリジンが読めません: " + origin, ex);
		}

		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

		if (host.isEmpty() || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
			|| uri.getRawQuery() != null || uri.getRawFragment() != null) {
			throw new IllegalArgumentException("パスキーのオリジンは https://ホスト[:ポート] の形で書いてください: " + origin);
		}

		boolean local = host.equals("localhost") || host.endsWith(".localhost");

		if (!scheme.equals("https") && !(scheme.equals("http") && local)) {
			throw new IllegalArgumentException("パスキーのオリジンは https にしてください（http は localhost だけ）: " + origin);
		}

		if (!host.equals(id) && !host.endsWith("." + id)) {
			throw new IllegalArgumentException(
				"パスキーのオリジン %s は rp_id %s では使えません（ホストが rp_id かそのサブドメインであること）".formatted(origin, id));
		}

	}

}
