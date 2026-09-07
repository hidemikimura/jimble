package io.jimble.web.support;

import io.jimble.util.convertor.UploadFile;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.RequestSource;
import io.jimble.web.http.ResponseSink;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.List;
import java.util.Map;

/**
 * テスト用の入力口・出力口
 *
 * <p>HTTP サーバーなしで WebContext を組み立てる（要件 F-C-10）。</p>
 */
public final class Fakes {

	/**
	 * コンストラクタ
	 */
	private Fakes () {

	}

	/**
	 * コンテキストを作る
	 *
	 * @param method	メソッド
	 * @param rawPath	生のパス
	 * @return	コンテキスト
	 */
	public static WebContext context (String method, String rawPath) {

		return new WebContext(new FakeRequestSource(method, rawPath), new FakeResponseSink());

	}

	/**
	 * コンテキストの出力口を取り出す
	 *
	 * @param context	コンテキスト
	 * @return	出力口
	 */
	public static FakeResponseSink sink (WebContext context) {

		return (FakeResponseSink) context.attribute("test.sink");

	}

	/**
	 * テスト用の入力口
	 */
	public static final class FakeRequestSource implements RequestSource {

		private final String method;
		private final String rawPath;
		private final Map<String, String> headers = new HashMap<>();
		private final Map<String, String> cookies = new HashMap<>();
		private final Map<String, List<String>> queryParams = new LinkedHashMap<>();
		private final Map<String, List<String>> formParams = new LinkedHashMap<>();
		private String bodyText = "";

		public FakeRequestSource(String method, String rawPath) {
			this.method = method;
			this.rawPath = rawPath;
		}

		/**
		 * ヘッダを設定する
		 *
		 * @param name	ヘッダ名（小文字）
		 * @param value	値
		 * @return	自身
		 */
		public FakeRequestSource header (String name, String value) {
			headers.put(name.toLowerCase(), value);
			return this;
		}

		@Override public String method () { return method; }
		@Override public String rawPath () { return rawPath; }
		@Override public String path () { return rawPath; }
		@Override public String url () { return "http://localhost" + rawPath; }
		@Override public String protocol () { return "HTTP/1.1"; }
		@Override public String scheme () { return "http"; }
		@Override public String host () { return "localhost"; }
		@Override public int port () { return 9000; }
		@Override public String remoteAddress () { return "127.0.0.1"; }
		@Override public String query () { return ""; }
		@Override public Map<String, String> headers () { return headers; }
		@Override public Map<String, String> cookies () { return cookies; }

		/** 受信 Cookie を足す */
		public FakeRequestSource cookie (String name, String value) {
			cookies.put(name, value);
			return this;
		}

		/** クエリパラメータを足す */
		public FakeRequestSource query (String name, String...values) {
			queryParams.computeIfAbsent(name, k -> new ArrayList<>()).addAll(List.of(values));
			return this;
		}

		/** フォームパラメータを足す */
		public FakeRequestSource form (String name, String...values) {
			formParams.computeIfAbsent(name, k -> new ArrayList<>()).addAll(List.of(values));
			return this;
		}

		/** 本文（JSON など）を設定する */
		public FakeRequestSource body (String contentType, String text) {
			this.bodyText = text;
			return header("Content-Type", contentType);
		}
		@Override public Map<String, List<String>> queryParams () { return queryParams; }
		@Override public Map<String, List<String>> formParams () { return formParams; }
		@Override public List<UploadFile> files () { return List.of(); }
		@Override public String bodyText (Charset charset) { return bodyText; }

	}

	/**
	 * テスト用の出力口
	 */
	public static final class FakeResponseSink implements ResponseSink {

		private int status = 200;
		private boolean sent = false;
		private String body = null;
		private final Map<String, String> headers = new LinkedHashMap<>();
		private final Map<String, List<String>> addedHeaders = new LinkedHashMap<>();
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		@Override public boolean isSent () { return sent; }

		@Override public void status (int statusCode) { this.status = statusCode; }

		@Override public void header (String name, String value) { headers.put(name, value); }

		@Override
		public void addHeader (String name, String value) {
			addedHeaders.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
		}

		/** 足されたヘッダ（Set-Cookie など） */
		public List<String> addedHeaders (String name) {
			return addedHeaders.getOrDefault(name, List.of());
		}

		/** Set-Cookie の一覧 */
		public List<String> setCookies () {
			return addedHeaders("Set-Cookie");
		}

		@Override
		public void send () {
			markSent(null);
		}

		@Override
		public void send (String text) {
			markSent(text);
		}

		@Override
		public void send (byte[] value) {
			markSent(new String(value, StandardCharsets.UTF_8));
		}

		@Override
		public void send (InputStream stream, long contentLength) {
			try {
				markSent(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public void sendFile (Path path, String fileName) {
			markSent("file:" + path);
		}

		@Override
		public void redirect (String url) {
			this.status = 302;
			headers.put("Location", url);
			markSent(null);
		}

		@Override
		public OutputStream outputStream () {
			sent = true;
			return buffer;
		}

		/**
		 * 送信済みにする
		 */
		private void markSent (String value) {
			if (sent) {
				throw new IllegalStateException("Response already sent");
			}
			this.body = value;
			this.sent = true;
		}

		/**
		 * ステータスコード
		 *
		 * @return	ステータスコード
		 */
		public int status () { return status; }

		/**
		 * ボディ
		 *
		 * @return	ボディ
		 */
		public String body () {
			if (body != null) {
				return body;
			}
			String buffered = buffer.toString(StandardCharsets.UTF_8);
			return buffered.isEmpty() ? null : buffered;
		}

		/**
		 * ヘッダ
		 *
		 * @return	ヘッダ
		 */
		public Map<String, String> headers () { return headers; }

	}

}
