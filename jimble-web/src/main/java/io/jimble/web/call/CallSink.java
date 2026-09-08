package io.jimble.web.call;

import io.jimble.util.io.FileUtil;
import io.jimble.web.http.ResponseSink;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部呼び出しの出力口
 *
 * <p>
 * <b>送らずに受け止める。</b>ハンドラから見ると普通に送信しているが、
 * 中身はここに溜まって {@link CallResponse} になる。
 * </p>
 *
 * <p>
 * ファイル・ストリームも本文として読み切る。
 * 大きなファイルを返す API を内部から呼ぶとそのぶんメモリに載るので、
 * <b>内部呼び出しに向くのは JSON を返す API である。</b>
 * </p>
 */
final class CallSink implements ResponseSink {

	/* ステータスコード */
	private int statusCode = 200;

	/* 送信済みか */
	private boolean sent = false;

	/* ヘッダ */
	private final Map<String, String> headers = new LinkedHashMap<>();

	/* 足したヘッダ */
	private final Map<String, List<String>> addedHeaders = new LinkedHashMap<>();

	/* 本文 */
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	@Override
	public boolean isSent () {

		return sent;

	}

	@Override
	public void status (int statusCode) {

		this.statusCode = statusCode;

	}

	@Override
	public void header (String name, String value) {

		headers.put(name, value);

	}

	@Override
	public void addHeader (String name, String value) {

		addedHeaders.computeIfAbsent(name, key -> new ArrayList<>()).add(value);

	}

	@Override
	public void send () {

		sent = true;

	}

	@Override
	public void send (String text) {

		write(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));

	}

	@Override
	public void send (byte[] body) {

		write(body);

	}

	@Override
	public void send (InputStream stream, long contentLength) {

		try {
			write(stream.readAllBytes());
		} catch (Exception cause) {
			throw new IllegalStateException("内部呼び出しの本文を読めませんでした", cause);
		}

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>Content-Type と Content-Disposition も入れる。</b>
	 * 入れないと、受け取った側は本文が何なのか分からないまま
	 * 文字として読もうとする。
	 * </p>
	 */
	@Override
	public void sendFile (Path path, String fileName) {

		try {

			headers.putIfAbsent("Content-Type", FileUtil.getFileContentType(path.toFile()));
			headers.putIfAbsent("Content-Disposition", "attachment; filename=\"%s\""
				.formatted(fileName == null ? path.getFileName().toString() : fileName));

			write(Files.readAllBytes(path));

		} catch (Exception cause) {
			throw new IllegalStateException("内部呼び出しでファイルを読めませんでした: " + path, cause);
		}

	}

	@Override
	public void redirect (String url) {

		this.statusCode = 302;
		headers.put("Location", url);
		this.sent = true;

	}

	@Override
	public OutputStream outputStream () {

		sent = true;

		return buffer;

	}

	/**
	 * 本文を書く
	 *
	 * @param body	本文
	 */
	private void write (byte[] body) {

		if (sent) {
			throw new IllegalStateException("内部呼び出しのレスポンスはすでに送信されています");
		}

		buffer.writeBytes(body);
		sent = true;

	}

	/**
	 * 結果にする
	 *
	 * @return	結果
	 */
	CallResponse toResponse () {

		Map<String, List<String>> all = new LinkedHashMap<>();

		for (Map.Entry<String, String> entry : headers.entrySet()) {
			all.put(entry.getKey(), List.of(entry.getValue()));
		}

		/*
		 * 同じ名前で複数返すヘッダ（Set-Cookie）は<b>並びのまま持つ。</b>
		 * "," で1本に繋ぐと、受け取った側では元に戻せない。
		 */
		for (Map.Entry<String, List<String>> entry : addedHeaders.entrySet()) {
			all.merge(entry.getKey(), List.copyOf(entry.getValue()), (a, b) -> {
				List<String> merged = new ArrayList<>(a);
				merged.addAll(b);
				return List.copyOf(merged);
			});
		}

		return new CallResponse(statusCode, all, buffer.toByteArray());

	}

}
