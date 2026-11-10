package io.jimble.util.hash;

import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import io.jimble.util.conf.Conf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id のパスワードハッシュ（{@link PasswordUtil} から使う）
 *
 * <p>
 * 形は PHC の文字列（{@code $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>}）で、
 * ほかの実装（libargon2・argon2-cffi・Spring Security など）が作ったものとそのまま照合できる。
 * 計算は Password4j に任せ、<b>文字列を読むことと比べることはここで行う</b>
 * （保存されている値の大きさを確かめてから計算し、比べるのは {@link MessageDigest#isEqual} で時間を揃える）。
 * </p>
 *
 * <p>
 * 既定は OWASP の Password Storage Cheat Sheet の最小の推奨（メモリ 19 MiB・2 回・並列 1）。
 * ソルトは 16 バイト、ハッシュは 32 バイト（RFC 9106 の推奨）。
 * </p>
 */
final class Argon2Hash {

	/** ハッシュの頭 */
	static final String PREFIX = "$argon2id$";

	/** メモリの既定（KiB。19 MiB） */
	static final int DEFAULT_MEMORY_KIB = 19 * 1024;

	/** 繰り返しの既定 */
	static final int DEFAULT_ITERATIONS = 2;

	/** 並列の既定 */
	static final int DEFAULT_PARALLELISM = 1;

	/**
	 * メモリの上限（KiB。1 GiB）
	 *
	 * <p>保存されている値もこれで確かめる。壊れた値（{@code m=4194304} など）を読んで、プロセスのメモリを使い切らないため。</p>
	 */
	static final int MAX_MEMORY_KIB = 1024 * 1024;

	/** 繰り返しの上限（保存されている値の確かめにも使う） */
	static final int MAX_ITERATIONS = 100;

	/** 並列の上限（保存されている値の確かめにも使う） */
	static final int MAX_PARALLELISM = 64;

	/** ソルトの長さ（バイト） */
	static final int SALT_BYTES = 16;

	/** ハッシュの長さ（バイト） */
	static final int HASH_BYTES = 32;

	/** 照合するハッシュの長さの範囲（バイト。ほかの実装が作ったものも読む） */
	static final int MIN_HASH_BYTES = 16;
	static final int MAX_HASH_BYTES = 64;

	/** Argon2 の版（1.3） */
	private static final int VERSION = 19;

	/* PHC の文字列を $ で分けたときの数（空・名前・版・パラメータ・ソルト・ハッシュ） */
	private static final int PHC_PARTS = 6;

	/* ソルト・ハッシュの base64（PHC は = を付けない） */
	private static final Base64.Encoder B64 = Base64.getEncoder().withoutPadding();
	private static final Base64.Decoder B64_DECODER = Base64.getDecoder();

	private Argon2Hash () {}

	/**
	 * パラメータ
	 *
	 * @param memoryKib		メモリ（KiB）
	 * @param iterations	繰り返し
	 * @param parallelism	並列
	 */
	record Params (int memoryKib, int iterations, int parallelism) {}

	/**
	 * 読んだハッシュ
	 *
	 * @param params	パラメータ
	 * @param salt		ソルト
	 * @param hash		ハッシュ
	 */
	record Parsed (Params params, byte[] salt, byte[] hash) {}

	/**
	 * 設定のパラメータ（{@code hash.password.argon2.*}）
	 *
	 * @return	パラメータ
	 * @throws IllegalStateException	範囲の外の場合
	 */
	static Params configured () {

		long memoryBytes = Conf.conf().getBytes(PasswordUtil.KEY_ARGON2_MEMORY, DEFAULT_MEMORY_KIB * 1024L);
		int iterations = Conf.conf().getInt(PasswordUtil.KEY_ARGON2_ITERATIONS, DEFAULT_ITERATIONS);
		int parallelism = Conf.conf().getInt(PasswordUtil.KEY_ARGON2_PARALLELISM, DEFAULT_PARALLELISM);

		if (parallelism < 1 || parallelism > MAX_PARALLELISM) {
			throw new IllegalStateException("%s は 1〜%d にしてください（既定 %d）。いまの値: %d"
				.formatted(PasswordUtil.KEY_ARGON2_PARALLELISM, MAX_PARALLELISM, DEFAULT_PARALLELISM, parallelism));
		}

		if (iterations < 1 || iterations > MAX_ITERATIONS) {
			throw new IllegalStateException("%s は 1〜%d にしてください（既定 %d）。いまの値: %d"
				.formatted(PasswordUtil.KEY_ARGON2_ITERATIONS, MAX_ITERATIONS, DEFAULT_ITERATIONS, iterations));
		}

		long memoryKib = memoryBytes / 1024;

		// Argon2 は並列 1 つあたり 8 KiB が要る（RFC 9106 の 3.1）
		if (memoryKib < 8L * parallelism || memoryKib > MAX_MEMORY_KIB) {
			throw new IllegalStateException("%s は %d KiB〜1 GiB にしてください（既定 19MiB）。いまの値: %d バイト"
				.formatted(PasswordUtil.KEY_ARGON2_MEMORY, 8 * parallelism, memoryBytes));
		}

		return new Params((int) memoryKib, iterations, parallelism);

	}

	/**
	 * Argon2id のハッシュか（頭だけを見る）
	 *
	 * @param hash	ハッシュ
	 * @return	Argon2id の場合 = true
	 */
	static boolean isArgon2id (String hash) {

		return hash != null && hash.startsWith(PREFIX);

	}

	/**
	 * ハッシュを作る
	 *
	 * @param password	パスワード
	 * @param params	パラメータ
	 * @param random	ソルトを作る乱数
	 * @return	PHC の文字列
	 */
	static String create (String password, Params params, SecureRandom random) {

		byte[] salt = new byte[SALT_BYTES];
		random.nextBytes(salt);

		byte[] hash = derive(password, params, salt, HASH_BYTES);

		return "%sv=%d$m=%d,t=%d,p=%d$%s$%s".formatted(PREFIX, VERSION
			, params.memoryKib(), params.iterations(), params.parallelism()
			, B64.encodeToString(salt), B64.encodeToString(hash));

	}

	/**
	 * 照合する
	 *
	 * @param password	入力されたパスワード
	 * @param hash		保存してあるハッシュ（PHC の文字列）
	 * @return	一致する場合 = true
	 * @throws IllegalArgumentException	Argon2id の形でない・値が範囲の外の場合
	 */
	static boolean matches (String password, String hash) {

		Parsed parsed = parse(hash);

		byte[] computed = derive(password, parsed.params(), parsed.salt(), parsed.hash().length);

		return MessageDigest.isEqual(computed, parsed.hash());

	}

	/**
	 * PHC の文字列を読む
	 *
	 * @param hash	ハッシュ
	 * @return	読んだもの
	 * @throws IllegalArgumentException	Argon2id の形でない・値が範囲の外の場合
	 */
	static Parsed parse (String hash) {

		if (!isArgon2id(hash)) {
			throw new IllegalArgumentException("Argon2id のハッシュではありません");
		}

		String[] parts = hash.split("\\$", -1);

		if (parts.length != PHC_PARTS) {
			throw new IllegalArgumentException("Argon2id のハッシュの区切りが違います");
		}

		if (!("v=" + VERSION).equals(parts[2])) {
			throw new IllegalArgumentException("Argon2 の版が 1.3（v=19）ではありません: " + parts[2]);
		}

		Params params = parseParams(parts[3]);

		byte[] salt;
		byte[] value;

		try {
			salt = B64_DECODER.decode(parts[4]);
			value = B64_DECODER.decode(parts[5]);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Argon2id のソルトかハッシュが base64 ではありません", ex);
		}

		if (salt.length < 8) {
			throw new IllegalArgumentException("Argon2id のソルトが短すぎます: " + salt.length + " バイト");
		}

		if (value.length < MIN_HASH_BYTES || value.length > MAX_HASH_BYTES) {
			throw new IllegalArgumentException("Argon2id のハッシュの長さが範囲の外です: " + value.length + " バイト");
		}

		return new Parsed(params, salt, value);

	}

	/**
	 * {@code m=...,t=...,p=...} を読む（順は PHC の決まりどおり）
	 */
	private static Params parseParams (String text) {

		String[] values = text.split(",", -1);

		if (values.length != 3 || !values[0].startsWith("m=") || !values[1].startsWith("t=") || !values[2].startsWith("p=")) {
			throw new IllegalArgumentException("Argon2id のパラメータの形が違います: " + text);
		}

		int memory = number(values[0], MAX_MEMORY_KIB);
		int iterations = number(values[1], MAX_ITERATIONS);
		int parallelism = number(values[2], MAX_PARALLELISM);

		if (memory < 8 * parallelism) {
			throw new IllegalArgumentException("Argon2id のメモリが並列の数に足りません: " + text);
		}

		return new Params(memory, iterations, parallelism);

	}

	private static int number (String pair, int max) {

		int value;

		try {
			value = Integer.parseInt(pair.substring(2));
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Argon2id のパラメータが数ではありません: " + pair, ex);
		}

		if (value < 1 || value > max) {
			throw new IllegalArgumentException("Argon2id のパラメータが範囲の外です（1〜%d）: %s".formatted(max, pair));
		}

		return value;

	}

	/**
	 * 計算する（パスワードは UTF-8 のバイトにする）
	 */
	private static byte[] derive (String password, Params params, byte[] salt, int length) {

		Argon2Function function = Argon2Function.getInstance(params.memoryKib(), params.iterations(), params.parallelism()
			, length, Argon2.ID, VERSION);

		return function.hash(password.getBytes(StandardCharsets.UTF_8), salt).getBytes();

	}

}
