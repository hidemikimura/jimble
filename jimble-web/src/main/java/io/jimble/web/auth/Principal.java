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
 * <h2>なぜ record ではないのか（要件 D-157）</h2>
 * <p>
 * <b>「3つだけ」という判断と、「3つで固定する」という約束は別物である。</b>
 * 上の理由は 1.0 以降も通るが、<b>record はコンポーネントを1つも足せない</b>——
 * テナント ID も、複数役割も、OIDC の provider も、
 * <b>入れたくなった時点で別の型を作って移行させるしかなくなる。</b>
 * </p>
 *
 * <p>
 * この型は {@code Auth.login} / {@code Auth.principal} / {@code Remember.restore} /
 * {@code Oidc.callback} / {@code Mfa.pending} の<b>5つの公開シグネチャすべてに出ている</b>ので、
 * 差し替えの費用がいちばん高い場所にある。<b>0.x のうちに record をやめておく。</b>
 * </p>
 *
 * <p>
 * <b>読み方は変わらない。</b>{@code id()} / {@code name()} / {@code role()} はそのままである。
 * 変わったのは<b>作り方だけ</b>で、{@code new Principal(...)} ではなく {@link #of} を使う。
 * </p>
 */
public final class Principal {

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

	/* 利用者の ID（0 は未ログイン） */
	private final long id;

	/* 表示名 */
	private final String name;

	/* 役割。空なら役割なし */
	private final String role;

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * <b>非公開である。</b>公開すると、あとから項目を足したときに
	 * <b>引数の数が増えて呼び出し側が壊れる</b>——{@link #of} なら古い形を残せる。
	 * </p>
	 *
	 * @param id	利用者の ID
	 * @param name	表示名
	 * @param role	役割
	 */
	private Principal (long id, String name, String role) {

		this.id = id;
		this.name = name == null ? "" : name;
		this.role = role == null ? "" : role;

	}

	// region 読む

	/**
	 * 利用者の ID
	 *
	 * @return	ID（0 は未ログイン）
	 */
	public long id () {

		return id;

	}

	/**
	 * 表示名
	 *
	 * @return	表示名（無ければ空文字）
	 */
	public String name () {

		return name;

	}

	/**
	 * 役割
	 *
	 * @return	役割（無ければ空文字）
	 */
	public String role () {

		return role;

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
	 * <p>
	 * <b>完全一致である。</b>「admin は editor を含む」のような対応表は持たない
	 * （<a href="https://jimble.io/ja/auth">認証</a>）。
	 * </p>
	 *
	 * @param role	役割
	 * @return	持っている場合 = true
	 */
	public boolean hasRole (String role) {

		return this.role.equals(role);

	}

	// endregion

	// region 作る

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

	// endregion

	// region 値としての振る舞い

	/*
	 * <b>record をやめたので、ここは自分で書く。</b>
	 * 項目を足したときに<b>ここへ足し忘れると、別人が同じ人として扱われる</b>——
	 * セッションの比較や、テストの assertEquals が黙って通るようになる。
	 * PrincipalTest がそれを見張っている。
	 */

	@Override
	public boolean equals (Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Principal other)) {
			return false;
		}

		return id == other.id
			&& name.equals(other.name)
			&& role.equals(other.role);

	}

	@Override
	public int hashCode () {

		int result = Long.hashCode(id);

		result = 31 * result + name.hashCode();
		result = 31 * result + role.hashCode();

		return result;

	}

	@Override
	public String toString () {

		return "Principal[id=%d, name=%s, role=%s]".formatted(id, name, role);

	}

	// endregion

}
