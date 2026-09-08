package io.jimble.db.migration;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * マイグレーションが実 DB に対して動くことの確認
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。実行は次のとおり。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 */
@Tag("db")
class MigrationIntegrationTest {

	/** テスト用の SQL ファイル置き場（テストごとに作り直す） */
	@TempDir
	Path sqlDir;

	// region 準備

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), MigrationIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS migration_test_a");
		db.execute("DROP TABLE IF EXISTS migration_test_b");
		db.execute("DELETE FROM migration WHERE name LIKE 'test_%'");
		db.execute("DELETE FROM migration_history WHERE name LIKE 'test_%'");

		// 既定に戻す（テストごとに設定を書き換えるため）
		conf("");

	}

	// endregion

	// region テスト

	@Test
	@DisplayName("未適用の SQL が名前順に適用される")
	void applyNew () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		write("test_10.sql", """
			# --- !Ups
			create table migration_test_b (id int primary key);
			# --- !Downs
			drop table migration_test_b;
			""");
		// 名前順が文字列順なら test_10 が test_2 より先になる。自然順であることを確かめる
		write("test_2.sql", """
			# --- !Ups
			alter table migration_test_a add column name varchar(10) null;
			# --- !Downs
			alter table migration_test_a drop column name;
			""");

		migrate();

		assertTrue(hasTable("migration_test_a"));
		assertTrue(hasTable("migration_test_b"));
		assertTrue(hasColumn("migration_test_a", "name"), "test_2 が test_10 より先に適用されていない");

		assertEquals("complete", state("test_1.sql"));
		assertEquals("complete", state("test_2.sql"));
		assertEquals("complete", state("test_10.sql"));

	}

	@Test
	@DisplayName("二度目の適用では何も実行しない")
	void applyTwice () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();
		long first = historyCount();

		migrate();

		assertEquals(first, historyCount(), "同じ SQL が二度実行されている");

	}

	@Test
	@DisplayName("up が失敗したら例外になり、状態が up_error になる")
	void upError () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			this is not sql;
			# --- !Downs
			drop table migration_test_a;
			""");

		assertThrows(MigrationException.class, this::migrate);

		assertEquals("up_error", state("test_1.sql"));
		assertNotNull(errorInfo("test_1.sql"));

	}

	@Test
	@DisplayName("up_error は次の実行で down → up をやり直す")
	void retryAfterUpError () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			this is not sql;
			# --- !Downs
			drop table if exists migration_test_a;
			""");

		assertThrows(MigrationException.class, this::migrate);
		assertEquals("up_error", state("test_1.sql"));

		// SQL を直す
		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table if exists migration_test_a;
			""");

		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertTrue(hasTable("migration_test_a"));

	}

	@Test
	@DisplayName("適用済みの SQL が書き換わっていたら、down が無効なら失敗する（F-G-10）")
	void modifiedWithoutDown () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();

		// 適用済みの SQL を書き換える
		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key, name varchar(10) null);
			# --- !Downs
			drop table migration_test_a;
			""");

		MigrationException ex = assertThrows(MigrationException.class, this::migrate);
		assertTrue(ex.getMessage().contains("書き換え"), ex.getMessage());

		// 状態は complete のまま（勝手に壊さない）
		assertEquals("complete", state("test_1.sql"));

	}

	@Test
	@DisplayName("down を有効にすると、書き換わった SQL は down → up でやり直す（D-2）")
	void modifiedWithDown () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();
		assertFalse(hasColumn("migration_test_a", "name"));

		conf("migration.down = true");

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key, name varchar(10) null);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertTrue(hasColumn("migration_test_a", "name"));

	}

	@Test
	@DisplayName("SQL ファイルが消えても、down が無効なら適用済みのまま残す")
	void removedWithoutDown () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();

		delete("test_1.sql");
		migrate();

		assertEquals("complete", state("test_1.sql"), "down が無効なのに履歴が消えている");
		assertTrue(hasTable("migration_test_a"), "down が無効なのにテーブルが消えている");

	}

	@Test
	@DisplayName("down を有効にすると、消えた SQL ファイルの down を実行して履歴も消す")
	void removedWithDown () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();

		conf("migration.down = true");
		delete("test_1.sql");
		migrate();

		assertNull(state("test_1.sql"), "履歴が残っている");
		assertFalse(hasTable("migration_test_a"), "down が実行されていない");

	}

	@Test
	@DisplayName("マイグレーション履歴に1文ずつ残る")
	void history () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			create table migration_test_b (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			drop table migration_test_b;
			""");

		migrate();

		List<Data> rows = DBUtil.getMainDB().selectList(
			"SELECT * FROM migration_history WHERE name = ? ORDER BY id", "test_1.sql");

		assertEquals(2, rows.size());
		assertEquals("up", rows.get(0).getString("kind"));
		assertEquals("complete", rows.get(0).getString("state"));
		assertTrue(rows.get(0).getString("sql_text").contains("migration_test_a"));

	}

	@Test
	@DisplayName("down のない SQL でも適用できる（警告のみ。F-G-12）")
	void withoutDown () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			""");

		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertEquals("", DBUtil.getMainDB()
			.select("SELECT down FROM migration WHERE name = ?", "test_1.sql").getString("down"));

	}

	// endregion

	// region ヘルパー

	/**
	 * マイグレーションを実行する
	 */
	private void migrate () {

		DBSource dbSource = DBUtil.getMainDataSource();

		List<MigrationInfo> list = new ArrayList<>();
		File[] files = sqlDir.toFile().listFiles();
		if (files != null) {
			for (File file : files) {
				MigrationInfo info = new MigrationInfo();
				info.sqlFile = file;
				info.sqlFileName = file.getName();
				list.add(info);
			}
		}

		Migration.migrate(dbSource, list);

	}

	/**
	 * SQL ファイルを書く
	 *
	 * @param name	ファイル名
	 * @param sql	SQL
	 */
	private void write (String name, String sql) {

		try {
			Files.writeString(sqlDir.resolve(name), sql, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * SQL ファイルを消す
	 *
	 * @param name	ファイル名
	 */
	private void delete (String name) {

		try {
			Files.delete(sqlDir.resolve(name));
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	追加する設定（HOCON）
	 */
	private void conf (String hocon) {

		Conf.reload();
		if (!hocon.isEmpty()) {
			Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));
		}

	}

	/**
	 * 適用状態
	 *
	 * @param name	SQL ファイル名
	 * @return	状態（未適用なら null）
	 */
	private String state (String name) {

		Data row = DBUtil.getMainDB().select("SELECT state FROM migration WHERE name = ?", name);

		return row == null ? null : row.getString("state");

	}

	/**
	 * エラー情報
	 *
	 * @param name	SQL ファイル名
	 * @return	エラー情報
	 */
	private String errorInfo (String name) {

		Data row = DBUtil.getMainDB().select("SELECT error_info FROM migration WHERE name = ?", name);

		return row == null ? null : row.getString("error_info");

	}

	/**
	 * 履歴の件数
	 *
	 * @return	件数
	 */
	private long historyCount () {

		return DBUtil.getMainDB()
			.select("SELECT COUNT(*) AS cnt FROM migration_history WHERE name LIKE 'test_%'")
			.getLong("cnt");

	}

	/**
	 * テーブルがあるか
	 *
	 * @param table	テーブル名
	 * @return	ある場合 = true
	 */
	private boolean hasTable (String table) {

		// 「いまのスキーマ」の書き方が製品で違う（要件 F-D-30）
		return DBUtil.getMainDB().select("""
			SELECT COUNT(*) AS cnt FROM information_schema.tables
			WHERE table_schema = %s AND table_name = ?
			""".formatted(TestDdl.currentSchema(DBUtil.getMainDB())), table).getLong("cnt") > 0;

	}

	/**
	 * 列があるか
	 *
	 * @param table		テーブル名
	 * @param column	列名
	 * @return	ある場合 = true
	 */
	private boolean hasColumn (String table, String column) {

		return DBUtil.getMainDB().select("""
			SELECT COUNT(*) AS cnt FROM information_schema.columns
			WHERE table_schema = %s AND table_name = ? AND column_name = ?
			""".formatted(TestDdl.currentSchema(DBUtil.getMainDB())), table, column).getLong("cnt") > 0;

	}

	// endregion

}
