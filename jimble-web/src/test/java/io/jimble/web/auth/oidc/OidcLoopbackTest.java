package io.jimble.web.auth.oidc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * http を通すのは、この機械の中だけ（D-248）
 */
class OidcLoopbackTest {

	@Test
	@DisplayName("localhost.evil.example を localhost とみなさない")
	void loopback () {

		assertTrue(OidcProvider.isLoopbackHttp("http://localhost:8080/realms/x"));
		assertTrue(OidcProvider.isLoopbackHttp("http://127.0.0.1/x"));
		assertTrue(OidcProvider.isLoopbackHttp("http://[::1]:8080/x"));
		assertFalse(OidcProvider.isLoopbackHttp("http://localhost.evil.example/x"));
		assertFalse(OidcProvider.isLoopbackHttp("http://127.0.0.1.evil.example/x"));
		assertFalse(OidcProvider.isLoopbackHttp("https://localhost/x"));

	}

}
