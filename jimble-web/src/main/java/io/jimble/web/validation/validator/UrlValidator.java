package io.jimble.web.validation.validator;

import io.jimble.util.pattern.Patterns;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * URL
 *
 * <p>
 * <b>全体が1つの URL であることを見る（要件 D-161）。</b>
 * 以前は部分一致だったので、{@code 見て https://example.com ここ} や
 * {@code https://example.com そのあとに何か} も<b>通っていた</b>。
 * </p>
 *
 * <p>
 * <b>スキームは要らない。</b>{@code example.com} も URL として通る。
 * </p>
 */
public class UrlValidator implements IValidator {

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
			return Patterns.WEB_URL.matcher(str).matches();
		} catch (Exception ex) {
			return false;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Url;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return new Data();

	}

}
