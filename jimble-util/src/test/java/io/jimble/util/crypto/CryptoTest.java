package io.jimble.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Signer} と {@link Aead} のテスト
 */
class CryptoTest {

	/** 鍵 */
	private static final String SECRET = "test-secret-key";

	// region 署名

	@Test
	@DisplayName("署名して検証できる")
	void signAndUnsign () {

		String signed = Signer.sign("user-42", SECRET);

		assertNotEquals("user-42", signed);
		assertTrue(signed.endsWith("|user-42"), signed);
		assertEquals("user-42", Signer.unsign(signed, SECRET));

	}

	@Test
	@DisplayName("値を書き換えたら検証に落ちる")
	void unsignRejectsTamperedValue () {

		String signed = Signer.sign("user-42", SECRET);
		String tampered = signed.substring(0, signed.indexOf('|') + 1) + "user-99";

		assertNull(Signer.unsign(tampered, SECRET));

	}

	@Test
	@DisplayName("鍵が違えば検証に落ちる")
	void unsignRejectsOtherSecret () {

		assertNull(Signer.unsign(Signer.sign("user-42", SECRET), "another"));

	}

	@Test
	@DisplayName("署名の形をしていなければ検証に落ちる")
	void unsignRejectsPlainValue () {

		assertNull(Signer.unsign("user-42", SECRET));

	}

	// endregion

	// region 暗号化

	@Test
	@DisplayName("暗号化して復号できる")
	void encryptAndDecrypt () {

		String encrypted = Aead.encrypt("秘密の値", SECRET);

		assertNotEquals("秘密の値", encrypted);
		assertEquals("秘密の値", Aead.decrypt(encrypted, SECRET));

	}

	@Test
	@DisplayName("同じ平文でも毎回違う暗号文になる")
	void encryptIsRandomized () {

		// 固定 IV だと「同じ値かどうか」が外から分かってしまう
		assertNotEquals(Aead.encrypt("same", SECRET), Aead.encrypt("same", SECRET));

	}

	@Test
	@DisplayName("暗号文を書き換えたら復号できない（空文字ではなく null）")
	void decryptRejectsTampered () {

		String encrypted = Aead.encrypt("秘密の値", SECRET);
		String tampered = encrypted.substring(0, encrypted.length() - 2)
			+ (encrypted.endsWith("AA") ? "BB" : "AA");

		assertNull(Aead.decrypt(tampered, SECRET));

	}

	@Test
	@DisplayName("鍵が違えば復号できない")
	void decryptRejectsOtherSecret () {

		assertNull(Aead.decrypt(Aead.encrypt("秘密の値", SECRET), "another"));

	}

	@Test
	@DisplayName("鍵が空なら暗号化で落とす（黙って平文を通さない）")
	void encryptRequiresSecret () {

		assertThrows(IllegalStateException.class, () -> Aead.encrypt("値", ""));

	}

	// endregion

}
