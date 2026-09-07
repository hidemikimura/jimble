package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * 正規表現
 */
public class RegexValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/* lock */
	private final ReentrantLock lock = new ReentrantLock();

	/* Pattern */
	private Pattern pattern = null;

	/* Compiled */
	private boolean compiled = false;

	/**
	 * 正規表現
	 *
	 * @param regex	正規表現
	 * @return	RegexValidator
	 */
	public RegexValidator regex (String regex) {

		this.settings.put("regex", regex);
		return this;

	}

	/**
	 * 正規表現をCompileする
	 */
	private void compile () {

		if (compiled) {
			return;
		}

		try {
			lock.lock();
			if (compiled) {
				return;
			}

			this.pattern = Pattern.compile(this.settings.getString("regex"));

			compiled = true;
		} finally {
			lock.unlock();
		}

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

		compile();

		return this.pattern.matcher(str).find();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Regex;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
