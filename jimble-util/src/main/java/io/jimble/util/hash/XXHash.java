package io.jimble.util.hash;

/**
 * XXHash
 */
public class XXHash {

	private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
	private static final long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
	private static final long PRIME64_3 = 0x165667B19E3779F9L;
	private static final long PRIME64_4 = 0x85EBCA77C2B2AE63L;
	private static final long PRIME64_5 = 0x27D4EB2F165667C5L;

	private static final int PRIME1 = 0x9E3779B1;
	private static final int PRIME2 = 0x85EBCA77;
	private static final int PRIME3 = 0xC2B2AE3D;
	private static final int PRIME4 = 0x27D4EB2F;
	private static final int PRIME5 = 0x165667B1;

	public static int hash32 (byte[] data, int seed) {
		int end = data.length;
		int offset = 0;
		int h32;
		if (data.length >= 16) {
			int limit = end - 16;
			int v1 = seed + PRIME1 + PRIME2;
			int v2 = seed + PRIME2;
			int v3 = seed;
			int v4 = seed - PRIME1;

			do {
				v1 += getInt(data, offset) * PRIME2;
				v1 = Integer.rotateLeft(v1, 13);
				v1 *= PRIME1;
				offset += 4;
				v2 += getInt(data, offset) * PRIME2;
				v2 = Integer.rotateLeft(v2, 13);
				v2 *= PRIME1;
				offset += 4;
				v3 += getInt(data, offset) * PRIME2;
				v3 = Integer.rotateLeft(v3, 13);
				v3 *= PRIME1;
				offset += 4;
				v4 += getInt(data, offset) * PRIME2;
				v4 = Integer.rotateLeft(v4, 13);
				v4 *= PRIME1;
				offset += 4;
			} while(offset <= limit);

			h32 = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7) + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
		} else {
			h32 = seed + PRIME5;
		}

		for(h32 += data.length; offset <= end - 4; offset += 4) {
			h32 += getInt(data, offset) * PRIME3;
			h32 = Integer.rotateLeft(h32, 17) * PRIME4;
		}

		while(offset < end) {
			h32 += (data[offset] & 255) * PRIME5;
			h32 = Integer.rotateLeft(h32, 11) * PRIME1;
			++offset;
		}

		h32 ^= h32 >>> 15;
		h32 *= PRIME2;
		h32 ^= h32 >>> 13;
		h32 *= PRIME3;
		h32 ^= h32 >>> 16;
		return h32;
	}

	public static long hash64 (byte[] input, long seed) {
		long hash;
		long remaining = input.length;
		int offset = 0;

		if (remaining >= 32) {
			long v1 = seed + PRIME64_1 + PRIME64_2;
			long v2 = seed + PRIME64_2;
			long v3 = seed;
			long v4 = seed - PRIME64_1;

			do {
				v1 += getLong(input, offset) * PRIME64_2;
				v1 = Long.rotateLeft(v1, 31);
				v1 *= PRIME64_1;

				v2 += getLong(input, offset + 8) * PRIME64_2;
				v2 = Long.rotateLeft(v2, 31);
				v2 *= PRIME64_1;

				v3 += getLong(input, offset + 16) * PRIME64_2;
				v3 = Long.rotateLeft(v3, 31);
				v3 *= PRIME64_1;

				v4 += getLong(input, offset + 24) * PRIME64_2;
				v4 = Long.rotateLeft(v4, 31);
				v4 *= PRIME64_1;

				offset += 32;
				remaining -= 32;
			} while (remaining >= 32);

			hash = Long.rotateLeft(v1, 1)
				+ Long.rotateLeft(v2, 7)
				+ Long.rotateLeft(v3, 12)
				+ Long.rotateLeft(v4, 18);

			v1 *= PRIME64_2;
			v1 = Long.rotateLeft(v1, 31);
			v1 *= PRIME64_1;
			hash ^= v1;
			hash = hash * PRIME64_1 + PRIME64_4;

			v2 *= PRIME64_2;
			v2 = Long.rotateLeft(v2, 31);
			v2 *= PRIME64_1;
			hash ^= v2;
			hash = hash * PRIME64_1 + PRIME64_4;

			v3 *= PRIME64_2;
			v3 = Long.rotateLeft(v3, 31);
			v3 *= PRIME64_1;
			hash ^= v3;
			hash = hash * PRIME64_1 + PRIME64_4;

			v4 *= PRIME64_2;
			v4 = Long.rotateLeft(v4, 31);
			v4 *= PRIME64_1;
			hash ^= v4;
			hash = hash * PRIME64_1 + PRIME64_4;
		} else {
			hash = seed + PRIME64_5;
		}

		hash += input.length;

		while (remaining >= 8) {
			long k1 = getLong(input, offset);
			k1 *= PRIME64_2;
			k1 = Long.rotateLeft(k1, 31);
			k1 *= PRIME64_1;
			hash ^= k1;
			hash = Long.rotateLeft(hash, 27) * PRIME64_1 + PRIME64_4;
			offset += 8;
			remaining -= 8;
		}

		if (remaining >= 4) {
			hash ^= getInt(input, offset) * PRIME64_1;
			hash = Long.rotateLeft(hash, 23) * PRIME64_2 + PRIME64_3;
			offset += 4;
			remaining -= 4;
		}

		while (remaining != 0) {
			hash ^= input[offset] * PRIME64_5;
			hash = Long.rotateLeft(hash, 11) * PRIME64_1;
			--remaining;
			++offset;
		}

		hash ^= hash >>> 33;
		hash *= PRIME64_2;
		hash ^= hash >>> 29;
		hash *= PRIME64_3;
		hash ^= hash >>> 32;
		return hash;
	}

	private static long getLong (byte[] array, int offset) {
		return (array[offset] & 0xFFL)
			| (array[offset + 1] & 0xFFL) << 8
			| (array[offset + 2] & 0xFFL) << 16
			| (array[offset + 3] & 0xFFL) << 24
			| (array[offset + 4] & 0xFFL) << 32
			| (array[offset + 5] & 0xFFL) << 40
			| (array[offset + 6] & 0xFFL) << 48
			| (array[offset + 7] & 0xFFL) << 56;
	}

	private static int getInt (byte[] bytes, int pos) {
		return (bytes[pos] & 0xFF)
			| ((bytes[pos + 1] & 0xFF) << 8)
			| ((bytes[pos + 2] & 0xFF) << 16)
			| ((bytes[pos + 3] & 0xFF) << 24);
	}

}
