package io.jimble.util.hash;

import io.jimble.util.conf.Conf;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CipherUtil {

    private static final byte[] KEY = Conf.conf().getString("cipher.key").getBytes(StandardCharsets.UTF_8);

    private static final byte[] IV = Conf.conf().getString("cipher.iv").getBytes(StandardCharsets.UTF_8);

    /**
     * 暗号化
     *
     * @param src 文字列
     * @return 暗号化文字列
     */
    public static String encryptAes (String src) {

        try {

            SecretKeySpec key = new SecretKeySpec(KEY, "AES");
            IvParameterSpec iv = new IvParameterSpec(IV);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);

            return new String(Base64.getEncoder().encode(cipher.doFinal(src.getBytes())));

        } catch (Exception ex) {

            ex.printStackTrace();
            return "";

        }
    }

    /**
     * 復号化
     *
     * @param src 暗号化文字列
     * @return 文字列
     */
    public static String decryptAes (String src) {

        try {

            SecretKeySpec key = new SecretKeySpec(KEY, "AES");
            IvParameterSpec iv = new IvParameterSpec(IV);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);

            return new String(cipher.doFinal(Base64.getDecoder().decode(src)));

        } catch (Exception ex) {

            return "";

        }

    }
}
