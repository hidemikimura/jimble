package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.web.validation.error.ValidationErrorType;
import io.jimble.web.validation.validator.IValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * アプリが足す検証（D-173。要件 NF-L-03）
 *
 * <p>
 * <b>{@code custom(IValidator)} は公開されている。</b>だから
 * {@code IValidator} に abstract を1つ足せば、<b>その瞬間にアプリが全部コンパイルエラーになる</b>。
 * 1.0 からは公開 API を壊す前に非推奨期間を置くと約束しているので、
 * <b>{@code validate} 以外は default である</b>ことをここで固定する。
 * </p>
 */
class ValidatorExtensionTest {

	/**
	 * {@code validate} だけ書けば実装になること
	 *
	 * <p>
	 * <b>このクラスがコンパイルできること自体がテストである。</b>
	 * {@code errorType()} か {@code settings()} が abstract に戻れば、ここで落ちる。
	 * </p>
	 */
	private static final class OnlyValidate implements IValidator {

		@Override
		public boolean validate (io.jimble.db.DB db, Data req, boolean isInsertRequest, Object value) {

			return value != null;

		}

	}

	/** 既定だけで動くこと */
	@Test
	@DisplayName("validate だけ実装すれば、検証として使える")
	void defaultsAreEnough () throws Exception {

		IValidator validator = new OnlyValidate();

		assertTrue(validator.validate(null, new Data(), false, "あたい"));
		assertFalse(validator.validate(null, new Data(), false, null));

		assertEquals(ValidationErrorType.Custom, validator.errorType()
			, "既定のエラー種別が Custom でない（文言の引き先が変わる）");

		assertNotNull(validator.settings(), "既定の設定が null");
		assertTrue(validator.settings().isEmpty(), "既定の設定が空でない");

	}

	/**
	 * 既定の設定が共有されていないこと
	 *
	 * <p>
	 * <b>1つの入れ物を返すと、1件書き換えただけで全部に効く。</b>
	 * 設定は文言に埋め込まれるので、別の項目のエラー文に混ざる。
	 * </p>
	 */
	@Test
	@DisplayName("既定の設定は毎回新しい入れ物である")
	void defaultSettingsAreNotShared () {

		IValidator validator = new OnlyValidate();

		Data first = validator.settings();
		first.put("min", 1);

		assertNotSame(first, validator.settings(), "同じ入れ物を返している");
		assertTrue(validator.settings().isEmpty(), "書き換えが次の呼び出しに残っている");

	}

	/** ルールに差し込めること */
	@Test
	@DisplayName("custom() に渡して動く")
	void worksAsCustomRule () throws Exception {

		ValidationRule rule = new ValidationRule().custom(new OnlyValidate());

		assertFalse(rule.validate(null, new Data(), false, "あたい").error());
		assertTrue(rule.validate(null, new Data(), false, null).error());

		assertEquals(ValidationErrorType.Custom
			, rule.validate(null, new Data(), false, null).validationError().errorType());

	}

}
