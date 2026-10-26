package io.jimble.web.golden;

import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cookie と応答のヘッダが 2.7.4 と同じか（2.7.5）
 *
 * <p>
 * 2.7.5 で Cookie・セキュリティヘッダなどの設定を、読み込んだ設定 1 つにつき 1 回だけ読む形にした。
 * {@code golden/response-2.7.4.txt} は {@link ResponseGoldenCases} を <b>Maven Central の 2.7.4 の jar で動かして</b>書き出したもの。
 * 設定を順に差し替えながら書き出すので、<b>差し替えたあとに古い値が残っていれば、ここで落ちる</b>。
 * 組み立てを足したら、答えも 2.7.4 の jar で作り直す（今のソースで作り直してはいけない）。
 * </p>
 */
class ResponseGoldenTest {

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("Set-Cookie・署名・セキュリティヘッダが、設定を差し替えるたびに 2.7.4 と同じになる")
	void sameAs274 () throws Exception {

		String expected;
		try (InputStream in = ResponseGoldenTest.class.getResourceAsStream("/golden/response-2.7.4.txt")) {
			assertNotNull(in, "golden/response-2.7.4.txt がありません");
			expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertEquals(expected, ResponseGoldenCases.render());
		// 2 回目は、どの値も一度読んで覚えたあと
		assertEquals(expected, ResponseGoldenCases.render());

	}

}
