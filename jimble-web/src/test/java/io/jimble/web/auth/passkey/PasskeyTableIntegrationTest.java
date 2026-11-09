package io.jimble.web.auth.passkey;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * パスキーの表の版（版2の (realm, user_id) の索引）を確かめる
 *
 * <p>
 * 版1の表がある DB に当てて版2へ上がること、
 * rp を渡さない一覧・deleteAll の {@code realm = ? AND user_id = ?} が新しい索引を使うことを見る。
 * </p>
 */
@Tag("db")
class PasskeyTableIntegrationTest {

	private static final String TABLE = FrameworkTables.AUTH_PASSKEY;

	private static final String INDEX = TABLE + "__realm_user";

	private static final String COMMENT = "パスキー";

	/* 索引を選ばせるための行の数（人の数） */
	private static final int USERS = 2000;

	private static final String REALM = "admin";

	private static final long USER_ID = 1234;

	@BeforeAll
	static void start () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), PasskeyTableIntegrationTest.class);

	}

	@AfterAll
	static void stop () {

		// ほかのテストのために、今の版の空の表に戻す
		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS " + TABLE);
		DBVersion.reload(DBUtil.getMainDB());
		Passkey.resetInstalled();
		Passkey.list(REALM, USER_ID);

		DBUtil.stop();

	}

	@Test
	@DisplayName("版1の表がある DB でも、版2へ上がって (realm, user_id) の索引ができ、その問い合わせが新しい索引を使う")
	void upgradeAddsRealmUserIndex () {

		// Arrange：版1の表（索引は (realm, rp_id, user_id) だけ）
		DB db = DBUtil.getMainDB();
		createVersion1Table(db);
		assertTrue(!hasIndex(db, INDEX), "版1の表に、もう新しい索引がある");

		// Act：パスキーを使うと表を確かめて版を上げる
		Passkey.resetInstalled();
		assertEquals(0, Passkey.list(REALM, USER_ID).size());

		// Assert
		assertTrue(hasIndex(db, INDEX), "版2の索引ができていない");
		assertTrue(hasIndex(db, TABLE + "__user"), "版1の索引が消えた");
		assertTrue(comment(db).endsWith(":2"), "表の版が 2 になっていない：" + comment(db));

		fill(db);
		assertEquals(2, Passkey.list(REALM, USER_ID).size());

		String select = "SELECT credential_hash FROM " + TABLE + " WHERE realm = ? AND user_id = ? ORDER BY created_at, credential_hash";
		String delete = "DELETE FROM " + TABLE + " WHERE realm = ? AND user_id = ?";
		assertUsesIndex(db, select);
		assertUsesIndex(db, delete);

		assertEquals(2, Passkey.deleteAll(REALM, USER_ID));
		assertEquals(0, Passkey.list(REALM, USER_ID).size());

	}

	/**
	 * 版1の形で表を作る（コメントに版1を書く）
	 */
	private static void createVersion1Table (DB db) {

		db.execute("DROP TABLE IF EXISTS " + TABLE);
		db.execute("""
			create table %s
			(
				credential_hash  varchar(64)   not null primary key
				, realm            varchar(64)   not null
				, rp_id            varchar(253)  not null
				, user_id          bigint        not null
				, credential_id    varchar(1400) not null
				, user_handle      varchar(64)   not null
				, public_key       varchar(2048) not null
				, algorithm        int           not null
				, sign_count       bigint        not null
				, backup_eligible  smallint      not null
				, backed_up        smallint      not null
				, label            varchar(255)  not null
				, created_at       bigint        not null
				, last_used_at     bigint        not null
			)
			""".formatted(TABLE));
		db.execute("create index %s__user on %s (realm, rp_id, user_id)".formatted(TABLE, TABLE));
		db.execute(db.dialect().setTableCommentSql(TABLE, COMMENT + ":1"));

		// DBVersion は DB ごとに表のコメントを1回だけ読むので、外から作ったら読み直す
		DBVersion.reload(db);

	}

	/**
	 * 同じ種別に大勢の人を入れる（調べる人は2つ持つ）
	 */
	private static void fill (DB db) {

		String sql = ("INSERT INTO %s (credential_hash, realm, rp_id, user_id, credential_id, user_handle, public_key"
			+ ", algorithm, sign_count, backup_eligible, backed_up, label, created_at, last_used_at)"
			+ " VALUES (?, ?, ?, ?, 'c', 'h', 'k', -7, 0, 0, 0, '', ?, 0)").formatted(TABLE);

		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < USERS; i++) {
			rows.add(new Object[] {"h" + i, REALM, "rp" + (i % 3) + ".localhost", (long) i, (long) i});
		}
		rows.add(new Object[] {"h-extra", REALM, "rp9.localhost", USER_ID, 1L});

		db.transaction(tx -> {
			for (Object[] row : rows) {
				db.execute(sql, row);
			}
		});

		db.execute(isPostgreSql(db) ? "ANALYZE " + TABLE : "ANALYZE TABLE " + TABLE);

	}

	private static void assertUsesIndex (DB db, String sql) {

		String plan = explain(db, sql);
		assertTrue(plan.contains(INDEX), "新しい索引を使っていない：" + sql + "\n" + plan);

	}

	/**
	 * 実行計画を1つの文字列にする
	 *
	 * <p>
	 * PostgreSQL は行が少ないと表をなめる方を選ぶことがあるので、
	 * <b>このトランザクションの中だけ</b>表をなめるのを止め、どの索引を選ぶかを見る。
	 * </p>
	 */
	private static String explain (DB db, String sql) {

		return db.transactionResult(tx -> {
			if (isPostgreSql(db)) {
				db.execute("SET LOCAL enable_seqscan = off");
			}
			List<Data> rows = db.selectList("EXPLAIN " + sql, REALM, USER_ID);
			tx.rollback();
			// MySQL は選んだ索引が key の列、PostgreSQL は QUERY PLAN の行に書かれる
			String column = isPostgreSql(db) ? "QUERY PLAN" : "key";
			return rows.stream().map(row -> row.getStringOptional(column)).collect(Collectors.joining("\n"));
		});

	}

	private static boolean hasIndex (DB db, String index) {

		String sql = isPostgreSql(db)
			? "SELECT count(*) AS cnt FROM pg_indexes WHERE schemaname = current_schema() AND tablename = ? AND indexname = ?"
			: "SELECT count(*) AS cnt FROM information_schema.statistics WHERE table_schema = database() AND table_name = ? AND index_name = ?";

		return db.select(sql, TABLE, index).orElseThrow().getLong("cnt") > 0;

	}

	private static String comment (DB db) {

		return db.selectList(db.dialect().tableCommentsSql()).stream()
			.filter(row -> row.getString("table_name").equalsIgnoreCase(TABLE))
			.findFirst().orElseThrow()
			.getStringOptional("table_comment");

	}

	private static boolean isPostgreSql (DB db) {

		return db.dialect() instanceof PostgreSqlDialect;

	}

}
