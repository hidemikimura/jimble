package io.jimble.util.ip;

import java.net.UnknownHostException;

/**
 * IP ユーティリティ
 */
public class IPUtil {

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
