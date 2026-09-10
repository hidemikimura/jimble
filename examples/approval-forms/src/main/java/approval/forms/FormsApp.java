package approval.forms;

import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

/**
 * サンプル：入力を確かめる（残件 N-3）
 *
 * <pre>
 * ./gradlew :examples:approval-forms:migrate
 * ./gradlew :examples:approval-forms:codegen
 * ./gradlew :examples:approval-forms:run
 * </pre>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code GET /requests/new}</td>
 *       <td>jte / CSRF / <b>入力値の再表示</b>（要件 F-W-09）</td></tr>
 *   <tr><td>{@code POST /requests}</td>
 *       <td><b>ValidationExecutor</b>（F-V-04）/ <b>明細のネストパラメータ</b>（F-W-03）/
 *       <b>下書きと提出で必須が変わる</b>（F-V-02）/ 添付（F-W-06）</td></tr>
 *   <tr><td>{@code GET /requests/{id}}</td>
 *       <td><b>生成した型付きアクセサ</b>（F-G-02 / F-D-22）</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>入力の確認が、保存の処理から出ている。</b>{@link SaveUseCase} には
 * {@code if (金額が空なら…)} が1行も無い。落ちたら {@link SaveValidation} が
 * 後続を捨てるので、<b>保存の側は「通ったもの」だけを相手にできる</b>。
 * </p>
 * <p>
 * <b>同じルールを下書きと提出で使い回している。</b>
 * 「提出なら必須」を保存の処理に {@code if} で書くと、
 * <b>口が増えたときにそのうち片方だけ直る</b>（{@link RequestRules}）。
 * </p>
 */
public class FormsApp extends JimbleApp {

	/**
	 * ルート定義
	 */
	public FormsApp () {

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			context.response().code(statusCode).text(cause.getMessage());

		});

		get("/requests/new", FormsApp::showForm);

		/*
		 * <b>Executor を2つ並べる。</b>前が落ちたら後ろは走らない（要件 F-V-04）。
		 * {@code ::new} にするのは、<b>エラーが Executor のインスタンスに溜まる</b>ためである。
		 */
		post("/requests", SaveValidation::new, SaveUseCase::new);

		get("/requests/{id}", ShowController::show);

	}

	/**
	 * 入力画面を出す
	 *
	 * @param context	コンテキスト
	 */
	private static void showForm (WebContext context) {

		context.response().putData("csrf_token", Csrf.token(context));
		context.response().view("forms/request.jte");

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		JimbleServer.start(new FormsApp());

	}

}
