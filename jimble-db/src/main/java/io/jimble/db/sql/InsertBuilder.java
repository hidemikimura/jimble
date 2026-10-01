package io.jimble.db.sql;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.Data;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.internal.sql.query.parameter.Parameter;
import io.jimble.db.internal.sql.query.set.ISet;
import io.jimble.db.internal.sql.query.set.Set;
import io.jimble.db.internal.sql.query.value.IValue;
import io.jimble.db.internal.sql.query.value.Value;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;

/**
 * insert builder
 */
public class InsertBuilder extends AbstractBuilder<InsertBuilder> {

	/**
	 * コンストラクタ
	 *
	 * @param table	Table
	 */
	public InsertBuilder (ITable table) {

		this.table = table;

	}

	// region TABLE

	/* FROM句 */
	private ITable table = null;

	/**
	 * TBALE
	 *
	 * @return	TABLE
	 */
	public ITable getTable() {

		return table;

	}

	// endregion

	// region VALUE

	/* valueリスト */
	private final List<IValue> valueList = new ArrayList<>();

	/**
	 * VALUE句
	 *
	 * @param column	column
	 * @param value		value
	 * @return	InsertBuilder
	 */
	public InsertBuilder value (IColumn column, Object value) {

		valueList.add(new Value(column, value));
		return this;

	}

	/**
	 * 平らな行で入れる（要件 D-192）
	 *
	 * <pre>
	 * Data row = new Data();
	 * row.put("title", title);
	 * row.put("status", "draft");
	 * SQL.insert(Post.instance()).valueRow(row)...
	 * </pre>
	 *
	 * <p>
	 * キーは<b>このテーブルの列名</b>、値はそのまま入れる。{@code value(Data)} と違って
	 * 包む形（{@code {"value": {"テーブル名": ...}}}）は要らない
	 * （現在時刻は {@code Dsl.now()} を値に入れる）。値が {@code Data} / {@code Map}（入れ子）なら例外——
	 * 結果の Data をそのまま渡して、テーブル名のキーを列だと思って入れる事故を止める。
	 * </p>
	 *
	 * @param row	列名 → 値
	 * @return	InsertBuilder
	 * @throws SqlBuildException	値に入れ子がある
	 * @since 1.5.0
	 */
	public InsertBuilder valueRow (Data row) {

		java.util.Objects.requireNonNull(row, "row");
		for (java.util.Map.Entry<String, Object> entry : row.entrySet()) {
			if (entry.getValue() instanceof java.util.Map<?, ?>) {
				throw new SqlBuildException(
					"valueRow(Data) には平らな行を渡してください（" + entry.getKey() + " の値が入れ子です。"
						+ "結果の Data なら getData(テーブル) か flattenTable(テーブル) で平らにしてから）");
			}
			if (entry.getValue() instanceof java.util.Collection<?>
				|| (entry.getValue() != null && entry.getValue().getClass().isArray() && !(entry.getValue() instanceof byte[]))) {
				/*
				 * <b>リストは断る</b>（D-204）。平らにされて値だけが増え、後ろの WHERE のプレースホルダーとずれる。
				 * MariaDB のドライバは余った値を黙って捨てるので、<b>別の行を書き換えていた</b>
				 */
				throw new SqlBuildException(
					"列 " + entry.getKey() + " の値がリストです。1つの列には1つの値を渡してください");
			}
			value(new TemporaryColumn(table, entry.getKey()), entry.getValue());
		}
		return this;

	}

	/**
	 * {@code value} 句（リクエストの JSON から組む）
	 *
	 * <p>
	 * 形は {@code {"value": {"テーブル名": {"列名": 値}}}}。
	 * <b>包むキーが無い・このテーブルの分が無い空でない Data は例外</b>（2.0。要件 D-194）——
	 * 1.x は黙って何もしなかった。平らな行なら {@link #valueRow(Data)}。
	 * </p>
	 *
	 * <p>
	 * <b>値はそのまま入れる。</b>1.x は文字列 {@code "now()"} を SQL の {@code NOW()} に変えていた
	 * （利用者の入力 {@code "now()"} も現在時刻になった）。現在時刻は {@code Dsl.now()} を値に入れる。
	 * </p>
	 *
	 * @param value {@code {"value": {"テーブル名": {"列名": 値}}}}
	 * @return  InsertBuilder
	 * @throws SqlBuildException	包むキーかテーブルの分が無い
	 */
	public InsertBuilder value (Data value) {

		return valueFrom(value, true);

	}

	/**
	 * {@code value} 句を Data から読む
	 *
	 * @param data		Data
	 * @param strict	包むキーが無ければ例外にするか（{@code apply} は false）
	 * @return	InsertBuilder
	 */
	private InsertBuilder valueFrom (Data data, boolean strict) {

		if (strict) {
			requireWrapped(data, "value", "value(Data)", "valueRow(Data)");
		}

		if (data == null || !data.containsKey("value")) {
			return this;
		}

		Data wrapped = data.getData("value");

		if (!wrapped.containsKey(table.name())) {
			if (strict && !wrapped.isEmpty()) {
				throw new SqlBuildException(
					"value(Data) の \"value\" に、このテーブル（" + table.name() + "）の分がありません（キー: " + wrapped.keySet() + "）");
			}
			return this;
		}

		Data tableData = wrapped.getDataOptional(table.name());

		for (String columnName : tableData.keySet()) {
			value(new TemporaryColumn(table, columnName), tableData.get(columnName));
		}

		return this;

	}

	// endregion

	// region VALUE（列指定のみ）

	/* value(列指定のみ)リスト */
	private final List<IColumn> columnList = new ArrayList<>();

	/**
	 * VALUE句（列指定のみ）
	 *
	 * @param column	column
	 * @return	InsertBuilder
	 */
	public InsertBuilder value (IColumn...column) {

		columnList.addAll(Arrays.asList(column));
		return this;

	}

	// endregion

	// region SelectBuilder

	/* SelectBuilder */
	private SelectBuilder selectBuilder = null;

	/**
	 * VALUE句（Select）
	 *
	 * @param selectBuilder select builder
	 * @return  InsertBuilder
	 */
	public InsertBuilder value (SelectBuilder selectBuilder) {

		this.selectBuilder = selectBuilder;
		return this;

	}

	// endregion

	// region SET

	/* set */
	private final List<ISet> setList = new ArrayList<>();

	/**
	 * on duplicate key update
	 *
	 * @param column	column
	 * @param value		value
	 * @return	InsertBuilder
	 */
	public InsertBuilder onDuplicateKeyUpdate (IColumn column, Object value) {

		setList.add(new Set(column, value));
		return this;

	}

	/**
	 * on duplicate key update
	 *
	 * @param column	column
	 * @return	InsertBuilder
	 */
	public InsertBuilder onDuplicateKeyUpdateValues (IColumn column) {

		setList.add(new Set(column, Dsl.values(column)));
		return this;

	}

	// endregion

	// region ignore

	/* ignore */
	private boolean ignore = false;

	/**
	 * ignore
	 *
	 * @return	InsertBuilder
	 */
	public InsertBuilder ignore() {

		this.ignore = true;
		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String sql (io.jimble.db.dialect.Dialect dialect) {

		SqlWriter sb = new SqlWriter(dialect);

		// INSERT句
		sb.append("INSERT ");
		if (this.ignore) {
			// MySQL は INSERT IGNORE、PostgreSQL は末尾の ON CONFLICT DO NOTHING（要件 F-D-30）
			sb.append(sb.dialect().insertIgnorePrefix());
		}
		sb.append("INTO ");

		// テーブル
		sb.identifier(table.name());

		// COLUMN句
		sb.append(" (");
		if (!columnList.isEmpty()) {
			for (int i = 0; i < columnList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.identifier(columnList.get(i).name());
			}
		} else {
			for (int i = 0; i < valueList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				valueList.get(i).columnSql(sb);
			}
		}
		sb.append(")");

		// VALUE句
		if (selectBuilder != null) {
			sb.append(" ");
			sb.append(selectBuilder.sql(dialect));
		} else {
			sb.append(" VALUES (");
			for (int i = 0; i < valueList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				valueList.get(i).valueSql(sb);
			}
			sb.append(")");
		}

		/*
		 * 重複したら更新する（要件 F-D-30）。
		 *
		 * PostgreSQL は<b>どのキーで重複を見るかを書かせる</b>ので、
		 * テーブル定義の主キーを渡す。分からなければ例外になる
		 * （書けない SQL を組み立てて実行時に落とさない）。
		 */
		if (!setList.isEmpty()) {

			sb.append(sb.dialect().onDuplicateKeyUpdate(conflictKeys()));

			boolean isFirst = true;
			for (ISet set : setList) {
				if (!isFirst) {
					sb.append(", ");
				}
				isFirst = false;
				set.setSql(sb);
			}

		}

		// 重複を無視する（PostgreSQL はここに出る）
		if (this.ignore) {
			sb.append(sb.dialect().insertIgnoreSuffix());
		}

		return sb.toString();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> params() {

		List<Object> params = new ArrayList<>();

		// value
		for (IValue value : valueList) {
			if (value.hasParameter()) {
				params.add(value.getParameter());
			}
		}

		// select
		if (selectBuilder != null) {
			params.addAll(selectBuilder.params());
		}

		// set
		for (ISet set : setList) {
			if (set.hasParameter()) {
				params.add(set.getParameter());
			}
		}

		return Parameter.flatten(params);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InsertBuilder apply(Data data) {

		// まとめて読むので、無い句は無いまま（value(Data) の「包むキーが無ければ例外」は通さない）
		valueFrom(data, false);
		return this;

	}

	/**
	 * 重複を見るキー（要件 F-D-30）
	 *
	 * <p>主キーを使う。複合主キーなら全列。</p>
	 *
	 * @return	列名。分からなければ空
	 */
	private List<String> conflictKeys () {

		if (!(table instanceof io.jimble.db.sql.definition.table.Table target)) {
			return List.of();
		}

		/*
		 * MySQL の ON DUPLICATE KEY UPDATE は<b>どの一意キーでも</b>発火するが、
		 * PostgreSQL の ON CONFLICT は<b>どのキーで見るかを書かせる</b>。
		 *
		 * よくあるのは「auto_increment の主キー ＋ 業務上の一意キー」で、
		 * INSERT には主キーを入れない。ここで主キーしか見ないと
		 * <b>衝突を見つけられずに一意制約違反で落ちる</b>。
		 * 入れようとしている列で埋まっているキーを選ぶ。
		 */
		java.util.Set<String> inserting = insertingColumns();

		for (List<io.jimble.db.sql.definition.column.Column> key : target.getKeyList()) {

			if (key.isEmpty()) {
				continue;
			}

			List<String> names = new ArrayList<>();
			boolean covered = true;

			for (io.jimble.db.sql.definition.column.Column column : key) {
				names.add(column.name());
				if (!inserting.contains(column.name())) {
					covered = false;
				}
			}

			if (covered) {
				return names;
			}

		}

		// 埋まっているキーが無い。主キーで見るしかない（分からなければ空 = 例外）
		List<String> names = new ArrayList<>();

		for (io.jimble.db.sql.definition.column.Column column : target.getPrimaryKeyList()) {
			names.add(column.name());
		}

		return names;

	}

	/**
	 * この INSERT が値を入れる列
	 *
	 * @return	列名
	 */
	private java.util.Set<String> insertingColumns () {

		java.util.Set<String> names = new LinkedHashSet<>();

		for (IColumn column : columnList) {
			names.add(column.name());
		}

		for (IValue value : valueList) {
			names.add(value.column().name());
		}

		return names;

	}

	// region 内省（要件 F-D-28）

	/**
	 * 既存の行を書き換えうるか（{@code ON DUPLICATE KEY UPDATE}）
	 *
	 * <p>
	 * <b>これが true なら、INSERT でも既存の行が変わる。</b>
	 * SQL 結果のキャッシュは、その行だけを消すことができない
	 * （どの行に当たるかは入れてみるまで分からない）。
	 * </p>
	 *
	 * @return	書き換えうるなら true
	 */
	public boolean isUpsert () {

		return !setList.isEmpty();

	}

	/**
	 * {@code INSERT ... SELECT} か
	 *
	 * @return	そうなら true
	 */
	public boolean hasSelect () {

		return selectBuilder != null;

	}

	// endregion

}
