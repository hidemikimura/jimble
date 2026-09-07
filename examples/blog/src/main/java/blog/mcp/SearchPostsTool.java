package blog.mcp;

import db.blog_example.BlogExample;
import db.blog_example.table.post.Post;
import io.jimble.db.sql.SQL;
import io.jimble.mcp.schema.JsonSchema;
import io.jimble.mcp.tool.McpTool;
import io.jimble.mcp.tool.ToolResult;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 記事を探す
 */
public class SearchPostsTool implements McpTool {

	/** 一度に返せる上限 */
	private static final int MAX_LIMIT = 50;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String description () {

		/*
		 * ここはモデルが読む。
		 * 「いつ使うか」と「何ができないか」を書く。
		 */
		return "ブログの記事をタイトルで探す。本文の全文検索はできない";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public JsonSchema inputSchema () {

		return JsonSchema.object()
			.string("keyword", "タイトルに含まれる語").required()
			.integer("limit", "返す件数（既定 10、最大 %d）".formatted(MAX_LIMIT)).min(1).max(MAX_LIMIT);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public JsonSchema outputSchema () {

		return JsonSchema.object()
			.integer("count", "見つかった件数")
			.array("posts", "記事", JsonSchema.object()
				.integer("id", "記事ID")
				.string("title", "タイトル"));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ToolResult call (WebContext context, Data arguments) {

		String keyword = arguments.getString("keyword");

		int limit = arguments.containsKey("limit") ? arguments.getInt("limit") : 10;
		limit = Math.clamp(limit, 1, MAX_LIMIT);

		List<Data> rows = BlogExample.db().selectList(
			SQL.select()
				.from(Post.instance())
				.where(Post.title.like("%" + keyword + "%"))
				.orderByDesc(Post.created_at)
				.limit(limit));

		if (rows == null) {
			// DB のエラーは戻り値で返る（要件 F-D-11）
			return ToolResult.error("記事を検索できませんでした");
		}

		List<Data> posts = new ArrayList<>();
		StringBuilder text = new StringBuilder();

		for (Data row : rows) {

			Data post = row.extractTableData(Post.instance());

			posts.add(new Data()
				.putData("id", post.getLong(Post.id))
				.putData("title", post.getString(Post.title)));

			text.append("- [%d] %s%n".formatted(post.getLong(Post.id), post.getString(Post.title)));

		}

		if (posts.isEmpty()) {
			return ToolResult.text("「%s」を含む記事はありませんでした".formatted(keyword))
				.structured(new Data().putData("count", 0).putData("posts", posts));
		}

		return ToolResult.text("%d 件見つかりました。%n%s".formatted(posts.size(), text))
			.structured(new Data().putData("count", posts.size()).putData("posts", posts));

	}

}
