package io.jimble.mcp;

import io.jimble.mcp.prompt.McpPrompt;
import io.jimble.mcp.resource.McpResource;
import io.jimble.mcp.tool.McpTool;
import io.jimble.util.log.Log;
import io.jimble.web.router.Controller;

import java.util.function.Supplier;

/**
 * MCP サーバー（要件 F-MCP-01）
 *
 * <pre>
 * public class WeatherMcp extends McpController {
 *
 *     {
 *         tool("get_weather", GetWeatherTool::new);
 *         tool("get_forecast", GetForecastTool::new);
 *
 *         resource("config://app", AppConfigResource::new);
 *
 *         prompt("summarize", SummarizePrompt::new);
 *     }
 *
 * }
 * </pre>
 *
 * <pre>
 * // アプリのルートに組み込む
 * install(WeatherMcp::new);
 * </pre>
 *
 * <p>
 * <b>上から読めば、このサーバーが何を公開しているかが全部分かる。</b>
 * アノテーションもクラスパスの走査も無い（原則1・原則2 / NF-P-03）。
 * Java の MCP 実装はたいてい注釈で宣言して起動時に走査するが、
 * <b>「どのクラスが拾われているか」が実行するまで分からない。</b>
 * </p>
 *
 * <p>
 * 公開する口は1本（既定 {@code POST /mcp}）。
 * <b>GET と DELETE は 405 で断る</b>（2026-07-28 でどちらも仕様から消えた）。
 * </p>
 */
public abstract class McpController extends Controller {

	/* 公開しているもの */
	private final McpRegistry registry = new McpRegistry();

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * ルートはここで生やす。<b>サブクラスの初期化ブロックより先に走る</b>ので、
	 * 実際の登録（{@code tool(...)}）はそのあとで {@link #registry} に入る。
	 * ハンドラは実行時に {@link #registry} を見るので、順番は問題にならない。
	 * </p>
	 */
	protected McpController () {

		String path = McpConf.path();

		McpHandler handler = new McpHandler(registry);

		post(path, handler::handle);

		/*
		 * 2025-11-25 までのクライアントは GET で常時接続を開き、
		 * DELETE でセッションを終わらせていた。どちらも仕様から消えた。
		 * 「何も起きない」より、はっきり断るほうがよい。
		 */
		get(path, context -> methodNotAllowed(context, "GET"));
		delete(path, context -> methodNotAllowed(context, "DELETE"));

		Log.info("MCP を公開しました: POST %s（仕様 %s）".formatted(path, McpProtocol.VERSION));

	}

	// region 登録（明示。要件 F-B-09 / F-M-07 と同じ形）

	/**
	 * ツールを公開する
	 *
	 * @param name		名前（英数字と {@code _ - .}、1〜128 文字）
	 * @param supplier	作るもの。<b>呼ばれるたびに1つ作る</b>
	 */
	protected void tool (String name, Supplier<McpTool> supplier) {

		registry.tool(name, supplier);

	}

	/**
	 * リソースを公開する
	 *
	 * @param uri		URI
	 * @param supplier	作るもの
	 */
	protected void resource (String uri, Supplier<McpResource> supplier) {

		registry.resource(uri, supplier);

	}

	/**
	 * プロンプトを公開する
	 *
	 * @param name		名前
	 * @param supplier	作るもの
	 */
	protected void prompt (String name, Supplier<McpPrompt> supplier) {

		registry.prompt(name, supplier);

	}

	// endregion

	/**
	 * 公開しているもの
	 *
	 * @return	公開しているもの
	 */
	public McpRegistry registry () {

		return registry;

	}

	/**
	 * 使えないメソッドだと返す
	 *
	 * @param context	コンテキスト
	 * @param method	メソッド
	 */
	private static void methodNotAllowed (io.jimble.web.context.WebContext context, String method) {

		context.response().code(405);
		context.response().setResponseHeader("Allow", "POST");
		context.response().json("error"
			, "%s は使えません。MCP %s では POST だけです".formatted(method, McpProtocol.VERSION));

	}

}
