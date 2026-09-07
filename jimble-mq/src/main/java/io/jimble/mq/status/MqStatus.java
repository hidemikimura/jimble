package io.jimble.mq.status;

/**
 * MQ のステータス
 *
 * <p>
 * <b>{@link #waiting} だけが処理の対象</b>である。それ以外は放っておかれる。
 * </p>
 */
public enum MqStatus {

	/** 待機中（これだけが拾われる） */
	waiting("待機中"),

	/** 処理中 */
	running("処理中"),

	/** 完了（行は消える） */
	completed("完了"),

	/** エラー。リトライの上限まではここを経て {@link #waiting} に戻る */
	error("エラー"),

	/** リトライの上限に達した（デッドレター。要件 F-M-04） */
	dead("処理不能"),

	/** キャンセルされた */
	cancel("キャンセル"),

	/** 手で見るために残す */
	preservation("保存")

	;

	/* 表示名 */
	private final String viewName;

	/**
	 * コンストラクタ
	 *
	 * @param viewName	表示名
	 */
	MqStatus (String viewName) {

		this.viewName = viewName;

	}

	/**
	 * 表示名
	 *
	 * @return	表示名
	 */
	public String viewName () {

		return viewName;

	}

}
