package io.jimble.mcp.tool;

import io.jimble.mcp.schema.JsonSchema;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

/**
 * ツール1つ
 *
 * <pre>
 * public class GetWeatherTool implements McpTool {
 *
 *     &#64;Override public String description () { return "指定した都市の天気を返す"; }
 *
 *     &#64;Override public JsonSchema inputSchema () {
 *         return JsonSchema.object()
 *             .string("city", "都市名").required();
 *     }
 *
 *     &#64;Override public ToolResult call (WebContext context, Data arguments) {
 *         return ToolResult.text(weatherOf(arguments.getString("city")));
 *     }
 *
 * }
 * </pre>
 *
 * <p>
 * <b>呼ばれるたびに1つ作る</b>（登録は {@code GetWeatherTool::new}）。
 * フィールドを持っても他の呼び出しと混ざらない（原則3。MQ の D-36 と同じ）。
 * </p>
 */
public interface McpTool {

	/**
	 * 何をするツールか
	 *
	 * <p>
	 * <b>ここはモデルが読む。</b>いつ使うべきかが分かるように書くこと。
	 * 「天気を返す」より「都市名から現在の天気（気温・天候）を返す。過去は返せない」。
	 * </p>
	 *
	 * @return	説明
	 */
	String description ();

	/**
	 * 入力の形
	 *
	 * <p>引数が無いなら {@link JsonSchema#empty()} を返す（null は仕様違反）。</p>
	 *
	 * @return	形
	 */
	JsonSchema inputSchema ();

	/**
	 * 実行する
	 *
	 * <p>
	 * <b>失敗は例外ではなく {@link ToolResult#error(String)} で返す。</b>
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param arguments	引数
	 * @return	結果
	 * @throws Exception	どうしようもない失敗（プロトコルのエラーになる）
	 */
	ToolResult call (WebContext context, Data arguments) throws Exception;

	/**
	 * 人が読む名前
	 *
	 * @return	名前。無ければ null
	 */
	default String title () {

		return null;

	}

	/**
	 * 出力の形
	 *
	 * @return	形。無ければ null
	 */
	default JsonSchema outputSchema () {

		return null;

	}

}
