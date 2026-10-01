package io.jimble.web.request;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * プロキシの後ろでのクライアントの IP（D-214）
 *
 * <p>
 * 2.2.2 までは X-Forwarded-For の<b>左端</b>（クライアントが好きに名乗れる）と、
 * CF-Connecting-IP / X-Real-IP を無条件に信じていた。名乗る値を変えるだけで、IP ごとのレート制限をすり抜けられた。
 * </p>
 */
class ClientIpTest {

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

	private static String ip (Fakes.FakeRequestSource source) {

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			return context.request().proxyAddress();
		}

	}

	private static Fakes.FakeRequestSource from (String remote) {

		return new Fakes.FakeRequestSource("GET", "/").remote(remote);

	}

	@Test
	@DisplayName("trust_proxy でなければ、ヘッダは見ない")
	void untrusted () {

		assertEquals("203.0.113.9", ip(from("203.0.113.9").header("X-Forwarded-For", "1.2.3.4")));

	}

	@Test
	@DisplayName("X-Forwarded-For は右端（直前の中継が足した値）。左端はクライアントが名乗れる")
	void rightmost () {

		conf("server.trust_proxy = true");

		// 攻撃者が 6.6.6.6 を名乗り、nginx が本当の接続元 198.51.100.7 を後ろに足した
		assertEquals("198.51.100.7", ip(from("10.0.0.1").header("X-Forwarded-For", "6.6.6.6, 198.51.100.7")));
		assertEquals("198.51.100.7", ip(from("10.0.0.1").header("X-Forwarded-For", "198.51.100.7")));

	}

	@Test
	@DisplayName("CF-Connecting-IP / X-Real-IP は、client_ip_header に書いたときだけ信じる")
	void clientIpHeader () {

		conf("server.trust_proxy = true");

		assertEquals("198.51.100.7", ip(from("10.0.0.1")
			.header("CF-Connecting-IP", "6.6.6.6").header("X-Real-IP", "7.7.7.7")
			.header("X-Forwarded-For", "198.51.100.7")));

		conf("server.client_ip_header = \"CF-Connecting-IP\"");

		assertEquals("203.0.113.5", ip(from("10.0.0.1").header("CF-Connecting-IP", "203.0.113.5")
			.header("X-Forwarded-For", "6.6.6.6, 10.0.0.2")));

	}

	@Test
	@DisplayName("trusted_proxies：中継を右から飛ばし、最初の中継でないものをクライアントとする")
	void trustedProxies () {

		conf("server.trust_proxy = true\nserver.trusted_proxies = [\"10.0.0.0/8\", \"192.168.1.10\"]");

		// CDN（10.x）→ LB（192.168.1.10）→ アプリ
		assertEquals("198.51.100.7", ip(from("192.168.1.10")
			.header("X-Forwarded-For", "6.6.6.6, 198.51.100.7, 10.1.2.3")));

	}

	@Test
	@DisplayName("trusted_proxies に入っていない相手から直に来たら、ヘッダは見ない")
	void directFromUntrusted () {

		conf("server.trust_proxy = true\nserver.trusted_proxies = [\"10.0.0.0/8\"]");

		assertEquals("203.0.113.9", ip(from("203.0.113.9").header("X-Forwarded-For", "1.2.3.4")));

	}

	@Test
	@DisplayName("trusted_proxies は IP の字面だけを読む。名前は引かない")
	void literalOnly () {

		conf("server.trust_proxy = true\nserver.trusted_proxies = [\"10.0.0.0/8\"]");

		// 名前は中継とみなさない（DNS を引いて許可リストを通らせない）
		assertEquals("localhost", ip(from("10.0.0.1").header("X-Forwarded-For", "1.2.3.4, localhost")));

		conf("server.trusted_proxies = [\"proxy.example.com\"]");
		assertThrows(IllegalStateException.class, () -> ip(from("10.0.0.1").header("X-Forwarded-For", "1.2.3.4")));

	}

	@Test
	@DisplayName("CIDR の判定（IPv4 / IPv6、境界）")
	void cidr () {

		ClientIp.Cidr v4 = ClientIp.Cidr.parse("192.168.0.0/23");
		assertEquals(true, v4.contains(ClientIp.Cidr.literal("192.168.1.255")));
		assertEquals(false, v4.contains(ClientIp.Cidr.literal("192.168.2.0")));

		ClientIp.Cidr v6 = ClientIp.Cidr.parse("2001:db8::/32");
		assertEquals(true, v6.contains(ClientIp.Cidr.literal("2001:db8:1::1")));
		assertEquals(false, v6.contains(ClientIp.Cidr.literal("192.168.1.1")));

	}

}
