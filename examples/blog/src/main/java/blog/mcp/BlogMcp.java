package blog.mcp;

import io.jimble.mcp.McpController;

/**
 * ブログを MCP で公開する（要件 F-MCP-01）
 *
 * <pre>
 * curl -s http://localhost:9000/mcp \
 *   -H 'Content-Type: application/json' \
 *   -H 'MCP-Protocol-Version: 2026-07-28' \
 *   -H 'Mcp-Method: tools/list' \
 *   -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
 * </pre>
 *
 * <p>
 * <b>ここを上から読めば、このサーバーが何を公開しているかが全部分かる。</b>
 * 注釈もクラスパスの走査も無い（原則1・原則2）。
 * </p>
 */
public class BlogMcp extends McpController {

	{

// docs:begin mcp-controller
		tool("search_posts", SearchPostsTool::new);
		tool("create_post", CreatePostTool::new);

		resource("blog://latest", LatestPostsResource::new);
// docs:end

	}

}
