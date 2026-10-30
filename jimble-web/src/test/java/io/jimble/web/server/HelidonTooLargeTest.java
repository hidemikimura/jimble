package io.jimble.web.server;

import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.jimble.web.http.HttpException;
import io.jimble.web.upload.UploadConf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * helidon が上限で断った例外を、jimble の 413 にする（{@code HelidonRequestSource.tooLarge}）
 *
 * <p>応答の本文には出ない文言を、ここで確かめる（アプリは例外の文言を見る）。</p>
 */
class HelidonTooLargeTest {

	@Test
	@DisplayName("部分の数の超過（helidon の HttpException 413）は、部分の数の上限を言う 413。helidon の文言は出さない")
	void partCount () {

		Optional<HttpException> result = HelidonRequestSource.tooLarge(
			new io.helidon.http.HttpException("Maximum multipart part count exceeded", Status.REQUEST_ENTITY_TOO_LARGE_413));

		assertTrue(result.isPresent());
		assertEquals(413, result.get().statusCode());
		assertEquals("アップロードできる部分（項目とファイル）は %d 個までです".formatted(UploadConf.MAX_PARTS), result.get().getMessage());

	}

	@Test
	@DisplayName("本文の超過（helidon の RequestException 413）は、包まれていても server.max_request_size を言う 413")
	void requestSize () {

		RequestException helidon = RequestException.builder()
			.message("Request entity too large")
			.status(Status.REQUEST_ENTITY_TOO_LARGE_413)
			.build();

		Optional<HttpException> result = HelidonRequestSource.tooLarge(
			new UncheckedIOException(new IOException(helidon)));

		assertTrue(result.isPresent());
		assertEquals(413, result.get().statusCode());
		assertEquals("リクエストのサイズが上限（%d バイト）を超えています".formatted(ServerConf.maxRequestSize()), result.get().getMessage());
		assertFalse(result.get().getMessage().contains("Request entity"));


	}

	@Test
	@DisplayName("413 でない helidon の例外や、ほかの例外は空（呼ぶ側が今までどおり 400 などにする）")
	void notTooLarge () {

		assertTrue(HelidonRequestSource.tooLarge(
			new io.helidon.http.HttpException("bad", Status.BAD_REQUEST_400)).isEmpty());
		assertTrue(HelidonRequestSource.tooLarge(
			RequestException.builder().message("bad").status(Status.BAD_REQUEST_400).build()).isEmpty());
		assertTrue(HelidonRequestSource.tooLarge(new IllegalStateException("x")).isEmpty());

	}

}
