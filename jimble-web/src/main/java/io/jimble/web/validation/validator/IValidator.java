package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * validator interface
 */
public interface IValidator {

	/**
	 * validate
	 *
	 * @param db				DB
	 * @param req				リクエスト情報
	 * @param isInsertRequest	登録リクエスト判定
	 * @param value				値
	 * @return	エラーの場合 = false
	 */
	boolean validate (DB db, Data req, boolean isInsertRequest, Object value) throws CodeException;

	/**
	 * エラー種別を取得する
	 *
	 * @return	エラー種別
	 */
	ValidationErrorType errorType();

	/**
	 * 設定情報を取得する
	 *
	 * @return	設定情報
	 */
	Data settings();

}
