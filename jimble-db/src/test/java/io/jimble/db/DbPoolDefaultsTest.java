package io.jimble.db;

import io.agroal.api.exceptionsorter.MySQLExceptionSorter;
import io.agroal.api.exceptionsorter.PostgreSQLExceptionSorter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * プールの大きさの既定と、Agroal に渡す例外の見分け方（D-201 / D-202）
 *
 * <ul>
 *   <li>{@code maximum_pool_size} / {@code minimum_idle} を書かないと 0 のままで、プールに渡していなかった。
 *       HikariCP は最小も 10 になり、<b>Agroal は起動時に落ちていた</b></li>
 *   <li>Agroal に例外の見分け方を渡していなかったので、DB が再起動すると<b>切れた接続を渡し続けた</b></li>
 * </ul>
 */
class DbPoolDefaultsTest {

	@Test
	@DisplayName("書かなければ上限 10・最小 1（ドキュメントの既定）")
	void defaults () {

		DBConf conf = new DBConf();

		assertEquals(10, conf.maximumPoolSize());
		assertEquals(1, conf.minimumIdle());

		DBUtil.checkPoolSize(conf);

		// 読み取り用・サブにも写る
		DBConf copy = DBConf.from(conf);
		assertEquals(10, copy.maximumPoolSize());
		assertEquals(1, copy.minimumIdle());

	}

	@Test
	@DisplayName("上限が 1 より小さければ、直し方つきで落とす（これまでは黙って無視していた）")
	void rejectsZeroMax () {

		DBConf conf = new DBConf();
		conf.maximumPoolSize(0);

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> DBUtil.checkPoolSize(conf));
		assertTrue(ex.getMessage().contains("maximum_pool_size"), ex.getMessage());

	}

	@Test
	@DisplayName("最小が 0 より小さければ落とす。0 は受ける")
	void minimumIdle () {

		DBConf negative = new DBConf();
		negative.minimumIdle(-1);
		assertThrows(IllegalStateException.class, () -> DBUtil.checkPoolSize(negative));

		DBConf zero = new DBConf();
		zero.minimumIdle(0);
		DBUtil.checkPoolSize(zero);
		assertEquals(0, zero.minimumIdle());

	}

	@ParameterizedTest
	@ValueSource(strings = {"postgresql", "postgres", "pgsql", "PostgreSQL"})
	@DisplayName("PostgreSQL なら PostgreSQL の見分け方")
	void postgresSorter (String product) {

		DBConf conf = new DBConf();
		conf.product(product);

		assertInstanceOf(PostgreSQLExceptionSorter.class, DBUtil.exceptionSorter(conf));

	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"mysql", "mariadb"})
	@DisplayName("MySQL / MariaDB と、書いていないときは MySQL の見分け方（方言の既定と同じ）")
	void mysqlSorter (String product) {

		DBConf conf = new DBConf();
		conf.product(product);

		assertInstanceOf(MySQLExceptionSorter.class, DBUtil.exceptionSorter(conf));

	}

	@Test
	@DisplayName("接続の例外（SQLState 08）は捨て、ふつうの SQL の誤りは捨てない")
	void fatalOnlyForConnectionErrors () {

		for (String product : new String[] {"mysql", "postgresql"}) {

			DBConf conf = new DBConf();
			conf.product(product);
			var sorter = DBUtil.exceptionSorter(conf);

			assertTrue(sorter.isFatal(new SQLException("Socket error", "08000")), product);
			assertTrue(sorter.isFatal(new SQLException("I/O error", "08006")), product);
			assertFalse(sorter.isFatal(new SQLException("syntax error", "42000")), product);
			assertFalse(sorter.isFatal(new SQLException("duplicate key", "23000")), product);

		}

	}

}
