package io.jimble.web.server;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerResponse;
import io.jimble.web.http.ResponseSink;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * helidon のレスポンスを包む
 *
 * <p><b>helidon の型を知っているのはこのクラスだけ。</b></p>
 */
final class HelidonResponseSink implements ResponseSink {

	/* helidon レスポンス */
	private final ServerResponse response;

	/*
	 * 出力ストリームを取ったかどうか。
	 *
	 * helidon の isSent() は send(...) 系を呼んだときしか true にならず、
	 * outputStream() で書き出した場合は false のままになる。
	 * これを見ずにいると「まだ何も返していない」と判断されて、
	 * 後段が 204 やエラーを重ねて返そうとし、
	 * 「Cannot set response header after requesting output stream」で落ちる。
	 */
	private boolean streamed = false;

	/**
	 * コンストラクタ
	 *
	 * @param response	helidon レスポンス
	 */
	HelidonResponseSink (ServerResponse response) {

		this.response = response;

	}

	@Override
	public boolean isSent () {

		return streamed || response.isSent();

	}

	@Override
	public void status (int statusCode) {

		response.status(Status.create(statusCode));

	}

	@Override
	public void header (String name, String value) {

		response.header(HeaderNames.create(name), value);

	}

	@Override
	public void addHeader (String name, String value) {

		HeaderName headerName = HeaderNames.create(name);

		List<String> values = new ArrayList<>();
		if (response.headers().contains(headerName)) {
			values.addAll(response.headers().get(headerName).allValues());
		}
		values.add(value);

		response.header(HeaderValues.create(headerName, values));

	}

	@Override
	public void send () {

		response.send();

	}

	@Override
	public void send (String text) {

		response.send(text);

	}

	@Override
	public void send (byte[] body) {

		response.send(body);

	}

	@Override
	public void send (InputStream stream, long contentLength) {

		if (contentLength >= 0) {
			header("Content-Length", contentLength);
		}

		streamed = true;

		try (OutputStream out = response.outputStream()) {
			stream.transferTo(out);
		} catch (Exception ex) {
			throw new IllegalStateException("ストリームの送信に失敗しました", ex);
		}

	}

	@Override
	public void sendFile (Path path, String fileName) {

		String name = (fileName == null || fileName.isEmpty()) ? path.getFileName().toString() : fileName;

		header("Content-Disposition"
			, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8));

		try (InputStream in = Files.newInputStream(path)) {
			send(in, Files.size(path));
		} catch (Exception ex) {
			throw new IllegalStateException("ファイルの送信に失敗しました: " + path, ex);
		}

	}

	@Override
	public void redirect (String url) {

		response.status(Status.FOUND_302);
		header("Location", url);
		response.send();

	}

	@Override
	public OutputStream outputStream () {

		streamed = true;

		return response.outputStream();

	}

}
