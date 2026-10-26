package bench;

import io.jimble.util.convertor.UploadFile;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.RequestSource;
import io.jimble.web.http.ResponseSink;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cookie を 1 つ付けて応答する 1 回の CPU（Context を作る・Cookie を署名して付ける・本文を返す・後始末）
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class WebBench {

	@Benchmark
	public void cookieResponse (Blackhole bh) {

		Sink sink = new Sink();

		try (WebContext context = new WebContext(new Source(), sink)) {
			context.run(() -> {
				context.cookies().put("cart", "c0ffee-1234");
				context.response().send("ok");
			});
		}

		bh.consume(sink.headers);

	}

	/** 入力口（GET /cart） */
	static final class Source implements RequestSource {

		private final Map<String, String> headers = new HashMap<>();

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
		@Override public Map<String, String> cookies () { return Map.of(); }
		@Override public Map<String, List<String>> queryParams () { return Map.of(); }
		@Override public Map<String, List<String>> formParams () { return Map.of(); }
		@Override public List<UploadFile> files () { return List.of(); }
		@Override public String bodyText (Charset charset) { return ""; }

	}

	/** 出力口（書いたヘッダを持つだけ） */
	static final class Sink implements ResponseSink {

		final List<String> headers = new ArrayList<>();

		private boolean sent = false;

		@Override public boolean isSent () { return sent; }
		@Override public void status (int statusCode) { }
		@Override public void header (String name, String value) { headers.add(value); }
		@Override public void addHeader (String name, String value) { headers.add(value); }
		@Override public void send () { sent = true; }
		@Override public void send (String text) { sent = true; }
		@Override public void send (byte[] body) { sent = true; }
		@Override public void send (InputStream stream, long contentLength) { sent = true; }
		@Override public void sendFile (Path path, String fileName) { sent = true; }
		@Override public void redirect (String url) { sent = true; }
		@Override public OutputStream outputStream () { sent = true; return OutputStream.nullOutputStream(); }

	}

}
