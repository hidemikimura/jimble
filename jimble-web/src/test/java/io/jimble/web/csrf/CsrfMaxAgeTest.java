package io.jimble.web.csrf;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSRF トークンの寿命が {@code cookie.max_age} から離れているか（要件 D-159）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>CSRF トークンの寿命は {@code cookie.max_age}（既定1年）に相乗りしていた。</b>
 * アプリが自分の都合で {@code cookie.max_age = 1h} と書くと、
 * <b>CSRF トークンも1時間で切れる</b>。
 * </p>
 *
 * <p>
 * <b>出るのは「CSRF トークンがありません」の 403 だけである。</b>
 * {@code csrf.*} という設定キーは1本も無かったので、
 * <b>Cookie の設定を短くしたせいだとは、まず思い至らない</b>。
 * </p>
 */
class CsrfMaxAgeTest {

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

	/**
	 * トークンを1つ発行して、その Set-Cookie を返す
	 *
	 * @return	Set-Cookie
	 */
	private String issue () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			Csrf.token(context);
			context.response().send("ok");
		}

		return sink.setCookies().stream()
			.filter(c -> c.startsWith(Csrf.COOKIE_NAME + "="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("csrf_token が出ていません: " + sink.setCookies()));

	}

	@Test
	@DisplayName("D-159 cookie.max_age を短くしても CSRF トークンは巻き添えにならない")
	void csrfDoesNotFollowCookieMaxAge () {

		conf("cookie { max_age = 1h }");

		assertEquals(Duration.ofDays(1), Csrf.maxAge(), "csrf.max_age の既定が変わっています");

		assertTrue(issue().contains("Max-Age=" + Duration.ofDays(1).toSeconds())
			, "cookie.max_age に引きずられています: " + issue());

	}

	@Test
	@DisplayName("D-159 csrf.max_age で変えられる")
	void csrfMaxAgeIsItsOwnKey () {

		conf("csrf { max_age = 2h }");

		assertEquals(Duration.ofHours(2), Csrf.maxAge());

		assertTrue(issue().contains("Max-Age=7200"), issue());

	}

	@Test
	@DisplayName("単位の無い値は落ちる")
	void bareNumberIsRefused () {

		conf("csrf { max_age = 3600 }");

		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, Csrf::maxAge);

	}

	// region ここで固定していないこと

	/*
	 * - <b>切れたあとの振る舞い</b>は見ていない。トークンが無ければ 403 になるところは
	 *   {@code CookiesTest} が見ている
	 * - <b>鍵の入れ替えのときの書き直し</b>も見ていない（{@code isStale} の道）。
	 *   同じ {@code maxAge()} を通しているので、ここが合っていれば同じである
	 */

	// endregion

}
