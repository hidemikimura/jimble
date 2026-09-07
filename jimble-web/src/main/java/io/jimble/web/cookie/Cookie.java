package io.jimble.web.cookie;

/**
 * Cookie
 *
 * <p>
 * <b>helidon の型は使わない。</b>{@code Set-Cookie} の文字列は {@link #toSetCookie()} で作る。
 * </p>
 *
 * <p>
 * 値の署名・暗号化はここではやらない。{@link Cookies} が読み書きのときに行う。
 * </p>
 */
public final class Cookie {

	/** 消すときの MaxAge */
	public static final long MAX_AGE_DELETE = 0;

	/** 指定しないときの MaxAge（セッション Cookie） */
	public static final long MAX_AGE_SESSION = -1;

	/* 名前 */
	private final String name;

	/* 値 */
	private String value;

	/* 有効秒数 */
	private long maxAge = MAX_AGE_SESSION;

	/* パス */
	private String path = "/";

	/* ドメイン */
	private String domain;

	/* HTTPS のみ */
	private boolean secure;

	/* JavaScript から読めない */
	private boolean httpOnly = true;

	/* SameSite */
	private SameSite sameSite;

	/**
	 * コンストラクタ
	 *
	 * @param name	名前
	 * @param value	値（null で削除）
	 */
	public Cookie (String name, String value) {

		this.name = name;
		this.value = value;

	}

	// region 設定

	/**
	 * 値を設定する
	 *
	 * @param value	値
	 * @return	Cookie
	 */
	public Cookie value (String value) {

		this.value = value;
		return this;

	}

	/**
	 * 有効秒数を設定する
	 *
	 * @param maxAge	秒数（{@value #MAX_AGE_DELETE} で削除、{@value #MAX_AGE_SESSION} で指定なし）
	 * @return	Cookie
	 */
	public Cookie maxAge (long maxAge) {

		this.maxAge = maxAge;
		return this;

	}

	/**
	 * パスを設定する
	 *
	 * @param path	パス
	 * @return	Cookie
	 */
	public Cookie path (String path) {

		this.path = path;
		return this;

	}

	/**
	 * ドメインを設定する
	 *
	 * @param domain	ドメイン
	 * @return	Cookie
	 */
	public Cookie domain (String domain) {

		this.domain = domain;
		return this;

	}

	/**
	 * HTTPS のみにする
	 *
	 * @param secure	HTTPS のみなら true
	 * @return	Cookie
	 */
	public Cookie secure (boolean secure) {

		this.secure = secure;
		return this;

	}

	/**
	 * JavaScript から読めなくする
	 *
	 * @param httpOnly	読めなくするなら true
	 * @return	Cookie
	 */
	public Cookie httpOnly (boolean httpOnly) {

		this.httpOnly = httpOnly;
		return this;

	}

	/**
	 * SameSite を設定する
	 *
	 * @param sameSite	SameSite
	 * @return	Cookie
	 */
	public Cookie sameSite (SameSite sameSite) {

		this.sameSite = sameSite;
		return this;

	}

	// endregion

	// region 取得

	/**
	 * 名前
	 *
	 * @return	名前
	 */
	public String name () {

		return name;

	}

	/**
	 * 値
	 *
	 * @return	値
	 */
	public String value () {

		return value;

	}

	/**
	 * 有効秒数
	 *
	 * @return	秒数
	 */
	public long maxAge () {

		return maxAge;

	}

	/**
	 * SameSite
	 *
	 * @return	SameSite
	 */
	public SameSite sameSite () {

		return sameSite;

	}

	// endregion

	/**
	 * {@code Set-Cookie} ヘッダの値を作る
	 *
	 * <p>値が null のときは即時失効（{@code Max-Age=0}）にする。</p>
	 *
	 * @return	ヘッダ値
	 */
	public String toSetCookie () {

		StringBuilder sb = new StringBuilder();

		sb.append(name).append('=');
		if (value != null) {
			sb.append(value);
		}

		if (path != null && !path.isEmpty()) {
			sb.append("; Path=").append(path);
		}

		if (domain != null && !domain.isEmpty()) {
			sb.append("; Domain=").append(domain);
		}

		if (value == null) {
			sb.append("; Max-Age=0");
		} else if (maxAge >= 0) {
			sb.append("; Max-Age=").append(maxAge);
		}

		if (secure) {
			sb.append("; Secure");
		}

		if (httpOnly) {
			sb.append("; HttpOnly");
		}

		if (sameSite != null) {
			sb.append("; SameSite=").append(sameSite.value());
		}

		return sb.toString();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return toSetCookie();

	}

}
