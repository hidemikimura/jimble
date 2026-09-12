package io.jimble.web.validation.validator;

import io.jimble.util.pattern.Patterns;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * ドメイン
 *
 * <p>
 * <b>全体が1つのドメイン名（か IP アドレス）であることを見る（要件 D-161）。</b>
 * 以前は部分一致だったので、{@code https://example.com} も
 * {@code example.com/../etc} も<b>通っていた</b>。
 * </p>
 */
public class DomainValidator implements IValidator {

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
			return Patterns.DOMAIN_NAME.matcher(str).matches();
		} catch (Exception ex) {
			return false;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Domain;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return new Data();

	}

}
