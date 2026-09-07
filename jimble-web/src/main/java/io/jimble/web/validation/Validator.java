package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.db.DB;

/**
 * validator
 */
public class Validator {

	/**
	 * 複数のValidationRuleを実行する
	 *
	 * @param db	DB
	 * @param req	リクエスト
	 * @param rules	ValidationRule
	 * @return	結果
	 */
	public static Data validate (DB db, Data req, ValidationRules...rules) {

		Data result = null;
		for (ValidationRules rule : rules) {

			Data validationResult = rule.validate(db, req);
			if (validationResult != null) {
				if (result == null) {
					result = new Data();
				}
				result.putAllData(validationResult);
			}

		}

		return result;

	}

}
