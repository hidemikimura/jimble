package bench;

import bench.BenchSchema.Product;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.Data;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 結果の行（表名で入れ子にした Data）から値を読む手間
 *
 * <ul>
 *   <li>{@code byColumn}：{@code row.getLong(Product.id)} のように列で 12 個読む（列ごとに表の Data を引く）</li>
 *   <li>{@code byTableData}：{@code row.getData(Product.instance())} で表の Data を 1 回取り、列名で 12 個読む</li>
 *   <li>{@code flattenThenRead}：{@code row.flattenTable(...)} で平らな写しを作ってから 12 個読む</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class DataReadBench {

	private static final List<Column> LONGS = List.of(Product.id, Product.shop_id);
	private static final List<Column> INTS = List.of(Product.stock, Product.status, Product.sort_order);
	private static final List<Column> STRINGS = List.of(Product.code, Product.name, Product.description);

	private Data row;

	@Setup
	public void setUp () {

		Data table = new Data();
		table.put("id", 1L);
		table.put("shop_id", 1L);
		table.put("code", "P1");
		table.put("name", "商品1");
		table.put("price", new BigDecimal("1200.00"));
		table.put("stock", 10);
		table.put("status", 1);
		table.put("description", "説明");
		table.put("sort_order", 1);
		table.put("created_at", new Date());
		table.put("updated_at", new Date());
		table.put("deleted_at", null);

		row = new Data();
		row.put("bench_product", table);

	}

	@Benchmark
	public void byColumn (Blackhole bh) {

		for (Column c : LONGS) {
			bh.consume(row.getLong(c));
		}
		for (Column c : INTS) {
			bh.consume(row.getInt(c));
		}
		for (Column c : STRINGS) {
			bh.consume(row.getString(c));
		}
		bh.consume(row.getBigDecimal(Product.price));
		bh.consume(row.getDate(Product.created_at));
		bh.consume(row.getDate(Product.updated_at));
		bh.consume(row.isNull(Product.deleted_at));

	}

	@Benchmark
	public void byTableData (Blackhole bh) {

		Data t = row.getData(Product.instance());
		for (Column c : LONGS) {
			bh.consume(t.getLong(c.name()));
		}
		for (Column c : INTS) {
			bh.consume(t.getInt(c.name()));
		}
		for (Column c : STRINGS) {
			bh.consume(t.getString(c.name()));
		}
		bh.consume(t.getBigDecimal("price"));
		bh.consume(t.getDate("created_at"));
		bh.consume(t.getDate("updated_at"));
		bh.consume(t.isNull("deleted_at"));

	}

	@Benchmark
	public void flattenThenRead (Blackhole bh) {

		Data t = row.flattenTable(Product.instance());
		for (Column c : LONGS) {
			bh.consume(t.getLong(c.name()));
		}
		for (Column c : INTS) {
			bh.consume(t.getInt(c.name()));
		}
		for (Column c : STRINGS) {
			bh.consume(t.getString(c.name()));
		}
		bh.consume(t.getBigDecimal("price"));
		bh.consume(t.getDate("created_at"));
		bh.consume(t.getDate("updated_at"));
		bh.consume(t.isNull("deleted_at"));

	}

}
