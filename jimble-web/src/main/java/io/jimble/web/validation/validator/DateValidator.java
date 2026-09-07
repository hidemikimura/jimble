package io.jimble.web.validation.validator;

import io.jimble.util.convertor.Convertor;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日時
 */
public class DateValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * フォーマット
	 *
	 * @param format	フォーマット
	 * @return	DateValidator
	 */
	public DateValidator format (String format) {

		this.settings.put("format", format);
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

		if (this.settings.containsKey("format")) {
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(this.settings.getString("format"));
				Date date = sdf.parse(str);
				return date != null;
			} catch (Exception ex) {
				return false;
			}
		}

		try {
			Date date = Convertor.convert(null, value, Date.class);
			return date != null;
		} catch (Exception ex) {
			return false;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Date;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
