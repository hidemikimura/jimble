package io.jimble.db.sql.definition.schema;

import io.jimble.util.data.definition.ISchema;

/**
 * 空スキーマ
 */
public class EmptyScheme extends AbstractSchema {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name() {

		return "empty";

	}

}
