package io.jimble.db.sql;

import io.jimble.db.DB;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 で消したもの・例外にしたもの（DB。要件 D-193 / D-194）
 *
 * <p>
 * 1.5 は警告を出していた（D-192）。2.0 は<b>コンパイルエラーか例外</b>にする——黙って意味が変わるものは無い。
 * </p>
 */
class Db2RemovalsTest {

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

	// region 消したもの

	private static boolean hasMethod (Class<?> type, String name) {

		return Arrays.stream(type.getMethods()).anyMatch(m -> m.getName().equals(name));

	}

	@Test
	@DisplayName("D-193 / D-194 1.5 で非推奨にしたものは消えている")
	void removed () {

		for (String m : new String[] {"beginTransaction", "commit", "commitEndTransaction", "rollback", "rollbackEndTransaction",
			"endTransaction", "isError", "getError", "isDuplicateKeyError"}) {
			assertFalse(hasMethod(DB.class, m), "DB." + m + " が残っています");
		}
		assertThrows(ClassNotFoundException.class, () -> Class.forName("io.jimble.db.DBTransaction"));
		assertFalse(hasMethod(Dsl.class, "or"), "Dsl.or が残っています");
		assertFalse(hasMethod(Dsl.class, "and"), "Dsl.and が残っています");
		assertFalse(hasMethod(ISelect.class, "subtract"), "subtract が残っています");

	}

	private static void assertForRemoval2 (AnnotatedElement element) {

		Deprecated d = element.getAnnotation(Deprecated.class);
		assertTrue(d != null && d.forRemoval() && "2.0.0".equals(d.since()), element + " が非推奨（forRemoval, since 2.0.0）になっていません");

	}

	@Test
	@DisplayName("D-193 2.0 で同じ意味になった別名に @Deprecated(since = \"2.0.0\", forRemoval = true)")
	void deprecatedIn2 () throws Exception {

		for (String m : new String[] {"selectOrThrow", "selectListOrThrow", "insertNoReturnKey"}) {
			assertForRemoval2(DB.class.getMethod(m, String.class, Object[].class));
		}
		assertForRemoval2(DB.class.getMethod("selectOrThrow", SelectBuilder.class));
		assertForRemoval2(DB.class.getMethod("selectListOrThrow", SelectBuilder.class));
		assertForRemoval2(DB.class.getMethod("insertNoReturnKey", InsertBuilder.class));
		assertForRemoval2(DB.class.getMethod("isBatchSuccess", List.class));
		assertForRemoval2(DB.class.getMethod("isBatchSuccess", int[].class));
		assertForRemoval2(io.jimble.db.redis.lock.RedisLockStatus.class.getField("Failed"));

	}

	@Test
	@DisplayName("D-194 Column.and / or は IWhere の約束なので消せない。非推奨のまま、呼ぶと例外")
	void columnAndOrStayDeprecated () throws Exception {

		for (String m : new String[] {"and", "or"}) {
			Deprecated d = Column.class.getMethod(m, IWhere.class).getAnnotation(Deprecated.class);
			assertTrue(d != null && d.forRemoval(), "Column." + m + " の非推奨が外れています");
		}

	}

	// endregion

	// region null との比較（要件 D-194）

	@Test
	@DisplayName("D-194 eq(null) / not(null) は例外（1.5 は警告、1.4 までは黙って0件）")
	void nullComparisonThrows () {

		SqlBuildException eq = assertThrows(SqlBuildException.class, () -> TestSchema.Site.deleted_at.eq(null));
		assertTrue(eq.getMessage().contains("is_null()"), eq.getMessage());
		SqlBuildException not = assertThrows(SqlBuildException.class, () -> TestSchema.Site.deleted_at.not(null));
		assertTrue(not.getMessage().contains("is_not_null()"), not.getMessage());

		// is_null と、値のある比較は通る
		String sql = SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance())
			.where(TestSchema.Site.deleted_at.is_null(), TestSchema.Site.id.eq(1L)).sql();
		assertTrue(sql.contains("IS NULL"), sql);
		assertEquals(List.of(), warns);

	}

	@Test
	@DisplayName("D-194 where(Data) の空の値は例外（どの比較でも。is_null / in は別）")
	void whereDataEmptyValueThrows () {

		for (String key : new String[] {"name", "name|eq", "name|not", "name|like", "feed_count|ge", "name|contains"}) {
			Data where = new Data();
			where.putData("where", new Data().putData("site", new Data().putData(key, null)));
			SqlBuildException e = assertThrows(SqlBuildException.class,
				() -> SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance()).where(where), key);
			assertTrue(e.getMessage().contains("site." + key), e.getMessage());
			assertTrue(e.getMessage().contains("is_null"), e.getMessage());
		}

		// 空の配列も「空の値」
		Data emptyList = Data.fromJsonString("{\"where\":{\"site\":{\"name\":[]}}}");
		assertThrows(SqlBuildException.class, () -> SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance()).where(emptyList));

		// is_null は値を見ない
		Data isNull = Data.fromJsonString("{\"where\":{\"site\":{\"deleted_at|is_null\":null}}}");
		String sql = SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance()).where(isNull).sql();
		assertTrue(sql.contains("IS NULL"), sql);

	}

	// endregion

	// region 包む形（要件 D-194）

	@Test
	@DisplayName("D-194 where(Data) に \"where\" キーの無い空でない Data を渡すと例外（1.x は条件が付かずに全件）")
	void whereDataUnwrappedThrows () {

		Data q = Data.fromJsonString("{\"q\":{\"site\":{\"id\":1}}}");

		SqlBuildException e = assertThrows(SqlBuildException.class,
			() -> SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance()).where(q));
		assertTrue(e.getMessage().contains("\"where\""), e.getMessage());
		assertThrows(SqlBuildException.class, () -> SQL.update(TestSchema.Site.instance()).where(q));
		assertThrows(SqlBuildException.class, () -> SQL.delete(TestSchema.Site.instance()).where(q));

		// 空の Data は何もしない（検索条件が無い一覧）
		String sql = SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance()).where(new Data()).sql();
		assertFalse(sql.contains("WHERE"), sql);

	}

	@Test
	@DisplayName("D-194 set(Data) / value(Data) に包まない行・このテーブルの無い Data を渡すと例外（1.x は黙って何もしない）")
	void setValueUnwrappedThrows () {

		Data flat = new Data();
		flat.put("name", "x");

		SqlBuildException e = assertThrows(SqlBuildException.class, () -> SQL.update(TestSchema.Site.instance()).set(flat));
		assertTrue(e.getMessage().contains("setRow(Data)"), e.getMessage());
		SqlBuildException v = assertThrows(SqlBuildException.class, () -> SQL.insert(TestSchema.Site.instance()).value(flat));
		assertTrue(v.getMessage().contains("valueRow(Data)"), v.getMessage());

		Data otherTable = Data.fromJsonString("{\"set\":{\"feed\":{\"title\":\"x\"}}}");
		SqlBuildException t = assertThrows(SqlBuildException.class, () -> SQL.update(TestSchema.Site.instance()).set(otherTable));
		assertTrue(t.getMessage().contains("site"), t.getMessage());
		assertThrows(SqlBuildException.class,
			() -> SQL.insert(TestSchema.Site.instance()).value(Data.fromJsonString("{\"value\":{\"feed\":{\"title\":\"x\"}}}")));

		// 空の Data は何もしない
		UpdateBuilder b = SQL.update(TestSchema.Site.instance()).set(new Data()).set(TestSchema.Site.name, "y").where(TestSchema.Site.id.eq(1L));
		assertEquals(List.of("y", 1L), b.params());

	}

	@Test
	@DisplayName("D-194 apply(Data) は句をまとめて読むので、無い句は無いまま（例外にしない）")
	void applyIsLenient () {

		Data onlyWhere = Data.fromJsonString("{\"where\":{\"site\":{\"id\":1}}}");
		UpdateBuilder b = SQL.update(TestSchema.Site.instance()).apply(onlyWhere).set(TestSchema.Site.name, "x");
		assertEquals("[x, 1]", b.params().toString(), b.sql());

		Data onlyOrder = Data.fromJsonString("{\"order\":{\"site\":{\"id\":\"desc\"}}}");
		String sql = SQL.select(TestSchema.Site.id).from(TestSchema.Site.instance()).apply(onlyOrder).sql();
		assertTrue(sql.contains("DESC"), sql);

		Data onlySet = Data.fromJsonString("{\"set\":{\"site\":{\"name\":\"z\"}}}");
		UpdateBuilder u = SQL.update(TestSchema.Site.instance()).apply(onlySet).where(TestSchema.Site.id.eq(2L));
		assertEquals(List.of("z", 2L), u.params());

		assertEquals(List.of("v"), SQL.insert(TestSchema.Site.instance())
			.apply(Data.fromJsonString("{\"value\":{\"site\":{\"name\":\"v\"}}}")).params());
		assertTrue(SQL.delete(TestSchema.Site.instance()).apply(onlyOrder).allRows().sql().startsWith("DELETE"));

	}

	@Test
	@DisplayName("D-194 文字列 \"now()\" はただの文字列として入る（1.x は SQL の NOW() に変えていた）")
	void nowStringIsJustAString () {

		UpdateBuilder b = SQL.update(TestSchema.Site.instance())
			.set(Data.fromJsonString("{\"set\":{\"site\":{\"name\":\"now()\"}}}"))
			.where(TestSchema.Site.id.eq(1L));

		assertFalse(b.sql().contains("NOW()"), b.sql());
		assertEquals(List.of("now()", 1L), b.params());

		InsertBuilder i = SQL.insert(TestSchema.Site.instance())
			.value(Data.fromJsonString("{\"value\":{\"site\":{\"name\":\"now()\"}}}"));
		assertFalse(i.sql().contains("NOW()"), i.sql());
		assertEquals(List.of("now()"), i.params());

		assertEquals(List.of(), warns);

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
