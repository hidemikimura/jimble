package io.jimble.db;

import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移植元から引き継いだ表・途中で止まった表に、列を足す版が正しく当たるか（D-302）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class DbVersionUpgradeIntegrationTest {

	/* テスト用の表 */
	private static final String TABLE = "version_upgrade_probe";

	/* 表のコメント */
	private static final String COMMENT = "版の引き継ぎのテスト";

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), DbVersionUpgradeIntegrationTest.class);

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS " + TABLE);
		DBUtil.stop();

	}

	@BeforeEach
	void dropTable () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS " + TABLE);

	}

	/**
	 * 版 1 で表を作り、版 2・3 で列を1つずつ足す定義
	 *
	 * @return	定義
	 */
	private static DBVersion definition () {

		DBVersion version = new DBVersion(TABLE, COMMENT);

		version.add(1)
			.mysql("""
				create table %s (
					id bigint not null primary key
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(TABLE, version.placeholder()))
			.postgresql("create table %s (id bigint not null primary key)".formatted(TABLE));

		version.add(2)
			.addsColumn("first_added")
			.mysql("alter table %s add column first_added bigint null".formatted(TABLE))
			.postgresql("alter table %s add column first_added bigint null".formatted(TABLE));

		version.add(3)
			.addsColumn("second_added")
			.mysql("alter table %s add column second_added int default 0 not null".formatted(TABLE))
			.postgresql("alter table %s add column second_added int default 0 not null".formatted(TABLE));

		return version;

	}

	/**
	 * 表を外から作る（版 1 の形で、コメントだけ好きな版にする）
	 *
	 * @param commentVersion	コメントに書く版
	 * @param extraColumns		版 1 のほかに作っておく列の定義（無ければ空）
	 */
	private static void createTable (long commentVersion, String extraColumns) {

		DB db = DBUtil.getMainDB();

		db.execute("create table %s (id bigint not null primary key%s)".formatted(TABLE, extraColumns));
		db.execute(db.dialect().setTableCommentSql(TABLE, COMMENT + ":" + commentVersion));

		// DBVersion は DB ごとに表のコメントを1回だけ読むので、外から書き換えたら読み直す
		DBVersion.reload(db);

	}

	private static boolean hasColumn (String column) {

		DB db = DBUtil.getMainDB();

		return db.select(db.dialect().columnExistsSql(), TABLE, column).orElseThrow().getLong("cnt") > 0;

	}

	private static String comment () {

		DB db = DBUtil.getMainDB();

		return db.selectList(db.dialect().tableCommentsSql()).stream()
			.filter(row -> row.getString("table_name").equalsIgnoreCase(TABLE))
			.findFirst().orElseThrow()
			.getStringOptional("table_comment");

	}

	@Test
	@DisplayName("D-302 コメントの版が jimble の版より大きい表（移植元から引き継いだ「:6」）にも、無い列は足す。コメントの版は下げない")
	void addsMissingColumnsToTableWithLargerVersion () {

		createTable(6, "");

		assertFalse(hasColumn("first_added"));

		assertTrue(definition().apply(DBUtil.getMainDB()));

		assertTrue(hasColumn("first_added"), "版 2 の列が足されていない");
		assertTrue(hasColumn("second_added"), "版 3 の列が足されていない");
		assertEquals(COMMENT + ":6", comment(), "コメントの版を下げた");

		// もう一度通しても何もしない（Duplicate column にならない）
		DBVersion.reload(DBUtil.getMainDB());
		assertTrue(definition().apply(DBUtil.getMainDB()));
		assertEquals(COMMENT + ":6", comment());

	}

	@Test
	@DisplayName("D-302 版 2 の列だけ足されてコメントが「:1」のまま止まった表は、版 3 の列だけを足して起動が通る。コメントは「:3」になる")
	void resumesAfterStoppingBetweenVersions () {

		createTable(1, ", first_added bigint null");

		assertTrue(definition().apply(DBUtil.getMainDB()));

		assertTrue(hasColumn("second_added"), "版 3 の列が足されていない");
		assertEquals(COMMENT + ":3", comment());

	}

	@Test
	@DisplayName("D-302 新しい DB では、版 1 から順に当たり、版ごとにコメントが進む")
	void createsFromScratch () {

		DBVersion.reload(DBUtil.getMainDB());

		assertTrue(definition().apply(DBUtil.getMainDB()));

		assertTrue(hasColumn("first_added"));
		assertTrue(hasColumn("second_added"));
		assertEquals(COMMENT + ":3", comment());

	}

}
