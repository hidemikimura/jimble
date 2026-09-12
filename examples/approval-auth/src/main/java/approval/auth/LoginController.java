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
 * ログインとログアウト（要件 F-S-02 / F-S-06 / F-S-07 / F-Y-10）
 *
 * <pre>
 * GET  /login   フォームを出す（CSRF トークンを発行する）
 * POST /login   照合して、セッションに入れて、リダイレクトする
 * POST /logout  セッションを捨てる
 * </pre>
 *
 * <p>
 * <b>POST のあとはリダイレクトする</b>（PRG）。そのままページを返すと、
 * リロードで二重投稿になる。リダイレクト先へ渡すメッセージが Flash である。
 * </p>
 */
public final class LoginController {

	/** ログイン後に行くところ */
	private static final String AFTER_LOGIN = "/me";

	private LoginController () {
	}

	/**
	 * フォームを出す
	 *
	 * @param context	コンテキスト
	 */
	static void show (WebContext context) {

		// ここで Cookie にトークンが載る（要件 F-S-06）
		context.response().putData("csrf_token", Csrf.token(context));

		// 直前の POST が残したメッセージ。読んだ時点で消える（要件 F-S-07）
		context.response().putData("message", context.flash().get("message"));

		context.response().view("auth/login.jte");

	}

	/**
	 * 照合する
	 *
	 * @param context	コンテキスト
	 */
	static void submit (WebContext context) {

		Csrf.verify(context);

		/*
		 * <b>{@code request()} から直接は読めない。</b>
		 * 送られてきた値は {@code bodyAll()} の中にある
		 * （パス変数・クエリ・フォームをまとめたもの）。
		 * {@code request().getString("login_id")} と書くと<b>黙って null が返る</b>——
		 * 例外にならないので、気づくまで「パスワードが違う」と言われ続ける。
		 */
		Data request = context.request().bodyAll();

		String loginId = request.getString("login_id");
		String password = request.getString("password");

		Data staff = findStaff(loginId);

		/*
		 * <b>「そのIDは無い」と「パスワードが違う」を分けない。</b>
		 * 分けると、<b>どのIDが存在するかを外から数えられる</b>。
		 * ログには分けて残す（運用では区別が要る）。
		 *
		 * <b>時間も分けない。</b>{@code PasswordUtil.check} は
		 * ハッシュが null だと<b>即座に false を返す</b>ので、
		 * 「利用者がいない」ほうが目に見えて速くなる（BCrypt は遅いのが仕事である）。
		 * {@code Auth.attemptLogin} は、いなくても<b>1回まわしてから</b> false を返す。
		 *
		 * <b>数える単位は「入力されたログインID」である</b>（要件 F-W-29）。
		 * {@code staff.getLong("id")} を渡すと、<b>いない相手のときだけ数えられない</b>——
		 * 総当たりはいない ID から始まるうえ、
		 * <b>待たされるかどうかでどの ID が在るかが分かってしまう</b>。
		 *
		 * まだ待ち時間が残っていれば 429 を投げる。画面へ飛ばすのは {@code AuthApp} の
		 * {@code error()} の仕事である。
		 */
		if (!Auth.attemptLogin(context, loginId, password
			, staff.isEmpty() ? null : staff.getString("password_hash"))) {

			context.flash().put("message", "ログインIDかパスワードが違います");
			context.response().redirect("/login");

			return;

		}

		/*
		 * <b>セッション ID を振り直してから入れる</b>（要件 F-S-13）。
		 * 振り直さないと、<b>ログイン前に仕込まれた ID がそのまま権限を持つ</b>。
		 * {@code Auth.login} が振り直しと保存までやる。
		 */
		Principal principal = Principal.of(
			staff.getLong("id")
			, staff.getString("name")
			, staff.getString("role"));

		boolean remember = "1".equals(request.getString("remember"));

		/*
		 * <b>二要素認証を有効にしている人は、ここではまだログインさせない</b>（要件 F-W-32）。
		 *
		 * {@code Auth.login} を呼んでからコードを聞くと、
		 * <b>コードを入れる前にログインできている</b>ことになる——
		 * 画面を1枚挟んでいるだけで、URL を直に叩けば素通りする。
		 *
		 * 覚える印も<b>コードが通ってから</b>付ける（{@link MfaController}）。
		 */
		if (Mfa.isActive(principal.id())) {

			MfaController.startChallenge(context, principal, remember);

			return;

		}

		Auth.login(context, principal);

		/*
		 * <b>印が付いたときだけ覚える</b>（要件 F-W-30）。
		 * いつも覚えると、<b>共用の端末で次の人が入れる</b>。
		 */
		if (remember) {
			Remember.issue(context, principal);
		}

		context.response().redirect(AFTER_LOGIN);

	}

	/**
	 * id から利用者を引き直す（remember-me で思い出すときに使う）
	 *
	 * <p>
	 * <b>役割をここで引くのが要点である。</b>
	 * Cookie や記憶の側に役割を持たせると、
	 * <b>権限を剥奪しても、その端末では次に切れるまで効かない</b>。
	 * </p>
	 *
	 * @param id	利用者 ID
	 * @return	見つからなければ null
	 */
	static Principal findPrincipal (long id) {

		Data row = ApprovalAuthExample.db().select(
			SQL.select()
				.from(Staff.instance())
				.where(Staff.id.eq(id)));

		if (row == null) {
			return null;
		}

		Data staff = row.getData(Staff.instance());

		return Principal.of(staff.getLong("id"), staff.getString("name"), staff.getString("role"));

	}

	/**
	 * パスワードを変える
	 *
	 * <p>
	 * <b>{@code Auth.FULL_AUTH} を付けたルートから呼ぶ</b>（{@code AuthApp}）。
	 * remember-me で戻ってきただけの人には触らせない——
	 * <b>Cookie を盗まれたときの被害がここで止まる</b>。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void changePassword (WebContext context) {

		Csrf.verify(context);

		/*
		 * <b>パスワードを変えたら、覚えているものを全部消す</b>（要件 F-W-30）。
		 * 消さないと、<b>盗まれた Cookie はそのまま使える</b>——変えた意味が無い。
		 */
		int forgotten = Remember.forgetAll(Auth.principal(context).id());

		context.response().json("forgotten", forgotten);

	}

	/**
	 * ログアウトする
	 *
	 * @param context	コンテキスト
	 */
	static void logout (WebContext context) {

		Csrf.verify(context);

		Auth.logout(context);

		context.response().redirect("/login");

	}

	/**
	 * ログインIDで1件引く
	 *
	 * <p>
	 * <b>SQL は文字列で書かない。</b>生成した {@link Staff} の列で組む（要件 F-D-03）ので、
	 * 列名を変えてマイグレーションを流し直すと<b>ここがコンパイルエラーになる</b>。
	 * 文字列で書いてあると、動かしてみるまで気づけない。
	 * </p>
	 *
	 * @param loginId	ログインID
	 * @return	見つからなければ空
	 */
	private static Data findStaff (String loginId) {

		if (loginId == null || loginId.isEmpty()) {
			return new Data();
		}

		Data row = ApprovalAuthExample.db().select(
			SQL.select()
				.from(Staff.instance())
				.where(Staff.login_id.eq(loginId)));

		if (row == null) {
			return new Data();
		}

		/*
		 * <b>結果はテーブル名でネストされている</b>（要件 F-D-02）。
		 * {@code row} の中身は {@code {staff: {id: .., name: ..}}} なので、
		 * {@code row.getString("name")} は空を返す。
		 *
		 * <b>{@code extractTableData} ではない。</b>あちらは JOIN の結果から
		 * 1つのテーブルぶんを<b>ネストしたまま</b>取り出すもので、
		 * ここで使うと {@code {staff: {...}}} が返ってきて何も変わらない（実際に踏んだ）。
		 */
		return row.getData(Staff.instance());

	}

}
