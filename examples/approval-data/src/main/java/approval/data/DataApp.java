package approval.data;

import db.approval_data_example.table.request.Request;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

/**
 * サンプル：DB の込み入った話（残件 N-3）
 *
 * <pre>
 * ./gradlew :examples:approval-data:migrate
 * ./gradlew :examples:approval-data:codegen
 * ./gradlew :examples:approval-data:run
 * </pre>
 *
 * <h2>DB を2つ使う唯一のサンプル</h2>
 * <table>
 *   <caption>データベース</caption>
 *   <tr><td>{@code approval_data_example}</td>
 *       <td>申請・通知・レート。{@code main = true}</td></tr>
 *   <tr><td>{@code approval_data_audit_example}</td>
 *       <td>監査ログ。<b>トップレベルの2本目</b>なので、
 *       マイグレーションも codegen も当たる</td></tr>
 * </table>
 *
 * <p>
 * 設定には<b>ぶら下げるサブ DB（{@code subs}）も1つ書いてある</b>。
 * 名前は似ているが別物で、<b>そちらにはマイグレーションも codegen も当たらない。</b>
 * </p>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code POST /requests/&#123;request.id&#125;/approve}</td>
 *       <td><b>トランザクション</b>（F-D-15）/ <b>forUpdate</b> /
 *       畳み忘れを拾う（F-D-16）/ 別 DB への監査（F-D-14）</td></tr>
 *   <tr><td>{@code POST /import}</td>
 *       <td><b>一括登録</b>（F-D-08）。SQL が揃っていないと止まる</td></tr>
 *   <tr><td>{@code GET /audit}</td>
 *       <td><b>サブ DB</b>（F-D-14）。マイグレーションも別（F-G-09）</td></tr>
 *   <tr><td>{@code GET /rates}</td>
 *       <td><b>キャッシュ</b>（F-U-07）。置き場は設定で変わる</td></tr>
 *   <tr><td>{@code POST /rates}</td>
 *       <td><b>まとまりごと失効</b>（F-U-08）/ <b>分散ロック</b>（F-U-09）</td></tr>
 *   <tr><td>{@code GET|POST /imported-on}</td>
 *       <td><b>DBValue</b>（F-Y-15）</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>2つの DB にまたがると、トランザクションは効かない。</b>
 * jimble は分散トランザクションをやらない。承認の確定と監査ログのどちらかは
 * 必ず先になるので、<b>どちらが欠けたほうが困らないか</b>を決めて書くしかない。
 * </p>
 * <p>
 * <b>Redis は前提ではない</b>（要件 F-U-10）。既定の設定に {@code redis} は無く、
 * キャッシュは DB、ロックは {@link io.jimble.db.lock.DBLock} で動く。
 * </p>
 */
public class DataApp extends JimbleApp {

	/**
	 * ルート定義
	 */
	public DataApp () {

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			context.response().code(statusCode).text(cause.getMessage());

		});

		// 承認（要件 F-D-15 / F-D-16 / F-D-14）
		post("/requests/" + Request.id.urlPathPlaceholder() + "/approve", ApproveController::approve);

		// 一括登録（要件 F-D-08）
		post("/import", ApproveController::importRequests);

		// 監査ログ（別の DB。要件 F-D-14 / F-G-09）
		get("/audit", ApproveController::auditList);

		// レート（要件 F-U-07 / F-U-08 / F-U-09）
		get("/rates", RateController::list);
		post("/rates", RateController::refresh);

		// DBValue（要件 F-Y-15）
		get("/imported-on", RateController::importedOn);
		post("/imported-on", RateController::importedOn);

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		JimbleServer.start(new DataApp());

	}

}
