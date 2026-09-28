package io.jimble.web.validation;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code errors(...)}：名前が中身を言う検査（要件 D-191）
 */
class ValidationErrorsTest {

	@Test
	@DisplayName("D-191 errors は validate と同じ一覧を返す")
	void errorsIsValidate () {

		ValidationRules rules = new ValidationRules()
			.put(ValidationTest.Item.name, new ValidationRule().required());
		Data req = new Data().putData(ValidationTest.Item.name, "");

		Data errors = rules.errors(null, req);
		assertFalse(errors.isEmpty(), "エラーが出ていません");
		assertEquals(rules.validate(null, req), errors);

		Data all = Validator.errors(null, req, rules);
		assertFalse(all.isEmpty());
		assertEquals(Validator.validate(null, req, rules), all);

	}

}
