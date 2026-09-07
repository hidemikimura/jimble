package io.jimble.db.sql.definition.table;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.util.data.definition.ISchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Table#getColumnList()} のテスト（D-17）
 *
 * <p>DB に触らないので通常の {@code build} で走る。</p>
 */
class TableColumnListTest {

	/** スキーマ */
	private static class TestSchema extends AbstractSchema {

		@Override
		public String name () {

			return "test_schema";

		}

	}

	// region declareColumns() を実装したテーブル（生成コードと同じ形）

	/** 列一覧を静的に宣言するテーブル */
	private static class DeclaredTable extends Table {

		static final Column id = new Column(instance(), "id", long.class, false, null, true);

		static final Column name = new Column(instance(), "name", String.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(id, name);

		@Override
		protected List<Column> declareColumns () {

			return COLUMNS;

		}

		DeclaredTable (ISchema schema, String tableName) {

			super(schema, tableName);

		}

		static DeclaredTable instance () {

			return new DeclaredTable(new TestSchema(), "declared");

		}

	}

	@Test
	@DisplayName("declareColumns() を実装していればそれが使われる（宣言順）")
	void declaredColumns () {

		List<Column> columns = DeclaredTable.instance().getColumnList();

		assertEquals(List.of("id", "name"), columns.stream().map(Column::name).toList());

	}

	// endregion

	// region declareColumns() を実装していないテーブル（手書き定義）

	/** 非公開クラス・非公開フィールドのテーブル */
	private static class ReflectedTable extends Table {

		private static final Column id = new Column(instance(), "id", long.class, false, null, true);

		private static final Column code = new Column(instance(), "code", String.class, false, null, false);

		ReflectedTable (ISchema schema, String tableName) {

			super(schema, tableName);

		}

		static ReflectedTable instance () {

			return new ReflectedTable(new TestSchema(), "reflected");

		}

	}

	@Test
	@DisplayName("実装していなければリフレクションで集める。非公開でも読める")
	void reflectedColumns () {

		// 移送元は非公開フィールドで IllegalAccessException になり、
		// それを Log.error に落として空のリストを返していた（D-17）
		List<Column> columns = ReflectedTable.instance().getColumnList();

		assertEquals(List.of("id", "code"), columns.stream().map(Column::name).toList());

	}

	// endregion

}
