package io.jimble.util.net;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

public class LocalAddress {

	private static volatile String cached;

	private final static ReentrantLock lock = new ReentrantLock();

	public static String get() {
		String v = cached;
		if (v == null) {
			try {
				lock.lock();
				if (cached == null) {
					cached = resolve();
				}
				v = cached;
			} finally {
				lock.unlock();
			}
		}
		return v;
	}

	private static String resolve() {
		// (a) 明示指定を最優先（コンテナ/CI で確実に固定できる）
		String override = System.getProperty("app.local-address");
		if (override == null || override.isBlank()) {
			override = System.getenv("APP_LOCAL_ADDRESS");
		}
		if (override != null && !override.isBlank()) {
			return override;
		}

		// (b) ルーティングテーブルから外向きインタフェースのIPを引く。
		//     UDP の connect はパケットを送信しないので実質ゼロコスト、名前解決も発生しない。
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.connect(InetAddress.getByName("8.8.8.8"), 10002); // リテラルIPなのでDNSは引かれない
			InetAddress addr = socket.getLocalAddress();
			if (addr != null && !addr.isAnyLocalAddress() && !addr.isLoopbackAddress()) {
				return addr.getHostAddress();
			}
		} catch (Exception ignore) {
			// オフライン時などはフォールバックへ
		}

		// (c) NIC 列挙にフォールバック（これも名前解決なし）
		try {
			for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
					continue;
				}
				for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
						return addr.getHostAddress();
					}
				}
			}
		} catch (Exception ignore) {
			// noop
		}

		return "127.0.0.1";
	}

}
