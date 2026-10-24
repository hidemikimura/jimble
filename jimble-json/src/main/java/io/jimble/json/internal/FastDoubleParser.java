package io.jimble.json.internal;

import java.math.BigInteger;

/**
 * 10進の小数を double にする速い道（Eisel–Lemire。D-307）
 *
 * <p>
 * D. Lemire, "Number Parsing at a Gigabyte per Second"（2021）の方法。
 * 仮数（19 桁まで）と 5 の累乗の 128 ビットの近似をかけて、正しく丸めた double を直接作る。
 * {@code Double.parseDouble} は文字列を作ってから読むので、1つに 80ns ほどかかる。
 * </p>
 *
 * <p>
 * <b>決めきれない場合は NaN を返す</b>（呼んだ側が {@code Double.parseDouble} で読み直す）。
 * 仮数が 19 桁を超えるとき、指数が範囲の外のとき、非正規化数（とても小さい数）、
 * あふれるとき、近似の誤差で丸めが決まらないとき。
 * </p>
 */
final class FastDoubleParser {

	/* 表の最小の指数 */
	private static final int SMALLEST_POWER = -342;

	/* 表の最大の指数 */
	private static final int LARGEST_POWER = 308;

	/* 5^q の 128 ビットの近似（上位 64 ビットと下位 64 ビットを交互に） */
	private static final long[] POWER_OF_FIVE_128 = powersOfFive();

	/* 仮数のビット数（暗黙の 1 を除く） */
	private static final int MANTISSA_BITS = 52;

	/* 丸めに使う精度のマスク（64 - 52 - 3 = 9 ビット） */
	private static final long PRECISION_MASK = 0xFFFFFFFFFFFFFFFFL >>> (MANTISSA_BITS + 3);

	/* 指数の偏り */
	private static final int MINIMUM_EXPONENT = -1023;

	/* 無限大の指数 */
	private static final int INFINITE_POWER = 0x7FF;

	private FastDoubleParser () {
	}

	/**
	 * 数値の文字を読む
	 *
	 * @param buf		文字
	 * @param start		始まり
	 * @param length	長さ（{@code [+-]? digits* (. digits*)? ([eE][+-]? digits+)?} の形であること）
	 * @return	値。速い道で決めきれなければ NaN
	 */
	static double parse (char[] buf, int start, int length) {

		int i = start;
		int last = start + length;

		boolean negative = false;
		char c = buf[i];
		if (c == '-' || c == '+') {
			negative = c == '-';
			i++;
		}

		long mantissa = 0;
		int digits = 0;
		int exponent = 0;
		boolean dot = false;

		for (; i < last; i++) {
			c = buf[i];
			if (c == '.') {
				dot = true;
				continue;
			}
			if (c < '0' || c > '9') {
				break;
			}
			if (mantissa == 0 && c == '0') {
				// 先頭の 0 は桁に数えない
				if (dot) {
					exponent--;
				}
				continue;
			}
			if (digits == 19) {
				// 20 桁目からは切り捨ての扱いが要る。遅い道に任せる
				return Double.NaN;
			}
			mantissa = mantissa * 10 + (c - '0');
			digits++;
			if (dot) {
				exponent--;
			}
		}

		if (i < last) {
			// 指数
			i++;
			boolean negativeExponent = false;
			if (buf[i] == '+' || buf[i] == '-') {
				negativeExponent = buf[i] == '-';
				i++;
			}
			int value = 0;
			for (; i < last; i++) {
				if (value > 100_000) {
					return Double.NaN;
				}
				value = value * 10 + (buf[i] - '0');
			}
			exponent += negativeExponent ? -value : value;
		}

		if (mantissa == 0) {
			return negative ? -0.0 : 0.0;
		}

		double value = compute(mantissa, exponent);

		return negative ? -value : value;

	}

	/**
	 * {@code mantissa × 10^q} を正しく丸めた double（決めきれなければ NaN）
	 */
	private static double compute (long w, int q) {

		if (q < SMALLEST_POWER || q > LARGEST_POWER) {
			return Double.NaN;
		}

		int lz = Long.numberOfLeadingZeros(w);
		w <<= lz;

		int index = 2 * (q - SMALLEST_POWER);
		long high = POWER_OF_FIVE_128[index];
		long low = POWER_OF_FIVE_128[index + 1];

		long productHigh = Math.unsignedMultiplyHigh(w, high);
		long productLow = w * high;

		if ((productHigh & PRECISION_MASK) == PRECISION_MASK) {
			long secondHigh = Math.unsignedMultiplyHigh(w, low);
			long sum = productLow + secondHigh;
			if (Long.compareUnsigned(sum, productLow) < 0) {
				productHigh++;
			}
			productLow = sum;
			if (productLow == -1L && (q < -27 || q > 55)) {
				// 近似の誤差で、丸めが決まらない
				return Double.NaN;
			}
		}

		int upperBit = (int) (productHigh >>> 63);
		long mantissa = productHigh >>> (upperBit + 64 - MANTISSA_BITS - 3);

		int power2 = power(q) + upperBit - lz - MINIMUM_EXPONENT;

		if (power2 <= 0) {
			// 非正規化数
			return Double.NaN;
		}

		// ちょうど真ん中なら偶数に丸める
		if (Long.compareUnsigned(productLow, 1) <= 0 && q >= -4 && q <= 23 && (mantissa & 3) == 1) {
			if ((mantissa << (upperBit + 64 - MANTISSA_BITS - 3)) == productHigh) {
				mantissa &= ~1L;
			}
		}

		mantissa += mantissa & 1;
		mantissa >>>= 1;

		if (Long.compareUnsigned(mantissa, 2L << MANTISSA_BITS) >= 0) {
			mantissa = 1L << MANTISSA_BITS;
			power2++;
		}

		mantissa &= ~(1L << MANTISSA_BITS);

		if (power2 >= INFINITE_POWER) {
			return Double.NaN;
		}

		return Double.longBitsToDouble(mantissa | ((long) power2 << MANTISSA_BITS));

	}

	/**
	 * 10^q の 2 の指数（floor(log2(10^q)) + 63）
	 */
	private static int power (int q) {

		return (((152170 + 65536) * q) >> 16) + 63;

	}

	/**
	 * 5^q（q は -342〜308）の 128 ビットの近似を作る
	 *
	 * <p>
	 * fast_float と同じ作り方：q が 0 以上なら 5^q を最上位ビットが 127 になるまで寄せて切り捨て、
	 * 負なら 2^b / 5^-q に 1 を足して（切り上げて）128 ビットに切り詰める。
	 * </p>
	 */
	private static long[] powersOfFive () {

		BigInteger two128 = BigInteger.ONE.shiftLeft(128);
		BigInteger two127 = BigInteger.ONE.shiftLeft(127);
		BigInteger five = BigInteger.valueOf(5);
		BigInteger mask64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

		long[] table = new long[2 * (LARGEST_POWER - SMALLEST_POWER + 1)];

		for (int q = SMALLEST_POWER; q <= LARGEST_POWER; q++) {

			BigInteger c;

			if (q < 0) {
				BigInteger power5 = five.pow(-q);
				int z = power5.subtract(BigInteger.ONE).bitLength();   // (1 << z) >= power5 になる最小の z
				int b = q >= -27 ? z + 127 : 2 * z + 2 * 64;
				c = BigInteger.ONE.shiftLeft(b).divide(power5).add(BigInteger.ONE);
				while (c.compareTo(two128) >= 0) {
					c = c.shiftRight(1);
				}
			} else {
				c = five.pow(q);
				while (c.compareTo(two127) < 0) {
					c = c.shiftLeft(1);
				}
				while (c.compareTo(two128) >= 0) {
					c = c.shiftRight(1);
				}
			}

			int index = 2 * (q - SMALLEST_POWER);
			table[index] = c.shiftRight(64).longValue();
			table[index + 1] = c.and(mask64).longValue();

		}

		return table;

	}

}
