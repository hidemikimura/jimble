package io.jimble.util.internal.http.httpclient.publisher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * multipart のパートのヘッダに入れる値（D-236）
 *
 * <p>かつてのエスケープは何も変えず、改行も通したので、パートのヘッダを差し込めた。</p>
 */
class MultipartEscapeTest {

	@Test
	@DisplayName("\" と CR / LF を %22 / %0D / %0A にする")
	void escape () {

		assertEquals("a%22b", MultipartFormDataBodyPublisher.escapeHeaderValue("a\"b"));
		assertEquals("x%0D%0AContent-Type: text/html", MultipartFormDataBodyPublisher.escapeHeaderValue("x\r\nContent-Type: text/html"));
		assertEquals("日本語.txt", MultipartFormDataBodyPublisher.escapeHeaderValue("日本語.txt"));

	}

}
