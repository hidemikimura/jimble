package io.jimble.web.validation;

import io.jimble.web.validation.validator.EmailValidator;
import io.jimble.web.validation.validator.UrlValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 長い入力でメールアドレス・URL の検証が落ちない（D-230）
 *
 * <p>かつては長さを見ずに正規表現にかけ、StackOverflowError で 500 になった。</p>
 */
class LongInputValidatorTest {

	@Test
	@DisplayName("長すぎるメールアドレスは、例外ではなく検証の誤り")
	void longEmail () throws Exception {

		EmailValidator email = new EmailValidator();

		assertFalse(email.validate(null, null, false, "a@" + "a.".repeat(10_000) + "com"));
		assertTrue(email.validate(null, null, false, "user@example.com"));

	}

	@Test
	@DisplayName("長すぎる URL は、例外ではなく検証の誤り。ふつうの長さの URL は、仮想スレッドでも通る")
	void longUrl () throws Exception {

		UrlValidator url = new UrlValidator();

		assertFalse(url.validate(null, null, false, "http://a.com/" + "a".repeat(10_000) + "\""));
		assertTrue(url.validate(null, null, false, "https://example.com/path?q=1"));

		boolean[] ok = {false};
		Thread thread = Thread.ofVirtual().start(() -> {
			try {
				ok[0] = url.validate(null, null, false, "https://example.com/" + "a".repeat(1_000));
			} catch (Exception ignore) {
				// 落ちたら false のまま
			}
		});
		thread.join();
		assertTrue(ok[0], "上限の内側の URL が、仮想スレッドで通りません");

	}

}
