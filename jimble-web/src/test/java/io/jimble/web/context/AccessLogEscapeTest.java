package io.jimble.web.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * アクセスログの1行にパスの制御文字を入れない（D-239）
 */
class AccessLogEscapeTest {

	@Test
	@DisplayName("改行などを \\xNN にする。制御文字が無ければ同じものを返す")
	void escape () {

		assertEquals("/a\\x0d\\x0aGET /admin 200", WebContext.escapeControl("/a\r\nGET /admin 200"));

		String plain = "/posts/1";
		assertSame(plain, WebContext.escapeControl(plain));

	}

}
