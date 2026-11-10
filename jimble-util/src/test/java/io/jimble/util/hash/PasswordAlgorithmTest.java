package io.jimble.util.hash;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ハッシュの方式（Argon2id・BCrypt）と、作り直しの判定
 */
class PasswordAlgorithmTest {

	/** AES の鍵（32 バイト） */
	private static final String KEY = "0123456789abcdef0123456789abcdef";

	/** IV（16 バイト） */
	private static final String IV = "0123456789abcdef";

	private static void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon));

	}

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("既定は Argon2id（19 MiB・2 回・並列 1）。bcrypt と書けば BCrypt")
	void defaultIsArgon2id () {

		conf("");

		assertEquals(PasswordUtil.ALGORITHM_ARGON2ID, PasswordUtil.algorithm());
		assertTrue(PasswordUtil.createHash("secret").startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));

		conf("hash.password.algorithm = bcrypt");

		assertEquals(PasswordUtil.ALGORITHM_BCRYPT, PasswordUtil.algorithm());
		assertTrue(PasswordUtil.createHash("secret").startsWith("$2a$10$"));

	}

	@Test
	@DisplayName("知らない方式は、キーの名前を言って断る")
	void unknownAlgorithm () {

		conf("hash.password.algorithm = scrypt");

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> PasswordUtil.createHash("secret"));
		assertTrue(thrown.getMessage().contains(PasswordUtil.KEY_ALGORITHM), thrown.getMessage());

	}

	@Test
	@DisplayName("方式を変えても、保存済みのハッシュはどちらも照合できる（頭で見分ける）。ペッパーと暗号化も同じ")
	void mixed () {

		String settings = """
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			hash.password.pepper = "pepper-value"
			""".formatted(KEY, IV);

		conf(settings + "hash.password.algorithm = bcrypt");
		String bcrypt = PasswordUtil.createHash("secret");

		conf(settings + "hash.password.algorithm = argon2id");
		String argon2 = PasswordUtil.createHash("secret");

		for (String algorithm : new String[]{ "bcrypt", "argon2id" }) {

			conf(settings + "hash.password.algorithm = " + algorithm);

			assertTrue(PasswordUtil.check("secret", bcrypt), algorithm + " の設定で BCrypt を照合できない");
			assertTrue(PasswordUtil.check("secret", argon2), algorithm + " の設定で Argon2id を照合できない");
			assertFalse(PasswordUtil.check("wrong", bcrypt));
			assertFalse(PasswordUtil.check("wrong", argon2));

		}

	}

	@Test
	@DisplayName("Argon2id でも BCrypt でもないもの・Argon2i は、落ちずに false")
	void otherFormats () {

		conf("");

		assertFalse(PasswordUtil.check("secret", "plain-text"));
		assertFalse(PasswordUtil.check("secret", "$argon2i$v=19$m=19456,t=2,p=1$amltYmxlLXNhbHQtMDAwMQ$6vM3x410lF4Uau50N2HhKXGZFDTpxgCiBfPW1V9QPXw"));
		assertFalse(PasswordUtil.check("secret", "$argon2id$v=19$m=4194304,t=2,p=1$amltYmxlLXNhbHQtMDAwMQ$6vM3x410lF4Uau50N2HhKXGZFDTpxgCiBfPW1V9QPXw"));

	}

	@Test
	@DisplayName("needsRehash：Argon2id の設定では、BCrypt と、パラメータの違う Argon2id を作り直す")
	void needsRehashUnderArgon2id () {

		conf("hash.password.algorithm = bcrypt");
		String bcrypt = PasswordUtil.createHash("secret");

		conf("hash.password.argon2.memory = 8MiB");
		String smaller = PasswordUtil.createHash("secret");

		conf("");
		String current = PasswordUtil.createHash("secret");

		assertTrue(PasswordUtil.needsRehash(bcrypt), "BCrypt を作り直さない");
		assertTrue(PasswordUtil.needsRehash(smaller), "メモリの違う Argon2id を作り直さない");
		assertFalse(PasswordUtil.needsRehash(current), "いまの設定で作ったものを作り直す");

		// 作り直したものは、もう作り直さない
		assertFalse(PasswordUtil.needsRehash(PasswordUtil.createHash("secret")));

	}

	@Test
	@DisplayName("needsRehash：BCrypt の設定では、Argon2id と、コストの違う BCrypt を作り直す")
	void needsRehashUnderBcrypt () {

		conf("");
		String argon2 = PasswordUtil.createHash("secret");

		conf("hash.password { algorithm = bcrypt, cost = 4 }");
		String cheap = PasswordUtil.createHash("secret");

		conf("hash.password.algorithm = bcrypt");
		String current = PasswordUtil.createHash("secret");

		assertTrue(PasswordUtil.needsRehash(argon2));
		assertTrue(PasswordUtil.needsRehash(cheap));
		assertFalse(PasswordUtil.needsRehash(current));

	}

	@Test
	@DisplayName("needsRehash：暗号化とペッパーを外してから見る。読めないもの・空は false")
	void needsRehashUnwraps () {

		String settings = """
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			hash.password.pepper = "pepper-value"
			""".formatted(KEY, IV);

		conf(settings + "hash.password.algorithm = bcrypt");
		String bcrypt = PasswordUtil.createHash("secret");

		conf(settings);
		String argon2 = PasswordUtil.createHash("secret");

		assertTrue(PasswordUtil.needsRehash(bcrypt));
		assertFalse(PasswordUtil.needsRehash(argon2));
		assertFalse(PasswordUtil.needsRehash("not-encrypted"));
		assertFalse(PasswordUtil.needsRehash(""));
		assertFalse(PasswordUtil.needsRehash(null));

	}

}
