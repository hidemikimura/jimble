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
import io.jimble.db.sql.query.where.IWhere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * update builder
 */
public class UpdateBuilder extends AbstractBuilder<UpdateBuilder> {

	/**
	 * コンストラクタ
	 *
	 * @param table	Table
	 */
	public UpdateBuilder (ITable table) {

		this.table = table;

	}

	// region Table

	/* Table */
	private ITable table = null;

	// endregion

	// region Set

	/* set */
	private final List<ISet> setList = new ArrayList<>();

	/**
	 * set
	 *
	 * @param column	column
	 * @param value		value
	 * @return	UpdateBuilder
	 */
	public UpdateBuilder set (IColumn column, Object value) {

		setList.add(new Set(column, value));
		return this;

	}

	/**
	 * 平らな行で入れる（要件 D-192）
	 *
	 * <pre>
	 * Data row = new Data();
	 * row.put("title", title);
	 * row.put("status", "draft");
	 * SQL.update(Post.instance()).setRow(row)...
	 * </pre>
	 *
	 * <p>
	 * キーは<b>このテーブルの列名</b>、値はそのまま入れる。{@code set(Data)} と違って
	 * 包む形（{@code {"set": {"テーブル名": ...}}}）は要らない
	 * （現在時刻は {@code Dsl.now()} を値に入れる）。値が {@code Data} / {@code Map}（入れ子）なら例外——
	 * 結果の Data をそのまま渡して、テーブル名のキーを列だと思って入れる事故を止める。
	 * </p>
	 *
	 * @param row	列名 → 値
	 * @return	UpdateBuilder
	 * @throws SqlBuildException	値に入れ子がある
	 * @since 1.5.0
	 */
	public UpdateBuilder setRow (Data row) {

		java.util.Objects.requireNonNull(row, "row");
		for (java.util.Map.Entry<String, Object> entry : row.entrySet()) {
			if (entry.getValue() instanceof java.util.Map<?, ?>) {
				throw new SqlBuildException(
					"setRow(Data) には平らな行を渡してください（" + entry.getKey() + " の値が入れ子です。"
						+ "結果の Data なら getData(テーブル) か flattenTable(テーブル) で平らにしてから）");
			}
			set(new TemporaryColumn(table, entry.getKey()), entry.getValue());
		}
		return this;

	}

	/**
	 * {@code set} 句（リクエストの JSON から組む）
	 *
	 * <p>
	 * 形は {@code {"set": {"テーブル名": {"列名": 値}}}}。
	 * <b>包むキーが無い・このテーブルの分が無い空でない Data は例外</b>（2.0。要件 D-194）——
	 * 1.x は黙って何もしなかった。平らな行なら {@link #setRow(Data)}。
	 * </p>
	 *
	 * <p>
	 * <b>値はそのまま入れる。</b>1.x は文字列 {@code "now()"} を SQL の {@code NOW()} に変えていた
	 * （利用者の入力 {@code "now()"} も現在時刻になった）。現在時刻は {@code Dsl.now()} を値に入れる。
	 * </p>
	 *
	 * @param data {@code {"set": {"テーブル名": {"列名": 値}}}}
	 * @return  UpdateBuilder
	 * @throws SqlBuildException	包むキーかテーブルの分が無い
	 */
	public UpdateBuilder set (Data data) {

		return setFrom(data, true);

	}

	/**
	 * {@code set} 句を Data から読む
	 *
	 * @param data		Data
	 * @param strict	包むキーが無ければ例外にするか（{@code apply} は false）
	 * @return	UpdateBuilder
	 */
	private UpdateBuilder setFrom (Data data, boolean strict) {

		if (strict) {
			requireWrapped(data, "set", "set(Data)", "setRow(Data)");
		}

		if (data == null || !data.containsKey("set")) {
			return this;
		}

		Data wrapped = data.getData("set");

		if (!wrapped.containsKey(table.name())) {
			if (strict && !wrapped.isEmpty()) {
				throw new SqlBuildException(
					"set(Data) の \"set\" に、このテーブル（" + table.name() + "）の分がありません（キー: " + wrapped.keySet() + "）");
			}
			return this;
		}

		Data tableData = wrapped.getDataOptional(table.name());

		for (String columnName : tableData.keySet()) {
			set(new TemporaryColumn(table, columnName), tableData.get(columnName));
		}

		return this;

	}

	// endregion

	// region WHERE

	/* WHERE句 */
	private final List<IWhere> whereList = new ArrayList<>();

	/* WHERE 無しを承知で組むか（D-173） */
	private boolean allRows = false;

	/**
	 * WHERE 無しで組むことを承知する（D-173）
	 *
	 * <p>
	 * <b>これを呼ばずに条件が1つも無いと、その場で落ちる。</b>
	 * 表を丸ごと 書き換える のは、たいてい書き間違いのほうである——
	 * {@code where(Data)} に知らない演算子を書いた、値が {@code null} で条件を足さなかった、
	 * 変数が空だった。<b>SQL は通るし、例外も出ない。</b>気づくのは、消えたあとである。
	 * </p>
	 *
	 * <p>本当に全行が対象なら、ここでそう言うこと。</p>
	 *
	 * @return	UpdateBuilder
	 */
	public UpdateBuilder allRows () {

		this.allRows = true;
		return this;

	}


	/**
	 * WHERE句
	 *
	 * @param where	WHERE句
	 * @return	UpdateBuilder
	 */
	public UpdateBuilder where(IWhere...where) {

		if (where == null || where.length == 0) {
			return this;
		}

		this.whereList.addAll(Arrays.asList(where));
		return this;

	}

	/**
	 * WHERE句
	 *
	 * <p>
 * 形は {@code {"where": {"テーブル名": {"列名|条件": 値}}}}。条件を省くと {@code eq}。
 * <b>1.4 までの Javadoc は {@code q} と書いていたが、読むのは {@code where} である</b>——
 * {@code q} で書くと条件が1つも付かない（要件 D-190）。
 * </p>
 *
 * @param data 条件（{@code where} キーの下）
	 * @return  UpdateBuilder
	 */
	public UpdateBuilder where (Data data) {

		// "where" キーの無い空でない Data は例外（1.x は条件が付かずに全件。要件 D-194）
		requireWrapped(data, "where", "where(Data)", null);

		for (IWhere w : whereList(data)) {
			where(w);
		}

		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String sql (io.jimble.db.dialect.Dialect dialect) {

		/*
		 * <b>条件が1つも無いなら落とす（D-173）。</b>
		 * {@link #allRows()} を呼んでいれば通す。
		 */
		if (whereList.isEmpty() && !allRows) {
			throw new SqlBuildException(
				"WHERE が1つもありません: %s（本当に全行なら allRows() を呼んでください）"
					.formatted(table.name()));
		}

		SqlWriter sb = new SqlWriter(dialect);

		// UPDATE句
		sb.append("UPDATE ");

		// テーブル
		sb.identifier(table.name());

		// SET句
		sb.append(" SET ");
		for (int i = 0; i < setList.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			setList.get(i).setSql(sb);
		}

		// WHERE句
		if (!whereList.isEmpty()) {
			sb.append(" WHERE ");
			for (int i = 0; i < whereList.size(); i++) {
				IWhere where = whereList.get(i);
				if (i > 0) {
					String logicalOperator = where.logicalOperator();
					if (logicalOperator == null || logicalOperator.isEmpty()) {
						sb.append(" AND ");
					} else {
						sb.append(" ");
						sb.append(logicalOperator);
						sb.append(" ");
					}
				}
				sb.append("(");
				where.whereSql(sb);
				sb.append(")");
			}
		}

		return sb.toString();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> params() {

		List<Object> params = new ArrayList<>();

		// set
		for (ISet set : setList) {
			if (set.hasParameter()) {
				params.add(set.getParameter());
			}
		}

		// where
		for (IWhere where : whereList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}

		return Parameter.flatten(params);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UpdateBuilder apply(Data data) {

		// まとめて読むので、無い句は無いまま（set(Data) の「包むキーが無ければ例外」は通さない）
		setFrom(data, false);
		for (IWhere w : whereList(data)) {
			where(w);
		}
		return this;

	}


	// region 内省（要件 F-D-28）

	/**
	 * 更新するテーブル
	 *
	 * @return	テーブル
	 */
	public ITable table () {

		return table;

	}

	/**
	 * WHERE
	 *
	 * @return	WHERE
	 */
	public List<IWhere> whereList () {

		return List.copyOf(whereList);

	}

	// endregion

}
