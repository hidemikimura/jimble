package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1つの値を書く場所にリストを渡させない（D-204）
 *
 * <p>
 * パラメータは入れ子のリストを平らにして渡す。<b>{@code ?} が1つの場所にリストが来ると、
 * 値だけが増えて、後ろのプレースホルダーとずれる。</b>MariaDB のドライバは余った値を黙って捨てるので、
 * </p>
 * <pre>
 * SQL.update(User).setRow({"nickname": ["x", 999]}).where(User.id.eq(me))
 * → UPDATE users SET nickname = ? WHERE id = ?   値 ["x", 999, me]
 * </pre>
 * <p>が<b>id = 999 の行を書き換えていた</b>。リクエストの JSON や {@code a[]=} のフォームは、そのままリストになる。</p>
 */
class BindValueShapeTest {

	private static final Column ID = TestSchema.Site.id;
	private static final Column NAME = TestSchema.Site.name;

	@Test
	@DisplayName("setRow：リストの値は断る（別の行を書き換えさせない）")
	void setRowRejectsList () {

		Data row = Data.fromJsonString("{\"name\": [\"x\", 999]}");

		SqlBuildException ex = assertThrows(SqlBuildException.class
			, () -> SQL.update(TestSchema.Site.instance()).setRow(row).where(ID.eq(1)).params());
		assertTrue(ex.getMessage().contains("name"), ex.getMessage());

	}

	@Test
	@DisplayName("valueRow：リストの値は断る")
	void valueRowRejectsList () {

		Data row = Data.fromJsonString("{\"name\": [\"x\", \"y\"]}");

		assertThrows(SqlBuildException.class, () -> SQL.insert(TestSchema.Site.instance()).valueRow(row).params());

	}

	@Test
	@DisplayName("set(列, リスト) / eq(リスト) / 大小比較 / LIKE も断る（contains は文字列にするのでずれない）")
	void scalarConditionsRejectList () {

		List<Object> smuggled = List.of("x", 999);

		assertThrows(SqlBuildException.class
			, () -> SQL.update(TestSchema.Site.instance()).set(NAME, smuggled).where(ID.eq(1)).params());
		assertThrows(SqlBuildException.class, () -> select(NAME.eq(smuggled)).params());
		assertThrows(SqlBuildException.class, () -> select(ID.gt(new Object[] {1, 2})).params());
		assertThrows(SqlBuildException.class, () -> select(NAME.like(smuggled)).params());

	}

	@Test
	@DisplayName("IN の一覧の中の入れ子は断る")
	void inRejectsNested () {

		assertThrows(SqlBuildException.class, () -> select(ID.in(List.of(List.of(1, 2), 3))).params());
		assertThrows(SqlBuildException.class, () -> select(ID.not_in(List.of(1, List.of(2, 3)))).params());

	}

	@Test
	@DisplayName("apply(Data) の where に入れ子のリストを入れても、後ろの条件の値はずれない")
	void applyRejectsNested () {

		Data request = Data.fromJsonString("{\"where\": {\"site\": {\"name\": [[\"x\", 2]]}}}");

		assertThrows(SqlBuildException.class
			, () -> SQL.select(ID).from(TestSchema.Site.instance()).apply(request).where(ID.eq(7)).params());

	}

	@Test
	@DisplayName("ふつうの使い方は通る：IN のリスト・素の配列、1つの値、バイナリ")
	void ordinaryValues () {

		assertEquals(List.of(1, 2, 3), select(ID.in(List.of(1, 2, 3))).params());
		assertEquals(List.of(1L, 2L), select(ID.in(new long[] {1L, 2L})).params());
		assertEquals(List.of("a", 1), SQL.update(TestSchema.Site.instance()).set(NAME, "a").where(ID.eq(1)).params());

		// バイナリは1つの値。かつては1バイトずつに平らにされ、値が増えてずれていた
		byte[] binary = {1, 2, 3};
		List<Object> params = SQL.update(TestSchema.Site.instance()).set(NAME, binary).where(ID.eq(1)).params();
		assertEquals(2, params.size(), params.toString());
		assertTrue(params.getFirst() == binary, params.toString());

	}

	private static SelectBuilder select (io.jimble.db.sql.query.where.IWhere where) {

		return SQL.select(ID).from(TestSchema.Site.instance()).where(where);

	}

}
