package io.jimble.web.auth.oidc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.util.internal.WarnOnce;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OIDC の時間の設定を単位つきで読む（要件 D-159）
 *
 * <p>
 * {@code clock_skew} / {@code discovery_ttl} は素の数値（秒）で読んでいて、
 * <b>時間の設定は単位を書く</b>という決まりから外れていた。
 * 単位つきで読むようにし、これまでの素の数値は秒として受け続ける。
 * </p>
 */
class OidcConfTest {

	/** プロバイダの名前 */
	private static final String GOOGLE = "google";

	/* 元の設定 */
	private Config originalConf;

	@BeforeEach
	void keep () {

		Conf.reload();
		originalConf = Conf.conf().config();
		WarnOnce.reset();

	}

	@AfterEach
	void restore () {

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

		WarnOnce.reset();

	}

	/**
	 * google の設定を差し替える
	 *
	 * @param hocon	google のブロックの中身
	 */
	private void google (String hocon) {

		Conf.replace(ConfigFactory.parseString("auth.oidc.google { " + hocon + " }").withFallback(originalConf));

	}

	@Test
	@DisplayName("D-159 単位つきで読める（60s / 1h / 2m）")
	void withUnits () {

		google("clock_skew = 90s, discovery_ttl = 2h");

		assertEquals(90, OidcConf.clockSkewSeconds(GOOGLE));
		assertEquals(7200, OidcConf.cacheSeconds(GOOGLE));
		assertFalse(WarnOnce.warned("auth.oidc.google.clock_skew"), "単位を書いたのに警告しています");

		google("clock_skew = 2m");
		assertEquals(120, OidcConf.clockSkewSeconds(GOOGLE));

	}

	@Test
	@DisplayName("D-159 書かなければ 60 秒 / 1 時間（これまでと同じ）")
	void defaults () {

		google("issuer = \"https://accounts.google.com\"");

		assertEquals(60, OidcConf.clockSkewSeconds(GOOGLE));
		assertEquals(3600, OidcConf.cacheSeconds(GOOGLE));

	}

	@Test
	@DisplayName("D-159 素の数値は秒として読み、単位を書くよう1度だけ警告する（これまでの書き方）")
	void bareNumbersAreSeconds () {

		google("clock_skew = 30, discovery_ttl = 600");

		assertEquals(30, OidcConf.clockSkewSeconds(GOOGLE));
		assertEquals(600, OidcConf.cacheSeconds(GOOGLE));
		assertTrue(WarnOnce.warned("auth.oidc.google.clock_skew"));
		assertTrue(WarnOnce.warned("auth.oidc.google.discovery_ttl"));

	}

	@Test
	@DisplayName("下限はこれまでどおり（clock_skew は 0 秒、discovery_ttl は 60 秒）")
	void lowerBounds () {

		google("clock_skew = -5s, discovery_ttl = 10s");

		assertEquals(0, OidcConf.clockSkewSeconds(GOOGLE));
		assertEquals(60, OidcConf.cacheSeconds(GOOGLE));

	}

}
