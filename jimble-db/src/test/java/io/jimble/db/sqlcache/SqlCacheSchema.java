package io.jimble.db.sqlcache;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

import java.util.List;

/**
 * SQL 結果キャッシュのテスト用スキーマ（要件 F-D-28）
 *
 * <p>
 * 生成コードと同じ形で、<b>主キーと一意キーを宣言してある</b>。
 * </p>
 */
public final class SqlCacheSchema extends AbstractSchema {

	/** インスタンス */
	public static final SqlCacheSchema INSTANCE = new SqlCacheSchema();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () {

		return "jimble_test";

	}

	/**
	 * customer テーブル
	 */
	public static final class Customer extends Table {

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** shop_id */
		public static final Column shop_id = new Column(instance(), "shop_id", long.class, false, null, false);

		/** code（一意） */
		public static final Column code = new Column(instance(), "code", String.class, true, null, false);

		/** name */
		public static final Column name = new Column(instance(), "name", String.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, shop_id, code, name);

		private static final List<List<Column>> UNIQUE_KEYS = List.of(List.of(code));

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected List<List<Column>> declareUniqueKeys () { return UNIQUE_KEYS; }

		private Customer () { super(INSTANCE, "customer"); }

		/**
		 * インスタンス
		 *
		 * @return	インスタンス
		 */
		public static Customer instance () { return new Customer(); }

	}

	/**
	 * shop テーブル
	 */
	public static final class Shop extends Table {

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** name */
		public static final Column name = new Column(instance(), "name", String.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, name);

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		private Shop () { super(INSTANCE, "shop"); }

		/**
		 * インスタンス
		 *
		 * @return	インスタンス
		 */
		public static Shop instance () { return new Shop(); }

	}

	/**
	 * order_item テーブル（customer に対して 1 対 N）
	 */
	public static final class OrderItem extends Table {

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** customer_id */
		public static final Column customer_id = new Column(instance(), "customer_id", long.class, false, null, false);

		/** amount */
		public static final Column amount = new Column(instance(), "amount", long.class, false, 0L, false);

		private static final List<Column> COLUMNS = List.of(id, customer_id, amount);

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		private OrderItem () { super(INSTANCE, "order_item"); }

		/**
		 * インスタンス
		 *
		 * @return	インスタンス
		 */
		public static OrderItem instance () { return new OrderItem(); }

	}

}
