/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_jobs_example;

import io.jimble.db.sql.definition.schema.AbstractSchema;

/**
 * approval_jobs_example（スキーマの実体。テーブルの定数は {@link ApprovalJobsExample}）
 *
 * <p>
 * テーブルクラスはこちらを使う。{@link ApprovalJobsExample} を使うと、クラスの初期化が輪になり、
 * 2つのスレッドが同時に初めて触ったときに止まる。
 * </p>
 */
public class ApprovalJobsExampleSchema extends AbstractSchema {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_jobs_example"; }

	/**
	 * テーブルの定数を持つクラス（{@link #tableList()} が読む。初期化はそのときまで遅らせる）
	 *
	 * @return	クラス
	 */
	@Override
	protected Class<?> tableHolder () { return ApprovalJobsExample.class; }

}
