package io.jimble.batch.status;

/**
 * バッチマスタのステータス
 */
public enum BatchMasterStatus {

	/** 有効 */
	enable,

	/** 無効 */
	disable,

	/** 登録が消えた（コードから居なくなった） */
	nothing

	;

}
