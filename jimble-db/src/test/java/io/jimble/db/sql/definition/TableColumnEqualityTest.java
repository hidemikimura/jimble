package io.jimble.db.sql.definition;

import io.jimble.db.sql.TestSchema;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.definition.table.TemporaryTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同じテーブル・同じ列かの見分け（D-175。要件 F-D-06）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>生成物の {@code instance()} は呼ぶたびに新しいものを返す</b>（D-174。
 * そうしないとクラス初期化の輪で {@code null} を掴む）。
 * だから<b>同一性で比べると、同じテーブルが別物になる</b>——
 * {@code Staff.id.table()} と {@code Staff.instance()} が等しくない、という形で出る。
 * </p>
 *
 * <p>
 * <b>これは「動かない」ではなく「静かに外れる」不具合である。</b>
 * 例外は出ない。外れるのは<b>「同じテーブルか」を見ているところ</b>だけで、
 * SQL は組み上がるし、値も返る。
 * </p>
 */
class TableColumnEqualityTest {

	/**
	 * 列が持つテーブルと、{@code instance()} が返すテーブルが等しいこと
	 *
	 * <p><b>ここが棚卸し（3.10）で挙がっていた形そのものである。</b></p>
	 */
	@Test
	@DisplayName("D-175 列のテーブルと instance() が等しい")
	void columnTableEqualsInstance () {

		Table fromColumn = (Table) TestSchema.Site.id.table();
		Table fromInstance = TestSchema.Site.instance();

		assertNotSame(fromColumn, fromInstance, "前提が変わっている（instance() が同じものを返している）");
		assertEquals(fromColumn, fromInstance, "同じテーブルなのに等しくない");

	}

	/** {@code instance()} を2回呼んだものが等しいこと */
	@Test
	@DisplayName("D-175 instance() を2回呼んでも等しい")
	void twoInstancesAreEqual () {

		assertNotSame(TestSchema.Site.instance(), TestSchema.Site.instance());
		assertEquals(TestSchema.Site.instance(), TestSchema.Site.instance());
		assertEquals(TestSchema.Site.instance().hashCode(), TestSchema.Site.instance().hashCode());

	}

	/**
	 * 別のテーブルとは等しくないこと
	 *
	 * <p><b>等しくなるほうを書き忘れると、全部が同じテーブルになる。</b></p>
	 */
	@Test
	@DisplayName("D-175 別のテーブルとは等しくない")
	void differentTablesAreNotEqual () {

		assertNotEquals(TestSchema.Site.instance(), TestSchema.Feed.instance());

	}

	/** 同じ列は等しく、別の列は等しくないこと */
	@Test
	@DisplayName("D-175 同じ列は等しく、別の列は等しくない")
	void columnEquality () {

		Column id = new Column(TestSchema.Site.instance(), "id", long.class, false, null, true);

		assertEquals(TestSchema.Site.id, id, "同じ列なのに等しくない");
		assertEquals(TestSchema.Site.id.hashCode(), id.hashCode());

		assertNotEquals(TestSchema.Site.id, TestSchema.Site.group_id, "別の列が等しい");
		assertNotEquals(TestSchema.Site.id, TestSchema.Feed.id, "別のテーブルの同じ名前の列が等しい");

	}

	/**
	 * 型や PK の違いは、どの列かを変えないこと
	 *
	 * <p>
	 * <b>それらは「同じ列の性質」であって、どの列かを決めるものではない。</b>
	 * 型まで見ると、<b>定義を1つ直しただけで別の列になる</b>。
	 * </p>
	 */
	@Test
	@DisplayName("D-175 型や PK が違っても、同じ名前の同じ列は等しい")
	void attributesDoNotChangeIdentity () {

		Column loose = new Column(TestSchema.Site.instance(), "id", String.class, true, "x", false);

		assertEquals(TestSchema.Site.id, loose, "型や PK でどの列かが変わっている");

	}

	/**
	 * 仮テーブル・仮列とは等しくならないこと
	 *
	 * <p>
	 * <b>名前だけで作ったものと、定義から来たものを混ぜない。</b>
	 * 仮のほうはスキーマが {@code empty} なので、そこで分かれる。
	 * {@code where(Data)} は文字列から仮列を組み立てるので、
	 * <b>ここが等しくなると「定義に無い列」が定義済みとして通る</b>。
	 * </p>
	 */
	@Test
	@DisplayName("D-175 仮テーブル・仮列とは等しくならない")
	void temporaryIsNotEqual () {

		assertNotEquals(TestSchema.Site.instance(), new TemporaryTable("site"));
		assertNotEquals(TestSchema.Site.id, new TemporaryColumn("site", "id"));

	}

	/**
	 * 鍵として使えること
	 *
	 * <p>
	 * <b>ここが効く場所である。</b>{@code ValidationRules} は
	 * {@code Map<Column, ValidationRule>} で規則を持つ。
	 * 等しさが同一性のままだと、<b>同じ列に2つ規則が入り、エラーが2件出る</b>。
	 * </p>
	 */
	@Test
	@DisplayName("D-175 別のインスタンスでも同じ鍵になる")
	void worksAsAKey () {

		Map<Column, String> map = new HashMap<>();
		map.put(TestSchema.Site.id, "ひとつめ");
		map.put(new Column(TestSchema.Site.instance(), "id", long.class, false, null, true), "ふたつめ");

		assertEquals(1, map.size(), "同じ列が2つの鍵になっている: " + map);
		assertEquals("ふたつめ", map.get(TestSchema.Site.id));

		Set<Table> tables = new HashSet<>();
		tables.add(TestSchema.Site.instance());
		tables.add(TestSchema.Site.instance());

		assertEquals(1, tables.size(), "同じテーブルが2つ入っている");

	}

	/** equals の約束（自分自身・null・別の型） */
	@Test
	@DisplayName("D-175 equals の約束を守る")
	void equalsContract () {

		Table table = TestSchema.Site.instance();

		assertEquals(table, table);
		assertNotEquals(null, table);
		assertNotEquals("site", table);

		// 対称
		Table other = TestSchema.Site.instance();
		assertTrue(table.equals(other) && other.equals(table), "対称になっていない");

	}

	/** toString は変わっていないこと（生成物の SchemaSQL が文字列連結で使う） */
	@Test
	@DisplayName("D-175 toString は変えていない")
	void toStringIsUnchanged () {

		assertEquals("site", TestSchema.Site.instance().toString());
		assertEquals("site.id", TestSchema.Site.id.toString());

	}

}
