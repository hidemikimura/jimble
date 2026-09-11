package approval.auth;

import io.jimble.web.auth.Auth;
import io.jimble.web.auth.BasicAuth;
import io.jimble.web.auth.Principal;
import io.jimble.web.auth.Remember;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.AttributeKey;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.session.SessionStores;

/**
 * サンプル：誰が入れるか（残件 N-3）
 *
 * <p>
 * <b>認証と認可を、{@code before} 1つとルート属性だけで書く。</b>
 * ルートごとに {@code if (ログインしていなければ…)} を書かない、というのがここの主題である。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:approval-auth:migrate
 * ./gradlew :examples:approval-auth:codegen
 * ./gradlew :examples:approval-auth:run
 * </pre>
 *
 * <p>
 * ログインできる人はマイグレーション {@code 002_seed_staff.sql} に書いてある
 * （{@code member1} / {@code approver1}、パスワードはどちらも {@code approval-sample}）。
 * </p>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code GET /public/guide}</td>
 *       <td><b>セッションを使わない</b>（要件 F-S-11 / F-S-12）</td></tr>
 *   <tr><td>{@code GET /login}</td><td>jte / <b>CSRF の発行</b>（F-S-06）/ Flash（F-S-07）</td></tr>
 *   <tr><td>{@code POST /login}</td>
 *       <td><b>パスワード照合</b>（F-Y-10）/ セッションの保存（F-S-02）</td></tr>
 *   <tr><td>{@code POST /logout}</td><td>セッションの破棄</td></tr>
 *   <tr><td>{@code GET /me}</td><td>セッションの型付き getter（F-S-04）</td></tr>
 *   <tr><td>{@code GET /requests}</td><td><b>ログインが要る</b>（ルート属性。F-R-16 / F-R-17）</td></tr>
 *   <tr><td>{@code GET /approvals}</td><td><b>承認者だけ</b>（ルート属性）</td></tr>
 *   <tr><td>{@code GET /ops/whoami}</td><td><b>Basic 認証</b>（F-W-13）</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>認可の判断がアプリ側に1行も無い。</b>{@code before(Auth::guard)} だけで、
 * ルートは<b>属性で「開いている」と宣言する</b>（既定は閉じている）。
 * ハンドラの側には認可の話が出てこない。
 * </p>
 *
 * <p>
 * <b>このサンプルは、以前は同じことを 80 行書いていた。</b>
 * 保存先を選ぶ before、認可の before、公開判定——
 * それらは<b>アプリごとに書き直してよい種類のものではなかった</b>ので、
 * {@link Auth} へ移した（D-148）。
 * </p>
 */
public class AuthApp extends JimbleApp {

	/** 役割：承認する人 */
	static final String ROLE_APPROVER = "approver";

	/**
	 * ルート定義
	 */
	public AuthApp () {

		/*
		 * <b>認証と認可はこの1行だけ。</b>（要件 F-W-28）
		 *
		 * セッションを使うかどうかの判断も、ログインと役割の判断も、
		 * <b>「どのルートにも当たらなかったら素通りする」も</b>この中にある。
		 * <b>いちばん最初に登録すること</b>——保存先を決める前に
		 * 誰かが session() を触ると間に合わない。
		 */
		/*
		 * <b>見張るより先に「思い出す」</b>（要件 F-W-30）。
		 * あとに置くと、<b>guard が「ログインしていない」と決めたあとで思い出す</b>ことになる。
		 *
		 * id から利用者を引き直すのはアプリの仕事である——
		 * <b>役割を Cookie 側に持たせない</b>ので、権限を剥奪すればすぐ効く。
		 */
		before(Remember.restore(LoginController::findPrincipal));

		before(Auth::guard);

		/*
		 * 最後に見たページを覚えておく（ログイン後に戻すため）。
		 *
		 * <b>これがあるから NO_SESSION に意味が出る。</b>
		 * これを入れる前は、公開ページに NO_SESSION を付けても付けなくても
		 * <b>外から見て何も変わらなかった</b>——誰も session() を触らないので、
		 * 保存先が何であれセッションは作られない。
		 * <b>「効いていない設定」はいちばん質が悪い</b>ので、
		 * 効いていることが分かる形にしてある。
		 *
		 * <b>after ではなく before に置く。</b>after は応答を送ったあとに走るので、
		 * <b>そこで Cookie を足しても、もうヘッダは出てしまっている</b>
		 * （after に置いて、sid が1つも出ないので気づいた）。
		 */
		before(AuthApp::rememberLastPath);

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			context.response().code(statusCode).text(cause.getMessage());

		});

		/*
		 * 公開側（要件 F-S-11 / F-S-12）。
		 * <b>ログインが要らないだけでなく、Cookie も CSRF も発行しない。</b>
		 */
		path("/public", () -> {

			/*
			 * <b>ブロックに1回書けば、この中のルート全部に付く</b>（要件 F-R-26）。
			 * ルートを足すたびに2行書かなくてよい——
			 * <b>書き忘れて公開ページにセッションが増える</b>のを防ぐのはこちらである。
			 */
			attribute(Auth.PUBLIC, true);
			attribute(Auth.NO_SESSION, true);

			get("/guide", context -> context.response()
				.text("経費と購買の申請のしかた（ログインは要りません）"));

		});

		/*
		 * <b>ログインの入口は「公開だが、セッションは要る」。</b>
		 * NO_SESSION を付けると CSRF トークンもセッションも持てず、
		 * <b>誰もログインできなくなる</b>（実際に踏んだ）。
		 */
		get("/login", LoginController::show).attribute(Auth.PUBLIC, true);
		post("/login", LoginController::submit).attribute(Auth.PUBLIC, true);

		post("/logout", LoginController::logout);

		/*
		 * <b>パスワードの変更は、パスワードを入れて入った人だけ</b>（要件 F-W-30）。
		 * remember-me で戻ってきただけの人は 401 になり、入り直すことになる——
		 * <b>Cookie を盗まれたときの被害がここで止まる</b>ので、remember-me を出せる。
		 */
		post("/password", LoginController::changePassword).attribute(Auth.FULL_AUTH, true);

		get("/me", AuthApp::me);

		get("/requests", context -> context.response()
			.json("items", java.util.List.of())
			.json("staff", Auth.principal(context).name()));

		// 承認者だけ（ルート属性で宣言する。ハンドラの中では判断しない）
		get("/approvals", context -> context.response()
			.json("items", java.util.List.of())
			.json("staff", Auth.principal(context).name()))
			.attribute(Auth.ROLE, ROLE_APPROVER);

		/*
		 * 運用向けの口は Basic 認証にする（要件 F-W-13）。
		 * <b>セッションのログインとは別の仕組み</b>なので、ログインは要らないと宣言する。
		 */
		path("/ops", () -> {
			before(BasicAuth.of("ops", "ops-sample-password"));
			get("/whoami", context -> context.response().text("ops"))
				.attribute(Auth.PUBLIC, true);
		});

	}

	/**
	 * 最後に見たページを覚える
	 *
	 * <p>
	 * <b>公開ページではセッションを作らない</b>のがここの要点である
	 * （{@link #NO_SESSION} が付いていると保存先が「なし」なので、
	 * {@code save()} は何も書かず、{@code sid} の Cookie も出ない）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	private static void rememberLastPath (WebContext context) {

		/*
		 * <b>「見た」ページだけ覚える。</b>GET 以外でここを保存すると、
		 * <b>そのリクエストの本来の保存を食ってしまう</b>——
		 * {@code save()} は1リクエストに1回だけ効く（2回目は黙って無視される）ので、
		 * ここで先に保存すると<b>ログインの POST が保存した内容が消える</b>
		 * （実際に踏んだ。302 は返るのに、次のリクエストで 401 になる）。
		 */
		if (!"GET".equals(context.request().method())) {
			return;
		}

		context.session().put("last_path", context.request().path());
		context.session().save();

	}

	/**
	 * いま誰か
	 *
	 * @param context	コンテキスト
	 */
	private static void me (WebContext context) {

		Principal me = Auth.principal(context);

		context.response()
			.json("id", me.id())
			.json("name", me.name())
			.json("role", me.role());

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		Bootstrap.load();

		JimbleServer.start(new AuthApp());

	}

}
