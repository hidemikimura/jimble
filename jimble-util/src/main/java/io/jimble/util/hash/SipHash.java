package io.jimble.util.hash;

/**
 * SipHash-2-4
 *
 * <p>
 * もとは guava の {@code Hashing.sipHash24()} を呼んでいた（要件 D-121）。
 * <b>出す値は 1 bit も変えていない。</b>ここで出した値は
 * {@code sql_cache} のタグ、{@code DBLock} のキー、レート制限の行として
 * <b>すでに DB に入っている</b>ので、変えると既存のデータと突き合わなくなる。
 * </p>
 *
 * <p>
 * 鍵は guava の既定と同じ（仕様書の例と同じ {@code 00 01 02 … 0f}）。
 * <b>秘密の鍵ではない。</b>これは暗号としてではなく、
 * 短くて衝突しにくい数を作るために使っている。
 * </p>
 *
 * @see <a href="https://www.aumasson.jp/siphash/siphash.pdf">SipHash: a fast short-input PRF</a>
 */
final class SipHash {

	/** 鍵の前半（guava の既定と同じ） */
	private static final long K0 = 0x0706050403020100L;

	/** 鍵の後半（guava の既定と同じ） */
	private static final long K1 = 0x0f0e0d0c0b0a0908L;

	private SipHash () {
	}

	/**
	 * SipHash-2-4 を計算する
	 *
	 * @param data もとの byte 列
	 * @return ハッシュ
	 */
	static long hash (byte[] data) {

		long v0 = K0 ^ 0x736f6d6570736575L;
		long v1 = K1 ^ 0x646f72616e646f6dL;
		long v2 = K0 ^ 0x6c7967656e657261L;
		long v3 = K1 ^ 0x7465646279746573L;

		int blocks = data.length / 8;

		for (int b = 0; b < blocks; b++) {

			long m = readLong(data, b * 8);

			v3 ^= m;

			// c = 2
			long[] v = {v0, v1, v2, v3};
			round(v);
			round(v);
			v0 = v[0]; v1 = v[1]; v2 = v[2]; v3 = v[3];

			v0 ^= m;

		}

		// 最後の半端 + 長さ（下位 1 byte）
		long last = ((long) data.length) << 56;

		for (int i = blocks * 8; i < data.length; i++) {
			last |= ((long) (data[i] & 0xFF)) << (8 * (i - blocks * 8));
		}

		v3 ^= last;

		long[] v = {v0, v1, v2, v3};
		round(v);
		round(v);
		v0 = v[0]; v1 = v[1]; v2 = v[2]; v3 = v[3];

		v0 ^= last;

		// d = 4
		v2 ^= 0xFF;

		v = new long[]{v0, v1, v2, v3};
		round(v);
		round(v);
		round(v);
		round(v);

		return v[0] ^ v[1] ^ v[2] ^ v[3];

	}

	/**
	 * SipRound を1回まわす
	 *
	 * @param v 状態（4つ）
	 */
	private static void round (long[] v) {

		v[0] += v[1];
		v[1] = Long.rotateLeft(v[1], 13);
		v[1] ^= v[0];
		v[0] = Long.rotateLeft(v[0], 32);

		v[2] += v[3];
		v[3] = Long.rotateLeft(v[3], 16);
		v[3] ^= v[2];

		v[0] += v[3];
		v[3] = Long.rotateLeft(v[3], 21);
		v[3] ^= v[0];

		v[2] += v[1];
		v[1] = Long.rotateLeft(v[1], 17);
		v[1] ^= v[2];
		v[2] = Long.rotateLeft(v[2], 32);

	}

	/**
	 * 8 byte を little endian で読む
	 *
	 * @param data	byte 列
	 * @param at	読み始める位置
	 * @return 読んだ値
	 */
	private static long readLong (byte[] data, int at) {

		long value = 0;

		for (int i = 7; i >= 0; i--) {
			value = (value << 8) | (data[at + i] & 0xFF);
		}

		return value;

	}

}
