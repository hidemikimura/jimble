package io.jimble.web.validation.validator;

import io.jimble.util.pattern.Patterns;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * メールアドレス
 *
 * <p>
 * <b>全体が1つのメールアドレスであることを見る（要件 D-161）。</b>
 * 以前は部分一致だったので、{@code こんにちは a@example.com です} も
 * {@code a@example.com'; DROP--} も<b>通っていた</b>。
 * </p>
 */
public class EmailValidator implements IValidator {

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

		try {
			return Patterns.EMAIL_ADDRESS.matcher(str).matches();
		} catch (Exception ex) {
			return false;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Email;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return new Data();

	}

}
