package io.jimble.web.captcha;

/**
 * ALTCHA の答えを確かめた結果
 */
public enum AltchaResult {

	/** 通してよい */
	VERIFIED("確かめました"),

	/** 送られていない */
	MISSING("ロボットでないことの確認（ALTCHA）が送られていません"),

	/** 形が正しくない（base64・JSON・項目） */
	MALFORMED("ロボットでないことの確認（ALTCHA）の形が正しくありません"),

	/** 期限が切れている */
	EXPIRED("ロボットでないことの確認（ALTCHA）の期限が切れています。もう一度確かめてください"),

	/** 署名か答えが合わない（書き換えた・解いていない） */
	INVALID("ロボットでないことを確かめられませんでした"),

	/** 一度使った答えをもう一度送ってきた */
	REPLAYED("ロボットでないことの確認（ALTCHA）はもう使われています。もう一度確かめてください");

	/* 断るときの文言 */
	private final String message;

	AltchaResult (String message) {

		this.message = message;

	}

	/**
	 * 通してよいか
	 *
	 * @return	通してよい場合 = true
	 */
	public boolean verified () {

		return this == VERIFIED;

	}

	/**
	 * 断るときの文言（利用者に見せてよい）
	 *
	 * @return	文言
	 */
	public String message () {

		return message;

	}

}
