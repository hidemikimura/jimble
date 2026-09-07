package io.jimble.util.ip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IPv4
 */
public class IPv4 {

	/* IP */
	private final String ip;

	/* lock */
	private final ReentrantLock lock = new ReentrantLock();

	/* 計算済み判定 */
	private boolean calculated = false;

	/* 最小値 */
	private long min;
	/* 最大値 */
	private long max;

	/**
	 * コンストラクタ
	 *
	 * @param ip	IP
	 */
	public IPv4 (String ip) {

		this.ip = ip;

	}

	/**
	 * 引数IP（CIDRなし）が範囲内かどうか判定する
	 *
	 * @param targetIp	対象IP（CIDRなし）
	 * @return	範囲内の場合 = true
	 * @throws UnknownHostException	例外
	 */
	public boolean inRange (String targetIp) throws UnknownHostException {

		calc();

		long ipLong = ipToLong(InetAddress.getByName(targetIp));
		return min <= ipLong && ipLong <= max;

	}

	/**
	 * IPをlongに変換する
	 *
	 * @param ip	IP
	 * @return	long
	 */
	private static long ipToLong (InetAddress ip) {
		byte[] octets = ip.getAddress();
		long result = 0;
		for (byte octet : octets) {
			result <<= 8;
			result |= (octet & 0xFF);
		}
		return result;
	}

	/**
	 * longをIP表記に変換する
	 *
	 * @param ip	long
	 * @return	IP表記
	 */
	private static String longToIp (long ip) {
		return ((ip >> 24) & 0xFF) + "." +
			((ip >> 16) & 0xFF) + "." +
			((ip >> 8) & 0xFF) + "." +
			(ip & 0xFF);
	}

	/**
	 * IPの最小、最大を計算する
	 *
	 * @throws UnknownHostException	例外
	 */
	private void calc () throws UnknownHostException {

		if (calculated) {
			return;
		}

		calcInner();

	}

	/**
	 * IPの最小、最大を計算する
	 *
	 * @throws UnknownHostException	例外
	 */
	private void calcInner () throws UnknownHostException {

		lock.lock();

		try {

			if (calculated) {
				return;
			}

			int cidrIndex = ip.indexOf('/');
			if (cidrIndex < 0) {
				InetAddress inetAddress = InetAddress.getByName(ip);
				long ipLong = ipToLong(inetAddress);
				min = ipLong;
				max = ipLong;
			} else {
				InetAddress inetAddress = InetAddress.getByName(ip.substring(0, cidrIndex));
				long ipLong = ipToLong(inetAddress);
				int cidr = Integer.parseInt(ip.substring(cidrIndex + 1));

				long mask = -1L << (32 - cidr);
				long network = ipLong & mask;
				long broadcast = network | ~mask;

				min = network + 1;
				max = broadcast - 1;
			}

			calculated = true;

		} finally {

			lock.unlock();

		}

	}

}
