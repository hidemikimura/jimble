package io.jimble.util.hash;

import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.security.Security;

/**
 * パスワードのハッシュ
 *
 * <p>
 * BCrypt でハッシュ化する。<b>そのうえで暗号化するかどうかは選べる</b>
 * （{@code hash.password.encrypt}）。ハッシュだけでも安全だが、
 * DB が漏れたときに総当たりの足がかりを与えないよう、
 * さらに鍵で包んでおきたい場合に使う。
 * </p>
 *
 * <pre>
 * hash {
 *   password {
 *     encrypt = true          # 既定は cipher.key があれば true、無ければ false
 *     pepper  = ${?PEPPER}
 *   }
 * }
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>暗号化が必須だった。</b>{@code cipher.key} が無いと
 *       {@link CipherUtil} の static 初期化子で落ち、
 *       <b>1回目と2回目以降で違うメッセージ</b>が出た。選べるようにした</li>
 *   <li><b>失敗が黙って false になっていた。</b>
 *       復号に失敗すると空文字が返り、その後の {@code substring} が落ち、
 *       {@code catch} が握って false を返していた。
 *       <b>鍵が違うのか、パスワードが違うのかが区別できない。</b>
 *       いまは理由をログに出す</li>
 *   <li><b>ペッパーが無いと落ちた。</b>既定値なしで設定を読んでいた。
 *       ペッパーは任意なので、無ければ空として扱う</li>
 * </ol>
 *
 * <h2>ペッパーの入れ方について（移送元のまま）</h2>
 * <p>
 * このクラスは <b>{@code BCrypt.hashpw(password) + pepper}</b> という形、
 * つまり<b>ハッシュした後ろにペッパーを繋げている</b>。
 * 一般にペッパーは<b>ハッシュする前の入力に混ぜる</b>ものなので、
 * この形では総当たりを難しくする効果がない（照合時に外して捨てているだけ）。
 * </p>
 * <p>
 * <b>直していない。</b>直すと、保存済みのハッシュが全部照合できなくなる。
 * 変えるなら、移行の段取り（両方で試して片方に寄せる）とセットで行うこと。
 * </p>
 */
public final class PasswordUtil {

	/** 暗号化するか */
	public static final String KEY_ENCRYPT = "hash.password.encrypt";

	/** ペッパー */
	public static final String KEY_PEPPER = "hash.password.pepper";

	/* ハッシュ化回数 */
	private static final int LOG_ROUNDS = 10;

	/* SecureRandom のアルゴリズム */
	private static final String ALGORITHM = detectAlgorithm();

	private PasswordUtil () {}

	/**
	 * 暗号化するか
	 *
	 * <p>
	 * <b>既定は「鍵が設定されていれば true」。</b>
	 * 明示したいときは {@code hash.password.encrypt} を書く。
	 * </p>
	 *
	 * <p>
	 * 既定を固定の false にしていないのは、<b>移送してきたアプリが黙って壊れる</b>
	 * からである。保存済みのハッシュは暗号化されているので、
	 * 鍵を設定しているのに平文の BCrypt として照合すると、全員ログインできなくなる。
	 * </p>
	 *
	 * @return	暗号化する場合 = true
	 */
	public static boolean isEncrypt () {

		return Conf.conf().getBoolean(KEY_ENCRYPT, CipherUtil.isConfigured());

	}

	/**
	 * ハッシュを作る
	 *
	 * @param password	パスワード
	 * @return	ハッシュ
	 */
	public static String createHash (String password) {

		return createHash(password, isEncrypt());

	}

	/**
	 * ハッシュを作る（暗号化するかを指定する）
	 *
	 * @param password	パスワード
	 * @param encrypt	暗号化する場合 = true
	 * @return	ハッシュ
	 */
	public static String createHash (String password, boolean encrypt) {

		String hash = BCrypt.hashpw(password, BCrypt.gensalt(LOG_ROUNDS, createSecureRandom())) + pepper();

		return encrypt ? CipherUtil.encryptAes(hash) : hash;

	}

	/**
	 * 入力されたパスワードを照合する
	 *
	 * @param inputPassword	入力されたパスワード
	 * @param passwordHash	保存してあるハッシュ
	 * @return	一致する場合 = true
	 */
	public static boolean check (String inputPassword, String passwordHash) {

		return check(inputPassword, passwordHash, isEncrypt());

	}

	/**
	 * 入力されたパスワードを照合する（暗号化されているかを指定する）
	 *
	 * @param inputPassword	入力されたパスワード
	 * @param passwordHash	保存してあるハッシュ
	 * @param encrypted		暗号化されている場合 = true
	 * @return	一致する場合 = true
	 */
	public static boolean check (String inputPassword, String passwordHash, boolean encrypted) {

		if (inputPassword == null || passwordHash == null || passwordHash.isEmpty()) {
			return false;
		}

		String hash;

		if (encrypted) {

			try {

				hash = CipherUtil.decryptAes(passwordHash);

			} catch (RuntimeException ex) {

				/*
				 * ここで false を返すだけだと「パスワードが違う」と区別がつかない。
				 * 鍵が変わったのか、暗号化していないハッシュを暗号化前提で読んだのかを言う。
				 */
				Log.error(ex, "パスワードハッシュを復号できませんでした。"
					+ "鍵（%s）が変わったか、%s の設定が保存時と食い違っています"
						.formatted(CipherUtil.KEY_CIPHER_KEY, KEY_ENCRYPT));

				return false;

			}

		} else {

			hash = passwordHash;

		}

		String pepper = pepper();

		if (hash.length() < pepper.length()) {
			Log.error("パスワードハッシュが短すぎます（%s の設定が保存時と食い違っている可能性があります）"
				.formatted(KEY_PEPPER));
			return false;
		}

		try {

			return BCrypt.checkpw(inputPassword, hash.substring(0, hash.length() - pepper.length()));

		} catch (IllegalArgumentException ex) {

			// BCrypt の形式ではない
			Log.error(ex, "パスワードハッシュが BCrypt の形式ではありません（%s の設定を確認してください）"
				.formatted(KEY_ENCRYPT));

			return false;

		}

	}

	// region 中身

	/**
	 * ペッパー
	 *
	 * @return	ペッパー（未設定なら空）
	 */
	private static String pepper () {

		return Conf.conf().getString(KEY_PEPPER, "");

	}

	/**
	 * SecureRandom のアルゴリズムを選ぶ
	 *
	 * @return	アルゴリズム（見つからなければ空）
	 */
	private static String detectAlgorithm () {

		boolean nonBlocking = false;
		boolean native_ = false;

		for (String algorithm : Security.getAlgorithms("SecureRandom")) {

			if ("NativePRNGNonBlocking".equalsIgnoreCase(algorithm)) {
				nonBlocking = true;
			}

			if ("NativePRNG".equalsIgnoreCase(algorithm)) {
				native_ = true;
			}

		}

		if (nonBlocking) {
			return "NativePRNGNonBlocking";
		}

		return native_ ? "NativePRNG" : "";

	}

	/**
	 * SecureRandom を作る
	 *
	 * @return	SecureRandom
	 */
	private static SecureRandom createSecureRandom () {

		if (ALGORITHM.isEmpty()) {

			try {
				return SecureRandom.getInstanceStrong();
			} catch (Exception ex) {
				return new SecureRandom();
			}

		}

		try {
			return SecureRandom.getInstance(ALGORITHM);
		} catch (Exception ex) {
			return new SecureRandom();
		}

	}

	// endregion

}
