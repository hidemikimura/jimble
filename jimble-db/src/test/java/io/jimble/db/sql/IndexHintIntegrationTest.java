package io.jimble.db.sql;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.TestDdl;
import io.jimble.db.dialect.DialectException;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * インデックスのヒントを付けた SQL が、実際に流れる（D-265）
 *
 * <p>MySQL では流れて同じ答えを返し、PostgreSQL では組み立てたところで断る。</p>
 */
@Tag("db")
class IndexHintIntegrationTest {

	@BeforeAll
	static void setUp () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), IndexHintIntegrationTest.class);

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS dsl_item");
		TestDdl.execute(db, """
			CREATE TABLE dsl_item (
				id       bigint unsigned auto_increment primary key,
				group_id bigint unsigned not null,
				name     varchar(50)     null,
				amount   bigint          not null,
				at       datetime        not null
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");
		db.execute("CREATE INDEX dsl_item__group_id ON dsl_item (group_id)");

		for (int i = 1; i <= 3; i++) {
			db.execute("INSERT INTO dsl_item (group_id, name, amount, at) VALUES (?, ?, ?, ?)"
				, i == 3 ? 2 : 1, "n" + i, i * 10, java.sql.Timestamp.valueOf("2026-10-04 00:00:00"));
		}

	}

	@AfterAll
	static void tearDown () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS dsl_item");
		DBUtil.stop();

	}

	@Test
	@DisplayName("MySQL：FORCE INDEX・JOIN の USE INDEX・IGNORE INDEX（PRIMARY）が流れて、ヒント無しと同じ答え。PostgreSQL：DialectException")
	void runs () {

		DB db = DBUtil.getMainDB();

		SelectBuilder forced = SQL.select(DslSchema.Item.id)
			.from(DslSchema.Item.instance().forceIndex("dsl_item__group_id"))
			.where(DslSchema.Item.group_id.eq(1L))
			.orderBy(DslSchema.Item.id.asc());

		if (db.dialect() instanceof PostgreSqlDialect) {
			assertThrows(DialectException.class, () -> db.selectList(forced));
			return;
		}

		List<Data> rows = db.selectList(forced);
		assertEquals(List.of(1L, 2L), rows.stream().map(row -> row.getLong(DslSchema.Item.id)).toList());

		List<Data> plain = db.selectList(SQL.select(DslSchema.Item.id).from(DslSchema.Item.instance())
			.where(DslSchema.Item.group_id.eq(1L)).orderBy(DslSchema.Item.id.asc()));
		assertEquals(plain, rows, "ヒントを付けたら答えが変わった");

		List<Data> ignored = db.selectList(SQL.select(DslSchema.Item.id)
			.from(DslSchema.Item.instance().ignoreIndex("PRIMARY"))
			.where(DslSchema.Item.id.eq(3L)));
		assertEquals(1, ignored.size());

		// EXPLAIN で、本当にその索引を使っていることを見る
		Data explain = db.select("EXPLAIN " + forced.sql(db.dialect()), forced.params().toArray()).orElseThrow();
		assertEquals("dsl_item__group_id", explain.getString("key"), explain.toString());
		assertTrue(forced.sql(db.dialect()).contains("FORCE INDEX (`dsl_item__group_id`)"));

	}

}
