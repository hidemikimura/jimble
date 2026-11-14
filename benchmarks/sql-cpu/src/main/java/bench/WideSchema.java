package bench;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.data.definition.ISchema;

import java.util.ArrayList;
import java.util.List;

/**
 * 列の多い表（EC の注文・明細・顧客ほどの幅）。生成物と同じく、列一覧は静的に持ち instance() は毎回 new
 */
public final class WideSchema extends AbstractSchema {

	@Override
	public String name () {
		return "jimble_test";
	}

	static List<Column> columns (Table table, String prefix, int count) {

		List<Column> list = new ArrayList<>();
		list.add(new Column(table, "id", long.class, false, null, true));
		for (int i = 1; i < count; i++) {
			list.add(new Column(table, prefix + "_column_" + i, String.class, true, null, false));
		}
		return List.copyOf(list);

	}

	/** 注文（60 列） */
	public static class Orders extends Table {

		static final List<Column> COLUMNS = columns(instance(), "order", 60);
		public static final Column id = COLUMNS.get(0);
		public static final Column customer_id = COLUMNS.get(1);
		public static final Column status = COLUMNS.get(2);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		public Orders (ISchema schema, String name) { super(schema, name); }

		public static Orders instance () { return new Orders(new WideSchema(), "bench_orders"); }

	}

	/** 明細（40 列） */
	public static class OrderItem extends Table {

		static final List<Column> COLUMNS = columns(instance(), "item", 40);
		public static final Column id = COLUMNS.get(0);
		public static final Column order_id = COLUMNS.get(1);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		public OrderItem (ISchema schema, String name) { super(schema, name); }

		public static OrderItem instance () { return new OrderItem(new WideSchema(), "bench_order_item"); }

	}

	/** 顧客（30 列） */
	public static class Customer extends Table {

		static final List<Column> COLUMNS = columns(instance(), "customer", 30);
		public static final Column id = COLUMNS.get(0);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		public Customer (ISchema schema, String name) { super(schema, name); }

		public static Customer instance () { return new Customer(new WideSchema(), "bench_customer"); }

	}

}
