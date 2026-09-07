package blog.mcp;

import blog.BlogApp;
import io.jimble.mcp.schema.JsonSchema;
import io.jimble.mcp.tool.McpTool;
import io.jimble.mcp.tool.ToolResult;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

/**
 * 記事を書く
 *
 * <p>
 * <b>副作用のあるツールである。</b>
 * MCP のクライアントは、実行する前に人に確認を出すことが求められている
 * （仕様 Security Considerations）。サーバー側でできるのは、
 * <b>何をするツールなのかを説明に正直に書く</b>ことである。
 * </p>
 */
public class CreatePostTool implements McpTool {

	/** タイトルの上限（DB の桁に合わせる） */
	private static final int MAX_TITLE = 250;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String description () {

		return "ブログに記事を1件書き込む。書き込みは取り消せない";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
// docs:begin mcp-input-schema
	public JsonSchema inputSchema () {

		return JsonSchema.object()
			.string("title", "タイトル（%d 文字まで）".formatted(MAX_TITLE)).required()
			.string("body", "本文")
			.bool("published", "すぐ公開するか（既定は非公開）");

	}
// docs:end

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ToolResult call (WebContext context, Data arguments) throws Exception {

		String title = arguments.getString("title");

		/*
		 * モデルが直せる失敗は isError で返す。
		 * 例外にすると、モデルには何が悪かったのか分からない。
		 */
		if (title.length() > MAX_TITLE) {
			return ToolResult.error(
				"タイトルが長すぎます（%d 文字）。%d 文字までにしてください"
					.formatted(title.length(), MAX_TITLE));
		}

		long id = BlogApp.insertPostWithNotice(arguments);

		if (id <= 0) {
			return ToolResult.error("記事を書き込めませんでした");
		}

		return ToolResult.text("記事を書きました（ID %d）".formatted(id))
			.structured(new Data().putData("id", id));

	}

}
