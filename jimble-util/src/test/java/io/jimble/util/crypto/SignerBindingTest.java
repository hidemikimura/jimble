package io.jimble.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 名前に結びつけた署名（D-220）
 *
 * <p>
 * {@code sign} は値だけに署名するので、攻撃者が決めた値を署名させられる場所が1つあると、
 * その署名を別の名前の Cookie に移し替えて使えた。
 * </p>
 */
class SignerBindingTest {

	private static final List<String> KEYS = List.of("current-key", "old-key");

	@Test
	@DisplayName("同じ名前なら読める。別の名前に移し替えると読めない")
	void boundToName () {

		String signed = Signer.signFor("flash__message", "admin", "current-key");

		assertTrue(signed.startsWith(Signer.BOUND_PREFIX), signed);
		assertEquals("admin", Signer.unsignAnyFor("flash__message", signed, KEYS, true).value());
		assertNull(Signer.unsignAnyFor("role", signed, KEYS, true), "別の名前に移し替えた署名が通っています");

	}

	@Test
	@DisplayName("値を書き換えると読めない。古い鍵で署名したものは古いと分かる")
	void tamperAndRotation () {

		String signed = Signer.signFor("sid", "abc", "current-key");
		assertNull(Signer.unsignAnyFor("sid", signed.replace("|abc", "|abd"), KEYS, true));

		KeyMatch old = Signer.unsignAnyFor("sid", Signer.signFor("sid", "abc", "old-key"), KEYS, true);
		assertEquals("abc", old.value());
		assertTrue(old.isStale());

	}

	@Test
	@DisplayName("名前に結びついていない古い形（2.2.2 まで）は、移す間だけ読み、古いものとして扱う")
	void legacy () {

		String legacy = Signer.sign("abc", "current-key");

		KeyMatch match = Signer.unsignAnyFor("sid", legacy, KEYS, true);
		assertEquals("abc", match.value());
		assertTrue(match.isStale(), "書き直させるため、古いものとして扱う");

		assertNull(Signer.unsignAnyFor("sid", legacy, KEYS, false), "古い形を断る設定でも読んでいます");

	}

	@Test
	@DisplayName("名前と値の区切りをずらしても同じ MAC にならない")
	void noBoundaryConfusion () {

		String signed = Signer.signFor("ab", "c", "current-key");

		assertNull(Signer.unsignAnyFor("a", signed.replace("|c", "|bc"), KEYS, false));
		assertFalse(signed.equals(Signer.signFor("a", "bc", "current-key")));

	}

}
