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
		DBUtil.load(Conf.conf().config(), DbIntegrationTest.class);

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

		long id = db.insertKey(
			SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, groupId)
				.value(TestSchema.Site.name, name));

		assertTrue(id > 0);

		return id;

	}

	private static boolean hasRows (DB db) {

		return db.select(SQL.select().from(TestSchema.Site.instance())).isPresent();

	}

	@Test
	@DisplayName("INSERT した行を SELECT できる。結果はテーブル名でネストする")
	void insertAndSelect () {

		DB db = DBUtil.getMainDB();

		long id = insertSite(db, "俺的まとめ", 1L);

		Data row = db.select(
			SQL.select()
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.eq(id))).orElseThrow();

		assertEquals("俺的まとめ", row.getString(TestSchema.Site.name), "Column 版で取れること");
		assertNull(row.getString("name"), "文字列キー版は null（テーブル名でネストしているため）");
		assertEquals(id, row.getLong(TestSchema.Site.id));

	}

	@Test
	@DisplayName("D-193 該当0件の select は空の Optional。例外ではない")
	void selectNoRowReturnsEmpty () {

		DB db = DBUtil.getMainDB();

		assertTrue(db.select(
			SQL.select()
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.eq(999999L))).isEmpty());

		assertTrue(db.selectList(
			SQL.select()
				.from(TestSchema.Site.instance())
				.where(TestSchema.Site.id.eq(999999L))).isEmpty(), "0件は空リスト");

	}

	@Test
	@DisplayName("D-193 SQL の失敗は SqlExecuteException（1.x は null / -1 / false を返していた）")
	void sqlErrorIsThrown () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException e = assertThrows(SqlExecuteException.class, () -> db.selectList("SELECT * FROM not_exists_table"));
		assertEquals("DB_999", e.getCode());
		assertTrue(e.getCause() instanceof CodeException, "元の例外が cause に残る");
		assertTrue(e.getCause().getCause() instanceof java.sql.SQLException, String.valueOf(e.getCause().getCause()));

		assertThrows(SqlExecuteException.class, () -> db.select("SELECT * FROM not_exists_table"));
		assertThrows(SqlExecuteException.class, () -> db.update("UPDATE not_exists_table SET x = 1"));
		assertThrows(SqlExecuteException.class, () -> db.delete("DELETE FROM not_exists_table"));
		assertThrows(SqlExecuteException.class, () -> db.insert("INSERT INTO not_exists_table (x) VALUES (1)"));
		assertThrows(SqlExecuteException.class, () -> db.execute("UPDATE not_exists_table SET x = 1"));

	}

	@Test
	@DisplayName("D-193 一意制約の違反だけは DuplicateKeyException で分けられる")
	void duplicateKeyIsDistinguished () {

		DB db = DBUtil.getMainDB();

		long id = insertSite(db, "先", 1L);

		DuplicateKeyException e = assertThrows(DuplicateKeyException.class, () -> db.insert(
			SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.id, id)
				.value(TestSchema.Site.group_id, 1L)
				.value(TestSchema.Site.name, "後")));
		assertTrue(e.getMessage().contains("一意制約"), e.getMessage());

		// 一意制約でない失敗は親の型のまま
		SqlExecuteException other = assertThrows(SqlExecuteException.class, () -> db.update("UPDATE site SET そんな列は無い = 1"));
		assertFalse(other instanceof DuplicateKeyException);

	}

	@Test
	@DisplayName("UPDATE / DELETE は件数を返す。execute も件数（DDL は 0）")
	void updateAndDelete () {

		DB db = DBUtil.getMainDB();

		long id = insertSite(db, "旧名", 1L);

		int updated = db.update(
			SQL.update(TestSchema.Site.instance())
				.set(TestSchema.Site.name, "新名")
				.set(TestSchema.Site.deleted_at, Dsl.now())
				.where(TestSchema.Site.id.eq(id)));

		assertEquals(1, updated);

		Data row = db.select(
			SQL.select().from(TestSchema.Site.instance()).where(TestSchema.Site.id.eq(id))).orElseThrow();

		assertEquals("新名", row.getString(TestSchema.Site.name));
		assertNotNull(row.getDate(TestSchema.Site.deleted_at), "Dsl.now() が反映されること");

		insertSite(db, "もう1件", 1L);
		assertEquals(2, db.execute("UPDATE site SET group_id = 3"), "execute は当たった件数");
		assertEquals(0, db.execute("UPDATE site SET group_id = 3 WHERE id = -1"));

		assertEquals(1, db.delete(
			SQL.delete(TestSchema.Site.instance()).where(TestSchema.Site.id.eq(id))));

	}

	@Test
	@DisplayName("トランザクションをロールバックできる")
	void transactionRollback () {

		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {
			insertSite(db, "巻き戻される", 1L);
			tx.rollback();
		}

		assertFalse(hasRows(db), "ロールバックされていること");

	}

	@Test
	@DisplayName("トランザクションをコミットできる")
	void transactionCommit () {

		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {
			insertSite(db, "残る", 1L);
			tx.commit();
		}

		assertTrue(hasRows(db));

	}

	// region トランザクションと、受け止めた失敗（D-155 / D-156）

	@Test
	@DisplayName("D-155 中の SQL の失敗を受け止めて続けても、コミットしない")
	void transactionRefusesToCommitAfterACaughtError () {

		/*
		 * 2.0 で失敗は例外になったが、<b>受け止めて続ける</b>と 1.x の -1 と同じ形になる。
		 * そのまま commit まで進めると、失敗した文の前後だけが入る（部分コミット）。
		 */
		DB db = DBUtil.getMainDB();

		TransactionException e = assertThrows(TransactionException.class, () -> db.transaction(tx -> {

			insertSite(db, "先に入れるほう", 1L);

			try {
				db.update("UPDATE site SET そんな列は無い = 1");
			} catch (SqlExecuteException ignore) {
				// 受け止めて続ける
			}

		}), "エラーが出ているのにコミットしています");

		assertEquals("DB_004", e.getCode(), e.getMessage());
		assertFalse(hasRows(db), "拒んだのに入っています");

	}

	@Test
	@DisplayName("D-156 失敗のあとに何を書いても、コミットまで進めなければ何も残らない")
	void nothingSurvivesAFailedTransaction () {

		/*
		 * <b>失敗のあとの文が通るかどうかは、製品によって違う。</b>
		 * PostgreSQL は ROLLBACK するまで以降を全部断り、MySQL はそのまま通す。
		 * ここで固定するのは「両方で同じこと」だけ——<b>コミットが拒まれ、1行も残らない</b>。
		 * <b>MySQL 側でこそ守りが要る。</b>あちらは失敗のあとの文が本当に通るので、
		 * commit の守りが無ければそれがそのままコミットされる（D-155）。
		 */
		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {

			insertSite(db, "失敗より前", 1L);

			assertThrows(SqlExecuteException.class, () -> db.update("UPDATE site SET そんな列は無い = 1"));

			// 通るか通らないかは製品による。どちらでもよい
			try {
				db.insert(SQL.insert(TestSchema.Site.instance())
					.value(TestSchema.Site.group_id, 1L).value(TestSchema.Site.name, "失敗より後"));
			} catch (SqlExecuteException ignore) {
				// PostgreSQL
			}

			assertThrows(TransactionException.class, tx::commit, "エラーが出ているのにコミットしています");

		}

		assertFalse(hasRows(db), "拒んだのに残っています（部分コミット）");

	}

	@Test
	@DisplayName("D-156 巻き戻せば、新しい Tx で書き直して続けられる")
	void rollbackLetsYouCarryOn () {

		/*
		 * 巻き戻しがエラーの持ち越しも畳むので、次の Tx の commit は「まだエラーが出ている」と言って断られない。
		 */
		DB db = DBUtil.getMainDB();

		try (Tx tx = db.begin()) {
			insertSite(db, "1つめ", 1L);
			tx.checkpoint();                    // ここで確定。トランザクションは続く

			assertThrows(SqlExecuteException.class, () -> db.update("UPDATE site SET そんな列は無い = 1"));

			tx.rollback();                      // 呼んだ側が決着を付ける
		}

		try (Tx tx = db.begin()) {
			insertSite(db, "2つめ", 1L);
			tx.commit();                        // 断られないこと
		}

		List<Data> rows = db.selectList(SQL.select().from(TestSchema.Site.instance()));

		assertEquals(2, rows.size(), "書き直したぶんが残っていません: " + rows);

	}

	@Test
	@DisplayName("D-155 エラーが無ければ、これまでどおりコミットする")
	void transactionStillCommits () {

		DB db = DBUtil.getMainDB();

		db.transaction(tx -> insertSite(db, "ふつうに入る", 1L));

		assertTrue(hasRows(db));

	}

	// endregion

	@Test
	@DisplayName("D-193 空の一覧の insertBatch / executeBatch は空リスト（1.x は null）")
	void emptyBatchReturnsEmptyList () {

		DB db = DBUtil.getMainDB();

		assertEquals(List.of(), db.insertBatch(List.of()));
		assertEquals(List.of(), db.executeBatch(List.of()));
		assertEquals(List.of(), db.executeBatch("UPDATE site SET name = ?", List.of()));
		assertEquals(List.of(), db.insertBatch("INSERT INTO site (group_id) VALUES (?)", List.of()));

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
		SqlExecuteException e = assertThrows(SqlExecuteException.class, () -> db.insertBatch(List.of(
			SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.group_id, 1L).value(TestSchema.Site.name, "A")
			, SQL.insert(TestSchema.Site.instance())
				.value(TestSchema.Site.name, "B").value(TestSchema.Site.group_id, 1L)
		)), "SQL が違うのに通っている");

		assertEquals("DB_998", e.getCode(), e.getMessage());

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
				.where(TestSchema.Site.group_id.eq(2L))).orElseThrow();

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
			SQL.select().from(TestSchema.Site.instance()).where(TestSchema.Site.id.eq(id))).orElseThrow();

		assertEquals("俺的まとめ速報＠あ｜ア", row.getString(TestSchema.Site.name));

	}

}
