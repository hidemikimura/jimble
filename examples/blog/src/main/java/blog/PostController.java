package blog;

import db.blog_example.BlogExample;
import db.blog_example.table.post.Post;
import io.jimble.db.sql.SQL;
import io.jimble.db.data.ResultSetFetcher;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.util.csv.CsvWriter;
import io.jimble.util.data.Data;
import io.jimble.web.http.HttpException;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Controller;
import io.jimble.web.sse.SseStream;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 記事の口（要件 F-R-02 / F-W-07）
 *
 * <p>
 * <b>REST 風のメソッドを一通り通す。</b>
 * サンプルが GET と POST しか使っていないと、
 * PUT / PATCH / DELETE / OPTIONS が動かなくなっても誰も気づかない（要件 10.3）。
 * </p>
 */
public class PostController extends Controller {

	/** CSV に出す件数の上限（無制限にすると事故になる） */
	private static final int CSV_LIMIT = 10000;

	{

		// 一覧（JSON）
		// docs:begin json-route
		get("/posts", context -> context.response().json("posts", BlogApp.listPosts()));
		// docs:end

		/*
		 * 1件（JSON）。コメントは遅延読み込み（要件 F-A-01）。
		 *
		 * JSON にする時点でコメントの SQL が飛ぶ。
		 * 「参照されたら読む」ので、返さない経路では飛ばない。
		 */
		get("/posts/{id}", context ->
			context.response().json("post", BlogApp.withComments(BlogApp.findPost(id(context)))));

		// 登録（トランザクション + MQ。要件 F-M-03）
		post("/posts", context -> {

			long id = BlogApp.insertPostWithNotice(context.request().bodyAll());

			if (id <= 0) {
				context.response().code(500).json("error", "登録できませんでした");
				return;
			}

			context.response().code(201).json("id", id);

		});

		/*
		 * 全部置き換える（PUT）。
		 * 送られてこなかった項目は既定値に戻る。
		 */
		put("/posts/{id}", context -> {

			long id = id(context);
			BlogApp.findPost(id);

			Data request = context.request().bodyAll();

			int updated = BlogExample.db().update(
				SQL.update(Post.instance())
					.set(Post.title, request.getString("title"))
					.set(Post.body, request.getStringOptional("body"))
					.set(Post.published, request.getBoolean("published"))
					.where(Post.id.eq(id))
			);

			context.response().json("updated", updated);

		});

		/*
		 * 送られてきた項目だけ変える（PATCH）。
		 *
		 * <b>「空文字で送ってきた」と「送ってこなかった」を区別する。</b>
		 * bodyAll() に鍵があるかどうかで見る。
		 */
// docs:begin rest-patch
		patch("/posts/{id}", context -> {

			long id = id(context);
			BlogApp.findPost(id);

			Data request = context.request().bodyAll();

			UpdateBuilder builder = SQL.update(Post.instance());
			boolean hasChange = false;

			if (request.containsKey("title")) {
				builder.set(Post.title, request.getString("title"));
				hasChange = true;
			}

			if (request.containsKey("body")) {
				builder.set(Post.body, request.getString("body"));
				hasChange = true;
			}

			if (request.containsKey("published")) {
				builder.set(Post.published, request.getBoolean("published"));
				hasChange = true;
			}

			if (!hasChange) {
				throw new HttpException(400, "変える項目がありません（title / body / published）");
			}

			int updated = BlogExample.db().update(builder.where(Post.id.eq(id)));

			context.response().json("updated", updated);

		});
// docs:end

		// 消す
		delete("/posts/{id}", context -> {

			long id = id(context);
			BlogApp.findPost(id);

			int deleted = BlogExample.db().delete(
				SQL.delete(Post.instance()).where(Post.id.eq(id)));

			context.response().json("deleted", deleted);

		});

		/*
		 * プリフライト（要件 F-W-19）。
		 * CorsHandler がヘッダを付けるので、ここは空で返すだけでよい。
		 * OPTIONS は Executor のキューを回さない。
		 */
		options("/posts", context -> context.response().send());
		options("/posts/{id}", context -> context.response().send());

		// CSV（ストリーミング。要件 F-W-07）
		get("/posts.csv", PostController::exportCsv);

		/*
		 * 進捗を送りながら処理する（SSE。要件 F-W-21）。
		 *
		 * 有限のぶんを送って閉じる形にしてある。
		 * 「相手が切るまで回し続ける」は書かないこと（SseStream の説明を参照）。
		 */
// docs:begin sse-route
		get("/posts/reindex", context -> {

			List<Data> posts = BlogApp.listPosts();

			try (SseStream sse = context.response().sse()) {

				sse.send("start", new Data().putData("total", posts.size()));

				int done = 0;

				for (Data row : posts) {

					if (!sse.isOpen()) {
						break;
					}

					Data post = row.extractTableData(Post.instance());
					done++;

					sse.send("progress", new Data()
						.putData("done", done)
						.putData("total", posts.size())
						.putData("title", post.getString(Post.title)));

				}

				sse.send("done", new Data().putData("done", done));

			}

		});
// docs:end

	}

	/**
	 * CSV で書き出す（要件 F-W-07）
	 *
	 * <p>
	 * <b>全件をメモリに載せない。</b>フェッチャで1行ずつ受けて、
	 * そのまま出力ストリームへ書く（要件 F-D-12）。
	 * 件数が増えても使うメモリが変わらない。
	 * </p>
	 *
	 * <p>
	 * ヘッダは本文を書き始める前に決まっていないといけないので、
	 * <b>1行目を書く前にすべて設定しておく</b>こと。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @throws Exception	書き出しに失敗した場合
	 */
	private static void exportCsv (WebContext context) throws Exception {

		context.response().setResponseHeader("Content-Type", "text/csv; charset=UTF-8");
		context.response().setResponseHeader("Content-Disposition", "attachment; filename=\"posts.csv\"");

		try (ResultSetFetcher fetcher = new ResultSetFetcher()) {

			BlogExample.db().selectListWithFetcher(fetcher
				, SQL.select()
					.from(Post.instance())
					.orderBy(Post.id)
					.limit(CSV_LIMIT));

			try (OutputStream out = context.response().outputStream();
				 OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
				 CsvWriter csv = new CsvWriter(writer)) {

				csv.writeLine("id", "title", "published", "created_at");

				for (Data row : fetcher) {

					Data post = row.extractTableData(Post.instance());

					csv.writeLine(
						post.getString(Post.id)
						, post.getStringOptional(Post.title)
						, post.getBoolean(Post.published)
						, post.getStringOptional(Post.created_at)
					);

				}

			}

		}

	}

	/**
	 * パスの記事ID
	 *
	 * @param context	コンテキスト
	 * @return	記事ID
	 */
	private static long id (WebContext context) {

		return context.request().bodyAll().getLong("id");

	}

}
