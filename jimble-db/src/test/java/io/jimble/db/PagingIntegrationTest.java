package io.jimble.db;

import io.jimble.db.data.SelectListResponse;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.paging.Paging;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ページングが実 DB に対して動くことの確認（要件 F-V-05）
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 */
@Tag("db")
class PagingIntegrationTest {

	/** 件数 */
	private static final int TOTAL = 25;

	@BeforeAll
	static void setUp () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), PagingIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS paging_item");
		TestDdl.execute(db, """
			CREATE TABLE paging_item (
				id   bigint unsigned auto_increment primary key,
				name varchar(50) not null
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

	}

	@AfterAll
	static void tearDown () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS paging_item");
		DBUtil.stop();

	}

	@BeforeEach
	void fill () {

		DB db = DBUtil.getMainDB();
		db.execute("TRUNCATE TABLE paging_item");

		for (int i = 1; i <= TOTAL; i++) {
			db.insert("INSERT INTO paging_item (name) VALUES (?)", "item-" + i);
		}

	}

	// region テスト

	@Test
	@DisplayName("SELECT にページングが適用され、総件数と最大ページが返る")
	void paging () {

		// docs:begin paging-select
		Paging paging = new Paging();
		paging.load(request("2", "10"), 0);

		SelectListResponse response = DBUtil.getMainDB().selectListWithRowCount(select().paging(paging));

		assertEquals(10, response.list.size(), "1ページ分だけ取れていない");
		assertEquals(TOTAL, response.rowCount, "総件数が LIMIT に影響されている");

		assertEquals(TOTAL, paging.totalCount());
		assertEquals(3, paging.maxPage(), "25 件を 10 件ずつなら 3 ページ");
		assertEquals(11, paging.start());
		// docs:end

		// 2ページ目の先頭は 11 件目
		assertEquals("item-11", response.list.getFirst().getString(PagingItem.name));

	}

	@Test
	@DisplayName("最後のページは余りだけ取れる")
	void lastPage () {

		Paging paging = new Paging();
		paging.load(request("3", "10"), 0);

		SelectListResponse response = DBUtil.getMainDB().selectListWithRowCount(select().paging(paging));

		assertEquals(5, response.list.size());
		assertEquals(TOTAL, paging.totalCount());
		assertEquals(5, paging.count());

	}

	@Test
	@DisplayName("範囲外のページは0件。総件数と最大ページは正しい")
	void outOfRangePage () {

		Paging paging = new Paging();
		paging.load(request("99", "10"), 0);

		SelectListResponse response = DBUtil.getMainDB().selectListWithRowCount(select().paging(paging));

		assertEquals(0, response.list.size());
		assertEquals(TOTAL, paging.totalCount());
		assertEquals(3, paging.maxPage());

	}

	@Test
	@DisplayName("per=all は全件取れる")
	void perAll () {

		Paging paging = new Paging();
		paging.load(request("1", "all"), 0);

		SelectListResponse response = DBUtil.getMainDB().selectListWithRowCount(select().paging(paging));

		assertEquals(TOTAL, response.list.size());
		assertEquals(1, paging.maxPage());

	}

	// endregion

	// region ヘルパー

	/**
	 * SELECT を組み立てる
	 *
	 * @return	SelectBuilder
	 */
	private SelectBuilder select () {

		return SQL.select().from(PagingItem.instance()).orderBy(PagingItem.id.asc());

	}

	/**
	 * リクエストデータ
	 *
	 * @param page	ページ番号
	 * @param per	取得件数
	 * @return	リクエストデータ
	 */
	private Data request (String page, String per) {

		Data data = new Data();
		data.put(Paging.namePage(), page);
		data.put(Paging.namePer(), per);

		return data;

	}

	// endregion

	// region テスト用のテーブル定義

	/** スキーマ */
	public static class PagingSchema extends io.jimble.db.sql.definition.schema.AbstractSchema {

		@Override
		public String name () {

			return "paging";

		}

	}

	/** テーブル */
	public static class PagingItem extends io.jimble.db.sql.definition.table.Table {

		/** ID */
		public static final io.jimble.db.sql.definition.column.Column id =
			new io.jimble.db.sql.definition.column.Column(instance(), "id", long.class, false, null, true);

		/** 名前 */
		public static final io.jimble.db.sql.definition.column.Column name =
			new io.jimble.db.sql.definition.column.Column(instance(), "name", String.class, false, null, false);

		private static final java.util.List<io.jimble.db.sql.definition.column.Column> COLUMNS = java.util.List.of(id, name);

		@Override
		protected java.util.List<io.jimble.db.sql.definition.column.Column> declareColumns () {

			return COLUMNS;

		}

		public PagingItem (io.jimble.util.data.definition.ISchema schema, String tableName) {

			super(schema, tableName);

		}

		/**
		 * インスタンス
		 *
		 * @return	テーブル
		 */
		public static PagingItem instance () {

			return new PagingItem(new PagingSchema(), "paging_item");

		}

	}

	// endregion

}
