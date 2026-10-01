package io.jimble.web.cookie;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.util.crypto.Signer;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 署名つき Cookie の署名を、別の名前の Cookie に移し替えられない（D-220）
 */
class CookieSignatureBindingTest {

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

	private static String read (String name, String value) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/").cookie(name, value);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			String read = context.cookies().get(name);
			return read == null || read.isEmpty() ? null : read;
		}

	}

	@Test
	@DisplayName("フラッシュで署名させた値を、別の Cookie に移し替えても読めない")
	void cannotMoveSignature () {

		// アプリがフラッシュに入力を入れると、攻撃者の決めた値に署名が付いて返ってくる
		String signedByFlash = Cookies.sign("flash__message", "admin");

		assertEquals("admin", read("flash__message", signedByFlash));
		assertNull(read("role", signedByFlash), "フラッシュの署名で、別の Cookie を作れています");

	}

	@Test
	@DisplayName("2.2.2 までの署名は、移す間だけ読み、古いものとして書き直させる。断る設定なら読まない")
	void legacySignature () {

		String legacy = Signer.sign("abc", "test-cookie-secret");

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/").cookie("sid", legacy);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals("abc", context.cookies().get("sid"));
			assertTrue(context.cookies().isStale("sid"), "書き直させるため、古いものとして扱う");
		}

		Conf.replace(ConfigFactory.parseString("cookie.accept_legacy_signature = false").withFallback(Conf.conf().config()));

		assertNull(read("sid", legacy));

	}

}
