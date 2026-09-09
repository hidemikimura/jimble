package io.jimble.mcp;

import io.jimble.core.lifecycle.Shutdown;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import io.jimble.web.call.CallRequest;
import io.jimble.web.call.Calls;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * MCP を標準入出力で受ける（要件 F-MCP-12）
 *
 * <p>
 * クライアントがこのプロセスを起こし、標準入出力で会話する。
 * <b>1行に1つの JSON-RPC メッセージ</b>で、改行を中に含めてはいけない（仕様）。
 * </p>
 *
 * <pre>{@code
 * public class BlogStdio {
 *
 *     public static void main (String[] args) throws Exception {
 *
 *         Bootstrap.load();                       // DB など、起動時に用意するもの
 *
 *         App app = new App();
 *
 *         McpStdio.run(app, app.mcp().registry());
 *
 *     }
 *
 * }
 * }</pre>
 *
 * <pre>
 * // クライアント側の設定（例）
 * {"command": "java", "args": ["-cp", "app.jar", "blog.BlogStdio"]}
 * </pre>
 *
 * <h2>ポートは開かない</h2>
 * <p>
 * HTTP サーバーは立てないが、<b>ルート表とディスパッチャは組む</b>ので、
 * 実装済みの API をそのままツールにする {@code RouteTool}（要件 F-MCP-15）が
 * <b>stdio でもそのまま効く</b>。{@code before} の認証も検証も同じ道を通る。
 * </p>
 *
 * <h2>標準出力に余計なものを書かせない</h2>
 * <p>
 * <b>これがいちばん壊れやすいところである。</b>仕様は「サーバーは stdout に
 * MCP のメッセージ以外を書いてはならない」と定めているが、
 * <b>logback の既定の出力先は stdout である</b>。ログが1行混ざるだけで
 * クライアントは「壊れた JSON が来た」として切る。
 * </p>
 * <p>
 * そこで {@link #run} は<b>本物の stdout を自分だけが持ち、
 * {@code System.out} を stderr に差し替える</b>。
 * アプリが {@code System.out.println} を書いても、logback がそこへ出しても、
 * 全部 stderr へ流れる（クライアントは stderr を無視してよいと決まっている）。
 * </p>
 */
public final class McpStdio {

	/** 本物の標準出力（差し替える前に取っておく） */
	private static volatile PrintStream out;

	/** 書き込みの錠（読み取りの輪と、通知を流すスレッドが同じ口を使う） */
	private static final Object LOCK = new Object();

	private McpStdio () {
	}

	/**
	 * 標準入出力で受け付ける（アプリのルートも使う）
	 *
	 * @param app		アプリケーション。{@code RouteTool} を使うなら要る
	 * @param registry	公開しているもの
	 * @throws Exception どうしようもない失敗
	 */
	public static void run (JimbleApp app, McpRegistry registry) throws Exception {

		run(app == null ? null : new Dispatcher(app), registry, System.in, stdout());

	}

	/**
	 * 標準入出力で受け付ける（アプリのルートを使わない）
	 *
	 * <p>
	 * <b>{@code RouteTool} は使えない。</b>ルート表が無いので、
	 * 呼ばれたときに「アプリを渡してください」と返す。
	 * </p>
	 *
	 * @param registry	公開しているもの
	 * @throws Exception どうしようもない失敗
	 */
	public static void run (McpRegistry registry) throws Exception {

		run((Dispatcher) null, registry, System.in, stdout());

	}

	/**
	 * 本物の標準出力を UTF-8 で開き直す
	 *
	 * <p>
	 * <b>{@code System.out} をそのまま使わない。</b>あれは JVM が
	 * 端末の文字コードで作ったもので、{@code LANG} が決まっていない環境では
	 * ASCII になる。{@link #write} はバイトで書くので実害は無いが、
	 * <b>口のほうも揃えておく</b>。
	 * </p>
	 *
	 * @return 標準出力
	 */
	private static PrintStream stdout () {

		return new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);

	}

	/**
	 * 受け付ける
	 *
	 * <p>テストから入出力を差し替えるために分けてある。</p>
	 *
	 * @param dispatcher	ディスパッチャ。無ければ null
	 * @param registry		公開しているもの
	 * @param in			入力
	 * @param realOut		出力（差し替える前の本物）
	 * @throws Exception どうしようもない失敗
	 */
	static void run (Dispatcher dispatcher, McpRegistry registry, InputStream in, PrintStream realOut) throws Exception {

		out = realOut;

		/*
		 * <b>System.out を潰す。</b>ここから先、誰が書いても stderr へ行く。
		 * ログが1行混ざるだけでクライアントは切るので、
		 * 「書かないように気をつける」ではなく<b>書けなくする</b>。
		 */
		System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8));

		McpDispatch dispatch = new McpDispatch(registry);

		Log.info("MCP を標準入出力で待ち受けます（仕様 %s / ルート表=%s）".formatted(
			McpProtocol.VERSION, dispatcher == null ? "なし" : "あり"));

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

			String line;

			while ((line = reader.readLine()) != null) {

				if (line.isBlank()) {
					continue;
				}

				handle(dispatch, dispatcher, line);

			}

		} finally {

			/*
			 * <b>標準入力が閉じたら終わる</b>（仕様。いちばん確実な終了の合図）。
			 * 開いている購読はきれいに閉じてから、預かっているものを止める。
			 */
			McpSubscriptions.closeAll();

			Shutdown.runAll();

			Log.info("MCP の標準入出力を閉じました");

		}

	}

	/**
	 * 1行を処理する
	 *
	 * @param dispatch		振り分け
	 * @param dispatcher	ディスパッチャ。無ければ null
	 * @param line			1行
	 */
	private static void handle (McpDispatch dispatch, Dispatcher dispatcher, String line) {

		Data body;

		try {

			body = Dson.decodes(line, Data.class);

		} catch (Exception ex) {

			/*
			 * 読めない行。<b>id が分からないので null で返す</b>（仕様どおり）。
			 * 黙って捨てると、クライアントは応答を待ち続ける。
			 *
			 * <b>ここは実際にはほとんど通らない。</b>{@code Dson} は
			 * 壊れた行でも例外にせず空の {@code Data} を返すので、
			 * 「読めません」を出しているのは {@link McpDispatch} のほうである
			 * （どちらも同じ {@code PARSE_ERROR} を返すので、外からは同じに見える）。
			 */
			write(McpErrors.of(null, McpErrors.PARSE_ERROR, "JSON として読めません"));
			return;

		}

		Object id = body == null ? null : body.get("id");

		/*
		 * <b>1行ごとに1つコンテキストを作る。</b>
		 * 実行 ID もログも DB の接続も、HTTP のリクエストとまったく同じ扱いになる。
		 */
		CallRequest request = CallRequest.of("POST", McpConf.path())
			.body("application/json", line);

		Calls.root(request, context -> {

			if (dispatcher != null) {
				context.dispatcher(dispatcher);
			}

			context.run(() -> {

				try {

					McpResponse response = dispatch.handle(context, body, null);

					if (response.stream()) {
						listen(id, body.getDataOptional("params"));
						return;
					}

					if (response.body() != null) {
						write(response.body());
					}

				} catch (Exception ex) {

					Log.error(ex, "MCP の要求の処理に失敗しました");

					write(McpErrors.of(id, McpErrors.INTERNAL_ERROR, "要求の処理に失敗しました"));

				}

			});

		});

	}

	/**
	 * 購読を開く（要件 F-MCP-13）
	 *
	 * <p>
	 * <b>ここで待たない。</b>stdio は入出力が1本ずつしか無いので、
	 * 待つと<b>取り消しの通知を読めなくなり、二度と閉じられない</b>。
	 * 通知は別のスレッド（アプリが {@code McpNotify} を呼んだところ）から
	 * 同じ標準出力へ流れる。
	 * </p>
	 *
	 * @param id		購読の識別子
	 * @param params	引数
	 */
	private static void listen (Object id, Data params) {

		McpSubscriptions.open(id, params, McpStdio::write);

	}

	/**
	 * 1つ書き出す
	 *
	 * <p>
	 * <b>改行を中に含めない</b>（仕様 MUST）。{@code Data} の JSON は
	 * もともと1行なので、そのまま書ける。
	 * </p>
	 *
	 * @param message メッセージ
	 */
	private static void write (Data message) {

		PrintStream stream = out;

		if (stream == null) {
			return;
		}

		/*
		 * <b>自分で UTF-8 のバイトにしてから書く。</b>
		 * {@code print} に任せると、その {@code PrintStream} の文字コードが使われる——
		 * 端末の文字コードが決まっていない環境（{@code LANG=C} のコンテナなど）では
		 * <b>日本語が全部 {@code ?} になって出る</b>。しかも例外は出ないので、
		 * <b>動いているように見えてしまう</b>。仕様は UTF-8 と決めている。
		 */
		byte[] bytes = (message.getJsonString() + '\n').getBytes(StandardCharsets.UTF_8);

		/*
		 * <b>錠が要る。</b>読み取りの輪と、購読の通知を流すスレッドが
		 * 同じ1本の出力を使う。混ざると<b>行の途中で別のメッセージが割り込む</b>。
		 */
		synchronized (LOCK) {
			stream.write(bytes, 0, bytes.length);
			stream.flush();
		}

	}

}
