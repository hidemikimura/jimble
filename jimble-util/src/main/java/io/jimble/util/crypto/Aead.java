package io.jimble.util.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 暗号化（改ざん検知つき）
 *
 * <p>
 * AES-256-GCM。<b>暗号化と改ざん検知を同時にやる</b>ので、これ1つで
 * 「署名 + 暗号化」（要件 F-S-08）を満たす。
 * </p>
 *
 * <h2>既存の {@code CipherUtil} を使わない理由</h2>
 * <p>
 * {@code CipherUtil} は AES/CBC/PKCS5Padding を<b>固定 IV</b> で使っている。
 * </p>
 * <ol>
 *   <li><b>IV を使い回すと、同じ平文が必ず同じ暗号文になる。</b>
 *       Cookie に載せると「同じ値かどうか」が外から分かる</li>
 *   <li><b>CBC には改ざん検知がない。</b>暗号文を書き換えても復号は通ってしまう</li>
 *   <li>復号に失敗したとき空文字を返すので、<b>失敗と「空の値」が区別できない</b></li>
 * </ol>
 * <p>
 * ここでは値ごとにランダムな nonce を作り、GCM の認証タグで改ざんを検知し、
 * 失敗したら null を返す。
 * </p>
 *
 * <p>
 * 出力は {@code Base64URL(nonce || 暗号文 || タグ)}。
 * </p>
 */
public final class Aead {

	/** 変換方式 */
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	/** 鍵のアルゴリズム */
	private static final String KEY_ALGORITHM = "AES";

	/** nonce の長さ（GCM の推奨値） */
	private static final int NONCE_LENGTH = 12;

	/** 認証タグの長さ（ビット） */
	private static final int TAG_LENGTH_BITS = 128;

	/** 鍵の長さ（バイト。AES-256） */
	public static final int KEY_LENGTH = 32;

	/* 乱数生成器 */
	private static final SecureRandom RANDOM = new SecureRandom();

	private Aead () {}

	/**
	 * 暗号化する
	 *
	 * @param plainText	平文
	 * @param secret	鍵（{@link #toKey(String)} で 32 バイトにする）
	 * @return	Base64URL 文字列
	 */
	public static String encrypt (String plainText, String secret) {

		if (plainText == null) {
			return null;
		}

		try {

			byte[] nonce = new byte[NONCE_LENGTH];
			RANDOM.nextBytes(nonce);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(
				Cipher.ENCRYPT_MODE
				, new SecretKeySpec(toKey(secret), KEY_ALGORITHM)
				, new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
			);

			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			byte[] output = ByteBuffer.allocate(nonce.length + encrypted.length)
				.put(nonce)
				.put(encrypted)
				.array();

			return Base64.getUrlEncoder().withoutPadding().encodeToString(output);

		} catch (Exception ex) {

			// 設定ミス（鍵がない等）。黙って平文を通すより落とす
			throw new IllegalStateException("暗号化できませんでした", ex);

		}

	}

	/**
	 * 復号する
	 *
	 * <p><b>改ざんされていたら null を返す。</b>空文字ではない。</p>
	 *
	 * @param cipherText	暗号文（Base64URL）
	 * @param secret		鍵
	 * @return	平文（復号できなければ null）
	 */
	public static String decrypt (String cipherText, String secret) {

		if (cipherText == null || cipherText.isEmpty()) {
			return null;
		}

		try {

			byte[] input = Base64.getUrlDecoder().decode(cipherText);
			if (input.length <= NONCE_LENGTH) {
				return null;
			}

			byte[] nonce = new byte[NONCE_LENGTH];
			System.arraycopy(input, 0, nonce, 0, NONCE_LENGTH);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(
				Cipher.DECRYPT_MODE
				, new SecretKeySpec(toKey(secret), KEY_ALGORITHM)
				, new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
			);

			byte[] decrypted = cipher.doFinal(input, NONCE_LENGTH, input.length - NONCE_LENGTH);

			return new String(decrypted, StandardCharsets.UTF_8);

		} catch (Exception ex) {

			// 改ざん・鍵違い・壊れた入力。どれも「読めない」でよい
			return null;

		}

	}

	/**
	 * 鍵文字列を 32 バイトの鍵にする
	 *
	 * <p>
	 * 設定に書く鍵の長さを利用者に強制しないための正規化。
	 * SHA-256 を通すだけで、鍵の強さは元の文字列の質で決まる。
	 * </p>
	 *
	 * @param secret	鍵文字列
	 * @return	32 バイトの鍵
	 * @throws IllegalStateException	鍵が空の場合
	 */
	private static byte[] toKey (String secret) {

		if (secret == null || secret.isEmpty()) {
			throw new IllegalStateException("暗号鍵が設定されていません");
		}

		try {
			return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ex) {
			throw new IllegalStateException("鍵を作れませんでした", ex);
		}

	}

}
