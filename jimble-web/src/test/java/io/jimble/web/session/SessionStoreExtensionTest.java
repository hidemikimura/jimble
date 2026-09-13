package io.jimble.web.session;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保存先の拡張点（{@code SessionStores.replace} / {@code SessionStore.cleanupExpired}）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code SessionStores.db()} ほかは具体クラスを返す。</b>1.0 では戻り値の型を
 * 変えられないので、代わりに<b>足りていなかった2つを足した</b>——
 * 自前の保存先を既定にする口と、保存先を知らずに掃除する口である。
 * </p>
 *
 * <p>
 * <b>掃除のほうが危ない。</b>{@code SessionStores.db().cleanupExpired()} と書いた
 * アプリは、設定を Redis に変えても<b>そのまま動く</b>——DB のセッション表だけを
 * 掃除しつづけるだけである。例外も出ないし、Redis のほうは TTL が消すので
 * <b>誰も困らないまま、掃除するつもりの対象が入れ替わっている。</b>
 * </p>
 */
class SessionStoreExtensionTest {

	/** 呼ばれたことだけ記録する保存先 */
	static final class RecordingStore implements SessionStore {

		final List<String> calls = new ArrayList<>();

		@Override
		public SessionEntry load (WebContext context) {
			calls.add("load");
			return SessionEntry.empty();
		}

		@Override
		public void save (WebContext context, SessionEntry entry) {
			calls.add("save");
		}

		@Override
		public void touch (WebContext context, SessionEntry entry) {
			calls.add("touch");
		}

		@Override
		public void destroy (WebContext context) {
			calls.add("destroy");
		}

	}

	@AfterEach
	void reset () {

		SessionStores.reset();
		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	HOCON
	 */
	private static void conf (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));
		SessionStores.reset();

	}

	// region 差し替え口

	@Test
	@DisplayName("自前の保存先を既定にできる")
	void replaceInstallsACustomDefaultStore () {

		conf("session.store = \"none\"\nsession.secret = \"test-secret\"");

		RecordingStore store = new RecordingStore();

		SessionStores.replace(store);

		assertSame(store, SessionStores.defaultStore());

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink())) {

			context.session().put("user_id", 1);
			context.session().save();
			context.response().send("ok");

		}

		assertTrue(store.calls.contains("save")
			, "差し替えた保存先が使われていません: " + store.calls);

	}

	@Test
	@DisplayName("null を渡すと設定から作り直す")
	void replaceNullGoesBackToTheSetting () {

		conf("session.store = \"none\"");

		RecordingStore store = new RecordingStore();

		SessionStores.replace(store);
		assertSame(store, SessionStores.defaultStore());

		SessionStores.replace(null);

		assertNotSame(store, SessionStores.defaultStore());
		assertSame(SessionStores.none(), SessionStores.defaultStore());

	}

	// endregion

	// region 保存先を知らずに掃除する

	@Test
	@DisplayName("溜め込まない保存先の掃除は 0 件")
	void cleanupIsZeroForStoresThatDoNotAccumulate () {

		conf("session.secret = \"test-secret\"");

		assertEquals(0, SessionStores.none().cleanupExpired());
		assertEquals(0, SessionStores.cookie().cleanupExpired());

	}

	@Test
	@DisplayName("DB の保存先は掃除を自分で持っている（既定の 0 に落ちない）")
	void theDbStoreOverridesCleanup () throws Exception {

		/*
		 * <b>反射で確かめるのは、落ち方が静かだからである。</b>
		 * DbSessionStore#cleanupExpired の名前が変わったり @Override が外れたりすると、
		 * SessionStores.defaultStore().cleanupExpired() は<b>例外も警告も無く 0 を返す</b>——
		 * 「掃除は動いているが1件も無い」と見分けがつかない。
		 */
		assertNotSame(
			SessionStore.class
			, DbSessionStore.class.getMethod("cleanupExpired").getDeclaringClass()
			, "DbSessionStore が cleanupExpired() を持っていません（既定の 0 が返ります）");

	}

	// endregion

}
