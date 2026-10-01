package io.jimble.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariDataSource;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接続プールへ設定を渡す（Agroal / HikariCP）
 *
 * <h2>なぜテストにするのか</h2>
 * <ul>
 *   <li>Agroal の {@code idleValidationTimeout} に <b>{@code keepalive_time} ではなく {@code idle_timeout}</b>
 *       （既定 10 分）を入れていた</li>
 *   <li>Agroal は {@code connection_test_query} を<b>接続直後に流す SQL</b>として渡していた——
 *       生存確認には使われず、{@code connection_init_sql} も押しのけていた</li>
 *   <li>リーク検知と、取り出すときの生存確認を設定から渡す手段が無かった</li>
 * </ul>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class PoolConfigIntegrationTest {

	/* 元の設定 */
	private Config originalConf;

	@BeforeEach
	void keep () {

		Conf.reload();
		originalConf = Conf.conf().config();

	}

	@AfterEach
	void restore () {

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	/**
	 * テスト用 DB の設定を上書きして読み込む
	 *
	 * @param hocon	db.jimble_test に足す設定
	 */
	private void load (String hocon) {

		Conf.replace(ConfigFactory.parseString("db.jimble_test { " + hocon + " }").withFallback(originalConf));
		DBUtil.load(Conf.conf().config(), PoolConfigIntegrationTest.class);

	}

	@Test
	@DisplayName("Agroal：keepalive_time は裏の定期確認と、寝ていた接続を渡す前の確認に入る（idle_timeout ではない）")
	void agroalKeepalive () {

		load("""
			connection_pool_type = "agroal"
			keepalive_time = 7s
			idle_timeout = 10m
			""");

		AgroalConnectionPoolConfiguration pool = agroal().getConfiguration().connectionPoolConfiguration();

		assertEquals(Duration.ofSeconds(7), pool.validationTimeout());
		assertEquals(Duration.ofSeconds(7), pool.idleValidationTimeout(), "idle_timeout の値が入っています");
		assertEquals(Duration.ofMinutes(10), pool.reapTimeout());

		assertTrue(DBUtil.getMainDB().select("SELECT 1 AS ok").isPresent());

	}

	@Test
	@DisplayName("Agroal：leak_timeout と validate_on_borrow を渡せる")
	void agroalLeakAndBorrow () {

		load("""
			connection_pool_type = "agroal"
			leak_timeout = 45s
			validate_on_borrow = true
			""");

		AgroalConnectionPoolConfiguration pool = agroal().getConfiguration().connectionPoolConfiguration();

		assertEquals(Duration.ofSeconds(45), pool.leakTimeout());
		assertTrue(pool.validateOnBorrow());

	}

	@Test
	@DisplayName("Agroal：書かなければリーク検知も取り出すときの確認もしない（これまでどおり）")
	void agroalDefaults () {

		load("connection_pool_type = \"agroal\"");

		AgroalConnectionPoolConfiguration pool = agroal().getConfiguration().connectionPoolConfiguration();

		assertEquals(Duration.ZERO, pool.leakTimeout());
		assertFalse(pool.validateOnBorrow());

	}

	@Test
	@DisplayName("Agroal：connection_test_query は生存確認に、connection_init_sql は接続直後に（押しのけない）")
	void agroalTestQueryAndInitSql () {

		load("""
			connection_pool_type = "agroal"
			connection_test_query = "SELECT 1"
			connection_init_sql = "SELECT 2"
			validate_on_borrow = true
			""");

		AgroalDataSource agroal = agroal();

		assertEquals("SELECT 2", agroal.getConfiguration().connectionPoolConfiguration()
			.connectionFactoryConfiguration().initialSql(), "connection_test_query が connection_init_sql を押しのけています");

		// 生存確認の SQL で確かめてから渡す（validate_on_borrow）。通らなければここで取れない
		assertTrue(DBUtil.getMainDB().select("SELECT 1 AS ok").isPresent());

	}

	@Test
	@DisplayName("Agroal：作れなければ起動を止める（黙って HikariCP に切り替えない）")
	void agroalFailureIsNotHidden () {

		/*
		 * Agroal は minimum_idle > maximum_pool_size を断る。HikariCP は黙って丸めるので、
		 * かつての「失敗したら HikariCP で作る」では、agroal と書いたのに HikariCP で動いていた。
		 */
		org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> load("""
			connection_pool_type = "agroal"
			maximum_pool_size = 2
			minimum_idle = 5
			"""));

	}

	@Test
	@DisplayName("HikariCP：leak_timeout はリーク検知に入る")
	void hikariLeak () {

		load("leak_timeout = 30s");

		assertEquals(30_000, hikari().getLeakDetectionThreshold());

	}

	/**
	 * プールの大きさを書かずに読み込む（テスト用の設定は書いているので、外してから）
	 *
	 * @param hocon	db.jimble_test に足す設定
	 */
	private void loadWithoutPoolSize (String hocon) {

		Config base = originalConf
			.withoutPath("db.jimble_test.maximum_pool_size")
			.withoutPath("db.jimble_test.minimum_idle");

		Conf.replace(ConfigFactory.parseString("db.jimble_test { " + hocon + " }").withFallback(base));
		DBUtil.load(Conf.conf().config(), PoolConfigIntegrationTest.class);

	}

	@Test
	@DisplayName("Agroal：maximum_pool_size を書かなくても起動する（上限 10・最小 1。D-201）")
	void agroalWithoutPoolSize () {

		// かつては「max size attribute is mandatory」で起動時に落ちていた
		loadWithoutPoolSize("connection_pool_type = \"agroal\"");

		AgroalConnectionPoolConfiguration pool = agroal().getConfiguration().connectionPoolConfiguration();

		assertEquals(10, pool.maxSize());
		assertEquals(1, pool.minSize());
		assertTrue(DBUtil.getMainDB().select("SELECT 1 AS ok").isPresent());

	}

	@Test
	@DisplayName("HikariCP：書かなければ上限 10・最小 1（HikariCP の既定の最小 10 ではない。D-201）")
	void hikariWithoutPoolSize () {

		loadWithoutPoolSize("connection_pool_type = \"hikari\"");

		assertEquals(10, hikari().getMaximumPoolSize());
		assertEquals(1, hikari().getMinimumIdle());

	}

	@Test
	@DisplayName("Agroal：製品に合った、接続を捨てる例外の見分け方を渡す（D-202）")
	void agroalExceptionSorter () {

		load("connection_pool_type = \"agroal\"");

		Object sorter = agroal().getConfiguration().connectionPoolConfiguration().exceptionSorter();
		Class<?> expected = io.jimble.db.dialect.PostgreSqlDialect.NAME.equals(
				io.jimble.db.dialect.Dialects.productNameOrNull(DBUtil.getMainDataSource().conf().product()))
			? io.agroal.api.exceptionsorter.PostgreSQLExceptionSorter.class
			: io.agroal.api.exceptionsorter.MySQLExceptionSorter.class;

		assertEquals(expected, sorter.getClass());

	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"hikari", "agroal"})
	@DisplayName("DB 側で接続が切られても、すぐに新しい接続で立ち直る（D-202）")
	void recoversAfterKilledConnection (String poolType) throws Exception {

		/*
		 * Agroal は例外の見分け方を渡していなかったので、<b>切れた接続をプールに戻しては渡し続け</b>、
		 * 裏の定期確認（keepalive_time）が回るまで失敗し続けた。
		 * 1本だけのプールにして、その1本を DB 側から切る。
		 */
		load("""
			connection_pool_type = "%s"
			maximum_pool_size = 1
			minimum_idle = 1
			""".formatted(poolType));

		DBSource source = DBUtil.getMainDataSource();
		boolean postgres = io.jimble.db.dialect.PostgreSqlDialect.NAME.equals(
			io.jimble.db.dialect.Dialects.productNameOrNull(source.conf().product()));

		long id;
		try (java.sql.Connection c = source.dataSource().getConnection();
			java.sql.Statement st = c.createStatement();
			java.sql.ResultSet rs = st.executeQuery(postgres ? "SELECT pg_backend_pid()" : "SELECT CONNECTION_ID()")) {
			rs.next();
			id = rs.getLong(1);
		}

		// プールを通さずに繋いで、プールの1本を切る
		try (java.sql.Connection killer = java.sql.DriverManager.getConnection(
				source.conf().url(), source.conf().user(), source.conf().password());
			java.sql.Statement st = killer.createStatement()) {
			st.execute(postgres ? "SELECT pg_terminate_backend(" + id + ")" : "KILL " + id);
		}

		// 切れたことに気づくまでの数回は失敗してよい。そのあとは新しい接続で通り続ける
		int failures = 0;
		boolean recovered = false;
		for (int i = 0; i < 5 && !recovered; i++) {
			try (java.sql.Connection c = source.dataSource().getConnection();
				java.sql.Statement st = c.createStatement();
				java.sql.ResultSet rs = st.executeQuery("SELECT 1")) {
				recovered = rs.next();
			} catch (java.sql.SQLException ex) {
				failures++;
			}
		}

		assertTrue(recovered, poolType + "：切れた接続を渡し続けています（" + failures + " 回失敗）");
		assertTrue(failures <= 2, poolType + "：立ち直るまでに " + failures + " 回失敗しました");

		for (int i = 0; i < 3; i++) {
			assertTrue(DBUtil.getMainDB().select("SELECT 1 AS ok").isPresent());
		}

	}

	/**
	 * いまの Agroal
	 */
	private static AgroalDataSource agroal () {

		if (DBUtil.getMainDataSource().dataSource() instanceof AgroalDataSource agroal) {
			return agroal;
		}

		throw new AssertionError("Agroal になっていません（黙って HikariCP に切り替わっていないか）: "
			+ DBUtil.getMainDataSource().dataSource().getClass());

	}

	/**
	 * いまの HikariCP
	 */
	private static HikariDataSource hikari () {

		if (DBUtil.getMainDataSource().dataSource() instanceof HikariDataSource hikari) {
			return hikari;
		}

		throw new AssertionError("HikariCP になっていません");

	}

}
