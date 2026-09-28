package io.jimble.db.lock;

import io.jimble.db.FrameworkTables;
import io.jimble.util.hash.Hash;
import io.jimble.db.DB;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.internal.version.DBVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * lock
 */
public class DBLock {

	/**
	 * ロックキーを作成する（あれば何もしない）
	 *
	 * <p>
	 * <b>失敗は例外</b>（2.0。要件 D-193）。1.x は {@code boolean} を返していた。
	 * </p>
	 *
	 * @param db        DB
	 * @param lockKey   ロックキー
	 * @throws io.jimble.db.SqlExecuteException	作れなかったとき
	 */
	public static void create (DB db, String...lockKey) {

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
		// 失敗は executeBatch が投げる
		db.executeBatch(
			Sqls.insertIgnoreInto(db.dialect(), FrameworkTables.DB_LOCK)
				+ " (lock_key) VALUES (?)"
				+ Sqls.insertIgnoreTail(db.dialect())
			, paramsList
		);

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
	 * ロックする（トランザクションの終わりまで、同じキーを持つ他の処理を待たせる）
	 *
	 * <pre>
	 * db.transaction(tx -&gt; {
	 *     DBLock.lock(db, "order:" + id);
	 *     ...
	 * });
	 * </pre>
	 *
	 * <p>
	 * <b>トランザクションの外で呼ぶと例外</b>（2.0。要件 D-193）。{@code SELECT ... FOR UPDATE} の鍵は
	 * 文の終わりで外れるので、1.x は外で呼んでも {@code true} を返し、<b>何も守らないまま先へ進んでいた</b>。
	 * キーが無い（{@link #create} していない）ときと、SQL の失敗も例外。
	 * </p>
	 *
	 * @param db        DB
	 * @param lockKey   ロックキー
	 * @throws IllegalStateException	トランザクションの外で呼んだとき、キーが無いとき
	 * @throws io.jimble.db.SqlExecuteException	SQL が失敗したとき
	 */
	public static void lock (DB db, String...lockKey) {

		if (lockKey == null) {
			return;
		}

		List<Long> lockKeyHashes = createLockKeys(lockKey);
		if (lockKeyHashes.isEmpty()) {
			return;
		}

		if (!db.isTransaction()) {
			throw new IllegalStateException(
				"DBLock.lock はトランザクションの中で呼んでください（外では鍵が文の終わりで外れ、何も守りません）。"
					+ "db.transaction(tx -> { DBLock.lock(db, ...); ... }) と書きます");
		}

		for (String key : lockKey) {
			if (key == null || key.isEmpty()) {
				continue;
			}
			if (db.select("SELECT * FROM db_lock WHERE lock_key = ? FOR UPDATE", Hash.sipHash(key)).isEmpty()) {
				throw new IllegalStateException(
					"ロックキーがありません（先に DBLock.create(db, キー) を呼んでください）: " + key);
			}
		}

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
