package io.jimble.web.auth;

import io.jimble.util.data.Data;
import io.jimble.web.auth.mfa.Mfa;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Router;
import io.jimble.web.session.SessionEntry;
import io.jimble.web.session.SessionStore;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ログインの種別（realm。D-185）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * 運用者の画面と利用者の管理画面を持つアプリでは、<b>同じブラウザで両方にログインすると、
 * 後からログインしたほうが前のログインを上書きしていた</b>（セッションの中の置き場所が 1 つだけ）。
 * ログアウトもセッションを丸ごと捨てるので、片方から出るともう片方も切れた。
 * </p>
 *
 * <p>
 * もう1つ固定したいのは<b>「種別を付けないアプリは、何も変わらない」</b>ことである。
 * 置き場所の鍵（{@code __auth_id}）もログアウトの動き（丸ごと捨てる）も、これまでと同じであること。
 * </p>
 *
 * <p>
 * セッションは、リクエストをまたいで中身を持ち越すメモリの保存先（{@link MemoryStore}）で持つ。DB は要らない。
 * </p>
 */
class AuthRealmTest {

	/** 運用者の種別 */
	private static final String OPERATOR = "operator";

	/** 利用者の種別 */
	private static final String MEMBER = "member";

	/* ルート（/ops は operator、/admin は member、/plain は種別なし） */
	private Router router;

	/* 1 つのブラウザのセッション */
	private MemoryStore store;

	@BeforeEach
	void setUp () {

		router = new Router();

		Router ops = router.path("/ops");
		ops.attribute(Auth.REALM, OPERATOR);
		ops.attribute(Auth.ROLE, "ops");
		ops.get("/me", context -> { });

		Router admin = router.path("/admin");
		admin.attribute(Auth.REALM, MEMBER);
		admin.attribute(Auth.ROLE, "member");
		admin.get("/me", context -> { });

		router.get("/plain/me", context -> { });

		router.seal();

		store = new MemoryStore();

	}

	/**
	 * 1 リクエスト（同じブラウザ = 同じ保存先）
	 */
	private void request (String path, Consumer<WebContext> action) {

		try (WebContext context = Fakes.context("GET", path)) {
			context.route(router.match("GET", path));
			context.sessionStore(store);
			action.accept(context);
		}

	}

	@Test
	@DisplayName("D-185 種別を付けないルートは、これまでと同じ鍵に入れる")
	void noRealmKeepsKeys () {

		request("/plain/me", context -> Auth.login(context, Principal.of(7, "太郎", "member")));

		assertEquals(7, store.data.getLong("__auth_id"));
		assertEquals("", Auth.realmOf(null));

		request("/plain/me", context -> assertEquals(7, Auth.principal(context).id()));

	}

	@Test
	@DisplayName("D-185 同じブラウザで 2 つの種別にログインでき、互いを上書きしない")
	void twoRealmsInOneSession () {

		request("/ops/me", context -> Auth.login(context, Principal.of(1, "運用 一郎", "ops")));
		request("/admin/me", context -> Auth.login(context, Principal.of(1, "利用 花子", "member")));

		request("/ops/me", context -> {
			assertDoesNotThrow(() -> Auth.guard(context));
			assertEquals("運用 一郎", Auth.principal(context).name());
		});

		request("/admin/me", context -> {
			assertDoesNotThrow(() -> Auth.guard(context));
			assertEquals("利用 花子", Auth.principal(context).name());
		});

		// 種別なしのルートからは、どちらのログインも見えない
		request("/plain/me", context -> assertFalse(Auth.principal(context).isAuthenticated()));

	}

	@Test
	@DisplayName("D-185 片方の種別だけにログインしていれば、もう片方のルートは 401")
	void otherRealmIsNotLoggedIn () {

		request("/ops/me", context -> Auth.login(context, Principal.of(1, "運用 一郎", "ops")));

		request("/admin/me", context -> {
			HttpException e = assertThrows(HttpException.class, () -> Auth.guard(context));
			assertEquals(401, e.statusCode());
		});

	}

	@Test
	@DisplayName("D-185 片方からログアウトしても、もう片方は残る。セッション ID は振り直す")
	void logoutOneRealm () {

		request("/ops/me", context -> Auth.login(context, Principal.of(1, "運用 一郎", "ops")));
		request("/admin/me", context -> Auth.login(context, Principal.of(2, "利用 花子", "member")));

		int regeneratedBefore = store.destroyed;

		request("/ops/me", Auth::logout);

		assertTrue(store.destroyed > regeneratedBefore, "振り直していない（古い ID が残る）");

		request("/ops/me", context -> assertFalse(Auth.principal(context).isAuthenticated()));
		request("/admin/me", context -> assertEquals(2, Auth.principal(context).id()));

	}

	@Test
	@DisplayName("D-185 最後の種別からログアウトしたら、セッションを丸ごと捨てる")
	void logoutLastRealmDestroys () {

		request("/ops/me", context -> {
			Auth.login(context, Principal.of(1, "運用 一郎", "ops"));
		});

		request("/ops/me", context -> {
			context.session().put("cart", "りんご");
			context.session().save();
		});

		request("/ops/me", Auth::logout);

		assertTrue(store.data == null || store.data.isEmpty(), "セッションの中身が残っている: " + store.data);

	}

	@Test
	@DisplayName("D-185 種別を付けないルートのログアウトは、これまでどおりすべてを捨てる")
	void logoutWithoutRealmDestroysAll () {

		request("/ops/me", context -> Auth.login(context, Principal.of(1, "運用 一郎", "ops")));
		request("/admin/me", context -> Auth.login(context, Principal.of(2, "利用 花子", "member")));

		request("/plain/me", Auth::logout);

		request("/ops/me", context -> assertFalse(Auth.principal(context).isAuthenticated()));
		request("/admin/me", context -> assertFalse(Auth.principal(context).isAuthenticated()));

	}

	@Test
	@DisplayName("D-185 パスワードを入れて入ったかは、種別ごとに持つ")
	void fullAuthPerRealm () {

		request("/ops/me", context -> Auth.login(context, Principal.of(1, "運用 一郎", "ops")));

		request("/ops/me", context -> assertTrue(Auth.fullyAuthenticated(context)));
		request("/admin/me", context -> assertFalse(Auth.fullyAuthenticated(context)));

	}

	@Test
	@DisplayName("D-185 二要素認証の途中の状態は、種別ごとに持ち、ログアウトで消える")
	void mfaPendingPerRealm () {

		request("/ops/me", context -> Mfa.pending(context, Principal.of(1, "運用 一郎", "ops"), OPERATOR));

		request("/ops/me", context -> assertTrue(Mfa.isPending(context)));
		request("/admin/me", context -> assertFalse(Mfa.isPending(context), "別の種別の途中の人が見えている"));

		// 利用者がログインしても、運用者の途中の状態は残る
		request("/admin/me", context -> Auth.login(context, Principal.of(2, "利用 花子", "member")));
		request("/ops/me", context -> assertTrue(Mfa.isPending(context)));

		request("/ops/me", Auth::logout);
		request("/ops/me", context -> assertFalse(Mfa.isPending(context)));
		request("/admin/me", context -> assertEquals(2, Auth.principal(context).id()));

	}

	@Test
	@DisplayName("D-185 remember-me の種別がルートの種別と違えば止める（ログアウトで記憶を消せなくなる）")
	void rememberMustUseSameRealm () {

		request("/ops/me", context -> {
			IllegalStateException e = assertThrows(IllegalStateException.class
				, () -> Remember.issue(context, Principal.of(1, "運用 一郎", "ops"), MEMBER));
			assertTrue(e.getMessage().contains("Auth.REALM"), e.getMessage());
		});

	}

	@Test
	@DisplayName("D-185 種別に使えない文字は断る")
	void invalidRealm () {

		Router bad = new Router();
		bad.get("/x", context -> { }).attribute(Auth.REALM, "運用者");
		bad.seal();

		try (WebContext context = Fakes.context("GET", "/x")) {
			context.route(bad.match("GET", "/x"));
			assertThrows(IllegalArgumentException.class, () -> Auth.principal(context));
		}

		try (WebContext context = Fakes.context("GET", "/plain/me")) {
			assertThrows(IllegalArgumentException.class, () -> Auth.principal(context, "a b"));
		}

	}

	/**
	 * リクエストをまたいで中身を持ち越すメモリの保存先（1 つのブラウザ）
	 */
	private static final class MemoryStore implements SessionStore {

		/* 中身（無ければ null） */
		Data data;

		/* destroy が呼ばれた回数（ID の振り直しとログアウト） */
		int destroyed;

		@Override
		public SessionEntry load (WebContext context) {
			return data == null ? SessionEntry.empty() : new SessionEntry(copy(data), true);
		}

		@Override
		public void save (WebContext context, SessionEntry entry) {
			data = copy(entry.data());
		}

		@Override
		public void touch (WebContext context, SessionEntry entry) {
		}

		@Override
		public void destroy (WebContext context) {
			data = null;
			destroyed++;
		}

		private static Data copy (Data source) {
			Data copy = new Data();
			if (source != null) {
				copy.putAll(source);
			}
			return copy;
		}

	}

}
