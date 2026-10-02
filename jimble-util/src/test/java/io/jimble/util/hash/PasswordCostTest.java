package io.jimble.util.hash;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BCrypt のコストを設定で決める（D-247）
 */
class PasswordCostTest {

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
	@DisplayName("既定は 10。書けばそのコストで作り、保存済みのハッシュはそのまま照合できる")
	void cost () {

		String old = PasswordUtil.createHash("secret", false);
		assertTrue(old.startsWith("$2a$10$"), old);

		Conf.replace(ConfigFactory.parseString("hash.password.cost = 12").withFallback(original));

		String hash = PasswordUtil.createHash("secret", false);
		assertTrue(hash.startsWith("$2a$12$"), hash);
		assertTrue(PasswordUtil.check("secret", hash, false));
		assertTrue(PasswordUtil.check("secret", old, false), "コストを変えたら、前のハッシュが照合できません");

	}

	@Test
	@DisplayName("4〜31 の外は断る")
	void range () {

		Conf.replace(ConfigFactory.parseString("hash.password.cost = 3").withFallback(original));
		assertThrows(IllegalStateException.class, PasswordUtil::cost);

		Conf.replace(ConfigFactory.parseString("hash.password.cost = 10").withFallback(original));
		assertEquals(10, PasswordUtil.cost());

	}

}
