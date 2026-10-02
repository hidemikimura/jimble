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

	/** メールアドレスの長さの上限（RFC 5321） */
	static final int MAX_LENGTH = 254;

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

		/*
		 * <b>長さを先に見る</b>（D-230）。メールアドレスは 254 文字まで（RFC 5321）。
		 * かつては見ずに正規表現にかけたので、"a@" + "a.".repeat(10000) で StackOverflowError になり、
		 * 検証の誤りではなく 500 になった（Error は Exception で受けられない）
		 */
		if (str.length() > MAX_LENGTH) {
			return false;
		}

		try {
			return Patterns.EMAIL_ADDRESS.matcher(str).matches();
		} catch (Exception | StackOverflowError ex) {
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
