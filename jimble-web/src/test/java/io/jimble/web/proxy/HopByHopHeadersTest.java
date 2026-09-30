package io.jimble.web.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 転送してはいけないヘッダのテスト
 */
class HopByHopHeadersTest {

	@Test
	@DisplayName("hop-by-hop ヘッダは転送しない")
	void hopByHop () {

		for (String name : List.of(
			"Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization"
			, "TE", "Trailer", "Transfer-Encoding", "Upgrade"
			, "Content-Length", "Host", "Expect")) {

			assertFalse(HopByHopHeaders.isForwardable(name), name);

		}

	}

	@Test
	@DisplayName("大文字小文字は区別しない")
	void caseInsensitive () {

		assertFalse(HopByHopHeaders.isForwardable("CONNECTION"));
		assertFalse(HopByHopHeaders.isForwardable("connection"));
		assertFalse(HopByHopHeaders.isForwardable("Connection"));

	}

	@Test
	@DisplayName("普通のヘッダは転送する")
	void forwardable () {

		for (String name : List.of("Content-Type", "Accept", "Authorization", "Cookie", "X-Request-Id")) {
			assertTrue(HopByHopHeaders.isForwardable(name), name);
		}

		/*
		 * Content-Encoding は渡す。本文をそのまま流すので、落とすと「圧縮されている」ことが伝わらず、
		 * jimble がもう一度 gzip をかけて、ブラウザには解けない本文が届いていた
		 */
		assertTrue(HopByHopHeaders.isForwardable("Content-Encoding"));

	}

	@Test
	@DisplayName("null は転送しない")
	void nullName () {

		assertFalse(HopByHopHeaders.isForwardable(null));

	}

}
