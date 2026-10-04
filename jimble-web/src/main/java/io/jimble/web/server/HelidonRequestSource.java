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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
	public Map<String, List<String>> headerValues () {

		Map<String, List<String>> result = new LinkedHashMap<>();

		request.headers().forEach(header ->
			result.put(header.name().toLowerCase(), List.copyOf(header.allValues())));

		return result;

	}

	@Override
	public Map<String, List<String>> cookieValues () {

		Map<String, List<String>> result = new LinkedHashMap<>();

		try {
			request.headers().cookies().toMap().forEach((name, values) ->
				result.put(name, List.copyOf(values)));
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
				Disposition disposition = disposition(part);

				if (disposition.fileName() == null) {
					// ファイル名が無いパートはフォーム項目
					multipartForm
						.computeIfAbsent(disposition.name(), key -> new ArrayList<>())
						.add(new String(part.inputStream().readAllBytes(), StandardCharsets.UTF_8));
					continue;
				}

				if (disposition.fileName().isEmpty()) {
					// ファイルを選んでいないファイル入力（filename=""）。捨てる。フォーム項目にも入れない
					part.inputStream().readAllBytes();
					continue;
				}

				if (multipartFiles.size() >= maxFiles) {
					throw new HttpException(TOO_LARGE_STATUS_CODE,
						"アップロードできるファイル数は %d 件までです".formatted(maxFiles));
				}

				long remaining = Math.min(maxFileSize, maxTotalSize - totalSize);
				UploadFile uploadFile = save(part, disposition, remaining);

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
	 * @param part			パート
	 * @param disposition	Content-Disposition から読んだ名前
	 * @param maxBytes		この1件に許す最大バイト数
	 * @return	アップロードファイル
	 * @throws IOException		書き込みに失敗した場合
	 * @throws HttpException	上限を超えた場合（413）
	 */
	private UploadFile save (ReadablePart part, Disposition disposition, long maxBytes) throws IOException {

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

		String path = disposition.fileName();

		return new UploadFile(
			disposition.name()
			, path.substring(path.lastIndexOf('/') + 1)
			, part.contentType() == null ? "" : part.contentType().text()
			, written
			, tempFile.toFile()
			, path);

	}

	// region Content-Disposition（D-266）

	/**
	 * パートの名前とファイル名
	 *
	 * @param name		項目名
	 * @param fileName	ファイル名（フォルダを含む。区切りは /）。ファイルでないパートは null、ファイルを選んでいない入力は空文字
	 */
	record Disposition(String name, String fileName) {}

	/**
	 * パートの Content-Disposition を読む
	 *
	 * <p>
	 * <b>helidon の {@code ReadablePart.name()} / {@code fileName()} は使わない</b>。ブラウザが送る名前を、次のとおり壊すため。
	 * </p>
	 * <ol>
	 *   <li>ヘッダは ISO-8859-1 で読まれる。ブラウザは UTF-8 のまま送るので、日本語は化け、
	 *       ファイル名は「制御文字を含む」として例外になる（{@code 請求書.pdf} が送れない。項目名の日本語も化ける）</li>
	 *   <li>URL デコードする。{@code a+b.png} が {@code a b.png} に、{@code 100%.txt} は例外になる</li>
	 *   <li>引用符の中の {@code ;} で切る（{@code a;b.txt} が {@code "a} になる）。helidon の {@code ContentDisposition.parse} も同じ</li>
	 * </ol>
	 *
	 * @param part	パート
	 * @return	名前とファイル名
	 * @throws HttpException	ファイル名が正しくない場合（400）
	 */
	static Disposition disposition (ReadablePart part) {

		if (!part.partHeaders().contains(HeaderNames.CONTENT_DISPOSITION)) {
			return new Disposition(part.name(), null);
		}

		return disposition(part.partHeaders().get(HeaderNames.CONTENT_DISPOSITION).get());

	}

	/**
	 * Content-Disposition の値を読む
	 *
	 * @param header	ヘッダの値（ISO-8859-1 で読まれたもの）
	 * @return	名前とファイル名
	 * @throws HttpException	ファイル名が正しくない場合（400）
	 */
	static Disposition disposition (String header) {

		Map<String, String> parameters = parameters(decodeUtf8(header));

		String name = unescapeHtmlForm(parameters.getOrDefault("name", ""));
		String fileName = parameters.containsKey("filename*")
			? extendedValue(parameters.get("filename*"))
			: parameters.containsKey("filename") ? unescapeHtmlForm(parameters.get("filename")) : null;

		if (fileName == null) {
			return new Disposition(name, null);
		}

		if (fileName.isEmpty()) {
			return new Disposition(name, "");
		}

		return new Disposition(name, normalizePath(fileName));

	}

	/**
	 * パラメータを読む（{@code form-data; name="x"; filename="a;b.txt"}。名前は小文字）
	 *
	 * <p>
	 * 引用符の中の {@code ;} と {@code =} は区切りにしない。<b>バックスラッシュはエスケープとして扱わない</b>
	 * ——ブラウザは {@code "} を {@code %22} にして送り、バックスラッシュはそのまま送る（古い IE の {@code C:\...}）。
	 * </p>
	 *
	 * @param value	ヘッダの値
	 * @return	パラメータ
	 */
	static Map<String, String> parameters (String value) {

		Map<String, String> parameters = new LinkedHashMap<>();
		int i = value.indexOf(';');

		while (i >= 0 && i < value.length()) {

			// ; を飛ばして名前を読む
			int start = i + 1;
			int equals = value.indexOf('=', start);
			int nextSemicolon = value.indexOf(';', start);

			if (equals < 0 || (nextSemicolon >= 0 && nextSemicolon < equals)) {
				i = nextSemicolon;
				continue;
			}

			String key = value.substring(start, equals).trim().toLowerCase(Locale.ROOT);
			int cursor = equals + 1;

			while (cursor < value.length() && value.charAt(cursor) == ' ') {
				cursor++;
			}

			String parameterValue;

			if (cursor < value.length() && value.charAt(cursor) == '"') {
				int close = value.indexOf('"', cursor + 1);
				close = close < 0 ? value.length() : close;
				parameterValue = value.substring(cursor + 1, close);
				i = value.indexOf(';', Math.min(close + 1, value.length()));
			} else {
				int end = value.indexOf(';', cursor);
				parameterValue = value.substring(cursor, end < 0 ? value.length() : end).trim();
				i = end;
			}

			parameters.putIfAbsent(key, parameterValue);

		}

		return parameters;

	}

	/**
	 * ブラウザが名前に掛けるエスケープを戻す（HTML の multipart/form-data。{@code "} → {@code %22}）
	 *
	 * <p>
	 * <b>{@code %22} だけを戻す</b>。{@code 100%.txt} の {@code %} はそのまま。改行（{@code %0D} / {@code %0A}）は
	 * ファイル名に入れさせたくないので戻さない。
	 * </p>
	 */
	private static String unescapeHtmlForm (String value) {

		return value.replace("%22", "\"");

	}

	/**
	 * RFC 5987 の値（{@code UTF-8''%E8%AB%8B...}）を読む（ブラウザ以外のクライアントが送る）
	 */
	private static String extendedValue (String value) {

		int first = value.indexOf('\'');
		int second = first < 0 ? -1 : value.indexOf('\'', first + 1);

		if (second < 0) {
			throw new HttpException(400, "ファイル名（filename*）の形が違います");
		}

		Charset charset;

		try {
			charset = Charset.forName(value.substring(0, first));
		} catch (RuntimeException ex) {
			throw new HttpException(400, "ファイル名（filename*）の文字コードが分かりません");
		}

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		String encoded = value.substring(second + 1);

		for (int i = 0; i < encoded.length(); i++) {
			char c = encoded.charAt(i);
			if (c == '%' && i + 2 < encoded.length()) {
				try {
					bytes.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
				} catch (NumberFormatException ex) {
					throw new HttpException(400, "ファイル名（filename*）の形が違います");
				}
				i += 2;
			} else if (c == '%') {
				throw new HttpException(400, "ファイル名（filename*）の形が違います");
			} else {
				bytes.write(c);
			}
		}

		return bytes.toString(charset);

	}

	/**
	 * ファイル名を確かめて、区切りを / にそろえる
	 *
	 * <p>
	 * フォルダを含むもの（{@code sub/b.txt}）はそのまま持つ（{@link UploadFile#relativePath()}）。
	 * 古い IE が送るフルパス（{@code C:\Users\a.png}）は、ドライブを外す。
	 * {@code .} / {@code ..}・空の段・制御文字は断る。
	 * </p>
	 *
	 * @param fileName	ファイル名
	 * @return	確かめたもの
	 * @throws HttpException	正しくない場合（400）
	 */
	private static String normalizePath (String fileName) {

		String path = fileName.replace('\\', '/');

		// 古い IE のフルパス（C:/...）と、頭の /（絶対パス）を外す
		if (path.length() >= 2 && path.charAt(1) == ':' && Character.isLetter(path.charAt(0))) {
			path = path.substring(path.lastIndexOf('/') + 1);
		}

		if (path.startsWith("/")) {
			path = path.substring(path.lastIndexOf('/') + 1);
		}

		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (Character.isISOControl(c) || isSpoofing(c)) {
				throw new HttpException(400, "ファイル名に使えない文字が入っています");
			}
		}

		for (String segment : path.split("/", -1)) {
			if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
				throw new HttpException(400, "ファイル名が正しくありません");
			}
			/*
			 * <b>ドライブ指定（C:evil.jsp）を断る</b>（D-276）。上の「古い IE のフルパス」は C:/... の形しか外さないので、
			 * / を含まない C:evil.jsp が fileName() に残った。Windows で uploadDir.resolve(fileName()) とすると、
			 * <b>置き場所の外（C ドライブのいまのフォルダ）に書けた</b>
			 */
			if (segment.length() >= 2 && segment.charAt(1) == ':' && isAsciiLetter(segment.charAt(0))) {
				throw new HttpException(400, "ファイル名が正しくありません");
			}
		}

		return path;

	}

	/**
	 * 見た目を偽れる文字か（D-276）
	 *
	 * <p>
	 * 文字の向きを変える制御文字（{@code invoice\u202Efdp.exe} が {@code invoiceexe.pdf} に見える）と、
	 * 行・段落の区切り。ゼロ幅接合子（U+200D）は絵文字の組み合わせに使うので通す。
	 * </p>
	 */
	private static boolean isSpoofing (char c) {

		return (c >= '\u202A' && c <= '\u202E')
			|| (c >= '\u2066' && c <= '\u2069')
			|| c == '\u200E' || c == '\u200F' || c == '\u061C'
			|| c == '\u2028' || c == '\u2029';

	}

	private static boolean isAsciiLetter (char c) {

		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');

	}

	/**
	 * ISO-8859-1 で読まれたヘッダの値を UTF-8 として読み直す
	 *
	 * <p>
	 * <b>UTF-8 として読めないものは、読まれたまま返す</b>（Latin-1 で送ってきた {@code café} の {@code é} を
	 * 置換文字に化けさせないため）。0xFF を超える文字があれば、もう文字として読めている。
	 * </p>
	 */
	static String decodeUtf8 (String raw) {

		if (raw.chars().anyMatch(c -> c > 0xFF)) {
			return raw;
		}

		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(raw.getBytes(StandardCharsets.ISO_8859_1)))
				.toString();
		} catch (CharacterCodingException ex) {
			return raw;
		}

	}

	// endregion

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
