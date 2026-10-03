package io.jimble.web.auth.passkey;

import io.jimble.util.data.Data;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * テスト用の認証器（ブラウザと認証器の代わりに、WebAuthn の応答を組み立てて署名する）
 */
final class FakeAuthenticator {

	static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

	/* 鍵 */
	final KeyPair keys;

	/* COSE のアルゴリズムの番号 */
	final int algorithm;

	/* 資格情報の ID */
	final byte[] credentialId;

	/* 署名の回数（0 なら同期するパスキーのように、いつも 0 を返す） */
	long signCount;

	/* 覚えている利用者のハンドル（base64url） */
	String userHandle;

	FakeAuthenticator (int algorithm, long signCount) throws Exception {

		this.algorithm = algorithm;
		this.signCount = signCount;
		this.credentialId = new byte[16];
		new java.security.SecureRandom().nextBytes(credentialId);

		KeyPairGenerator generator = switch (algorithm) {
			case CoseKey.ES256 -> {
				KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
				g.initialize(new ECGenParameterSpec("secp256r1"));
				yield g;
			}
			case CoseKey.EDDSA -> KeyPairGenerator.getInstance("Ed25519");
			default -> {
				KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
				g.initialize(2048);
				yield g;
			}
		};

		this.keys = generator.generateKeyPair();

	}

	// region 登録

	/**
	 * navigator.credentials.create の結果（toJSON の形）
	 */
	Data create (String challenge, String origin, String rpId, int flags) throws Exception {

		return create(challenge, origin, rpId, flags, "webauthn.create", coseKey());

	}

	Data create (String challenge, String origin, String rpId, int flags, String type, Map<Object, Object> coseKey) throws Exception {

		byte[] clientData = clientData(type, challenge, origin);

		ByteArrayOutputStream auth = new ByteArrayOutputStream();
		auth.write(Passkey.sha256(rpId.getBytes(StandardCharsets.UTF_8)));
		auth.write(flags | 0x40);
		auth.write(counter());
		auth.write(new byte[16]);                                    // AAGUID
		auth.write(credentialId.length >> 8);
		auth.write(credentialId.length & 0xff);
		auth.write(credentialId);
		auth.write(encode(coseKey));

		Map<Object, Object> attestation = new LinkedHashMap<>();
		attestation.put("fmt", "none");
		attestation.put("attStmt", new LinkedHashMap<>());
		attestation.put("authData", auth.toByteArray());

		return new Data()
			.putData("id", B64.encodeToString(credentialId))
			.putData("rawId", B64.encodeToString(credentialId))
			.putData("type", "public-key")
			.putData("response", new Data()
				.putData("clientDataJSON", B64.encodeToString(clientData))
				.putData("attestationObject", B64.encodeToString(encode(attestation))));

	}

	/**
	 * 公開鍵（COSE_Key）
	 */
	Map<Object, Object> coseKey () {

		Map<Object, Object> cose = new LinkedHashMap<>();

		switch (algorithm) {
			case CoseKey.ES256 -> {
				ECPublicKey ec = (ECPublicKey) keys.getPublic();
				cose.put(1L, 2L);
				cose.put(3L, (long) CoseKey.ES256);
				cose.put(-1L, 1L);
				cose.put(-2L, fixed(ec.getW().getAffineX(), 32));
				cose.put(-3L, fixed(ec.getW().getAffineY(), 32));
			}
			case CoseKey.EDDSA -> {
				byte[] spki = keys.getPublic().getEncoded();
				cose.put(1L, 1L);
				cose.put(3L, (long) CoseKey.EDDSA);
				cose.put(-1L, 6L);
				cose.put(-2L, java.util.Arrays.copyOfRange(spki, spki.length - 32, spki.length));
			}
			default -> {
				RSAPublicKey rsa = (RSAPublicKey) keys.getPublic();
				cose.put(1L, 3L);
				cose.put(3L, (long) CoseKey.RS256);
				cose.put(-1L, unsigned(rsa.getModulus()));
				cose.put(-2L, unsigned(rsa.getPublicExponent()));
			}
		}

		return cose;

	}

	// endregion

	// region ログイン

	/**
	 * navigator.credentials.get の結果（toJSON の形）
	 */
	Data get (String challenge, String origin, String rpId, int flags) throws Exception {

		return get(challenge, origin, rpId, flags, keys.getPrivate());

	}

	Data get (String challenge, String origin, String rpId, int flags, PrivateKey signer) throws Exception {

		if (signCount > 0) {
			signCount++;
		}

		byte[] clientData = clientData("webauthn.get", challenge, origin);

		ByteArrayOutputStream auth = new ByteArrayOutputStream();
		auth.write(Passkey.sha256(rpId.getBytes(StandardCharsets.UTF_8)));
		auth.write(flags);
		auth.write(counter());
		byte[] authData = auth.toByteArray();

		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		signed.write(authData);
		signed.write(Passkey.sha256(clientData));

		Signature signature = Signature.getInstance(switch (algorithm) {
			case CoseKey.ES256 -> "SHA256withECDSA";
			case CoseKey.EDDSA -> "Ed25519";
			default -> "SHA256withRSA";
		});
		signature.initSign(signer);
		signature.update(signed.toByteArray());

		return new Data()
			.putData("id", B64.encodeToString(credentialId))
			.putData("rawId", B64.encodeToString(credentialId))
			.putData("type", "public-key")
			.putData("response", new Data()
				.putData("clientDataJSON", B64.encodeToString(clientData))
				.putData("authenticatorData", B64.encodeToString(authData))
				.putData("signature", B64.encodeToString(signature.sign()))
				.putData("userHandle", userHandle));

	}

	// endregion

	// region 小物

	static byte[] clientData (String type, String challenge, String origin) {

		return ("{\"type\":\"%s\",\"challenge\":\"%s\",\"origin\":\"%s\",\"crossOrigin\":false}"
			.formatted(type, challenge, origin)).getBytes(StandardCharsets.UTF_8);

	}

	private byte[] counter () {

		return new byte[] { (byte) (signCount >>> 24), (byte) (signCount >>> 16), (byte) (signCount >>> 8), (byte) signCount };

	}

	private static byte[] fixed (BigInteger value, int length) {

		byte[] raw = value.toByteArray();
		byte[] out = new byte[length];
		int copy = Math.min(raw.length, length);
		System.arraycopy(raw, raw.length - copy, out, length - copy, copy);
		return out;

	}

	private static byte[] unsigned (BigInteger value) {

		byte[] raw = value.toByteArray();
		return raw[0] == 0 ? java.util.Arrays.copyOfRange(raw, 1, raw.length) : raw;

	}

	/**
	 * CBOR に書く（テストに要るぶんだけ）
	 */
	static byte[] encode (Object value) {

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		write(out, value);
		return out.toByteArray();

	}

	private static void write (ByteArrayOutputStream out, Object value) {

		switch (value) {
			case Long n when n >= 0 -> head(out, 0, n);
			case Long n -> head(out, 1, -1 - n);
			case Integer n -> write(out, (long) n);
			case byte[] bytes -> {
				head(out, 2, bytes.length);
				out.writeBytes(bytes);
			}
			case String text -> {
				byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
				head(out, 3, bytes.length);
				out.writeBytes(bytes);
			}
			case List<?> list -> {
				head(out, 4, list.size());
				list.forEach(item -> write(out, item));
			}
			case Map<?, ?> map -> {
				head(out, 5, map.size());
				map.forEach((k, v) -> {
					write(out, k);
					write(out, v);
				});
			}
			case Boolean b -> out.write(b ? 0xf5 : 0xf4);
			default -> throw new IllegalArgumentException("書けない値: " + value);
		}

	}

	private static void head (ByteArrayOutputStream out, int major, long length) {

		int m = major << 5;

		if (length < 24) {
			out.write(m | (int) length);
		} else if (length < 0x100) {
			out.write(m | 24);
			out.write((int) length);
		} else if (length < 0x10000) {
			out.write(m | 25);
			out.write((int) (length >> 8));
			out.write((int) length & 0xff);
		} else {
			out.write(m | 26);
			for (int shift = 24; shift >= 0; shift -= 8) {
				out.write((int) (length >> shift) & 0xff);
			}
		}

	}

	// endregion

}
