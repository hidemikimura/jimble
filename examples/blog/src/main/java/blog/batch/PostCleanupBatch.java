package blog.batch;

import db.blog_example.BlogExample;
import db.blog_example.table.post.Post;
import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.List;

/**
 * 下書きのまま古くなった記事を消すバッチ
 *
 * <pre>
 * java -cp app.jar blog.BlogBatch env=local class=blog.batch.PostCleanupBatch days=30
 * </pre>
 */
public class PostCleanupBatch extends AbstractBatch {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String batchName () {

		return "下書きの整理";

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>毎日 03:10（分 時 日 月 曜日）。</p>
	 */
	@Override
	public String cron () {

		return "10 3 * * *";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data defaultBatchSettings () {

		return new Data().putData("days", 30);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute (BatchArgs args) {

		// 引数があればそちら、無ければマスタの設定
		long days = args.cliArgs().containsKey("days")
			? args.cliArgs().getLong("days")
			: settings().getLong("days");

		List<Data> posts = BlogExample.db().selectList(
			SQL.select()
				.from(Post.instance())
				.where(Post.published.eq(false)));

		int deleted = 0;

		for (Data post : posts) {

			// 長時間バッチは中断指示を確認する（要件 F-B-06）
			if (isCancelOrder()) {
				Log.info("中断されました");
				break;
			}

			BlogExample.db().delete(
				SQL.delete(Post.instance())
					.where(Post.id.eq(post.getLong(Post.id))));

			deleted++;

		}

		// 実行情報はバッチ履歴に残る（要件 F-B-08）
		executeInfo().putData("days", days);
		executeInfo().putData("deleted", deleted);

		Log.info("下書きを %d 件消しました".formatted(deleted));

	}

}
