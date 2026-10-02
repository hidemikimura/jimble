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

		/*
		 * <b>IP の字面だけを受ける。名前は引かない</b>（D-237）。かつては対象を InetAddress.getByName に渡していたので、
		 * 名前（X-Forwarded-For に書かれたものなど）を渡すと DNS を引き、<b>引いた先の IP で許可リストを通れた</b>
		 */
		if (!isLiteral(target)) {
			return false;
		}

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


	/**
	 * IP の字面か（名前は引かない）
	 *
	 * @param value	値
	 * @return	IPv4 / IPv6 の字面なら true
	 */
	static boolean isLiteral (String value) {

		if (value == null || value.isBlank()) {
			return false;
		}

		try {
			java.net.InetAddress.ofLiteral(value.trim());
			return true;
		} catch (IllegalArgumentException ex) {
			return false;
		}

	}

}
