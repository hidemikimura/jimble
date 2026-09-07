package io.jimble.util.hash;

import io.jimble.util.conf.Conf;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.security.Security;

public class PasswordUtil {

    /* SecureRandomアルゴリズム */
    private static String algorithm = "";
    static {
        boolean nativePRNGNonBlocking = false;
        boolean nativePRNG = false;
        for (String algorithm : Security.getAlgorithms("SecureRandom")) {
            if ("NativePRNGNonBlocking".equalsIgnoreCase(algorithm)) {
                nativePRNGNonBlocking = true;
            }
            if ("NativePRNG".equalsIgnoreCase(algorithm)) {
                nativePRNG = true;
            }
        }
        if (nativePRNGNonBlocking) {
            algorithm = "NativePRNGNonBlocking";
        } else if (nativePRNG) {
            algorithm = "NativePRNG";
        }
    }

    /* ハッシュ化回数 */
    private static final int LOG_ROUNDS = 10;

    /**
     * ペッパー取得
     *
     * @return ペッパー
     */
    private static String getPepper () {

        return Conf.conf().getString("hash.password.pepper");

    }

    /**
     * SecureRandomを作成する
     *
     * @return SecureRandom
     */
    private static SecureRandom createSecureRandom () {

        if (algorithm == null || algorithm.isEmpty()) {
            try {
                return SecureRandom.getInstanceStrong();
            } catch (Exception ex) {
                return new SecureRandom();
            }
        }

        try {
            return SecureRandom.getInstance(algorithm);
        } catch (Exception ex) {
            return new SecureRandom();
        }

    }

    /**
     * ハッシュ生成
     *
     * @param password パスワード
     * @return ハッシュ
     */
    public static String createHash (String password) {

        return CipherUtil.encryptAes(BCrypt.hashpw(password, BCrypt.gensalt(LOG_ROUNDS, createSecureRandom())) + getPepper());

    }

    /**
     * 入力パスワードチェック
     *
     * @param inputPassword 入力パスワード
     * @param passwordHash  パスワードハッシュ
     * @return 一致する場合 = true
     */
    public static boolean check (String inputPassword, String passwordHash) {

        try {
            String hash = CipherUtil.decryptAes(passwordHash);
            hash = hash.substring(0, hash.length() - getPepper().length());
            return BCrypt.checkpw(inputPassword, hash);
        } catch (Exception ex) {
            return false;
        }

    }
}
