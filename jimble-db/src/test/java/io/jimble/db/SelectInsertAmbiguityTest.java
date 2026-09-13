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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 戻り値の2義性を消す口（{@code selectOrThrow} / {@code insertKey}）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code select} の {@code null} には2つの意味がある</b>——「1件も無かった」と
 * 「読めなかった」である。<b>見分けるには {@code isError()} を見るしかない</b>ので、
 * </p>
 *
 * <pre>
 * Data user = db.select(sql, id);
 * if (user == null) { return 誰でもない; }
 * </pre>
 *
 * <p>
 * と書いたアプリは、<b>DB が読めなかった日に「そんな利用者はいません」と答える。</b>
 * 例外も出ないしログにも残らない。<b>認証でこれをやると、落ちているあいだ
 * 全員がログインできないのではなく、全員が「知らない人」になる。</b>
 * </p>
 *
 * <p>
 * {@code insert} も同じで、<b>{@code 1} が「id=1」なのか「1件」なのかは
 * 表の定義で決まる</b>——採番列を1本足しただけで、呼ぶ側を触っていないのに意味が変わる。
 * </p>
 *
 * <p>
 * <b>1.0 では戻り値の型を変えられない</b>ので、既定はそのままにして、
 * <b>見分けられる呼び方を足した</b>。ここで固定するのは「足したほうが正しく分岐すること」と
 * 「<b>足しても元の作法が変わっていないこと</b>」の両方である。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-db:pgTest}</p>
 */
@Tag("db")
class SelectInsertAmbiguityTest {

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), SelectInsertAmbiguityTest.class)
			, "DB に接続できませんでした");

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

	// region いままでの作法は変えていない

	@Test
	@DisplayName("select は 0件でもエラーでも null のまま（既定は変えていない）")
	void selectStillReturnsNullForBoth () {

		DB db = DBUtil.getMainDB();

		assertNull(db.select("SELECT * FROM amb_keyed WHERE id = ?", 1));
		assertFalse(db.isError(), "0件なのにエラーになっています");

		assertNull(db.select(UNREADABLE));
		assertTrue(db.isError(), "読めていないのにエラーになっていません");

	}

	@Test
	@DisplayName("selectList は 0件が空リスト・エラーが null のまま（既定は変えていない）")
	void selectListStillReturnsNullOnError () {

		DB db = DBUtil.getMainDB();

		assertEquals(List.of(), db.selectList("SELECT * FROM amb_keyed"));
		assertNull(db.selectList(UNREADABLE));

	}

	@Test
	@DisplayName("insert は失敗すると -1 のまま（既定は変えていない）")
	void insertStillReturnsMinusOneOnFailure () {

		DB db = DBUtil.getMainDB();

		assertEquals(1, db.insert("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "a", "A"));

		// 主キー重複
		assertEquals(-1, db.insert("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "a", "A"));
		assertTrue(db.isError());

	}

	@Test
	@DisplayName("採番列の無い表では insert が『件数』を返す——これが2義性である")
	void insertReturnsTheCountWhenThereIsNoGeneratedColumn () {

		DB db = DBUtil.getMainDB();

		long value = db.insert("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "x", "X");

		// 1 が返るが、これは「1件入った」であって「id=1」ではない
		assertEquals(1, value);
		assertFalse(db.isError());

	}

	// endregion

	// region 足したほうは分岐できる

	@Test
	@DisplayName("selectOrThrow の null は『1件も無かった』だけ")
	void selectOrThrowReturnsNullOnlyForEmpty () {

		DB db = DBUtil.getMainDB();

		assertNull(db.selectOrThrow("SELECT * FROM amb_keyed WHERE id = ?", 1));

		db.insert("INSERT INTO amb_keyed (name) VALUES (?)", "one");

		Data row = db.selectOrThrow("SELECT * FROM amb_keyed WHERE name = ?", "one");

		assertEquals("one", row.getString("name"));

	}

	@Test
	@DisplayName("selectOrThrow は読めなければ投げる")
	void selectOrThrowThrowsWhenUnreadable () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException thrown = assertThrows(SqlExecuteException.class
			, () -> db.selectOrThrow(UNREADABLE));

		assertEquals("DB_999", thrown.getCode());
		assertTrue(thrown.getMessage().contains("SELECT"), thrown.getMessage());

	}

	@Test
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

		assertEquals("two", db.selectOrThrow("SELECT * FROM amb_keyed WHERE id = ?", second).getString("name"));

	}

	@Test
	@DisplayName("採番列が無ければ insertKey は投げる（件数を採番値として返さない）")
	void insertKeyThrowsWhenThereIsNoGeneratedColumn () {

		DB db = DBUtil.getMainDB();

		SqlExecuteException thrown = assertThrows(SqlExecuteException.class
			, () -> db.insertKey("INSERT INTO amb_nokey (code, name) VALUES (?, ?)", "y", "Y"));

		assertTrue(thrown.getMessage().contains("insertNoReturnKey"), thrown.getMessage());

		// 投げても、入ったものは入っている（INSERT 自体は成功している）
		assertEquals(1, db.selectListOrThrow("SELECT * FROM amb_nokey WHERE code = ?", "y").size());

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

	// endregion

}
