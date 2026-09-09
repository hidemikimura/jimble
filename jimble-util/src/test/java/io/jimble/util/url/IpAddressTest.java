package io.jimble.util.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP アドレスの書き方の判定（{@link IpAddress}）と {@link UrlUtil#isIPUrl(String)}
 *
 * <p>
 * commons-validator の {@code InetAddressValidator} から自前に置き換えたときの答えを
 * 固定してある（要件 D-121）。<b>540,066 件で突き合わせて、1件も違わない</b>ことを
 * 確かめたうえで書いた。
 * </p>
 */
class IpAddressTest {

	// region IPv4

	@Test
	@DisplayName("IPv4 として読める")
	void v4 () {

		assertTrue(IpAddress.isV4("0.0.0.0"));
		assertTrue(IpAddress.isV4("255.255.255.255"));
		assertTrue(IpAddress.isV4("192.168.0.1"));
		assertTrue(IpAddress.isV4("10.0.0.255"));

	}

	@Test
	@DisplayName("IPv4 として読めない")
	void notV4 () {

		assertFalse(IpAddress.isV4("256.1.1.1"));
		assertFalse(IpAddress.isV4("1.2.3"));
		assertFalse(IpAddress.isV4("1.2.3.4.5"));
		assertFalse(IpAddress.isV4("1.2.3."));
		assertFalse(IpAddress.isV4(".1.2.3"));
		assertFalse(IpAddress.isV4("1..2.3"));
		assertFalse(IpAddress.isV4("1.2.3.a"));
		assertFalse(IpAddress.isV4("1.2.3.4 "));
		assertFalse(IpAddress.isV4("1.2.3.4/24"));
		assertFalse(IpAddress.isV4(""));
		assertFalse(IpAddress.isV4(null));

	}

	@Test
	@DisplayName("頭に 0 の付いた数は通さない")
	void leadingZero () {

		// 8進数と読む処理系があり、<b>読み手によって別のアドレスになる</b>
		assertFalse(IpAddress.isV4("01.2.3.4"));
		assertFalse(IpAddress.isV4("1.2.3.04"));
		assertFalse(IpAddress.isV4("0.0.0.00"));

		// 0 そのものは通す
		assertTrue(IpAddress.isV4("0.1.2.3"));

	}

	// endregion

	// region IPv6

	@Test
	@DisplayName("IPv6 として読める")
	void v6 () {

		assertTrue(IpAddress.isV6("::"));
		assertTrue(IpAddress.isV6("::1"));
		assertTrue(IpAddress.isV6("1::"));
		assertTrue(IpAddress.isV6("2001:db8::1"));
		assertTrue(IpAddress.isV6("1:2:3:4:5:6:7:8"));
		assertTrue(IpAddress.isV6("2001:0db8:0000:0000:0000:0000:0000:0001"));
		assertTrue(IpAddress.isV6("ABCD:EF01:2345:6789:ABCD:EF01:2345:6789"));

	}

	@Test
	@DisplayName("末尾の IPv4 記法も読める")
	void v6WithV4 () {

		assertTrue(IpAddress.isV6("::ffff:192.168.0.1"));
		assertTrue(IpAddress.isV6("::1.2.3.4"));
		assertTrue(IpAddress.isV6("1:2:3:4:5:6:1.2.3.4"));
		assertTrue(IpAddress.isV6("64:ff9b::1.2.3.4"));

		// 末尾の IPv4 が壊れていれば通さない
		assertFalse(IpAddress.isV6("::ffff:1.2.3.4.5"));
		assertFalse(IpAddress.isV6("::ffff:256.1.1.1"));

	}

	@Test
	@DisplayName("ゾーンとプレフィックス長を読む")
	void v6ZoneAndPrefix () {

		assertTrue(IpAddress.isV6("fe80::1%eth0"));
		assertTrue(IpAddress.isV6("fe80::1%1"));
		assertTrue(IpAddress.isV6("2001:db8::/32"));
		assertTrue(IpAddress.isV6("::1/0"));
		assertTrue(IpAddress.isV6("::1/128"));

		// ゾーンはプレフィックス長より<b>前</b>に書く
		assertTrue(IpAddress.isV6("fe80::1%eth0/64"));
		assertFalse(IpAddress.isV6("fe80::1/64%eth0"));

		assertFalse(IpAddress.isV6("::1%"));
		assertFalse(IpAddress.isV6("::1%et h0"));
		assertFalse(IpAddress.isV6("::1/129"));
		assertFalse(IpAddress.isV6("::1/"));
		assertFalse(IpAddress.isV6("::1/abc"));

	}

	@Test
	@DisplayName("IPv6 として読めない")
	void notV6 () {

		assertFalse(IpAddress.isV6("1:2:3:4:5:6:7:8:9"));
		assertFalse(IpAddress.isV6("1:2:3:4:5:6:7"));
		assertFalse(IpAddress.isV6("1::2::3"));
		assertFalse(IpAddress.isV6("12345::1"));
		assertFalse(IpAddress.isV6(":1:2:3:4:5:6:7"));
		assertFalse(IpAddress.isV6("1:2:3:4:5:6:7:"));
		assertFalse(IpAddress.isV6(":::"));
		assertFalse(IpAddress.isV6("g::1"));
		assertFalse(IpAddress.isV6("[::1]"));
		assertFalse(IpAddress.isV6(""));
		assertFalse(IpAddress.isV6(null));

	}

	// endregion

	// region URL から

	@Test
	@DisplayName("URL のホストが IP アドレスか分かる")
	void isIpUrl () {

		assertTrue(UrlUtil.isIPUrl("http://192.168.0.1/"));
		assertTrue(UrlUtil.isIPUrl("https://1.2.3.4:8080/path?a=1"));

		assertFalse(UrlUtil.isIPUrl("http://example.com/"));
		assertFalse(UrlUtil.isIPUrl("http://256.1.1.1/"));
		assertFalse(UrlUtil.isIPUrl("これは URL ではない"));
		assertFalse(UrlUtil.isIPUrl(""));

	}

	@Test
	@DisplayName("IPv6 の URL も分かる（角かっこを外す）")
	void isIpUrlV6 () {

		// <b>URI.getHost() は "[::1]" と角かっこ付きで返す。</b>
		// 外していなかったので、置き換える前は IPv6 が一度も当たっていなかった（要件 D-121）
		assertTrue(UrlUtil.isIPUrl("http://[::1]/"));
		assertTrue(UrlUtil.isIPUrl("http://[2001:db8::1]:8080/x"));

	}

	// endregion

}
