package io.jimble.util.ip;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IPv6
 */
public class IPv6 {

	/* IP */
	private final String ip;

	/* lock */
	private final ReentrantLock lock = new ReentrantLock();

	/* 計算済み判定 */
	private boolean calculated = false;

	/* 最小値 */
	private BigInteger min;
	/* 最大値 */
	private BigInteger max;

	/**
	 * コンストラクタ
	 *
	 * @param ip	IP
	 */
	public IPv6 (String ip) {

		this.ip = ip;

	}

	/**
	 * 引数IP（CIDRなし）が範囲内かどうか判定する
	 *
	 * @param targetIp	対象IP（CIDRなし）
	 * @return	範囲内の場合 = true
	 * @throws UnknownHostException    例外
	 */
	public boolean inRange (String targetIp) throws UnknownHostException {

		calc();

		BigInteger ipBigInteger = ipToBigInteger(InetAddress.getByName(targetIp));
		return min.compareTo(ipBigInteger) <= 0 && 0 <= max.compareTo(ipBigInteger);

	}

	/**
	 * IPをBigIntegerに変換する
	 *
	 * @param ip	IP
	 * @return	BigInteger
	 */
	private static BigInteger ipToBigInteger (InetAddress ip) {
		return new BigInteger(1, ip.getAddress());
	}

	/**
	 * BigIntegerをIP表記に変換する
	 *
	 * @param ip	BigInteger
	 * @return	IP表記
	 */
	private static String bigIntegerToIp (BigInteger ip) throws UnknownHostException {
		byte[] bytes = ip.toByteArray();
		byte[] unsignedBytes = new byte[16];

		if (bytes.length == 17 && bytes[0] == 0) {
			System.arraycopy(bytes, 1, unsignedBytes, 0, 16);
		} else {
			System.arraycopy(bytes, 0, unsignedBytes, 16 - bytes.length, bytes.length);
		}

		InetAddress inetAddress = InetAddress.getByAddress(unsignedBytes);
		return inetAddress.getHostAddress();
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
				BigInteger ipBigInteger = ipToBigInteger(inetAddress);
				min = ipBigInteger;
				max = ipBigInteger;
			} else {
				InetAddress inetAddress = InetAddress.getByName(ip.substring(0, cidrIndex));
				BigInteger ipBigInteger = ipToBigInteger(inetAddress);
				int cidr = Integer.parseInt(ip.substring(cidrIndex + 1));

				BigInteger mask = BigInteger.ONE.shiftLeft(128 - cidr).subtract(BigInteger.ONE).not();

				min = ipBigInteger.and(mask);
				max = min.or(mask.not());
			}

			calculated = true;

		} finally {

			lock.unlock();

		}

	}

}
