package io.jimble.db;

import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空の DB に何台かが同時に初めて起動しても、jimble の管理の表を取り合わない
 *
 * <p>
 * 管理の表（{@code db_lock}・{@code db_value}・セッションなど）は、起動時に読んだ表の一覧を見て、
 * 無ければ {@link DBVersion#apply} が作る。<b>何台かが同時に初めて起動すると、どの台も「無い」と読んである</b>ので、
 * 2 台目からの {@code CREATE TABLE} が「もうある」で失敗し、起動しなかった。
 * </p>
 *
 * <p>
 * ほかの台が jimble として作った表（コメントが「説明:版」）なら飛ばして続ける。
 * <b>アプリが同じ名前の表を持っている</b>（コメントが違う・無い）なら、これまでどおり止める。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class DbVersionConcurrentCreateIntegrationTest {

	/* テスト用の表 */
	private static final String TABLE = "version_race_probe";

	/* 表のコメント */
	private static final String COMMENT = "同時に作るテスト";

	/* 同時に起動する台の数 */
	private static final int INSTANCES = 6;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), DbVersionConcurrentCreateIntegrationTest.class);

	}

	@AfterAll
	static void stopDataSource () {

		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS " + TABLE);
			DBVersion.reload(db);
		}
		DBUtil.stop();

	}

	@BeforeEach
	void dropTable () {

		// どの台も「無い」と読んである状態にする
		try (DB db = DBUtil.getMainDB()) {
			db.execute("DROP TABLE IF EXISTS " + TABLE);
			DBVersion.reload(db);
		}

	}

	@Test
	@DisplayName("読んだあとにほかの台が作っていても、「もうある」で止めずに続ける")
	void createdByAnotherInstance () {

		// ほかの台が先に作った（この台の一覧には無いまま）
		try (DB db = DBUtil.getMainDB()) {
			createTable(db, COMMENT + ":1");
		}

		try (DB db = DBUtil.getMainDB()) {
			assertTrue(definition().apply(db));
			assertEquals(COMMENT + ":1", comment(db));
		}

	}

	@Test
	@DisplayName("何台かが同時に作っても、どの台も起動に失敗しない")
	void createdAtTheSameTime () throws Exception {

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);

		try {

			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < INSTANCES; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try (DB db = DBUtil.getMainDB()) {
						return definition().apply(db);
					}
				}));
			}

			start.countDown();

			for (Future<Boolean> result : results) {
				assertTrue(result.get(30, TimeUnit.SECONDS));     // 失敗した台があれば、ここで例外になる
			}

		} finally {
			pool.shutdownNow();
		}

		try (DB db = DBUtil.getMainDB()) {
			assertEquals(COMMENT + ":1", comment(db));
		}

	}

	@Test
	@DisplayName("アプリが同じ名前の表を持っていたら（コメントが違う）、これまでどおり止める")
	void foreignTableStillFails () {

		try (DB db = DBUtil.getMainDB()) {
			createTable(db, "アプリの表");
		}

		try (DB db = DBUtil.getMainDB()) {
			assertThrows(SqlExecuteException.class, () -> definition().apply(db));
		}

	}

	@Test
	@DisplayName("アプリが同じ名前の表を持っていたら（コメントが無い）、これまでどおり止める")
	void uncommentedTableStillFails () {

		try (DB db = DBUtil.getMainDB()) {
			db.execute("create table %s (id bigint not null primary key)".formatted(TABLE));
		}

		try (DB db = DBUtil.getMainDB()) {
			assertThrows(SqlExecuteException.class, () -> definition().apply(db));
		}

	}

	// region ヘルパー

	/**
	 * 版 1 で表を作る定義（jimble の管理の表と同じ書き方）
	 *
	 * @return	定義
	 */
	private static DBVersion definition () {

		DBVersion version = new DBVersion(TABLE, COMMENT);

		version.add(1)
			.mysql("""
				create table %s (
					id bigint not null primary key
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(TABLE, version.placeholder()))
			.postgresql("create table %s (id bigint not null primary key)".formatted(TABLE));

		return version;

	}

	/**
	 * 表を外から作る
	 *
	 * @param db		DB
	 * @param comment	表のコメント
	 */
	private static void createTable (DB db, String comment) {

		db.execute("create table %s (id bigint not null primary key)".formatted(TABLE));
		db.execute(db.dialect().setTableCommentSql(TABLE, comment));

	}

	/**
	 * 表のコメント
	 *
	 * @param db	DB
	 * @return	コメント
	 */
	private static String comment (DB db) {

		return db.selectList(db.dialect().tableCommentsSql()).stream()
			.filter(row -> TABLE.equalsIgnoreCase(row.getString("table_name")))
			.map(row -> row.getStringOptional("table_comment"))
			.findFirst().orElseThrow();

	}

	// endregion

}
