package io.jimble.web.auth;

/**
 * ログインしている人（要件 F-W-28）
 *
 * <h2>なぜ3つだけなのか</h2>
 * <p>
 * <b>セッションに入るのはここに書いた3つの値だけ</b>である。
 * 利用者のオブジェクトを丸ごと入れると、
 * </p>
 * <ul>
 *   <li>DB で名前を直しても<b>ログインし直すまで古い名前のまま</b>になる</li>
 *   <li>権限を剥奪しても<b>セッションが切れるまで効かない</b></li>
 *   <li>Cookie セッションのときは、<b>その全部がブラウザへ出ていく</b></li>
 * </ul>
 *
 * <p>
 * <b>「誰か」を決めるのに要る最小限だけを持ち、残りは要るときに DB から引く。</b>
 * 引くのが重いなら、それはキャッシュ（{@code ICache}）の仕事である。
 * </p>
 *
 * @param id	利用者の ID（0 は未ログイン）
 * @param name	表示名
 * @param role	役割。空なら役割なし
 */
public record Principal (long id, String name, String role) {

	/**
	 * ログインしていない人
	 *
	 * <p>
	 * <b>{@code null} は返さない。</b>「ログインしていない」を {@code null} で表すと、
	 * <b>判定を書き忘れたコードが NullPointerException で落ちる</b>——
	 * それは「入れてはいけない人を入れた」より良いが、
	 * <b>500 は 401 ではない</b>ので、利用者にも運用にも何も伝わらない。
	 * </p>
	 */
	public static final Principal ANONYMOUS = new Principal(0, "", "");

	/**
	 * コンパクトコンストラクタ
	 */
	public Principal {

		name = name == null ? "" : name;
		role = role == null ? "" : role;

	}

	/**
	 * ログインしているか
	 *
	 * @return	している場合 = true
	 */
	public boolean isAuthenticated () {

		return id > 0;

	}

	/**
	 * 役割を持っているか
	 *
	 * @param role	役割
	 * @return	持っている場合 = true
	 */
	public boolean hasRole (String role) {

		return this.role.equals(role);

	}

	/**
	 * 作る
	 *
	 * @param id	ID
	 * @param name	表示名
	 * @param role	役割
	 * @return	Principal
	 */
	public static Principal of (long id, String name, String role) {

		return new Principal(id, name, role);

	}

	/**
	 * 役割なしで作る
	 *
	 * @param id	ID
	 * @param name	表示名
	 * @return	Principal
	 */
	public static Principal of (long id, String name) {

		return new Principal(id, name, "");

	}

}
