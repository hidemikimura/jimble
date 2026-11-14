package bench;

import bench.BenchSchema.Product;
import bench.BenchSchema.Shop;
import bench.WideSchema.Customer;
import bench.WideSchema.OrderItem;
import bench.WideSchema.Orders;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.IBuilder;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SelectTemplate;
import io.jimble.db.sql.query.dsl.Dsl;

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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 形を決めて値だけ差し替える SELECT（SQL.template。3.0.0 から）の組み立て。DB に繋がない
 *
 * <p>
 * {@link BuildBench} の同じ名前の形（{@code pkAll}・{@code joinAll}・{@code wideJoin3}）を template にしたもの。
 * 1 回あたり {@code bind} して SQL とパラメータを取り出すまで。
 * 3.0.0 より前の版には template が無いので、{@code -Pjimble.local=true} のときだけビルドに入る。
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class TemplateBench {

	static final SelectTemplate PK = SQL.template(SQL.select().from(Product.instance())
		.where(Product.id.eq(Dsl.param("id"))));

	static final SelectTemplate JOIN = SQL.template(SQL.select()
		.from(Product.instance())
		.inner(Shop.instance())
		.on(Shop.id.eq(Product.shop_id))
		.where(Product.shop_id.eq(Dsl.param("shop")))
		.where(Product.id.in(Dsl.params("ids")))
		.where(Product.deleted_at.is_null())
		.orderBy(Product.sort_order.asc())
		.limit(20)
		.offset(0));

	static final SelectTemplate WIDE_JOIN3 = SQL.template(SQL.select().from(Orders.instance())
		.inner(OrderItem.instance()).on(OrderItem.order_id.eq(Orders.id))
		.inner(Customer.instance()).on(Customer.id.eq(Orders.customer_id))
		.where(Orders.id.eq(Dsl.param("id")))
		.where(Orders.status.eq("ordered")));

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
	public void pkAllTemplate (Blackhole bh) {

		consume(bh, PK.bind("id", nextId()));

	}

	@Benchmark
	public void joinAllTemplate (Blackhole bh) {

		long first = nextId();
		consume(bh, JOIN.bind("shop", 1L, "ids", List.of(first, first + 1, first + 2)));

	}

	@Benchmark
	public void wideJoin3Template (Blackhole bh) {

		consume(bh, WIDE_JOIN3.bind("id", nextId()));

	}

}
