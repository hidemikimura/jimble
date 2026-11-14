package bench;

import bench.BenchSchema.Product;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.util.conf.Conf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * DB の道（組み立て・バインド・ドライバ・結果の読み取り）の 1 回あたりのスレッドの CPU 時間
 *
 * <ul>
 *   <li>{@code pk}：主キー 1 行・全列の {@code select}</li>
 *   <li>{@code list20}：20 行・全列の {@code selectList}（結果の読み取りが多い）</li>
 *   <li>{@code cached}：SQL 結果キャッシュに当たる {@code selectListCached}</li>
 * </ul>
 */
public final class DbPathCpu {

	private DbPathCpu () {
	}

	public static void main (String[] args) {

		int count = args.length > 0 ? Integer.parseInt(args[0]) : 20_000;

		SqlBench bench = new SqlBench();
		bench.setUp();

		try {

			ThreadMXBean threads = ManagementFactory.getThreadMXBean();
			DB db = DBUtil.getMainDB();

			for (int round = 0; round < 3; round++) {

				long pk = CpuPerQuery.measure(threads, count, i -> db.select(SqlBench.pk(i % 100 + 1)));
				long list = CpuPerQuery.measure(threads, count / 2, i -> db.selectList(SQL.select().from(Product.instance())
					.where(Product.shop_id.eq(1L))
					.orderBy(Product.sort_order.asc())
					.limit(20).offset((i % 5) * 20)));
				long cached = CpuPerQuery.measure(threads, count * 10, i -> db.selectListCached(SqlBench.pk(i % 100 + 1)));

				System.out.printf("round %d\tpk %,d\tlist20 %,d\tcached %,d ns CPU/回%n", round + 1, pk, list, cached);

			}

			db.close();

		} finally {
			bench.tearDown();
			Conf.reload();
		}

	}

}
