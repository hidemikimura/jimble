package io.jimble.db.sql;

import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.util.data.Data;
import io.jimble.util.internal.WarnOnce;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 で消すもの・変わるものを、1.5 のうちに知らせる（DB。要件 D-192）
 */
@SuppressWarnings("removal")  // 1.x の書き方も確かめている（2.0 で消す。要件 D-192）
class DbDeprecationAndWarningTest {

	private final List<String> warns = new ArrayList<>();

	@BeforeEach
	void capture () {

		WarnOnce.reset();
		Log.sink((logger, level, message, data, throwable) -> {
			if (level.toInt() >= org.slf4j.event.Level.WARN.toInt()) {
				warns.add(message);
			}
		});

	}

	@AfterEach
	void reset () {

		Log.resetSink();
		WarnOnce.reset();

	}

	private long count (String text) {

		return warns.stream().filter(w -> w.contains(text)).count();

	}

	// region 非推奨

	private static void assertForRemoval (AnnotatedElement element) {

		Deprecated d = element.getAnnotation(Deprecated.class);
		assertTrue(d != null && d.forRemoval() && "1.5.0".equals(d.since()), element + " が非推奨（forRemoval, since 1.5.0）になっていません");

	}

	@Test
	@DisplayName("D-192 2.0 で消すものに @Deprecated(since = \"1.5.0\", forRemoval = true)")
	void deprecated () throws Exception {

		assertForRemoval(DBTransaction.class);
		for (String m : new String[] {"beginTransaction", "commit", "commitEndTransaction", "rollback", "rollbackEndTransaction", "endTransaction"}) {
			assertForRemoval(DB.class.getMethod(m));
		}
		assertForRemoval(Dsl.class.getMethod("or", IWhere.class));
		assertForRemoval(Dsl.class.getMethod("and", IWhere.class));
		assertForRemoval(Column.class.getMethod("and", IWhere.class));
		assertForRemoval(Column.class.getMethod("or", IWhere.class));
		assertForRemoval(ISelect.class.getMethod("subtract", Object.class));

	}

	// endregion

	// region 警告

	@Test
	@DisplayName("D-192 eq(null) / not(null) は1度だけ警告する（is_null は言わない）")
	void nullComparison () {

		TestSchema.Site.deleted_at.eq(null);
		TestSchema.Site.deleted_at.eq(null);
		TestSchema.Site.deleted_at.not(null);
		TestSchema.Site.deleted_at.is_null();
		TestSchema.Site.id.eq(1L);

		assertEquals(1, count("eq(null)"), warns.toString());
		assertEquals(1, count("not(null)"), warns.toString());
		assertEquals(2, warns.size(), warns.toString());
		assertTrue(warns.getFirst().contains("is_null()"), warns.getFirst());

	}

	@Test
	@DisplayName("D-192 where(Data) の空の値も同じ警告になる")
	void whereDataEmptyValue () {

		SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance())
			.where(Data.fromJsonString("{\"where\":{\"site\":{\"name\":null}}}"));

		assertEquals(1, count("eq(null)"), warns.toString());

	}

	@Test
	@DisplayName("D-192 set(Data) / value(Data) に包まない行を渡すと警告する（包んだ形・空は言わない）")
	void unwrapped () {

		Data flat = new Data();
		flat.put("name", "x");

		SQL.update(TestSchema.Site.instance()).set(flat);
		SQL.insert(TestSchema.Site.instance()).value(flat);
		SQL.update(TestSchema.Site.instance()).set(new Data());

		assertEquals(1, count("set(Data)"), warns.toString());
		assertEquals(1, count("value(Data)"), warns.toString());

		warns.clear();
		WarnOnce.reset();
		SQL.update(TestSchema.Site.instance()).set(Data.fromJsonString("{\"set\":{\"site\":{\"name\":\"x\"}}}"));
		assertEquals(List.of(), warns);

	}

	@Test
	@DisplayName("D-192 文字列 \"now()\" を NOW() に変えたら警告する")
	void nowString () {

		UpdateBuilder b = SQL.update(TestSchema.Site.instance())
			.set(Data.fromJsonString("{\"set\":{\"site\":{\"name\":\"now()\"}}}"))
			.where(TestSchema.Site.id.eq(1L));

		assertTrue(b.sql().contains("NOW()"), b.sql());
		assertEquals(1, count("\"now()\""), warns.toString());

	}

	// endregion

	// region 平らな行

	@Test
	@DisplayName("D-192 setRow / valueRow は平らな行を入れ、\"now()\" を変えない")
	void rows () {

		Data row = new Data();
		row.put("name", "now()");
		row.put("group_id", 3L);

		InsertBuilder insert = SQL.insert(TestSchema.Site.instance()).valueRow(row);
		assertTrue(insert.sql().contains("`name`"), insert.sql());
		assertEquals(List.of("now()", 3L), insert.params());

		UpdateBuilder update = SQL.update(TestSchema.Site.instance()).setRow(row).where(TestSchema.Site.id.eq(1L));
		assertEquals(List.of("now()", 3L, 1L), update.params());
		assertEquals(List.of(), warns, "setRow が警告を出している");

	}

	@Test
	@DisplayName("D-192 setRow / valueRow に入れ子の行（結果の Data）を渡すと例外")
	void nestedRowThrows () {

		Data nested = Data.fromJsonString("{\"site\":{\"name\":\"x\"}}");
		SqlBuildException e = assertThrows(SqlBuildException.class, () -> SQL.insert(TestSchema.Site.instance()).valueRow(nested));
		assertTrue(e.getMessage().contains("flattenTable"), e.getMessage());
		assertThrows(SqlBuildException.class, () -> SQL.update(TestSchema.Site.instance()).setRow(nested));

	}

	// endregion

}
