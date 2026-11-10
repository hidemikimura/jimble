package io.jimble.util.hash;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Argon2id を同時に計算する数を絞る（hash.password.argon2.max_concurrency）
 *
 * <p>
 * 1 回の計算は終わるまで memory（既定 19 MiB）を持ち続けるので、普通のスレッドを並べると、並べた数だけメモリを使う。
 * 小さいヒープの別の JVM で、絞らなければ落ち、絞れば落ちないことを確かめる。
 * </p>
 */
class Argon2ConcurrencyTest {

	/** 子の JVM のヒープ */
	private static final String HEAP = "-Xmx160m";

	/** 並べるスレッドの数（19 MiB × 50 = 950 MiB。ヒープに入らない） */
	private static final int THREADS = 50;

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("絞れば、小さいヒープで普通のスレッドを 50 本並べても落ちない")
	void bounded () throws Exception {

		assertEquals(0, probe(2), "絞っているのに OutOfMemoryError になった");

	}

	@Test
	@DisplayName("絞らなければ（上限を大きくすれば）、同じ条件で OutOfMemoryError になる（上のテストが絞ったおかげで通っていること）")
	void unbounded () throws Exception {

		assertEquals(Argon2LoadProbe.EXIT_OOM, probe(Argon2Hash.MAX_CONCURRENCY), "絞らなくても落ちなかった（テストの条件が弱い）");

	}

	@Test
	@DisplayName("既定は CPU の数。範囲の外は、キーの名前を言って断る")
	void configured () {

		assertEquals(Runtime.getRuntime().availableProcessors(), Argon2Hash.maxConcurrency());

		for (String value : List.of("0", "1025")) {
			Conf.replace(ConfigFactory.parseString("hash.password.argon2.max_concurrency = " + value));
			IllegalStateException thrown = assertThrows(IllegalStateException.class, Argon2Hash::maxConcurrency, value);
			assertTrue(thrown.getMessage().contains(PasswordUtil.KEY_ARGON2_MAX_CONCURRENCY), thrown.getMessage());
		}

		Conf.replace(ConfigFactory.parseString("hash.password.argon2.max_concurrency = 3"));
		assertEquals(3, Argon2Hash.maxConcurrency());

	}

	/**
	 * 別の JVM で計算させる
	 *
	 * @param maxConcurrency	同時に計算する数の上限
	 * @return	終了コード
	 */
	private static int probe (int maxConcurrency) throws Exception {

		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

		Process process = new ProcessBuilder(java, HEAP, "-cp", System.getProperty("java.class.path")
			, Argon2LoadProbe.class.getName(), String.valueOf(maxConcurrency), String.valueOf(THREADS))
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start();

		assertTrue(process.waitFor(2, TimeUnit.MINUTES), "子の JVM が終わらない");

		return process.exitValue();

	}

}
