package blog.data;

import db.blog_example.BlogExample;
import db.blog_example.table.comment.Comment;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.util.data.async.AsyncList;

import java.util.List;

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
 * 先読み（要件 F-A-06。Phase 2）はそのためのものである。
 * </p>
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
