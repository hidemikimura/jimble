package io.jimble.db;

import io.jimble.core.context.BatchContext;
import io.jimble.core.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.jimble.util.conf.Conf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 畳み忘れたトランザクションの後始末（要件 F-D-16）
 *
 * <p>
 * 要件 F-D-16 は MUST だが、<b>実装が入っていなかった。</b>
 * {@code Context} の javadoc には「未コミットトランザクションのロールバック」と
 * 書いてあったが、それを行うコードがどこにも無い。
 * try-with-resources を使っているかぎり {@code DBTransaction.close()} が拾うので、
 * <b>使わずに途中で return したときだけ漏れる。</b>
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class TransactionCloseTest {

	@Test
	@DisplayName("畳み忘れたトランザクションは実行の終わりに戻る")
	void rollbackOnContextClose () throws Exception {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), TransactionCloseTest.class));

		try (DB setup = DBUtil.getMainDB()) {
			TestDdl.execute(setup, """
				CREATE TABLE IF NOT EXISTS tx_leak (
					id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(100) NOT NULL
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");
			setup.execute("TRUNCATE TABLE tx_leak");
		}

		DB db = DBUtil.getMainDB();

		/*
		 * try-with-resources を使わずに始めて、そのまま実行を終える。
		 * 移送元でも jimble でも、これは黙って漏れていた。
		 */
		try (BatchContext context = new BatchContext("tx-leak-test")) {

			context.run(() -> {

				try {
					DBTransaction transaction = new DBTransaction(db);
					transaction.beginTransaction();
					db.insert("INSERT INTO tx_leak (name) VALUES (?)", "漏れた行");
					// commit も rollback も close もしない
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

			});

		}

		// 実行が終わった時点で戻っている
		try (DB check = DBUtil.getMainDB()) {
			assertNull(check.select("SELECT id FROM tx_leak WHERE name = ?", "漏れた行")
				, "畳み忘れたトランザクションが戻っていない");
		}

		DBUtil.stop();

	}

	@Test
	@DisplayName("ちゃんと閉じたものは実行の終わりに触られない")
	void committedIsLeftAlone () throws Exception {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), TransactionCloseTest.class));

		try (DB setup = DBUtil.getMainDB()) {
			TestDdl.execute(setup, """
				CREATE TABLE IF NOT EXISTS tx_leak (
					id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(100) NOT NULL
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");
			setup.execute("TRUNCATE TABLE tx_leak");
		}

		try (BatchContext context = new BatchContext("tx-ok-test")) {

			context.run(() -> {

				try (DB db = DBUtil.getMainDB();
					 DBTransaction transaction = new DBTransaction(db)) {

					transaction.beginTransaction();
					db.insert("INSERT INTO tx_leak (name) VALUES (?)", "残る行");
					transaction.commitEndTransaction();

				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

			});

		}

		try (DB check = DBUtil.getMainDB()) {
			assertNotNull(check.select("SELECT id FROM tx_leak WHERE name = ?", "残る行")
				, "コミットしたのに戻されている");
		}

		DBUtil.stop();

	}

	@Test
	@DisplayName("commit() はトランザクションを終わらせない")
	void commitDoesNotEnd () throws Exception {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), TransactionCloseTest.class));

		try (DB setup = DBUtil.getMainDB()) {
			TestDdl.execute(setup, """
				CREATE TABLE IF NOT EXISTS tx_leak (
					id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(100) NOT NULL
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");
			setup.execute("TRUNCATE TABLE tx_leak");
		}

		/*
		 * 移送元の commit() は中で commitEndTransaction() を呼んでいたので、
		 * ここでトランザクションが終わっていた。
		 * 「途中まで確定させて続ける」つもりの2件目が
		 * 自動コミットになり、ロールバックしても残る。
		 */
		try (DB db = DBUtil.getMainDB();
			 DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			db.insert("INSERT INTO tx_leak (name) VALUES (?)", "1件目");
			transaction.commit();

			assertTrue(db.isTransaction(), "commit() でトランザクションが終わっている");

			db.insert("INSERT INTO tx_leak (name) VALUES (?)", "2件目");
			transaction.rollbackEndTransaction();

		}

		try (DB check = DBUtil.getMainDB()) {
			assertEquals(1, check.select("SELECT COUNT(*) AS cnt FROM tx_leak").getInt("cnt")
				, "commit() のあとがトランザクションの外になっている");
		}

		DBUtil.stop();

	}

}
