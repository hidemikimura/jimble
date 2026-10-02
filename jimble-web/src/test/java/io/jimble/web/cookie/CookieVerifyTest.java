package io.jimble.web.cookie;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WebSocket などで受け取った Cookie も、署名を確かめて読む（D-242）
 */
class CookieVerifyTest {

	private Config original;

	@BeforeEach
	void keep () {

		Conf.reload();
		original = Conf.conf().config();
		Conf.replace(ConfigFactory.parseString("cookie.secret = \"test-cookie-secret\"").withFallback(original));

	}

	@AfterEach
	void restore () {

		Conf.replace(original);

	}

	@Test
	@DisplayName("署名が合えば値だけ、合わなければ null")
	void verify () {

		String signed = Cookies.sign("sid", "abc");

		assertEquals("abc", Cookies.verify("sid", signed));
		assertNull(Cookies.verify("sid", "forged"));
		assertNull(Cookies.verify("other", signed), "別の名前の署名で読めています");

	}

}
