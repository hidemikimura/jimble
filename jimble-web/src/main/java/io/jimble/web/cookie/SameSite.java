package io.jimble.web.cookie;

/**
 * SameSite 属性
 */
public enum SameSite {

	/** クロスサイトでも送る（Secure が必須） */
	NONE("None"),

	/** 同一サイトのみ */
	STRICT("Strict"),

	/** トップレベル遷移の GET までは送る */
	LAX("Lax");

	/* Set-Cookie に書く値 */
	private final String value;

	SameSite (String value) {

		this.value = value;

	}

	/**
	 * Set-Cookie に書く値
	 *
	 * @return	値
	 */
	public String value () {

		return value;

	}

	/**
	 * 文字列から解決する
	 *
	 * @param name	名前（大小問わず）
	 * @return	SameSite（該当なしは null）
	 */
	public static SameSite of (String name) {

		if (name == null || name.isEmpty()) {
			return null;
		}

		for (SameSite sameSite : values()) {
			if (sameSite.name().equalsIgnoreCase(name)) {
				return sameSite;
			}
		}

		return null;

	}

}
