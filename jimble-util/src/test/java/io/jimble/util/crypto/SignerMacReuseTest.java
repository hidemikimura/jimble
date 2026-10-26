package io.jimble.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 鍵で初期化した Mac を写して使う（2.7.5）
 *
 * <p>署名のたびに Mac を作り直すのをやめた。値が変わらないこと、鍵が混ざらないことを見る。</p>
 */
class SignerMacReuseTest {

	@Test
	@DisplayName("署名は HMAC-SHA256 をその場で計算したものと同じ（覚えた鍵でも、上限を超えた鍵でも）")
	void sameAsFreshMac () throws Exception {

		// 覚える上限（16）を超える数の鍵を、2 周ずつ使う
		for (int round = 0; round < 2; round++) {
			for (int i = 0; i < 40; i++) {
				String secret = "key-" + i;
				assertEquals(expected("value-" + i, secret), Signer.sign("value-" + i, secret), secret);
			}
		}

	}

	@Test
	@DisplayName("同時に署名しても、鍵ごとの値が混ざらない")
	void concurrent () throws Exception {

		ExecutorService pool = Executors.newFixedThreadPool(8);

		try {

			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 2_000; i++) {
				String secret = "k" + (i % 5);
				String value = "v" + i;
				results.add(pool.submit(() -> Signer.sign(value, secret)));
			}

			for (int i = 0; i < results.size(); i++) {
				assertEquals(expected("v" + i, "k" + (i % 5)), results.get(i).get());
			}

		} finally {
			pool.shutdownNow();
		}

	}

	@Test
	@DisplayName("鍵が空なら、これまでどおり落ちる")
	void emptySecret () {

		assertThrows(IllegalStateException.class, () -> Signer.sign("v", ""));
		assertThrows(IllegalStateException.class, () -> Signer.sign("v", ""));

	}

	private static String expected (String value, String secret) throws Exception {

		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))) + "|" + value;

	}

}
