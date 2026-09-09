package io.jimble.db.migration;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.util.hash.Hash;
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

	@Test
	@DisplayName("ファイル名の接尾辞で、いまの製品のものだけ流れる（F-G-20）")
	void productSuffix () {

		String product = DBUtil.getMainDataSource().dialect().name();

		write("test_1.mysql.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		write("test_1.postgresql.sql", """
			# --- !Ups
			create table migration_test_b (id int primary key);
			# --- !Downs
			drop table migration_test_b;
			""");

		migrate();

		boolean isMySql = "mysql".equals(product);

		assertEquals(isMySql, hasTable("migration_test_a"), "mysql 向けの適用がおかしい: " + product);
		assertEquals(!isMySql, hasTable("migration_test_b"), "postgresql 向けの適用がおかしい: " + product);

		// 流さなかったほうは履歴にも残さない（残すと、その製品で流したように見える）
		assertEquals(isMySql ? "complete" : null, state("test_1.mysql.sql"));
		assertEquals(isMySql ? null : "complete", state("test_1.postgresql.sql"));

	}

	@Test
	@DisplayName("接尾辞が無ければどの製品でも流れる（F-G-20）")
	void withoutProductSuffix () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertTrue(hasTable("migration_test_a"));

	}

	@Test
	@DisplayName("製品名でない「.」は接尾辞として扱わない（F-G-20）")
	void dotIsNotAlwaysProduct () {

		// バージョンのつもりで「.」を使っただけのファイルを、黙って飛ばしてはいけない
		write("test_1.v2.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();

		assertEquals("complete", state("test_1.v2.sql"), "製品名でない接尾辞で飛ばされている");
		assertTrue(hasTable("migration_test_a"));

	}

	@Test
	@DisplayName("他の製品向けのファイルを「消えた」と見なして down しない（F-G-20）")
	void otherProductIsNotRemoved () {

		String product = DBUtil.getMainDataSource().dialect().name();
		boolean isMySql = "mysql".equals(product);

		// いまの製品のぶんだけ流す
		write("test_1.mysql.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		write("test_1.postgresql.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();
		assertTrue(hasTable("migration_test_a"));

		// down を有効にしても、もう片方の製品向けは「消えた」ではない
		conf("migration.down = true");
		migrate();

		assertTrue(hasTable("migration_test_a"), "他の製品向けのファイルを消えたと見て down している");
		assertEquals(isMySql ? "complete" : null, state("test_1.mysql.sql"));
		assertEquals(isMySql ? null : "complete", state("test_1.postgresql.sql"));

	}

	@Test
	@DisplayName("他の製品で流した履歴が残っていても down しない（F-G-20）")
	void otherProductHistoryIsNotRemoved () {

		String product = DBUtil.getMainDataSource().dialect().name();
		String other = "mysql".equals(product) ? "postgresql" : "mysql";

		write("test_1." + product + ".sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		write("test_1." + other + ".sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();
		assertTrue(hasTable("migration_test_a"));

		/*
		 * この DB が前は別の製品で使われていた状態を作る
		 * （論理ダンプの移し替え、product の書き換えなど）。
		 * 他の製品向けの履歴が残っている。
		 */
		DBUtil.getMainDB().insert(
			"INSERT INTO migration (name, hash, up, down, state, error_info) VALUES (?, ?, ?, ?, ?, ?)"
			, "test_1." + other + ".sql"
			, "0123456789abcdef0123456789abcdef"
			, "create table migration_test_a (id int primary key);"
			, "drop table migration_test_a;"
			, "complete"
			, null);

		conf("migration.down = true");

		migrate();

		assertTrue(hasTable("migration_test_a")
			, "他の製品の履歴を「消えた」と見て down している");
		assertEquals("complete", state("test_1." + other + ".sql"), "他の製品の履歴を消している");

	}

	@Test
	@DisplayName("接尾辞なしを製品別に分けたら、どちらの製品でも止めて直し方を出す（F-G-20）")
	void splitAppliedFile () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();
		assertEquals("complete", state("test_1.sql"));

		// 製品ごとに分ける。中身は製品で変わるので、ハッシュでは見分けられない
		delete("test_1.sql");
		write("test_1.mysql.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key) ENGINE=InnoDB;
			# --- !Downs
			drop table migration_test_a;
			""");
		write("test_1.postgresql.sql", """
			# --- !Ups
			create table migration_test_a (id bigserial primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		String product = DBUtil.getMainDataSource().dialect().name();

		MigrationException ex = assertThrows(MigrationException.class, this::migrate);

		assertTrue(ex.getMessage().contains("名前が変わった"), ex.getMessage());
		assertTrue(ex.getMessage().contains("test_1." + product + ".sql"), ex.getMessage());
		assertTrue(ex.getMessage().contains("UPDATE migration SET name"), ex.getMessage());

		// 止まったので、テーブルも履歴もそのまま
		assertTrue(hasTable("migration_test_a"));
		assertEquals("complete", state("test_1.sql"));

		// 出てきた SQL をそのまま流せば通る（中身も変わっているので hash も入っている）
		assertTrue(ex.getMessage().contains(", hash = "), ex.getMessage());
		runSqlFrom(ex.getMessage());

		migrate();

		assertEquals("complete", state("test_1." + product + ".sql"));
		assertTrue(hasTable("migration_test_a"));

	}

	@Test
	@DisplayName("いまの製品向けのファイルが無くなったら、履歴の消し方を出して止める（F-G-20）")
	void renamedToOtherProductOnly () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");
		migrate();

		// もう片方の製品向けの名前にだけ変えてしまった
		String other = "mysql".equals(DBUtil.getMainDataSource().dialect().name())
			? "postgresql"
			: "mysql";

		delete("test_1.sql");
		write("test_1." + other + ".sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		conf("migration.down = true");

		MigrationException ex = assertThrows(MigrationException.class, this::migrate);

		assertTrue(ex.getMessage().contains("他の製品向けの名前"), ex.getMessage());
		assertTrue(ex.getMessage().contains("DELETE FROM migration"), ex.getMessage());

		// down が有効でも消していない（黙って drop table しない）
		assertTrue(hasTable("migration_test_a"), "他の製品向けに変わっただけで down している");

	}

	@Test
	@DisplayName("up_error のまま名前を変えたら、止めずにやり直す（F-G-20）")
	void renamedWhileUpError () {

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			this is not sql;
			# --- !Downs
			drop table if exists migration_test_a;
			""");
		assertThrows(MigrationException.class, this::migrate);
		assertEquals("up_error", state("test_1.sql"));

		String product = DBUtil.getMainDataSource().dialect().name();

		delete("test_1.sql");
		write("test_1." + product + ".sql", """
			# --- !Ups
			create table if not exists migration_test_a (id int primary key);
			# --- !Downs
			drop table if exists migration_test_a;
			""");

		migrate();

		assertNull(state("test_1.sql"), "up_error の履歴が残っている");
		assertEquals("complete", state("test_1." + product + ".sql"));
		assertTrue(hasTable("migration_test_a"));

	}

	@Test
	@DisplayName("同じ版が、いまの製品で2つとも流れるなら止める（F-G-20）")
	void sameVersionTwice () {

		String product = DBUtil.getMainDataSource().dialect().name();
		String alias = "mysql".equals(product) ? "mariadb" : "postgres";

		write("test_1." + product + ".sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			""");
		write("test_1." + alias + ".sql", """
			# --- !Ups
			create table migration_test_b (id int primary key);
			""");

		MigrationException ex = assertThrows(MigrationException.class, this::migrate);

		assertTrue(ex.getMessage().contains("同じ版"), ex.getMessage());
		assertFalse(hasTable("migration_test_a"), "止める前に流している");

	}

	@Test
	@DisplayName("別名（mariadb）は mysql と同じものとして扱う（F-G-20）")
	void productAlias () {

		write("test_1.mariadb.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();

		boolean isMySql = "mysql".equals(DBUtil.getMainDataSource().dialect().name());

		assertEquals(isMySql, hasTable("migration_test_a"));
		assertEquals(isMySql ? "complete" : null, state("test_1.mariadb.sql"));

	}

	@Test
	@DisplayName("コメントの中のセミコロンでは1文に切れない（F-G-05）")
	void commentInSql () {

		write("test_1.sql", """
			# --- !Ups
			-- 作る; いれる
			create table migration_test_a (id int primary key, v varchar(50) null);
			/* ここにも ; がある */
			insert into migration_test_a (id, v) values (1, 'a;b');
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertTrue(hasTable("migration_test_a"));
		assertEquals("a;b", DBUtil.getMainDB()
			.select("SELECT v FROM migration_test_a WHERE id = 1").getString("v"));

		// コメントで切れていれば、実行は create と insert の2文になる
		assertEquals(2, upCount("test_1.sql"), "1文ずつに切れていない");

	}

	@Test
	@DisplayName("文字列の中のバックスラッシュで、後ろの SQL を巻き込まない（F-G-05）")
	void backslashInLiteral () {

		// バックスラッシュ1つの書き方が製品で違う（MySQL はエスケープになる）
		String value = "mysql".equals(DBUtil.getMainDataSource().dialect().name())
			? "'c:\\\\'"
			: "'c:\\'";

		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id int primary key, v varchar(50) null);
			insert into migration_test_a (id, v) values (1, %s);
			insert into migration_test_a (id, v) values (2, 'x');
			# --- !Downs
			drop table migration_test_a;
			""".formatted(value));

		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertEquals(2, DBUtil.getMainDB()
			.select("SELECT COUNT(*) AS cnt FROM migration_test_a").getLong("cnt")
			, "バックスラッシュのせいで後ろの insert が巻き込まれている");
		assertEquals("c:\\", DBUtil.getMainDB()
			.select("SELECT v FROM migration_test_a WHERE id = 1").getString("v"));

		/*
		 * 件数だけでは足りない。PostgreSQL の JDBC は「;」で区切った複数文を
		 * 1回の execute で通してしまうので、切り分けを間違えても入るものは入る。
		 * 実行が3文に分かれたことまで見る
		 */
		assertEquals(3, upCount("test_1.sql"), "1文ずつに切れていない");

	}

	@Test
	@DisplayName("MySQL の # 行コメントも1文に切れない（F-G-05）")
	void hashCommentInSql () {

		if (!"mysql".equals(DBUtil.getMainDataSource().dialect().name())) {
			// # は MySQL だけのコメント
			return;
		}

		write("test_1.sql", """
			# --- !Ups
			# 作る; いれる
			create table migration_test_a (id int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();

		assertEquals("complete", state("test_1.sql"));
		assertEquals(1, upCount("test_1.sql"), "1文ずつに切れていない");

	}

	@Test
	@DisplayName("全部コメントで1文も取り出せなければ失敗にする（F-G-05）")
	void nothingToExecute () {

		write("test_1.sql", """
			# --- !Ups
			-- あとで書く
			""");

		MigrationException ex = assertThrows(MigrationException.class, this::migrate);

		assertTrue(ex.getMessage().contains("実行できる SQL がありません"), ex.getMessage());
		assertEquals("up_error", state("test_1.sql"), "何もしていないのに complete になっている");

	}

	@Test
	@DisplayName("ハッシュの取り方は変えない（F-G-10）")
	void hashIsStable () {

		// 空白を2つ続ける。ここが1つに潰れるようだと、潰し方が変わっている
		write("test_1.sql", """
			# --- !Ups
			create table migration_test_a (id  int primary key);
			# --- !Downs
			drop table migration_test_a;
			""");

		migrate();

		/*
		 * <b>タブと改行を空白1つに潰して trim したもの</b>のハッシュである。
		 * ここを変えると、適用済みのマイグレーションが全部
		 * 「書き換えられました」になって起動しなくなる
		 */
		String expected = Hash.md5(
			"# --- !Ups create table migration_test_a (id  int primary key);"
			+ " # --- !Downs drop table migration_test_a;");

		assertEquals(expected
			, DBUtil.getMainDB()
				.select("SELECT hash FROM migration WHERE name = ?", "test_1.sql").getString("hash")
			, "ハッシュの取り方が変わっている");

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
	 * エラーメッセージに出てきた UPDATE / DELETE をそのまま流す
	 *
	 * <p>
	 * <b>出した直し方が本当に直すこと</b>を確かめるため、手で書き写さずに流す。
	 * </p>
	 *
	 * @param message	エラーメッセージ
	 */
	private void runSqlFrom (String message) {

		DB db = DBUtil.getMainDB();
		int count = 0;

		for (String line : message.split("\\R")) {

			String sql = line.strip();

			if (!sql.startsWith("UPDATE ") && !sql.startsWith("DELETE ")) {
				continue;
			}

			db.execute(sql);
			assertFalse(db.isError(), sql + " / " + (db.isError() ? db.getError().getMessage() : ""));
			count++;

		}

		assertTrue(count > 0, "直し方の SQL が出ていません: " + message);

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
	 * up として実行された文の数
	 *
	 * @param name	SQL ファイル名
	 * @return	件数
	 */
	private long upCount (String name) {

		return DBUtil.getMainDB()
			.select("SELECT COUNT(*) AS cnt FROM migration_history WHERE name = ? AND kind = 'up'", name)
			.getLong("cnt");

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
