package approval.auth;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
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

	/* サーバー */
	private static JimbleServer server;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		// 本番の入口と同じものを呼ぶ
		Bootstrap.load();

		server = JimbleServer.start(new AuthApp(), 0);

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

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
	@DisplayName("無い URL は 404（401 ではない）")
	void unknownPathIsNotFound () throws Exception {

		/*
		 * <b>401 を返すと「ログインすれば何かある」と伝えてしまう。</b>
		 * 存在しない URL は、ログインしていてもしていなくても 404 である
		 */
		assertEquals(404, get(newClient(), "/そんなものは無い").statusCode());

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

		String token = csrfToken(client);

		String body = "csrf_token=" + enc(token)
			+ "&login_id=" + enc(loginId)
			+ "&password=" + enc(password);

		return send(client, "POST", "/login"
			, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
			, "Content-Type", "application/x-www-form-urlencoded");

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
