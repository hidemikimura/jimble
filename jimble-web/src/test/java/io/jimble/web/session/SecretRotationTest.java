package io.jimble.web.session;

import io.jimble.util.conf.Conf;
import io.jimble.util.metrics.Metrics;
import io.jimble.web.cookie.Cookies;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鍵の入れ替え（要件 NF-S-09 / D-128）
 *
 * <p>
 * <b>ここで確かめたいことは1つである——鍵を替えても誰もログアウトしないこと。</b>
 * 替えた瞬間に全員のセッションが切れるなら、それは<b>手順ではなく事故</b>であり、
 * 「怖いから替えない」が正解になってしまう。
 * </p>
 *
 * <p>
 * 鍵を替える／古い鍵を消す、をサーバーの起動をまたいで実際にやる。
 * 設定を差し替えるだけでは足りない——<b>本物のブラウザは、
 * 前の鍵で発行された Cookie を持ったまま戻ってくる</b>ためである。
 * </p>
 */
class SecretRotationTest {

	/** 古い鍵 */
	private static final String OLD = "old-secret-old-secret-old-secret";

	/** 新しい鍵 */
	private static final String NEW = "new-secret-new-secret-new-secret";

	/** セッションに入れる名前 */
	private static final String USER = "kimura";

	@AfterEach
	void reset () {

		Conf.reload();
		Metrics.reset();

	}

	// region 動かすもの

	/** Cookie セッションを使うアプリ */
	static final class CookieSessionApp extends JimbleApp {

		{
			get("/login", context -> {
				context.session().put("user", USER);
				// jimble は明示保存（要件 F-S-02）
				context.session().save();
				context.response().send("ok");
			});

			get("/me", context -> context.response().send(context.session().get("user")));

			/*
			 * <b>セッションを読まずに保存だけ呼ぶ。</b>
			 * こうすると {@code touch}（生存期間だけ延ばす道）を通る
			 */
			get("/ping", context -> {
				context.session().save();
				context.response().send("pong");
			});
		}

	}

	/** 署名つき Cookie だけを使うアプリ（セッションは使わない） */
	static final class SignedCookieApp extends JimbleApp {

		{
			get("/put", context -> {
				context.cookies().put("mine", USER);
				context.response().send("ok");
			});

			get("/get", context -> context.response().send(context.cookies().get("mine")));

			get("/stale", context -> context.response().send(
				String.valueOf(context.cookies().isStale("mine"))));

			get("/sid", context -> context.response().send(SessionId.getOrCreate(context)));

			get("/csrf", context -> context.response().send(Csrf.token(context)));
		}

	}

	// endregion

	// region 小物

	/**
	 * 設定を差し替える
	 *
	 * @param store		セッションの置き場
	 * @param current	いまの鍵
	 * @param previous	古い鍵（0 個でもよい）
	 */
	private static void conf (String store, String current, String... previous) {

		StringBuilder list = new StringBuilder();

		for (String secret : previous) {
			list.append(list.isEmpty() ? "" : ", ").append('"').append(secret).append('"');
		}

		Conf.replace(ConfigFactory.parseString("""
			cookie {
				secret           = "%s"
				previous_secrets = [%s]
			}
			session {
				store            = "%s"
				secret           = "%s"
				previous_secrets = [%s]
			}
			""".formatted(current, list, store, current, list)));

		/*
		 * <b>保存先は最初に使ったときの設定で作られ、そのまま持ち回される。</b>
		 * 本番は入れ替えのたびにプロセスが起き直るので問題にならないが、
		 * ここでは同じプロセスで替えるので作り直す
		 */
		SessionStores.reset();

	}

	/**
	 * 送る
	 *
	 * @param server	サーバー
	 * @param path		パス
	 * @param cookies	送る Cookie
	 * @return	応答
	 * @throws Exception 失敗した場合
	 */
	private static HttpResponse<String> get (JimbleServer server, String path, List<String> cookies)
		throws Exception {

		HttpRequest.Builder builder = HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.timeout(Duration.ofSeconds(10))
			.GET();

		if (!cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ", cookies));
		}

		try (HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5)).build()) {

			return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

		}

	}

	/**
	 * 応答から Cookie を取り出す（{@code 名前=値} の形）
	 *
	 * @param response	応答
	 * @return	Cookie
	 */
	private static List<String> cookiesOf (HttpResponse<String> response) {

		List<String> result = new ArrayList<>();

		for (String header : response.headers().allValues("set-cookie")) {
			result.add(header.split(";", 2)[0]);
		}

		return result;

	}

	// endregion

	// region Cookie セッション

	@Test
	@DisplayName("【回帰】cookie.secret を設定していても、Cookie セッションが続く")
	void cookieSessionSurvivesWithSigning () throws Exception {

		/*
		 * <b>ここは以前まるごと壊れていた。</b>
		 * セッションの Cookie は<b>署名せずに</b>書いていたのに、
		 * 受け取り側は {@code cookie.secret} があると<b>すべての Cookie の署名を検証し、
		 * 落ちたものを捨てる</b>。つまり {@code cookie.secret} を設定した瞬間に
		 * Cookie セッションが効かなくなっていた——<b>例外もログも出ず、
		 * 「保存したのに消えている」だけ</b>が残る形だった
		 */
		conf("cookie", OLD);

		JimbleServer server = JimbleServer.start(new CookieSessionApp(), 0);

		try {

			List<String> held = cookiesOf(get(server, "/login", List.of()));

			assertEquals(USER, get(server, "/me", held).body(), "セッションが次のリクエストに残っていない");

		} finally {
			server.stop();
		}

	}

	@Test
	@DisplayName("鍵を入れ替えても、ログインしたままでいられる")
	void sessionSurvivesRotation () throws Exception {

		// 1. 古い鍵でログインする
		conf("cookie", OLD);

		List<String> held;

		JimbleServer before = JimbleServer.start(new CookieSessionApp(), 0);

		try {
			held = cookiesOf(get(before, "/login", List.of()));
			assertFalse(held.isEmpty(), "セッションの Cookie が発行されていない");
		} finally {
			before.stop();
		}

		// 2. 新しい鍵に入れ替え、古い鍵は読む用に残す
		conf("cookie", NEW, OLD);

		JimbleServer after = JimbleServer.start(new CookieSessionApp(), 0);

		try {

			HttpResponse<String> response = get(after, "/me", held);

			/*
			 * <b>ここが全部である。</b>これが空なら、
			 * 鍵を替えた瞬間に全員ログアウトしているということになる
			 */
			assertEquals(USER, response.body(), "鍵を替えたらログアウトしている");

			// 古い鍵で読めたことが数えられている（0 になるまで古い鍵を捨てられない）
			assertEquals(1, Metrics.snapshot().getData("counter")
				.getLong(CookieSessionStore.METRIC_STALE));

			// 3. その場で新しい鍵に書き直されている
			List<String> rewritten = cookiesOf(response);

			assertFalse(rewritten.isEmpty(), "書き直されていない（入れ替えが終わらない）");
			assertNotEquals(held, rewritten, "同じ暗号文が返っている");

			// 4. 書き直されたものは、古い鍵を消しても読める
			after.stop();

			conf("cookie", NEW);

			JimbleServer done = JimbleServer.start(new CookieSessionApp(), 0);

			try {
				assertEquals(USER, get(done, "/me", rewritten).body()
					, "書き直したはずの Cookie が新しい鍵で読めない");
			} finally {
				done.stop();
			}

		} finally {
			after.stop();
		}

	}

	@Test
	@DisplayName("セッションを読まないページでも、古い鍵の Cookie は包み直される")
	void touchRewrapsStale () throws Exception {

		conf("cookie", OLD);

		List<String> held;

		JimbleServer before = JimbleServer.start(new CookieSessionApp(), 0);

		try {
			held = cookiesOf(get(before, "/login", List.of()));
		} finally {
			before.stop();
		}

		conf("cookie", NEW, OLD);

		List<String> rewritten;

		JimbleServer after = JimbleServer.start(new CookieSessionApp(), 0);

		try {

			/*
			 * <b>ここは中身を読まない。</b>生存期間だけ延ばす道（{@code touch}）を通る。
			 * ここで古い暗号文をそのまま延ばしていると、
			 * <b>このページしか踏まない人はいつまでも古い鍵のまま</b>になり、
			 * 入れ替えが終わらない
			 */
			rewritten = cookiesOf(get(after, "/ping", held));

			assertFalse(rewritten.isEmpty(), "延命だけして包み直していない");

		} finally {
			after.stop();
		}

		// 包み直されているなら、古い鍵を消しても読める
		conf("cookie", NEW);

		JimbleServer done = JimbleServer.start(new CookieSessionApp(), 0);

		try {
			assertEquals(USER, get(done, "/me", rewritten).body()
				, "古い暗号文がそのまま延命されている");
		} finally {
			done.stop();
		}

	}

	@Test
	@DisplayName("読めなくなった Cookie は、延命せずに放っておく")
	void touchDoesNotExtendUnreadable () throws Exception {

		conf("cookie", OLD);

		List<String> held;

		JimbleServer before = JimbleServer.start(new CookieSessionApp(), 0);

		try {
			held = cookiesOf(get(before, "/login", List.of()));
		} finally {
			before.stop();
		}

		// 古い鍵を残さずに替える＝もう読めない
		conf("cookie", NEW);

		JimbleServer after = JimbleServer.start(new CookieSessionApp(), 0);

		try {

			/*
			 * <b>読めないものを延ばしても意味がない。</b>
			 * 前は中身を見ずに延ばしていたので、<b>読めない Cookie が
			 * 踏まれるたびに寿命を更新され、永久に残り続けた</b>
			 */
			assertTrue(cookiesOf(get(after, "/ping", held)).isEmpty()
				, "読めない Cookie を延命している");

		} finally {
			after.stop();
		}

	}

	@Test
	@DisplayName("古い鍵を消したら、古い Cookie は切れる")
	void oldCookieDiesWhenSecretRemoved () throws Exception {

		conf("cookie", OLD);

		List<String> held;

		JimbleServer before = JimbleServer.start(new CookieSessionApp(), 0);

		try {
			held = cookiesOf(get(before, "/login", List.of()));
		} finally {
			before.stop();
		}

		// previous_secrets に入れずに替える＝入れ替えではなく、ただの切り替え
		conf("cookie", NEW);

		JimbleServer after = JimbleServer.start(new CookieSessionApp(), 0);

		try {
			assertTrue(get(after, "/me", held).body().isEmpty(), "古い鍵の Cookie が読めてしまっている");
		} finally {
			after.stop();
		}

	}

	// endregion

	// region 署名つき Cookie

	@Test
	@DisplayName("署名の鍵を入れ替えても、Cookie は読める")
	void signedCookieSurvivesRotation () throws Exception {

		conf("none", OLD);

		List<String> held;

		JimbleServer before = JimbleServer.start(new SignedCookieApp(), 0);

		try {
			held = cookiesOf(get(before, "/put", List.of()));
		} finally {
			before.stop();
		}

		conf("none", NEW, OLD);

		JimbleServer after = JimbleServer.start(new SignedCookieApp(), 0);

		try {

			assertEquals(USER, get(after, "/get", held).body());

			// 古い鍵で読めたことがアプリから見える（アプリは自分で書き直せる）
			assertEquals("true", get(after, "/stale", held).body());

			assertTrue(Metrics.snapshot().getData("counter").getLong(Cookies.METRIC_STALE) > 0);

		} finally {
			after.stop();
		}

	}

	@Test
	@DisplayName("入れ替えが終わっていれば、古い鍵で読めたとは言わない")
	void notStaleWhenCurrent () throws Exception {

		conf("none", NEW, OLD);

		JimbleServer server = JimbleServer.start(new SignedCookieApp(), 0);

		try {

			List<String> held = cookiesOf(get(server, "/put", List.of()));

			assertEquals("false", get(server, "/stale", held).body());
			assertEquals(0, Metrics.snapshot().getData("counter").getLong(Cookies.METRIC_STALE));

		} finally {
			server.stop();
		}

	}

	// endregion

	// region 枠組みが出す Cookie

	/**
	 * Set-Cookie から Max-Age を取り出す
	 *
	 * @param response	応答
	 * @param name		Cookie 名
	 * @return	Max-Age（無ければ -1）
	 */
	private static long maxAgeOf (HttpResponse<String> response, String name) {

		for (String header : response.headers().allValues("set-cookie")) {

			if (!header.startsWith(name + "=")) {
				continue;
			}

			for (String part : header.split(";")) {
				String trimmed = part.trim();
				if (trimmed.regionMatches(true, 0, "Max-Age=", 0, 8)) {
					return Long.parseLong(trimmed.substring(8));
				}
			}

		}

		return -1;

	}

	@Test
	@DisplayName("セッション ID は、発行したときと同じ寿命で署名し直される")
	void sessionIdIsResigned () throws Exception {

		conf("none", OLD);

		List<String> held;

		JimbleServer before = JimbleServer.start(new SignedCookieApp(), 0);

		String issued;

		try {
			HttpResponse<String> first = get(before, "/sid", List.of());
			issued = first.body();
			held = cookiesOf(first);
		} finally {
			before.stop();
		}

		conf("none", NEW, OLD);

		List<String> rewritten;

		JimbleServer after = JimbleServer.start(new SignedCookieApp(), 0);

		try {

			HttpResponse<String> response = get(after, "/sid", held);

			// 同じ ID のまま（＝セッションが切れていない）
			assertEquals(issued, response.body(), "セッション ID が変わっている");

			rewritten = cookiesOf(response);

			assertFalse(rewritten.isEmpty(), "署名し直していない（入れ替えが終わらない）");

			/*
			 * <b>寿命は発行するときと同じ（セッションのタイムアウト）にする。</b>
			 * 既定の有効期限（1年）で書き直すと、
			 * <b>30分で切れるはずの Cookie が1年ブラウザに残る</b>
			 */
			assertEquals(SessionConf.timeout().toSeconds()
				, maxAgeOf(response, SessionConf.cookieName()), "寿命が変わっている");

		} finally {
			after.stop();
		}

		// 署名し直したものは、古い鍵を消しても読める
		conf("none", NEW);

		JimbleServer done = JimbleServer.start(new SignedCookieApp(), 0);

		try {
			assertEquals(issued, get(done, "/sid", rewritten).body());
		} finally {
			done.stop();
		}

	}

	@Test
	@DisplayName("CSRF トークンは、値を変えずに署名し直される")
	void csrfTokenIsResigned () throws Exception {

		conf("none", OLD);

		List<String> held;
		String issued;

		JimbleServer before = JimbleServer.start(new SignedCookieApp(), 0);

		try {
			HttpResponse<String> first = get(before, "/csrf", List.of());
			issued = first.body();
			held = cookiesOf(first);
		} finally {
			before.stop();
		}

		conf("none", NEW, OLD);

		JimbleServer after = JimbleServer.start(new SignedCookieApp(), 0);

		try {

			HttpResponse<String> response = get(after, "/csrf", held);

			/*
			 * <b>トークンの値は変えない。</b>変えると、
			 * いま開いているフォームが<b>送信した瞬間に 403 になる</b>
			 */
			assertEquals(issued, response.body(), "トークンが変わっている");

			assertFalse(cookiesOf(response).isEmpty(), "署名し直していない");

		} finally {
			after.stop();
		}

	}

	// endregion

}
