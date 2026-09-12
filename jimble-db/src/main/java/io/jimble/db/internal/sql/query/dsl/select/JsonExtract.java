package io.jimble.db.internal.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;

/* json_extract */
public class JsonExtract implements IDsl {

	/* field */
	private final ISelect field;

	/* json path */
	private final String jsonPath;

	/**
	 * コンストラクタ
	 *
	 * @param field     field
	 * @param jsonPath  json path
	 */
	public JsonExtract(ISelect field, String jsonPath) {

		this.field = field;
		this.jsonPath = jsonPath;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.dialect().jsonExtract(sb.builder(), () -> field.selectSql(sb), jsonPath, false);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return field.hasParameter();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return field.getParameter();

	}

}
