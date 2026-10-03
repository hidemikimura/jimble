package io.jimble.web.auth.passkey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * パスキーを結びつけるサイト（D-262）
 */
class PasskeyRpTest {

	@Test
	@DisplayName("名前とオリジンを書かなければ、rp_id と https://rp_id になる。大文字は小文字にする")
	void defaults () {

		PasskeyRp rp = PasskeyRp.of("Client-A.Example.COM", "");

		assertEquals("client-a.example.com", rp.id());
		assertEquals("client-a.example.com", rp.name());
		assertEquals(List.of("https://client-a.example.com"), rp.origins());

	}

	@Test
	@DisplayName("サブドメインのオリジン・ポート・localhost の http は受け付ける")
	void accepts () {

		assertEquals(List.of("https://app.example.com", "https://admin.example.com:8443")
			, PasskeyRp.of("example.com", "例", List.of("https://app.example.com/", "https://admin.example.com:8443")).origins());

		PasskeyRp.of("localhost", "手元", List.of("http://localhost:9000"));
		PasskeyRp.of("client-a.localhost", "手元", List.of("http://client-a.localhost:9000"));

	}

	@Test
	@DisplayName("rp_id にスキーム・ポート・パスがある、オリジンが rp_id の外・http・パス付き、なら断る")
	void rejects () {

		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("", "x"));
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("https://example.com", "x"));
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("example.com:443", "x"));
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("example.com/app", "x"));
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of(".example.com", "x"));

		// 別のクライアントのドメインを並べてしまった
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("client-a.example.com", "x", List.of("https://client-b.example.com")));
		// 名前が rp_id で終わるだけの別のドメイン
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("example.com", "x", List.of("https://evilexample.com")));
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("example.com", "x", List.of("http://example.com")));
		assertThrows(IllegalArgumentException.class, () -> PasskeyRp.of("example.com", "x", List.of("https://example.com/login")));

	}

}
