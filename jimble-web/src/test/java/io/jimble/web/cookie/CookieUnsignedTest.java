package io.jimble.web.cookie;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 署名をあとから入れるときの移行期間（要件 D-159）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code cookie.secret} を初めて設定した瞬間、いま配ってある Cookie が
 * 全部いっぺんに検証落ちして捨てられる。</b>
 * {@code sid} も {@code csrf_token} も {@code remember} も flash もである——
 * <b>全員ログアウト、フォームは 403</b>。
 * </p>
 *
 * <p>
 * <b>しかも例外もログも出ない。</b>署名の合わない Cookie を捨てるのは<b>正しい動き</b>なので、
 * 枠組みから見れば異常が起きていない。
 * <b>「署名なしも受け付ける」途中の状態が作れなかった</b>のが問題だった。
 * </p>
 */
class CookieUnsignedTest {

	/** 署名鍵 */
	private static final String SECRET = "0123456789abcdef0123456789abcdef";

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
	 * 署名の無い Cookie を1つ持たせて読ませる
	 *
	 * @return	読めた値（読めなければ空文字）
	 */
	private String readUnsigned () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/");

		source.cookie("last_post", "123");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {
			return context.cookies().get("last_post");
		}

	}

	@Test
	@DisplayName("D-159 既定では、署名の無い Cookie は捨てる")
	void unsignedIsDroppedByDefault () {

		conf("cookie { secret = \"%s\" }".formatted(SECRET));

		assertTrue(CookieConf.isSigned());
		assertFalse(CookieConf.acceptUnsigned(), "移行用の口が既定で開いています");

		assertEquals("", readUnsigned(), "改ざんされた値をアプリに渡しています");

	}

	@Test
	@DisplayName("D-159 accept_unsigned = true の間は読む")
	void unsignedIsAcceptedDuringMigration () {

		conf("cookie { secret = \"%s\", accept_unsigned = true }".formatted(SECRET));

		/*
		 * <b>これが無いと、鍵を入れた瞬間に全員ログアウトする。</b>
		 * 書くほうは最初から署名するので、放っておけば署名つきに入れ替わる。
		 */
		assertEquals("123", readUnsigned(), "移行期間なのに捨てています");

	}

	@Test
	@DisplayName("署名を使っていなければ、そもそも関係ない")
	void withoutASecretNothingChanges () {

		conf("");

		assertFalse(CookieConf.isSigned());
		assertEquals("123", readUnsigned());

	}

	@Test
	@DisplayName("移行期間でも、書くほうは署名する")
	void writingAlwaysSigns () {

		conf("cookie { secret = \"%s\", accept_unsigned = true }".formatted(SECRET));

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/"), sink)) {
			context.cookies().put("last_post", "123");
			context.response().send("ok");
		}

		String setCookie = sink.setCookies().getFirst();

		/*
		 * <b>ここが「放っておけば入れ替わる」の根拠である。</b>
		 * 書くほうまで署名しなくなったら、移行は永久に終わらない。
		 */
		assertFalse(setCookie.startsWith("last_post=123;")
			, "署名せずに書いています（移行が終わりません）: " + setCookie);

	}

	// region ここで固定していないこと

	/*
	 * - <b>{@code cookie.unsigned} の数え方</b>は見ていない。
	 *   「0 になったら戻す」という運用の目安なので、値そのものは固定しない
	 * - <b>鍵の入れ替え</b>（{@code previous_secrets}）は見ていない。そこは
	 *   {@code SecretRotationTest} が見ている（要件 NF-S-09）
	 */

	// endregion

}
