package io.jimble.util.hash;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * パスワードのハッシュ（暗号化は任意）
 *
 * <p>
 * 移送してきた RSS アプリで、鍵が無いときに
 * <b>1回目は {@code null}、2回目以降は「Could not initialize class」</b>
 * という形で落ちたのがきっかけで直したもの。
 * </p>
 */
class PasswordUtilTest {

	/** AES の鍵（32 バイト） */
	private static final String KEY = "0123456789abcdef0123456789abcdef";

	/** IV（16 バイト） */
	private static final String IV = "0123456789abcdef";

	/**
	 * 設定を差し替える
	 *
	 * @param hocon	設定
	 */
	private static void conf (String hocon) {

		Conf.replace(ConfigFactory.parseString(hocon));

	}

	@AfterEach
	void restore () {

		Conf.reload();

	}

	// region 暗号化しない

	@Test
	@DisplayName("鍵が無ければ暗号化しない。作って照合できる")
	void plainWithoutKey () {

		conf("");

		assertFalse(CipherUtil.isConfigured());
		assertFalse(PasswordUtil.isEncrypt(), "鍵が無いのに暗号化しようとしている");

		String hash = PasswordUtil.createHash("password");

		assertTrue(hash.startsWith("$2a$"), "BCrypt のハッシュがそのまま入っているはず: " + hash);
		assertTrue(PasswordUtil.check("password", hash));
		assertFalse(PasswordUtil.check("wrong", hash));

	}

	@Test
	@DisplayName("鍵があっても encrypt = false なら暗号化しない")
	void plainWhenTurnedOff () {

		conf("""
			cipher { key = "%s", iv = "%s" }
			hash { password { encrypt = false } }
			""".formatted(KEY, IV));

		assertTrue(CipherUtil.isConfigured());
		assertFalse(PasswordUtil.isEncrypt());

		String hash = PasswordUtil.createHash("password");

		assertTrue(hash.startsWith("$2a$"), hash);
		assertTrue(PasswordUtil.check("password", hash));

	}

	// endregion

	// region 暗号化する

	@Test
	@DisplayName("D-159 encrypt = true と書けば暗号化する。作って照合できる")
	void encryptedByDefaultWithKey () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			""".formatted(KEY, IV));

		assertTrue(PasswordUtil.isEncrypt(), "true と書いてあるのに暗号化していない");

		String hash = PasswordUtil.createHash("password");

		assertFalse(hash.startsWith("$2a$"), "暗号化されていない: " + hash);
		assertTrue(PasswordUtil.check("password", hash));
		assertFalse(PasswordUtil.check("wrong", hash));

	}

	@Test
	@DisplayName("ペッパーを付けても照合できる")
	void withPepper () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			hash { password { pepper = "pepper-value" } }
			""".formatted(KEY, IV));

		String hash = PasswordUtil.createHash("password");

		assertTrue(PasswordUtil.check("password", hash));

	}

	@Test
	@DisplayName("ペッパーが変わると照合できなくなる")
	void pepperChangeBreaksCheck () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			hash { password { pepper = "before" } }
			""".formatted(KEY, IV));

		String hash = PasswordUtil.createHash("password");

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			hash { password { pepper = "after" } }
			""".formatted(KEY, IV));

		assertFalse(PasswordUtil.check("password", hash));

	}

	// endregion

	// region 食い違ったとき

	@Test
	@DisplayName("暗号化したハッシュを平文として読んでも、落ちずに false を返す")
	void encryptedHashReadAsPlain () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			""".formatted(KEY, IV));

		String hash = PasswordUtil.createHash("password");

		conf("""
			cipher { key = "%s", iv = "%s" }
			hash { password { encrypt = false } }
			""".formatted(KEY, IV));

		assertFalse(PasswordUtil.check("password", hash));

	}

	@Test
	@DisplayName("鍵が変わったら、落ちずに false を返す")
	void keyChanged () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			""".formatted(KEY, IV));

		String hash = PasswordUtil.createHash("password");

		conf("""
			hash.password.encrypt = true
			cipher { key = "fedcba9876543210fedcba9876543210", iv = "%s" }
			""".formatted(IV));

		assertFalse(PasswordUtil.check("password", hash));

	}

	@Test
	@DisplayName("空のハッシュは false")
	void emptyHash () {

		conf("");

		assertFalse(PasswordUtil.check("password", ""));
		assertFalse(PasswordUtil.check("password", null));
		assertFalse(PasswordUtil.check(null, "x"));

	}

	// endregion

	// region CipherUtil

	@Test
	@DisplayName("鍵が無いのに暗号化しようとしたら、何をすればよいかを言って落ちる")
	void missingKeySaysWhatToDo () {

		conf("");

		IllegalStateException ex = assertThrows(
			IllegalStateException.class, () -> CipherUtil.encryptAes("x"));

		assertTrue(ex.getMessage().contains("cipher.key"), ex.getMessage());
		assertTrue(ex.getMessage().contains("application.conf"), ex.getMessage());

	}

	@Test
	@DisplayName("2回呼んでも同じメッセージが出る（static 初期化子をやめたので）")
	void sameMessageOnSecondCall () {

		conf("");

		String first = assertThrows(IllegalStateException.class, () -> CipherUtil.encryptAes("x"))
			.getMessage();

		String second = assertThrows(IllegalStateException.class, () -> CipherUtil.encryptAes("x"))
			.getMessage();

		assertEquals(first, second);

	}

	@Test
	@DisplayName("鍵の長さが違えば、その長さを言って落ちる")
	void badKeyLength () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "short", iv = "%s" }
			""".formatted(IV));

		IllegalStateException ex = assertThrows(
			IllegalStateException.class, () -> CipherUtil.encryptAes("x"));

		assertTrue(ex.getMessage().contains("16 / 24 / 32"), ex.getMessage());

	}

	@Test
	@DisplayName("暗号化して復号できる。同じ平文は同じ暗号文になる（固定 IV）")
	void encryptAndDecrypt () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			""".formatted(KEY, IV));

		String encrypted = CipherUtil.encryptAes("こんにちは");

		assertNotEquals("こんにちは", encrypted);
		assertEquals("こんにちは", CipherUtil.decryptAes(encrypted));

		// 固定 IV なので同じになる。だから新しいものには使わない
		assertEquals(encrypted, CipherUtil.encryptAes("こんにちは"));

	}

	@Test
	@DisplayName("暗号文でないものを復号したら、黙って空文字を返さずに落ちる")
	void decryptGarbage () {

		conf("""
			hash.password.encrypt = true
			cipher { key = "%s", iv = "%s" }
			""".formatted(KEY, IV));

		assertThrows(IllegalArgumentException.class, () -> CipherUtil.decryptAes("これは暗号文ではない"));

	}

	// endregion


	@Test
	@DisplayName("D-159 cipher.* があるのに encrypt を書いていなければ落ちる")
	void encryptMustBeWrittenWhenCipherIsSet () {

		/*
		 * <b>ここが「離れたキーで決まる既定」だった。</b>
		 * 既定は cipher.key と cipher.iv の<b>両方</b>が揃っていれば true——
		 * ところが Javadoc も CHANGELOG も要件も<b>「cipher.key があれば true」</b>と書いていた。
		 *
		 * つまり <b>cipher.key だけ足したアプリは false のまま</b>で、
		 * <b>あとから cipher.iv を足した瞬間に反転する</b>。
		 * 保存済みの BCrypt が「暗号化済み」として読まれ、<b>全員ログインできなくなる</b>——
		 * 返るのは「IDかパスワードが違います」だけである。
		 */
		conf("""
			cipher { key = "%s", iv = "%s" }
			""".formatted(KEY, IV));

		IllegalStateException thrown = assertThrows(IllegalStateException.class
			, PasswordUtil::isEncrypt, "書いていないのに黙って決めています");

		assertTrue(thrown.getMessage().contains(PasswordUtil.KEY_ENCRYPT), thrown.getMessage());

	}

	@Test
	@DisplayName("D-159 cipher.key だけでも落ちる（あとから iv を足した瞬間の反転を止める）")
	void halfConfiguredCipherAlsoFails () {

		conf("""
			cipher { key = "%s" }
			""".formatted(KEY));

		assertFalse(CipherUtil.isConfigured(), "片方だけでは揃っていない");
		assertTrue(CipherUtil.isPartlyConfigured());

		assertThrows(IllegalStateException.class, PasswordUtil::isEncrypt);

	}

	@Test
	@DisplayName("cipher.* をまったく書いていなければ、これまでどおり何も書かなくてよい")
	void withoutCipherNothingIsRequired () {

		conf("");

		assertFalse(PasswordUtil.isEncrypt());

	}

}
