package approval.jobs;

import approval.jobs.mq.JobsQueue;
import approval.jobs.mq.MailExecutor;
import approval.jobs.mq.NoticeExecutor;

import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqQueue;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.util.List;

/**
 * 手で触るための口
 *
 * <p>
 * バッチとキューは、放っておくと<b>動いているのか止まっているのか分からない。</b>
 * ここは「動かしてみる」「覗いてみる」ためだけのルートである。
 * </p>
 *
 * <p>
 * <b>バッチのほうは書かなくてよい。</b>一覧・いま動かす・履歴・中断は
 * バッチ管理画面（要件 F-B-11）が持っている。{@link JobsApp} の {@code install} 1行で生える。
 * </p>
 */
public final class JobsController {

	private JobsController () {
	}

	// region 積む

	/**
	 * 申請を1件作る
	 *
	 * <p>締切バッチが拾うものを手で足す。</p>
	 *
	 * @param context	コンテキスト
	 */
	static void createRequest (WebContext context) {

		Data body = context.request().bodyAll();

		long staffId = body.getLong("staff_id");
		long amount = body.getLong("amount");
		int inDays = body.getInt("in_days");

		if (staffId <= 0 || amount <= 0) {
			throw new HttpException(400, "staff_id と amount を入れてください");
		}

		DB db = DBUtil.getMainDB();

		long id = db.insert("""
				INSERT INTO request (staff_id, amount, needed_on, status, created_at)
				VALUES (?, ?, current_date + %d, 'pending', NOW())
			""".formatted(inDays)
			, staffId, amount);

		if (db.isError()) {
			throw new HttpException(500, "申請を作れませんでした");
		}

		context.response().json("id", id);

	}

	/**
	 * 通知を1件積む
	 *
	 * <p>
	 * <b>{@code fail_until} を入れるとリトライが見られる。</b>
	 * </p>
	 * <pre>
	 * curl -XPOST localhost:8080/notices -d '{"kind":"mail","to":"a@example.co.jp","fail_until":1}'
	 * curl -XPOST localhost:8080/notices -d '{"kind":"mail","to":"a@example.co.jp","fail_until":99}'
	 * </pre>
	 *
	 * <p>
	 * 積むのは<b>トランザクションの中</b>である。キューは同じ DB のテーブルなので、
	 * ロールバックすれば積んだ行も消える（要件 F-M-03）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void queueNotice (WebContext context) {

		Data body = context.request().bodyAll();

		String kind = body.getStringOptional("kind");

		DB db = DBUtil.getMainDB();

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			long id = switch (kind) {

				case "mail" -> new MailExecutor().put(db, new Data()
					.putData("to", body.getStringOptional("to"))
					.putData("fail_until", body.getInt("fail_until")));

				case "notice" -> new NoticeExecutor().put(db, new Data()
					.putData("request_id", body.getLong("request_id"))
					.putData("to_staff_id", body.getLong("to_staff_id"))
					.putData("kind", NoticeExecutor.KIND_DUE_SOON));

				default -> throw new HttpException(400, "kind は mail か notice です: " + kind);

			};

			if (id < 0) {
				transaction.rollbackEndTransaction();
				throw new HttpException(500, "積めませんでした");
			}

			transaction.commitEndTransaction();

			context.response().json("id", id);

		} catch (HttpException ex) {

			throw ex;

		} catch (Exception ex) {

			Log.error(ex, "通知を積めませんでした");
			throw new HttpException(500, "積めませんでした");

		}

	}

	// endregion

	// region 覗く

	/**
	 * キューの様子を返す
	 *
	 * <p>
	 * <b>滞留数は自分で数えにいく。</b>フレームワークは勝手にゲージにしない——
	 * {@code pendingCount()} は SQL を1本飛ばすので、
	 * <b>誰も見ていないのに毎回引かれる</b>形にしないためである。
	 * メトリクスに出したければ、アプリが
	 * {@code Metrics.gauge("mq.notice.pending", () -> queue.pendingCount())} と書く。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void queueStatus (WebContext context) {

		MqQueue queue = new MqQueue(JobsQueue.NOTICE);

		context.response()
			.json("pending", queue.pendingCount())
			.json("dead", deadCount());

	}

	/**
	 * 溜まったデッドレターを返す
	 *
	 * <p>
	 * <b>ここはフレームワークが持っていない。</b>
	 * {@code dead} になった行は<b>消えずに残り続ける</b>（{@code completed} だけが消える）。
	 * 掃除する仕組みも、やり直す口も無い。
	 * </p>
	 * <p>
	 * <b>それでよい。</b>デッドレターは「人が見て決めるもの」で、
	 * <b>自動でやり直してよいなら、そもそも dead にする必要がない。</b>
	 * どう見せて、どう戻すかはアプリが決める——というのがこの1画面である。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void deadList (WebContext context) {

		DB db = DBUtil.getMainDB();

		List<Data> rows = db.selectList("""
				SELECT
					id, mq_key, retry_count, data, log_info, updated_at
				FROM
					%s
				WHERE
					status = ?
				ORDER BY
					id ASC
			""".formatted(db.dialect().identifier(JobsQueue.NOTICE))
			, MqStatus.dead.name());

		context.response().json("dead", rows);

	}

	/**
	 * デッドレターを1件やり直す
	 *
	 * <p>
	 * <b>{@code retry_count} を 0 に戻す。</b>戻さないと、
	 * 拾われた瞬間に上限を超えていてまた {@code dead} になる。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void deadRetry (WebContext context) {

		long id = context.request().bodyAll().getLong("id");

		DB db = DBUtil.getMainDB();

		int updated = db.update("""
				UPDATE %s SET
					status = ?
					, retry_count = 0
					, scheduled_at = NULL
					, updated_at = NOW()
				WHERE
					id = ?
					AND status = ?
			""".formatted(db.dialect().identifier(JobsQueue.NOTICE))
			, MqStatus.waiting.name()
			, id
			, MqStatus.dead.name());

		if (updated == 0) {
			throw new HttpException(404, "そのデッドレターはありません: " + id);
		}

		Log.info("デッドレターをやり直します: id=%d".formatted(id));

		context.response().json("id", id).json("status", MqStatus.waiting.name());

	}

	/**
	 * デッドレターの数
	 *
	 * @return	件数
	 */
	private static long deadCount () {

		DB db = DBUtil.getMainDB();

		Data row = db.select("SELECT COUNT(1) AS cnt FROM %s WHERE status = ?"
			.formatted(db.dialect().identifier(JobsQueue.NOTICE))
			, MqStatus.dead.name());

		return row == null ? 0 : row.getLong("cnt");

	}

	// endregion

}
