package io.jimble.db.sql.query.dsl.select;

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
	public void dslSql(StringBuilder sb) {

		sb.append("JSON_EXTRACT(");
		field.selectSql(sb);
		sb.append(", '");
		sb.append(jsonPath);
		sb.append("')");

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
