package io.jimble.web.response;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 既定で付けるセキュリティのヘッダ（D-213）
 *
 * <p>2.2.2 までは何も付けておらず、どのページも別のサイトの iframe に入れられた。</p>
 */
class SecurityHeadersTest {

	private Config original;

	@BeforeEach
	void keep () {

		Conf.reload();
		original = Conf.conf().config();

	}

	@AfterEach
	void restore () {

		Conf.replace(original);

	}

	private static void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	private static Fakes.FakeResponseSink get (JimbleApp app) {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x"), sink)) {
			new Dispatcher(app).dispatch(context);
		}

		return sink;

	}

	private static JimbleApp app () {

		return new JimbleApp() {
			{
				get("/x", context -> context.response().send("ok"));
			}
		};

	}

	@Test
	@DisplayName("既定で nosniff / SAMEORIGIN / Referrer-Policy を付ける。HSTS は付けない")
	void defaults () {

		Fakes.FakeResponseSink sink = get(app());

		assertEquals("nosniff", sink.headers().get("X-Content-Type-Options"));
		assertEquals("SAMEORIGIN", sink.headers().get("X-Frame-Options"));
		assertEquals("strict-origin-when-cross-origin", sink.headers().get("Referrer-Policy"));
		assertNull(sink.headers().get("Strict-Transport-Security"));

	}

	@Test
	@DisplayName("アプリが決めたヘッダは上書きしない")
	void appWins () {

		Fakes.FakeResponseSink sink = get(new JimbleApp() {
			{
				get("/x", context -> context.response()
					.setResponseHeader("X-Frame-Options", "DENY")
					.send("ok"));
			}
		});

		assertEquals("DENY", sink.headers().get("X-Frame-Options"));

	}

	@Test
	@DisplayName("空にしたヘッダは付けない。enabled = false なら全部付けない")
	void optOut () {

		conf("security_headers.frame_options = \"\"");
		Fakes.FakeResponseSink one = get(app());
		assertNull(one.headers().get("X-Frame-Options"));
		assertEquals("nosniff", one.headers().get("X-Content-Type-Options"));

		conf("security_headers.enabled = false");
		Fakes.FakeResponseSink none = get(app());
		assertNull(none.headers().get("X-Content-Type-Options"));
		assertNull(none.headers().get("Referrer-Policy"));

	}

	@Test
	@DisplayName("HSTS は設定して、https で受けたときだけ付ける")
	void hsts () {

		conf("security_headers.hsts = 180d");

		// http では付けない（ブラウザが無視する）
		assertNull(get(app()).headers().get("Strict-Transport-Security"));

		assertEquals("max-age=15552000", SecurityHeadersConf.hsts());

		conf("security_headers.hsts_include_subdomains = true");
		assertEquals("max-age=15552000; includeSubDomains", SecurityHeadersConf.hsts());

	}

}
