package blog.data;

import db.blog_example.BlogExample;
import db.blog_example.table.comment.Comment;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.util.data.async.AsyncList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 記事のコメント（遅延読み込み。要件 F-A-01〜05）
 *
 * <p>
 * <b>参照された時点ではじめて SQL が飛ぶ。</b>
 * 一覧のように「記事は返すがコメントは見ないかもしれない」場面で、
 * 見なかったぶんのクエリが消える。
 * </p>
 *
 * <pre>
 * Data post = ...;
 * post.put("comments", new CommentList(post.getLong(Post.id)));
 *
 * // ここまでは SQL が飛ばない
 * post.getJsonString();   // ← ここで飛ぶ
 * </pre>
 *
 * <p>
 * 逆に言うと、<b>一覧の全行にこれを付けて全部を JSON にすると N+1 になる。</b>
 * それを消すのが {@link #loadBatch(List)} である（要件 F-A-06）。
 * </p>
 *
 * <pre>
 * AsyncPrefetch.run(response);   // ← post_id IN (...) の1本にまとまる
 * </pre>
 */
public class CommentList extends AsyncList {

	private static final long serialVersionUID = 1L;

	/* 記事ID */
	private final long postId;

	/**
	 * コンストラクタ
	 *
	 * @param postId	記事ID
	 */
	public CommentList (long postId) {

		this.postId = postId;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
// docs:begin async-load
	protected List<Data> load () {

		return BlogExample.db().selectList(
			SQL.select()
				.from(Comment.instance())
				.where(Comment.post_id.eq(postId))
				.orderBy(Comment.created_at)
		);

	}
// docs:end

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>上の {@link #load()} と同じことを IN 句で書く</b>（要件 F-A-06）。
	 * 並べて置いてあるのは、片方だけ直すと結果がずれるからである。
	 * </p>
	 */
	@Override
// docs:begin async-load-batch
	protected Map<Object, List<Data>> loadBatch (List<Object> ids) {

		List<Data> rows = BlogExample.db().selectList(
			SQL.select()
				.from(Comment.instance())
				.where(Comment.post_id.in(ids))
				.orderBy(Comment.post_id, Comment.created_at)
		);

		if (rows == null) {
			// 引けなかった。1つも読み込み済みにしないよう、例外にして個別読みへ落とす
			throw new IllegalStateException("コメントを引けませんでした: " + BlogExample.db().getError());
		}

		Map<Object, List<Data>> byPost = new LinkedHashMap<>();

		for (Data row : rows) {
			byPost
				.computeIfAbsent(row.getLong(Comment.post_id), key -> new ArrayList<>())
				.add(row);
		}

		return byPost;

	}
// docs:end

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setData (Data data) {

		add(data.extractTableData(Comment.instance()));

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>まとめて読む単位（要件 F-A-10）。先読みが入ったらここを見る。</p>
	 */
	@Override
	public String batchKey () {

		return "CommentList";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object batchId () {

		return postId;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String hashKey () {

		return "CommentList:" + postId;

	}

}
