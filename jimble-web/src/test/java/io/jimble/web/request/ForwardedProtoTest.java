package io.jimble.web.request;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * プロキシの後ろでの HTTPS の判定（X-Forwarded-Proto。D-292）
 */
class ForwardedProtoTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	private static void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	private static String scheme (String remote, String forwardedProto) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/").remote(remote);
		if (forwardedProto != null) {
			source.header("X-Forwarded-Proto", forwardedProto);
		}

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			assertEquals("https".equals(context.request().scheme()), context.request().isSecure());
			return context.request().scheme();
		}

	}

	@Test
	@DisplayName("D-292 trust_proxy が false なら、X-Forwarded-Proto を見ない（名乗りにすぎない）")
	void notTrusted () {

		assertEquals("http", scheme("10.0.0.1", "https"));

	}

	@Test
	@DisplayName("D-292 trust_proxy なら X-Forwarded-Proto を見る。複数なら右端。http / https 以外は無視")
	void trusted () {

		conf("server.trust_proxy = true");

		assertEquals("https", scheme("10.0.0.1", "https"));
		assertEquals("https", scheme("10.0.0.1", "HTTPS"));
		assertEquals("http", scheme("10.0.0.1", null));
		// 左はクライアントが名乗れる。いちばん近い中継が付けた右端を見る
		assertEquals("http", scheme("10.0.0.1", "https, http"));
		assertEquals("https", scheme("10.0.0.1", "http,https"));
		assertEquals("http", scheme("10.0.0.1", "ftp"));

	}

	@Test
	@DisplayName("D-292 trusted_proxies を書いていれば、直に来た相手がそこに入っているときだけ見る")
	void trustedProxies () {

		conf("""
			server.trust_proxy = true
			server.trusted_proxies = ["10.0.0.0/8"]
			""");

		assertEquals("https", scheme("10.1.2.3", "https"));
		assertEquals("http", scheme("192.168.0.9", "https"));

	}

	@Test
	@DisplayName("D-292 プロキシの後ろでも HSTS を出す（https で来たときだけ）")
	void hstsBehindProxy () {

		conf("""
			server.trust_proxy = true
			security_headers.hsts = 365d
			""");

		assertTrue(hsts("https").startsWith("max-age="));
		assertNull(hsts(null), "http で来たのに HSTS を出した");

	}

	private static String hsts (String forwardedProto) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/").remote("10.0.0.1");
		if (forwardedProto != null) {
			source.header("X-Forwarded-Proto", forwardedProto);
		}

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			context.response().send("ok");
		}

		return sink.headers().get("Strict-Transport-Security");

	}

}
