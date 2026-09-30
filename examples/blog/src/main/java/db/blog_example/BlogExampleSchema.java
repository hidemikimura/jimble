/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */

package db.blog_example;

import io.jimble.db.sql.definition.schema.AbstractSchema;

/**
 * blog_example（スキーマの実体。テーブルの定数は {@link BlogExample}）
 *
 * <p>
 * テーブルクラスはこちらを使う。{@link BlogExample} を使うと、クラスの初期化が輪になり、
 * 2つのスレッドが同時に初めて触ったときに止まる。
 * </p>
 */
public class BlogExampleSchema extends AbstractSchema {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () { return "blog_example"; }

	/**
	 * テーブルの定数を持つクラス（{@link #tableList()} が読む。初期化はそのときまで遅らせる）
	 *
	 * @return	クラス
	 */
	@Override
	protected Class<?> tableHolder () { return BlogExample.class; }

}
