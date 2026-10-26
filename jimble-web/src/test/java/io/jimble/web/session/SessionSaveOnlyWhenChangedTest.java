package io.jimble.web.session;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.crypto.Signer;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 変えたときだけ書く（2.7.6）
 *
 * <p>
 * 2.7.5 までは、<b>読んだだけでも {@code save()} で中身を丸ごと書き直していた</b>
 * （Redis は DEL・項目の数だけ HSET・PEXPIRE、DB は {@code UPDATE ... SET data = ?}）。
 * 要求ごとに {@code save()} を呼ぶアプリでは、読むだけのページでも毎回これが走る。
 * </p>
 *
 * <p>
 * <b>速さだけの話ではない。</b>読むだけのリクエスト A が読んだあと、同じ人のリクエスト B が
 * カートを変えて保存すると、<b>A の {@code save()} が古い中身で書き戻し、B の変更が消えていた</b>。
 * </p>
 */
class SessionSaveOnlyWhenChangedTest {

	/** 読み込むと既存のセッションを返し、呼ばれたことを記録する保存先 */
	static final class RecordingStore implements SessionStore {

		final List<String> calls = new ArrayList<>();

		/** 読み込んで返した中身 */
		SessionEntry loaded;

		/** touch に渡された中身 */
		SessionEntry touched;

		@Override
		public SessionEntry load (WebContext context) {
			calls.add("load");
			Data data = new Data();
			data.put("user_id", "42");
			loaded = new SessionEntry(data, true);
			return loaded;
		}

		@Override
		public void save (WebContext context, SessionEntry entry) {
			calls.add("save");
		}

		@Override
		public void touch (WebContext context, SessionEntry entry) {
			calls.add("touch");
			touched = entry;
		}

		@Override
		public void destroy (WebContext context) {
			calls.add("destroy");
		}

	}

	private final RecordingStore store = new RecordingStore();

	@AfterEach
	void reset () {

		Conf.reload();

	}

	/**
	 * 1 リクエスト動かす
	 *
	 * @param source	入力口
	 * @param sink		出力口
	 * @param handler	中でやること
	 */
	private void request (Fakes.FakeRequestSource source, Fakes.FakeResponseSink sink, java.util.function.Consumer<Session> handler) {

		try (WebContext context = new WebContext(source, sink)) {
			context.sessionStore(store);
			handler.accept(context.session());
			context.response().send("ok");
		}

	}

	private void request (java.util.function.Consumer<Session> handler) {

		request(new Fakes.FakeRequestSource("GET", "/"), new Fakes.FakeResponseSink(), handler);

	}

	@Test
	@DisplayName("読んだだけなら save() は中身を書かず、生存期間だけ延ばす（読んだ中身を渡す）")
	void readOnlyTouches () {

		request(session -> {
			assertEquals(42, session.getInt("user_id"));
			session.save();
		});

		assertEquals(List.of("load", "touch"), store.calls);
		assertTrue(store.touched.isExisting(), "読んだ中身が渡っていない");
		assertEquals("42", store.touched.data().getString("user_id"));

	}

	@Test
	@DisplayName("変えたら save() で書く")
	void changedSaves () {

		request(session -> {
			session.put("cart", "1");
			session.save();
		});

		assertEquals(List.of("load", "save"), store.calls);

	}

	@Test
	@DisplayName("消しても全部消しても書く")
	void removeAndClearSave () {

		request(session -> {
			session.remove("user_id");
			session.save();
		});

		request(session -> {
			session.clear();
			session.save();
		});

		assertEquals(List.of("load", "save", "load", "save"), store.calls);

	}

	@Test
	@DisplayName("ID を振り直したら、中身を変えていなくても書く（新しい ID で書き直す）")
	void regenerateSaves () {

		request(session -> {
			session.get("user_id");
			session.regenerateId();
			session.save();
		});

		assertEquals(List.of("load", "destroy", "save"), store.calls);

	}

	@Test
	@DisplayName("触っていなければ、これまでどおり生存期間だけ延ばす（空の中身を渡す）")
	void untouchedTouches () {

		request(Session::save);

		assertEquals(List.of("touch"), store.calls);
		assertFalse(store.touched.isExisting());

	}

	@Test
	@DisplayName("書いたあとに読むだけの save() を重ねても、もう書かない")
	void savedThenSavedAgain () {

		request(session -> {
			session.put("cart", "1");
			session.save();
			session.get("cart");
			session.save();
		});

		assertEquals(List.of("load", "save"), store.calls);

	}

	@Test
	@DisplayName("読んだだけでも、古い鍵で署名されたセッション ID は今の鍵で署名し直す")
	void readOnlyResignsStaleSessionId () {

		Conf.replace(ConfigFactory.parseString("""
			cookie.secret = "new-secret"
			cookie.previous_secrets = ["old-secret"]
			""").withFallback(Conf.conf().config()));

		String name = SessionConf.cookieName();

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");
		source.cookie(name, Signer.signFor(name, "sid-1", "old-secret"));
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		request(source, sink, session -> {
			session.get("user_id");
			session.save();
		});

		assertEquals(List.of("load", "touch"), store.calls);

		String expected = name + "=" + Signer.signFor(name, "sid-1", "new-secret") + ";";
		assertTrue(sink.setCookies().stream().anyMatch(c -> c.startsWith(expected))
			, "署名し直していない: " + sink.setCookies());

	}

	@Test
	@DisplayName("延ばすときに渡すのは、読み込んだ中身そのもの（保存先が読み直さずに済む）")
	void touchReceivesTheLoadedEntry () {

		request(session -> {
			session.has("user_id");
			session.save();
		});

		assertSame(store.loaded, store.touched);

	}

}
