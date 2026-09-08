package io.jimble.db;

import io.jimble.util.conf.Conf;
import io.jimble.core.context.BatchContext;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.TestSchema;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移送した DB 層が実 DB に対して動くことの確認
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。実行は次のとおり。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 *
 * <p>
 * 接続先は {@code src/test/resources/application.dbtest.conf}。
 * 環境変数 {@code JIMBLE_TEST_DB_URL} / {@code JIMBLE_TEST_DB_USER} /
 * {@code JIMBLE_TEST_DB_PASSWORD} で上書きできる。
 * </p>
 */
@Tag("db")
class DbIntegrationTest {

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), DbIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS site");
		TestDdl.execute(db, """
			CREATE TABLE site (
				id          bigint unsigned auto_increment comment 'ID' primary key,
				group_id    bigint unsigned not null comment 'グループID',
				name        varchar(250)    null comment 'サイト名',
				feed_count  bigint unsigned default 0 not null comment 'フィード数',
				deleted_at  datetime        null comment '削除日時'
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment 'サイト'
			""");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DBUtil.getMainDB().execute("TRUNCATE TABLE site");

	}

	/**
	 * サイトを1件登録する
	 */
	private long insertSite (DB db, String name, long groupId) {

		long id = db.insert(
			SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, groupId)
				.value(TestSchema.Site.name, name));

		assertFalse(db.isError(), String.valueOf(db.getError()));
		assertTrue(id > 0);

		return id;

	}

	@Test
	@DisplayName("INSERT した行を SELECT できる。結果はテーブル名でネストする")
	void insertAndSelect () {

		DB db = DBUtil.getMainDB();

		long id = insertSite(db, "俺的まとめ", 1L);

		Data row = db.select(
			SQL.select()
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.eq(id)));

		assertNotNull(row);
		assertEquals("俺的まとめ", row.getString(TestSchema.Site.name), "Column 版で取れること");
		assertNull(row.getString("name"), "文字列キー版は null（テーブル名でネストしているため）");
		assertEquals(id, row.getLong(TestSchema.Site.id));

	}

	@Test
	@DisplayName("F-D-11 該当0件の select は null を返す。エラーではない")
	void selectNoRowReturnsNull () {

		DB db = DBUtil.getMainDB();

		Data row = db.select(
			SQL.select()
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.eq(999999L)));

		assertNull(row);
		assertFalse(db.isError(), "0件はエラーではない");

	}

	@Test
	@DisplayName("F-D-11 SQL エラーは例外ではなく戻り値で返る")
	void sqlErrorIsReturnedNotThrown () {

		DB db = DBUtil.getMainDB();

		List<Data> rows = db.selectList("SELECT * FROM not_exists_table");

		assertNull(rows, "エラー時は null を返す");
		assertTrue(db.isError(), "エラーが保持されていること");
		assertNotNull(db.getError());

	}

	@Test
	@DisplayName("UPDATE / DELETE と Dsl.now()")
	void updateAndDelete () {

		DB db = DBUtil.getMainDB();

		long id = insertSite(db, "旧名", 1L);

		int updated = db.update(
			SQL.update(TestSchema.Site.instance())
				.set(TestSchema.Site.name, "新名")
				.set(TestSchema.Site.deleted_at, Dsl.now())
				.where(TestSchema.Site.id.eq(id)));

		assertEquals(1, updated);
		assertFalse(db.isError());

		Data row = db.select(
			SQL.select().from(TestSchema.Site.instance()).where(TestSchema.Site.id.eq(id)));

		assertEquals("新名", row.getString(TestSchema.Site.name));
		assertNotNull(row.getDate(TestSchema.Site.deleted_at), "Dsl.now() が反映されること");

		assertEquals(1, db.delete(
			SQL.delete(TestSchema.Site.instance()).where(TestSchema.Site.id.eq(id))));

	}

	@Test
	@DisplayName("トランザクションをロールバックできる")
	void transactionRollback () throws Exception {

		DB db = DBUtil.getMainDB();

		db.beginTransaction();
		try {
			insertSite(db, "巻き戻される", 1L);
			db.rollbackEndTransaction();
		} catch (Exception ex) {
			db.rollbackEndTransaction();
			throw ex;
		}

		assertNull(
			db.select(SQL.select().from(TestSchema.Site.instance()))
			, "ロールバックされていること");

	}

	@Test
	@DisplayName("トランザクションをコミットできる")
	void transactionCommit () throws Exception {

		DB db = DBUtil.getMainDB();

		db.beginTransaction();
		insertSite(db, "残る", 1L);
		db.commitEndTransaction();

		assertNotNull(db.select(SQL.select().from(TestSchema.Site.instance())));

	}

	@Test
	@DisplayName("insertBatch でまとめて登録できる")
	void insertBatch () {

		DB db = DBUtil.getMainDB();

		List<Long> ids = db.insertBatch(List.of(
			SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, 1L).value(TestSchema.Site.name, "A")
			, SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, 1L).value(TestSchema.Site.name, "B")
			, SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, 2L).value(TestSchema.Site.name, "C")
		));

		assertNotNull(ids, String.valueOf(db.getError()));
		assertEquals(3, ids.size());

		List<Data> rows = db.selectList(
			SQL.select().from(TestSchema.Site.instance()).orderBy(TestSchema.Site.id));

		assertEquals(List.of("A", "B", "C")
			, rows.stream().map(row -> row.getString(TestSchema.Site.name)).toList());

	}

	@Test
	@DisplayName("in() と JOIN と集約が実 DB で動く")
	void inJoinAggregate () {

		DB db = DBUtil.getMainDB();

		long a = insertSite(db, "A", 1L);
		long b = insertSite(db, "B", 2L);
		insertSite(db, "C", 2L);

		List<Data> rows = db.selectList(
			SQL.select()
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.in(List.of(a, b)))
				.orderBy(TestSchema.Site.id));

		assertEquals(2, rows.size());

		Data count = db.select(
			SQL.select(Dsl.count(TestSchema.Site.id).as("cnt"))
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.group_id.eq(2L)));

		assertEquals(2, count.getInt("cnt"), count.toString());

	}

	@Test
	@DisplayName("F-D-17 SQL の実行回数と実行時間が Context に集計される")
	void sqlMetricsAreRecordedOnContext () {

		try (BatchContext context = new BatchContext("DBテスト")) {
			context.run(() -> {
				DB db = DBUtil.getMainDB();

				insertSite(db, "計測1", 1L);
				insertSite(db, "計測2", 1L);
				db.selectList(SQL.select().from(TestSchema.Site.instance()));
			});

			assertTrue(context.sqlExecuteCount() >= 3
				, "実行回数が集計されていること: " + context.sqlExecuteCount());
			assertFalse(context.sqlExecuteTime().isZero(), "実行時間が集計されていること");
		}

	}

	@Test
	@DisplayName("日本語がそのまま往復する")
	void japaneseRoundTrip () {

		DB db = DBUtil.getMainDB();

		long id = insertSite(db, "俺的まとめ速報＠あ｜ア", 1L);

		Data row = db.select(
			SQL.select().from(TestSchema.Site.instance()).where(TestSchema.Site.id.eq(id)));

		assertEquals("俺的まとめ速報＠あ｜ア", row.getString(TestSchema.Site.name));

	}

}
