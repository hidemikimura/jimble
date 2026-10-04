package io.jimble.web.server;

import io.helidon.http.Headers;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.ContentEncodingContextConfig;

import java.util.Locale;

/**
 * 圧縮するものを選ぶ（D-279）
 *
 * <p>
 * <b>helidon はリクエストの Accept-Encoding だけを見て、どの応答も圧縮する。</b>
 * 画像・動画・zip・ダウンロードのファイルも圧縮し直していた（縮まないのに CPU を使い、
 * 5MB の画像で 100ms ほど。Content-Length も消えて chunked になる）。50 バイトの JSON でも圧縮器を作っていた。
 * </p>
 *
 * <p>
 * 送る側（{@link HelidonResponseSink}）が、応答の種類と長さから決めて {@link #ENCODE} に置く。
 * ここはそれを見て、圧縮しないなら何もしない圧縮器を返す。置かれていなければ helidon のまま。
 * </p>
 */
final class SelectiveContentEncoding implements ContentEncodingContext {

	/** この応答を圧縮してよいか（送る側が、送るあいだだけ置く） */
	static final ScopedValue<Boolean> ENCODE = ScopedValue.newInstance();

	/** これより短いものは圧縮しない（バイト） */
	static final long MIN_LENGTH = 1024;

	/* helidon の既定 */
	private final ContentEncodingContext delegate;

	private SelectiveContentEncoding (ContentEncodingContext delegate) {

		this.delegate = delegate;

	}

	/**
	 * 作る
	 *
	 * @param enabled	圧縮するか（{@code server.compression}）
	 * @return	作ったもの
	 */
	static SelectiveContentEncoding create (boolean enabled) {

		return new SelectiveContentEncoding(ContentEncodingContext.create(
			builder -> builder.contentEncodingsDiscoverServices(enabled)));

	}

	/**
	 * 圧縮して縮むものか
	 *
	 * <p>
	 * 文字のもの（{@code text/*}・JSON・JavaScript・XML・SVG）で、長さが分からないか {@value #MIN_LENGTH} バイト以上。
	 * SSE（{@code text/event-stream}）は圧縮しない（溜めてから送ることになり、届くのが遅れる）。
	 * </p>
	 *
	 * @param contentType	Content-Type（無ければ null）
	 * @param length		長さ（分からなければ負）
	 * @return	圧縮する場合 = true
	 */
	static boolean compressible (String contentType, long length) {

		if (contentType == null || contentType.isEmpty()) {
			return false;
		}

		if (length >= 0 && length < MIN_LENGTH) {
			return false;
		}

		String type = contentType.toLowerCase(Locale.ROOT);
		int semicolon = type.indexOf(';');
		if (semicolon >= 0) {
			type = type.substring(0, semicolon);
		}
		type = type.trim();

		if (type.equals("text/event-stream")) {
			return false;
		}

		return type.startsWith("text/")
			|| type.equals("application/json") || type.endsWith("+json")
			|| type.equals("application/javascript") || type.equals("application/x-javascript")
			|| type.equals("application/xml") || type.endsWith("+xml")
			|| type.equals("application/x-ndjson");

	}

	@Override
	public ContentEncoder encoder (Headers headers) {

		if (ENCODE.isBound() && !ENCODE.get()) {
			return ContentEncoder.NO_OP;
		}

		return delegate.encoder(headers);

	}

	@Override
	public boolean contentEncodingEnabled () {

		return delegate.contentEncodingEnabled();

	}

	@Override
	public boolean contentDecodingEnabled () {

		return delegate.contentDecodingEnabled();

	}

	@Override
	public boolean contentEncodingSupported (String encodingId) {

		return delegate.contentEncodingSupported(encodingId);

	}

	@Override
	public boolean contentDecodingSupported (String encodingId) {

		return delegate.contentDecodingSupported(encodingId);

	}

	@Override
	public ContentEncoder encoder (String encodingId) {

		return delegate.encoder(encodingId);

	}

	@Override
	public ContentDecoder decoder (String encodingId) {

		return delegate.decoder(encodingId);

	}

	@Override
	public ContentEncodingContextConfig prototype () {

		return delegate.prototype();

	}

}
