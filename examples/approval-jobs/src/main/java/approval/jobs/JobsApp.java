package approval.jobs;

import approval.jobs.batch.ArchiveChunkBatch;
import approval.jobs.batch.ReminderBatch;
import approval.jobs.mq.MailExecutor;
import approval.jobs.mq.NoticeExecutor;
import approval.jobs.mq.ReportExecutor;

import io.jimble.batch.BatchRegistry;
import io.jimble.batch.manager.BatchManagerController;
import io.jimble.db.DBUtil;
import io.jimble.mq.MqRegistry;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

/**
 * サンプル：時間のかかる仕事（残件 N-3）
 *
 * <pre>
 * ./gradlew :examples:approval-jobs:migrate
 * ./gradlew :examples:approval-jobs:codegen
 * ./gradlew :examples:approval-jobs:run
 * </pre>
 *
 * <h2>入口が3つある</h2>
 * <table>
 *   <caption>入口</caption>
 *   <tr><td>{@link JobsApp}</td><td>Web。管理画面と、手で触る口</td></tr>
 *   <tr><td>{@link JobsBatch}</td><td>バッチを1本だけ動かす（cron や手作業から）</td></tr>
 *   <tr><td>{@link JobsScheduler}</td><td>cron を回し、キューのワーカーも回す</td></tr>
 * </table>
 * <p>
 * <b>3つとも {@link Bootstrap#load()} を通る。</b>用意するものが揃っていないと、
 * 「Web だけ立てたら最初のリクエストが 500」のような形で出る。
 * </p>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code /jimble/batch/manager}</td>
 *       <td><b>バッチ管理画面</b>（F-B-11）。一覧・いま動かす・履歴・中断。Basic 認証つき</td></tr>
 *   <tr><td>{@code POST /requests}</td>
 *       <td>締切バッチが拾うものを手で足す</td></tr>
 *   <tr><td>{@code POST /notices}</td>
 *       <td><b>キューに積む</b>（F-M-03）。{@code fail_until} を入れるとリトライが見られる</td></tr>
 *   <tr><td>{@code GET /queue}</td>
 *       <td>滞留数と dead 数。<b>数えにいくのはアプリの仕事</b></td></tr>
 *   <tr><td>{@code GET /dead}</td>
 *       <td><b>デッドレター</b>（F-M-04）。フレームワークは掃除も再投入もしない</td></tr>
 *   <tr><td>{@code POST /dead/retry}</td>
 *       <td>1件だけ {@code waiting} に戻す</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>失敗する道を通す。</b>{@code examples/blog} の MQ は必ず成功するので、
 * リトライもデッドレターも一度も動いたことがなかった。
 * ここでは<b>落ちて、やり直されて、最後にあきらめる</b>ところまで実際に見える。
 * </p>
 * <p>
 * <b>2回実行されても壊れない書き方が、コードの形として出ている。</b>
 * 「気をつける」ではなく、<b>先に見る</b>と<b>DB にも言わせる</b>の2枚重ねである
 * （{@link NoticeExecutor}）。
 * </p>
 */
public class JobsApp extends JimbleApp {

	/**
	 * ルート定義
	 */
	public JobsApp () {

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			context.response().code(statusCode).text(cause.getMessage());

		});

		/*
		 * バッチ管理画面（要件 F-B-11）。
		 *
		 * <b>設定が揃っていなければ何も生えない。</b>
		 * batch_manager.username か password が空だと、
		 * 画面そのものを組み込まずにエラーログを出す
		 * （設定し忘れたときに誰でも見られる、という形にしないため）。
		 */
		install(BatchManagerController::new);

		post("/requests", JobsController::createRequest);

		post("/notices", JobsController::queueNotice);
		get("/queue", JobsController::queueStatus);

		get("/dead", JobsController::deadList);
		post("/dead/retry", JobsController::deadRetry);

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		/*
		 * Web でも登録しておく。
		 *
		 * <b>管理画面から「いま動かす」を押すと、この JVM ではなく
		 * スケジューラの JVM が実行する</b>（キューに1行積むだけ）ので、
		 * 動かすためだけなら要らない。
		 * それでも登録するのは、<b>マスタの一覧を出すのに要る</b>のと、
		 * Web からキューに積むのに Executor が要るためである。
		 */
		MqRegistry.add(NoticeExecutor::new);
		MqRegistry.add(MailExecutor::new);
		MqRegistry.add(ReportExecutor::new);

		BatchRegistry.add(ReminderBatch::new);
		BatchRegistry.add(ArchiveChunkBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		JimbleServer.start(new JobsApp());

	}

}
