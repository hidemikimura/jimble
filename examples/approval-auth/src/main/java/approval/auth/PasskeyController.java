package approval.auth;

import io.jimble.util.data.Data;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.auth.passkey.Passkey;
import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.http.HttpException;

/**
 * パスキー（パスワードなしのログイン。D-261）
 *
 * <pre>
 * GET  /passkey.js                ブラウザ側の JS（jimble が同梱しているもの）
 * POST /passkey/login/options     ログインの options（誰でも）
 * POST /passkey/login             確かめてログインさせる（誰でも）
 * GET  /passkeys                  登録しているパスキーの一覧と、登録のボタン（ログインしている人）
 * POST /passkey/register/options  登録の options（パスワードを入れて入った人だけ）
 * POST /passkey/register          確かめて保存する（パスワードを入れて入った人だけ）
 * POST /passkey/delete            1つ消す（パスワードを入れて入った人だけ）
 * </pre>
 *
 * <p>
 * パスキーでログインした人には、<b>二要素認証のコードを聞かない</b>。パスキーは端末の本人確認（指紋・顔・PIN）込みなので、
 * それだけで「持っている端末」と「本人確認」の2つを満たす。
 * </p>
 *
 * <p>
 * サイトは設定（{@code conf/application.local.conf} の {@code auth.passkey}。手元は {@code localhost}）から決まる。
 * クライアントごとにドメインが違う SaaS なら、{@code PasskeyRp} を作って各メソッドに渡す。
 * </p>
 */
public final class PasskeyController {

	/** ログイン後に行くところ */
	static final String AFTER_LOGIN = "/me";

	private PasskeyController () {
	}

	// region ログイン

	/**
	 * ログインの options
	 *
	 * @param context	コンテキスト
	 */
	static void loginOptions (WebContext context) {

		Csrf.verify(context);

		context.response().json(Passkey.loginOptions(context));

	}

	/**
	 * 確かめてログインさせる
	 *
	 * <p>
	 * <b>断る理由は返さない</b>（どこまで通ったかを測らせない）。理由は jimble がログに出す。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void login (WebContext context) {

		Csrf.verify(context);

		// 利用者 ID から引き直す（役割は表のいまの値。権限を剥奪すればすぐ効く）
		if (!Passkey.login(context, context.request().bodyJson(), LoginController::findPrincipal)) {
			throw new HttpException(401, "パスキーでログインできませんでした");
		}

		context.response().json("ok", true).json("next", AFTER_LOGIN);

	}

	// endregion

	// region 登録・一覧・削除

	/**
	 * 一覧と登録のボタン
	 *
	 * @param context	コンテキスト
	 */
	static void show (WebContext context) {

		Principal me = Auth.principal(context);

		context.response().putData("csrf_token", Csrf.token(context));
		context.response().putData("message", context.flash().get("message"));
		context.response().putData("passkeys", Passkey.list(me.id()));
		context.response().putData("full_auth", Auth.fullyAuthenticated(context));

		context.response().view("auth/passkeys.jte");

	}

	/**
	 * 登録の options
	 *
	 * @param context	コンテキスト
	 */
	static void registrationOptions (WebContext context) {

		Csrf.verify(context);

		Principal me = Auth.principal(context);

		// パスキーの一覧（端末の画面）に出る名前。ログイン ID にする
		context.response().json(Passkey.registrationOptions(context, MfaController.loginIdOf(me.id())));

	}

	/**
	 * 確かめて保存する
	 *
	 * @param context	コンテキスト
	 */
	static void register (WebContext context) {

		Csrf.verify(context);

		Data credential = context.request().bodyJson();

		// 利用者が見分けるための名前（JS が credential と一緒に送る。無ければ空）
		String label = credential.getStringOptional("label");

		Passkey.register(context, credential, label.length() > 100 ? label.substring(0, 100) : label);

		context.response().json("ok", true);

	}

	/**
	 * 1つ消す
	 *
	 * @param context	コンテキスト
	 */
	static void delete (WebContext context) {

		Csrf.verify(context);

		Principal me = Auth.principal(context);

		// 渡された id がほかの人のものなら消えない（jimble が利用者 ID と組で消す）
		boolean deleted = Passkey.delete(me.id(), context.request().bodyForm().getStringOptional("id"));

		context.flash().put("message", deleted ? "パスキーを消しました" : "消せませんでした");
		context.response().redirect("/passkeys");

	}

	// endregion

}
