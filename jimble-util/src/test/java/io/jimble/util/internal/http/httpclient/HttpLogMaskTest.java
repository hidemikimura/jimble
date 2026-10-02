package io.jimble.util.internal.http.httpclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP クライアントのログに秘密を出さない（D-240）
 */
class HttpLogMaskTest {

	@Test
	@DisplayName("Authorization / Cookie / Set-Cookie / *token* などのヘッダを伏せる")
	void headers () {

		Map<String, Object> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer abc");
		headers.put("Cookie", "sid=1");
		headers.put("X-Api-Key", "k");
		headers.put("Accept", "application/json");

		Map<String, Object> masked = AbstractHttpExecutor.maskHeaders(headers);

		assertEquals("***", masked.get("Authorization"));
		assertEquals("***", masked.get("Cookie"));
		assertEquals("***", masked.get("X-Api-Key"));
		assertEquals("application/json", masked.get("Accept"));

		Map<String, List<String>> response = Map.of("Set-Cookie", List.of("sid=2"));
		assertEquals("***", AbstractHttpExecutor.maskHeaders(response).get("Set-Cookie"));

	}

	@Test
	@DisplayName("URL のクエリの秘密を伏せる")
	void url () {

		assertEquals("https://x/a?q=1&access_token=***&api_key=***",
			AbstractHttpExecutor.maskUrl("https://x/a?q=1&access_token=abc&api_key=k"));
		assertEquals("https://x/a", AbstractHttpExecutor.maskUrl("https://x/a"));

	}

}
