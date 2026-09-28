package io.jimble.db;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 戻り値の2義性が無くなったこと（要件 D-193）
 *
 * <p>
 * 1.x の {@code select} の {@code null} には「1件も無かった」と「読めなかった」の2つの意味があり、
 * {@code insert} の {@code 1} は「id=1」か「1件」かが表の定義で決まっていた。
 * 2.0 で {@code select} は {@code Optional}（読めなければ例外）、{@code insert} は値を返さない形にした。
 * 1.1 で足した {@code selectOrThrow} / {@code insertNoReturnKey} は 2.0 で非推奨の別名として残る。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-db:pgTest}</p>
 */
@Tag("db")
class SelectInsertAmbiguityTest {

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), SelectInsertAmbiguityTest.class);

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS amb_keyed");
		db.execute("DROP TABLE IF EXISTS amb_nokey");

		TestDdl.execute(db, """
			CREATE TABLE amb_keyed (
				id    bigint unsigned auto_increment primary key,
				name  varchar(250) null
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

		/*
		 * <b>採番列を持たない表。</b>列名に id を使わないのは、
		 * PostgreSQL の getGeneratedKeys が行の全列を返すからである
		 * （id という名前があると、採番していなくても採番値として読めてしまう）。
		 */
		TestDdl.execute(db, """
			CREATE TABLE amb_nokey (
				code  varchar(50)  not null primary key,
				name  varchar(250) null
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

	}

	@AfterAll
	static void stopDataSource () {

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS amb_keyed");
		db.execute("DROP TABLE IF EXISTS amb_nokey");

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DBUtil.getMainDB().execute("DELETE FROM amb_keyed");
		DBUtil.getMainDB().execute("DELETE FROM amb_nokey");

	}

	/** 読めない SQL */
	private static final String UNREADABLE = "SELECT * FROM amb_no_such_table";

	// region 2.0 の作法

	@Test
	@DisplayName("D-193 select は 0件が空、読めなければ例外（1.x は両方 null）")
	void selectSeparatesEmptyAndFailure () {

		DB db = DBUtil.getMainDB();

		assertTrue(db.select("SELECT * FROM amb_keyed WHERE id = ?", 1).isEmpty());

		assertThrows(SqlExecuteException.class, () -> db.select(UNREADABLE));

	}

	@Test
	@DisplayName("D-193 selectList は 0件が空リスト、読めなければ例外（1.x は null）")
	void selectListNeverReturnsNull () {

		DB db = DBUtil.getMainDB();

		assertEquals(List.of(), db.selectList("SELECT * FROM amb_keyed"));
		assertThrows(SqlExecuteException.class, () -> db.selectList(UNREADABLE));

	}

	@Test
	@DisplayName("D-193 insert は失敗すると例外（1.x は -1）。主キー重複は DuplicateKeyException")
	void insertThrowsOnFailure () {

		DB db = DBUtil.getMainDB();

		db.insert("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "a", "A");

		assertThrows(DuplicateKeyException.class, () -> db.insert("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "a", "A"));

		assertEquals(1, db.selectList("SELECT * FROM amb_nokey").size());

	}

	// endregion

	// region 1.1 で足したもの（2.0 で非推奨の別名）

	@Test
	@SuppressWarnings("removal")
	@DisplayName("selectOrThrow の null は『1件も無かった』だけ")
	void selectOrThrowReturnsNullOnlyForEmpty () {

		DB db = DBUtil.getMainDB();

		assertNull(db.selectOrThrow("SELECT * FROM amb_keyed WHERE id = ?", 1));

		db.insert("INSERT INTO amb_keyed (name) VALUES (?)", "one");

		Data row = db.selectOrThrow("SELECT * FROM amb_keyed WHERE name = ?", "one");

		assertEquals("one", row.getString("name"));

	}

	@Test
	@SuppressWarnings("removal")
	@DisplayName("selectOrThrow は読めなければ投げる")
	void selectOrThrowThrowsWhenUnreadable () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException thrown = assertThrows(SqlExecuteException.class
			, () -> db.selectOrThrow(UNREADABLE));

		assertEquals("DB_999", thrown.getCode());
		assertTrue(thrown.getMessage().contains("SELECT"), thrown.getMessage());

	}

	@Test
	@SuppressWarnings("removal")
	@DisplayName("selectListOrThrow は 0件が空リスト、読めなければ投げる")
	void selectListOrThrowNeverReturnsNull () {

		DB db = DBUtil.getMainDB();

		assertEquals(List.of(), db.selectListOrThrow("SELECT * FROM amb_keyed"));

		assertThrows(SqlExecuteException.class, () -> db.selectListOrThrow(UNREADABLE));

	}

	@Test
	@DisplayName("insertKey は採番された値だけを返す")
	void insertKeyReturnsTheGeneratedKey () {

		DB db = DBUtil.getMainDB();

		long first = db.insertKey("INSERT INTO amb_keyed (name) VALUES (?)", "one");
		long second = db.insertKey("INSERT INTO amb_keyed (name) VALUES (?)", "two");

		assertTrue(first > 0, "採番値が返っていません: " + first);
		assertEquals(first + 1, second, "採番が進んでいません");

		assertEquals("two", db.select("SELECT * FROM amb_keyed WHERE id = ?", second).orElseThrow().getString("name"));

	}

	@Test
	@DisplayName("採番列が無ければ insertKey は投げる（件数を採番値として返さない）")
	void insertKeyThrowsWhenThereIsNoGeneratedColumn () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException thrown = assertThrows(SqlExecuteException.class
			, () -> db.insertKey("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "y", "Y"));

		assertTrue(thrown.getMessage().contains("insert を使って"), thrown.getMessage());

		// 投げても、入ったものは入っている（INSERT 自体は成功している）
		assertEquals(1, db.selectList("SELECT * FROM amb_nokey WHERE code = ?", "y").size());

	}

	@Test
	@DisplayName("insertKey は入らなければ投げる")
	void insertKeyThrowsWhenTheInsertFails () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException thrown = assertThrows(SqlExecuteException.class
			, () -> db.insertKey("INSERT INTO amb_keyed (no_such_column) VALUES (?)", 1));

		assertEquals("DB_999", thrown.getCode());
		assertTrue(thrown.getMessage().contains("INSERT"), thrown.getMessage());

	}

	@Test
	@SuppressWarnings("removal")
	@DisplayName("D-193 insertNoReturnKey（非推奨）は件数を返し、失敗は例外")
	void insertNoReturnKeyReturnsCount () {

		DB db = DBUtil.getMainDB();

		assertEquals(1, db.insertNoReturnKey("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "n", "N"));
		assertThrows(DuplicateKeyException.class, () -> db.insertNoReturnKey("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "n", "N"));

	}

	// endregion

}
