package io.jimble.mcp.tool;

import io.jimble.mcp.McpProtocol;
import io.jimble.util.data.Data;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * ツールの結果
 *
 * <pre>
 * return ToolResult.text("東京は晴れ、22度です");
 *
 * return ToolResult.of()
 *     .text("2件見つかりました")
 *     .structured(new Data().putData("count", 2));
 * </pre>
 *
 * <p>
 * <b>ツールの中で起きた失敗は例外にしない。</b>{@link #error(String)} で返す。
 * 例外にするとプロトコルのエラーになり、<b>モデルには直しようがない</b>
 * （{@link io.jimble.mcp.McpErrors} の説明を参照）。
 * 「日付の形式が違う」「値が範囲外」は、モデルが読んで直せる形で返すこと。
 * </p>
 */
public final class ToolResult {

	/* 中身 */
	private final List<Data> content = new ArrayList<>();

	/* 構造化した中身 */
	private Object structured;

	/* 失敗したか */
	private boolean failed;

	private ToolResult () {}

	/**
	 * 空から組み立てる
	 *
	 * @return	結果
	 */
	public static ToolResult of () {

		return new ToolResult();

	}

	/**
	 * 文字だけ返す
	 *
	 * @param text	文字
	 * @return	結果
	 */
	public static ToolResult text (String text) {

		return of().addText(text);

	}

	/**
	 * ツールの中で失敗したことを返す
	 *
	 * <p>
	 * <b>モデルが読んで直せるように書く。</b>
	 * 「失敗しました」ではなく「開始日が過去です。今日は 2026-09-06 です」。
	 * </p>
	 *
	 * @param message	内容
	 * @return	結果
	 */
	public static ToolResult error (String message) {

		ToolResult result = of().addText(message);
		result.failed = true;

		return result;

	}

	// region 中身を足す

	/**
	 * 文字を足す
	 *
	 * @param text	文字
	 * @return	自分
	 */
	public ToolResult addText (String text) {

		Data block = new Data();
		block.put("type", "text");
		block.put("text", text == null ? "" : text);

		content.add(block);

		return this;

	}

	/**
	 * 画像を足す
	 *
	 * @param bytes		中身
	 * @param mimeType	型（{@code image/png} など）
	 * @return	自分
	 */
	public ToolResult addImage (byte[] bytes, String mimeType) {

		Data block = new Data();
		block.put("type", "image");
		block.put("data", Base64.getEncoder().encodeToString(bytes));
		block.put("mimeType", mimeType);

		content.add(block);

		return this;

	}

	/**
	 * リソースへの参照を足す
	 *
	 * @param uri			URI
	 * @param name			名前
	 * @param mimeType		型
	 * @return	自分
	 */
	public ToolResult addResourceLink (String uri, String name, String mimeType) {

		Data block = new Data();
		block.put("type", "resource_link");
		block.put("uri", uri);
		block.put("name", name);

		if (mimeType != null) {
			block.put("mimeType", mimeType);
		}

		content.add(block);

		return this;

	}

	/**
	 * 構造化した中身を付ける
	 *
	 * <p>
	 * ツールに {@code outputSchema} があるなら、それに沿った形で返す。
	 * <b>あわせて同じものを文字でも入れておく</b>のが仕様の勧めるところである
	 * （古いクライアントは構造化を読まない）。
	 * </p>
	 *
	 * @param value	中身
	 * @return	自分
	 */
	public ToolResult structured (Object value) {

		this.structured = value;

		return this;

	}

	// endregion

	/**
	 * JSON にする
	 *
	 * @return	JSON
	 */
	public Data toData () {

		Data data = new Data();
		data.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		data.put("content", List.copyOf(content));

		if (structured != null) {
			data.put("structuredContent", structured);
		}

		data.put("isError", failed);

		return data;

	}

}
