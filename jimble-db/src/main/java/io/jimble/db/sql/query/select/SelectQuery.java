package io.jimble.db.sql.query.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.IDsl;

import java.util.ArrayList;
import java.util.List;

/**
 * select
 */
public class SelectQuery implements ISelect {

	/* ISelect */
	private ISelect select = null;

	/**
	 * ISelect
	 *
	 * @param select	ISelect
	 * @return	ISelect
	 */
	public ISelect select(ISelect select) {

		this.select = select;
		return this;

	}

	/* as */
	private String as = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect as(String as) {

		this.as = as;
		return this;

	}

	/* DSL */
	private IDsl dsl = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect dsl(IDsl dsl) {

		this.dsl = dsl;
		return this;

	}

	/**
	 * DSL（要件 F-D-31）
	 *
	 * <p>ウィンドウ関数が中身を取り出すのに使う。</p>
	 *
	 * @return	DSL。無ければ null
	 */
	public IDsl dsl () {

		return dsl;

	}

	/**
	 * 別名や計算が付いているか（要件 F-D-31）
	 *
	 * <p>
	 * ウィンドウ関数が「中の関数だけ」を取り出してよいかの判断に使う。
	 * 付いているのに捨てると<b>黙って別の値</b>になる。
	 * </p>
	 *
	 * @return	付いている場合 = true
	 */
	public boolean hasDecoration () {

		return as != null || !operations.isEmpty();

	}

	/*
	 * 四則演算（書いた順）。
	 *
	 * <b>1.4 までは種類ごとに1つずつ持ち、else-if で最初の1つだけを出していた</b>ので、
	 * {@code col.plus(1).multiply(2)} は<b>黙って {@code col + ?} になり、2 は捨てられていた</b>。
	 * 同じ種類を2度書くと、前のが上書きされていた（要件 D-190）。
	 */
	private record Operation (String operator, Object operand) {}

	private final List<Operation> operations = new ArrayList<>();

	/**
	 * 演算を足す
	 *
	 * @param operator	演算子
	 * @param value		値
	 * @return	ISelect
	 */
	private ISelect operation (String operator, Object value) {

		this.operations.add(new Operation(operator, value));
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect plus(Object value) {

		return operation(" + ", value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect minus(Object value) {

		return operation(" - ", value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect multiply(Object value) {

		return operation(" * ", value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISelect divide(Object value) {

		return operation(" / ", value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Deprecated(since = "1.5.0", forRemoval = true)
	@SuppressWarnings("removal")
	public ISelect subtract(Object value) {

		return divide(value);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 演算が2つ以上あるときは、<b>書いた順に左から括弧でくくる</b>——
	 * {@code col.plus(1).multiply(2)} は {@code (col + ?) * ?}。
	 * SQL の優先順位（掛け算が先）に任せると、書いた順と違う値になる。
	 * 1つだけのときは括弧を付けない（1.4 と同じ字面。結果キャッシュの鍵が変わらない）。
	 * </p>
	 */
	@Override
	public void selectSql (SqlWriter sb) {

		for (int i = 1; i < operations.size(); i++) {
			sb.append("(");
		}

		if (this.dsl == null) {
			this.select.selectSql(sb);
		} else {
			this.dsl.dslSql(sb);
		}

		for (int i = 0; i < operations.size(); i++) {
			if (i > 0) {
				sb.append(")");
			}
			Operation operation = operations.get(i);
			sb.append(operation.operator());
			Object operand = operation.operand();
			if (operand instanceof IColumn column) {
				sb.qualified(column);
			} else if (operand instanceof IDsl d) {
				d.dslSql(sb);
			} else if (operand instanceof ISelect s) {
				s.selectSql(sb);
			} else {
				sb.append("?");
			}
		}

		if (this.as != null && !this.as.isEmpty()) {
			sb.append(" AS ");
			sb.identifier(this.as);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (this.dsl == null) {
			if (this.select.hasParameter()) {
				return true;
			}
		} else {
			if (this.dsl.hasParameter()) {
				return true;
			}
		}

		List<Object> params = new ArrayList<>();
		for (Operation operation : operations) {
			addParam(params, operation.operand());
		}

		return !params.isEmpty();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();
		if (this.dsl == null) {
			if (this.select.hasParameter()) {
				params.add(this.select.getParameter());
			}
		} else {
			if (this.dsl.hasParameter()) {
				params.add(this.dsl.getParameter());
			}
		}

		for (Operation operation : operations) {
			addParam(params, operation.operand());
		}

		return params;

	}

	/**
	 * パラメータ追加
	 *
	 * @param params	パラメータ一覧
	 * @param value		値
	 */
	private void addParam (List<Object> params, Object value) {

		if (value instanceof IDsl d) {
			if (d.hasParameter()) {
				params.add(d.getParameter());
			}
		} else if (value instanceof ISelect s) {
			if (s.hasParameter()) {
				params.add(s.getParameter());
			}
		} else {
			params.add(value);
		}

	}

}
