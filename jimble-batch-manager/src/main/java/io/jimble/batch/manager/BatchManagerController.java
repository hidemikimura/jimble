package io.jimble.batch.manager;

import io.jimble.batch.BatchExecutor;
import io.jimble.batch.scheduler.CronSchedule;
import io.jimble.batch.scheduler.SchedulerControl;
import io.jimble.batch.scheduler.mq.ExecuteBatchExecutor;
import io.jimble.batch.scheduler.mq.SchedulerQueue;
import io.jimble.batch.scheduler.mq.ReExecuteBatchExecutor;
import io.jimble.batch.status.BatchHistoryStatus;
import io.jimble.batch.status.BatchMasterStatus;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.data.SelectListResponse;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.paging.Paging;
import io.jimble.web.assets.AssetHandler;
import io.jimble.web.auth.BasicAuth;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * バッチ管理画面（要件 F-B-11）
 *
 * <pre>
 * install(BatchManagerController::new);
 * </pre>
 *
 * <p>
 * 設定が揃っていなければ<b>何も登録しない</b>（{@link BatchManagerConf}）。
 * </p>
 *
 * <table>
 *   <caption>エンドポイント（{@code batch_manager.path} の下）</caption>
 *   <tr><th>メソッド</th><th>パス</th><th>内容</th></tr>
 *   <tr><td>GET</td><td>{@code /}</td><td>画面</td></tr>
 *   <tr><td>POST</td><td>{@code /api/history}</td><td>履歴一覧</td></tr>
 *   <tr><td>GET</td><td>{@code /api/history/{id}}</td><td>履歴詳細</td></tr>
 *   <tr><td>POST</td><td>{@code /api/history/{id}/cancel}</td><td>中断を指示する</td></tr>
 *   <tr><td>POST</td><td>{@code /api/history/{id}/re-execute}</td><td>同じ引数でやり直す</td></tr>
 *   <tr><td>POST</td><td>{@code /api/master}</td><td>バッチ一覧</td></tr>
 *   <tr><td>POST</td><td>{@code /api/master/{class_name}}</td><td>cron と設定を保存する</td></tr>
 *   <tr><td>POST</td><td>{@code /api/master/{class_name}/status}</td><td>有効・無効</td></tr>
 *   <tr><td>POST</td><td>{@code /api/master/{class_name}/execute}</td><td>いま動かす</td></tr>
 *   <tr><td>GET</td><td>{@code /api/status}</td><td>全体の状態</td></tr>
 *   <tr><td>POST</td><td>{@code /api/status/change}</td><td>全体の入り切り</td></tr>
 * </table>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>Basic 認証が任意だった。</b>{@code public static BasicAuthentication basicAuthentication = null;}
 *       という差し替え可能なフィールドで、<b>設定し忘れると管理 API が誰でも叩ける状態で
 *       公開されていた。</b>しかも何の警告も出ない。
 *       jimble は<b>認証情報が無ければ登録しない</b>（要件 F-B-11 は「Basic 認証つき」）</li>
 *   <li><b>状態を変える操作が GET だった</b>（中断・やり直し・実行）。
 *       リンクを踏んだだけで、あるいは<b>クローラが辿っただけでバッチが動く。</b>
 *       すべて POST にした</li>
 *   <li><b>cron を検証せずに保存していた。</b>打ち間違えるとスケジューラが黙って読み飛ばし、
 *       <b>そのバッチだけ動かなくなる。</b>保存時に確かめて 400 を返す</li>
 *   <li><b>ステータスに {@code nothing} を設定できた。</b>これは
 *       「コードから消えた」ことを表す内部の値で、人が付けるものではない</li>
 *   <li>知らない {@code kind} の状態変更を<b>黙って 200 で返していた</b></li>
 * </ol>
 */
public final class BatchManagerController extends Controller {

	/** 画面のリソース置き場 */
	public static final String ASSET_DIR = "jimble/batch-manager";

	/**
	 * コンストラクタ（設定から組み立てる）
	 */
	public BatchManagerController () {

		this(BatchManagerConf.enabled()
			, BatchManagerConf.path()
			, BatchManagerConf.username()
			, BatchManagerConf.password()
			, BatchManagerConf.realm());

	}

	/**
	 * コンストラクタ（手で全部決める）
	 *
	 * <p>
	 * <b>{@code batch_manager.enabled} は見ない（D-173）。</b>
	 * 引数で全部渡している以上、<b>ここで設定を混ぜると
	 * 「渡したのに生えない」が起きる</b>——それはそれで分かりにくい。
	 * </p>
	 *
	 * <p>
	 * <b>設定で切り替えたいなら、引数無しの
	 * {@link #BatchManagerController()} を使うこと。</b>
	 * かつてはこの違いが書かれていなかったので、
	 * <b>{@code batch_manager.enabled = false} を全環境に配っても、
	 * この書き方のアプリだけ管理画面が開いたままだった</b>。
	 * </p>
	 *
	 * @param basePath	パス
	 * @param username	ユーザー名
	 * @param password	パスワード
	 * @param realm		realm
	 */
	public BatchManagerController (String basePath, String username, String password, String realm) {

		this(true, basePath, username, password, realm);

	}

	/**
	 * コンストラクタ
	 *
	 * @param enabled	組み込むか
	 * @param basePath	パス
	 * @param username	ユーザー名
	 * @param password	パスワード
	 * @param realm		realm
	 */
	private BatchManagerController (boolean enabled, String basePath, String username, String password, String realm) {

		if (!enabled) {
			Log.info("バッチ管理画面は無効です: %s".formatted(BatchManagerConf.KEY_ENABLED));
			return;
		}

		if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
			/*
			 * 認証が無いなら公開しない。
			 * 移送元は「認証を付け忘れたまま公開」ができてしまっていた。
			 */
			Log.error("バッチ管理画面を組み込みませんでした: %s と %s を設定してください"
				.formatted(BatchManagerConf.KEY_USERNAME, BatchManagerConf.KEY_PASSWORD));
			return;
		}

		BasicAuth auth = BasicAuth.of(username, password, realm);

		AssetHandler assets = new AssetHandler(ASSET_DIR);

		path(basePath, () -> {

			before(auth::handle);

			get("/", context -> assets.send(context, "/" + ASSET_DIR + "/index.html"));

			post("/api/history", this::historyList);
			get("/api/history/{id}", this::historyDetail);
			post("/api/history/{id}/cancel", this::historyCancel);
			post("/api/history/{id}/re-execute", this::historyReExecute);

			post("/api/master", this::masterList);
			get("/api/master/{class_name}", this::masterDetail);
			post("/api/master/{class_name}", this::masterSave);
			post("/api/master/{class_name}/status", this::masterStatus);
			post("/api/master/{class_name}/execute", this::masterExecute);

			get("/api/status", this::status);
			post("/api/status/change", this::statusChange);

		});

		Log.info("バッチ管理画面を組み込みました: %s".formatted(basePath));

	}

	// region 履歴

	/**
	 * 履歴一覧
	 *
	 * @param context	コンテキスト
	 */
	private void historyList (WebContext context) {

		Data request = context.request().bodyAll();
		Paging paging = context.request().paging();

		StringBuilder where = new StringBuilder();
		SQLParameterList params = new SQLParameterList();

		String className = request.getStringOptional("class_name");

		if (!className.isEmpty()) {
			where.append(where.isEmpty() ? " WHERE " : " AND ").append(" class_name = ? ");
			params.add(className);
		}

		List<String> statuses = request.getStringListOptional("status");

		if (statuses != null && !statuses.isEmpty()) {

			where.append(where.isEmpty() ? " WHERE " : " AND ").append(" status IN (");

			for (int i = 0; i < statuses.size(); i++) {
				where.append(i > 0 ? ", ?" : "?");
				params.add(statuses.get(i));
			}

			where.append(") ");

		}

		Date from = request.getDate("starts_at_from");
		Date to = request.getDate("starts_at_to");

		if (from != null) {
			where.append(where.isEmpty() ? " WHERE " : " AND ").append(" starts_at >= ? ");
			params.add(from);
		}

		if (to != null) {
			where.append(where.isEmpty() ? " WHERE " : " AND ").append(" starts_at <= ? ");
			params.add(to);
		}

		List<Object> all = new ArrayList<>(params);
		all.add(paging.per());
		all.add(paging.start() - 1);

		SelectListResponse response = DBUtil.getMainDB().selectListWithRowCount("""
				SELECT
					id, class_name, name, status, cancel_status, starts_at, ends_at, required_time
				FROM
					batch_history
				%s
				ORDER BY
					id DESC
				LIMIT ?
				OFFSET ?
			""".formatted(where)
			, all.toArray());

		if (response == null) {
			context.response().send(500);
			return;
		}

		paging.set(response.list().size(), response.rowCount());

		context.response().json("rows", response.list());

	}

	/**
	 * 履歴詳細
	 *
	 * @param context	コンテキスト
	 */
	private void historyDetail (WebContext context) {

		Data row = DBUtil.getMainDB().select(
			"SELECT * FROM batch_history WHERE id = ?", context.request().bodyAll().getLong("id"));

		if (row == null) {
			context.response().code(404).json("error", "履歴がありません");
			return;
		}

		context.response().json("row", row);

	}

	/**
	 * 中断を指示する（要件 F-B-06）
	 *
	 * @param context	コンテキスト
	 */
	private void historyCancel (WebContext context) {

		long id = context.request().bodyAll().getLong("id");

		DB db = DBUtil.getMainDB();

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			Data row = db.select("SELECT status FROM batch_history WHERE id = ? FOR UPDATE", id);

			if (row == null) {
				transaction.rollbackEndTransaction();
				context.response().code(404).json("error", "履歴がありません");
				return;
			}

			if (!BatchHistoryStatus.in_process.name().equals(row.getStringOptional("status"))) {
				transaction.rollbackEndTransaction();
				context.response().code(409).json("error", "実行中ではありません");
				return;
			}

			db.update("UPDATE batch_history SET cancel_status = 1 WHERE id = ?", id);

			if (db.isError()) {
				transaction.rollbackEndTransaction();
				context.response().code(500).json("error", "中断を指示できませんでした");
				return;
			}

			transaction.commitEndTransaction();

			context.response().json("ok", true);

		} catch (Exception ex) {

			Log.error(ex, "中断の指示に失敗しました: id=%d".formatted(id));
			context.response().code(500).json("error", "中断を指示できませんでした");

		}

	}

	/**
	 * 同じ引数でやり直す
	 *
	 * @param context	コンテキスト
	 */
	private void historyReExecute (WebContext context) throws Exception {

		long id = context.request().bodyAll().getLong("id");

		Data row = DBUtil.getMainDB().select("SELECT id FROM batch_history WHERE id = ?", id);

		if (row == null) {
			context.response().code(404).json("error", "履歴がありません");
			return;
		}

		if (!SchedulerControl.isRunning()) {
			context.response().code(409).json("error", "スケジューラが動いていません");
			return;
		}

		try (DB db = DBUtil.getMainDB()) {

			long queueId = new ReExecuteBatchExecutor().request(db, id);

			if (queueId <= 0) {
				context.response().code(500).json("error", queueError(db));
				return;
			}

		}

		context.response().json("ok", true);

	}

	// endregion

	// region マスタ

	/**
	 * バッチ一覧
	 *
	 * @param context	コンテキスト
	 */
	private void masterList (WebContext context) {

		Paging paging = context.request().paging();

		SelectListResponse response = DBUtil.getMainDB().selectListWithRowCount("""
				SELECT
					*
				FROM
					batch_master
				WHERE
					status <> ?
				ORDER BY
					class_name ASC
				LIMIT ?
				OFFSET ?
			"""
			, BatchMasterStatus.nothing.name()
			, paging.per()
			, paging.start() - 1);

		if (response == null) {
			context.response().send(500);
			return;
		}

		paging.set(response.list().size(), response.rowCount());

		context.response().json("rows", response.list());

	}

	/**
	 * バッチ詳細
	 *
	 * @param context	コンテキスト
	 */
	private void masterDetail (WebContext context) {

		Data row = DBUtil.getMainDB().select("SELECT * FROM batch_master WHERE class_name = ?"
			, context.request().bodyAll().getString("class_name"));

		if (row == null) {
			context.response().code(404).json("error", "バッチがありません");
			return;
		}

		context.response().json("row", row);

	}

	/**
	 * cron と設定を保存する
	 *
	 * @param context	コンテキスト
	 */
	private void masterSave (WebContext context) {

		Data request = context.request().bodyAll();

		String className = request.getString("class_name");
		String cron = request.getStringOptional("cron");
		Data settings = request.getDataOptional("settings");

		/*
		 * 移送元は cron を確かめずに保存していた。
		 * 打ち間違えるとスケジューラが黙って読み飛ばし、そのバッチだけ動かなくなる。
		 */
		if (!cron.isEmpty() && CronSchedule.parse(cron) == null) {
			context.response().code(400).json("error", "cron を読めません: " + cron);
			return;
		}

		DB db = DBUtil.getMainDB();

		db.update("UPDATE batch_master SET cron = ?, settings = ? WHERE class_name = ?"
			, cron, settings, className);

		if (db.isError()) {
			context.response().code(500).json("error", "保存できませんでした");
			return;
		}

		context.response().json("ok", true);

	}

	/**
	 * 有効・無効を切り替える
	 *
	 * @param context	コンテキスト
	 */
	private void masterStatus (WebContext context) {

		Data request = context.request().bodyAll();

		String className = request.getString("class_name");
		String status = request.getStringOptional("status");

		/*
		 * nothing は「コードから消えた」ことを表す内部の値なので、人が付けるものではない。
		 * 移送元は valueOf() が通れば何でも受け付けていた。
		 */
		if (!BatchMasterStatus.enable.name().equals(status)
			&& !BatchMasterStatus.disable.name().equals(status)) {

			context.response().code(400).json("error", "指定できるのは enable か disable です");
			return;

		}

		DB db = DBUtil.getMainDB();

		int count = db.update("UPDATE batch_master SET status = ? WHERE class_name = ? AND status <> ?"
			, status, className, BatchMasterStatus.nothing.name());

		if (db.isError()) {
			context.response().code(500).json("error", "変更できませんでした");
			return;
		}

		if (count <= 0) {
			context.response().code(404).json("error", "バッチがありません");
			return;
		}

		context.response().json("ok", true);

	}

	/**
	 * いま動かす
	 *
	 * @param context	コンテキスト
	 */
	private void masterExecute (WebContext context) throws Exception {

		String className = context.request().bodyAll().getString("class_name");

		Data row = DBUtil.getMainDB().select("SELECT class_name FROM batch_master WHERE class_name = ?", className);

		if (row == null) {
			context.response().code(404).json("error", "バッチがありません");
			return;
		}

		if (!SchedulerControl.isRunning()) {
			context.response().code(409).json("error", "スケジューラが動いていません");
			return;
		}

		try (DB db = DBUtil.getMainDB()) {

			long queueId = new ExecuteBatchExecutor().request(db, className);

			if (queueId <= 0) {
				context.response().code(500).json("error", queueError(db));
				return;
			}

		}

		context.response().json("ok", true);

	}

	/**
	 * 依頼を積めなかった理由（要件 F-X-05 / D-74）
	 *
	 * <p>
	 * <b>「依頼を積めませんでした」だけ返さない。</b>
	 * これだけだと、画面には 500 が出るのに何をすればよいか分からず、
	 * <b>原因はサーバーのログにしか無い</b>。
	 * </p>
	 *
	 * <p>
	 * いちばん多いのは<b>スケジューラのキューのテーブルがまだ無い</b>ことである
	 * （{@code mq_scheduler}。{@code DbScheduler.start()} が作る）。
	 * ハートビートはテーブルを作る<b>前</b>に打つので、
	 * 「スケジューラは動いている」と見えていても、まだ無いことがある。
	 * </p>
	 *
	 * @param db	DB
	 * @return	理由
	 */
	private static String queueError (DB db) {

		String reason = db.isError() && db.getError() != null
			? String.valueOf(db.getError().getMessage()) : "";

		Log.error("バッチの実行依頼を積めませんでした: キュー=%s / 理由=%s"
			.formatted(SchedulerQueue.name(), reason.isEmpty() ? "不明" : reason));

		if (reason.isEmpty()) {
			return "依頼を積めませんでした（%s に入れられませんでした）".formatted(SchedulerQueue.name());
		}

		return "依頼を積めませんでした（%s）: %s".formatted(SchedulerQueue.name(), reason);

	}

	// endregion

	// region 全体

	/**
	 * 全体の状態
	 *
	 * @param context	コンテキスト
	 */
	private void status (WebContext context) {

		context.response().json("row", new Data()
			.putData("batch_enabled", !BatchExecutor.isAllNotStart())
			.putData("batch_running", BatchExecutor.isExecutingBatch())
			.putData("scheduler_enabled", SchedulerControl.isEnabled())
			.putData("scheduler_running", SchedulerControl.isRunning()));

	}

	/**
	 * 全体の入り切り
	 *
	 * @param context	コンテキスト
	 */
	private void statusChange (WebContext context) {

		Data request = context.request().bodyAll();

		String kind = request.getStringOptional("kind");
		boolean enable = request.getBoolean("enabled");

		boolean ok = switch (kind) {
			case "batch" -> enable ? BatchExecutor.releaseAll() : BatchExecutor.stopAll();
			case "scheduler" -> enable ? SchedulerControl.enable() : SchedulerControl.disable();
			// 移送元は知らない kind でも 200 を返していた
			default -> false;
		};

		if (!ok) {
			context.response().code(400).json("error", "変更できませんでした: kind=" + kind);
			return;
		}

		context.response().json("ok", true);

	}

	// endregion

}
