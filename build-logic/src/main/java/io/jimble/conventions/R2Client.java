package io.jimble.conventions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Cloudflare R2 のバケットに、S3 の API でファイルを置く・読む
 *
 * <p>
 * 使うのは PUT / GET / HEAD の3つだけ。HTTP は JDK の HttpClient（D-24 と同じ）。
 * 公開の URL（repo.jimble.io）は CDN を通るので、<b>「もう上がっているか」「いまの版の一覧」は
 * こちら（API）で見る</b>。CDN の古い答えで判断しない。
 * </p>
 */
final class R2Client {

	/** R2 のリージョン */
	static final String REGION = "auto";

	/* 失敗したときに試す回数（5xx・429（流量の制限）・通信の失敗だけ） */
	private static final int ATTEMPTS = 3;

	/* 流量の制限（これも待ってやり直す） */
	private static final int TOO_MANY_REQUESTS = 429;

	/* 1回の要求を待つ時間 */
	private static final Duration TIMEOUT = Duration.ofMinutes(2);

	/* x-amz-date の形 */
	private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

	/* HTTP */
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

	/* 署名 */
	private final S3Signer signer;

	/* API の入口（https://<アカウント>.r2.cloudflarestorage.com） */
	private final String endpoint;

	/* バケット */
	private final String bucket;

	/**
	 * @param accountId			Cloudflare のアカウント ID
	 * @param bucket			バケット
	 * @param accessKeyId		R2 の API トークンの Access Key ID
	 * @param secretAccessKey	R2 の API トークンの Secret Access Key
	 */
	R2Client (String accountId, String bucket, String accessKeyId, String secretAccessKey) {

		this("https://" + accountId + ".r2.cloudflarestorage.com", bucket, accessKeyId, secretAccessKey, REGION);

	}

	/**
	 * 入口を指定する（S3 互換のほかの置き場で確かめるため。MinIO など）
	 *
	 * @param endpoint			API の入口（末尾の / なし）
	 * @param bucket			バケット
	 * @param accessKeyId		Access Key ID
	 * @param secretAccessKey	Secret Access Key
	 * @param region			リージョン
	 */
	R2Client (String endpoint, String bucket, String accessKeyId, String secretAccessKey, String region) {

		this.signer = new S3Signer(accessKeyId, secretAccessKey, region, "s3");
		this.endpoint = endpoint;
		this.bucket = bucket;

	}

	/**
	 * 置いてあるものの ETag（1回の PUT で置いたものは中身の MD5 の16進）
	 *
	 * @param key	バケットの中のパス
	 * @return ETag（小文字・引用符なし。無ければ空）
	 */
	Optional<String> etag (String key) {

		HttpResponse<byte[]> response = send("HEAD", key, new byte[0], Map.of());

		return switch (response.statusCode()) {
			case 200 -> Optional.of(response.headers().firstValue("ETag").orElse("").replace("\"", "").toLowerCase(Locale.ROOT));
			case 404 -> Optional.empty();
			default -> throw failure("HEAD", key, response);
		};

	}

	/**
	 * 中身を文字列で読む
	 *
	 * @param key	バケットの中のパス
	 * @return 中身（無ければ空）
	 */
	Optional<String> getText (String key) {

		HttpResponse<byte[]> response = send("GET", key, new byte[0], Map.of());

		return switch (response.statusCode()) {
			case 200 -> Optional.of(new String(response.body(), StandardCharsets.UTF_8));
			case 404 -> Optional.empty();
			default -> throw failure("GET", key, response);
		};

	}

	/**
	 * 置く（同じパスにあれば上書きする）
	 *
	 * @param key			バケットの中のパス
	 * @param body			中身
	 * @param contentType	Content-Type
	 * @param cacheControl	Cache-Control（公開の URL で返る）
	 */
	void put (String key, byte[] body, String contentType, String cacheControl) {

		HttpResponse<byte[]> response = send("PUT", key, body, Map.of("Content-Type", contentType, "Cache-Control", cacheControl));

		if (response.statusCode() != 200) {
			throw failure("PUT", key, response);
		}

	}

	private HttpResponse<byte[]> send (String method, String key, byte[] body, Map<String, String> unsignedHeaders) {

		IOException last = null;

		for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {

			try {

				HttpResponse<byte[]> response = http.send(request(method, key, body, unsignedHeaders), HttpResponse.BodyHandlers.ofByteArray());

				if ((response.statusCode() < 500 && response.statusCode() != TOO_MANY_REQUESTS) || attempt == ATTEMPTS) {
					return response;
				}

			} catch (IOException ex) {
				last = ex;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("止められました: " + method + " " + key, ex);
			}

			sleep(attempt);

		}

		throw new IllegalStateException(method + " " + key + " が " + ATTEMPTS + " 回とも失敗しました: " + last.getMessage(), last);

	}

	/* 署名は送るたびに作り直す（時刻が入るので、やり直しで古い署名を使わない） */
	private HttpRequest request (String method, String key, byte[] body, Map<String, String> unsignedHeaders) {

		String path = S3Signer.encodePath("/" + bucket + "/" + key);
		URI uri = URI.create(endpoint + path);

		String amzDate = ZonedDateTime.now(ZoneOffset.UTC).format(AMZ_DATE);
		String payloadHash = S3Signer.sha256Hex(body);

		Map<String, String> signed = new TreeMap<>();
		// HttpClient が送る Host と同じにする（既定でないポートは付く）
		signed.put("host", uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort());
		signed.put("x-amz-content-sha256", payloadHash);
		signed.put("x-amz-date", amzDate);

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(TIMEOUT)
			.header("x-amz-content-sha256", payloadHash)
			.header("x-amz-date", amzDate)
			.header("Authorization", signer.authorization(method, path, signed, payloadHash, amzDate));

		unsignedHeaders.forEach(builder::header);

		return "PUT".equals(method)
			? builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build()
			: builder.method(method, HttpRequest.BodyPublishers.noBody()).build();

	}

	/* 中身は R2 のエラー（XML）。署名も鍵も入っていないのでそのまま出す */
	private static IllegalStateException failure (String method, String key, HttpResponse<byte[]> response) {

		String detail = new String(response.body(), StandardCharsets.UTF_8);

		String hint = response.statusCode() == 403
			? "\nAPI トークンがこのバケットに「Object Read & Write」を持っているか、アカウント ID とバケット名が合っているかを確かめてください"
			: "";

		return new IllegalStateException("R2 が " + response.statusCode() + " を返しました: " + method + " " + key
			+ (detail.isBlank() ? "" : "\n" + detail) + hint);

	}

	private static void sleep (int attempt) {

		try {
			Thread.sleep(2000L * attempt);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("止められました", ex);
		}

	}

}
