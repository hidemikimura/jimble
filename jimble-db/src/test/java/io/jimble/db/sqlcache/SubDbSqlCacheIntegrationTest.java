package io.jimble.db.sqlcache;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sqlcache.SqlCacheSchema.Shop;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * サブ DB の SQL 結果キャッシュが、親やほかのサブと混ざらない
 *
 * <p>
 * サブ（{@code db.<名前>.subs.<サブ>}）の名前は親と同じで、SQL 結果キャッシュのキーとタグには
 * その名前が入っていた。<b>サブを別の DB に向けると、同じ SQL・同じ値のキャッシュが親やほかのサブと混ざり、
 * 別の DB の結果が返っていた</b>（docs はサブを「シャード」として別の DB に向ける書き方を載せている）。
 * </p>
 *
 * <p>
 * 主（{@code jimble_test}）に、別の DB（{@code jimble_test_sub}）を指すサブ {@code sub} と、
 * 主と同じ DB を指すサブ {@code same} を付ける。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class SubDbSqlCacheIntegrationTest {

	@BeforeAll
	static void load () {

		Conf.reload();
		Config base = Conf.conf().config();
		String mainUrl = base.getString("db.jimble_test.url");

		Conf.replace(ConfigFactory.parseString("migration.on_startup = false")
			.withFallback(base
				.withValue(SqlCacheConf.KEY_ENABLED, ConfigValueFactory.fromAnyRef(true))
				.withValue("db.jimble_test.subs.same.url", ConfigValueFactory.fromAnyRef(mainUrl))));

		DBUtil.load(Conf.conf().config(), SubDbSqlCacheIntegrationTest.class);

		for (DB db : new DB[] { DBUtil.getMainDB(), DBUtil.getMainDB().newSubDB("sub") }) {
			try (db) {
				db.execute("DROP TABLE IF EXISTS shop");
				TestDdl.execute(db, """
					CREATE TABLE shop (
						id   bigint unsigned auto_increment primary key,
						name varchar(100) null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
					""");
			}
		}

	}

	@AfterAll
	static void stop () {

		for (DB db : new DB[] { DBUtil.getMainDB(), DBUtil.getMainDB().newSubDB("sub") }) {
			try (db) {
				db.execute("DROP TABLE IF EXISTS shop");
			}
		}

		SqlCache.clear();
		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void reset () {

		SqlCache.clear();

		try (DB main = DBUtil.getMainDB().withoutSqlCache(); DB sub = DBUtil.getMainDB().newSubDB("sub").withoutSqlCache()) {
			main.execute("DELETE FROM shop");
			sub.execute("DELETE FROM shop");
			main.execute("INSERT INTO shop (id, name) VALUES (1, 'main')");
			sub.execute("INSERT INTO shop (id, name) VALUES (1, 'sub')");
		}

	}

	@Test
	@DisplayName("親で読んだあとにサブで読んでも、サブの DB の結果が返る")
	void subDoesNotSeeParentCache () {

		try (DB main = DBUtil.getMainDB(); DB sub = DBUtil.getMainDB().newSubDB("sub")) {
			assertEquals("main", name(main));
			assertEquals("sub", name(sub), "親のキャッシュがサブに返った");
		}

	}

	@Test
	@DisplayName("サブ同士でも混ざらない（別の DB を指すサブのあとに、主と同じ DB を指すサブで読む）")
	void subsDoNotShareCache () {

		try (DB sub = DBUtil.getMainDB().newSubDB("sub"); DB same = DBUtil.getMainDB().newSubDB("same")) {
			assertEquals("sub", name(sub));
			assertEquals("main", name(same), "ほかのサブのキャッシュが返った");
		}

	}

	@Test
	@DisplayName("サブで書けば、そのサブのキャッシュが消える")
	void writeOnSubInvalidatesSub () {

		try (DB sub = DBUtil.getMainDB().newSubDB("sub")) {

			assertEquals("sub", name(sub));

			sub.update(SQL.update(Shop.instance()).set(Shop.name, "sub2").where(Shop.id.eq(1)));

			assertEquals("sub2", name(sub), "サブで書いたのに、サブの古いキャッシュが返った");

		}

	}

	@Test
	@DisplayName("親と同じ DB を指すサブで書けば、親のキャッシュも消える（同じ表なので、これまでどおり）")
	void sameDatabaseSubSharesWithParent () {

		try (DB main = DBUtil.getMainDB(); DB same = DBUtil.getMainDB().newSubDB("same")) {

			assertEquals("main", name(main));

			same.update(SQL.update(Shop.instance()).set(Shop.name, "main2").where(Shop.id.eq(1)));

			assertEquals("main2", name(main), "同じ DB を指すサブで書いたのに、親の古いキャッシュが返った");
			assertEquals("main2", name(same));

		}

	}

	@Test
	@DisplayName("親で読んだキャッシュのキーは変わらない（データソース名のまま）")
	void parentKeyIsUnchanged () {

		try (DB main = DBUtil.getMainDB()) {

			assertEquals("main", name(main));

			SelectBuilder builder = select();
			String key = SqlCache.key("jimble_test", builder.sql(main.dialect()), builder.params());

			assertNotNull(SqlCache.get(key), "親のキャッシュのキーが変わった");

		}

	}

	// region ヘルパー

	private static SelectBuilder select () {

		return SQL.select().from(Shop.instance()).where(Shop.id.eq(1));

	}

	private static String name (DB db) {

		return db.selectCached(select()).orElseThrow().getData("shop").getString("name");

	}

	// endregion

}
