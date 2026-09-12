package approval.auth;

import db.approval_auth_example.ApprovalAuthExample;
import db.approval_auth_example.table.staff.Staff;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.auth.Remember;
import io.jimble.web.auth.mfa.Mfa;
import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;

/**
 * 二要素認証（要件 F-W-32）
 *
 * <pre>
 * GET  /login/code   コードを入れる画面（まだログインしていない）
 * POST /login/code   コードを確かめて、通ればログインまで進む
 *
 * POST /mfa/enroll   秘密鍵と回復コードを出す（有効にはならない）
 * POST /mfa/activate コードが合ったら有効にする
 * POST /mfa/disable  やめる
 * </pre>
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>「パスワードは合ったが、コードがまだ」は「ログインしていない」である。</b>
 * {@link Mfa#pending} はセッションに預けるだけで {@link Auth#login} を呼ばないので、
 * この間 {@code /me} も {@code /requests} も <b>401</b> になる——
 * <b>ルートを足した人が何もしなければ入れない</b>（{@code Auth.PUBLIC} の既定と同じ考え）。
 * </p>
 *
 * <p>
 * <b>登録の口は3つとも {@code Auth.FULL_AUTH} のブロックに入れてある</b>（{@link AuthApp}）。
 * 属性をブロックに1回書けば中のルート全部に付く（要件 F-R-26）ので、
 * <b>口を1つ足すたびに書き忘れる余地が無い</b>。
 * </p>
 */
public final class MfaController {

	/**
	 * セッションに入れる鍵：コードが通ったら覚えるか
	 *
	 * <p>
	 * <b>コードを入れる前に {@link Remember#issue} を呼んではいけない。</b>
	 * 呼ぶと、<b>コードを知らない人の手元に「次からパスワード無しで入れる Cookie」が残る</b>——
	 * 二要素にした意味が無くなる。印だけ預けておいて、通ってから出す。
	 * </p>
	 */
	private static final String KEY_REMEMBER = "remember_after_mfa";

	private MfaController () {
	}

	// region ログインの続き

	/**
	 * コードを入れる画面を出す
	 *
	 * @param context	コンテキスト
	 */
	static void show (WebContext context) {

		/*
		 * <b>預かっていない人は入口へ戻す。</b>
		 * ここを開けたままにすると、パスワードを通していない人が
		 * <b>コードだけ総当たりできる</b>入口になる。
		 */
		if (!Mfa.isPending(context)) {
			context.response().redirect("/login");
			return;
		}

		context.response().putData("csrf_token", Csrf.token(context));
		context.response().putData("message", context.flash().get("message"));

		context.response().view("auth/code.jte");

	}

	/**
	 * コードを確かめる
	 *
	 * @param context	コンテキスト
	 */
	static void submit (WebContext context) {

		Csrf.verify(context);

		if (!Mfa.isPending(context)) {

			/*
			 * <b>猶予（auth.mfa.pending_seconds）を過ぎるとここに来る。</b>
			 * 「コードが違います」ではなく<b>はじめからやり直し</b>である——
			 * 違いを言わないと、利用者は正しいコードを入れ続けることになる。
			 */
			context.flash().put("message", "時間が経ちすぎました。もう一度ログインしてください");
			context.response().redirect("/login");

			return;

		}

		Data request = context.request().bodyAll();

		/*
		 * <b>読むのも消すのも {@link Mfa#complete} より前である。</b>
		 * complete は中で {@link Auth#login} まで進み、<b>そこで保存が1回使われる</b>——
		 * あとから remove しても<b>黙って捨てられる</b>（save は1リクエストに1回）。
		 */
		boolean remember = "1".equals(context.session().get(KEY_REMEMBER));

		context.session().remove(KEY_REMEMBER);

		/*
		 * 通れば中で Auth.login まで済んでいる。
		 * <b>回復コードもここで通る</b>——利用者から見れば同じ1つの欄である。
		 *
		 * 総当たりは Lockout が抑える（超えると 429 が飛び、AuthApp の error() が拾う）。
		 * <b>6桁は 100 万通りしかない</b>ので、抑えないと1日で当たる。
		 */
		if (!Mfa.complete(context, request.getString("code"))) {

			context.flash().put("message", "コードが違います");
			context.response().redirect("/login/code");

			return;

		}

		if (remember) {
			Remember.issue(context, Auth.principal(context));
		}

		context.response().redirect("/me");

	}

	/**
	 * ログインのときに、コードを待たせる（{@link LoginController} から呼ぶ）
	 *
	 * @param context	コンテキスト
	 * @param principal	パスワードが合った人
	 * @param remember	「ログインしたままにする」に印が付いていたか
	 */
	static void startChallenge (WebContext context, Principal principal, boolean remember) {

		/*
		 * <b>預ける印は {@link Mfa#pending} より先に置く。</b>
		 * pending はセッション ID を振り直して<b>保存まで済ませる</b>ので、
		 * あとに置くと<b>保存されず、通ったときに覚えてくれない</b>
		 * （振り直しは中身を持ち越すので、先に置けば残る）。
		 */
		if (remember) {
			context.session().put(KEY_REMEMBER, "1");
		} else {

			/*
			 * <b>消すほうも要る。</b>印は前のログインの試みから<b>そのまま残っている</b>——
			 * セッション ID の振り直しは中身を持ち越すし、
			 * コードを間違えたリクエストは保存まで進まないので消えない。
			 *
			 * 消さないと、<b>1度「ログインしたままにする」を押した人が、
			 * 次に押さずに入っても覚えられる</b>。共用の端末で次の人が入る形である。
			 */
			context.session().remove(KEY_REMEMBER);

		}

		Mfa.pending(context, principal);

		context.response().redirect("/login/code");

	}

	// endregion

	// region 登録・解除（Auth.FULL_AUTH のブロックの中）

	/**
	 * 有効にする準備をする
	 *
	 * <p>
	 * <b>ここではまだ有効にならない。</b>{@link Mfa#activate} でコードが合って初めて有効になる——
	 * ここで有効にすると、<b>認証アプリに入れ損ねた人が二度と入れなくなる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>QR は描いていない。</b>{@code otpauth://} の URI をそのまま返すので、
	 * 認証アプリの「手で入力」から登録できる。画像にするなら画面側の仕事である
	 * （枠組みに QR を描く機能は無い。<b>依存を増やさない</b>）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void enroll (WebContext context) {

		Csrf.verify(context);

		Principal me = Auth.principal(context);

		Mfa.Enrollment enrollment = Mfa.enroll(me.id(), loginIdOf(me.id()));

		/*
		 * <b>回復コードが平文で出るのはここだけである。</b>
		 * DB には SHA-256 しか残らないので、<b>閉じたらもう出せない</b>。
		 * 本物のアプリでは「印刷して保管してください」と言って1度だけ見せる。
		 */
		context.response()
			.json("uri", enrollment.uri())
			.json("secret", enrollment.secret())
			.json("recovery_codes", enrollment.recoveryCodes());

	}

	/**
	 * コードが合ったら有効にする
	 *
	 * @param context	コンテキスト
	 */
	static void activate (WebContext context) {

		Csrf.verify(context);

		boolean activated = Mfa.activate(
			Auth.principal(context).id(), context.request().bodyAll().getString("code"));

		context.response().json("activated", activated);

	}

	/**
	 * やめる
	 *
	 * <p>
	 * <b>{@code Auth.FULL_AUTH} のブロックに入れてある</b>のがここの要点である。
	 * 緩いと、<b>Cookie を盗んだ側が二要素を外せる</b>——入れた意味が無くなる。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void disable (WebContext context) {

		Csrf.verify(context);

		Mfa.disable(Auth.principal(context).id());

		context.response().json("enabled", false);

	}

	/**
	 * 残っている回復コードの数
	 *
	 * @param context	コンテキスト
	 */
	static void status (WebContext context) {

		long id = Auth.principal(context).id();

		context.response()
			.json("active", Mfa.isActive(id))
			.json("recovery_codes", Mfa.remainingRecoveryCodes(id));

	}

	// endregion

	/**
	 * 認証アプリの一覧に出す名前を引く
	 *
	 * <p>
	 * <b>表示名ではなくログインIDにしている。</b>認証アプリには
	 * いくつものサービスが並ぶので、<b>入れるときに使う文字列</b>が出ているほうが迷わない。
	 * </p>
	 *
	 * @param id	社員ID
	 * @return	ログインID
	 */
	private static String loginIdOf (long id) {

		Data row = ApprovalAuthExample.db().select(
			SQL.select()
				.from(Staff.instance())
				.where(Staff.id.eq(id)));

		return row == null ? "" : row.getData(Staff.instance()).getString("login_id");

	}

}
