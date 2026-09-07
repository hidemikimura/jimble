package io.jimble.gradle.run;

/**
 * 再起動のきっかけ（要件 F-X-02）
 */
public enum RestartMode {

	/**
	 * 次のリクエストが来たときに作り直して再起動する
	 *
	 * <p>
	 * 保存しただけでは何もしない。<b>連続して保存しても再起動は1回</b>で、
	 * しかも「リロードしたときには必ず最新」になる。
	 * </p>
	 */
	ON_REQUEST("on_request"),

	/**
	 * 変更が落ち着いたらすぐ再起動する
	 */
	IMMEDIATE("immediate");

	/* 設定に書く名前 */
	private final String key;

	/**
	 * コンストラクタ
	 *
	 * @param key	設定に書く名前
	 */
	RestartMode (String key) {

		this.key = key;

	}

	/**
	 * 設定に書く名前
	 *
	 * @return	名前
	 */
	public String key () {

		return key;

	}

	/**
	 * 名前から引く
	 *
	 * @param key	名前
	 * @return	きっかけ。読めなければ {@link #ON_REQUEST}
	 */
	public static RestartMode of (String key) {

		if (key != null) {
			for (RestartMode mode : values()) {
				if (mode.key.equalsIgnoreCase(key.trim())) {
					return mode;
				}
			}
		}

		return ON_REQUEST;

	}

}
