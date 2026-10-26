package bench;

import bench.BenchSchema.Product;
import bench.BenchSchema.Shop;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * SQL の組み立てと、それを使う SELECT 1 回の CPU
 *
 * <ul>
 *   <li>{@code pkBuild}：主キー 1 行の SELECT の SQL とパラメータを作るだけ</li>
 *   <li>{@code joinBuild}：JOIN・IN・ORDER BY・ページングつきの SELECT を作るだけ</li>
 *   <li>{@code pkSelect}：主キー 1 行の SELECT を DB に投げる（bench 用の MySQL）</li>
 *   <li>{@code cachedSelect}：SQL 結果キャッシュに当たる SELECT（DB には行かない）</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class SqlBench {

	private DB db;

	private Dialect dialect;

	private long id;

	@Setup(Level.Trial)
	public void setUp () {

		DBUtil.load(Conf.conf().config(), SqlBench.class);
		db = DBUtil.getMainDB();
		dialect = db.dialect();

		db.execute("DROP TABLE IF EXISTS bench_product");
		db.execute("DROP TABLE IF EXISTS bench_shop");
		db.execute("""
			CREATE TABLE bench_shop (
				id BIGINT PRIMARY KEY, code VARCHAR(32) NOT NULL, name VARCHAR(64) NOT NULL, deleted_at DATETIME NULL
			)""");
		db.execute("""
			CREATE TABLE bench_product (
				id BIGINT PRIMARY KEY, shop_id BIGINT NOT NULL, code VARCHAR(32) NOT NULL, name VARCHAR(64) NOT NULL,
				price DECIMAL(10, 2) NOT NULL, stock INT NOT NULL, status INT NOT NULL, description TEXT NULL,
				sort_order INT NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, deleted_at DATETIME NULL,
				KEY (shop_id)
			)""");
		db.execute("INSERT INTO bench_shop VALUES (1, 'shop1', 'ショップ1', NULL)");
		for (int i = 1; i <= 100; i++) {
			db.execute("INSERT INTO bench_product VALUES (?, 1, ?, ?, 1200.00, 10, 1, '説明', ?, NOW(), NOW(), NULL)"
				, i, "P" + i, "商品" + i, i);
		}

	}

	@TearDown(Level.Trial)
	public void tearDown () {

		db.close();

	}

	private long nextId () {

		id = id % 100 + 1;
		return id;

	}

	static SelectBuilder pk (long id) {

		return new SelectBuilder()
			.from(Product.instance())
			.where(Product.id.eq(id));

	}

	static SelectBuilder join (long shopId, List<Long> ids) {

		return new SelectBuilder()
			.from(Product.instance())
			.inner(Shop.instance())
			.on(Shop.id.eq(Product.shop_id))
			.where(Product.shop_id.eq(shopId))
			.where(Product.id.in(ids))
			.where(Product.deleted_at.is_null())
			.orderBy(Product.sort_order.asc())
			.limit(20)
			.offset(0);

	}

	@Benchmark
	public void pkBuild (Blackhole bh) {

		SelectBuilder builder = pk(nextId());
		bh.consume(builder.sql(dialect));
		bh.consume(builder.params());

	}

	@Benchmark
	public void joinBuild (Blackhole bh) {

		long first = nextId();
		SelectBuilder builder = join(1, List.of(first, first + 1, first + 2));
		bh.consume(builder.sql(dialect));
		bh.consume(builder.params());

	}

	@Benchmark
	public Optional<Data> pkSelect () {

		return db.select(pk(nextId()));

	}

	@Benchmark
	public List<Data> cachedSelect () {

		return db.selectListCached(pk(nextId()));

	}

}
