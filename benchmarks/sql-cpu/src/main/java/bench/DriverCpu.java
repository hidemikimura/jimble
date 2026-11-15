package bench;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.util.conf.Conf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

import bench.BenchSchema.Product;

/**
 * JDBC ドライバごとの、DB の道（組み立て・ドライバ・結果の読み取り）の 1 回あたりのスレッドの CPU 時間
 *
 * <p>
 * ドライバは設定で替える（既定は MariaDB Connector/J。{@code -Djimble.env=cj} で mysql-connector-j、
 * {@code -Djimble.env=cjcache} で mysql-connector-j の {@code cachePrepStmts=true}）。
 * JDBC は呼んだスレッドで動くので、スレッドの CPU 時間にドライバの手間が入る。
 * </p>
 *
 * <ul>
 *   <li>{@code pk}：主キー 1 行・全列の {@code select}</li>
 *   <li>{@code list20}：20 行・全列の {@code selectList}</li>
 *   <li>{@code update}：主キー 1 行の {@code update}</li>
 *   <li>{@code batch20}：20 行の {@code executeBatch}（UPDATE）</li>
 * </ul>
 */
public final class DriverCpu {

	private DriverCpu () {
	}

	public static void main (String[] args) {

		int count = args.length > 0 ? Integer.parseInt(args[0]) : 20_000;

		SqlBench bench = new SqlBench();
		bench.setUp();

		try {

			ThreadMXBean threads = ManagementFactory.getThreadMXBean();
			DB db = DBUtil.getMainDB();

			String driver = Conf.conf().config().getString("db.bench.driver");
			System.out.println("driver\t" + driver + " " + Class.forName(driver).getPackage().getImplementationVersion()
				+ "\turl\t" + Conf.conf().config().getString("db.bench.url").replaceAll("^.*\\?", "?"));

			for (int round = 0; round < 3; round++) {

				long pk = CpuPerQuery.measure(threads, count, i -> db.select(SqlBench.pk(i % 100 + 1)));
				long list = CpuPerQuery.measure(threads, count / 2, i -> db.selectList(SQL.select().from(Product.instance())
					.where(Product.shop_id.eq(1L))
					.orderBy(Product.sort_order.asc())
					.limit(20).offset((i % 5) * 20)));
				long update = CpuPerQuery.measure(threads, count / 2, i -> db.update(SQL.update(Product.instance())
					.set(Product.stock, i % 50)
					.where(Product.id.eq((long) (i % 100 + 1)))));
				long batch = CpuPerQuery.measure(threads, count / 20, i -> db.executeBatch(batch(i)));

				System.out.printf("round %d\tpk %,d\tlist20 %,d\tupdate %,d\tbatch20 %,d ns CPU/回%n", round + 1, pk, list, update, batch);

			}

			db.close();

		} catch (ClassNotFoundException ex) {
			throw new IllegalStateException(ex);
		} finally {
			bench.tearDown();
			Conf.reload();
		}

	}

	private static List<io.jimble.db.sql.IBuilder> batch (int round) {

		List<io.jimble.db.sql.IBuilder> list = new ArrayList<>();
		for (long id = 1; id <= 20; id++) {
			list.add(SQL.update(Product.instance()).set(Product.stock, round % 50).where(Product.id.eq(id)));
		}
		return list;

	}

}
