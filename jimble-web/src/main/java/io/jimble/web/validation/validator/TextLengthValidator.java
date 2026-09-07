package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * 文字列長
 */
public class TextLengthValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * 最小値設定
	 *
	 * @param min	最小値
	 * @return	TextLengthValidator
	 */
	public TextLengthValidator min (int min) {

		this.settings.put("min", min);
		return this;

	}

	/**
	 * 最大値設定
	 *
	 * @param max	最大値
	 * @return	TextLengthValidator
	 */
	public TextLengthValidator max (int max) {

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

		if (this.settings.containsKey("min")) {
			if (str.length() < this.settings.getInt("min")) {
				return false;
			}
		}

		if (this.settings.containsKey("max")) {
			if (this.settings.getInt("max") < str.length()) {
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

		return ValidationErrorType.TextLength;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}
}
