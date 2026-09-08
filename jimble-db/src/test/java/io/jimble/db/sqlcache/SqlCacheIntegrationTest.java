package io.jimble.db.sqlcache;

import io.jimble.db.TestDdl;
import io.jimble.core.context.BatchContext;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.db.sqlcache.SqlCacheSchema.Customer;
import io.jimble.db.sqlcache.SqlCacheSchema.OrderItem;
import io.jimble.db.sqlcache.SqlCacheSchema.Shop;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 結果キャッシュを実 DB で確かめる（要件 F-D-28）
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 *
 * <p>
 * 「効いているか」は<b>飛んだ SQL の本数</b>で見る（要件 F-D-17）。
 * 値が合っているだけでは、引き直しているのか当たっているのか分からない。
 * </p>
 */
@Tag("db")
class SqlCacheIntegrationTest {

	@BeforeAll
	static void createTables () {

		enableSqlCache();

		assertTrue(DBUtil.load(Conf.conf().config(), SqlCacheIntegrationTest.class), "DB に接続できませんでした");

		try (DB db = DBUtil.getMainDB()) {

			db.execute("DROP TABLE IF EXISTS customer");
			db.execute("DROP TABLE IF EXISTS shop");
			db.execute("DROP TABLE IF EXISTS order_item");

			TestDdl.execute(db, """
				CREATE TABLE shop (
					id   bigint unsigned auto_increment primary key,
					name varchar(100) null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
				""");

			TestDdl.execute(db, """
				CREATE TABLE customer (
					id      bigint unsigned auto_increment primary key,
					shop_id bigint unsigned not null,
					code    varchar(50) null,
					name    varchar(100) null,
					constraint customer_code unique (code)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
				""");

			TestDdl.execute(db, """
				CREATE TABLE order_item (
					id          bigint unsigned auto_increment primary key,
					customer_id bigint unsigned not null,
					amount      bigint unsigned not null default 0
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
				""");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * SQL 結果キャッシュを有効にする
	 *
	 * <p>
	 * <b>既定は false</b>（D-95）。使わないアプリに更新のたびの後始末を払わせないためで、
	 * テストは明示的に入れる。
	 * </p>
	 */
	private static void enableSqlCache () {

		Conf.reload();

		Conf.replace(Conf.conf().config().withValue(
			SqlCacheConf.KEY_ENABLED, com.typesafe.config.ConfigValueFactory.fromAnyRef(true)));

	}

	@AfterAll
	static void dropTables () {

		Conf.reload();


		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS customer");
			db.execute("DROP TABLE IF EXISTS shop");
			db.execute("DROP TABLE IF EXISTS order_item");
		} catch (Exception ignore) {
		}

	}

	@BeforeEach
	void reset () {

		enableSqlCache();
		SqlCache.replace(new MemorySqlCacheStore());

		try (DB db = DBUtil.getMainDB()) {

			db.execute("DELETE FROM order_item");
			db.execute("DELETE FROM customer");
			db.execute("DELETE FROM shop");

			db.execute("INSERT INTO shop (id, name) VALUES (1, '店1'), (2, '店2')");
			db.execute("INSERT INTO customer (id, shop_id, code, name) VALUES (1, 1, 'C1', '顧客1'), (2, 2, 'C2', '顧客2')");

			// id を明示して入れたので、採番の続きを合わせる（PostgreSQL は追いつかない）
			TestDdl.syncSequence(db, "shop", "id");
			TestDdl.syncSequence(db, "customer", "id");
			TestDdl.syncSequence(db, "order_item", "id");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	// region 小物

	/**
	 * ご提示の SELECT
	 *
	 * <pre>
	 * SELECT * FROM customer INNER JOIN shop ON (customer.shop_id = shop.id) WHERE customer.id = 1
	 * </pre>
	 *
	 * @param db	DB
	 * @return	結果
	 */
	private static Data selectCustomer1 (DB db) {

		return db.selectCached(
			SQL.select()
				.from(Customer.instance())
				.inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
				.where(Customer.id.eq(1)));

	}

	/**
	 * 飛んだ SQL の本数を数える
	 *
	 * @param name	名前
	 * @param body	処理
	 * @return	本数
	 */
	private long countSql (String name, Runnable body) {

		try (BatchContext context = new BatchContext(name)) {
			context.run(body);
			return context.sqlExecuteCount();
		}

	}

	// endregion

	@Test
	@DisplayName("2回目はキャッシュに当たって SQL が飛ばない")
	void hitsCache () {

		try (DB db = DBUtil.getMainDB()) {

			long first = countSql("1回目", () -> assertNotNull(selectCustomer1(db)));
			long second = countSql("2回目", () -> assertNotNull(selectCustomer1(db)));

			assertEquals(1, first, "1回目で引いていない");
			assertEquals(0, second, "2回目で引き直している: " + second);

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("同じ id を更新したらキャッシュが消える")
	void updateSameCustomerClears () {

		try (DB db = DBUtil.getMainDB()) {

			assertEquals("顧客1", selectCustomer1(db).getData("customer").getString("name"));

			db.update(SQL.update(Customer.instance())
				.set(Customer.name, "name1")
				.where(Customer.id.eq(1)));

			long sql = countSql("引き直し", () ->
				assertEquals("name1", selectCustomer1(db).getData("customer").getString("name")));

			assertEquals(1, sql, "キャッシュが消えていない");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("違う id を更新してもキャッシュは消えない")
	void updateOtherCustomerKeeps () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.update(SQL.update(Customer.instance())
				.set(Customer.name, "name2")
				.where(Customer.id.eq(2)));

			long sql = countSql("当たるはず", () -> assertNotNull(selectCustomer1(db)));

			assertEquals(0, sql, "関係ない行の更新でキャッシュが消えている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("結合先（shop）の同じ id を更新したらキャッシュが消える")
	void updateSameShopClears () {

		try (DB db = DBUtil.getMainDB()) {

			assertEquals("店1", selectCustomer1(db).getData("shop").getString("name"));

			db.update(SQL.update(Shop.instance())
				.set(Shop.name, "shop1")
				.where(Shop.id.eq(1)));

			long sql = countSql("引き直し", () ->
				assertEquals("shop1", selectCustomer1(db).getData("shop").getString("name")));

			assertEquals(1, sql, "結合先の更新でキャッシュが消えていない");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("結合先（shop）の違う id を更新してもキャッシュは消えない")
	void updateOtherShopKeeps () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.update(SQL.update(Shop.instance())
				.set(Shop.name, "shop2")
				.where(Shop.id.eq(2)));

			long sql = countSql("当たるはず", () -> assertNotNull(selectCustomer1(db)));

			assertEquals(0, sql, "関係ない結合先の更新でキャッシュが消えている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("一意列で絞った更新も、同じ行なら消える")
	void updateByUniqueClears () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.update(SQL.update(Customer.instance())
				.set(Customer.name, "コードで更新")
				.where(Customer.code.eq("C1")));

			long sql = countSql("引き直し", () ->
				assertEquals("コードで更新", selectCustomer1(db).getData("customer").getString("name")));

			assertEquals(1, sql, "一意列で絞った更新でキャッシュが消えていない");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("キーでない列で絞った更新は、そのテーブルに触るものを全部消す")
	void updateByNonKeyClearsTable () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.update(SQL.update(Customer.instance())
				.set(Customer.name, "まとめて")
				.where(Customer.shop_id.eq(1)));

			long sql = countSql("引き直し", () -> assertNotNull(selectCustomer1(db)));

			assertEquals(1, sql, "絞れない更新でキャッシュが残っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("INSERT は一覧のキャッシュを消す")
	void insertClearsList () {

		try (DB db = DBUtil.getMainDB()) {

			List<Data> before = db.selectListCached(
				SQL.select().from(Customer.instance()).where(Customer.shop_id.eq(1)));

			assertEquals(1, before.size());

			db.insert(SQL.insert(Customer.instance())
				.value(Customer.shop_id, 1)
				.value(Customer.code, "C3")
				.value(Customer.name, "顧客3"));

			List<Data> after = db.selectListCached(
				SQL.select().from(Customer.instance()).where(Customer.shop_id.eq(1)));

			assertEquals(2, after.size(), "INSERT のあとも古い一覧が返っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("1対Nの結合は、子を足したら消える")
	void insertChildClearsJoin () {

		try (DB db = DBUtil.getMainDB()) {

			List<Data> before = db.selectListCached(
				SQL.select()
					.from(Customer.instance())
					.left(OrderItem.instance()).on(OrderItem.customer_id.eq(Customer.id))
					.where(Customer.id.eq(1)));

			assertEquals(1, before.size());

			db.insert(SQL.insert(OrderItem.instance())
				.value(OrderItem.customer_id, 1)
				.value(OrderItem.amount, 100));

			List<Data> after = db.selectListCached(
				SQL.select()
					.from(Customer.instance())
					.left(OrderItem.instance()).on(OrderItem.customer_id.eq(Customer.id))
					.where(Customer.id.eq(1)));

			assertEquals(1, after.size());
			assertEquals(100L, after.getFirst().getData("order_item").getLong("amount")
				, "子を足したのに古い結果が返っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("生 SQL の更新は全部消す（どこに当たるか読めないため）")
	void rawSqlClearsAll () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.update("UPDATE customer SET name = ? WHERE id = ?", "生SQL", 1);

			long sql = countSql("引き直し", () ->
				assertEquals("生SQL", selectCustomer1(db).getData("customer").getString("name")));

			assertEquals(1, sql, "生 SQL の更新でキャッシュが残っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("ロールバックしたらキャッシュは消えない")
	void rollbackKeepsCache () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.beginTransaction();
			db.update(SQL.update(Customer.instance()).set(Customer.name, "消える").where(Customer.id.eq(1)));
			db.rollbackEndTransaction();

			long sql = countSql("当たるはず", () ->
				assertEquals("顧客1", selectCustomer1(db).getData("customer").getString("name")));

			assertEquals(0, sql, "ロールバックしたのにキャッシュが消えている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("コミットしたらまとめて消える")
	void commitClearsCache () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.beginTransaction();
			db.update(SQL.update(Customer.instance()).set(Customer.name, "確定").where(Customer.id.eq(1)));
			db.commitEndTransaction();

			long sql = countSql("引き直し", () ->
				assertEquals("確定", selectCustomer1(db).getData("customer").getString("name")));

			assertEquals(1, sql, "コミットしたのにキャッシュが残っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("トランザクションの中では未確定の値をキャッシュに入れない")
	void doesNotCacheInsideTransaction () {

		try (DB db = DBUtil.getMainDB()) {

			db.beginTransaction();
			db.update(SQL.update(Customer.instance()).set(Customer.name, "未確定").where(Customer.id.eq(1)));

			// トランザクションの中なので素通しで引く（自分の変更が見える）
			assertEquals("未確定", selectCustomer1(db).getData("customer").getString("name"));

			db.rollbackEndTransaction();

			// 未確定の値が残っていたら、ここで「未確定」が返る
			assertEquals("顧客1", selectCustomer1(db).getData("customer").getString("name")
				, "未コミットの値がキャッシュに入っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("キャッシュから戻しても値の型がそのまま")
	void typesSurviveTheCache () {

		try (DB db = DBUtil.getMainDB()) {

			Data first = selectCustomer1(db);
			Data second = selectCustomer1(db);

			assertEquals(1L, second.getData("customer").getLong("id"));
			assertEquals("顧客1", second.getData("customer").getString("name"));
			assertEquals("店1", second.getData("shop").getString("name"));

			/*
			 * <b>型そのものが変わらないことが要点。</b>
			 * JSON にして戻すと Long が Integer に、BigDecimal が Float に、
			 * byte[] が数値の配列に、"true" という文字列が Boolean になる。
			 * <b>キャッシュに当たったときだけ結果が変わる</b>という、
			 * いちばん気づけない形になる。
			 */
			for (String table : new String[]{"customer", "shop"}) {
				for (String column : first.getData(table).keySet()) {

					Object before = first.getData(table).get(column);
					Object after = second.getData(table).get(column);

					if (before == null) {
						assertNull(after, table + "." + column);
						continue;
					}

					assertEquals(before.getClass(), after.getClass()
						, "%s.%s の型が変わった".formatted(table, column));
					assertEquals(before, after, table + "." + column);

				}
			}

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("ON DUPLICATE KEY UPDATE で書き換えた行のキャッシュも消える")
	void upsertClearsRowCache () {

		try (DB db = DBUtil.getMainDB()) {

			selectCustomer1(db);

			db.insert(SQL.insert(Customer.instance())
				.value(Customer.id, 1)
				.value(Customer.shop_id, 1)
				.value(Customer.code, "C1")
				.value(Customer.name, "upsert")
				.onDuplicateKeyUpdate(Customer.name, "upsert"));

			assertEquals("upsert", selectCustomer1(db).getData("customer").getString("name")
				, "upsert で書き換えたのに古い行が返っている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	// region 切っているとき（要件 F-D-28 / D-95）

	@Test
	@DisplayName("切っていると selectCached は素通しで引く")
	void disabledBypassesCache () {

		try (DB db = DBUtil.getMainDB()) {

			Conf.reload();   // sql_cache.enabled を書いていない状態に戻す

			long first = countSql("1回目", () -> assertNotNull(selectCustomer1(db)));
			long second = countSql("2回目", () -> assertNotNull(selectCustomer1(db)));

			assertEquals(1, first);
			assertEquals(1, second, "切っているのにキャッシュに当たっている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("切っていると更新はキャッシュに触らない")
	void disabledDoesNotInvalidate () {

		MemorySqlCacheStore store = new MemorySqlCacheStore();
		SqlCache.replace(store);

		try (DB db = DBUtil.getMainDB()) {

			// 有効なうちに1件入れておく
			selectCustomer1(db);
			assertEquals(1, store.size());

			Conf.reload();   // ここから切る

			db.update(SQL.update(Customer.instance())
				.set(Customer.name, "切ってから更新")
				.where(Customer.id.eq(1)));

			db.insert(SQL.insert(Customer.instance())
				.value(Customer.shop_id, 1).value(Customer.code, "C9").value(Customer.name, "顧客9"));

			db.delete(SQL.delete(Customer.instance()).where(Customer.id.eq(2)));

			/*
			 * <b>1件も消えていないことが要点。</b>
			 * 消えていたら、切っているのに削除処理が走っている。
			 */
			assertEquals(1, store.size(), "切っているのにキャッシュを消しにいっている");

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

	}

	@Test
	@DisplayName("切ったまま selectCached を呼んだら初回だけ警告する")
	void disabledWarnsOnce () {

		List<String> warns = new ArrayList<>();

		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level == org.slf4j.event.Level.WARN) {
				warns.add(message);
			}
		});

		try (DB db = DBUtil.getMainDB()) {

			Conf.reload();
			SqlCache.resetWarning();

			selectCustomer1(db);
			selectCustomer1(db);
			selectCustomer1(db);

			long count = warns.stream().filter(text -> text.contains("sql_cache.enabled")).count();

			assertEquals(1, count, "警告が " + count + " 回出ている: " + warns);

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		} finally {
			Log.resetSink();
		}

	}

	// endregion

	@Test
	@DisplayName("置き場を DB にしても回り込まない（無限再帰しない）")
	void dbStoreDoesNotRecurse () {

		SqlCache.replace(new DbSqlCacheStore());

		try (DB db = DBUtil.getMainDB()) {

			/*
			 * 置き場が db_cache を書き換えるたびにキャッシュを消しにいくと、
			 * <b>消す処理がまた書き込みを起こして際限なく回る。</b>
			 */
			assertNotNull(selectCustomer1(db));

			long sql = countSql("2回目", () -> assertNotNull(selectCustomer1(db)));

			// 置き場が DB なので取り出しの1本は飛ぶ。引き直していないことが要点
			assertTrue(sql <= 1, "DB の置き場で引き直している: " + sql);

			db.update(SQL.update(Customer.instance())
				.set(Customer.name, "db置き場")
				.where(Customer.id.eq(1)));

			assertEquals("db置き場", selectCustomer1(db).getData("customer").getString("name"));

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		} finally {
			SqlCache.replace(new MemorySqlCacheStore());
		}

	}

}
