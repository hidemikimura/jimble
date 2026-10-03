package io.jimble.web.auth.passkey;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

/**
 * パスキーの公開鍵（COSE_Key。RFC 9053）を JDK の鍵にする（D-261）
 *
 * <p>受け付けるのは3つだけ。どれも JDK だけで検証できる。</p>
 *
 * <table>
 *   <caption>受け付けるアルゴリズム</caption>
 *   <tr><th>COSE</th><th>名前</th><th>中身</th></tr>
 *   <tr><td>-7</td><td>ES256</td><td>ECDSA P-256 / SHA-256（ほとんどのパスキー）</td></tr>
 *   <tr><td>-8</td><td>EdDSA</td><td>Ed25519</td></tr>
 *   <tr><td>-257</td><td>RS256</td><td>RSASSA-PKCS1-v1_5 / SHA-256（Windows Hello の一部）。2048 ビット以上</td></tr>
 * </table>
 *
 * <p>DB には X.509 の SubjectPublicKeyInfo（{@link PublicKey#getEncoded()}）とアルゴリズムの番号を置く。</p>
 */
final class CoseKey {

	/** ES256 */
	static final int ES256 = -7;

	/** EdDSA（Ed25519） */
	static final int EDDSA = -8;

	/** RS256 */
	static final int RS256 = -257;

	/* Ed25519 の SubjectPublicKeyInfo の頭（RFC 8410） */
	private static final byte[] ED25519_SPKI_PREFIX = {
		0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00 };

	private CoseKey () {
	}

	/**
	 * 鍵とアルゴリズム
	 *
	 * @param key		公開鍵
	 * @param algorithm	COSE のアルゴリズムの番号
	 */
	record Parsed(PublicKey key, int algorithm) {}

	/**
	 * COSE_Key を読む
	 *
	 * @param cose	CBOR を読んだマップ
	 * @return	鍵とアルゴリズム
	 * @throws IllegalArgumentException	受け付けない鍵の場合
	 */
	static Parsed parse (Map<?, ?> cose) {

		long kty = number(cose, 1L, "kty");
		long alg = number(cose, 3L, "alg");

		try {

			if (kty == 2 && alg == ES256) {

				if (number(cose, -1L, "crv") != 1) {
					throw new IllegalArgumentException("P-256 でない EC の鍵は受け付けません");
				}

				byte[] x = bytes(cose, -2L, "x");
				byte[] y = bytes(cose, -3L, "y");

				if (x.length != 32 || y.length != 32) {
					throw new IllegalArgumentException("P-256 の座標の長さが違います");
				}

				return new Parsed(ecKey(new BigInteger(1, x), new BigInteger(1, y)), ES256);

			}

			if (kty == 1 && alg == EDDSA) {

				if (number(cose, -1L, "crv") != 6) {
					throw new IllegalArgumentException("Ed25519 でない OKP の鍵は受け付けません");
				}

				byte[] x = bytes(cose, -2L, "x");

				if (x.length != 32) {
					throw new IllegalArgumentException("Ed25519 の鍵の長さが違います");
				}

				byte[] spki = new byte[ED25519_SPKI_PREFIX.length + 32];
				System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
				System.arraycopy(x, 0, spki, ED25519_SPKI_PREFIX.length, 32);

				return new Parsed(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki)), EDDSA);

			}

			if (kty == 3 && alg == RS256) {

				BigInteger n = new BigInteger(1, bytes(cose, -1L, "n"));
				BigInteger e = new BigInteger(1, bytes(cose, -2L, "e"));

				if (n.bitLength() < 2048) {
					throw new IllegalArgumentException("2048 ビットより短い RSA の鍵は受け付けません");
				}

				return new Parsed(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)), RS256);

			}

		} catch (GeneralSecurityException ex) {
			throw new IllegalArgumentException("パスキーの公開鍵を読めません", ex);
		}

		throw new IllegalArgumentException("受け付けない鍵の形です（kty=%d, alg=%d）".formatted(kty, alg));

	}

	/**
	 * DB に置いた形（SubjectPublicKeyInfo）から戻す
	 *
	 * @param spki		SubjectPublicKeyInfo
	 * @param algorithm	COSE のアルゴリズムの番号
	 * @return	公開鍵
	 */
	static PublicKey restore (byte[] spki, int algorithm) {

		try {
			return KeyFactory.getInstance(keyFactory(algorithm)).generatePublic(new X509EncodedKeySpec(spki));
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("保存したパスキーの公開鍵を読めません", ex);
		}

	}

	/**
	 * 署名を確かめる
	 *
	 * @param key		公開鍵
	 * @param algorithm	COSE のアルゴリズムの番号
	 * @param data		署名したもの
	 * @param signature	署名（ES256 は DER）
	 * @return	合っていれば true
	 */
	static boolean verify (PublicKey key, int algorithm, byte[] data, byte[] signature) {

		try {
			Signature verifier = Signature.getInstance(signatureAlgorithm(algorithm));
			verifier.initVerify(key);
			verifier.update(data);
			return verifier.verify(signature);
		} catch (GeneralSecurityException | IllegalArgumentException ex) {
			// 壊れた署名（DER として読めないなど）は「合わない」
			return false;
		}

	}

	/**
	 * 受け付けるアルゴリズムか
	 *
	 * @param algorithm	COSE のアルゴリズムの番号
	 * @return	受け付けるなら true
	 */
	static boolean supported (long algorithm) {

		return algorithm == ES256 || algorithm == EDDSA || algorithm == RS256;

	}

	private static String signatureAlgorithm (int algorithm) {

		return switch (algorithm) {
			case ES256 -> "SHA256withECDSA";
			case EDDSA -> "Ed25519";
			case RS256 -> "SHA256withRSA";
			default -> throw new IllegalArgumentException("受け付けないアルゴリズムです: " + algorithm);
		};

	}

	private static String keyFactory (int algorithm) {

		return switch (algorithm) {
			case ES256 -> "EC";
			case EDDSA -> "Ed25519";
			case RS256 -> "RSA";
			default -> throw new IllegalArgumentException("受け付けないアルゴリズムです: " + algorithm);
		};

	}

	/**
	 * P-256 の点から公開鍵を作る（曲線の上にあることを確かめる）
	 *
	 * <p>
	 * 曲線の上に無い点を受け入れると、無効な曲線の攻撃の入口になる。JDK に任せず、ここで確かめる。
	 * </p>
	 */
	private static ECPublicKey ecKey (BigInteger x, BigInteger y) throws GeneralSecurityException {

		AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
		parameters.init(new ECGenParameterSpec("secp256r1"));
		ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);

		BigInteger p = ((java.security.spec.ECFieldFp) spec.getCurve().getField()).getP();
		BigInteger a = spec.getCurve().getA();
		BigInteger b = spec.getCurve().getB();

		if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0
			|| !y.modPow(BigInteger.TWO, p).equals(x.pow(3).add(a.multiply(x)).add(b).mod(p))) {
			throw new IllegalArgumentException("P-256 の曲線の上に無い点です");
		}

		return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));

	}

	private static long number (Map<?, ?> cose, Object key, String name) {

		if (!(cose.get(key) instanceof Long value)) {
			throw new IllegalArgumentException("COSE の鍵に %s がありません".formatted(name));
		}

		return value;

	}

	private static byte[] bytes (Map<?, ?> cose, Object key, String name) {

		if (!(cose.get(key) instanceof byte[] value)) {
			throw new IllegalArgumentException("COSE の鍵に %s がありません".formatted(name));
		}

		return value;

	}

}
