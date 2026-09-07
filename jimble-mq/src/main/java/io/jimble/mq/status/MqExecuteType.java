package io.jimble.mq.status;

/**
 * MQ の実行種別（要件 F-M-08）
 *
 * <p>
 * <b>処理時間の見込みで分ける。</b>
 * 長い処理が短い処理を待たせないよう、種別ごとに別のワーカーが回る。
 * </p>
 *
 * <p>
 * ここに書いてある数はスレッド数の<b>既定値</b>である。
 * 設定 {@code mq.thread_count.<種別>} で変えられる（{@code MqConf} 参照）。
 * </p>
 *
 * <p>
 * 移送元は種別ごとのスレッド数を<b>この enum と、MQ クラスの
 * {@code getExecuteTypeThreadCount()} の2箇所</b>に持っていた。
 * 設定に寄せて1箇所にした。
 * </p>
 */
public enum MqExecuteType {

	/** ごく短い（〜数百ミリ秒） */
	short_minus_time(1),

	/** 短い（〜1秒） */
	short_time(2),

	/** やや短い（〜数秒） */
	short_plus_time(3),

	/** ふつう（〜十数秒） */
	middle_time(4),

	/** やや長い（〜1分） */
	middle_plus_time(5),

	/** 長い（〜数分） */
	long_time(8),

	/** とても長い（それ以上） */
	long_plus_time(9)

	;

	/* 既定のスレッド数 */
	private final int defaultThreadCount;

	/**
	 * コンストラクタ
	 *
	 * @param defaultThreadCount	既定のスレッド数
	 */
	MqExecuteType (int defaultThreadCount) {

		this.defaultThreadCount = defaultThreadCount;

	}

	/**
	 * 既定のスレッド数
	 *
	 * @return	スレッド数
	 */
	public int defaultThreadCount () {

		return defaultThreadCount;

	}

}
