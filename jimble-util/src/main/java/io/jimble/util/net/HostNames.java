package io.jimble.util.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class HostNames {

	private static volatile String cached;

	private final static ReentrantLock lock = new ReentrantLock();

	public static String get() {

		String v = cached;
		if (v == null) {
			try {
				lock.lock();
				if (cached == null) {
					cached = resolveHostName();
				}
				v = cached;
			} finally {
				lock.unlock();
			}
		}
		return v;

	}

	private static String resolveHostName() {
		// 1. 明示指定を最優先（Docker/K8s/CI では必ずこれで渡すのが堅実）
		for (String v : new String[] {
			System.getProperty("app.hostname")
			, System.getenv("APP_HOSTNAME")
			// Linux コンテナでは通常セットされる
			, System.getenv("HOSTNAME")
			// Windows
			, System.getenv("COMPUTERNAME")
		}) {
			if (v != null && !v.isBlank()) {
				return v.trim();
			}
		}

		// 2. Linux なら /etc/hostname が最速
		Path etc = Path.of("/etc/hostname");
		if (Files.isReadable(etc)) {
			try {
				String v = Files.readString(etc).trim();
				if (!v.isBlank()) {
					return v;
				}
			} catch (IOException ignore) {
				// fallthrough
			}
		}

		// 3. hostname コマンド（macOS/Windows を含めて確実、名前解決なし）
		try {
			Process p = new ProcessBuilder("hostname").redirectErrorStream(true).start();
			String v = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0 && !v.isBlank()) {
				return v;
			}
			p.destroyForcibly();
		} catch (Exception ignore) {
			// fallthrough
		}

		return "unknown";
	}

}
