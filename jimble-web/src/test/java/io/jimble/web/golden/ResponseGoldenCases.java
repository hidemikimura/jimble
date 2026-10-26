package io.jimble.web.golden;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.util.convertor.UploadFile;
import io.jimble.util.crypto.Signer;
import io.jimble.web.context.WebContext;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.http.RequestSource;
import io.jimble.web.http.ResponseSink;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cookie と応答のヘッダの「答え合わせ」に使う組み立て一式（2.7.5）
 *
 * <p>
 * 2.7.5 で Cookie・セキュリティヘッダ・応答の設定を、読み込んだ設定 1 つにつき 1 回だけ読む形にし、
 * 署名の Mac を鍵ごとに写して使う形にした。<b>設定を差し替えるたびに出てくるヘッダが 2.7.4 と同じか</b>を見る。
 * 答えは Maven Central の 2.7.4 の jar で {@link #render()} を動かして作る（{@link ResponseGoldenTest} を参照）。
 * <b>ここは 2.7.4 にもある API だけで書く。</b>
 * </p>
 *
 * <p>
 * 設定を順に差し替えて同じ JVM の中で続けて書き出すので、<b>前の設定の値が残っていればここで分かる</b>。
 * </p>
 */
public final class ResponseGoldenCases {

	/** 設定（名前と HOCON） */
	private static final List<String[]> CONFIGS = List.of(
		new String[] { "default", "" },
		new String[] { "signed", "cookie.secret = \"golden-secret-1\"" },
		new String[] { "signed rotated strict", """
			cookie {
			  secret = "golden-secret-2"
			  previous_secrets = ["golden-secret-1"]
			  same_site = strict
			  domain = "example.jp"
			  secure = false
			  http_only = false
			  max_age = 0s
			}
			""" },
		new String[] { "signed none 7d", """
			cookie {
			  secret = "golden-secret-1"
			  same_site = none
			  max_age = 7d
			  accept_unsigned = true
			  accept_legacy_signature = true
			}
			""" },
		new String[] { "hsts", """
			security_headers {
			  hsts = 365d
			  hsts_include_subdomains = true
			  frame_options = DENY
			  referrer_policy = no-referrer
			  content_type_options = nosniff
			}
			""" },
		new String[] { "headers off", "security_headers.enabled = false" },
		new String[] { "default again", "" }
	);

	private ResponseGoldenCases () {
	}

	/**
	 * 全部の設定で書き出す
	 *
	 * @return	書き出したもの
	 */
	public static String render () {

		StringBuilder out = new StringBuilder();

		try {

			for (String[] config : CONFIGS) {

				Conf.replace(ConfigFactory.parseString(config[1] + "\nserver.access_log = false"));

				out.append("== ").append(config[0]).append('\n');
				out.append("conf: secret=").append(CookieConf.secret())
					.append(" secrets=").append(CookieConf.secrets())
					.append(" signed=").append(CookieConf.isSigned())
					.append(" acceptUnsigned=").append(CookieConf.acceptUnsigned())
					.append(" acceptLegacy=").append(CookieConf.acceptLegacySignature())
					.append(" secure=").append(CookieConf.secure())
					.append(" httpOnly=").append(CookieConf.httpOnly())
					.append(" sameSite=").append(CookieConf.sameSite())
					.append(" domain=").append(CookieConf.domain())
					.append(" maxAge=").append(CookieConf.maxAge())
					.append('\n');

				out.append(response(Map.of()));

				// 前の鍵で署名した Cookie を読む（鍵を回したときに読めるか）
				String signed = Signer.signFor("cart", "c0ffee", "golden-secret-1");
				out.append("signFor: ").append(signed).append('\n');
				out.append(read(Map.of("cart", signed, "plain", "unsigned-value")));

			}

		} finally {
			Conf.reload();
		}

		return out.toString();

	}

	/**
	 * Cookie を付けて応答する
	 *
	 * @param cookies	リクエストの Cookie
	 * @return	書き出したもの
	 */
	private static String response (Map<String, String> cookies) {

		Sink sink = new Sink();

		try (WebContext context = new WebContext(new Source(cookies), sink)) {
			context.run(() -> {
				context.cookies().put("cart", "c0ffee-1234");
				context.cookies().put("pref", "x y;é", 3600);
				context.cookies().remove("old");
				context.response().send("ok");
			});
		}

		return "status: " + sink.status + "\n" + String.join("\n", sink.headers) + "\n";

	}

	/**
	 * Cookie を読む
	 *
	 * @param cookies	リクエストの Cookie
	 * @return	書き出したもの
	 */
	private static String read (Map<String, String> cookies) {

		StringBuilder out = new StringBuilder();

		try (WebContext context = new WebContext(new Source(cookies), new Sink())) {
			context.run(() -> {
				out.append("read cart: ").append(context.cookies().get("cart")).append('\n');
				out.append("read plain: ").append(context.cookies().get("plain")).append('\n');
				out.append("stale: ").append(context.cookies().staleNames()).append('\n');
			});
		}

		return out.toString();

	}

	/** 入力口 */
	private static final class Source implements RequestSource {

		private final Map<String, String> headers = new HashMap<>();

		private final Map<String, String> cookies;

		Source (Map<String, String> cookies) {
			this.cookies = new LinkedHashMap<>(cookies);
		}

		@Override public String method () { return "GET"; }
		@Override public String rawPath () { return "/cart"; }
		@Override public String path () { return "/cart"; }
		@Override public String url () { return "https://localhost/cart"; }
		@Override public String protocol () { return "HTTP/1.1"; }
		@Override public String scheme () { return "https"; }
		@Override public String host () { return "localhost"; }
		@Override public int port () { return 443; }
		@Override public String remoteAddress () { return "127.0.0.1"; }
		@Override public String query () { return ""; }
		@Override public Map<String, String> headers () { return headers; }
		@Override public Map<String, String> cookies () { return cookies; }
		@Override public Map<String, List<String>> queryParams () { return Map.of(); }
		@Override public Map<String, List<String>> formParams () { return Map.of(); }
		@Override public List<UploadFile> files () { return List.of(); }
		@Override public String bodyText (Charset charset) { return ""; }

	}

	/** 出力口（書いた順にヘッダを持つ） */
	private static final class Sink implements ResponseSink {

		private final List<String> headers = new ArrayList<>();

		private int status = 200;

		private boolean sent = false;

		@Override public boolean isSent () { return sent; }
		@Override public void status (int statusCode) { this.status = statusCode; }
		@Override public void header (String name, String value) { headers.add("header " + name + ": " + value); }
		@Override public void addHeader (String name, String value) { headers.add("add " + name + ": " + value); }
		@Override public void send () { sent = true; headers.add("send"); }
		@Override public void send (String text) { sent = true; headers.add("send " + text); }
		@Override public void send (byte[] body) { sent = true; headers.add("send bytes " + body.length); }
		@Override public void send (InputStream stream, long contentLength) { sent = true; headers.add("send stream"); }
		@Override public void sendFile (Path path, String fileName) { sent = true; }
		@Override public void redirect (String url) { sent = true; }
		@Override public OutputStream outputStream () { sent = true; return OutputStream.nullOutputStream(); }

	}

}
