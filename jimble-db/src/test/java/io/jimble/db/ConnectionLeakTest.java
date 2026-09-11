package io.jimble.db;

import com.zaxxer.hikari.HikariDataSource;

import io.jimble.core.context.BatchContext;
import io.jimble.core.context.Context;
import io.jimble.db.data.ResultSetFetcher;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 閉じ忘れた DB コネクションを実行の終わりに拾う（要件 F-D-16）
 *
 * <h2>ここで見ているもの</h2>
 * <p>
 * <b>「閉じたかどうか」ではなく「プールに戻ったかどうか」を見る。</b>
 * {@code DB} を閉じ忘れても、<b>ふつうはコネクションが残らない</b>——
 * {@code closeAfterQuery()} が1文ごとに返しているためである。
 * だから「閉じ忘れ＝漏れ」ではない。漏れるのは<b>握ったまま抜ける道</b>だけで、
 * それは2つしかない。
 * </p>
 *
 * <ol>
 *   <li>トランザクション中（{@code closeAfterQuery()} が返さない）</li>
 *   <li>カーソル（{@code selectListWithFetcher}。読み終わるまで開けておく）</li>
 * </ol>
 *
 * <p>
 * 使用中の本数は Hikari に聞く。<b>件数や例外では見ない</b>——
 * 漏れているときも SQL は成功するので、
 * <b>プールの数字以外はどれも「うまくいった」と言う。</b>
 * </p>
 *
 * <p>開発用 DB が要る（要件 D-16）。</p>
 */
@Tag("db")
class ConnectionLeakTest {

	@BeforeEach
	void setUp () throws Exception {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), ConnectionLeakTest.class), "DB に接続できませんでした");

		try (DB setup = DBUtil.getMainDB()) {

			TestDdl.execute(setup, """
				CREATE TABLE IF NOT EXISTS conn_leak (
					id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(100) NOT NULL
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

			setup.execute("TRUNCATE TABLE conn_leak");

			setup.execute("INSERT INTO conn_leak (name) VALUES ('1件目'), ('2件目'), ('3件目')");

		}

	}

	@AfterEach
	void tearDown () {

		DBUtil.stop();

	}

	// region 握ったまま抜ける2つの道

	@Test
	@DisplayName("F-D-16 DB に直接始めたトランザクションも、実行の終わりに戻る")
	void rawBeginTransactionIsRolledBack () throws Exception {

		int before = activeConnections();

		DB db = DBUtil.getMainDB();

		try (BatchContext context = new BatchContext("raw-begin")) {

			context.run(() -> {

				try {

					/*
					 * <b>DBTransaction を通さない。</b>
					 * こちらの道は、拾う仕掛けが DBTransaction にあったころは
					 * <b>誰も見ていなかった。</b>
					 */
					db.beginTransaction();
					db.insert("INSERT INTO conn_leak (name) VALUES (?)", "戻る行");

					// commit も rollback も close もしない

				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

			});

		}

		assertEquals(before, activeConnections(), "コネクションがプールへ戻っていない");

		try (DB check = DBUtil.getMainDB()) {
			assertEquals(0, count(check, "戻る行"), "ロールバックされていない");
		}

	}

	@Test
	@DisplayName("F-D-16 1文も流さずに始めたトランザクションも戻る")
	void beginWithoutStatementIsClosed () throws Exception {

		int before = activeConnections();

		DB db = DBUtil.getMainDB();

		try (BatchContext context = new BatchContext("begin-only")) {

			context.run(() -> {

				try {
					/*
					 * <b>1文も流さない。</b>
					 * 登録を closeAfterQuery() の側だけに置くと、
					 * ここは一度も通らないので拾えない。
					 */
					db.beginTransaction();
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

			});

		}

		assertEquals(before, activeConnections(), "コネクションがプールへ戻っていない");

	}

	@Test
	@DisplayName("F-D-16 カーソルを開いたまま終わった DB も、実行の終わりに閉じる")
	void unclosedCursorIsClosed () throws Exception {

		int before = activeConnections();

		try (BatchContext context = new BatchContext("cursor")) {

			context.run(() -> {

				try (ResultSetFetcher fetcher = new ResultSetFetcher()) {

					/*
					 * <b>これが examples/approval-list と examples/blog が書いていた形である。</b>
					 * fetcher は閉じているが、<b>DB は誰も閉じていない。</b>
					 * カーソルはコネクションを握るので、CSV を出すたびに1本ずつ減っていた。
					 */
					DBUtil.getMainDB().selectListWithFetcher(fetcher
						, "SELECT id, name FROM conn_leak ORDER BY id");

					int rows = 0;
					for (Data row : fetcher) {
						assertNotNull(row);
						rows++;
					}

					assertEquals(3, rows, "カーソルが読めていない");

				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

			});

		}

		assertEquals(before, activeConnections(), "カーソルのコネクションがプールへ戻っていない");

	}

	// endregion

	// region 触ってはいけないもの

	@Test
	@DisplayName("F-D-16 普通の SQL は、閉じ忘れても最初から漏れていない")
	void plainStatementsDoNotLeak () throws Exception {

		int before = activeConnections();

		try (BatchContext context = new BatchContext("plain")) {

			context.run(() -> {

				/*
				 * <b>閉じない DB を 50 本作る。</b>
				 * 1文ごとに返しているので、これで増えてはいけない。
				 *
				 * <b>「作った DB を全部 Context に登録する」形にすると、
				 * ここに 50 個の後始末が積まれる。</b>
				 * 長いバッチのループなら際限なく増えるので、そうしていない。
				 */
				for (int i = 0; i < 50; i++) {
					assertNotNull(DBUtil.getMainDB().select("SELECT id FROM conn_leak ORDER BY id"));
				}

			});

		}

		assertEquals(before, activeConnections(), "1文ごとに返せていない");

	}

	@Test
	@DisplayName("F-D-16 畳んだものは後始末の列に残らない")
	void closedLeavesNothingPending () throws Exception {

		try (BatchContext context = new BatchContext("pending")) {

			context.run(() -> {

				try {

					// 普通の SQL（そもそも積まれない）
					for (int i = 0; i < 20; i++) {
						assertNotNull(DBUtil.getMainDB().select("SELECT id FROM conn_leak ORDER BY id"));
					}

					// 畳んだトランザクション（積まれて、外れる）
					for (int i = 0; i < 20; i++) {
						try (DB db = DBUtil.getMainDB();
							 DBTransaction transaction = new DBTransaction(db)) {
							transaction.beginTransaction();
							db.select("SELECT id FROM conn_leak ORDER BY id");
							transaction.commitEndTransaction();
						}
					}

					// 閉じたカーソル（積まれて、外れる）
					for (int i = 0; i < 20; i++) {
						try (DB db = DBUtil.getMainDB();
							 ResultSetFetcher fetcher = new ResultSetFetcher()) {
							db.selectListWithFetcher(fetcher, "SELECT id FROM conn_leak ORDER BY id");
							for (Data row : fetcher) {
								assertNotNull(row);
							}
						}
					}

				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

				/*
				 * <b>実行が終わる前に見る。</b>close() が走ると列は空になるので、
				 * 終わってから数えても always 0 になり、何も確かめたことにならない。
				 *
				 * <b>ここが 0 でないと、長いバッチで後始末が積み上がる。</b>
				 * 「登録するのは握ったものだけ」を選んだ意味が、この行に集まっている。
				 */
				assertEquals(0, Context.current().pendingCloseTaskCount()
					, "畳んだのに後始末が残っている");

			});

		}

	}

	@Test
	@DisplayName("F-D-16 畳んだトランザクションは実行の終わりに触られない")
	void committedIsLeftAlone () throws Exception {

		int before = activeConnections();

		try (BatchContext context = new BatchContext("committed")) {

			context.run(() -> {

				try (DB db = DBUtil.getMainDB();
					 DBTransaction transaction = new DBTransaction(db)) {

					transaction.beginTransaction();
					db.insert("INSERT INTO conn_leak (name) VALUES (?)", "残る行");
					transaction.commitEndTransaction();

				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}

			});

		}

		assertEquals(before, activeConnections(), "コネクションがプールへ戻っていない");

		try (DB check = DBUtil.getMainDB()) {
			assertEquals(1, count(check, "残る行"), "コミットしたのに戻されている");
		}

	}

	@Test
	@DisplayName("F-D-16 コンテキストの外で握っても落ちない")
	void outsideContextDoesNotThrow () throws Exception {

		/*
		 * 起動時のマイグレーションはコンテキストの外で走る。
		 * <b>拾う相手がいないので何もしない</b>——ここで例外にすると、
		 * 起動そのものが止まる。
		 */
		try (DB db = DBUtil.getMainDB()) {

			db.beginTransaction();
			db.insert("INSERT INTO conn_leak (name) VALUES (?)", "外の行");
			db.rollbackEndTransaction();

		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

		try (DB check = DBUtil.getMainDB()) {
			assertEquals(0, count(check, "外の行"), "ロールバックされていない");
		}

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>エラーログの文面は見ていない。</b>
	 *   Log を差し替える口が無いので、出ていることを機械的に確かめられない。
	 *   「戻っている」ことだけを見ている
	 * - <b>プールが枯れるところまでは見ていない。</b>
	 *   maximumPoolSize ぶん漏らして待たせる形になり、テストが時間に依存する
	 * - <b>Web の実行（WebContext）では見ていない。</b>
	 *   拾うのは Context の側なので、実行単位の種類では変わらない
	 */

	// endregion

	// region 道具

	/**
	 * いま使われているコネクションの本数
	 *
	 * @return	本数
	 */
	private static int activeConnections () {

		if (DBUtil.getMainDataSource().dataSource instanceof HikariDataSource hikari) {
			return hikari.getHikariPoolMXBean().getActiveConnections();
		}

		throw new AssertionError("Hikari ではないので本数が見られません");

	}

	/**
	 * 名前で数える
	 *
	 * @param db	DB
	 * @param name	名前
	 * @return	件数
	 */
	private static int count (DB db, String name) {

		return db.select("SELECT COUNT(*) AS cnt FROM conn_leak WHERE name = ?", name).getInt("cnt");

	}

	// endregion

}
