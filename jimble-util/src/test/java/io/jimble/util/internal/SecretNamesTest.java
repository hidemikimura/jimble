package io.jimble.util.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秘密を表す名前とクエリの伏せ方のテスト（D-306）
 */
class SecretNamesTest {

	@ParameterizedTest
	@ValueSource(strings = {
		"password", "new_password", "passwd", "client_secret", "token", "access_token", "csrf_token",
		"api_key", "api-key", "apikey", "card_number", "cardNumber",
		"authorization", "cookie", "key", "sig", "signature", "cvc", "cvv", "pin", "otp", "code_verifier",
		"TOKEN", "Signature",
		"user[password]", "users[0][token]", "a[key]", "a.sig"
	})
	@DisplayName("D-306 秘密を表す名前は伏せる（入れ子の名前も）")
	void secretNames (String name) {

		assertTrue(SecretNames.isSecret(name), name);

	}

	@ParameterizedTest
	@ValueSource(strings = {"page", "q", "keyword", "monkey", "keys", "pinned", "code", "state", "sort", ""})
	@DisplayName("D-306 秘密でない名前は伏せない（key・pin などは名前そのものが一致するときだけ）")
	void notSecretNames (String name) {

		assertFalse(SecretNames.isSecret(name), name);

	}

	@Test
	@DisplayName("D-306 足した名前は、名前そのもの（入れ子の段を含む）が一致するときだけ伏せる。大文字小文字は区別しない")
	void extraNames () {

		Set<String> extra = Set.of("invite");

		assertTrue(SecretNames.isSecret("invite", extra));
		assertTrue(SecretNames.isSecret("INVITE", extra));
		assertTrue(SecretNames.isSecret("a[invite]", extra));
		assertFalse(SecretNames.isSecret("invited_by", extra));
		assertFalse(SecretNames.isSecret("invite", Set.of()));

	}

	@Test
	@DisplayName("D-306 クエリはキーを残して値だけ伏せる")
	void maskQuery () {

		assertEquals("token=***&page=2", SecretNames.maskQuery("token=abc&page=2", Set.of()));
		assertEquals("q=x&access_token=***&api_key=***", SecretNames.maskQuery("q=x&access_token=abc&api_key=k", Set.of()));
		assertEquals("token=***&token=***", SecretNames.maskQuery("token=a&token=b", Set.of()));
		assertEquals("user%5Bpassword%5D=***", SecretNames.maskQuery("user%5Bpassword%5D=p", Set.of()));
		assertEquals("invite=***&page=1", SecretNames.maskQuery("invite=abc&page=1", Set.of("invite")));

	}

	@Test
	@DisplayName("D-306 名前はデコードしてから比べる（%74oken ですり抜けない）")
	void maskQueryDecodesName () {

		assertEquals("%74oken=***", SecretNames.maskQuery("%74oken=abc", Set.of()));
		assertEquals("api%5Fkey=***", SecretNames.maskQuery("api%5Fkey=k", Set.of()));

	}

	@Test
	@DisplayName("D-306 値が空でも伏せる。= の無い名前・壊れたエンコードはそのまま")
	void maskQueryEdges () {

		assertEquals("token=***", SecretNames.maskQuery("token=", Set.of()));
		assertEquals("token&page=1", SecretNames.maskQuery("token&page=1", Set.of()));
		assertEquals("%zz=1&token=***", SecretNames.maskQuery("%zz=1&token=a", Set.of()));
		assertEquals("a=1&&b=2", SecretNames.maskQuery("a=1&&b=2", Set.of()));

	}

	@Test
	@DisplayName("D-306 伏せるものが無ければ、渡した文字列をそのまま返す（作り直さない）")
	void maskQueryReturnsSameWhenNothingToMask () {

		String query = "page=2&q=abc";

		assertSame(query, SecretNames.maskQuery(query, Set.of()));
		assertEquals("", SecretNames.maskQuery("", Set.of()));
		assertNull(SecretNames.maskQuery(null, Set.of()));

	}

}
