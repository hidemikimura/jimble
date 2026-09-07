package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * 数値
 */
public class NumberValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * 最小値設定
	 *
	 * @param min	最小値
	 * @return	NumberValidator
	 */
	public NumberValidator min (double min) {

		this.settings.put("min", min);
		return this;

	}

	/**
	 * 最大値設定
	 *
	 * @param max	最大値
	 * @return	NumberValidator
	 */
	public NumberValidator max (double max) {

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

		double v;
		try {
			v = Double.parseDouble(str);
		} catch (Exception ex) {
			return false;
		}

		if (this.settings.containsKey("min")) {
			if (v < this.settings.getDouble("min")) {
				return false;
			}
		}

		if (this.settings.containsKey("max")) {
			if (this.settings.getDouble("max") < v) {
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

		return ValidationErrorType.Number;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
