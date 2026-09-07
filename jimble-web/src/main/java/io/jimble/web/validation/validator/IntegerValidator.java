package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * 整数
 */
public class IntegerValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * 最小値設定
	 *
	 * @param min	最小値
	 * @return	IntegerValidator
	 */
	public IntegerValidator min (long min) {

		this.settings.put("min", min);
		return this;

	}

	/**
	 * 最大値設定
	 *
	 * @param max	最大値
	 * @return	IntegerValidator
	 */
	public IntegerValidator max (long max) {

		this.settings.put("max", max);
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validate(DB db, Data req, boolean isInsertRequest, Object value) throws CodeException {

		if (value == null) {
			return true;
		}

		String str = String.valueOf(value);
		if (str.isEmpty()) {
			return true;
		}

		long v;
		try {
			v = Long.parseLong(str);
		} catch (Exception ex) {
			return false;
		}

		if (this.settings.containsKey("min")) {
			if (v < this.settings.getLong("min")) {
				return false;
			}
		}

		if (this.settings.containsKey("max")) {
			if (this.settings.getLong("max") < v) {
				return false;
			}
		}

		return true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Integer;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
