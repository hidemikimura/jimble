package io.jimble.db.lock;

import io.jimble.db.FrameworkTables;
import io.jimble.util.hash.Hash;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.version.DBVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * lock
 */
public class DBLock {

	/**
	 * ロックキーを作成する
	 *
	 * @param db        DB
	 * @param lockKey   ロックキー
	 * @return  正常に終了した場合 = true
	 */
	public static boolean create (DB db, String...lockKey) {

		if (lockKey == null || lockKey.length == 0) {
			return true;
		}

		List<Long> lockKeyHashes = createLockKeys(lockKey);
		if (lockKeyHashes.isEmpty()) {
			return true;
		}

		List<List<Object>> paramsList = new ArrayList<>();
		for (long key : lockKeyHashes) {
			paramsList.add(new SQLParameterList(key));
		}
		List<Integer> results = db.executeBatch(
			Sqls.insertIgnoreInto(db.dialect(), FrameworkTables.DB_LOCK)
				+ " (lock_key) VALUES (?)"
				+ Sqls.insertIgnoreTail(db.dialect())
			, paramsList
		);

		return DB.isBatchSuccess(results);

	}

	/**
	 * ロックキーを削除する
	 *
	 * @param db        DB
	 * @param lockKey   ロックキー
	 */
	public static void delete (DB db, String...lockKey) {

		if (lockKey == null || lockKey.length == 0) {
			return;
		}

		List<Long> lockKeyHashes = createLockKeys(lockKey);
		if (lockKeyHashes.isEmpty()) {
			return;
		}

		List<List<Object>> paramsList = new ArrayList<>();
		for (long key : lockKeyHashes) {
			paramsList.add(new SQLParameterList(key));
		}

		db.executeBatch(
			"DELETE FROM db_lock WHERE lock_key = ?"
			, paramsList
		);

	}

	/**
	 * ロックする
	 *
	 * @param db        DB
	 * @param lockKey   ロックキー
	 * @return  正常にロックできた場合 = true
	 */
	public static boolean lock (DB db, String...lockKey) {

		if (lockKey == null) {
			return true;
		}

		List<Long> lockKeyHashes = createLockKeys(lockKey);
		if (lockKeyHashes.isEmpty()) {
			return true;
		}

		for (long key : lockKeyHashes) {
			Data dbLock = db.select("SELECT * FROM db_lock WHERE lock_key = ? FOR UPDATE", key);
			if (dbLock == null || db.isError()) {
				return false;
			}
		}

		return true;

	}

	private static List<Long> createLockKeys (String...lockKey) {

		List<Long> lockKeyHashes = new ArrayList<>();

		if (lockKey == null) {
			return lockKeyHashes;
		}

		for (String key : lockKey) {
			if (key != null && !key.isEmpty()) {
				lockKeyHashes.add(Hash.sipHash(key));
			}
		}

		return lockKeyHashes;

	}

	/**
	 * 初期化
	 *
	 * @param db DB
	 */
	public static void init (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.DB_LOCK, "ロック情報");
		dbVersion.add(1)
			.mysql("""
				create table db_lock (
					lock_key varchar(250) not null primary key
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT '%s'
			""".formatted(dbVersion.placeholder()))
			.postgresql("""
				create table db_lock (
					lock_key varchar(250) not null primary key
				)
			""");
		dbVersion.add(2)
			.mysql("""
				truncate table db_lock
			"""
			, """
				alter table db_lock modify lock_key bigint not null
			""")
			.postgresql("""
				truncate table db_lock
			"""
			, """
				alter table db_lock alter column lock_key type bigint using lock_key::bigint
			""");
		dbVersion.apply(db);

	}

}
