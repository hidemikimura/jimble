package io.jimble.web.auth.oidc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.auth.mfa.Mfa;
import io.jimble.web.auth.mfa.MfaConf;
import io.jimble.web.auth.mfa.Totp;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OIDC で入る人にも二要素認証が掛かること（D-155）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>0.6.0 は OIDC と二要素認証を同じ版で入れたのに、噛み合っていなかった。</b>
 * {@code Oidc.callback} は自分の中で {@link Auth#login} まで進んでいたので、
 * <b>「Google でログイン」を選ぶだけで二要素が飛んだ</b>。
 * </p>
 *
 * <p>
 * <b>誰も落ちない。</b>ログインは成功し、画面も出る。
 * 起きているのは「掛けたはずの二要素が掛かっていない」だけで、
 * <b>掛かっていることを外から確かめる手段が無い</b>——だからテストが要る。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-web:pgTest
 * </pre>
 */
@Tag("db")
class OidcMfaIntegrationTest {

	/** 二要素を有効にしている人 */
	private static final long MFA_USER_ID = 8801;

	/** 有効にしていない人 */
	private static final long PLAIN_USER_ID = 8802;

	/** コードを入れる画面 */
	private static final String MFA_PATH = "/login/code";

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();

		originalConf = Conf.conf().config();

		Conf.replace(ConfigFactory.parseString("""
			session.store = "none"
			auth.mfa.secret_key = "0123456789abcdef0123456789abcdef"
			""").withFallback(originalConf));

		assertTrue(DBUtil.load(Conf.conf().config(), OidcMfaIntegrationTest.class)
			, "DB に接続できませんでした");

	}

	@AfterAll
	static void stopDataSource () {

		Mfa.disable(MFA_USER_ID);
		Mfa.disable(PLAIN_USER_ID);

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		SessionStores.reset();

		Mfa.disable(MFA_USER_ID);
		Mfa.disable(PLAIN_USER_ID);

	}

	@Test
	@DisplayName("D-155 二要素を有効にしている人は、OIDC でもコードを聞かれる")
	void mfaUserIsSentToTheCodeScreen () {

		enrollAndActivate();

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/auth/google/callback"), sink)) {

			Oidc.finishLogin(context, principal(MFA_USER_ID), "google:1234", MFA_PATH);

			/*
			 * <b>ここが要点である。</b>
			 * プロバイダの検証は全部通っているのに、<b>まだログインしていない</b>。
			 */
			assertFalse(Auth.principal(context).isAuthenticated()
				, "コードを入れる前にログインさせています（二要素が飛んでいます）");

			assertTrue(Mfa.isPending(context), "預かっていません");
			assertEquals(MFA_USER_ID, Mfa.pendingPrincipal(context).id());

			/*
			 * <b>{@code redirect} は組み立てるだけである。</b>
			 * 送るのはディスパッチャなので、テストでは自分で送る
			 * （{@code json()} に {@code send()} が要らないのと同じ仕掛け）。
			 */
			context.response().send();

			assertEquals(302, sink.status());
			assertEquals(MFA_PATH, sink.headers().get("Location"));

		}

	}

	@Test
	@DisplayName("D-155 コードを入れる画面を渡していなければ、入れない")
	void withoutTheCodeScreenTheUserIsRefused () {

		enrollAndActivate();

		try (WebContext context = Fakes.context("GET", "/auth/google/callback")) {

			/*
			 * <b>黙って入れるより、入れないほうがよい。</b>
			 * 2引数の callback（コードの画面を渡さない形）はここに来る。
			 * 断り方はいつもの 401 で、理由は返さない。
			 */
			HttpException thrown = assertThrows(HttpException.class
				, () -> Oidc.finishLogin(context, principal(MFA_USER_ID), "google:1234", null)
				, "コードの画面が無いのにログインさせています");

			assertEquals(401, thrown.statusCode());

			assertFalse(Auth.principal(context).isAuthenticated());
			assertFalse(Mfa.isPending(context), "断ったのに預かっています");

		}

	}

	@Test
	@DisplayName("二要素を使っていない人は、これまでどおりそのまま入れる")
	void plainUserLogsInDirectly () {

		try (WebContext context = Fakes.context("GET", "/auth/google/callback")) {

			Oidc.finishLogin(context, principal(PLAIN_USER_ID), "google:5678", MFA_PATH);

			assertTrue(Auth.principal(context).isAuthenticated(), "入れていません");
			assertEquals(PLAIN_USER_ID, Auth.principal(context).id());
			assertFalse(Mfa.isPending(context), "使っていない人を待たせています");

		}

	}

	@Test
	@DisplayName("画面のパスを渡していても、二要素を使っていない人は待たされない")
	void theCodeScreenDoesNotAffectPlainUsers () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("GET", "/auth/google/callback"), sink)) {

			Oidc.finishLogin(context, principal(PLAIN_USER_ID), "google:5678", MFA_PATH);

			context.response().send();

			assertTrue(Auth.principal(context).isAuthenticated());
			assertFalse(MFA_PATH.equals(sink.headers().get("Location"))
				, "使っていない人をコードの画面へ送っています");

		}

	}

	// region ここで固定していないこと

	/*
	 * - <b>プロバイダとのやり取り</b>（state / nonce / PKCE / ID トークンの検証）は見ていない。
	 *   ここで見たいのは<b>検証が全部通ったあとの分かれ道</b>だけで、
	 *   手前は {@code IdTokenTest} が見ている
	 * - <b>コードを入れたあとにログインが完了するところ</b>も見ていない。
	 *   そこはパスワードで入る道と同じ {@code Mfa.complete} で、
	 *   {@code MfaIntegrationTest} が見ている
	 */

	// endregion

	/**
	 * 二要素を有効にする
	 */
	private static void enrollAndActivate () {

		Mfa.Enrollment enrollment = Mfa.enroll(MFA_USER_ID, "member1@example.com");

		// 1つ前の窓のコードで有効にする（いまのコードは使い回しとして弾かれる）
		String code = Totp.at(Totp.fromBase32(enrollment.secret())
			, Instant.now().getEpochSecond() - MfaConf.period()
			, MfaConf.period(), MfaConf.digits());

		assertTrue(Mfa.activate(MFA_USER_ID, code), "有効にできていません");

	}

	/**
	 * 利用者
	 *
	 * @param id	利用者 ID
	 * @return	利用者
	 */
	private static Principal principal (long id) {

		return Principal.of(id, "テスト", "member");

	}

}
