package io.jimble.util.ip;

import java.net.UnknownHostException;

/**
 * IP ユーティリティ
 */
public class IPUtil {

	public static void main (String[] args) {

		System.out.println(inRange("192.168.1.3", "192.168.1.0/24"));
		System.out.println(inRange("192.168.2.3", "192.168.1.0/24"));

		System.out.println();

		System.out.println(inRange("2001:db8:1234:1a00::2", "2001:db8:1234:1a00::2/64"));
		System.out.println(inRange("2001:db8:1234:1a01::2", "2001:db8:1234:1a00::2/64"));

		System.out.println();

		System.out.println(inRange("192.168.2.3", "2001:db8:1234:1a00::2/64"));
		System.out.println(inRange("2001:db8:1234:1a01::2", "192.168.1.0/24"));

		System.out.println();

		long start = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			inRange("192.168.1.3", "192.168.1.0/24");
		}
		long end = System.currentTimeMillis();
		System.out.println("IPv4: " + (end - start) + "ms");
		// IPv4 = 0.0006ms


		start = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			inRange("2001:db8:1234:1a01::2", "2001:db8:1234:1a00::2/64");
		}
		end = System.currentTimeMillis();
		System.out.println("IPv6: " + (end - start) + "ms");
		// IPv6 = 0.0014ms

	}

	/**
	 * IP範囲判定
	 *
	 * @param target	対象IP
	 * @param range		範囲IP（CIDRありなし）
	 * @return	範囲内の場合 = true
	 */
	public static boolean inRange (String target, String range) {

		try {

			if (range.contains(":")) {
				// IPv6
				if (!target.contains(":")) {
					throw new UnknownHostException();
				}
				return new IPv6(range).inRange(target);
			} else {
				// IPv4
				if (target.contains(":")) {
					throw new UnknownHostException();
				}
				return new IPv4(range).inRange(target);
			}

		} catch (Throwable ex) {

			return false;

		}

	}

}
