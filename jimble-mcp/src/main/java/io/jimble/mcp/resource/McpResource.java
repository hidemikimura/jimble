package io.jimble.mcp.resource;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

import java.util.List;

/**
 * リソース1つ
 *
 * <p>
 * モデルやユーザーに読ませる中身。ツールと違って<b>副作用を持たない。</b>
 * </p>
 */
public interface McpResource {

	/**
	 * 何のリソースか
	 *
	 * @return	説明
	 */
	String description ();

	/**
	 * 中身の型
	 *
	 * @return	型（{@code text/plain} など）
	 */
	default String mimeType () {

		return "text/plain";

	}

	/**
	 * 人が読む名前
	 *
	 * @return	名前。無ければ null
	 */
	default String title () {

		return null;

	}

	/**
	 * 読む
	 *
	 * @param context	コンテキスト
	 * @param uri		URI
	 * @return	中身
	 * @throws Exception	読めなかった場合
	 */
	String read (WebContext context, String uri) throws Exception;

	/**
	 * 読んだものを仕様の形にする
	 *
	 * @param uri	URI
	 * @param text	中身
	 * @return	JSON
	 */
	default Data toContents (String uri, String text) {

		Data content = new Data();
		content.put("uri", uri);
		content.put("mimeType", mimeType());
		content.put("text", text);

		Data data = new Data();
		data.put("resultType", io.jimble.mcp.McpProtocol.RESULT_TYPE_COMPLETE);
		data.put("contents", List.of(content));

		return data;

	}

}
