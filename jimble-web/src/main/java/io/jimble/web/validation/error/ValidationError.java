package io.jimble.web.validation.error;

import io.jimble.util.data.Data;

/**
 * validation error
 */
public class ValidationError {

	/* error type */
	public ValidationErrorType errorType = ValidationErrorType.Unknown;

	/* 設定情報 */
	public Data settings = new Data();

	/* 値 */
	public Object value;

}
