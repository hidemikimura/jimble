package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.util.internal.WarnOnce;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertEquals(rules.validate(null, req), errors);

		Data all = Validator.errors(null, req, rules);
		assertFalse(all.isEmpty());
		assertEquals(Validator.validate(null, req, rules), all);

	}

	@Test
	@DisplayName("D-192 required() の列が送られてこなかったら1度だけ警告する（送られていれば言わない）")
	void requiredMissingKeyWarns () {

		java.util.List<String> warns = new java.util.ArrayList<>();
		WarnOnce.reset();
		Log.sink((logger, level, message, data, throwable) -> warns.add(message));
		try {
			ValidationRules rules = new ValidationRules()
				.put(ValidationTest.Item.name, new ValidationRule().required());

			assertTrue(rules.errors(null, new Data().putData(ValidationTest.Item.name, "x")).isEmpty());
			assertTrue(warns.isEmpty(), warns.toString());

			assertTrue(rules.errors(null, new Data()).isEmpty(), "キーが無いのに失敗している（1.x は素通り）");
			rules.errors(null, new Data());
			assertEquals(1, warns.stream().filter(w -> w.contains("required()")).count(), warns.toString());
			assertTrue(warns.getFirst().contains("name"), warns.getFirst());
		} finally {
			Log.resetSink();
			WarnOnce.reset();
		}

	}

}
