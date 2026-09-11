package approval.auth;

import db.approval_auth_example.ApprovalAuthExample;
import db.approval_auth_example.table.staff.Staff;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
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
		Auth.login(context, Principal.of(
			staff.getLong("id")
			, staff.getString("name")
			, staff.getString("role")));

		context.response().redirect(AFTER_LOGIN);

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
