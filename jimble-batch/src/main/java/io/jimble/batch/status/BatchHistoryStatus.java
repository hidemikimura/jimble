package io.jimble.batch.status;

/**
 * バッチ履歴のステータス
 */
public enum BatchHistoryStatus {

	/** 実行中 */
	in_process,

	/** 完了 */
	completed,

	/** エラー */
	error,

	/** 中断された */
	canceled,

	/** プロセスごと落とされた */
	sigint

	;

}
