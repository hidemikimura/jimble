package io.jimble.db;

import io.jimble.util.conf.Conf;
import io.jimble.core.context.BatchContext;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.TestSchema;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.data.Data;
import io.jimble.util.exception.CodeException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	// region トランザクションと、戻り値で返るエラー（D-155 / D-156）

	@Test
	@DisplayName("D-155 中でエラーが出たら、コミットしない")
	void transactionRefusesToCommitAfterAnError () throws Exception {

		/*
		 * <b>ここが素通りしていた。</b>
		 *
		 * jimble の DB は<b>エラーを戻り値で返す</b>（原則4）ので、
		 * 中の書き込みが -1 を返しても<b>処理は正常に終わったように見える</b>——
		 * `DBTransaction.transaction(...)` はそのまま commit していた。
		 */
		DB db = DBUtil.getMainDB();

		assertThrows(CodeException.class, () -> DBTransaction.transaction(db, transaction -> {

			insertSite(db, "先に入れるほう", 1L);

			// 無い列を触って失敗させる（例外にはならず -1 が返る）
			db.update("UPDATE site SET そんな列は無い = 1");

		}), "エラーが出ているのにコミットしています");

		assertNull(db.select(SQL.select().from(TestSchema.Site.instance()))
			, "拒んだのに入っています");

	}

	@Test
	@DisplayName("D-156 失敗のあとに何を書いても、コミットまで進めなければ何も残らない")
	void nothingSurvivesAFailedTransaction () throws Exception {

		/*
		 * <b>失敗のあとの文が通るかどうかは、製品によって違う。</b>
		 *
		 * - PostgreSQL：<b>ROLLBACK するまで以降を全部断る</b>
		 *   （{@code current transaction is aborted, commands ignored until end of transaction block}）
		 * - MySQL：<b>そのまま通る</b>（1文の失敗でトランザクションを中断しない）
		 *
		 * <b>この違いは CI が教えてくれた。</b>「PostgreSQL では通らない」を決まりとして書いたら、
		 * MySQL の dbTest で落ちた——<b>手元に MariaDB が無く、確かめずに書いていた</b>。
		 *
		 * <b>なので、ここで固定するのは「両方で同じこと」だけにする。</b>
		 * すなわち<b>コミットが拒まれ、1行も残らない</b>。
		 * 途中で何本通ったかは<b>製品の都合</b>であって、jimble の約束ではない。
		 *
		 * <b>MySQL 側でこそ守りが要る。</b>あちらは失敗のあとの文が本当に通るので、
		 * {@code commit()} の守りが無ければ<b>それがそのままコミットされる</b>——
		 * これが D-155 で塞いだ部分コミットである。
		 */
		DB db = DBUtil.getMainDB();

		db.beginTransaction();

		insertSite(db, "失敗より前", 1L);

		db.update("UPDATE site SET そんな列は無い = 1");
		assertTrue(db.isError(), "失敗していない（テストの前提が崩れています）");

		// 通るか通らないかは製品による。どちらでもよい
		db.insert(SQL.insert(TestSchema.Site.instance())
			.value(TestSchema.Site.group_id, 1L).value(TestSchema.Site.name, "失敗より後"));

		assertThrows(CodeException.class, db::commitEndTransaction
			, "エラーが出ているのにコミットしています");

		assertNull(db.select(SQL.select().from(TestSchema.Site.instance()))
			, "拒んだのに残っています（部分コミット）");

	}

	@Test
	@DisplayName("D-156 rollback すれば、そこから書き直して続けられる")
	void rollbackLetsYouCarryOn () throws Exception {

		/*
		 * <b>呼んだ側が分岐して続けられること。</b>
		 *
		 * SQL → commit → SQL（失敗）→ rollback → SQL → commit と書ける。
		 * <b>rollback がエラーの持ち越しも畳む</b>ので、
		 * 最後の commit は「まだエラーが出ている」と言って断られない。
		 */
		DB db = DBUtil.getMainDB();

		db.beginTransaction();

		try {

			insertSite(db, "1つめ", 1L);
			db.commit();                       // ここで確定。トランザクションは続く

			db.update("UPDATE site SET そんな列は無い = 1");
			assertTrue(db.isError(), "失敗していない（テストの前提が崩れています）");

			db.rollback();                     // 呼んだ側が決着を付ける

			insertSite(db, "2つめ", 1L);
			db.commitEndTransaction();         // 断られないこと

		} catch (Exception ex) {
			db.rollbackEndTransaction();
			throw ex;
		}

		List<Data> rows = db.selectList(SQL.select().from(TestSchema.Site.instance()));

		assertEquals(2, rows.size(), "書き直したぶんが残っていません: " + rows);

	}

	@Test
	@DisplayName("D-156 DBTransaction を通さなくても、コミットは拒む")
	void rawTransactionAlsoRefuses () throws Exception {

		/*
		 * <b>枠組み自身が4か所、DBTransaction を通さずに直に書いている</b>
		 * （`DbRateLimitStore` / `DbSqlCacheStore` / `Migration` / `CodeMigration`）。
		 * 守りが `DBTransaction` にしか無いと、この道だけ部分コミットに戻る。
		 */
		DB db = DBUtil.getMainDB();

		db.beginTransaction();

		insertSite(db, "直に書いた道", 1L);

		db.update("UPDATE site SET そんな列は無い = 1");

		assertThrows(CodeException.class, db::commitEndTransaction
			, "DBTransaction を通さない道だけ素通りしています");

		assertNull(db.select(SQL.select().from(TestSchema.Site.instance()))
			, "拒んだのに入っています");

	}

	@Test
	@DisplayName("D-155 エラーが無ければ、これまでどおりコミットする")
	void transactionStillCommits () throws Exception {

		DB db = DBUtil.getMainDB();

		DBTransaction.transaction(db, transaction -> insertSite(db, "ふつうに入る", 1L));

		assertNotNull(db.select(SQL.select().from(TestSchema.Site.instance())));

	}

	// region ここで固定していないこと（トランザクション）

	/*
	 * <b>失敗のあとに何本通るかは、ここでは固定していない。</b>
	 * PostgreSQL は断り、MySQL は通す——<b>製品の都合</b>であって jimble の約束ではない。
	 * 固定しているのは「コミットが拒まれ、1行も残らない」ほうである。
	 *
	 * <b>ミューテーションのうち2つは、PostgreSQL では落とせない。</b>
	 *
	 * - <b>{@code commit()} の守り</b>（{@code requireNoErrorSinceTransaction}）
	 * - <b>守りが投げる前に巻き戻すこと</b>
	 *
	 * PostgreSQL は<b>中断したトランザクションへの COMMIT を ROLLBACK として扱う</b>ので、
	 * 守りが無くても結果が同じになる。加えて {@code endTransaction()} が
	 * 最後の文のエラーを投げ直すので、例外も出てしまう。
	 *
	 * <b>MySQL では効く。</b>あちらは失敗のあとの文がそのまま通るので、
	 * 守りが無ければ<b>それがコミットされる</b>——部分コミットが戻る。
	 * <b>この前提は CI（dbTest / MySQL）が確かめた</b>：
	 * 「PostgreSQL では通らない」と決め打ちした版が、MySQL で落ちた。
	 *
	 * <b>だから両方残す。</b>「PostgreSQL がたまたま助けてくれる」に頼らない。
	 */

	// endregion

	// endregion

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
	@DisplayName("F-D-08 SQL が揃っていない insertBatch は止まる（値が横にずれない）")
	void insertBatchRejectsMismatchedSql () {

		DB db = DBUtil.getMainDB();

		/*
		 * <b>value() の並びが違うと SQL が変わる。</b>
		 *
		 * 直していなかったころは、<b>先頭の SQL に全員のパラメータを流し込んで</b>いた。
		 * 個数が合っているので DB も気づかず、
		 * 2件目は group_id に "B" を、name に 1 を入れようとする——
		 * 型が合えば<b>例外も警告も無しに値が入れ替わって入る</b>。
		 */
		List<Long> ids = db.insertBatch(List.of(
			SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, 1L).value(TestSchema.Site.name, "A")
			, SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.name, "B").value(TestSchema.Site.group_id, 1L)
		));

		assertNull(ids, "SQL が違うのに通っている");
		assertTrue(db.isError(), "エラーが立っていない");
		assertEquals("DB_998", db.getError().getCode(), String.valueOf(db.getError()));

		// 1件も入っていない（まとめて止めるので、途中まで入ることもない）
		assertEquals(0, db.selectList(SQL.select().from(TestSchema.Site.instance())).size());

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
