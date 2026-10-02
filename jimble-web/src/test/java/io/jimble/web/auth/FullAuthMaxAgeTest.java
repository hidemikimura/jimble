package io.jimble.web.auth;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FULL_AUTH を「最近パスワードを入れた人」に絞れる（D-249）
 */
class FullAuthMaxAgeTest {

	private Config original;

	@BeforeEach
	void keep () {

		Conf.reload();
		original = Conf.conf().config();

	}

	@AfterEach
	void restore () {

		Conf.replace(original);

	}

	@Test
	@DisplayName("既定では、パスワードを入れて入った人なら時間がたっても通す（これまでどおり）")
	void defaultHasNoLimit () {

		try (WebContext context = Fakes.context("GET", "/password")) {
			Auth.login(context, Principal.of(1, "alice", ""));
			assertTrue(Auth.fullyAuthenticated(context));
		}

	}

	@Test
	@DisplayName("auth.full_auth_max_age を書くと、その時間を過ぎたら入れ直しを求める")
	void maxAge () throws Exception {

		Conf.replace(ConfigFactory.parseString("auth.full_auth_max_age = 1s").withFallback(original));

		try (WebContext context = Fakes.context("GET", "/password")) {

			Auth.login(context, Principal.of(1, "alice", ""));
			assertTrue(Auth.fullyAuthenticated(context), "入れた直後なのに通しません");

			Thread.sleep(2100);

			assertFalse(Auth.fullyAuthenticated(context), "時間を過ぎても通しています");

		}

	}

}
