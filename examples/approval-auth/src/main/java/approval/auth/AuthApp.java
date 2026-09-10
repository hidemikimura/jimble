package approval.auth;

import io.jimble.web.auth.BasicAuth;
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
 * <b>認可の判断が1か所にしかない。</b>{@code before} が
 * {@link #NEEDS_LOGIN} と {@link #NEEDS_ROLE} を見るだけで、
 * ハンドラの側には認可の話が1行も出てこない。
 * <b>ルートを足した人が判断を書き忘れる</b>形にしないため、
 * {@link #NEEDS_LOGIN} の既定値は {@code true}（＝黙って足したら閉じている）にしてある。
 * </p>
 */
public class AuthApp extends JimbleApp {

	/**
	 * ログインが要るか
	 *
	 * <p>
	 * <b>既定は {@code true}（要る）である。</b>ここを {@code false} にすると、
	 * <b>ルートを足した人が何も書かなければ誰でも入れる</b>ことになる。
	 * 「開いているほうを明示させる」のが安全側である。
	 * </p>
	 */
	static final AttributeKey<Boolean> NEEDS_LOGIN = new AttributeKey<>("needs_login", true);

	/** 要る役割。空なら役割は問わない */
	static final AttributeKey<String> NEEDS_ROLE = new AttributeKey<>("needs_role", "");

	/**
	 * セッションをまったく使わないか（要件 F-S-11 / F-S-12）
	 *
	 * <p>
	 * <b>{@link #NEEDS_LOGIN} とは別の判断である。</b>ここを一緒にして
	 * 「ログインが要らない＝セッションも要らない」と書いたら、
	 * <b>ログインの画面と POST 自身がセッションを持てなくなり、誰もログインできなくなった</b>
	 * （302 は返るのに、次のリクエストで 401 になる。実際に踏んだ）。
	 * </p>
	 *
	 * <p>
	 * ログインの入口は<b>ログインが要らないが、セッションは要る</b>。
	 * どちらも要らないのは公開のページだけである。
	 * </p>
	 */
	static final AttributeKey<Boolean> NO_SESSION = new AttributeKey<>("no_session", false);

	/** 役割：承認する人 */
	static final String ROLE_APPROVER = "approver";

	/** セッションに入れる鍵：社員ID */
	static final String SESSION_STAFF_ID = "staff_id";

	/** セッションに入れる鍵：氏名 */
	static final String SESSION_STAFF_NAME = "staff_name";

	/** セッションに入れる鍵：役割 */
	static final String SESSION_ROLE = "role";

	/**
	 * ルート定義
	 */
	public AuthApp () {

		/*
		 * <b>いちばん最初に、セッションを使うかどうかを決める。</b>
		 * 決めたあとで session() を触ると遅い——
		 * 先に触られると、そのリクエストはもう保存先が決まっている。
		 */
		before(AuthApp::chooseSessionStore);

		// 認可（要件 F-R-16 / F-R-17）。判断はここ1か所だけ
		before(AuthApp::authorize);

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
			get("/guide", context -> context.response()
				.text("経費と購買の申請のしかた（ログインは要りません）"))
				.attribute(NEEDS_LOGIN, false)
				.attribute(NO_SESSION, true);
		});

		get("/login", LoginController::show).attribute(NEEDS_LOGIN, false);
		post("/login", LoginController::submit).attribute(NEEDS_LOGIN, false);

		post("/logout", LoginController::logout);

		get("/me", AuthApp::me);

		get("/requests", context -> context.response()
			.json("items", java.util.List.of())
			.json("staff", context.session().get(SESSION_STAFF_NAME)));

		// 承認者だけ（ルート属性で宣言する。ハンドラの中では判断しない）
		get("/approvals", context -> context.response()
			.json("items", java.util.List.of())
			.json("staff", context.session().get(SESSION_STAFF_NAME)))
			.attribute(NEEDS_ROLE, ROLE_APPROVER);

		/*
		 * 運用向けの口は Basic 認証にする（要件 F-W-13）。
		 * <b>セッションのログインとは別の仕組み</b>なので、ログインは要らないと宣言する。
		 */
		path("/ops", () -> {
			before(BasicAuth.of("ops", "ops-sample-password"));
			get("/whoami", context -> context.response().text("ops"))
				.attribute(NEEDS_LOGIN, false);
		});

	}

	/**
	 * このリクエストでセッションを使うかを決める（要件 F-S-11 / F-S-12）
	 *
	 * <p>
	 * <b>公開側はセッションを持たない。</b>設定（{@code session.store}）は
	 * アプリ全体で1つなので、これが無いと<b>ログインしない利用者にも
	 * セッションの行が1件ずつ増える</b>。
	 * </p>
	 *
	 * <p>
	 * <b>{@link #NEEDS_LOGIN} では判断しない。</b>
	 * ログインの入口はログインが要らないが、セッションは要る（{@link #NO_SESSION} の説明）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	private static void chooseSessionStore (WebContext context) {

		if (!context.route().matched()) {
			return;
		}

		if (context.route().route().attribute(NO_SESSION)) {
			context.sessionStore(SessionStores.none());
		}

	}

	/**
	 * ログインと役割を見る（要件 F-R-16 / F-R-17）
	 *
	 * @param context	コンテキスト
	 */
	private static void authorize (WebContext context) {

		if (isPublic(context)) {
			return;
		}

		long staffId = context.session().getLong(SESSION_STAFF_ID);

		if (staffId <= 0) {
			throw new HttpException(401, "ログインしてください");
		}

		String needsRole = context.route().route().attribute(NEEDS_ROLE);

		if (needsRole.isEmpty()) {
			return;
		}

		if (!needsRole.equals(context.session().get(SESSION_ROLE))) {
			/*
			 * <b>403 であって 401 ではない。</b>
			 * ログインし直しても結果が変わらないことを、状態コードで言う
			 */
			throw new HttpException(403, "この画面は %s だけが見られます".formatted(needsRole));
		}

	}

	/**
	 * ログインが要らないルートか
	 *
	 * @param context	コンテキスト
	 * @return	要らない場合 = true
	 */
	private static boolean isPublic (WebContext context) {

		/*
		 * <b>どのルートにも当たらなかったときも「公開」として扱う。</b>
		 * ここで 401 を返すと、<b>存在しない URL を叩いた人に
		 * 「ログインすれば何かある」と伝えてしまう</b>（本当は 404 である）。
		 */
		if (!context.route().matched()) {
			return true;
		}

		return !context.route().route().attribute(NEEDS_LOGIN);

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

		context.response()
			.json("id", context.session().getLong(SESSION_STAFF_ID))
			.json("name", context.session().get(SESSION_STAFF_NAME))
			.json("role", context.session().get(SESSION_ROLE));

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
