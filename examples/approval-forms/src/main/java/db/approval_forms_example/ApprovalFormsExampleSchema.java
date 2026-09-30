/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.approval_forms_example;

import io.jimble.db.sql.definition.schema.AbstractSchema;

/**
 * approval_forms_example（スキーマの実体。テーブルの定数は {@link ApprovalFormsExample}）
 *
 * <p>
 * テーブルクラスはこちらを使う。{@link ApprovalFormsExample} を使うと、クラスの初期化が輪になり、
 * 2つのスレッドが同時に初めて触ったときに止まる。
 * </p>
 */
public class ApprovalFormsExampleSchema extends AbstractSchema {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "approval_forms_example"; }

	/**
	 * テーブルの定数を持つクラス（{@link #tableList()} が読む。初期化はそのときまで遅らせる）
	 *
	 * @return	クラス
	 */
	@Override
	protected Class<?> tableHolder () { return ApprovalFormsExample.class; }

}
