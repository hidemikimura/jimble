package bench;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * 主キー 1 行の SELECT を、ビルダーと template（3.0.0）で投げたときの、1 回あたりのスレッドの CPU 時間
 *
 * <p>
 * DB に投げるもの（{@code pkSelect}）と、SQL 結果キャッシュに当たるもの（{@code cachedSelect}）。
 * 待ちを除いた CPU（ドライバ・プールを含む。DB サーバー側は含まない）。{@code -Pjimble.local=true} のときだけビルドに入る。
 * </p>
 */
public final class TemplateCpuPerQuery {

	private TemplateCpuPerQuery () {
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
				long pkTemplate = CpuPerQuery.measure(threads, count, i -> db.select(TemplateBench.PK.bind("id", (long) (i % 100 + 1))));
				long cached = CpuPerQuery.measure(threads, count * 10, i -> db.selectListCached(SqlBench.pk(i % 100 + 1)));
				long cachedTemplate = CpuPerQuery.measure(threads, count * 10, i -> db.selectListCached(TemplateBench.PK.bind("id", (long) (i % 100 + 1))));

				System.out.printf("round %d\tpkSelect %,d → template %,d ns CPU/回\tcachedSelect %,d → template %,d ns CPU/回%n"
					, round + 1, pk, pkTemplate, cached, cachedTemplate);

			}

			db.close();

		} finally {
			bench.tearDown();
			Conf.reload();
		}

	}

}
