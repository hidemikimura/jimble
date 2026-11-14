package bench;

import bench.BenchSchema.Product;
import bench.WideSchema.Customer;
import bench.WideSchema.OrderItem;
import bench.WideSchema.Orders;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.IBuilder;
import io.jimble.db.sql.SQL;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SQL の組み立てだけ（DB に繋がない）。購入導線にありがちな形を並べる
 *
 * <ul>
 *   <li>{@code pkAll}：全列・主キー 1 行</li>
 *   <li>{@code namedList}：列を名指し（{@code SQL.select(列...)}）・WHERE 3 つ・ORDER BY</li>
 *   <li>{@code joinAll}：2 表の全列・ON・IN・IS NULL・LIMIT</li>
 *   <li>{@code inList}：全列・IN（値 1〜5 個で文が変わる）</li>
 *   <li>{@code update}：在庫を減らす UPDATE</li>
 *   <li>{@code insert}：8 列の INSERT</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class BuildBench {

	private final Dialect dialect = Dialects.of("mysql");

	private long id;

	private long nextId () {

		id = id % 100 + 1;
		return id;

	}

	private void consume (Blackhole bh, IBuilder builder) {

		bh.consume(builder.sql(dialect));
		bh.consume(builder.params());

	}

	@Benchmark
	public void pkAll (Blackhole bh) {

		consume(bh, SQL.select().from(Product.instance()).where(Product.id.eq(nextId())));

	}

	@Benchmark
	public void namedList (Blackhole bh) {

		consume(bh, SQL.select(Product.id, Product.code, Product.name, Product.price, Product.stock)
			.from(Product.instance())
			.where(Product.shop_id.eq(nextId()))
			.where(Product.status.eq(1))
			.where(Product.deleted_at.is_null())
			.orderBy(Product.sort_order.asc()));

	}

	@Benchmark
	public void joinAll (Blackhole bh) {

		long first = nextId();
		consume(bh, SqlBench.join(1, List.of(first, first + 1, first + 2)));

	}

	@Benchmark
	public void inList (Blackhole bh) {

		long first = nextId();
		int n = (int) (first % 5) + 1;
		Long[] ids = new Long[n];
		for (int i = 0; i < n; i++) {
			ids[i] = first + i;
		}
		consume(bh, SQL.select().from(Product.instance()).where(Product.id.in(List.of(ids))));

	}

	@Benchmark
	public void update (Blackhole bh) {

		consume(bh, SQL.update(Product.instance())
			.set(Product.stock, Product.stock.minus(1L))
			.set(Product.updated_at, new Date())
			.where(Product.id.eq(nextId()))
			.where(Product.stock.ge(1)));

	}

	@Benchmark
	public void insert (Blackhole bh) {

		long n = nextId();
		consume(bh, SQL.insert(Product.instance())
			.value(Product.id, n)
			.value(Product.shop_id, 1L)
			.value(Product.code, "P" + n)
			.value(Product.name, "商品")
			.value(Product.price, BigDecimal.TEN)
			.value(Product.stock, 10)
			.value(Product.status, 1)
			.value(Product.sort_order, 1));

	}

	@Benchmark
	public void widePk (Blackhole bh) {

		consume(bh, SQL.select().from(Orders.instance()).where(Orders.id.eq(nextId())));

	}

	@Benchmark
	public void wideJoin3 (Blackhole bh) {

		consume(bh, SQL.select().from(Orders.instance())
			.inner(OrderItem.instance()).on(OrderItem.order_id.eq(Orders.id))
			.inner(Customer.instance()).on(Customer.id.eq(Orders.customer_id))
			.where(Orders.id.eq(nextId()))
			.where(Orders.status.eq("ordered")));

	}

	// 案 3 の見積もり用：ビルダーを作って値を集めるだけ（SQL の字面は作らない）

	@Benchmark
	public void pkAllNoSql (Blackhole bh) {

		bh.consume(SQL.select().from(Product.instance()).where(Product.id.eq(nextId())).params());

	}

	@Benchmark
	public void joinAllNoSql (Blackhole bh) {

		long first = nextId();
		bh.consume(SqlBench.join(1, List.of(first, first + 1, first + 2)).params());

	}

	@Benchmark
	public void wideJoin3NoSql (Blackhole bh) {

		bh.consume(SQL.select().from(Orders.instance())
			.inner(OrderItem.instance()).on(OrderItem.order_id.eq(Orders.id))
			.inner(Customer.instance()).on(Customer.id.eq(Orders.customer_id))
			.where(Orders.id.eq(nextId()))
			.where(Orders.status.eq("ordered")).params());

	}

}
