package io.jimble.util.hash;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 小さいヒープの別の JVM で、普通のスレッドを並べて Argon2id を計算させる（{@link Argon2ConcurrencyTest} から起動する）
 *
 * <p>引数：同時に計算する数の上限・並べるスレッドの数。OutOfMemoryError が出たら終了コード 3、出なければ 0。</p>
 */
public final class Argon2LoadProbe {

	/** OutOfMemoryError が出たときの終了コード */
	static final int EXIT_OOM = 3;

	private Argon2LoadProbe () {}

	public static void main (String[] args) throws Exception {

		int maxConcurrency = Integer.parseInt(args[0]);
		int threads = Integer.parseInt(args[1]);

		Conf.replace(ConfigFactory.parseString("hash.password.argon2.max_concurrency = " + maxConcurrency)
			.withFallback(Conf.conf().config()));

		// 先に1度作っておく（設定と Password4j を読み込む）
		PasswordUtil.createHash("warm-up");

		AtomicBoolean outOfMemory = new AtomicBoolean(false);
		CyclicBarrier start = new CyclicBarrier(threads);
		List<Thread> running = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			running.add(Thread.ofPlatform().start(() -> {
				try {
					start.await();
					PasswordUtil.createHash("password");
				} catch (OutOfMemoryError ex) {
					outOfMemory.set(true);
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}));
		}

		for (Thread thread : running) {
			thread.join();
		}

		System.out.println(outOfMemory.get() ? "OOM" : "OK");
		System.exit(outOfMemory.get() ? EXIT_OOM : 0);

	}

}
