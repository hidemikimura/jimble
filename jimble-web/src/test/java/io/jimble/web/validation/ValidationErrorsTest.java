package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.util.internal.WarnOnce;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		assertEquals(rules.errors(null, req), errors);

		Data all = Validator.errors(null, req, rules);
		assertFalse(all.isEmpty());
		assertEquals(Validator.errors(null, req, rules), all);

	}

	@Test
	@DisplayName("D-196 required() の列が送られてこなければ失敗（1.x は素通り、1.5 は警告）")
	void requiredMissingKeyFails () {

		ValidationRules rules = new ValidationRules()
			.put(ValidationTest.Item.name, new ValidationRule().required());

		assertTrue(rules.errors(null, new Data().putData(ValidationTest.Item.name, "x")).isEmpty());
		assertFalse(rules.errors(null, new Data()).isEmpty(), "キーが無いのに通っている");

	}

	@Test
	@DisplayName("D-196 validate は通らなければ 422 の ValidationException（errors() は一覧）")
	void validateThrows422 () {

		ValidationRules rules = new ValidationRules()
			.put(ValidationTest.Item.name, new ValidationRule().required());

		rules.validate(null, new Data().putData(ValidationTest.Item.name, "x"));     // 通る

		ValidationException e = assertThrows(ValidationException.class, () -> rules.validate(null, new Data().putData(ValidationTest.Item.name, "")));
		assertEquals(422, e.statusCode());
		assertEquals(rules.errors(null, new Data().putData(ValidationTest.Item.name, "")), e.errors());

		assertThrows(ValidationException.class, () -> Validator.validate(null, new Data(), rules));
		ValidationException rows = assertThrows(ValidationException.class,
			() -> rules.validate(null, java.util.List.of(new Data().putData(ValidationTest.Item.name, "x"), new Data())));
		assertEquals(1, rows.errors().getDataList("rows").size());

	}

}
