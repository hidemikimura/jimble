package io.jimble.util.hash;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Argon2id のハッシュ（PHC の文字列）
 */
class Argon2HashTest {

	/**
	 * ほかの実装（OpenSSL 3.6 の ARGON2ID）で作った値
	 *
	 * <p>
	 * {@code openssl kdf -keylen 32 -kdfopt pass:... -kdfopt salt:... -kdfopt iter:2 -kdfopt memcost:19456 -kdfopt lanes:1 ARGON2ID}
	 * の結果を PHC の文字列にしたもの。その OpenSSL は RFC 9106 の 5.3 のテストベクタと一致することを確かめた。
	 * </p>
	 */
	private static final List<String[]> VECTORS = List.of(
		new String[]{ "correct horse battery staple", "$argon2id$v=19$m=19456,t=2,p=1$amltYmxlLXNhbHQtMDAwMQ$6vM3x410lF4Uau50N2HhKXGZFDTpxgCiBfPW1V9QPXw" }
		, new String[]{ "パスワード🔑", "$argon2id$v=19$m=4096,t=3,p=4$c2FsdHNhbHRzYWx0c2FsdA$aa7ggc0amru7iU9tqOt4Cx/AHOhpJzyWPmsxkzecxk4" }
		, new String[]{ "short-output", "$argon2id$v=19$m=1024,t=1,p=2$YWJjZGVmZ2hpamtsbW5vcA$r5baWB5/5QdOnLf1zakheg" }
	);

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
	@DisplayName("ほかの実装（OpenSSL）で作ったハッシュと照合できる。日本語・絵文字は UTF-8・並列 4・16 バイトのハッシュも")
	void interoperable () {

		for (String[] vector : VECTORS) {
			assertTrue(Argon2Hash.matches(vector[0], vector[1]), vector[1]);
			assertFalse(Argon2Hash.matches(vector[0] + "x", vector[1]), vector[1]);
		}

	}

	@Test
	@DisplayName("作ったものは PHC の形で、設定のパラメータが入る。ソルトは毎回違う")
	void create () {

		String first = Argon2Hash.create("secret", Argon2Hash.configured(), new SecureRandom());
		String second = Argon2Hash.create("secret", Argon2Hash.configured(), new SecureRandom());

		assertTrue(first.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}"), first);
		assertNotEquals(first, second, "ソルトが同じ");
		assertTrue(Argon2Hash.matches("secret", first));
		assertTrue(Argon2Hash.matches("secret", second));

	}

	@Test
	@DisplayName("パラメータは hash.password.argon2.* で変えられる（メモリは大きさの単位で書く）")
	void configured () {

		Conf.replace(ConfigFactory.parseString("hash.password.argon2 { memory = 64MiB, iterations = 3, parallelism = 4 }").withFallback(original));

		assertEquals(new Argon2Hash.Params(65536, 3, 4), Argon2Hash.configured());

	}

	@Test
	@DisplayName("設定が範囲の外なら、キーの名前を言って断る")
	void configuredRange () {

		for (String conf : List.of("parallelism = 0", "parallelism = 65", "iterations = 0", "iterations = 101", "memory = 4KiB", "memory = 2GiB"
			, "memory = 16KiB, parallelism = 4")) {

			Conf.replace(ConfigFactory.parseString("hash.password.argon2 { %s }".formatted(conf)).withFallback(original));

			IllegalStateException thrown = assertThrows(IllegalStateException.class, Argon2Hash::configured, conf);
			assertTrue(thrown.getMessage().contains("hash.password.argon2."), thrown.getMessage());

		}

	}

	@Test
	@DisplayName("Argon2id の形でない・値が範囲の外のものは、計算する前に断る（壊れた値でメモリを使い切らない）")
	void rejectsMalformed () {

		String salt = "amltYmxlLXNhbHQtMDAwMQ";
		String hash = "6vM3x410lF4Uau50N2HhKXGZFDTpxgCiBfPW1V9QPXw";

		for (String malformed : List.of(
			"$argon2i$v=19$m=19456,t=2,p=1$" + salt + "$" + hash        // 別の型
			, "$argon2id$v=16$m=19456,t=2,p=1$" + salt + "$" + hash     // 古い版
			, "$argon2id$m=19456,t=2,p=1$" + salt + "$" + hash          // 版が無い
			, "$argon2id$v=19$m=4194304,t=2,p=1$" + salt + "$" + hash   // 4 GiB
			, "$argon2id$v=19$m=19456,t=1000,p=1$" + salt + "$" + hash
			, "$argon2id$v=19$m=19456,t=2,p=1000$" + salt + "$" + hash
			, "$argon2id$v=19$m=8,t=2,p=4$" + salt + "$" + hash         // 並列に足りない
			, "$argon2id$v=19$t=2,m=19456,p=1$" + salt + "$" + hash     // 順が違う
			, "$argon2id$v=19$m=x,t=2,p=1$" + salt + "$" + hash
			, "$argon2id$v=19$m=19456,t=2,p=1$" + salt + "$!!!"
			, "$argon2id$v=19$m=19456,t=2,p=1$" + salt + "$" + hash + "$extra"
			, "$argon2id$v=19$m=19456,t=2,p=1$" + salt + "$AAAA"        // 短すぎる
		)) {
			assertThrows(IllegalArgumentException.class, () -> Argon2Hash.matches("correct horse battery staple", malformed), malformed);
		}

	}

}
