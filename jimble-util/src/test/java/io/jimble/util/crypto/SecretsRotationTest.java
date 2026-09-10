package io.jimble.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鍵を順に試す（{@link Secrets} / {@link Signer#unsignAny} / {@link Aead#decryptAny}／要件 NF-S-09）
 *
 * <p>
 * <b>ここが黙って壊れると、鍵の入れ替えが「全員ログアウト」になる。</b>
 * しかも失敗の形が「読めなかった」なので、<b>普通のログアウトと見分けが付かない</b>。
 * </p>
 */
class SecretsRotationTest {

	/** 古い鍵 */
	private static final String OLD = "old-secret-old-secret";

	/** 新しい鍵 */
	private static final String NEW = "new-secret-new-secret";

	// region 並べ方

	@Test
	@DisplayName("先頭が「いま書くのに使う鍵」")
	void currentIsFirst () {

		assertEquals(List.of(NEW, OLD), Secrets.of(NEW, List.of(OLD)));

	}

	@Test
	@DisplayName("空は落とす")
	void dropsEmpty () {

		/*
		 * 鍵は環境変数から入ることが多く、<b>渡し忘れると空文字が混ざる</b>。
		 * ここで落とさないと、使うところで初めて落ちる
		 */
		assertEquals(List.of(NEW), Secrets.of(NEW, Arrays.asList("", null, NEW)));
		assertEquals(List.of(), Secrets.of("", List.of()));
		assertEquals(List.of(), Secrets.of(null, null));

	}

	@Test
	@DisplayName("同じ鍵は2度並べない")
	void dropsDuplicates () {

		/*
		 * <b>入れ替えが終わったあと previous_secrets を消し忘れる</b>のはよくある。
		 * 落とさないと「古い鍵で読めた」と数えてしまい、
		 * <b>終わっているのに終わっていないと言い続ける</b>
		 */
		assertEquals(List.of(NEW), Secrets.of(NEW, List.of(NEW)));

	}

	@Test
	@DisplayName("古い鍵しか無ければ、それが書く鍵になる")
	void onlyPrevious () {

		// secret を消して previous_secrets だけ残す、は書き間違いだが黙って壊さない
		assertEquals(List.of(OLD), Secrets.of("", List.of(OLD)));

	}

	@Test
	@DisplayName("返す並びは書き換えられない")
	void immutable () {

		List<String> secrets = Secrets.of(NEW, List.of(OLD));

		try {
			secrets.add("x");
		} catch (UnsupportedOperationException expected) {
			return;
		}

		throw new AssertionError("書き換えられてしまった");

	}

	// endregion

	// region 署名

	@Test
	@DisplayName("新しい鍵で署名したものは「今の鍵」で読める")
	void signCurrent () {

		KeyMatch match = Signer.unsignAny(Signer.sign("あたい", NEW), Secrets.of(NEW, List.of(OLD)));

		assertEquals("あたい", match.value());
		assertTrue(match.current());
		assertFalse(match.isStale());

	}

	@Test
	@DisplayName("古い鍵で署名したものも読めるが、「古い」と分かる")
	void signStale () {

		KeyMatch match = Signer.unsignAny(Signer.sign("あたい", OLD), Secrets.of(NEW, List.of(OLD)));

		assertEquals("あたい", match.value());

		/*
		 * <b>読めただけでは足りない。</b>古い鍵だったと分からないと、
		 * その場で書き直せないし、<b>いつ古い鍵を捨ててよいのかも分からない</b>
		 */
		assertTrue(match.isStale());

	}

	@Test
	@DisplayName("どの鍵でも合わなければ null")
	void signNoMatch () {

		assertNull(Signer.unsignAny(Signer.sign("あたい", "よその鍵"), Secrets.of(NEW, List.of(OLD))));

	}

	@Test
	@DisplayName("鍵が1本も無ければ null（黙って通さない）")
	void signNoSecrets () {

		assertNull(Signer.unsignAny(Signer.sign("あたい", NEW), List.of()));
		assertNull(Signer.unsignAny(Signer.sign("あたい", NEW), null));
		assertNull(Signer.unsignAny(null, Secrets.of(NEW, List.of())));

	}

	// endregion

	// region 暗号

	@Test
	@DisplayName("新しい鍵の暗号文は「今の鍵」で読める")
	void decryptCurrent () {

		KeyMatch match = Aead.decryptAny(Aead.encrypt("なかみ", NEW), Secrets.of(NEW, List.of(OLD)));

		assertEquals("なかみ", match.value());
		assertTrue(match.current());

	}

	@Test
	@DisplayName("古い鍵の暗号文も読めるが、「古い」と分かる")
	void decryptStale () {

		KeyMatch match = Aead.decryptAny(Aead.encrypt("なかみ", OLD), Secrets.of(NEW, List.of(OLD)));

		assertEquals("なかみ", match.value());
		assertTrue(match.isStale());

	}

	@Test
	@DisplayName("違う鍵でうっかり読めてしまうことはない")
	void decryptNeverWrongKey () {

		/*
		 * GCM は改ざん検知つきなので、鍵が違えば<b>必ず</b>読めない。
		 * これが成り立たない方式（CBC など）で順に試すと、
		 * <b>ゴミを平文として受け取る</b>ことがある
		 */
		List<String> others = new ArrayList<>();

		for (int i = 0; i < 50; i++) {
			others.add("鍵" + i);
		}

		String encrypted = Aead.encrypt("なかみ", NEW);

		assertNull(Aead.decryptAny(encrypted, others), "違う鍵で読めてしまった");

	}

	@Test
	@DisplayName("壊れた入力でも落ちない")
	void decryptBroken () {

		List<String> secrets = Secrets.of(NEW, List.of(OLD));

		assertNull(Aead.decryptAny("こわれている", secrets));
		assertNull(Aead.decryptAny("", secrets));
		assertNull(Aead.decryptAny(null, secrets));
		assertNull(Aead.decryptAny(Aead.encrypt("なかみ", NEW), List.of()));

	}

	// endregion

}
