package approval.auth;

import db.approval_auth_example.ApprovalAuthExample;
import db.approval_auth_example.table.staff.Staff;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.util.conf.Conf;
import io.jimble.web.auth.Lockout;
import io.jimble.web.auth.Remember;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-auth）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p>
 * <b>認可が効いていることは、外から叩かないと確かめられない。</b>
 * 「`before` が呼ばれた」ではなく「入れなかった」を見る。
 * </p>
 *
 * <p>PostgreSQL が要る。</p>
 *
 * <pre>
 * ./gradlew :examples:approval-auth:pgTest
 * </pre>
 */
@Tag("db")
class ApprovalAuthIntegrationTest {

	/** サンプルの利用者のパスワード（マイグレーション 002_seed_staff.sql） */
	private static final String PASSWORD = "approval-sample";

	/** 総当たりを試すログインID（実在しない） */
	private static final String BRUTE_FORCE_ID = "総当たり";

	/* サーバー */
	private static JimbleServer server;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		// 本番の入口と同じものを呼ぶ
		Bootstrap.load();

		server = JimbleServer.start(new AuthApp(), 0);

		clearLockout();
		forget();

	}

	@AfterAll
	static void stopServer () {

		clearLockout();
		forget();

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

	}

	/**
	 * 覚えているものを全部消す（要件 F-W-30）
	 *
	 * <p>
	 * <b>テストごとに消す。</b>記憶は 90 日残るので、
	 * 消さないと<b>流すたびに行が増え続ける</b>。
	 * </p>
	 */
	private static void forget () {

		for (String loginId : new String[] { "member1", "approver1" }) {
			Data staff = ApprovalAuthExample.db().select(
				SQL.select().from(Staff.instance()).where(Staff.login_id.eq(loginId)));
			if (staff != null) {
				Remember.forgetAll(staff.getData(Staff.instance()).getLong("id"));
			}
		}

	}

	/**
	 * ログイン失敗の記録を消す（要件 F-W-29）
	 *
	 * <p>
	 * <b>これが無いと、同じ日に何度も流したときだけ落ちる。</b>
	 * 失敗の記録は 24 時間残るので、
	 * 「居ないIDで失敗する」テストを1日に4回流すと<b>4回目から 429 になる</b>。
	 * </p>
	 */
	private static void clearLockout () {

		for (String key : new String[] { "member1", "approver1", "居ない人", BRUTE_FORCE_ID }) {
			Lockout.clear(key);
		}

	}

	// region 入れる・入れない

	@Test
	@DisplayName("正しいパスワードで入れて、名前と役割がセッションに残る")
	void loginSucceeds () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> login = login(client, "member1", PASSWORD);

		assertEquals(302, login.statusCode());

		/*
		 * <b>行き先まで見る。</b>失敗したときも 302 なので、
		 * 状態コードだけ見ていると<b>入れていないのに通ったことになる</b>
		 */
		assertEquals("/me", login.headers().firstValue("location").orElse("")
			, "ログインに失敗しています（/login へ戻されました）");

		HttpResponse<String> me = get(client, "/me");

		assertEquals(200, me.statusCode());
		assertTrue(me.body().contains("申請 太郎"), me.body());
		assertTrue(me.body().contains("member"), me.body());

	}

	@Test
	@DisplayName("パスワードが違うと入れない（理由は「IDかパスワード」までしか言わない）")
	void loginFailsWithWrongPassword () throws Exception {

		HttpClient client = newClient();

		HttpResponse<String> response = login(client, "member1", "ちがうパスワード");

		assertEquals(302, response.statusCode());
		assertEquals("/login", response.headers().firstValue("location").orElse(""));

		/*
		 * <b>先に Flash を読む。</b>Flash は<b>次の1回のリクエストだけ</b>残る（要件 F-S-07）ので、
		 * 間に別のページを1枚挟むと消える。
		 * <b>「テストの順番を変えたら落ちた」のはここである。</b>
		 *
		 * あわせて<b>「そのIDは無い」と言っていない</b>ことも見る。
		 * 言うと、どのログインIDが存在するかを外から数えられる
		 */
		String flash = get(client, "/login").body();
		assertTrue(flash.contains("ログインIDかパスワードが違います"), flash);
		assertFalse(flash.contains("見つかりません"), flash);

		// 入れていない
		assertEquals(401, get(client, "/me").statusCode());

		// 2回目には出ない（要件 F-S-07）
		assertFalse(get(client, "/login").body().contains("ログインIDかパスワードが違います")
			, "Flash が消えていません");

	}

	@Test
	@DisplayName("F-W-29 何度も間違えると、しばらく待たされる")
	void repeatedFailuresStartWaiting () throws Exception {

		HttpClient client = newClient();

		Lockout.clear(BRUTE_FORCE_ID);

		try {

			/*
			 * <b>「4回目でちょうど 429」とは書かない。</b>待ち時間は
			 * 「要る秒数 - 経過秒数」なので、<b>1回の往復に1秒かかる環境では
			 * 最初の1秒は追い越されてしまう</b>。
			 * 待ち時間は失敗のたびに倍になるので、続ければ必ず追いつく——
			 * <b>見たいのは「何度間違えても待たされないことがない」ほうである</b>。
			 */
			HttpResponse<String> response = null;

			for (int i = 0; i < 8; i++) {

				response = login(client, BRUTE_FORCE_ID, "ちがうパスワード");

				if (response.statusCode() != 302) {
					break;
				}

			}

			assertEquals(429, response.statusCode()
				, "何度間違えても待たされない（総当たりが素通りする）");

			assertFalse(response.headers().firstValue("retry-after").orElse("").isEmpty()
				, "Retry-After が無い（いつやり直せるか分からない）");

			assertFalse(response.body().contains("パスワード")
				, "断り方でパスワードの当たり外れを教えている: " + response.body());

		} finally {
			Lockout.clear(BRUTE_FORCE_ID);
		}

	}

	@Test
	@DisplayName("居ないログインIDでも、パスワード違いと同じ返事になる")
	void loginFailsWithUnknownId () throws Exception {

		HttpClient client = newClient();

		assertEquals(302, login(client, "居ない人", PASSWORD).statusCode());
		assertEquals(401, get(client, "/me").statusCode());

	}

	@Test
	@DisplayName("ログアウトすると入れなくなる")
	void logout () throws Exception {

		HttpClient client = newClient();

		login(client, "member1", PASSWORD);
		assertEquals(200, get(client, "/me").statusCode());

		HttpResponse<String> response = send(client, "POST", "/logout"
			, HttpRequest.BodyPublishers.ofString(
				"csrf_token=" + csrfToken(client), StandardCharsets.UTF_8)
			, "Content-Type", "application/x-www-form-urlencoded");

		assertEquals(302, response.statusCode());
		assertEquals(401, get(client, "/me").statusCode());

	}

	// endregion

	// region 認可（ルート属性）

	@Test
	@DisplayName("ログインしていなければ 401（ルート属性の既定が「要る」）")
	void needsLoginByDefault () throws Exception {

		assertEquals(401, get(newClient(), "/requests").statusCode());
		assertEquals(401, get(newClient(), "/me").statusCode());

	}

	@Test
	@DisplayName("承認者でなければ承認の画面は 403（401 ではない）")
	void needsApproverRole () throws Exception {

		HttpClient client = newClient();

		login(client, "member1", PASSWORD);

		// 申請の一覧は見える
		assertEquals(200, get(client, "/requests").statusCode());

		/*
		 * <b>403 であること。</b>401 を返すと
		 * 「ログインし直せば見られる」と読めてしまう
		 */
		HttpResponse<String> response = get(client, "/approvals");

		assertEquals(403, response.statusCode());
		assertTrue(response.body().contains("approver"), response.body());

	}

	@Test
	@DisplayName("承認者なら承認の画面が見える")
	void approverCanSeeApprovals () throws Exception {

		HttpClient client = newClient();

		login(client, "approver1", PASSWORD);

		HttpResponse<String> response = get(client, "/approvals");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("承認 一郎"), response.body());

	}

	@Test
	@DisplayName("Basic 認証の口は、セッションのログインとは別に閉じている")
	void basicAuth () throws Exception {

		HttpClient client = newClient();

		// ログインしていても Basic が無ければ 401
		login(client, "approver1", PASSWORD);
		assertEquals(401, get(client, "/ops/whoami").statusCode());

		String basic = java.util.Base64.getEncoder()
			.encodeToString("ops:ops-sample-password".getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> response = send(client, "GET", "/ops/whoami"
			, HttpRequest.BodyPublishers.noBody(), "Authorization", "Basic " + basic);

		assertEquals(200, response.statusCode());

	}

	// endregion

	// region 公開側（要件 F-S-11 / F-S-12）

	@Test
	@DisplayName("公開のページはログイン無しで見られて、Cookie を1つも置かない")
	void publicRouteIssuesNoCookie () throws Exception {

		HttpResponse<String> response = get(newClient(), "/public/guide");

		assertEquals(200, response.statusCode());

		/*
		 * <b>ここが要点である。</b>ログインが要らないだけなら簡単だが、
		 * セッションを使わないと宣言していないと<b>見に来た人の数だけ
		 * セッションの行が増える</b>（要件 F-S-11 / F-S-12）
		 */
		assertTrue(response.headers().allValues("set-cookie").isEmpty()
			, "公開ページが Cookie を置いています: " + response.headers().allValues("set-cookie"));

	}

	@Test
	@DisplayName("公開でないページは、ログイン前でもセッションを作る（公開側との差）")
	void nonPublicRouteCreatesSession () throws Exception {

		/*
		 * <b>公開側と並べて初めて「効いている」と言える。</b>
		 * 片方だけ見ても、たまたま Cookie が出ていないだけかもしれない
		 */
		HttpResponse<String> response = get(newClient(), "/login");

		assertTrue(response.headers().allValues("set-cookie").stream()
			.anyMatch(value -> value.startsWith("sid="))
			, "ログイン画面にセッションがありません: " + response.headers().allValues("set-cookie"));

	}

	@Test
	@DisplayName("F-S-13 ログインするとセッション ID が変わる（セッション固定化）")
	void loginRegeneratesSessionId () throws Exception {

		HttpClient client = newClient();

		/*
		 * ログイン画面を開いた時点で sid が1つ出る。
		 * <b>攻撃者がこの値を仕込んだ、という想定である。</b>
		 */
		HttpResponse<String> form = get(client, "/login");

		String before = sessionIdOf(form);

		assertNotNull(before, "ログイン画面でセッションが出ていない");

		HttpResponse<String> loggedIn = login(client, "approver1", PASSWORD);

		assertEquals(302, loggedIn.statusCode(), loggedIn.body());

		String after = sessionIdOf(loggedIn);

		assertNotNull(after, "ログインの応答で新しいセッションが出ていない");

		/*
		 * <b>ここが変わっていないと、仕込まれた ID がそのまま権限を持つ。</b>
		 * ログインは通り、画面も見えるので、<b>外から見て何も壊れていない</b>——
		 * だからテストが無いと気づけない。
		 */
		assertNotEquals(before, after, "ログインの前後でセッション ID が変わっていない");

		// 振り直しても、ログインしたことは残っている
		assertEquals(200, get(client, "/me").statusCode());

	}

	@Test
	@DisplayName("無い URL は 404（401 ではない）")
	void unknownPathIsNotFound () throws Exception {

		/*
		 * <b>401 を返すと「ログインすれば何かある」と伝えてしまう。</b>
		 * 存在しない URL は、ログインしていてもしていなくても 404 である
		 */
		assertEquals(404, get(newClient(), "/そんなものは無い").statusCode());

	}

	// endregion

	// region ログインしたままにする（要件 F-W-30）

	@Test
	@DisplayName("F-W-30 印を付けておくと、ブラウザを閉じても入れる")
	void remembersAcrossBrowserRestart () throws Exception {

		HttpClient client = newClient();

		try {

			assertEquals("/me", login(client, "member1", PASSWORD, true)
				.headers().firstValue("location").orElse(""));

			assertNotNull(cookieOf(client, "remember"), "remember の Cookie が出ていない");

			// セッションだけ捨てる＝ブラウザを閉じたのと同じ
			closeBrowser(client);

			HttpResponse<String> me = get(client, "/me");

			assertEquals(200, me.statusCode(), "思い出せていない");
			assertTrue(me.body().contains("申請 太郎"), me.body());

		} finally {
			forget();
		}

	}

	@Test
	@DisplayName("F-W-30 印を付けなければ、閉じたら入れない")
	void doesNotRememberWithoutTheCheckbox () throws Exception {

		HttpClient client = newClient();

		login(client, "member1", PASSWORD);

		assertNull(cookieOf(client, "remember"), "頼んでいないのに覚えている（共用の端末で次の人が入る）");

		closeBrowser(client);

		assertEquals(401, get(client, "/me").statusCode());

	}

	@Test
	@DisplayName("F-W-30 思い出しただけの人は、パスワードを変えられない")
	void restoredUserCannotChangeThePassword () throws Exception {

		try {

			// パスワードを入れて入った人は通る
			HttpClient justLoggedIn = newClient();
			login(justLoggedIn, "member1", PASSWORD, true);

			HttpResponse<String> allowed = post(justLoggedIn, "/password");
			assertEquals(200, allowed.statusCode(), allowed.body());

			/*
			 * <b>パスワードを変えたら、覚えているものは全部消える</b>（要件 F-W-30）。
			 * 消さないと、盗まれた Cookie はそのまま使える——変えた意味が無い。
			 */
			assertEquals("{\"forgotten\":1}", allowed.body());

			// 思い出して入り直した人は、同じことができない
			HttpClient restored = newClient();
			login(restored, "member1", PASSWORD, true);
			closeBrowser(restored);

			assertEquals(200, get(restored, "/me").statusCode(), "思い出せていない");

			/*
			 * <b>ここが remember-me を出せる理由である。</b>
			 * Cookie を盗まれても、パスワードの変更まではできない。
			 */
			assertEquals(401, post(restored, "/password").statusCode()
				, "思い出しただけの人がパスワードを変えられてしまう");

		} finally {
			forget();
		}

	}

	@Test
	@DisplayName("F-W-30 ログアウトすると、覚えていたものも消える")
	void logoutForgets () throws Exception {

		HttpClient client = newClient();

		try {

			login(client, "member1", PASSWORD, true);

			assertEquals(302, post(client, "/logout").statusCode());

			closeBrowser(client);

			/*
			 * <b>ここを消し忘れると、ログアウトの次のリクエストでまた入る。</b>
			 * 画面上はログアウトできたように見えるので、共用の端末で踏むまで気づけない。
			 */
			assertEquals(401, get(client, "/me").statusCode()
				, "ログアウトしたのに、まだ覚えている");

		} finally {
			forget();
		}

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>鍵のローテーション（NF-S-09）は設定として置いてあるだけ</b>で、
	 *   ここでは古い鍵の Cookie を持ち込んでいない。動きは SecretRotationTest が見ている
	 * - <b>セッションの期限切れ</b>（session.timeout_minutes）は待たないと確かめられないので見ていない
	 * - <b>CSRF そのもの</b>は blog の formWithoutCsrf が見ている。
	 *   ここでは「ログインの POST が CSRF を通る」ことだけを間接的に確かめている
	 */

	// endregion

	/**
	 * Cookie を持ち回るクライアントを1つ作る
	 *
	 * <p><b>テストごとに新しく作る。</b>使い回すと、前のテストのログインが残る。</p>
	 *
	 * @return	クライアント
	 */
	private static HttpClient newClient () {

		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	}

	/**
	 * ログインする
	 *
	 * @param client	クライアント
	 * @param loginId	ログインID
	 * @param password	パスワード
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> login (HttpClient client, String loginId, String password)
		throws Exception {

		return login(client, loginId, password, false);

	}

	/**
	 * ログインする
	 *
	 * @param client	クライアント
	 * @param loginId	ログインID
	 * @param password	パスワード
	 * @param remember	「ログインしたままにする」に印を付けるか
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> login (HttpClient client, String loginId, String password
		, boolean remember) throws Exception {

		String token = csrfToken(client);

		String body = "csrf_token=" + enc(token)
			+ "&login_id=" + enc(loginId)
			+ "&password=" + enc(password)
			+ (remember ? "&remember=1" : "");

		return send(client, "POST", "/login"
			, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
			, "Content-Type", "application/x-www-form-urlencoded");

	}

	/**
	 * Cookie を1つ読む
	 *
	 * @param client	クライアント
	 * @param name		名前
	 * @return	値。無ければ null
	 */
	private static String cookieOf (HttpClient client, String name) {

		CookieManager manager = (CookieManager) client.cookieHandler().orElseThrow();

		for (HttpCookie cookie : manager.getCookieStore().getCookies()) {
			if (cookie.getName().equals(name)) {
				return cookie.getValue();
			}
		}

		return null;

	}

	/**
	 * セッションの Cookie だけ捨てる（ブラウザを閉じたのと同じ）
	 *
	 * @param client	クライアント
	 */
	private static void closeBrowser (HttpClient client) {

		CookieManager manager = (CookieManager) client.cookieHandler().orElseThrow();

		java.util.List<HttpCookie> cookies = new java.util.ArrayList<>(manager.getCookieStore().getCookies());
		java.util.List<java.net.URI> uris = new java.util.ArrayList<>(manager.getCookieStore().getURIs());

		for (HttpCookie cookie : cookies) {

			if (!"sid".equals(cookie.getName())) {
				continue;
			}

			manager.getCookieStore().remove(null, cookie);

			for (java.net.URI uri : uris) {
				manager.getCookieStore().remove(uri, cookie);
			}

		}

	}

	/**
	 * 応答の Set-Cookie からセッション ID を取る
	 *
	 * @param response	応答
	 * @return	セッション ID。出ていなければ null
	 */
	private static String sessionIdOf (HttpResponse<String> response) {

		for (String value : response.headers().allValues("set-cookie")) {

			if (!value.startsWith("sid=")) {
				continue;
			}

			String id = value.substring("sid=".length());
			int semicolon = id.indexOf(';');

			return semicolon < 0 ? id : id.substring(0, semicolon);

		}

		return null;

	}

	/**
	 * ログイン画面を開いて CSRF トークンを取る
	 *
	 * @param client	クライアント
	 * @return	トークン
	 * @throws Exception	失敗した場合
	 */
	private static String csrfToken (HttpClient client) throws Exception {

		String html = get(client, "/login").body();

		Matcher matcher = Pattern
			.compile("name=\"csrf_token\" value=\"([^\"]+)\"")
			.matcher(html);

		if (!matcher.find()) {
			throw new AssertionError("CSRF トークンがフォームに出ていません: " + html);
		}

		return matcher.group(1);

	}

	/**
	 * URL エンコードする
	 *
	 * @param value	値
	 * @return	エンコードしたもの
	 */
	private static String enc (String value) {

		return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);

	}

	/**
	 * GET する
	 *
	 * @param client	クライアント
	 * @param path		パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> post (HttpClient client, String path) throws Exception {

		String body = "csrf_token=" + enc(csrfToken(client));

		return send(client, "POST", path
			, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
			, "Content-Type", "application/x-www-form-urlencoded");

	}

	/**
	 * GET する
	 *
	 * @param client	クライアント
	 * @param path		パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (HttpClient client, String path) throws Exception {

		return send(client, "GET", path, HttpRequest.BodyPublishers.noBody());

	}

	/**
	 * 叩く
	 *
	 * @param client	クライアント
	 * @param method	メソッド
	 * @param path		パス
	 * @param body		本文
	 * @param headers	ヘッダ（名前・値の並び）
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> send (
		HttpClient client, String method, String path
		, HttpRequest.BodyPublisher body, String...headers) throws Exception {

		HttpRequest.Builder builder = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.method(method, body);

		for (int index = 0; index + 1 < headers.length; index += 2) {
			builder.header(headers[index], headers[index + 1]);
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

}
