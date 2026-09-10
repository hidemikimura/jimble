package io.jimble.web.server;

import io.jimble.util.conf.Conf;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鍵が無いことを起動時に言う（要件 F-S-08 / NF-S-09）
 *
 * <p>
 * <b>鍵が無いと、署名の機能が丸ごと黙って無効になる。</b>
 * 例外も出ないし Cookie は普通に読み書きできるので、<b>動いているように見える</b>——
 * <b>効いていないことに気づく手がかりが1つも無い</b>のがいちばん困る。
 * </p>
 */
class MissingSecretWarningTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	設定
	 */
	private static void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon));

	}

	@Test
	@DisplayName("cookie.secret が空なら言う")
	void noCookieSecret () {

		conf("");

		List<String> warnings = StartupReport.missingSecretWarnings();

		assertEquals(1, warnings.size(), warnings.toString());

		// 何を設定すればよいのかを言う（言わないと探させることになる）
		assertTrue(warnings.get(0).contains("cookie.secret"), warnings.get(0));

	}

	@Test
	@DisplayName("session.store = cookie なのに session.secret が空なら言う")
	void noSessionSecret () {

		conf("""
			cookie  { secret = "あるよ" }
			session { store  = "cookie" }
			""");

		List<String> warnings = StartupReport.missingSecretWarnings();

		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("session.secret"), warnings.get(0));

	}

	@Test
	@DisplayName("Cookie セッションを使っていなければ、session.secret が空でも言わない")
	void sessionSecretNotNeeded () {

		/*
		 * <b>要らないものを毎回言うと、ログの警告が読み飛ばされるようになる。</b>
		 * そうなると、本当に効く警告も読まれない
		 */
		conf("""
			cookie  { secret = "あるよ" }
			session { store  = "db" }
			""");

		assertEquals(List.of(), StartupReport.missingSecretWarnings());

	}

	@Test
	@DisplayName("両方あれば何も言わない")
	void allSet () {

		conf("""
			cookie  { secret = "あるよ" }
			session { store = "cookie", secret = "これもあるよ" }
			""");

		assertEquals(List.of(), StartupReport.missingSecretWarnings());

	}

	@Test
	@DisplayName("両方無ければ2つとも言う")
	void bothMissing () {

		conf("""
			session { store = "cookie" }
			""");

		assertEquals(2, StartupReport.missingSecretWarnings().size());

	}

}
