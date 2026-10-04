package io.jimble.db.sqlcache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生 SQL の更新先が、キャッシュと関係ないテーブルだけか（D-273）
 *
 * <p>
 * <b>迷ったら false（全部消す）</b>。true にしてよいのは、確かに1つの届け出たテーブルだけを更新する形のときだけ。
 * </p>
 */
class SqlCacheTargetTest {

	@Test
	@DisplayName("D-273 jimble 自身のテーブルだけを更新する形は消さない（jimble が書く形）")
	void frameworkTables () {

		assertTrue(SqlCache.isUncachedTarget("""
			INSERT INTO `session` (session_id, data, created_at, last_accessed_at)
			VALUES (?, ?, NOW(), NOW()) ON DUPLICATE KEY UPDATE data = VALUES(data)
			"""));
		assertTrue(SqlCache.isUncachedTarget("UPDATE \"session\" SET last_accessed_at = NOW() WHERE session_id = ?"));
		assertTrue(SqlCache.isUncachedTarget("\n\t\tDELETE FROM rate_limit WHERE updated_at < ?"));
		assertTrue(SqlCache.isUncachedTarget("insert ignore into db_lock (lock_key) values (?)"));
		assertTrue(SqlCache.isUncachedTarget("REPLACE INTO `jimble`.`db_value` (k, v) VALUES (?, ?)"));
		assertTrue(SqlCache.isUncachedTarget("UPDATE auth_api_token t SET last_used_at = ? WHERE id = ?"));
		// INSERT ... SELECT の結合は読むだけ
		assertTrue(SqlCache.isUncachedTarget("INSERT INTO db_log (a) SELECT c.name FROM customer c JOIN shop s ON s.id = c.shop_id"));

	}

	@Test
	@DisplayName("D-273 届け出たテーブルも同じに扱う（設定で名前を変えたセッション、MQ のキュー）")
	void excludedTables () {

		assertFalse(SqlCache.isUncachedTarget("UPDATE my_sessions SET data = ? WHERE session_id = ?"));

		SqlCache.excludeTable("My_Sessions");

		assertTrue(SqlCache.isUncachedTarget("UPDATE my_sessions SET data = ? WHERE session_id = ?"));
		assertTrue(SqlCache.isUncachedTarget("UPDATE `MY_SESSIONS` SET data = ? WHERE session_id = ?"));

	}

	@Test
	@DisplayName("D-273 アプリのテーブル・読めない形・複数のテーブルを書き換えうる形は、これまでどおり全部消す")
	void everythingElseClears () {

		assertFalse(SqlCache.isUncachedTarget("UPDATE customer SET name = ? WHERE id = ?"));
		assertFalse(SqlCache.isUncachedTarget("UPDATE session_archive SET a = 1"));
		// 複文
		assertFalse(SqlCache.isUncachedTarget("UPDATE session SET a = 1; UPDATE customer SET name = 'x'"));
		// 複数のテーブル
		assertFalse(SqlCache.isUncachedTarget("UPDATE session, customer SET customer.name = 'x' WHERE customer.id = session.user_id"));
		assertFalse(SqlCache.isUncachedTarget("UPDATE session s, customer c SET c.name = 'x'"));
		assertFalse(SqlCache.isUncachedTarget("UPDATE session s JOIN customer c ON c.id = s.user_id SET c.name = 'x'"));
		assertFalse(SqlCache.isUncachedTarget("DELETE FROM session, customer USING session JOIN customer"));
		assertFalse(SqlCache.isUncachedTarget("DELETE s, c FROM session s JOIN customer c ON c.id = s.user_id"));
		// 読めない書き出し
		assertFalse(SqlCache.isUncachedTarget("WITH x AS (SELECT 1) UPDATE session SET a = 1"));
		assertFalse(SqlCache.isUncachedTarget("/* c */ UPDATE session SET a = 1"));
		assertFalse(SqlCache.isUncachedTarget("CALL purge_sessions()"));
		assertFalse(SqlCache.isUncachedTarget(null));

	}

}
