package io.jimble.util.hash;

import io.jimble.util.conf.Conf;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 共通鍵暗号（AES/CBC/PKCS5Padding。固定 IV）
 *
 * <p>
 * <b>新しく暗号化するものにこれを使わないこと。</b>
 * IV が固定なので、同じ平文が必ず同じ暗号文になる（内容の同一性が漏れる）。
 * 改ざん検知も無い。新しく作るものは {@link io.jimble.util.crypto.Aead}（AES-256-GCM）を使う。
 * </p>
 *
 * <p>
 * ここに残してあるのは、<b>移送してきたアプリが既に保存している暗号文を読むため</b>である
 * （{@link PasswordUtil} が保存済みのパスワードハッシュを復号するのに使う）。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>鍵を static フィールドで読んでいた。</b>
 *       設定が無いと static 初期化子の中で例外になり、
 *       <b>1回目は {@code ExceptionInInitializerError}（メッセージが null）、
 *       2回目以降は {@code Could not initialize class ...}</b> になる。
 *       同じ原因なのに違うメッセージが出て、どちらも何をすればよいか分からない
 *       （要件 F-X-05）。<b>呼ばれたときに読む</b>ようにした</li>
 *   <li><b>失敗を黙って握りつぶしていた。</b>
 *       {@code encryptAes} は {@code printStackTrace()} して<b>空文字を返し</b>、
 *       {@code decryptAes} も空文字を返していた。
 *       暗号化に失敗したのに空文字が保存される、という壊れ方をする。
 *       <b>例外を投げる</b>ようにした</li>
 *   <li><b>鍵の長さを見ていなかった。</b>
 *       AES の鍵は 16 / 24 / 32 バイトでなければならない。
 *       違うと {@code InvalidKeyException} が出るが、上の2番で握りつぶされていた</li>
 * </ol>
 */
public final class CipherUtil {

	/** 鍵 */
	public static final String KEY_CIPHER_KEY = "cipher.key";

	/** 初期化ベクトル */
	public static final String KEY_CIPHER_IV = "cipher.iv";

	/** AES が受け付ける鍵の長さ（バイト） */
	private static final int[] KEY_LENGTHS = { 16, 24, 32 };

	/** IV の長さ（バイト） */
	private static final int IV_LENGTH = 16;

	private CipherUtil () {}

	/**
	 * 鍵が設定されているか
	 *
	 * <p>
	 * <b>読むだけで、無くても落ちない。</b>
	 * 暗号化を使うかどうかを設定から決めたい側（{@link PasswordUtil}）が見る。
	 * </p>
	 *
	 * @return	設定されていれば true
	 */
	public static boolean isConfigured () {

		return !Conf.conf().getString(KEY_CIPHER_KEY, "").isEmpty()
			&& !Conf.conf().getString(KEY_CIPHER_IV, "").isEmpty();

	}

	/**
	 * 暗号化する
	 *
	 * @param src	文字列
	 * @return	暗号化した文字列（Base64）
	 * @throws IllegalStateException	鍵が無い、または鍵の長さが違う場合
	 * @throws IllegalArgumentException	暗号化に失敗した場合
	 */
	public static String encryptAes (String src) {

		try {

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key(), iv());

			return Base64.getEncoder()
				.encodeToString(cipher.doFinal(src.getBytes(StandardCharsets.UTF_8)));

		} catch (IllegalStateException ex) {

			throw ex;

		} catch (Exception ex) {

			// 黙って空文字を返さない。返すと、空のハッシュが保存される
			throw new IllegalArgumentException("暗号化に失敗しました", ex);

		}

	}

	/**
	 * 復号する
	 *
	 * @param src	暗号化した文字列（Base64）
	 * @return	復号した文字列
	 * @throws IllegalStateException	鍵が無い、または鍵の長さが違う場合
	 * @throws IllegalArgumentException	復号に失敗した場合（鍵が違う／暗号文でない）
	 */
	public static String decryptAes (String src) {

		try {

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key(), iv());

			return new String(
				cipher.doFinal(Base64.getDecoder().decode(src)), StandardCharsets.UTF_8);

		} catch (IllegalStateException ex) {

			throw ex;

		} catch (Exception ex) {

			throw new IllegalArgumentException(
				"復号に失敗しました。鍵（%s）が変わっているか、暗号文ではありません"
					.formatted(KEY_CIPHER_KEY), ex);

		}

	}

	// region 鍵

	/**
	 * 鍵
	 *
	 * @return	鍵
	 */
	private static SecretKeySpec key () {

		byte[] bytes = read(KEY_CIPHER_KEY);

		for (int length : KEY_LENGTHS) {
			if (bytes.length == length) {
				return new SecretKeySpec(bytes, "AES");
			}
		}

		throw new IllegalStateException(
			"%s の長さが %d バイトです。AES の鍵は 16 / 24 / 32 バイトでなければなりません"
				.formatted(KEY_CIPHER_KEY, bytes.length));

	}

	/**
	 * 初期化ベクトル
	 *
	 * @return	初期化ベクトル
	 */
	private static IvParameterSpec iv () {

		byte[] bytes = read(KEY_CIPHER_IV);

		if (bytes.length != IV_LENGTH) {
			throw new IllegalStateException(
				"%s の長さが %d バイトです。%d バイトでなければなりません"
					.formatted(KEY_CIPHER_IV, bytes.length, IV_LENGTH));
		}

		return new IvParameterSpec(bytes);

	}

	/**
	 * 設定を読む
	 *
	 * @param key	設定のキー
	 * @return	値
	 */
	private static byte[] read (String key) {

		String value = Conf.conf().getString(key, "");

		if (value.isEmpty()) {
			throw new IllegalStateException(
				"""
				%s が設定されていません。
				  application.conf に次を足すか、環境変数で渡してください。
				    cipher { %s = ${?CIPHER_%s} }
				""".formatted(key, key.substring(key.indexOf('.') + 1)
					, key.substring(key.indexOf('.') + 1).toUpperCase()));
		}

		return value.getBytes(StandardCharsets.UTF_8);

	}

	// endregion

}
