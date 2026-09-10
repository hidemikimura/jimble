package approval.list;

import db.approval_list_example.table.department.Department;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

/**
 * サンプル：たくさんの中から探す（残件 N-3）
 *
 * <pre>
 * ./gradlew :examples:approval-list:migrate
 * ./gradlew :examples:approval-list:codegen
 * ./gradlew :examples:approval-list:run
 * </pre>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code GET /requests}</td>
 *       <td><b>ページング</b>（F-V-05 / F-V-06）/ <b>JOIN</b>（F-D-05）/ 絞り込み /
 *       <b>空の in を作らない</b>（F-D-07）</td></tr>
 *   <tr><td>{@code GET /requests/summary}</td>
 *       <td><b>groupBy と集計</b>（F-D-05）/ <b>CASE 式</b>（F-D-09）/ 日付関数（F-D-31）</td></tr>
 *   <tr><td>{@code GET /requests.csv}</td>
 *       <td><b>フェッチャ</b>（F-D-12）＋ CSV（F-Y-08）。JOIN 込み</td></tr>
 *   <tr><td>{@code GET /departments}</td>
 *       <td><b>SQL 結果キャッシュ</b>（F-D-28）と、更新したときの自動失効</td></tr>
 *   <tr><td>{@code POST /departments/&#123;department.id&#125;/rename}</td>
 *       <td><b>Column からのパスパラメータ</b>（F-R-18）</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>一覧は N+1 を作らずに書ける。</b>申請を引いてから社員を1件ずつ引く、をやらない。
 * JOIN 1本で引いて、<b>結果はテーブル名でネストされたまま</b>受け取る（要件 F-D-02）。
 * </p>
 * <p>
 * <b>キャッシュに手を入れる場所は無い。</b>読むときに {@code selectCached} と書くだけで、
 * <b>消すのは更新した側が勝手にやる</b>（要件 F-D-28）。
 * タグを自分で付けたり消したりしない——<b>付け忘れたときに古いものが出続ける</b>形にしないため。
 * </p>
 */
public class ListApp extends JimbleApp {

	/**
	 * ルート定義
	 */
	public ListApp () {

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			context.response().code(statusCode).text(cause.getMessage());

		});

		get("/requests", RequestListController::list);
		get("/requests/summary", RequestListController::summary);
		get("/requests.csv", RequestListController::exportCsv);

		get("/departments", DepartmentController::list);

		/*
		 * <b>パスの型をテーブル定義から作る</b>（要件 F-R-18）。
		 * {@code "/{id}"} と手で書くと、列名を変えたときに<b>ここだけ古くなる</b>。
		 * 出来上がるのは {@code /departments/{department.id}/rename} で、
		 * 読むときは {@code bodyAll().getLong(Department.id)} とそのまま書ける。
		 */
		post("/departments/" + Department.id.urlPathPlaceholder() + "/rename"
			, DepartmentController::rename);

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		JimbleServer.start(new ListApp());

	}

}
