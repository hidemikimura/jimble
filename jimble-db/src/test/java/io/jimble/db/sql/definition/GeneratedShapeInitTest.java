package io.jimble.db.sql.definition;

import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SqlBuildException;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.data.definition.ISchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成物のクラス初期化が輪になっても壊れないこと（D-174。要件 F-G-03）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>生成物は2つのクラスが互いを呼ぶ。</b>
 * スキーマクラスはテーブルの定数を持ち（{@code Oreteki.group = Group.instance()}）、
 * テーブルクラスは自分を作るのにスキーマを {@code new} する
 * （{@code new Group(new Oreteki(), "group")}）。
 * </p>
 *
 * <p>
 * <b>テーブルクラスを先に触ると、この輪が回る。</b>
 * </p>
 *
 * <pre>
 * Group.&lt;clinit&gt;  → new Oreteki()          スキーマの clinit を起こす
 *   Oreteki.&lt;clinit&gt; → Group.instance()     Group はまだ初期化の途中
 *   Oreteki.group = ???                       ← ここが何になるか
 * </pre>
 *
 * <p>
 * <b>{@code instance()} が共有の静的フィールドを返す形だと、ここが {@code null} になる。</b>
 * 初期化の途中なので、まだ代入されていないためである。
 * <b>例外は出ない。</b>出るのは、その {@code null} を {@code from(...)} に渡した SQL だけで、
 * しかもそれは<b>「SELECT と FROM が消えた SQL」</b>という形をしている。
 * </p>
 *
 * <p>
 * <b>どちらのクラスを先に触るかで結果が変わる</b>ので、
 * スキーマクラスから書き始めたアプリでは再現しない。
 * ここは<b>テーブルクラスを先に触る順序</b>を固定する。
 * </p>
 *
 * <p>
 * ここで固定していないこと：{@code Table} / {@code Column} の {@code equals}
 * （いまも同一性のままで、{@code Group.id.table()} は {@code Group.instance()} と等しくない）。
 * </p>
 */
class GeneratedShapeInitTest {

	// region 生成物と同じ形

	/** スキーマクラス（生成物と同じ形） */
	static final class Oreteki extends AbstractSchema {

		/** テーブルの定数。生成物はこの形で持つ */
		static final Table group = Group.instance();

		@Override
		public String name () { return "oreteki"; }

	}

	/** テーブルクラス（生成物と同じ形） */
	static final class Group extends Table {

		/** 列。生成物はこの形で持つ */
		static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** 列 */
		static final Column deleted_at = new Column(instance(), "deleted_at", java.util.Date.class, true, null, false);

		Group (ISchema schema, String name) { super(schema, name); }

		static Group instance () { return new Group(new Oreteki(), "group"); }

	}

	// endregion

	/**
	 * テーブルクラスを先に触っても、スキーマクラスの定数が null にならないこと
	 *
	 * <p>
	 * <b>{@code Group.deleted_at} を先に読むのが「テーブルクラスを先に触る」である。</b>
	 * アプリでは {@code SQL.select().from(Oreteki.group).where(Group.deleted_at.is_null())}
	 * のような1行の中でも、先に評価されたほうが先に初期化される。
	 * </p>
	 */
	@Test
	@DisplayName("D-174 テーブルクラスを先に触っても、スキーマの定数が null にならない")
	void schemaConstantIsNotNullWhenTableLoadsFirst () {

		// テーブルクラスを先に初期化させる
		assertNotNull(Group.deleted_at, "列が null");

		assertNotNull(Oreteki.group
			, "スキーマの定数が null（クラス初期化が輪になって、途中の静的フィールドを読んでいる）");

	}

	/** その状態で組み立てた SQL が、ちゃんと SELECT と FROM を持つこと */
	@Test
	@DisplayName("D-174 SELECT と FROM が消えない")
	void selectAndFromAreNotLost () {

		String sql = SQL.select()
			.from(Oreteki.group)
			.where(Group.deleted_at.is_null())
			.orderBy(Group.id.asc())
			.sql(Dialects.of("mysql"));

		assertTrue(sql.startsWith("SELECT "), "SELECT の中身が消えている:\n" + sql);
		assertTrue(sql.contains(" FROM "), "FROM が消えている:\n" + sql);
		assertTrue(sql.contains("deleted_at"), sql);

	}

	/**
	 * それでも null が来たら、その場で落ちること
	 *
	 * <p>
	 * <b>ここが最後の砦である。</b>初期化の輪は生成物の形を変えれば直るが、
	 * <b>「null を渡すと黙って壊れた SQL が出来る」</b>ほうは、
	 * 別の理由で null になったときにも同じことを起こす。
	 * </p>
	 */
	@Test
	@DisplayName("D-174 from(null) はその場で落ちる")
	void nullFromIsRefused () {

		assertThrows(SqlBuildException.class, () -> SQL.select().from(null));

	}

	/**
	 * 列も FROM も無いまま組み立てようとしたら落ちること
	 *
	 * <p>
	 * <b>「FROM が無い」だけでは落とさない。</b>列を名指ししてあれば
	 * {@code SELECT NOW()} のような文は成り立つので、
	 * <b>落とすのは、どちらも無くて何も出ないとき</b>だけである。
	 * </p>
	 */
	@Test
	@DisplayName("D-174 列も FROM も無い select は組み立てられない")
	void selectWithNothingIsRefused () {

		assertThrows(SqlBuildException.class
			, () -> SQL.select().sql(Dialects.of("mysql")));

	}

	/** 列を名指ししてあれば、FROM が無くても組み立てられること */
	@Test
	@DisplayName("D-174 列を名指しした FROM 無しの select は通る")
	void selectWithColumnsButNoFromIsAllowed () {

		String sql = SQL.select(Group.id).sql(Dialects.of("mysql"));

		assertTrue(sql.startsWith("SELECT "), sql);

	}

}
