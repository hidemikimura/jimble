package io.jimble.db.migration.code;

/**
 * コードマイグレーションの状態
 */
public enum MigrationCodeState {

	/** 登録済み・未実行 */
	waiting,

	/** 実行中 */
	running,

	/** 完了 */
	completed,

	/** 失敗 */
	error

}
