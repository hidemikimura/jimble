package bench;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.data.definition.ISchema;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 生成物と同じ形のテーブル定義（instance() は毎回 new、列一覧は静的に持つ）
 */
public final class BenchSchema extends AbstractSchema {

	@Override
	public String name () {
		return "jimble_test";
	}

	/** 商品（購入導線でいちばん読む形：主キーか shop_id で 1〜数行） */
	public static class Product extends Table {

		public static final Column id = new Column(instance(), "id", long.class, false, null, true);
		public static final Column shop_id = new Column(instance(), "shop_id", long.class, false, null, false);
		public static final Column code = new Column(instance(), "code", String.class, false, null, false);
		public static final Column name = new Column(instance(), "name", String.class, false, null, false);
		public static final Column price = new Column(instance(), "price", BigDecimal.class, false, null, false);
		public static final Column stock = new Column(instance(), "stock", int.class, false, null, false);
		public static final Column status = new Column(instance(), "status", int.class, false, null, false);
		public static final Column description = new Column(instance(), "description", String.class, true, null, false);
		public static final Column sort_order = new Column(instance(), "sort_order", int.class, false, null, false);
		public static final Column created_at = new Column(instance(), "created_at", Date.class, false, null, false);
		public static final Column updated_at = new Column(instance(), "updated_at", Date.class, false, null, false);
		public static final Column deleted_at = new Column(instance(), "deleted_at", Date.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, shop_id, code, name, price, stock, status
			, description, sort_order, created_at, updated_at, deleted_at);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		public Product (ISchema schema, String name) { super(schema, name); }

		public static Product instance () { return new Product(new BenchSchema(), "bench_product"); }

	}

	/** ショップ */
	public static class Shop extends Table {

		public static final Column id = new Column(instance(), "id", long.class, false, null, true);
		public static final Column code = new Column(instance(), "code", String.class, false, null, false);
		public static final Column name = new Column(instance(), "name", String.class, false, null, false);
		public static final Column deleted_at = new Column(instance(), "deleted_at", Date.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, code, name, deleted_at);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		public Shop (ISchema schema, String name) { super(schema, name); }

		public static Shop instance () { return new Shop(new BenchSchema(), "bench_shop"); }

	}

}
