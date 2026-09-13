package io.jimble.web.server;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.ReadablePart;
import io.helidon.webserver.http.ServerRequest;
import io.jimble.util.convertor.UploadFile;
import io.jimble.util.log.Log;
import io.jimble.web.http.HttpException;
import io.jimble.web.http.RequestSource;
import io.jimble.web.upload.UploadConf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * helidon のリクエストを包む
 *
 * <p><b>helidon の型を知っているのはこのクラスだけ。</b></p>
 */
final class HelidonRequestSource implements RequestSource {

	/* Cookie ヘッダの名前（中身の区切りが "; " なので、連結もそれに合わせる） */
	private static final String COOKIE_HEADER = "cookie";

	/** multipart のメディアタイプ */
	private static final String MULTIPART = "multipart/form-data";

	/** フォームのメディアタイプ */
	private static final String URL_ENCODED = "application/x-www-form-urlencoded";

	/** 上限超過のステータスコード */
	private static final int TOO_LARGE_STATUS_CODE = 413;

	/* helidon リクエスト */
	private final ServerRequest request;

	/*
	 * multipart は本体を1回しか読めない。
	 * フォーム項目とファイルは同じ本体に混ざっているので、まとめて1回で解析して持っておく。
	 */
	private boolean multipartParsed = false;

	/* multipart のフォーム項目 */
	private final Map<String, List<String>> multipartForm = new LinkedHashMap<>();

	/* multipart のファイル */
	private final List<UploadFile> multipartFiles = new ArrayList<>();

	/**
	 * コンストラクタ
	 *
	 * @param request	helidon リクエスト
	 */
	HelidonRequestSource (ServerRequest request) {

		this.request = request;

	}

	@Override
	public String method () {

		return request.prologue().method().text();

	}

	@Override
	public String rawPath () {

		return request.path().rawPath();

	}

	@Override
	public String path () {

		return request.path().path();

	}

	@Override
	public String url () {

		return request.requestedUri().toUri().toString();

	}

	@Override
	public String protocol () {

		return request.prologue().protocolVersion();

	}

	@Override
	public String scheme () {

		return request.isSecure() ? "https" : "http";

	}

	@Override
	public String host () {

		return request.requestedUri().host();

	}

	@Override
	public int port () {

		return request.requestedUri().port();

	}

	@Override
	public String remoteAddress () {

		try {
			return request.remotePeer().host();
		} catch (Exception ex) {
			return "";
		}

	}

	@Override
	public String query () {

		return request.query().rawValue();

	}

	@Override
	public Map<String, String> headers () {

		Map<String, String> result = new LinkedHashMap<>();

		request.headers().forEach(header -> {

			String name = header.name().toLowerCase();

			/*
			 * 連結の区切りはヘッダで違う（D-173）。
			 *
			 * 全部 ";" で繋いでいたが、HTTP で複数値を1行にまとめる区切りは ", " である（RFC 9110）。
			 * ";" はその中の「パラメータの区切り」——Accept: text/html;q=0.9 の ; である。
			 *
			 * つまり Accept: a, b と Accept: c が2行で来ると、かつては a, b;c になっていた——
			 * c が b のパラメータとして読まれる。例外は出ないし、たいていのリクエストは通る。
			 * 変わるのは選ばれる型だけである。
			 *
			 * Cookie だけは中身の区切りが "; " なので、そちらで繋ぐ。
			 */
			String separator = COOKIE_HEADER.equals(name) ? "; " : ", ";

			result.put(name, String.join(separator, header.allValues()));

		});

		return result;

	}

	@Override
	public Map<String, String> cookies () {

		Map<String, String> result = new LinkedHashMap<>();

		try {
			request.headers().cookies().toMap().forEach((name, values) -> {
				if (!values.isEmpty()) {
					result.put(name, values.getFirst());
				}
			});
		} catch (Exception ignore) {
			// Cookie ヘッダが無い場合
		}

		return result;

	}

	@Override
	public Map<String, List<String>> queryParams () {

		return toMultiMap(request.query());

	}

	@Override
	public Map<String, List<String>> formParams () {

		if (isMultipart()) {
			parseMultipart();
			return multipartForm;
		}

		try {
			if (!isContentType(URL_ENCODED)) {
				return Map.of();
			}
			return toMultiMap(request.content().as(Parameters.class));
		} catch (Exception ignore) {
			return Map.of();
		}

	}

	@Override
	public List<UploadFile> files () {

		if (!isMultipart()) {
			return List.of();
		}

		parseMultipart();

		return multipartFiles;

	}

	@Override
	public void cleanup () {

		for (UploadFile uploadFile : multipartFiles) {
			if (uploadFile.file() == null) {
				continue;
			}
			try {
				Files.deleteIfExists(uploadFile.file().toPath());
			} catch (IOException ex) {
				Log.warn("アップロードの一時ファイルを消せませんでした: " + uploadFile.file());
			}
		}

		multipartFiles.clear();

	}

	// region multipart（要件 F-W-06）

	/**
	 * multipart か
	 *
	 * @return	multipart の場合 = true
	 */
	private boolean isMultipart () {

		return isContentType(MULTIPART);

	}

	/**
	 * Content-Type がこれで始まるか
	 *
	 * @param mediaType	メディアタイプ
	 * @return	一致する場合 = true
	 */
	private boolean isContentType (String mediaType) {

		return request.headers().contentType()
			.map(type -> type.text().startsWith(mediaType))
			.orElse(false);

	}

	/**
	 * multipart を1回だけ解析する
	 *
	 * <p>
	 * <b>本体は1回しか読めない。</b>helidon の {@code MultiPart} は
	 * パートを順番に流すので、次に進む前にその場で読み切る必要がある。
	 * フォーム項目とファイルを一緒に集めるのはこのため。
	 * </p>
	 *
	 * @throws HttpException	上限を超えた場合（413）
	 */
	private void parseMultipart () {

		if (multipartParsed) {
			return;
		}

		multipartParsed = true;

		long maxFileSize = UploadConf.maxFileSize();
		long maxTotalSize = UploadConf.maxTotalSize();
		int maxFiles = UploadConf.maxFiles();

		long totalSize = 0;

		try {

			MultiPart multiPart = request.content().as(MultiPart.class);

			while (multiPart.hasNext()) {

				ReadablePart part = multiPart.next();

				if (part.fileName().isEmpty()) {
					// ファイル名が無いパートはフォーム項目
					multipartForm
						.computeIfAbsent(part.name(), key -> new ArrayList<>())
						.add(new String(part.inputStream().readAllBytes(), StandardCharsets.UTF_8));
					continue;
				}

				if (multipartFiles.size() >= maxFiles) {
					throw new HttpException(TOO_LARGE_STATUS_CODE,
						"アップロードできるファイル数は %d 件までです".formatted(maxFiles));
				}

				long remaining = Math.min(maxFileSize, maxTotalSize - totalSize);
				UploadFile uploadFile = save(part, remaining);

				totalSize += uploadFile.fileSize();
				multipartFiles.add(uploadFile);

			}

		} catch (HttpException ex) {

			// ここまでに作った一時ファイルを残さない
			cleanup();
			throw ex;

		} catch (Exception ex) {

			cleanup();
			throw new HttpException(400, "multipart を読めませんでした", ex);

		}

	}

	/**
	 * パートを一時ファイルに書き出す
	 *
	 * <p>
	 * <b>上限を超えたらその場で打ち切る。</b>全部読んでから大きさを見ると、
	 * 上限を超えた分もディスクに書いてしまう。
	 * </p>
	 *
	 * @param part		パート
	 * @param maxBytes	この1件に許す最大バイト数
	 * @return	アップロードファイル
	 * @throws IOException		書き込みに失敗した場合
	 * @throws HttpException	上限を超えた場合（413）
	 */
	private UploadFile save (ReadablePart part, long maxBytes) throws IOException {

		Path tempFile = Files.createTempFile(UploadConf.tempDir(), UploadConf.TEMP_PREFIX, ".tmp");

		long written = 0;

		try (InputStream in = part.inputStream(); OutputStream out = Files.newOutputStream(tempFile)) {

			byte[] buffer = new byte[8192];
			int read;

			while ((read = in.read(buffer)) > 0) {

				written += read;

				if (written > maxBytes) {
					Files.deleteIfExists(tempFile);
					throw new HttpException(TOO_LARGE_STATUS_CODE,
						"アップロードのサイズが上限（%d バイト）を超えています".formatted(maxBytes));
				}

				out.write(buffer, 0, read);

			}

		}

		return new UploadFile(
			part.name()
			, part.fileName().orElse("")
			, part.contentType() == null ? "" : part.contentType().text()
			, written
			, tempFile.toFile());

	}

	// endregion

	@Override
	public InputStream bodyStream () {

		try {
			return request.content().inputStream();
		} catch (Exception ignore) {
			return InputStream.nullInputStream();
		}

	}

	@Override
	public String bodyText (Charset charset) {

		try {
			return request.content().as(String.class);
		} catch (Exception ignore) {
			return "";
		}

	}

	/**
	 * パラメータを Map に変換する
	 *
	 * @param parameters	パラメータ
	 * @return	Map
	 */
	private static Map<String, List<String>> toMultiMap (Parameters parameters) {

		Map<String, List<String>> result = new LinkedHashMap<>();

		for (String name : parameters.names()) {
			result.put(name, new ArrayList<>(parameters.all(name)));
		}

		return result;

	}

}
