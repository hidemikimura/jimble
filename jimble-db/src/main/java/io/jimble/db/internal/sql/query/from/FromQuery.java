package io.jimble.db.internal.sql.query.from;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.ITable;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereTerms;

import java.util.ArrayList;
import java.util.List;

/**
 * from
 */
public class FromQuery implements IFrom {

	/* fromリスト */
	private final List<IFrom> fromList = new ArrayList<>();

	/**
	 * コンストラクタ
	 *
	 * @param from	IFrom
	 */
	public FromQuery(IFrom from) {

		this.fromList.add(new FromQueryInner().from(from));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IFrom inner(IFrom from) {

		this.fromList.add(new FromQueryInner().inner(from));
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IFrom left(IFrom from) {

		this.fromList.add(new FromQueryInner().left(from));
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IFrom on(IWhere...where) {

		this.fromList.getLast().on(where);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void fromSql (SqlWriter sb) {

		for (IFrom from : fromList) {
			from.fromSql(sb);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		for (IFrom from : fromList) {
			if (from.hasParameter()) {
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

		List<Object> parameters = new ArrayList<>();
		for (IFrom from : fromList) {
			if (from.hasParameter()) {
				parameters.add(from.getParameter());
			}
		}
		return parameters;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ITable> getTableList() {

		List<ITable> tableList = new ArrayList<>();
		for (IFrom from : fromList) {
			tableList.addAll(from.getTableList());
		}
		return tableList;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTerms (WhereTerms terms) {

		for (IFrom from : fromList) {
			from.onTerms(terms);
		}

	}

	/**
	 * from
	 */
	static class FromQueryInner implements IFrom {

		/* from */
		private IFrom from = null;

		/* join種別(0=inner, 1=left) */
		private int joinType = 0;

		/* join */
		private IFrom join = null;

		/* where */
		private IWhere[] where = null;

		/**
		 * from
		 *
		 * @param from	IFrom
		 * @return	IFrom
		 */
		public IFrom from (IFrom from) {

			this.from = from;
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IFrom inner(IFrom from) {

			this.joinType = 0;
			this.join = from;
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IFrom left(IFrom from) {

			this.joinType = 1;
			this.join = from;
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IFrom on(IWhere...where) {

			this.where = where;
			return this;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void fromSql (SqlWriter sb) {

			if (from != null) {

				from.fromSql(sb);

			} else if (join != null) {

				if (joinType == 0) {
					sb.append(" INNER JOIN");
				} else if (joinType == 1) {
					sb.append(" LEFT JOIN");
				}

				join.fromSql(sb);

			}

			if (where != null && where.length > 0) {
				sb.append(" ON (");
				for (int i = 0; i < where.length; i++) {
					if (i > 0) {
						sb.append(" AND ");
					}
					sb.append("(");
					where[i].whereSql(sb);
					sb.append(")");
				}
				sb.append(")");
			}

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean hasParameter() {

			if (from != null) {

				return from.hasParameter();

			} else if (join != null) {

				if (join.hasParameter()) {
					return true;
				}

				if (where != null) {
					for (IWhere w : where) {
						if (w.hasParameter()) {
							return true;
						}
					}
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

			if (from != null) {

				if (from.hasParameter()) {
					params.add(from.getParameter());
				}

			} else if (join != null) {

				if (join.hasParameter()) {
					params.add(join.getParameter());
				}

				if (where != null) {
					for (IWhere w : where) {
						if (w.hasParameter()) {
							params.add(w.getParameter());
						}
					}
				}

			}

			return params;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public List<ITable> getTableList() {

			List<ITable> tableList = new ArrayList<>();
			if (from != null) {
				tableList.addAll(from.getTableList());
			} else if (join != null) {
				tableList.addAll(join.getTableList());
			}
			return tableList;

		}


		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onTerms (WhereTerms terms) {

			if (where == null) {
				return;
			}

			// ON (...) は AND で並べて出しているので、そのまま読む
			for (IWhere w : where) {
				w.terms(terms);
			}

		}

	}

}