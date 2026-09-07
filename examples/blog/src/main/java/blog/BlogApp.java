package blog;

import blog.data.CommentList;
import blog.mcp.BlogMcp;
import blog.mq.NoticeExecutor;
import blog.ws.PostFeedHandler;
import db.blog_example.BlogExample;
import db.blog_example.table.comment.Comment;
import db.blog_example.table.post.Post;
import io.jimble.batch.manager.BatchManagerController;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.db.migration.Migration;
import io.jimble.db.sql.SQL;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.cors.Cors;
import io.jimble.web.cors.CorsHandler;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.http.HttpException;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import java.util.Date;
import java.util.List;

/**
 * サンプルアプリケーション
 *
 * <p>
 * <b>フレームワークの機能を一通り通すためのもの</b>である（要件 10.3 / 11.1）。
 * 「これが動く＝その機能が動く」という形にしてある。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:blog:codegen -Pjimble.autoGenerate=true
 * ./gradlew :examples:blog:run
 * </pre>
 *
 * <p>
 * {@code db.blog_example.*} は生成コードである。<b>手で書かない。</b>
 * </p>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code GET /}</td><td>jte / DB</td></tr>
 *   <tr><td>{@code GET /posts}</td><td>JSON</td></tr>
 *   <tr><td>{@code GET /posts/{id}}</td><td><b>AsyncList</b>（コメントの遅延読み込み）</td></tr>
 *   <tr><td>{@code POST /posts}</td><td><b>トランザクション + MQ</b></td></tr>
 *   <tr><td>{@code PUT / PATCH / DELETE /posts/{id}}</td><td><b>REST 風のメソッド</b></td></tr>
 *   <tr><td>{@code OPTIONS /posts}</td><td><b>CORS のプリフライト</b></td></tr>
 *   <tr><td>{@code GET /posts.csv}</td><td><b>ストリーミング + CSV</b></td></tr>
 *   <tr><td>{@code GET /form} / {@code POST /form}</td><td><b>CSRF / Flash / Cookie / アップロード</b></td></tr>
 * </table>
 */
public class BlogApp extends JimbleApp {

	/** 一覧で返す件数 */
	private static final int LIST_LIMIT = 20;

	/**
	 * ルート定義
	 */
	public BlogApp () {

		/*
		 * ブラウザの別オリジンから叩けるようにする（要件 F-W-12）。
		 * 許すオリジンは列挙する。allAllowOrigin() は開発のときだけにすること。
		 */
		before(new CorsHandler(new Cors()
			.addAllowOrigin("https://example.com")
			.addAllowedMethod("GET")
			.addAllowedMethod("POST")
			.addAllowedMethod("PUT")
			.addAllowedMethod("PATCH")
			.addAllowedMethod("DELETE")
			.addAllowHeader("Content-Type")
			.addAllowHeader(Csrf.HEADER_NAME)
			.addExposeHeader("X-Total-Count")));

		/*
		 * 例外はここで整形する。ルートに当たらなかった 404 もここへ来る（要件 F-R-09b）。
		 * HTML を求められていれば HTML、そうでなければ JSON で返す。
		 */
		error((context, cause, statusCode) -> {

			if (context.request().acceptJson() || !context.request().accept().contains("text/html")) {
				context.response().json("error", cause.getMessage());
				return;
			}

			context.response().send("エラー: %d %s".formatted(statusCode, cause.getMessage()));

		});

		// 一覧（HTML。要件 F-W-08）
// docs:begin view-route
		get("/", context -> {

			context.response().putData("title", "ブログ");
			context.response().putData("posts", listPosts());
			context.response().view("blog/posts.jte");

		});
// docs:end

		// バッチ管理画面（要件 F-B-11）。設定が揃っていなければ何も生えない
		install(BatchManagerController::new);

		install(PostController::new);
		install(FormController::new);

		// MCP サーバー（要件 F-MCP-01）。POST /mcp
		install(BlogMcp::new);

		// 記事の更新を流す（要件 F-W-22）。ws://.../ws/posts
		ws("/ws/posts", PostFeedHandler::new);

	}

	/**
	 * エントリポイント
	 *
	 * @param args	コマンドライン引数
	 */
	public static void main (String[] args) {

		// 起動時マイグレーション。ローカルでは何もしない（要件 F-G-07 / F-G-15）
		Migration.install();

		DBUtil.load(Conf.conf().config(), BlogApp.class);

		JimbleServer.start(new BlogApp());

	}

	// region 共通で使うもの

	/**
	 * 記事を1件引く
	 *
	 * @param id	記事ID
	 * @return	記事
	 * @throws HttpException	無い場合
	 */
	static Data findPost (long id) {

		Data row = BlogExample.db().select(
			SQL.select()
				.from(Post.instance())
				.where(Post.id.eq(id))
		);

		if (row == null) {
			throw new HttpException(404, "記事がありません: " + id);
		}

		return row;

	}

	/**
	 * 記事の一覧を引く
	 *
	 * @return	一覧
	 */
	public static List<Data> listPosts () {

		return BlogExample.db().selectList(
			SQL.select()
				.from(Post.instance())
				.orderByDesc(Post.created_at)
				.limit(LIST_LIMIT)
		);

	}

	/**
	 * 記事にコメントを付ける（遅延読み込み。要件 F-A-01）
	 *
	 * <p>
	 * <b>ここでは SQL は飛ばない。</b>コメントを参照した時点で飛ぶ。
	 * </p>
	 *
	 * @param row	記事の行
	 * @return	記事
	 */
	static Data withComments (Data row) {

		Data post = row.extractTableData(Post.instance());

		post.put("comments", new CommentList(post.getLong(Post.id)));

		return post;

	}

	/**
	 * コメントを足す
	 *
	 * @param db		DB
	 * @param postId	記事ID
	 * @param name		名前
	 * @param body		本文
	 * @return	コメントID
	 * @throws Exception	入れられなかった場合
	 */
	static long insertComment (DB db, long postId, String name, String body) throws Exception {

		return db.insert(
			SQL.insert(Comment.instance())
				.value(Comment.post_id, postId)
				.value(Comment.name, name)
				.value(Comment.body, body)
				.value(Comment.created_at, new Date())
		);

	}

	/**
	 * 記事を1件入れ、お知らせをキューに積む（要件 F-M-03）
	 *
	 * <p>
	 * <b>同じトランザクションで行う。</b>ロールバックすればキューも消えるので、
	 * 「記事は入らなかったのにお知らせだけ飛ぶ」が起きない。
	 * </p>
	 *
	 * @param request	リクエスト
	 * @return	記事ID。入らなければ -1
	 * @throws Exception	キューに積めなかった場合
	 */
	public static long insertPostWithNotice (Data request) throws Exception {

		DB db = BlogExample.db();

// docs:begin transaction-mq
		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			long id = db.insert(
				SQL.insert(Post.instance())
					.value(Post.title, request.getString("title"))
					.value(Post.body, request.getString("body"))
					.value(Post.image_name, request.getStringOptional("image_name"))
					.value(Post.published, request.getBoolean("published"))
					.value(Post.created_at, new Date())
			);

			if (id <= 0) {
				transaction.rollbackEndTransaction();
				return -1;
			}

			new NoticeExecutor().put(db, new Data()
				.putData("post_id", id)
				.putData("title", request.getString("title")));

			transaction.commitEndTransaction();

			/*
			 * コミットしてから流す。
			 * 先に流すと、ロールバックしたときに
			 * 「入っていない記事のお知らせ」だけが届く。
			 */
			PostFeedHandler.notifyNewPost(request.getStringOptional("title"));

			return id;

		}
// docs:end

	}

	// endregion

}
