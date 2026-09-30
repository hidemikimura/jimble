/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_data_audit_example;

import io.jimble.db.sql.definition.schema.AbstractSchema;

/**
 * approval_data_audit_example（スキーマの実体。テーブルの定数は {@link ApprovalDataAuditExample}）
 *
 * <p>
 * テーブルクラスはこちらを使う。{@link ApprovalDataAuditExample} を使うと、クラスの初期化が輪になり、
 * 2つのスレッドが同時に初めて触ったときに止まる。
 * </p>
 */
public class ApprovalDataAuditExampleSchema extends AbstractSchema {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_data_audit_example"; }

	/**
	 * テーブルの定数を持つクラス（{@link #tableList()} が読む。初期化はそのときまで遅らせる）
	 *
	 * @return	クラス
	 */
	@Override
	protected Class<?> tableHolder () { return ApprovalDataAuditExample.class; }

}
