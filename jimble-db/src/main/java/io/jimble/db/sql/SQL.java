package io.jimble.db.sql;

import io.jimble.util.data.definition.ITable;

/**
 * SQLビルダー
 */
public class SQL {

	/* バージョン */
	public static final long VERSION = 1;

	/**
	 * SELECT文SQLビルダーを作成する
	 *
	 * @param select    Select句
	 * @return	SQLビルダー
	 */
	public static SelectBuilder select (Object...select) {

		return new SelectBuilder(select);

	}

	/**
	 * 形を決めて、値だけを差し替える SELECT を作る（2.8.0）
	 *
	 * <pre>
	 * static final SelectTemplate PRODUCT_BY_ID = SQL.template(SQL.select()
	 *     .from(Product.instance())
	 *     .where(Product.id.eq(Dsl.param("id"))));
	 *
	 * Optional&lt;Data&gt; product = db.select(PRODUCT_BY_ID.bind("id", productId));
	 * </pre>
	 *
	 * <p>
	 * 値の場所に {@link io.jimble.db.sql.query.dsl.Dsl#param(String)}、{@code IN} / {@code NOT IN} の値の並びに
	 * {@link io.jimble.db.sql.query.dsl.Dsl#params(String)} を置く。SQL の字面は方言ごとに 1 回だけ作る。
	 * 渡したビルダーは、そのあと使わないこと（{@link SelectTemplate}）。
	 * </p>
	 *
	 * @param select	形
	 * @return	template
	 * @throws SqlBuildException	値の並びの印を IN / NOT IN 以外に置いた・同じ名前を 1 つの値と値の並びの両方に使ったとき
	 */
	public static SelectTemplate template (SelectBuilder select) {

		return new SelectTemplate(select);

	}

	/**
	 * INSERT文SQLビルダーを作成する
	 *
	 * @return	SQLビルダー
	 */
	public static InsertBuilder insert (ITable table) {

		return new InsertBuilder(table);

	}

	/**
	 * UPDATE文SQLビルダーを作成する
	 *
	 * @return	SQLビルダー
	 */
	public static UpdateBuilder update (ITable table) {

		return new UpdateBuilder(table);

	}

	/**
	 * DELETE文SQLビルダーを作成する
	 *
	 * @return	SQLビルダー
	 */
	public static DeleteBuilder delete (ITable table) {

		return new DeleteBuilder(table);

	}

}
