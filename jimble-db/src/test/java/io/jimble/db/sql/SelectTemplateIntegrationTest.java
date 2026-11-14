package io.jimble.db.sql;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sqlcache.MemorySqlCacheStore;
import io.jimble.db.sqlcache.SqlCache;
import io.jimble.db.sqlcache.SqlCacheConf;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形を決めて値だけ差し替える SELECT を DB に投げる（2.8.0）
 *
 * <p>
 * 結果の形（テーブル名でネストした Data）・SQL 結果キャッシュ（当たる・書き込みで外れる）が、
 * 同じ形を今のビルダーで投げたときと同じかを見る。
 * </p>
 */
@Tag("db")
class SelectTemplateIntegrationTest {

	/** スキーマ */
	static final class TplSchema extends AbstractSchema {

		static final TplSchema INSTANCE = new TplSchema();

		@Override
		public String name () {
			return "jimble_test";
		}

	}

	/** tpl_shop */
	static final class TplShop extends Table {

		static final Column id = new Column(instance(), "id", long.class, false, null, true);
		static final Column name = new Column(instance(), "name", String.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, name);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		private TplShop () { super(TplSchema.INSTANCE, "tpl_shop"); }

		static TplShop instance () { return new TplShop(); }

	}

	/** tpl_customer */
	static final class TplCustomer extends Table {

		static final Column id = new Column(instance(), "id", long.class, false, null, true);
		static final Column shop_id = new Column(instance(), "shop_id", long.class, false, null, false);
		static final Column name = new Column(instance(), "name", String.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, shop_id, name);

		@Override
		protected List<Column> declareColumns () { return COLUMNS; }

		private TplCustomer () { super(TplSchema.INSTANCE, "tpl_customer"); }

		static TplCustomer instance () { return new TplCustomer(); }

	}

	private static final SelectTemplate CUSTOMER_BY_ID = SQL.template(SQL.select()
		.from(TplCustomer.instance())
		.inner(TplShop.instance()).on(TplShop.id.eq(TplCustomer.shop_id))
		.where(TplCustomer.id.eq(Dsl.param("id"))));

	private static final SelectTemplate CUSTOMERS_IN_SHOP = SQL.template(SQL.select()
		.from(TplCustomer.instance())
		.where(TplCustomer.shop_id.eq(Dsl.param("shop")))
		.where(TplCustomer.id.in(Dsl.params("ids")))
		.orderBy(TplCustomer.id.asc()));

	@BeforeAll
	static void createTables () {

		enableSqlCache();
		DBUtil.load(Conf.conf().config(), SelectTemplateIntegrationTest.class);

		try (DB db = DBUtil.getMainDB()) {

			db.execute("DROP TABLE IF EXISTS tpl_customer");
			db.execute("DROP TABLE IF EXISTS tpl_shop");

			TestDdl.execute(db, """
				CREATE TABLE tpl_shop (
					id   bigint unsigned primary key,
					name varchar(100) null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
				""");

			TestDdl.execute(db, """
				CREATE TABLE tpl_customer (
					id      bigint unsigned primary key,
					shop_id bigint unsigned not null,
					name    varchar(100) null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
				""");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	private static void enableSqlCache () {

		Conf.reload();
		Conf.replace(Conf.conf().config().withValue(
			SqlCacheConf.KEY_ENABLED, com.typesafe.config.ConfigValueFactory.fromAnyRef(true)));

	}

	@AfterAll
	static void dropTables () {

		Conf.reload();

		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS tpl_customer");
			db.execute("DROP TABLE IF EXISTS tpl_shop");
		} catch (Exception ignore) {
		}

	}

	@BeforeEach
	void reset () {

		enableSqlCache();
		SqlCache.replace(new MemorySqlCacheStore());

		try (DB db = DBUtil.getMainDB()) {

			db.execute("DELETE FROM tpl_customer");
			db.execute("DELETE FROM tpl_shop");
			db.execute("INSERT INTO tpl_shop (id, name) VALUES (1, 'ショップ1'), (2, 'ショップ2')");
			db.execute("INSERT INTO tpl_customer (id, shop_id, name) VALUES (10, 1, 'あ'), (11, 1, 'い'), (12, 1, 'う'), (20, 2, 'え')");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	private static String json (Optional<Data> row) {

		return row.map(Data::getJsonString).orElse("(なし)");

	}

	private static String json (List<Data> rows) {

		return rows.stream().map(Data::getJsonString).toList().toString();

	}

	@Test
	@DisplayName("1 行・結合：今のビルダーと同じ形（テーブル名でネスト）で返る")
	void selectSameShapeAsBuilder () {

		try (DB db = DBUtil.getMainDB()) {

			Optional<Data> expected = db.select(SQL.select()
				.from(TplCustomer.instance())
				.inner(TplShop.instance()).on(TplShop.id.eq(TplCustomer.shop_id))
				.where(TplCustomer.id.eq(11L)));

			Optional<Data> actual = db.select(CUSTOMER_BY_ID.bind("id", 11L));

			assertTrue(actual.isPresent());
			assertEquals(json(expected), json(actual));
			assertEquals("い", actual.get().getData("tpl_customer").getString("name"));
			assertEquals("ショップ1", actual.get().getData("tpl_shop").getString("name"));
			assertTrue(db.select(CUSTOMER_BY_ID.bind("id", 999L)).isEmpty());

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("IN の値の数を変えても、その数の行が返る")
	void selectListWithInList () {

		try (DB db = DBUtil.getMainDB()) {

			assertEquals(1, db.selectList(CUSTOMERS_IN_SHOP.bind("shop", 1L, "ids", List.of(10L))).size());
			assertEquals(3, db.selectList(CUSTOMERS_IN_SHOP.bind("shop", 1L, "ids", List.of(10L, 11L, 12L, 20L))).size());

			List<Data> expected = db.selectList(SQL.select().from(TplCustomer.instance())
				.where(TplCustomer.shop_id.eq(1L))
				.where(TplCustomer.id.in(List.of(11L, 12L)))
				.orderBy(TplCustomer.id.asc()));
			assertEquals(json(expected), json(db.selectList(CUSTOMERS_IN_SHOP.bind("shop", 1L, "ids", List.of(11L, 12L)))));

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("SQL 結果キャッシュ：2 回目は当たり、行を書き換えると外れる")
	void cachedHitAndInvalidate () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();
		SqlCache.replace(store);

		try (DB db = DBUtil.getMainDB()) {

			// 同じ形をビルダーで引いて入れておく
			db.selectCached(SQL.select()
				.from(TplCustomer.instance())
				.inner(TplShop.instance()).on(TplShop.id.eq(TplCustomer.shop_id))
				.where(TplCustomer.id.eq(11L)));
			assertEquals(1, store.size());

			// template で引いても増えない（キーが同じで、当たっている）
			assertEquals("い", db.selectCached(CUSTOMER_BY_ID.bind("id", 11L)).orElseThrow().getData("tpl_customer").getString("name"));
			assertEquals(1, store.size(), "template のキーがビルダーと違う");

			assertEquals(1, db.selectListCached(CUSTOMERS_IN_SHOP.bind("shop", 1L, "ids", List.of(11L))).size());
			assertEquals(2, store.size());
			db.selectListCached(CUSTOMERS_IN_SHOP.bind("shop", 1L, "ids", List.of(11L)));
			assertEquals(2, store.size());

			// 行を足しても、キーで 1 行ずつに決まっている結果は消えない（IN の値の並びの印も、ビルダーの IN と同じく値として扱う）
			db.insert(SQL.insert(TplCustomer.instance()).value(TplCustomer.id, 30L).value(TplCustomer.shop_id, 1L).value(TplCustomer.name, "お"));
			assertEquals(2, store.size(), "行を足しただけで、キーで決まっている結果が消えた");

			// ビルダーで書き換えると、その行に依存するキャッシュが消え、引き直すと新しい値になる
			db.update(SQL.update(TplCustomer.instance()).set(TplCustomer.name, "Y").where(TplCustomer.id.eq(11L)));
			assertEquals(0, store.size(), "書き換えた行のキャッシュが残っている");
			assertEquals("Y", db.selectCached(CUSTOMER_BY_ID.bind("id", 11L)).orElseThrow().getData("tpl_customer").getString("name"));
			assertEquals("Y", db.selectListCached(CUSTOMERS_IN_SHOP.bind("shop", 1L, "ids", List.of(11L))).getFirst().getData("tpl_customer").getString("name"));

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("値の印を template にせずビルダーのまま投げると、DB に送る前に断る")
	void paramInPlainBuilderIsRejected () {

		try (DB db = DBUtil.getMainDB()) {

			assertThrows(SqlBuildException.class, () -> db.select(SQL.select().from(TplCustomer.instance())
				.where(TplCustomer.id.eq(Dsl.param("id")))));
			assertThrows(SqlBuildException.class, () -> db.selectList(SQL.select().from(TplCustomer.instance())
				.where(TplCustomer.id.in(Dsl.params("ids")))));

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

}
