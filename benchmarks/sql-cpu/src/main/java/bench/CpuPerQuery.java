package bench;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * 1 回あたりにスレッドが使った CPU 時間（DB を往復するものも、待ちを除いて測る）
 *
 * <p>
 * JMH の時間は DB の往復の待ちを含むので、主キー 1 行の SELECT では組み立ての差が見えない。
 * こちらは {@link ThreadMXBean#getCurrentThreadCpuTime()} で、待っている間を除いた CPU を測る
 * （ドライバ・プールを含む。DB サーバー側の CPU は含まない）。
 * </p>
 *
 * <pre>
 * java -cp 'build/install/bench-sql-cpu/lib/*' bench.CpuPerQuery [回数]
 * </pre>
 */
public final class CpuPerQuery {

	private CpuPerQuery () {
	}

	public static void main (String[] args) {

		int count = args.length > 0 ? Integer.parseInt(args[0]) : 20_000;

		SqlBench bench = new SqlBench();
		bench.setUp();

		try {

			ThreadMXBean threads = ManagementFactory.getThreadMXBean();
			DB db = DBUtil.getMainDB();

			for (int round = 0; round < 3; round++) {

				long pk = measure(threads, count, i -> db.select(SqlBench.pk(i % 100 + 1)));
				long cached = measure(threads, count * 10, i -> db.selectListCached(SqlBench.pk(i % 100 + 1)));

				System.out.printf("round %d\tpkSelect %,d ns CPU/回\tcachedSelect %,d ns CPU/回%n", round + 1, pk, cached);

			}

			db.close();

		} finally {
			bench.tearDown();
			Conf.reload();
		}

	}

	private interface Body {
		Object run (int i);
	}

	private static long measure (ThreadMXBean threads, int count, Body body) {

		// 空回し
		for (int i = 0; i < count; i++) {
			body.run(i);
		}

		long start = threads.getCurrentThreadCpuTime();
		for (int i = 0; i < count; i++) {
			body.run(i);
		}
		return (threads.getCurrentThreadCpuTime() - start) / count;

	}

}
