package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * DSL の結合テスト用のスキーマ（要件 F-D-31）
 *
 * <p>本番では {@code codegen} が同じ形のクラスを生成する。</p>
 */
public final class DslSchema extends AbstractSchema {

	/** インスタンス */
	public static final DslSchema INSTANCE = new DslSchema();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () {

		return "jimble_test";

	}

	/**
	 * dsl_item テーブル
	 */
	public static final class Item extends Table {

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** group_id */
		public static final Column group_id = new Column(instance(), "group_id", long.class, false, null, false);

		/** name */
		public static final Column name = new Column(instance(), "name", String.class, true, null, false);

		/** amount */
		public static final Column amount = new Column(instance(), "amount", long.class, false, null, false);

		/** at */
		public static final Column at = new Column(instance(), "at", java.util.Date.class, false, null, false);

		/**
		 * コンストラクタ
		 */
		private Item () {

			super(INSTANCE, "dsl_item");

		}

		/**
		 * インスタンス
		 *
		 * @return	インスタンス
		 */
		public static Item instance () {

			return new Item();

		}

	}

}
