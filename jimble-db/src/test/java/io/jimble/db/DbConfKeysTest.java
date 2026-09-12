package io.jimble.db;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code db.<名前>} の知らないキーを落とすか（要件 D-159）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>21 個のキーを1つずつ {@code hasPath} で拾っていた。</b>
 * だから<b>綴りを間違えたキーは何も言わずに無視された</b>——
 * {@code maximum_pool_size} を打ち間違えたアプリは、<b>プールが既定のまま</b>である。
 * </p>
 *
 * <p>
 * <b>動くには動く。</b>気づくのは、負荷が上がって接続が足りなくなったときで、
 * そのときに疑うのは設定ファイルではない。
 * </p>
 */
class DbConfKeysTest {

	@Test
	@DisplayName("D-159 知らないキーは起動時に落とす")
	void unknownKeyFails () {

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> DBUtil.checkKeys(ConfigFactory.parseString("""
				url = "jdbc:x"
				maximumPoolSizzze = 4
				"""))
			, "打ち間違いを黙って無視しています");

		assertTrue(thrown.getMessage().contains("maximumPoolSizzze"), thrown.getMessage());

		// 直し方（書けるキーの一覧）も出す
		assertTrue(thrown.getMessage().contains("maximum_pool_size"), thrown.getMessage());

	}

	@Test
	@DisplayName("D-159 snake_case は通る")
	void snakeCaseIsFine () {

		DBUtil.checkKeys(ConfigFactory.parseString("""
			driver = "x"
			url = "jdbc:x"
			username = "u"
			password = "p"
			product = "postgresql"
			schema = "s"
			maximum_pool_size = 4
			minimum_idle = 1
			fetch_size = 100
			idle_timeout = 5m
			max_lifetime = 9m
			connection_timeout = 3s
			keepalive_time = 30s
			connection_init_sql = "SET x"
			connection_test_query = "SELECT 1"
			connection_pool_type = "hikari"
			transaction_isolation = "TRANSACTION_READ_COMMITTED"
			create_database_sql = "CREATE DATABASE x"
			long_connection_log = true
			long_connection_time = 1s
			"""));

	}

	@Test
	@DisplayName("camelCase の古い綴りも、まだ通る（警告は出る）")
	void oldSpellingsStillRead () {

		DBUtil.checkKeys(ConfigFactory.parseString("""
			maximumPoolSize = 4
			idleTimeout = 300000
			scheme = "s"
			"""));

	}

	@Test
	@DisplayName("入れ子（main / read / subs）は値ではない")
	void nestedKeysAreAllowed () {

		DBUtil.checkKeys(ConfigFactory.parseString("""
			main = true
			url = "jdbc:x"
			read { url = "jdbc:y" }
			subs { one { url = "jdbc:z" } }
			"""));

	}

	// region ここで固定していないこと

	/*
	 * - <b>古い綴りの警告が実際に出るところ</b>は見ていない。ログの出力先を差し替えれば見られるが、
	 *   ここで守りたいのは<b>「読める」ことと「知らないキーで落ちる」こと</b>である
	 * - <b>値の型</b>も見ていない。{@code maximum_pool_size = "abc"} は
	 *   typesafe config 側が落とす
	 */

	// endregion


	@Test
	@DisplayName("D-159 DBUtil.load からも落ちる（繋ぎにいく前に見る）")
	void loadFailsBeforeConnecting () {

		/*
		 * <b>読みながら見ると、投げた例外が「接続に失敗しました」に化ける。</b>
		 * 下の catch は「そんなデータベースは無い」を拾う場所なので、
		 * <b>打ち間違いがそこへ落ちると、原因が設定だと分からなくなる</b>。
		 *
		 * URL はでたらめでよい——<b>繋ぎにいく前に落ちる</b>のがここで見たいことである。
		 */
		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, () -> DBUtil.load(ConfigFactory.parseString("""
				db {
					main {
						url = "jdbc:mysql://127.0.0.1:1/nothing"
						maximumPoolSizzze = 4
					}
				}
				"""), DbConfKeysTest.class)
			, "打ち間違いを黙って無視しています");

		assertTrue(thrown.getMessage().contains("maximumPoolSizzze"), thrown.getMessage());

	}

	@Test
	@DisplayName("D-159 read / subs の中も見る")
	void nestedBlocksAreChecked () {

		assertThrows(IllegalStateException.class
			, () -> DBUtil.load(ConfigFactory.parseString("""
				db {
					main {
						url = "jdbc:mysql://127.0.0.1:1/nothing"
						subs { one { url = "jdbc:x", fetchSizzze = 1 } }
					}
				}
				"""), DbConfKeysTest.class)
			, "subs の中を見ていません");

	}

}
