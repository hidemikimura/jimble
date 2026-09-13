package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * validation rules
 */
public class ValidationRules {

	/* validation rules */
	/*
	 * 登録順を保つ。移送元は HashMap で、
	 * エラーの並び順が実行ごとに変わっていた（Router の D-8 と同じ性質の問題）。
	 */
	private final Map<Column, ValidationRule> validationRules = new LinkedHashMap<>();

	/* other validation rules */
	private final List<ValidationRule> otherValidationRules = new ArrayList<>();

	/**
	 * その他バリデーションルールを設定する
	 *
	 * @param validationRule	バリデーションルール
	 * @return	ValidationRules
	 */
	public ValidationRules put (ValidationRule validationRule) {

		otherValidationRules.add(validationRule);
		return this;

	}

	/**
	 * バリデーションルールを設定する
	 *
	 * @param column			列
	 * @param validationRule	バリデーションルール
	 * @return	ValidationRules
	 */
	public ValidationRules put (Column column, ValidationRule validationRule) {

		validationRules.put(column, validationRule);
		return this;

	}

	/* insertリクエスト判定関数 */
	private Function<Data, Boolean> insertRequestChecker = null;

	/**
	 * insertリクエスト判定関数を設定する
	 *
	 * @param insertRequestChecker	insertリクエスト判定関数
	 * @return	ValidationRules
	 */
	public ValidationRules insertRequestChecker (Function<Data, Boolean> insertRequestChecker) {

		this.insertRequestChecker = insertRequestChecker;
		return this;

	}

	/**
	 * バリデーション
	 *
	 * @param req	リクエスト情報
	 * @return	エラー情報
	 */
	public Data validate (DB db, Data req) {

		Data errorData = new Data();

		boolean isInsertRequest = insertRequestChecker != null && insertRequestChecker.apply(req);

		for (Map.Entry<Column, ValidationRule> entry : validationRules.entrySet()) {

			Column column = entry.getKey();
			ValidationRule validationRule = entry.getValue();
			if (req.containsKey(column)
				|| (isInsertRequest && validationRule.isInsertRequired())) {
				Object input = req.getObject(column);
				List<Object> inputList = new ArrayList<>();
				if (input instanceof List<?> _list) {
					inputList.addAll(_list);
				} else {
					inputList.add(input);
				}
				for (Object value : inputList) {
					try {
						ValidationResult validationResult = validationRule.validate(db, req, isInsertRequest, value);
						if (validationResult.error()) {
							errorData.putData(column, new Data()
								.putData("validation_type", validationResult.validationError().errorType())
								.putData("validation_setting", validationResult.validationError().settings())
								.putData("input", validationResult.validationError().value())
							);
							break;
						}
					} catch (Exception ex) {
						errorData.putData(column, new Data()
							.putData("validation_type", ValidationErrorType.Error)
							.putData("validation_setting", new Data())
							.putData("input", value)
						);
						break;
					}
				}
			}

		}

		/*
		 * 列に紐づかないルール（項目をまたぐ相関チェックなど）。
		 *
		 * 移送元は最初の1件で break していて、列ごとのチェックが全部集めるのと
		 * 挙動が食い違っていた。エラーは一覧で返す約束（要件 F-V-03）なので、
		 * ここも最後まで回して全部集める。
		 */
		for (ValidationRule otherValidationRule : otherValidationRules) {

			try {
				ValidationResult validationResult = otherValidationRule.validate(db, req, isInsertRequest, null);
				if (validationResult.error()) {
					for (Column column : validationResult.getTargetColumnList()) {
						errorData.putData(column, new Data()
							.putData("validation_type", validationResult.validationError().errorType())
							.putData("validation_setting", validationResult.validationError().settings())
							.putData("input", validationResult.validationError().value())
						);
					}
				}
			} catch (Exception ex) {
				errorData.putData("unknown", new Data()
					.putData("validation_type", ValidationErrorType.Error)
					.putData("validation_setting", new Data())
					.putData("input", null)
				);
			}

		}

		return errorData;

	}

	/**
	 * バリデーション（複数件）
	 *
	 * <p>
	 * 移送元は単体版の中身を丸ごとコピーしていた（約 80 行の重複）。
	 * 片方だけ直すと挙動がずれるので、単体版を呼ぶ形にした。
	 * </p>
	 *
	 * @param db	DB
	 * @param list	データ一覧
	 * @return	エラー情報一覧（エラーのある行だけ。{@code index} は 1 始まり）
	 */
	public List<Data> validate (DB db, List<Data> list) {

		List<Data> errorDataList = new ArrayList<>();

		int rowNumber = 1;
		for (Data req : list) {

			Data errorData = validate(db, req);

			if (!errorData.isEmpty()) {
				errorData.putData("index", rowNumber);
				errorDataList.add(errorData);
			}

			rowNumber++;

		}

		return errorDataList;

	}

}
