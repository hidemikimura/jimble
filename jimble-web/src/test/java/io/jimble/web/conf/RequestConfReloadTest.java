package io.jimble.web.conf;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.cookie.SameSite;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.response.SecurityHeadersConf;
import io.jimble.web.session.SessionConf;
import io.jimble.web.template.TemplateConf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 要求ごとに読む設定は 1 回だけ解釈して持つが、差し替えたら読み直す（2.7.5）
 *
 * <p>Conf.replace のたびに、どの値も新しい設定のものになるか（古い値が残らないか）を見る。</p>
 */
class RequestConfReloadTest {

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("Cookie・CSRF・セッション・テンプレート・セキュリティヘッダの値が、差し替えのたびに変わる")
	void picksUpEveryReplace () {

		Conf.replace(ConfigFactory.parseString("""
			cookie { secret = s1, previous_secrets = [s0], same_site = strict, domain = a.jp, max_age = 1h, secure = false }
			csrf { bind_session = true, max_age = 10m }
			session { timeout = 5m, cookie_name = sid1 }
			template.json_fallback = true
			security_headers { enabled = false, hsts = 1d, frame_options = DENY }
			"""));

		assertEquals("s1", CookieConf.secret());
		assertEquals(List.of("s1", "s0"), CookieConf.secrets());
		assertEquals(SameSite.STRICT, CookieConf.sameSite());
		assertEquals("a.jp", CookieConf.domain());
		assertEquals(3600, CookieConf.maxAge());
		assertFalse(CookieConf.secure());
		assertTrue(Csrf.bindSession());
		assertEquals(Duration.ofMinutes(10), Csrf.maxAge());
		assertEquals(Duration.ofMinutes(5), SessionConf.timeout());
		assertEquals("sid1", SessionConf.cookieName());
		assertTrue(TemplateConf.jsonFallback());
		assertFalse(SecurityHeadersConf.enabled());
		assertEquals("max-age=86400", SecurityHeadersConf.hsts());
		assertEquals("DENY", SecurityHeadersConf.frameOptions());

		Conf.replace(ConfigFactory.parseString(""));

		assertEquals("", CookieConf.secret());
		assertEquals(List.of(), CookieConf.secrets());
		assertEquals(SameSite.LAX, CookieConf.sameSite());
		assertEquals("", CookieConf.domain());
		assertEquals(CookieConf.DEFAULT_MAX_AGE.toSeconds(), CookieConf.maxAge());
		assertTrue(CookieConf.secure());
		assertFalse(Csrf.bindSession());
		assertEquals(Csrf.DEFAULT_MAX_AGE, Csrf.maxAge());
		assertEquals(SessionConf.DEFAULT_TIMEOUT, SessionConf.timeout());
		assertEquals(SessionConf.DEFAULT_COOKIE_NAME, SessionConf.cookieName());
		assertFalse(TemplateConf.jsonFallback());
		assertTrue(SecurityHeadersConf.enabled());
		assertNull(SecurityHeadersConf.hsts());
		assertEquals("SAMEORIGIN", SecurityHeadersConf.frameOptions());

	}

	@Test
	@DisplayName("書き間違いは覚えず、読むたびに例外になる（直した設定に差し替えれば読める）")
	void mistakesAreNotRemembered () {

		Conf.replace(ConfigFactory.parseString("cookie.max_age = 30\ncsrf.max_age = 5"));

		assertThrows(IllegalStateException.class, CookieConf::maxAge);
		assertThrows(IllegalStateException.class, CookieConf::maxAge);
		assertThrows(IllegalStateException.class, Csrf::maxAge);

		Conf.replace(ConfigFactory.parseString("cookie.max_age = 30s\ncsrf.max_age = 5s"));

		assertEquals(30, CookieConf.maxAge());
		assertEquals(Duration.ofSeconds(5), Csrf.maxAge());

	}

}
