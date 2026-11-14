package bench;

import bench.BenchSchema.Product;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.util.conf.Conf;

/**
 * JFR で CPU の内訳を見るために、1 本のスレッドで同じ SELECT を回し続ける
 *
 * <pre>
 * java -XX:StartFlightRecording=filename=pk.jfr,settings=profile -cp 'build/install/bench-sql-cpu/lib/*' bench.ProfileLoop pk 20
 * </pre>
 *
 * <ul>
 *   <li>{@code pk}：主キー 1 行・全列の {@code select}（DB に行く）</li>
 *   <li>{@code list}：1 つのショップの 20 行・全列の {@code selectList}（DB に行く。行の読み取りが多い）</li>
 *   <li>{@code cached}：SQL 結果キャッシュに当たる {@code selectListCached}（DB に行かない）</li>
 * </ul>
 */
public final class ProfileLoop {

	private ProfileLoop () {
	}

	public static void main (String[] args) {

		String mode = args.length > 0 ? args[0] : "pk";
		long seconds = args.length > 1 ? Long.parseLong(args[1]) : 20;

		SqlBench bench = new SqlBench();
		bench.setUp();

		try {

			DB db = DBUtil.getMainDB();
			long end = System.nanoTime() + seconds * 1_000_000_000L;
			long count = 0;

			while (System.nanoTime() < end) {
				long id = count % 100 + 1;
				Object result = switch (mode) {
					case "pk" -> db.select(SqlBench.pk(id));
					case "list" -> db.selectList(SQL.select().from(Product.instance())
						.where(Product.shop_id.eq(1L))
						.orderBy(Product.sort_order.asc())
						.limit(20).offset((int) (id % 5) * 20));
					case "cached" -> db.selectListCached(SqlBench.pk(id));
					default -> throw new IllegalArgumentException(mode);
				};
				if (result == null) {
					throw new IllegalStateException();
				}
				count++;
			}

			System.out.printf("%s: %,d 回 / %d 秒%n", mode, count, seconds);
			db.close();

		} finally {
			bench.tearDown();
			Conf.reload();
		}

	}

}
