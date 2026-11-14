package bench;

import com.dynatrace.hash4j.hashing.Hasher128;
import com.dynatrace.hash4j.hashing.Hasher64;
import com.dynatrace.hash4j.hashing.Hashing;
import io.jimble.util.hash.Hash;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * SQL 結果キャッシュのキーに使うハッシュの比較（1 回あたり）
 *
 * <p>
 * 長さ 800 byte ほどが、主キー 1 行の全列 SELECT のキー（名前空間・データソース・SQL・値）の長さ。
 * XXH3・rapidhash・komihash は hash4j（ベンチだけの依存。jimble は使っていない）。
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "-Xms1g", "-Xmx1g" })
@State(Scope.Thread)
public class HashBench {

	private static final Hasher64 XXH3_64 = Hashing.xxh3_64();

	private static final Hasher128 XXH3_128 = Hashing.xxh3_128();

	private static final Hasher64 RAPIDHASH_V3 = Hashing.rapidhashV3();

	private static final Hasher64 KOMIHASH_5 = Hashing.komihash5_0();

	@Param({ "64", "256", "800", "4000" })
	int length;

	private byte[] data;

	@Setup
	public void setUp () {

		data = new byte[length];
		new Random(1).nextBytes(data);

		// 書き直した SipHash が今のものと同じ値を出すこと（違えば比べる意味が無い）
		if (SipHashRewritten.hash(data) != Hash.sipHashOf(data)) {
			throw new IllegalStateException("書き直した SipHash の値が違う");
		}

	}

	@Benchmark
	public long sipHash () {

		return Hash.sipHashOf(data);

	}

	@Benchmark
	public long sipHashRewritten () {

		return SipHashRewritten.hash(data);

	}

	@Benchmark
	public long xxh3_64 () {

		return XXH3_64.hashBytesToLong(data);

	}

	@Benchmark
	public void xxh3_128 (Blackhole bh) {

		var value = XXH3_128.hashBytesTo128Bits(data);
		bh.consume(value.getMostSignificantBits());
		bh.consume(value.getLeastSignificantBits());

	}

	@Benchmark
	public long rapidhashV3 () {

		return RAPIDHASH_V3.hashBytesToLong(data);

	}

	@Benchmark
	public long komihash5 () {

		return KOMIHASH_5.hashBytesToLong(data);

	}

	/**
	 * 出す値を変えずに書き直した SipHash-2-4（状態をローカル変数に置き、8 byte ずつ読む）
	 */
	static final class SipHashRewritten {

		private static final long K0 = 0x0706050403020100L;
		private static final long K1 = 0x0f0e0d0c0b0a0908L;
		private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

		static long hash (byte[] data) {

			if (data.length == 0) {
				return 0;
			}

			long v0 = K0 ^ 0x736f6d6570736575L;
			long v1 = K1 ^ 0x646f72616e646f6dL;
			long v2 = K0 ^ 0x6c7967656e657261L;
			long v3 = K1 ^ 0x7465646279746573L;
			int blocks = data.length / 8;
			for (int b = 0; b < blocks; b++) {
				long m = (long) LONG_LE.get(data, b * 8);
				v3 ^= m;
				for (int r = 0; r < 2; r++) {
					v0 += v1; v1 = Long.rotateLeft(v1, 13); v1 ^= v0; v0 = Long.rotateLeft(v0, 32);
					v2 += v3; v3 = Long.rotateLeft(v3, 16); v3 ^= v2;
					v0 += v3; v3 = Long.rotateLeft(v3, 21); v3 ^= v0;
					v2 += v1; v1 = Long.rotateLeft(v1, 17); v1 ^= v2; v2 = Long.rotateLeft(v2, 32);
				}
				v0 ^= m;
			}
			long last = ((long) data.length) << 56;
			for (int i = blocks * 8; i < data.length; i++) {
				last |= ((long) (data[i] & 0xFF)) << (8 * (i - blocks * 8));
			}
			v3 ^= last;
			for (int r = 0; r < 2; r++) {
				v0 += v1; v1 = Long.rotateLeft(v1, 13); v1 ^= v0; v0 = Long.rotateLeft(v0, 32);
				v2 += v3; v3 = Long.rotateLeft(v3, 16); v3 ^= v2;
				v0 += v3; v3 = Long.rotateLeft(v3, 21); v3 ^= v0;
				v2 += v1; v1 = Long.rotateLeft(v1, 17); v1 ^= v2; v2 = Long.rotateLeft(v2, 32);
			}
			v0 ^= last;
			v2 ^= 0xFF;
			for (int r = 0; r < 4; r++) {
				v0 += v1; v1 = Long.rotateLeft(v1, 13); v1 ^= v0; v0 = Long.rotateLeft(v0, 32);
				v2 += v3; v3 = Long.rotateLeft(v3, 16); v3 ^= v2;
				v0 += v3; v3 = Long.rotateLeft(v3, 21); v3 ^= v0;
				v2 += v1; v1 = Long.rotateLeft(v1, 17); v1 ^= v2; v2 = Long.rotateLeft(v2, 32);
			}
			return v0 ^ v1 ^ v2 ^ v3;

		}

	}

}
