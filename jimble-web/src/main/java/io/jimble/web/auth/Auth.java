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

	/**
	 * パスワードを入れ直した人だけが通れるルートか（要件 F-W-30）
	 *
	 * <p>
	 * <b>既定は {@code false}。</b>これを立てると、
	 * <b>remember-me で戻ってきただけの人</b>は 401 になり、入り直すことになる。
	 * </p>
	 *
	 * <p>
	 * パスワードの変更・退会・決済・連絡先の変更など、
	 * <b>Cookie を盗まれたときにいちばん困る操作</b>に付ける。
	 * ここで止めるから、remember-me を出しても被害が広がらない。
	 * </p>
	 */
	public static final AttributeKey<Boolean> FULL_AUTH = new AttributeKey<>("auth_full", false);

	// endregion

	// region 見張り

	/** セッションに入れる鍵：ID */
	private static final String KEY_ID = "__auth_id";

	/** セッションに入れる鍵：表示名 */
	private static final String KEY_NAME = "__auth_name";

	/** セッションに入れる鍵：役割 */
	private static final String KEY_ROLE = "__auth_role";

	/** セッションに入れる鍵：パスワードを入れて入ったか */
	private static final String KEY_FULL = "__auth_full";

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

		if (!needsRole.isEmpty() && !principal.hasRole(needsRole)) {
			/*
			 * <b>403 であって 401 ではない。</b>
			 * ログインし直しても結果が変わらないことを、状態コードで言う。
			 * 401 を返すと、利用者は<b>入り直せば見られると思って何度も試す</b>
			 */
			throw new HttpException(403, "この画面は %s だけが見られます".formatted(needsRole));
		}

		/*
		 * <b>役割を見たあとで見る。</b>
		 * 先に見ると、<b>そもそも見られない画面のためにパスワードを入れ直させて、
		 * そのうえで 403 を返す</b>ことになる。
		 */
		if (context.route().route().attribute(FULL_AUTH) && !fullyAuthenticated(context)) {
			/*
			 * <b>403 ではなく 401 である。</b>
			 * 「入り直せば見られる」のだから、状態コードもそう言う——
			 * 403 だと、パスワードを入れ直せばよいことが伝わらない。
			 */
			throw new HttpException(401, "この操作にはパスワードの入力が要ります");
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

		store(context, principal, true);

	}

	/**
	 * パスワードを見ずにログインさせる（remember-me で思い出したとき）
	 *
	 * <p>
	 * <b>{@link Remember} だけが呼ぶ。</b>
	 * こうして入った人は {@link #fullyAuthenticated} が {@code false} なので、
	 * {@link #FULL_AUTH} を付けたルートには入れない。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param principal	ログインする人
	 */
	static void loginWithoutPassword (WebContext context, Principal principal) {

		store(context, principal, false);

	}

	/**
	 * セッションに入れる
	 *
	 * @param context	コンテキスト
	 * @param principal	ログインする人
	 * @param fullAuth	パスワードを入れて入ったか
	 */
	private static void store (WebContext context, Principal principal, boolean fullAuth) {

		if (principal == null || !principal.isAuthenticated()) {
			throw new IllegalArgumentException("ログインさせる相手がいません（id が 0 です）");
		}

		context.session().regenerateId();

		context.session().put(KEY_ID, principal.id());
		context.session().put(KEY_NAME, principal.name());
		context.session().put(KEY_ROLE, principal.role());
		context.session().put(KEY_FULL, fullAuth);

		context.session().save();

	}

	/**
	 * パスワードを入れて入った人か（要件 F-W-30）
	 *
	 * <p>
	 * remember-me で思い出しただけなら {@code false}。
	 * ルートを閉じるなら {@link #FULL_AUTH} を使うほうがよい（書き忘れが起きない）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @return	パスワードを入れて入った場合 = true
	 */
	public static boolean fullyAuthenticated (WebContext context) {

		return context.session().getBoolean(KEY_FULL);

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

		/*
		 * <b>セッションより先に remember を消す。</b>
		 * 消し忘れると、<b>ログアウトした次のリクエストでまた入ってしまう</b>——
		 * 画面上はログアウトできたように見えるので、共用の端末でこれをやると気づけない。
		 */
		Remember.forget(context);

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

	/** 待たされたときに返す状態コード */
	public static final int LOCKED_STATUS_CODE = 429;

	/**
	 * ログインを1回試す（待たせる → 照合する → 成功なら数えたものを消す）
	 *
	 * <p>
	 * <b>ログインの入口ではこれを呼ぶ。</b>
	 * {@link #checkPassword} を直に呼んでも動くが、それだと
	 * <b>失敗を数える／成功したら消す</b>を書き忘れる（要件 F-W-29）。
	 * </p>
	 *
	 * <pre>
	 * Data staff = findStaff(loginId);
	 *
	 * if (!Auth.attemptLogin(context, loginId, password,
	 *         staff.isEmpty() ? null : staff.getString("password_hash"))) {
	 *     context.flash().put("message", "ログインIDかパスワードが違います");
	 *     context.response().redirect("/login");
	 *     return;
	 * }
	 *
	 * Auth.login(context, Principal.of(staff.getLong("id"), ...));
	 * </pre>
	 *
	 * <h4>まだ待つ時間が残っているとき</h4>
	 * <p>
	 * {@code 429} の {@link HttpException} を投げ、{@code Retry-After} を付ける。
	 * <b>ここでも画面へは飛ばさない</b>——{@link #guard} と同じで、アプリの
	 * {@code error()} が決める。
	 * </p>
	 *
	 * <h4>数える単位は「入力されたログイン ID」</h4>
	 * <p>
	 * <b>利用者の DB 上の ID を渡してはいけない。</b>
	 * 存在しない ID には DB 上の ID が無いので、<b>存在しない ID だけ数えられなくなる</b>——
	 * 総当たりは存在しない ID から始まるうえ、
	 * <b>「待たされるかどうか」でどの ID が在るかが分かってしまう</b>。
	 * </p>
	 *
	 * @param context		コンテキスト
	 * @param key			数える単位（<b>入力されたログイン ID</b>）
	 * @param inputPassword	入力されたパスワード
	 * @param passwordHash	保存してあるハッシュ。利用者がいなければ null
	 * @return	ログインしてよい場合 = true
	 */
	public static boolean attemptLogin (WebContext context, String key, String inputPassword, String passwordHash) {

		long waitSeconds = Lockout.waitSeconds(key);

		if (waitSeconds > 0) {

			if (context != null) {
				context.response().setResponseHeader("Retry-After", String.valueOf(waitSeconds));
			}

			/*
			 * <b>「あと何秒」をメッセージに書かない。</b>
			 * 数えているのは在る ID も無い ID も同じなので待たされること自体は漏れないが、
			 * 秒数まで返すと<b>攻撃者に「何回失敗した状態か」を教える</b>ことになる
			 * （Retry-After は待つために要るので、そちらには入れる）。
			 */
			throw new HttpException(LOCKED_STATUS_CODE, "しばらく待ってからやり直してください");

		}

		if (checkPassword(inputPassword, passwordHash)) {
			Lockout.clear(key);
			return true;
		}

		Lockout.fail(key);

		return false;

	}


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
