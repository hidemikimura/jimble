package io.jimble.web.server;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.convertor.UploadFile;
import io.jimble.util.data.Data;
import io.jimble.web.upload.UploadConf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ファイルアップロードの確認（要件 F-W-06）
 *
 * <p>
 * <b>実際に helidon を起動して multipart を投げる。</b>
 * 一時ファイルの後始末まで見たいので、単体テストでは足りない。
 * </p>
 */
class UploadIntegrationTest {

	/** 境界文字列 */
	private static final String BOUNDARY = "----jimbleTestBoundary";

	/* サーバー */
	private static JimbleServer server;

	/* 受け取った内容（ハンドラから書き込む） */
	private static final List<String> received = new ArrayList<>();

	/* 受け取った一時ファイル */
	private static final List<Path> tempFiles = new ArrayList<>();

	@BeforeAll
	static void startServer () {

		Conf.reload();

		JimbleApp app = new JimbleApp() {
			{
				post("/upload", context -> {

					Data body = context.request().bodyAll();

					received.add("title=" + body.getStringOptional("title"));

					// docs:begin upload-read
					for (Object value : context.request().bodyFile().values()) {
						if (value instanceof List<?> list) {
							for (Object item : list) {
								UploadFile uploadFile = (UploadFile) item;
								received.add("%s:%s:%d".formatted(
									uploadFile.name, uploadFile.fileName, uploadFile.fileSize));
								received.add("content=" + read(uploadFile.file.toPath()));
								tempFiles.add(uploadFile.file.toPath());
							}
						}
					}
					// docs:end

					context.response().send("ok");

				});
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

	@AfterEach
	void clear () {

		received.clear();
		tempFiles.clear();
		Conf.reload();

	}

	// region テスト

	@Test
	@DisplayName("ファイルとフォーム項目を同時に受け取れる")
	void uploadFileAndForm () throws Exception {

		HttpResponse<String> response = post(multipart(
			field("title", "ロゴ画像")
			, file("logo", "logo.png", "PNG-DATA")
		));

		assertEquals(200, response.statusCode());

		// multipart のフォーム項目も取れること。
		// ファイルとフォームは同じ本体に混ざっているので、片方だけ読むと他方が消える
		assertTrue(received.contains("title=ロゴ画像"), received.toString());
		assertTrue(received.contains("logo:logo.png:8"), received.toString());
		assertTrue(received.contains("content=PNG-DATA"), received.toString());

	}

	@Test
	@DisplayName("複数ファイルを受け取れる")
	void uploadMultipleFiles () throws Exception {

		post(multipart(
			file("images", "a.png", "AAA")
			, file("images", "b.png", "BBBB")
		));

		assertTrue(received.contains("images:a.png:3"), received.toString());
		assertTrue(received.contains("images:b.png:4"), received.toString());

	}

	@Test
	@DisplayName("リクエストが終わると一時ファイルは消える")
	void tempFileIsRemoved () throws Exception {

		post(multipart(file("logo", "logo.png", "PNG-DATA")));

		assertEquals(1, tempFiles.size());

		// 後始末はコンテキストのクローズで走る。応答が返った直後は競合しうるので少し待つ
		for (int i = 0; i < 50 && Files.exists(tempFiles.getFirst()); i++) {
			Thread.sleep(20);
		}

		assertTrue(Files.notExists(tempFiles.getFirst()),
			"一時ファイルが残っている: " + tempFiles.getFirst());

	}

	@Test
	@DisplayName("上限を超えたファイルは 413 で断る")
	void rejectsTooLargeFile () throws Exception {

		Conf.replace(ConfigFactory
			.parseString(UploadConf.KEY_MAX_FILE_SIZE + " = 10B")
			.withFallback(Conf.conf().config()));

		HttpResponse<String> response = post(multipart(
			file("big", "big.bin", "0123456789ABCDEF")
		));

		assertEquals(413, response.statusCode());

	}

	@Test
	@DisplayName("上限で断ったときも一時ファイルを残さない")
	void cleansUpAfterRejection () throws Exception {

		Conf.replace(ConfigFactory
			.parseString(UploadConf.KEY_MAX_FILE_SIZE + " = 10B")
			.withFallback(Conf.conf().config()));

		long before = countTempFiles();

		post(multipart(file("big", "big.bin", "0123456789ABCDEF")));

		Thread.sleep(200);

		assertEquals(before, countTempFiles(), "一時ファイルが残っている");

	}

	@Test
	@DisplayName("multipart でないリクエストは今までどおり")
	void plainFormStillWorks () throws Exception {

		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create(url("/upload")))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("title=%E3%83%86%E3%82%B9%E3%83%88"))
				.build()
			, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(received.contains("title=テスト"), received.toString());

	}

	// endregion

	// region ヘルパー

	/**
	 * multipart を投げる
	 *
	 * @param body	本体
	 * @return	レスポンス
	 * @throws Exception	通信に失敗した場合
	 */
	private HttpResponse<String> post (byte[] body) throws Exception {

		return HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create(url("/upload")))
				.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build()
			, HttpResponse.BodyHandlers.ofString());

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
	 * multipart の本体を組み立てる
	 *
	 * @param parts	パート
	 * @return	本体
	 * @throws IOException	組み立てに失敗した場合
	 */
	private byte[] multipart (byte[]... parts) throws IOException {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		for (byte[] part : parts) {
			out.write(part);
		}

		out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

		return out.toByteArray();

	}

	/**
	 * フォーム項目のパート
	 *
	 * @param name	名前
	 * @param value	値
	 * @return	パート
	 */
	private byte[] field (String name, String value) {

		return ("--" + BOUNDARY + "\r\n"
			+ "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
			+ "\r\n"
			+ value + "\r\n").getBytes(StandardCharsets.UTF_8);

	}

	/**
	 * ファイルのパート
	 *
	 * @param name		名前
	 * @param fileName	ファイル名
	 * @param content	中身
	 * @return	パート
	 */
	private byte[] file (String name, String fileName, String content) {

		return ("--" + BOUNDARY + "\r\n"
			+ "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n"
			+ "Content-Type: application/octet-stream\r\n"
			+ "\r\n"
			+ content + "\r\n").getBytes(StandardCharsets.UTF_8);

	}

	/**
	 * 一時ファイルを読む
	 *
	 * @param path	パス
	 * @return	中身
	 */
	private static String read (Path path) {

		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			return "";
		}

	}

	/**
	 * 一時ディレクトリにある jimble の一時ファイル数
	 *
	 * @return	件数
	 */
	private long countTempFiles () {

		try (Stream<Path> paths = Files.list(UploadConf.tempDir())) {
			return paths.filter(path -> path.getFileName().toString().startsWith(UploadConf.TEMP_PREFIX)).count();
		} catch (IOException ex) {
			return 0;
		}

	}

	// endregion

}
