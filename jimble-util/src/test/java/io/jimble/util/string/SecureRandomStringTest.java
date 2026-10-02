package io.jimble.util.string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * パスワードと数字の文字列を、暗号用の乱数で作る（D-233）
 */
class SecureRandomStringTest {

	@Test
	@DisplayName("createPassword：長さどおり、使う字だけ。毎回ちがう")
	void createPassword () {

		Set<String> seen = new HashSet<>();

		for (int i = 0; i < 200; i++) {
			String password = StringUtil.createPassword(32, false);
			assertEquals(32, password.length());
			assertTrue(password.matches("[0-9A-Za-z]+"), password);
			seen.add(password);
		}

		assertEquals(200, seen.size());
		assertTrue(StringUtil.createPassword(64, true).chars().allMatch(c -> c >= 0x21 && c < 0x7b));

	}

	@Test
	@DisplayName("randomNumberString：桁数どおり。19 桁以上でも桁あふれしない")
	void randomNumberString () {

		assertTrue(StringUtil.randomNumberString(6).matches("\\d{6}"));
		assertTrue(StringUtil.randomNumberString(40).matches("\\d{40}"));
		assertEquals("", StringUtil.randomNumberString(0));

	}

}
