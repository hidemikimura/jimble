package io.jimble.mcp;

/**
 * 変わったことをクライアントに知らせる（要件 F-MCP-13）
 *
 * <p>
 * <b>アプリが呼ぶ。</b>jimble はリソースの中身を知らないので、
 * いつ変わったのかも知りようがない。
 * </p>
 *
 * <pre>{@code
 * // 記事を保存したあと
 * postDao.save(post);
 * McpNotify.resourceUpdated("blog://posts/" + post.getId());
 * }</pre>
 *
 * <p>
 * 誰も購読していなければ何も起きない（{@code Map} を1つ回るだけ）。
 * 購読していても、<b>そのリソースを頼んだ相手にしか届かない</b>。
 * </p>
 *
 * <h2>ツールとプロンプトの一覧は変わらない</h2>
 * <p>
 * <b>{@code tools/list_changed} と {@code prompts/list_changed} は用意していない。</b>
 * ツールもプロンプトも起動時に明示登録する（原則2 / 要件 F-MCP-01）ので、
 * 動いているあいだに増えも減りもしない。<b>発火しようが無いものの口を作ると、
 * 「呼んでいるのに届かない」で悩ませる</b>だけである。
 * </p>
 */
public final class McpNotify {

	private McpNotify () {
	}

	/**
	 * リソースが変わったことを知らせる
	 *
	 * @param uri 変わったリソースの URI
	 */
	public static void resourceUpdated (String uri) {

		if (uri == null || uri.isEmpty()) {
			return;
		}

		McpSubscriptions.resourceUpdated(uri);

	}

	/**
	 * リソースの一覧が変わったことを知らせる
	 *
	 * <p>
	 * 登録そのものは起動時に決まるが、<b>1つの登録が返す中身が増減する</b>ことはある
	 * （DB の行を1件ずつリソースとして見せている場合など）。
	 * </p>
	 */
	public static void resourcesListChanged () {

		McpSubscriptions.resourcesListChanged();

	}

}
