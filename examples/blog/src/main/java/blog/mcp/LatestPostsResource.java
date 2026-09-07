package blog.mcp;

import blog.BlogApp;
import db.blog_example.table.post.Post;
import io.jimble.mcp.resource.McpResource;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

/**
 * 最近の記事（読むだけ）
 */
public class LatestPostsResource implements McpResource {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String description () {

		return "最近の記事の一覧";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String title () {

		return "最近の記事";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String mimeType () {

		return "text/markdown";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String read (WebContext context, String uri) {

		StringBuilder text = new StringBuilder("# 最近の記事\n\n");

		for (Data row : BlogApp.listPosts()) {

			Data post = row.extractTableData(Post.instance());

			text.append("- %s（%s）%n".formatted(
				post.getString(Post.title)
				, post.getDateString(Post.created_at, "yyyy-MM-dd")));

		}

		return text.toString();

	}

}
