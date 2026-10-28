package io.jimble.db.migration;

import com.typesafe.config.ConfigFactory;

import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * マイグレーションを何台かが同時に当てる（要件 F-G-15）
 *
 * <p>
 * ロックは 1 本目の接続の Tx で {@code db_lock} の行を取り、DDL は 2 本目の接続で流す。
 * <b>MySQL・MariaDB の DDL の暗黙コミットは 2 本目にしか効かない</b>ので、ロックは commit まで外れない。
 * それを約束にするテストと、接続のまわりで抜けていたところのテストである。
 * </p>
 *
 * <ul>
 * <li>待ちの上限（{@code migration.lock_timeout}）を入れた接続を、<b>そのままプールに返していた</b>（MySQL・MariaDB）</li>
 * <li>どれを当てたかを<b>読み取り専用の接続（レプリカ）から読んでいた</b>。レプリカが遅れていると、
 *     先に当てた台の記録が見えず、同じファイルをもう一度流す</li>
 * <li>プールが 1 本だと、2 本目の接続を待ち続けて {@code connection_timeout} で落ちていた</li>
 * </ul>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class MigrationConcurrencyIntegrationTest {

	/** 当てるファイル */
	private static final String FILE = "test_concurrent.sql";

	/** 同時に当てる台の数 */
	private static final int INSTANCES = 3;

	/** SQL ファイルの置き場 */
	@TempDir
	Path sqlDir;

	/** 待ちの上限（既定の 60 秒・MySQL の既定の 50 秒と見分けられる値） */
	private static final int LOCK_TIMEOUT_SECONDS = 7;

	@AfterAll
	static void stop () {

		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void load () {

		load("");

	}

	@AfterEach
	void clean () {

		DBUtil.stop();
		Conf.reload();
		DBUtil.load(Conf.conf().config(), MigrationConcurrencyIntegrationTest.class);

		try (DB db = DBUtil.getMainDB()) {
			dropTables(db);
		}

	}

	@Test
	@DisplayName("何台かが同時に当てても、DDL を含むファイルは1回だけ当たる")
	void appliedOnceAtTheSameTime () throws Exception {

		DBSource source = DBUtil.getMainDataSource();
		List<MigrationInfo> files = files(sleepSql(source));

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);

		try {

			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < INSTANCES; i++) {
				results.add(pool.submit(() -> {
					start.await();
					Migration.migrate(source, files);
					return null;
				}));
			}

			start.countDown();

			for (Future<?> result : results) {
				result.get(60, TimeUnit.SECONDS);     // 失敗した台があれば、ここで例外になる
			}

		} finally {
			pool.shutdownNow();
		}

		try (DB db = DBUtil.getMainDB()) {
			assertEquals("complete", db.select("SELECT state FROM migration WHERE name = ?", FILE).orElseThrow().getString("state"));
			assertEquals(3, db.select("SELECT COUNT(*) AS cnt FROM migration_history WHERE name = ? AND kind = 'up'", FILE)
				.orElseThrow().getLong("cnt"), "up の文が1回ずつではない");
		}

	}

	@Test
	@DisplayName("待ちの上限を入れた接続を、上限を入れたままプールに返さない")
	void lockTimeoutIsNotLeftOnPooledConnections () {

		load("migration.lock_timeout = " + LOCK_TIMEOUT_SECONDS + "s");

		DBSource source = DBUtil.getMainDataSource();
		Migration.migrate(source, files(""));

		// プールの接続を全部借りて、1本も上限が残っていないことを見る
		List<DB> borrowed = new ArrayList<>();
		try {
			for (int i = 0; i < source.conf().maximumPoolSize(); i++) {
				DB db = DBUtil.getMainDB();
				db.begin();     // Tx の間は接続を返さない
				borrowed.add(db);
				assertNotEquals(LOCK_TIMEOUT_SECONDS * 1000L, lockTimeoutMillis(db)
					, "マイグレーションの待ちの上限が、プールの接続に残っている");
			}
		} finally {
			for (DB db : borrowed) {
				db.close();
			}
		}

	}

	@Test
	@DisplayName("どれを当てたかは書き込みの接続から読む（レプリカが遅れていても、もう一度流さない）")
	void readsStateFromWriteConnection () {

		// 読み取りの接続を、記録の無い別の DB に向ける（遅れたレプリカの代わり）
		load("db.jimble_test.read.url = ${db.jimble_test_sub.url}");

		try (DB other = DBUtil.getDB("jimble_test_sub")) {
			dropTables(other);
			if (hasTable(other, "migration")) {
				other.execute("DELETE FROM migration WHERE name = ?", FILE);
			}
		}

		DBSource source = DBUtil.getMainDataSource();

		Migration.migrate(source, files(""));
		Migration.migrate(source, files(""));     // 読み取りの接続を見ていれば、ここで同じ DDL を流して落ちる

		try (DB db = DBUtil.getMainDB().setUseWriteConnectionOnly()) {
			assertEquals(3, db.select("SELECT COUNT(*) AS cnt FROM migration_history WHERE name = ? AND kind = 'up'", FILE)
				.orElseThrow().getLong("cnt"), "同じファイルをもう一度流した");
		}

	}

	@Test
	@DisplayName("プールが1本なら、接続を待ち続けずに、直し方の分かる例外で止める")
	void poolOfOneFailsClearly () {

		load("""
			db.jimble_test.maximum_pool_size = 1
			db.jimble_test.connection_timeout = 30s
			""");

		DBSource source = DBUtil.getMainDataSource();

		MigrationException thrown = assertTimeoutPreemptively(Duration.ofSeconds(10)
			, () -> assertThrows(MigrationException.class, () -> Migration.migrate(source, files(""))));

		assertTrue(thrown.getMessage().contains("maximum_pool_size"), thrown.getMessage());

	}

	// region ヘルパー

	/**
	 * 設定を足して読み込み直す
	 *
	 * @param hocon	足す設定
	 */
	private static void load (String hocon) {

		DBUtil.stop();
		Conf.reload();
		if (!hocon.isEmpty()) {
			Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()).resolve());
		}
		DBUtil.load(Conf.conf().config(), MigrationConcurrencyIntegrationTest.class);

		try (DB db = DBUtil.getMainDB()) {
			dropTables(db);
			if (hasTable(db, "migration")) {
				db.execute("DELETE FROM migration WHERE name = ?", FILE);
				db.execute("DELETE FROM migration_history WHERE name = ?", FILE);
			}
		}

	}

	/**
	 * 当てるファイル（DDL を 2 つと、間に置く SQL）
	 *
	 * @param between	2つの DDL の間に流す SQL（空なら置かない）
	 * @return	ファイル
	 */
	private List<MigrationInfo> files (String between) {

		String sql = """
			# --- !Ups
			create table migration_conc_a (id int primary key);
			%s
			create table migration_conc_b (id int primary key);
			# --- !Downs
			drop table migration_conc_b;
			drop table migration_conc_a;
			""".formatted(between.isEmpty() ? "select 1;" : between);

		try {
			Path file = sqlDir.resolve(FILE);
			Files.writeString(file, sql, StandardCharsets.UTF_8);
			return List.of(MigrationInfo.ofFile(FILE, file.toFile()));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

	}

	/**
	 * 2つの DDL の間で少し待つ SQL（ほかの台がロックを待つあいだに、DDL の暗黙コミットが起きるように）
	 *
	 * @param source	データソース
	 * @return	SQL
	 */
	private static String sleepSql (DBSource source) {

		try (DB db = DBUtil.getDB(source.name())) {
			return db.dialect() instanceof PostgreSqlDialect ? "select pg_sleep(1);" : "select sleep(1);";
		}

	}

	/**
	 * いまの接続の待ちの上限（ミリ秒）
	 *
	 * @param db	DB（Tx の中）
	 * @return	ミリ秒
	 */
	private static long lockTimeoutMillis (DB db) {

		if (db.dialect() instanceof PostgreSqlDialect) {
			// 単位付きの文字列（"0" や "7s"）で返るので、ミリ秒にそろえる
			return db.select("SELECT setting::bigint AS v FROM pg_settings WHERE name = 'lock_timeout'")
				.orElseThrow().getLong("v");
		}

		return db.select("SELECT @@SESSION.innodb_lock_wait_timeout AS v").orElseThrow().getLong("v") * 1000L;

	}

	private static void dropTables (DB db) {

		db.execute("DROP TABLE IF EXISTS migration_conc_b");
		db.execute("DROP TABLE IF EXISTS migration_conc_a");

	}

	private static boolean hasTable (DB db, String table) {

		return db.select("""
			SELECT COUNT(*) AS cnt FROM information_schema.tables
			WHERE table_schema = %s AND table_name = ?
			""".formatted(TestDdl.currentSchema(db)), table).orElseThrow().getLong("cnt") > 0;

	}

	// endregion

}
