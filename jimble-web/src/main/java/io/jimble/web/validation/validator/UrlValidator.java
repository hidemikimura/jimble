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
	 * URL の長さの上限（D-230）
	 *
	 * <p>
	 * URL の正規表現は、長さに比例して深く潜る。リクエストを処理する仮想スレッドのスタックでは、
	 * JIT が効く前だと 2,000 文字ほどで使い切ることがあった。余裕を見て 1,024 文字にする。
	 * </p>
	 */
	static final int MAX_LENGTH = 1024;

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
		 * <b>長さを先に見る</b>（D-230）。かつては見ずに正規表現にかけたので、数 KB の URL で
		 * StackOverflowError になり、検証の誤りではなく 500 になった
		 */
		if (str.length() > MAX_LENGTH) {
			return false;
		}

		try {
			return Patterns.WEB_URL.matcher(str).matches();
		} catch (Exception | StackOverflowError ex) {
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
