package io.jimble.web.validation;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.web.validation.error.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * validation result
 */
public class ValidationResult {

	/**
	 * エラー判定
	 *
	 * @return	エラーの場合 = true
	 */
	public boolean error () {

		return validationError != null;

	}

	/* エラー内容 */
	private ValidationError validationError = null;

	/**
	 * エラー内容
	 *
	 * @return	エラー内容
	 */
	public ValidationError validationError () {

		return validationError;

	}

	/**
	 * エラー内容を設定する
	 *
	 * @param validationError	エラー内容
	 */
	public void setValidationError (ValidationError validationError) {

		if (this.validationError == null) {
			this.validationError = validationError;
		}

		validationErrorList.add(validationError);

	}

	/* エラー内容一覧 */
	private final List<ValidationError> validationErrorList = new ArrayList<>();

	/**
	 * エラー内容一覧
	 *
	 * @return	エラー内容一覧
	 */
	public List<ValidationError> getValidationErrorList () {

		return validationErrorList;

	}

	/* 対象列一覧 */
	private final List<Column> targetColumnList = new ArrayList<>();

	/**
	 * 対象列一覧
	 *
	 * @return	対象列一覧
	 */
	public List<Column> getTargetColumnList () {

		return targetColumnList;

	}

	/**
	 * 対象列追加
	 *
	 * @param column	列
	 * @return	ValidationResult
	 */
	public ValidationResult addTargetColumn (Column column) {

		targetColumnList.add(column);
		return this;

	}

}
