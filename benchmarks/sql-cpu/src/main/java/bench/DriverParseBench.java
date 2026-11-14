package bench;

import org.mariadb.jdbc.util.ClientParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * MariaDB Connector/J（4.0.0）が prepareStatement のたびに SQL の字面を解析する手間
 *
 * <p>
 * useServerPrepStmts=false のとき、ClientPreparedStatement のコンストラクタが、
 * rewriteBatchedStatements=true なら {@code ClientParser.rewritableParts}、false なら {@code parameterParts} を呼ぶ。
 * SQL は 12 列の表の主キー 1 行の全列 SELECT（2.x の別名つきと 3.0 の別名なし）。
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class DriverParseBench {

	static final String SQL_27 = "SELECT `bench_product`.`id` AS `bench_product__id`, `bench_product`.`shop_id` AS `bench_product__shop_id`, `bench_product`.`code` AS `bench_product__code`, `bench_product`.`name` AS `bench_product__name`, `bench_product`.`price` AS `bench_product__price`, `bench_product`.`stock` AS `bench_product__stock`, `bench_product`.`status` AS `bench_product__status`, `bench_product`.`description` AS `bench_product__description`, `bench_product`.`sort_order` AS `bench_product__sort_order`, `bench_product`.`created_at` AS `bench_product__created_at`, `bench_product`.`updated_at` AS `bench_product__updated_at`, `bench_product`.`deleted_at` AS `bench_product__deleted_at` FROM `bench_product` WHERE ( `bench_product`.`id` = ?)";

	static final String SQL_30 = "SELECT `bench_product`.`id`, `bench_product`.`shop_id`, `bench_product`.`code`, `bench_product`.`name`, `bench_product`.`price`, `bench_product`.`stock`, `bench_product`.`status`, `bench_product`.`description`, `bench_product`.`sort_order`, `bench_product`.`created_at`, `bench_product`.`updated_at`, `bench_product`.`deleted_at` FROM `bench_product` WHERE ( `bench_product`.`id` = ?)";

	@Param({ "2.7", "3.0" })
	String form;

	private String sql;

	@org.openjdk.jmh.annotations.Setup
	public void setUp () {

		sql = "2.7".equals(form) ? SQL_27 : SQL_30;

	}

	@Benchmark
	public ClientParser rewritableParts () {

		return ClientParser.rewritableParts(sql, false);

	}

	@Benchmark
	public ClientParser parameterParts () {

		return ClientParser.parameterParts(sql, false);

	}

}
