package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.upload.UploadConf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * helidon が上限で断ったものも 413 で返すか
 *
 * <p>
 * helidon は、部分の数（{@value UploadConf#MAX_PARTS} 個。変えられない）と、
 * 大きさを名乗らない本文（chunked）の {@code server.max_request_size} を自分で見て、413 の例外を投げる。
 * 2.7.11 までは、それを 400「multipart を読めませんでした」に包んでいた。
 * jimble 自身の上限（413）と混ざらないよう、413 で返す。
 * </p>
 */
class UploadPartLimitIntegrationTest {

	/** 境界文字列 */
	private static final String BOUNDARY = "----jimblePartLimit";

	/** 本文の上限（部分 1,000 個は収まり、chunked で超えさせやすい大きさ） */
	private static final int MAX_REQUEST_SIZE = 512 * 1024;

	/* サーバー */
	private static JimbleServer server;

	@BeforeAll
	static void startServer () {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString("""
			server { max_request_size = %dB }
			upload { max_total_size = %dB, max_files = %d }
			""".formatted(MAX_REQUEST_SIZE, MAX_REQUEST_SIZE, UploadConf.MAX_PARTS))
			.withFallback(Conf.conf().config()));

		JimbleApp app = new JimbleApp() {
			{
				post("/upload", context -> {
					int files = context.request().bodyFile().size();
					context.response().send("files=" + files + " fields=" + context.request().bodyForm().size());
				});
				post("/text", context -> context.response().send("length=" + context.request().bodyText().length()));
			}
		};

		server = JimbleServer.start(app, 0);

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		Conf.reload();

	}

	@Test
	@DisplayName("部分がちょうど上限（項目 1 個 + ファイル 999 個）なら読める")
	void exactlyMaxParts () throws Exception {

		HttpResponse<String> response = post("/upload", multipart(1, UploadConf.MAX_PARTS - 1), false);

		assertEquals(200, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("部分が上限を超える（項目 1 個 + ファイル 1,000 個）と 413。400 に包まない")
	void tooManyParts () throws Exception {

		HttpResponse<String> response = post("/upload", multipart(1, UploadConf.MAX_PARTS), false);

		assertEquals(413, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("chunked の multipart が server.max_request_size を超えると 413。400 に包まない")
	void chunkedMultipartTooLarge () throws Exception {

		byte[] content = new byte[MAX_REQUEST_SIZE * 2];
		Arrays.fill(content, (byte) 'a');

		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(filePart(0, new String(content, StandardCharsets.US_ASCII)));
		body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> response = post("/upload", body.toByteArray(), true);

		assertEquals(413, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("chunked の本文（multipart でない）が上限を超えても 413。空として読ませない")
	void chunkedTextTooLarge () throws Exception {

		byte[] content = new byte[MAX_REQUEST_SIZE * 2];
		Arrays.fill(content, (byte) 'a');

		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create(url("/text")))
				.header("Content-Type", "text/plain")
				.POST(chunked(content))
				.build()
			, HttpResponse.BodyHandlers.ofString());

		assertEquals(413, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("chunked の JSON が上限を超えると 413。壊れた JSON（400）にしない")
	void chunkedJsonTooLarge () throws Exception {

		byte[] content = ("[" + "1,".repeat(MAX_REQUEST_SIZE) + "1]").getBytes(StandardCharsets.US_ASCII);

		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create(url("/text")))
				.header("Content-Type", "application/json")
				.POST(chunked(content))
				.build()
			, HttpResponse.BodyHandlers.ofString());

		assertEquals(413, response.statusCode(), response.body());

	}

	@Test
	@DisplayName("壊れた multipart は今までどおり 400")
	void brokenMultipartStays400 () throws Exception {

		byte[] broken = ("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nvalue")
			.getBytes(StandardCharsets.UTF_8);

		HttpResponse<String> response = post("/upload", broken, false);

		assertEquals(400, response.statusCode(), response.body());

	}

	// region ヘルパー

	/**
	 * multipart を投げる
	 *
	 * @param path		パス
	 * @param body		本体
	 * @param chunked	大きさを名乗らずに送る場合 = true
	 * @return	レスポンス
	 * @throws Exception	通信に失敗した場合
	 */
	private HttpResponse<String> post (String path, byte[] body, boolean chunked) throws Exception {

		return HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create(url(path)))
				.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
				.POST(chunked ? chunked(body) : HttpRequest.BodyPublishers.ofByteArray(body))
				.build()
			, HttpResponse.BodyHandlers.ofString());

	}

	/**
	 * 大きさを名乗らない本文（Transfer-Encoding: chunked になる）
	 *
	 * @param body	本体
	 * @return	本文
	 */
	private static HttpRequest.BodyPublisher chunked (byte[] body) {

		return HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body));

	}

	/**
	 * URL
	 *
	 * @param path	パス
	 * @return	URL
	 */
	private static String url (String path) {

		return "http://localhost:%d%s".formatted(server.port(), path);

	}

	/**
	 * 項目とファイルを並べた multipart の本体
	 *
	 * @param fields	項目の数
	 * @param files		ファイルの数
	 * @return	本体
	 * @throws Exception	組み立てに失敗した場合
	 */
	private byte[] multipart (int fields, int files) throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		for (int i = 0; i < fields; i++) {
			out.write(("--" + BOUNDARY + "\r\n"
				+ "Content-Disposition: form-data; name=\"field" + i + "\"\r\n"
				+ "\r\n"
				+ "v\r\n").getBytes(StandardCharsets.UTF_8));
		}

		for (int i = 0; i < files; i++) {
			out.write(filePart(i, "x"));
		}

		out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

		return out.toByteArray();

	}

	/**
	 * ファイルのパート
	 *
	 * @param index		番号
	 * @param content	中身
	 * @return	パート
	 */
	private byte[] filePart (int index, String content) {

		return ("--" + BOUNDARY + "\r\n"
			+ "Content-Disposition: form-data; name=\"file\"; filename=\"f" + index + ".txt\"\r\n"
			+ "Content-Type: text/plain\r\n"
			+ "\r\n"
			+ content + "\r\n").getBytes(StandardCharsets.UTF_8);

	}

	// endregion

}
