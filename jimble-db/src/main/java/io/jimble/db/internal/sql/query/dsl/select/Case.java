package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.select.SelectQuery;
import io.jimble.db.sql.query.select.SelectValue;
import io.jimble.db.sql.query.where.IWhere;

import java.util.ArrayList;
import java.util.List;

/**
 * case
 */
public class Case implements IDsl {

	/* When Then List */
	private final List<WhenThen> whenThenList = new ArrayList<>();

	/* Else */
	private ISelect caseElse = null;

	/**
	 * when
	 *
	 * @param where IWhere
	 * @return  Case
	 */
	public Case when (IWhere where) {

		WhenThen whenThen = new WhenThen();
		whenThen.when(where);
		whenThenList.add(whenThen);
		return this;

	}

	/**
	 * then
	 *
	 * @param select    ISelect
	 * @return  Case
	 */
	public Case then (ISelect select) {

		whenThenList.getLast().then(select);
		return this;

	}

	/**
	 * then
	 *
	 * @param value    値
	 * @return  Case
	 */
	public Case then (String value) {

		whenThenList.getLast().then(new SelectValue().value(value));
		return this;

	}

	/**
	 * then
	 *
	 * @param value    値
	 * @return  Case
	 */
	public Case then (long value) {

		whenThenList.getLast().then(new SelectValue().value(value));
		return this;

	}

	/**
	 * then
	 *
	 * @param value    値
	 * @return  Case
	 */
	public Case then (double value) {

		whenThenList.getLast().then(new SelectValue().value(value));
		return this;

	}

	/**
	 * else
	 *
	 * @param select    ISelect
	 * @return  Case
	 */
	public Case elseCase (ISelect select) {

		caseElse = select;
		return this;

	}

	/**
	 * else
	 *
	 * @param value    値
	 * @return  Case
	 */
	public Case elseCase (String value) {

		caseElse = new SelectValue().value(value);
		return this;

	}

	/**
	 * else
	 *
	 * @param value    値
	 * @return  Case
	 */
	public Case elseCase (long value) {

		caseElse = new SelectValue().value(value);
		return this;

	}

	/**
	 * else
	 *
	 * @param value    値
	 * @return  Case
	 */
	public Case elseCase (double value) {

		caseElse = new SelectValue().value(value);
		return this;

	}

	/**
	 * end
	 *
	 * @return  ISelect
	 */
	public ISelect end () {

		return new SelectQuery().dsl(this);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append("CASE");
		for (WhenThen whenThen : whenThenList) {
			sb.append(" ");
			whenThen.sql(sb);
		}

		if (caseElse != null) {
			sb.append(" ELSE ");
			caseElse.selectSql(sb);
		}

		sb.append(" END");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (caseElse != null && caseElse.hasParameter()) {
			return true;
		}

		for (WhenThen whenThen : whenThenList) {
			if (whenThen.hasParameter()) {
				return true;
			}
		}

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();

		for (WhenThen whenThen : whenThenList) {
			if (whenThen.hasParameter()) {
				params.add(whenThen.getParameter());
			}
		}

		if (caseElse != null && caseElse.hasParameter()) {
			params.add(caseElse.getParameter());
		}

		return params;

	}

	/* When Then */
	private static class WhenThen {

		/* When */
		private IWhere when;

		/* Then */
		private ISelect then;

		/**
		 * When
		 *
		 * @param when  When
		 */
		public void when (IWhere when) {

			this.when = when;

		}

		/**
		 * Then
		 *
		 * @param then  Then
		 */
		public void then (ISelect then) {

			this.then = then;

		}

		/**
		 * SQL
		 *
		 * @param sb    StringBuilder
		 */
		public void sql (SqlWriter sb) {

			sb.append("WHEN ");
			when.whereSql(sb);
			sb.append(" THEN ");
			then.selectSql(sb);

		}

		/**
		 * パラメータ存在判定
		 *
		 * @return  存在する場合 = true
		 */
		public boolean hasParameter() {

			if (when.hasParameter()) {
				return true;
			}

			if (then.hasParameter()) {
				return true;
			}

			return false;

		}

		/**
		 * パラメータを取得する
		 *
		 * @return  パラメータ
		 */
		public Object getParameter() {

			List<Object> params = new ArrayList<>();

			if (when.hasParameter()) {
				params.add(when.getParameter());
			}

			if (then.hasParameter()) {
				params.add(then.getParameter());
			}

			return params;

		}

	}

}
