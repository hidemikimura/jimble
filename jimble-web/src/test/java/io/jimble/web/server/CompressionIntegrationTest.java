package io.jimble.web.server;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圧縮するものを選ぶ（D-279）
 *
 * <p>
 * 2.5.1 までは、Accept-Encoding に gzip があれば<b>どの応答も</b>圧縮していた
 * （画像・ダウンロードを圧縮し直し、Content-Length が消えていた。50 バイトの JSON にも圧縮器を作っていた）。
 * </p>
 */
class CompressionIntegrationTest {

	private static final byte[] IMAGE = new byte[5000];

	private static JimbleServer server;

	private static File imageFile;

	@BeforeAll
	static void start () throws Exception {

		Conf.reload();

		imageFile = File.createTempFile("jimble-compression", ".png");
		Files.write(imageFile.toPath(), IMAGE);

		server = JimbleServer.start(new JimbleApp() {
			{
				get("/json-big", context -> {
					Data data = new Data();
					data.put("text", "あ".repeat(3000));
					context.response().send(data);
				});
				get("/json-small", context -> {
					Data data = new Data();
					data.put("ok", 1);
					context.response().send(data);
				});
				get("/text-big", context -> context.response().send("x".repeat(5000), "text/plain"));
				get("/text-stream", context -> context.response().stream(
					new ByteArrayInputStream("y".repeat(5000).getBytes(StandardCharsets.UTF_8)), "text/plain"));
				get("/image", context -> context.response().stream(new ByteArrayInputStream(IMAGE), "image/png", IMAGE.length));
				get("/image-file", context -> context.response().file(imageFile, "image/png"));
			}
		}, 0);

	}

	@AfterAll
	static void stop () {

		if (server != null) {
			server.stop();
		}

		if (imageFile != null) {
			imageFile.delete();
		}

	}

	@Test
	@DisplayName("D-279 文字の応答で 1KB 以上なら圧縮する（JSON・テキスト・長さの分からないストリーム）")
	void compressesLargeText () throws Exception {

		for (String path : new String[] {"/json-big", "/text-big", "/text-stream"}) {

			HttpResponse<byte[]> response = get(path, true);

			assertEquals(200, response.statusCode(), path);
			assertEquals(Optional.of("gzip"), response.headers().firstValue("content-encoding"), path + " を圧縮していない");

			try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
				assertTrue(in.readAllBytes().length >= 5000, path);
			}

		}

	}

	@Test
	@DisplayName("D-279 小さい応答・画像・ファイルは圧縮しない（Content-Length も残る）")
	void skipsSmallAndBinary () throws Exception {

		HttpResponse<byte[]> small = get("/json-small", true);
		assertTrue(small.headers().firstValue("content-encoding").isEmpty(), "50 バイトの JSON を圧縮している");
		assertEquals("{\"ok\":1}", new String(small.body(), StandardCharsets.UTF_8));

		for (String path : new String[] {"/image", "/image-file"}) {

			HttpResponse<byte[]> image = get(path, true);

			assertTrue(image.headers().firstValue("content-encoding").isEmpty(), path + " を圧縮し直している");
			assertEquals(Optional.of(String.valueOf(IMAGE.length)), image.headers().firstValue("content-length"), path + " の Content-Length が消えた");
			assertEquals(IMAGE.length, image.body().length);

		}

	}

	@Test
	@DisplayName("Accept-Encoding が無ければ、大きい文字の応答も圧縮しない（これまでどおり）")
	void noAcceptEncoding () throws Exception {

		HttpResponse<byte[]> response = get("/json-big", false);

		assertTrue(response.headers().firstValue("content-encoding").isEmpty());

	}

	@Test
	@DisplayName("圧縮するかの判定")
	void decision () {

		assertTrue(SelectiveContentEncoding.compressible("application/json; charset=UTF-8", -1));
		assertTrue(SelectiveContentEncoding.compressible("application/problem+json", 2000));
		assertTrue(SelectiveContentEncoding.compressible("image/svg+xml", 2000));
		assertTrue(SelectiveContentEncoding.compressible("Text/HTML", 1024));
		assertTrue(!SelectiveContentEncoding.compressible("text/html", 1023));
		assertTrue(!SelectiveContentEncoding.compressible("text/event-stream", -1));
		assertTrue(!SelectiveContentEncoding.compressible("image/png", -1));
		assertTrue(!SelectiveContentEncoding.compressible("application/octet-stream", -1));
		assertTrue(!SelectiveContentEncoding.compressible(null, 5000));

	}

	private static HttpResponse<byte[]> get (String path, boolean gzip) throws Exception {

		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));

		if (gzip) {
			builder.header("Accept-Encoding", "gzip");
		}

		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
		}

	}

}
