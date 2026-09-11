package io.jimble.web.auth;

import io.jimble.util.hash.PasswordUtil;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.AttributeKey;
import io.jimble.web.session.SessionStores;

/**
 * ログインと、ルートごとの許可（要件 F-W-28）
 *
 * <h2>使い方</h2>
 * <pre>
 * public class App extends JimbleApp {
 *
 *     {
 *         before(Auth::guard);                                    // これ1行
 *
 *         path("/public", () -&gt; {
 *             attribute(Auth.PUBLIC, true);                       // ブロックごと公開
 *             attribute(Auth.NO_SESSION, true);
 *             get("/guide", Guide::show);
 *         });
 *
 *         get("/login",  Login::show).attribute(Auth.PUBLIC, true);
 *         post("/login", Login::submit).attribute(Auth.PUBLIC, true);
 *
 *         get("/requests",  RequestController::list);             // 既定で要ログイン
 *         get("/approvals", ApprovalController::list).attribute(Auth.ROLE, "approver");
 *     }
 *
 * }
 * </pre>
 *
 * <h2>ここが持っている判断</h2>
 * <p>
 * <b>アプリごとに書き直させてよい種類のものではない</b>3つを、ここに閉じ込めてある。
 * </p>
 * <ol>
 *   <li><b>既定は「要ログイン」。</b>ルートを足した人が何も書かなければ<b>閉じている</b>。
 *       逆にすると、書き忘れたルートが黙って開く</li>
 *   <li><b>どのルートにも当たらなかったら、何もしない。</b>ここで 401 を返すと、
 *       <b>存在しない URL を叩いた人に「ログインすれば何かある」と伝えてしまう</b>（本当は 404）</li>
 *   <li><b>「ログイン不要」と「セッション不要」は別。</b>
 *       ログインの入口は<b>ログインが要らないが、セッションは要る</b>——
 *       一緒にすると、<b>ログイン画面自身がセッションを持てず、誰もログインできなくなる</b></li>
 * </ol>
 *
 * <h2>やらないこと</h2>
 * <p>
 * <b>401 のときに転送しない。</b>投げるのは {@link HttpException} だけで、
 * 画面へ飛ばすか JSON を返すかは<b>アプリの {@code error()} が決める</b>。
 * 設定で切り替える口を作るより、<b>アプリのコードに1か所書いてあるほうが読める</b>。
 * </p>
 *
 * <pre>
 * error((context, cause, statusCode) -&gt; {
 *     if (statusCode == 401) {
 *         context.response().redirect("/login");
 *         return;
 *     }
 *     ...
 * });
 * </pre>
 */
public final class Auth {

	private Auth () {
	}

	// region ルート属性

	/**
	 * ログインが要らないルートか
	 *
	 * <p>
	 * <b>既定は {@code false}（＝要る）。</b>
	 * ブロックに書けば、そのブロックの中のルート全部に付く（要件 F-R-26）。
	 * </p>
	 */
	public static final AttributeKey<Boolean> PUBLIC = new AttributeKey<>("auth_public", false);

	/**
	 * 要る役割
	 *
	 * <p>空なら役割は問わない（ログインしていればよい）。</p>
	 */
	public static final AttributeKey<String> ROLE = new AttributeKey<>("auth_role", "");

	/**
	 * セッションをまったく使わないルートか（要件 F-S-11 / F-S-12）
	 *
	 * <p>
	 * <b>{@link #PUBLIC} とは別の判断である。</b>
	 * これを立てると、そのリクエストは<b>セッションの Cookie も CSRF トークンも作らない</b>。
	 * 公開ページに付けると、<b>ログインしない利用者にセッションの行が増えなくなる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>ログインの入口に付けてはいけない。</b>ログイン画面と POST は
	 * ログインが要らないが<b>セッションは要る</b>。
	 * </p>
	 */
	public static final AttributeKey<Boolean> NO_SESSION = new AttributeKey<>("auth_no_session", false);

	// endregion

	// region 見張り

	/** セッションに入れる鍵：ID */
	private static final String KEY_ID = "__auth_id";

	/** セッションに入れる鍵：表示名 */
	private static final String KEY_NAME = "__auth_name";

	/** セッションに入れる鍵：役割 */
	private static final String KEY_ROLE = "__auth_role";

	/**
	 * ログインと役割を見る
	 *
	 * <p>
	 * {@code before(Auth::guard)} で登録する。<b>いちばん最初に登録すること</b>——
	 * セッションを使うかどうかをここで決めるので、
	 * <b>先に誰かが {@code session()} を触ると間に合わない</b>。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	public static void guard (WebContext context) {

		/*
		 * <b>ルートが決まっていないときは何もしない。</b>
		 *
		 * ディスパッチャは<b>未マッチなら before を回す前に 404 を投げる</b>ので、
		 * ふつうの流れでここへは来ない（<b>「401 を返すと存在を漏らす」は、
		 * この道では起きない</b>——サンプルには長らくそう書いてあったが、間違いだった）。
		 *
		 * それでも見るのは、<b>{@code route()} が {@code null} を返しうる</b>ためである。
		 * ルートが決まる前、あるいは {@code before} の外から直に呼ばれると、
		 * ここを外した瞬間に <b>401 ではなく NullPointerException（500）</b>になる。
		 */
		if (context.route() == null || !context.route().matched()) {
			return;
		}

		if (context.route().route().attribute(NO_SESSION)) {
			context.sessionStore(SessionStores.none());
		}

		if (context.route().route().attribute(PUBLIC)) {
			return;
		}

		Principal principal = principal(context);

		if (!principal.isAuthenticated()) {
			throw new HttpException(401, "ログインしてください");
		}

		String needsRole = context.route().route().attribute(ROLE);

		if (needsRole.isEmpty()) {
			return;
		}

		if (!principal.hasRole(needsRole)) {
			/*
			 * <b>403 であって 401 ではない。</b>
			 * ログインし直しても結果が変わらないことを、状態コードで言う。
			 * 401 を返すと、利用者は<b>入り直せば見られると思って何度も試す</b>
			 */
			throw new HttpException(403, "この画面は %s だけが見られます".formatted(needsRole));
		}

	}

	// endregion

	// region ログイン

	/**
	 * ログインさせる
	 *
	 * <p>
	 * <b>セッション ID を振り直してから入れる</b>（要件 F-S-13）。
	 * 振り直さないと、<b>ログイン前に仕込まれた ID がそのまま権限を持つ</b>。
	 * </p>
	 *
	 * <p>
	 * <b>保存もここで行う。</b>ログインは「保存し忘れたら黙ってログインできない」が
	 * いちばん起きやすいところなので、ここだけは明示の {@code save()} を待たない
	 * （要件 F-S-02 の例外）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param principal	ログインする人
	 */
	public static void login (WebContext context, Principal principal) {

		if (principal == null || !principal.isAuthenticated()) {
			throw new IllegalArgumentException("ログインさせる相手がいません（id が 0 です）");
		}

		context.session().regenerateId();

		context.session().put(KEY_ID, principal.id());
		context.session().put(KEY_NAME, principal.name());
		context.session().put(KEY_ROLE, principal.role());

		context.session().save();

	}

	/**
	 * ログアウトさせる
	 *
	 * <p>
	 * <b>セッションを丸ごと捨てる。</b>ログインの鍵だけ消すと、
	 * <b>買い物かごや下書きが次の利用者に見える</b>（共用の端末で効く）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	public static void logout (WebContext context) {

		context.session().destroy();

	}

	/**
	 * いまログインしている人
	 *
	 * <p><b>{@code null} は返さない。</b>ログインしていなければ {@link Principal#ANONYMOUS}。</p>
	 *
	 * @param context	コンテキスト
	 * @return	ログインしている人
	 */
	public static Principal principal (WebContext context) {

		long id = context.session().getLong(KEY_ID);

		if (id <= 0) {
			return Principal.ANONYMOUS;
		}

		return new Principal(id, context.session().get(KEY_NAME), context.session().get(KEY_ROLE));

	}

	// endregion

	// region パスワード

	/** 相手がいないときに時間を合わせるためのハッシュ */
	private static volatile String dummyHash;

	/**
	 * パスワードを照合する（相手がいなくても同じだけ時間を使う）
	 *
	 * <p>
	 * <b>{@link PasswordUtil#check} をそのまま使うと、利用者の有無が応答時間で分かる。</b>
	 * ハッシュが {@code null} のときは即座に {@code false} が返るのに対し、
	 * 在るときは BCrypt を1回まわす——その差は<b>外から測れるほど大きい</b>
	 * （BCrypt は「遅いこと」が仕事なので、当然そうなる）。
	 * </p>
	 *
	 * <p>
	 * 利用者が見つからなかったときは <b>捨てるためのハッシュと照合して</b>、
	 * 同じだけ時間を使ってから {@code false} を返す。
	 * </p>
	 *
	 * <pre>
	 * Data staff = db.select(...);   // 見つからなければ null
	 *
	 * if (!Auth.checkPassword(input, staff == null ? null : staff.getString(Staff.password))) {
	 *     throw new HttpException(401, "ログインできませんでした");
	 * }
	 * </pre>
	 *
	 * <p>
	 * <b>メッセージも分けないこと。</b>「その ID はありません」と
	 * 「パスワードが違います」を分けて返すと、<b>時間を合わせた意味が無くなる</b>。
	 * </p>
	 *
	 * @param inputPassword	入力されたパスワード
	 * @param passwordHash	保存してあるハッシュ。利用者がいなければ null
	 * @return	一致する場合 = true
	 */
	public static boolean checkPassword (String inputPassword, String passwordHash) {

		if (passwordHash != null && !passwordHash.isEmpty()) {
			return PasswordUtil.check(inputPassword, passwordHash);
		}

		// 相手がいない。時間だけ合わせて false
		PasswordUtil.check(inputPassword == null ? "" : inputPassword, dummyHash());

		return false;

	}

	/**
	 * 捨てるためのハッシュ
	 *
	 * <p>
	 * <b>最初に要るまで作らない。</b>ハッシュを作るのに設定（pepper / 暗号化）が要るので、
	 * クラスが読まれた時点では作れない。
	 * </p>
	 *
	 * @return	ハッシュ
	 */
	private static String dummyHash () {

		String hash = dummyHash;

		if (hash == null) {
			hash = PasswordUtil.createHash("jimble-auth-dummy-password");
			dummyHash = hash;
		}

		return hash;

	}

	// endregion

}
