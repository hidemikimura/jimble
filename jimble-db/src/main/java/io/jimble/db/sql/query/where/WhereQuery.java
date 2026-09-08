package io.jimble.db.sql.query.where;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.where.condition.*;

import java.util.ArrayList;
import java.util.List;

/**
 * where
 */
public class WhereQuery implements IWhere {

	/* whereリスト */
	private final List<IWhere> whereList = new ArrayList<>();

	/**
	 * コンストラクタ
	 *
	 * @param where	IWhere
	 */
	public WhereQuery (IWhere where) {

		whereList.add(new WhereQueryInner().left(where));

	}

	/**
	 * コンストラクタ
	 *
	 * @param dsl	IDsl
	 */
	public WhereQuery (IDsl dsl) {

		whereList.add(new WhereQueryInner().right(dsl));

	}

	/* 結合演算子 */
	private String logicalOperator = null;

	/**
	 * 結合演算子
	 *
	 * @param logicalOperator	結合演算子
	 */
	public WhereQuery logicalOperator (String logicalOperator) {

		this.logicalOperator = logicalOperator;
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere and(IWhere where) {

		whereList.add(new WhereQueryInner().left(where));
		whereList.getLast().and(null);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere or(IWhere where) {

		whereList.add(new WhereQueryInner().left(where));
		whereList.getLast().or(null);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere eq(Object value) {

		whereList.getLast().eq(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not(Object value) {

		whereList.getLast().not(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere gt(Object value) {

		whereList.getLast().gt(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere lt(Object value) {

		whereList.getLast().lt(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere ge(Object value) {

		whereList.getLast().ge(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere le(Object value) {

		whereList.getLast().le(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere is_null() {

		whereList.getLast().is_null();
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere is_not_null() {

		whereList.getLast().is_not_null();
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere between(Object value1, Object value2) {

		whereList.getLast().between(value1, value2);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere like(Object value) {

		whereList.getLast().like(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not_like(Object value) {

		whereList.getLast().not_like(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere contains(Object value) {

		whereList.getLast().contains(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere starts_with(Object value) {

		whereList.getLast().starts_with(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere ends_with(Object value) {

		whereList.getLast().ends_with(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere in(Object value) {

		whereList.getLast().in(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere not_in(Object value) {

		whereList.getLast().not_in(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWhere exists(Object value) {

		whereList.getLast().exists(value);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String logicalOperator() {

		return logicalOperator;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void whereSql (SqlWriter sb) {

		for (IWhere where : whereList) {
			where.whereSql(sb);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		for (IWhere where : whereList) {
			if (where.hasParameter()) {
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
		for (IWhere where : whereList) {
			if (where.hasParameter()) {
				params.add(where.getParameter());
			}
		}
		return params;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>この括弧の中に OR があれば、中身をまるごと捨てる。</b>
	 * {@code a = 1 OR b = 2} から {@code a = 1} だけを取り出すと、
	 * 「a = 1 の行に絞り込めている」と読み違える。
	 * </p>
	 */
	@Override
	public void terms (WhereTerms terms) {

		WhereTerms group = new WhereTerms();

		for (IWhere where : whereList) {
			where.terms(group);
		}

		if (group.hasOr()) {
			return;
		}

		terms.addAll(group.terms());

	}

	/**
	 * where
	 */
	static class WhereQueryInner implements IWhere {

		/* AND,OR */
		private String logicalOperator = null;

		/* left */
		private IWhere left = null;

		/* right */
		private Object right = null;

		/**
		 * left
		 *
		 * @param left	IWhere
		 * @return	IWhere
		 */
		public IWhere left (IWhere left) {

			this.left = left;
			return this;

		}

		/**
		 * right
		 *
		 * @param right IDsl
		 * @return  IWhere
		 */
		public IWhere right (IDsl right) {

			this.right = right;
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere and(IWhere where) {

			this.logicalOperator = "AND";
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere or(IWhere where) {

			this.logicalOperator = "OR";
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere eq(Object value) {

			this.right = new Eq(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere not(Object value) {

			this.right = new Not(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere gt(Object value) {

			this.right = new Gt(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere lt(Object value) {

			this.right = new Lt(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere ge(Object value) {

			this.right = new Ge(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere le(Object value) {

			this.right = new Le(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere is_null() {

			this.right = new IsNull();
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere is_not_null() {

			this.right = new IsNotNull();
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere between(Object value1, Object value2) {

			this.right = new BetWeen(value1, value2);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere like(Object value) {

			this.right = new Like(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere not_like(Object value) {

			this.right = new NotLike(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere contains(Object value) {

			this.right = new Contains(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere starts_with(Object value) {

			this.right = new StartsWith(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere ends_with(Object value) {

			this.right = new EndsWith(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere in(Object value) {

			this.right = new In(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere not_in(Object value) {

			this.right = new NotIn(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IWhere exists(Object value) {

			this.right = new Exists(value);
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String logicalOperator() {

			return logicalOperator;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void whereSql (SqlWriter sb) {

			if (this.logicalOperator != null && !this.logicalOperator.isEmpty()) {
				sb.append(" ");
				sb.append(this.logicalOperator);
			}

			if (this.left != null) {
				sb.append(" ");
				if (this.left instanceof WhereQuery whereQuery
					&& whereQuery.whereList.size() > 1) {
					sb.append("(");
					this.left.whereSql(sb);
					sb.append(")");
				} else {
					this.left.whereSql(sb);
				}
			}

			if (this.right != null) {
				if (this.right instanceof ICondition condition) {
					condition.conditionSql(sb);
				} else if (this.right instanceof IDsl dsl) {
					dsl.dslSql(sb);
				} else if (this.right instanceof ISelect select) {
					select.selectSql(sb);
				} else if (this.right instanceof IWhere where) {
					where.whereSql(sb);
				}
			}

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean hasParameter() {

			if (this.left != null && this.left.hasParameter()) {
				return true;
			}

			if (this.right != null) {
				if (this.right instanceof IWhere where) {
					return where.hasParameter();
				} else if (this.right instanceof ICondition condition) {
					return condition.hasParameter();
				} else if (this.right instanceof IDsl dsl) {
					return dsl.hasParameter();
				} else if (this.right instanceof ISelect select) {
					return select.hasParameter();
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

			if (this.left != null && this.left.hasParameter()) {
				params.add(this.left.getParameter());
			}

			if (this.right != null) {
				if (this.right instanceof IWhere where) {
					if (where.hasParameter()) {
						params.add(where.getParameter());
					}
				} else if (this.right instanceof ICondition condition) {
					if (condition.hasParameter()) {
						params.add(condition.getParameter());
					}
				} else if (this.right instanceof IDsl dsl) {
					if (dsl.hasParameter()) {
						params.add(dsl.getParameter());
					}
				} else if (this.right instanceof ISelect select) {
					if (select.hasParameter()) {
						params.add(select.getParameter());
					}
				}
			}

			return params;

		}


		/**
		 * {@inheritDoc}
		 */
		@Override
		public void terms (WhereTerms terms) {

			if ("OR".equals(this.logicalOperator)) {
				terms.markOr();
				return;
			}

			// 列 = 値 / 列 IN (...)
			if (this.left instanceof IColumn column && this.right instanceof ICondition condition) {
				terms.add(column, condition.operator(), condition.conditionValue());
				return;
			}

			// 入れ子の括弧
			if (this.left != null && this.right == null) {
				this.left.terms(terms);
			}

			/*
			 * それ以外（DSL・サブクエリ・全文検索）は読まない。
			 * 読めないものは「絞り込めていない」扱いになるだけで、
			 * 安全側（テーブルごと消す）に倒れる。
			 */

		}

	}

}