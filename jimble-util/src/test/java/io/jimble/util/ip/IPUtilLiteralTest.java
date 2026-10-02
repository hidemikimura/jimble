package io.jimble.util.ip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 範囲の判定は IP の字面だけを受ける（D-237）
 */
class IPUtilLiteralTest {

	@Test
	@DisplayName("名前は引かない（引いた先の IP で許可リストを通らせない）")
	void noDnsLookup () {

		assertTrue(IPUtil.inRange("127.0.0.1", "127.0.0.0/8"));
		assertFalse(IPUtil.inRange("localhost", "127.0.0.0/8"), "名前を引いて範囲に入れています");
		assertTrue(IPUtil.inRange("2001:db8::1", "2001:db8::/32"));
		assertFalse(IPUtil.inRange("", "0.0.0.0/0"));

	}

}
