package io.jimble.web.session;

import io.jimble.util.crypto.Aead;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cookie セッションの有効期限をサーバー側でも見る（D-209）
 *
 * <p>
 * 2.2.2 までは暗号文の中に時刻が無く、{@code session.timeout} は Cookie の Max-Age になるだけだった。
 * <b>1度盗まれた Cookie は、ログアウトしても、タイムアウトを過ぎても、いつまでも使えた。</b>
 * </p>
 */
class CookieSessionExpiryTest {

	private static final String SECRET = "test-secret";

	/** 30 分・発行から 1 日 */
	private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
	private final CookieSessionStore store = new CookieSessionStore(List.of(SECRET), 30, Duration.ofDays(1));

	{
		store.clock = now::get;
	}

	/** 保存して、Set-Cookie の値を返す */
	private String save (Data data, SessionEntry from) {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			store.save(context, from == null ? new SessionEntry(data, false) : new SessionEntry(data, true, from.issuedAt()));
			// Cookie は応答を返すときに書き出される
			context.response().send("ok");
		}

		return cookieValue(sink);

	}

	private static String cookieValue (Fakes.FakeResponseSink sink) {

		String setCookie = sink.setCookies().stream()
			.filter(c -> c.startsWith(CookieSessionStore.COOKIE_NAME + "=")).findFirst().orElse(null);

		if (setCookie == null) {
			return null;
		}

		return setCookie.substring((CookieSessionStore.COOKIE_NAME + "=").length(), setCookie.indexOf(';'));

	}

	/** その値を持って来たリクエストで読む */
	private SessionEntry load (String value) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(CookieSessionStore.COOKIE_NAME, value);

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			return store.load(context);
		}

	}

	/** その値を持って来たリクエストで延ばし、新しい値（書き直さなければ同じ値）を返す */
	private String touch (String value) {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(CookieSessionStore.COOKIE_NAME, value);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			store.touch(context, store.load(context));
			context.response().send("ok");
		}

		String renewed = cookieValue(sink);
		return renewed == null ? value : renewed;

	}

	private static Data user (int id) {

		Data data = new Data();
		data.put("__auth_id", id);
		return data;

	}

	@Test
	@DisplayName("最後に使ってから session.timeout を過ぎた Cookie は、ブラウザが送ってきても捨てる")
	void idleTimeout () {

		String value = save(user(42), null);

		now.addAndGet(Duration.ofMinutes(29).toMillis());
		assertEquals(42, load(value).data().getInt("__auth_id"), "まだ使える");

		now.addAndGet(Duration.ofMinutes(2).toMillis());
		assertTrue(load(value).data().isEmpty(), "30 分を過ぎても使えています");

	}

	@Test
	@DisplayName("ログアウトしたあとの写しも、タイムアウトを過ぎれば使えない（かつてはいつまでも使えた）")
	void copiedAfterLogout () {

		String stolen = save(user(42), null);

		// ログアウト（Cookie を消すだけ）。盗んだ側は写しを持っている
		now.addAndGet(Duration.ofHours(2).toMillis());

		assertTrue(load(stolen).data().isEmpty(), "盗まれた Cookie が、2 時間後も使えています");

	}

	@Test
	@DisplayName("使い続けても、発行から session.absolute_timeout を過ぎたら捨てる")
	void absoluteTimeout () {

		String value = save(user(42), null);

		// 10 分おきに使い続ける（そのたびに最後に使った時刻を書き直す）
		for (int i = 0; i < 6 * 23; i++) {
			now.addAndGet(Duration.ofMinutes(10).toMillis());
			value = touch(value);
			assertEquals(42, load(value).data().getInt("__auth_id"), "途中で切れました: " + i);
		}

		now.addAndGet(Duration.ofHours(1).toMillis() + Duration.ofMinutes(1).toMillis());
		value = touch(value);

		assertTrue(load(value).data().isEmpty(), "発行から 1 日を過ぎても使えています");

	}

	@Test
	@DisplayName("中身を書き換えても、発行した時刻は引き継ぐ（延命できない）。ID を作り直すと数え直す")
	void issuedAtCarriesOver () {

		String first = save(user(42), null);
		SessionEntry loaded = load(first);
		long issuedAt = loaded.issuedAt();

		now.addAndGet(Duration.ofMinutes(5).toMillis());
		String rewritten = save(user(42), loaded);
		assertEquals(issuedAt, load(rewritten).issuedAt());

		// ログイン（regenerateId）は既存ではない新規として保存する
		String renewed = save(user(42), null);
		assertNotEquals(issuedAt, load(renewed).issuedAt());

	}

	@Test
	@DisplayName("少し前に使ったばかりなら暗号化し直さない。時間が経っていれば、最後に使った時刻を書き直す")
	void touchResolution () {

		String value = save(user(42), null);

		now.addAndGet(10_000);
		assertEquals(value, touch(value), "10 秒で書き直しています");

		now.addAndGet(CookieSessionStore.SEEN_RESOLUTION_MILLIS);
		String renewed = touch(value);
		assertNotEquals(value, renewed);

		// 書き直した値は、元の値の期限を過ぎても使える
		now.addAndGet(Duration.ofMinutes(29).toMillis());
		assertTrue(load(value).data().isEmpty() || load(renewed).data().getInt("__auth_id") == 42);
		assertEquals(42, load(renewed).data().getInt("__auth_id"));

	}

	@Test
	@DisplayName("時刻の無い 2.2.2 までの形は捨てる（いつ発行されたか分からない）")
	void legacyFormatRejected () {

		String legacy = Aead.encrypt(Dson.encodes(user(42)), SECRET);

		assertTrue(load(legacy).data().isEmpty());

	}

	@Test
	@DisplayName("読めた中身は今までどおり")
	void roundTrip () {

		Data data = user(7);
		data.put("name", "きむら");

		SessionEntry loaded = load(save(data, null));

		assertNotNull(loaded);
		assertEquals("きむら", loaded.data().getString("name"));
		assertNull(loaded.data().getObject("iat"), "封筒の時刻がアプリのデータに混ざっています");

	}

}
