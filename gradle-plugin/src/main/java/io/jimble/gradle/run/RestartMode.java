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
	 * <p>
	 * <b>読めない名前は落とす</b>（要件 D-89）。
	 * 前は黙って {@link #ON_REQUEST} に倒していたので、
	 * 書き間違えても<b>「設定したのに効かない」だけ</b>が残った。
	 * 実際、ドキュメントに無い名前（{@code on_change}）が載っていて、
	 * そのとおり書いても何も起きなかった。
	 * </p>
	 *
	 * <p>指定が無い（null・空）ときは {@link #ON_REQUEST}。</p>
	 *
	 * @param key	名前
	 * @return	きっかけ
	 * @throws IllegalArgumentException	読めない名前だった場合
	 */
	public static RestartMode of (String key) {

		if (key == null || key.isBlank()) {
			return ON_REQUEST;
		}

		for (RestartMode mode : values()) {
			if (mode.key.equalsIgnoreCase(key.trim())) {
				return mode;
			}
		}

		throw new IllegalArgumentException(
			"jimbleRun { restartMode } に書けない値です: %s%n  書けるのは %s です。"
				.formatted(key, keys()));

	}

	/**
	 * 書ける名前
	 *
	 * @return	名前（カンマ区切り）
	 */
	private static String keys () {

		StringBuilder result = new StringBuilder();

		for (RestartMode mode : values()) {

			if (!result.isEmpty()) {
				result.append(" / ");
			}

			result.append(mode.key);

		}

		return result.toString();

	}

}
