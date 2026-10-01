package io.jimble.util.internal.http.httpclient;

import com.sun.net.httpserver.HttpServer;
import io.jimble.util.internal.http.httpclient.method.HttpGetExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP クライアントの上限と、JVM 全体の設定（D-225）
 */
class HttpExecutorLimitsTest {

	private static HttpServer server;
	private static String base;

	@BeforeAll
	static void start () throws Exception {

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

		// 解くと 5MB になる gzip（届くのは数 KB）
		server.createContext("/bomb", exchange -> {
			ByteArrayOutputStream gz = new ByteArrayOutputStream();
			try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
				out.write(new byte[5 * 1024 * 1024]);
			}
			exchange.getResponseHeaders().add("Content-Encoding", "gzip");
			exchange.sendResponseHeaders(200, gz.size());
			try (OutputStream body = exchange.getResponseBody()) {
				body.write(gz.toByteArray());
			}
		});

		server.createContext("/deflate", exchange -> {
			ByteArrayOutputStream z = new ByteArrayOutputStream();
			try (DeflaterOutputStream out = new DeflaterOutputStream(z)) {
				out.write("こんにちは deflate".getBytes(StandardCharsets.UTF_8));
			}
			exchange.getResponseHeaders().add("Content-Encoding", "deflate");
			exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
			exchange.sendResponseHeaders(200, z.size());
			try (OutputStream body = exchange.getResponseBody()) {
				body.write(z.toByteArray());
			}
		});

		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();

	}

	@AfterAll
	static void stop () {

		server.stop(0);

	}

	@Test
	@DisplayName("解いたあとの大きさで上限を見る（gzip 爆弾でメモリを使い切らない）")
	void maxResponseSize () {

		HttpGetExecutor http = new HttpGetExecutor().setUrl(base + "/bomb").setMaxResponseSize(1024 * 1024).execute();

		assertEquals(200, http.responseCode);
		assertTrue(http.getContentText() == null || http.getContentText().isEmpty(), "上限を超えた本文を読んでいます");

	}

	@Test
	@DisplayName("deflate を解く（かつては DeflaterInputStream で、もう一度圧縮していた）")
	void deflate () {

		HttpGetExecutor http = new HttpGetExecutor().setUrl(base + "/deflate").execute();

		assertEquals("こんにちは deflate", http.getContentText());

	}

	@Test
	@DisplayName("SSL エラーを無視しても、JVM 全体のホスト名の確認を切らない")
	void ignoreSslErrorStaysLocal () {

		new HttpGetExecutor().setUrl(base + "/deflate").setIgnoreSslError(true).execute();

		assertNull(System.getProperty("jdk.internal.httpclient.disableHostnameVerification")
			, "JVM 全体の設定を変えています（ほかの HttpClient もホスト名を確かめなくなる）");

	}

}
