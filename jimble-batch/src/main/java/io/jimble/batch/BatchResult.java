package io.jimble.batch;

/**
 * バッチ実行の結果
 *
 * <p>
 * <b>移送元はここが無く、実行されなかったときは黙って {@code return} していた。</b>
 * マスタに無い / 無効 / 同時実行数オーバー / 全停止フラグ、
 * どれでも<b>「起動したのに何も起きない」だけが残る。</b>
 * 呼んだ側が理由を知れるようにする（要件 F-X-05）。
 * </p>
 */
public enum BatchResult {

	/** 最後まで走った */
	completed(true),

	/** 走ったが例外で終わった */
	error(true),

	/** 走ったが中断された */
	canceled(true),

	/** 登録されていない（要件 F-B-09） */
	skipped_not_registered(false),

	/** マスタに行が無い */
	skipped_no_master(false),

	/** マスタで無効になっている */
	skipped_disabled(false),

	/** 同時実行数の上限に達している（要件 F-B-05） */
	skipped_concurrent(false),

	/** 全バッチ停止フラグが立っている（要件 F-B-07） */
	skipped_all_stopped(false),

	/** 引数が足りない */
	invalid_args(false)

	;

	/* 実行されたか */
	private final boolean executed;

	/**
	 * コンストラクタ
	 *
	 * @param executed	実行された場合 = true
	 */
	BatchResult (boolean executed) {

		this.executed = executed;

	}

	/**
	 * バッチ本体が走ったか
	 *
	 * @return	走った場合 = true
	 */
	public boolean isExecuted () {

		return executed;

	}

	/**
	 * 走らなかったか
	 *
	 * @return	走らなかった場合 = true
	 */
	public boolean isSkipped () {

		return !executed;

	}

}
