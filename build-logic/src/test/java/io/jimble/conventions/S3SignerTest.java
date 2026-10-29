package io.jimble.conventions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 署名（AWS Signature Version 4）を、AWS が公開している例と突き合わせる
 */
class S3SignerTest {

	/** 空の本文の SHA-256 */
	private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

	@Test
	@DisplayName("AWS の検証用の例（get-vanilla）と同じ署名になる")
	void getVanilla () {

		S3Signer signer = new S3Signer("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "us-east-1", "service");

		Map<String, String> headers = new TreeMap<>();
		headers.put("host", "example.amazonaws.com");
		headers.put("x-amz-date", "20150830T123600Z");

		String authorization = signer.authorization("GET", "/", headers, EMPTY_SHA256, "20150830T123600Z");

		assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
			+ "SignedHeaders=host;x-amz-date, "
			+ "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31", authorization);

	}

	@Test
	@DisplayName("S3 の例（GET Object）と同じ署名になる")
	void s3GetObject () {

		S3Signer signer = new S3Signer("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", "s3");

		Map<String, String> headers = new TreeMap<>();
		headers.put("host", "examplebucket.s3.amazonaws.com");
		headers.put("range", "bytes=0-9");
		headers.put("x-amz-content-sha256", EMPTY_SHA256);
		headers.put("x-amz-date", "20130524T000000Z");

		String authorization = signer.authorization("GET", "/test.txt", headers, EMPTY_SHA256, "20130524T000000Z");

		assertEquals("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, "
			+ "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, "
			+ "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", authorization);

	}

	@Test
	@DisplayName("パスは区切りごとに RFC 3986 で符号化し、/ はそのまま残す")
	void encodesPath () {

		assertEquals("/jimble-repo/io/jimble/a%20b/x%2By.jar", S3Signer.encodePath("/jimble-repo/io/jimble/a b/x+y.jar"));
		assertEquals("/io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar", S3Signer.encodePath("/io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar"));

	}

	@Test
	@DisplayName("SHA-256 を16進で返す")
	void sha256Hex () {

		assertEquals(EMPTY_SHA256, S3Signer.sha256Hex(new byte[0]));
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			S3Signer.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));

	}

}
