package io.jimble.db;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.metrics.Metrics;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接続プールの使用率をメトリクスに出しているか（要件 NF-O-04）
 *
 * <p>
 * <b>接続プールが詰まっているかどうかは、外から見えないと分からない。</b>
 * 「たまに遅い」の原因がプール待ちなのかクエリなのかは、待っている数を見るのがいちばん早い。
 * <b>開発用 DB が必要</b>（要件 D-16）。
 * </p>
 *
 * <h2>ここで固定していないこと</h2>
 * <ul>
 *   <li><b>読み取り用（レプリカ）の分</b>（結合テストの設定にレプリカが無く、
 *       {@code db.pool.<名前>.read.*} を通す入力を作れない）</li>
 *   <li><b>Agroal の側</b>（結合テストの設定が hikari 固定のため。
 *       {@code connection_pool_type} を切り替えると 1データソースだけ別の実装になり、
 *       ほかのテストの前提が変わる）</li>
 * </ul>
 */
@Tag("db")
class DbMetricsIntegrationTest {

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), DbMetricsIntegrationTest.class), "DB に接続できませんでした");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	@Test
	@DisplayName("データソースごとに4つ登録されている")
	void registered () {

		Data gauge = Metrics.snapshot().getData("gauge");

		for (String suffix : new String[]{"active", "idle", "total", "waiting"}) {
			assertTrue(gauge.containsKey("db.pool.jimble_test." + suffix), gauge.keySet().toString());
		}

		// サブ DB（要件 F-D-14）も別の名前で出す
		assertTrue(gauge.containsKey("db.pool.jimble_test.sub.total"), gauge.keySet().toString());

		// 別のデータソースも
		assertTrue(gauge.containsKey("db.pool.jimble_test_sub.total"), gauge.keySet().toString());

	}

	@Test
	@DisplayName("借りているあいだは使用中の数が増える")
	void activeWhileBorrowed () throws Exception {

		/*
		 * <b>0 が返るだけの実装になっていないか</b>を見る。
		 * Agroal は metricsEnabled を立てないと常に 0 を返すので、
		 * 「登録されている」だけでは足りない
		 */
		long before = value("db.pool.jimble_test.active");

		try (DB db = DBUtil.getMainDB()) {

			/*
			 * <b>トランザクションの外では、1文ごとに返してしまう。</b>
			 * 借りたままにするために取引を開く
			 */
			db.beginTransaction();
			db.select("SELECT 1 AS ok");

			assertEquals(before + 1, value("db.pool.jimble_test.active"), "借りたのに増えていない");

		}

		assertEquals(before, value("db.pool.jimble_test.active"), "返したのに減っていない");

	}

	@Test
	@DisplayName("プールの上限を超えて数えない")
	void withinPoolSize () {

		// application.*test.conf の maximumPoolSize = 4
		assertTrue(value("db.pool.jimble_test.total") <= 4
			, "total=" + value("db.pool.jimble_test.total"));

		assertEquals(0, value("db.pool.jimble_test.waiting"), "誰も待っていないはず");

	}

	@Test
	@DisplayName("止めたら外す")
	void removedOnStop () {

		DBUtil.stop();

		List<String> left = Metrics.gaugeNames().stream()
			.filter(name -> name.startsWith("db.pool."))
			.sorted()
			.toList();

		assertTrue(left.isEmpty(), "止めたのに残っている: " + left);

		// あとのテストのために戻す
		loadDataSource();

	}

	/**
	 * ゲージの値を引く
	 *
	 * @param name 名前
	 * @return 値
	 */
	private static long value (String name) {

		return Metrics.snapshot().getData("gauge").getLong(name);

	}

}
