package io.jimble.util.ip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP が範囲に入っているか（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>公開しているのに、どこからも呼ばれておらず、テストも無かった。</b>
 * 名前からして<b>「この IP を通すか」を決めるところで使われる</b>道具である——
 * <b>間違えれば、通してはいけない相手を通す</b>。
 * </p>
 */
class IPUtilTest {

	@Test
	@DisplayName("IPv4 の CIDR")
	void ipv4Cidr () {

		assertTrue(IPUtil.inRange("192.168.1.3", "192.168.1.0/24"));
		assertTrue(IPUtil.inRange("192.168.1.255", "192.168.1.0/24"), "末尾が外れています");
		assertFalse(IPUtil.inRange("192.168.2.3", "192.168.1.0/24"));

		/*
		 * <b>境界の1つ外</b>。{@code /24} は 192.168.1.0〜255 なので、
		 * 192.168.0.255 は入らない。
		 */
		assertFalse(IPUtil.inRange("192.168.0.255", "192.168.1.0/24"));

		// 網の先頭も入る
		assertTrue(IPUtil.inRange("192.168.1.0", "192.168.1.0/24"), "先頭が外れています");

	}

	@Test
	@DisplayName("D-164 /32 は、その1台に当たる")
	void slashThirtyTwoMatchesTheOneHost () {

		/*
		 * <b>以前は誰にも当たらなかった。</b>
		 * 範囲を {@code network + 1} 〜 {@code broadcast - 1} で作っていたので、
		 * {@code /32} は <b>min が max を追い越して空になる</b>——
		 * <b>1台だけを許す一覧のいちばんふつうの書き方が、全員を外していた</b>。
		 */
		assertTrue(IPUtil.inRange("1.2.3.4", "1.2.3.4/32"));
		assertFalse(IPUtil.inRange("1.2.3.5", "1.2.3.4/32"));

		// 点対点でよく使う /31 も同じく空だった
		assertTrue(IPUtil.inRange("1.2.3.4", "1.2.3.4/31"));
		assertTrue(IPUtil.inRange("1.2.3.5", "1.2.3.4/31"));
		assertFalse(IPUtil.inRange("1.2.3.6", "1.2.3.4/31"));

	}

	@Test
	@DisplayName("D-164 網の先頭と末尾も、その網に入っている")
	void theEdgesOfTheBlockAreInside () {

		/*
		 * <b>「割り当てられるホストか」ではなく「この網に入っているか」を見る。</b>
		 * IPv6 側は元から削っていなかったので、<b>同じ書き方で片方だけ答えが違っていた</b>。
		 */
		assertTrue(IPUtil.inRange("10.0.0.0", "10.0.0.0/8"));
		assertTrue(IPUtil.inRange("10.255.255.255", "10.0.0.0/8"));
		assertFalse(IPUtil.inRange("11.0.0.0", "10.0.0.0/8"));

		assertTrue(IPUtil.inRange("2001:db8::", "2001:db8::/32"));
		assertTrue(IPUtil.inRange("2001:db8::1", "2001:db8::1/128"));

	}

	@Test
	@DisplayName("IPv6 の CIDR")
	void ipv6Cidr () {

		assertTrue(IPUtil.inRange("2001:db8:1234:1a00::2", "2001:db8:1234:1a00::2/64"));
		assertFalse(IPUtil.inRange("2001:db8:1234:1a01::2", "2001:db8:1234:1a00::2/64"));

	}

	@Test
	@DisplayName("CIDR が無ければ、そのアドレスと同じかどうか")
	void withoutACidrItIsAnExactMatch () {

		assertTrue(IPUtil.inRange("192.168.1.3", "192.168.1.3"));
		assertFalse(IPUtil.inRange("192.168.1.4", "192.168.1.3"));

	}

	@Test
	@DisplayName("IPv4 と IPv6 は混ざらない")
	void theTwoFamiliesDoNotMix () {

		/*
		 * <b>ここが「通す」に倒れたら事故である。</b>
		 * 片方だけを書いた許可一覧に、もう片方で入られる。
		 */
		assertFalse(IPUtil.inRange("192.168.2.3", "2001:db8:1234:1a00::2/64"));
		assertFalse(IPUtil.inRange("2001:db8:1234:1a01::2", "192.168.1.0/24"));

	}

	@Test
	@DisplayName("読めないものは通さない")
	void garbageIsNotInRange () {

		/*
		 * <b>迷ったら閉じる。</b>例外を投げずに false を返す作りなので、
		 * <b>閉じる側に倒れていること</b>だけは押さえておく。
		 */
		assertFalse(IPUtil.inRange("abc", "192.168.1.0/24"));
		assertFalse(IPUtil.inRange("192.168.1.3", "abc"));
		assertFalse(IPUtil.inRange("", "192.168.1.0/24"));
		assertFalse(IPUtil.inRange(null, "192.168.1.0/24"));
		assertFalse(IPUtil.inRange("192.168.1.3", null));

	}

	// region ここで固定していないこと

	/*
	 * - <b>速さ</b>は見ていない。手で測った跡が {@code main} に残っていたが、
	 *   <b>その {@code main} は消した</b>（要件 D-164）——
	 *   公開クラスに置きっぱなしの動作確認用の入口だった
	 * - <b>ホスト名</b>（{@code example.com}）を渡したときの名前解決は見ていない。
	 *   <b>解決しに行く道</b>があると、判定のたびに外へ問い合わせが飛びうる
	 */

	// endregion

}
