package io.jimble.web.response.stream;

import io.jimble.web.response.Response;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * レスポンスの出力ストリーム
 *
 * <p>クローズ時にレスポンスの後処理を呼ぶ。</p>
 */
public class ResponseOutputStream extends OutputStream {

	/* レスポンス */
	private final Response response;

	/* 出力ストリーム */
	private OutputStream outputStream;

	/**
	 * コンストラクタ
	 *
	 * @param response		レスポンス
	 * @param outputStream	出力ストリーム
	 */
	public ResponseOutputStream (Response response, OutputStream outputStream) {

		this.response = response;
		this.outputStream = outputStream;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (int b) throws IOException {

		this.outputStream.write(b);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (@NonNull byte[] b) throws IOException {

		this.outputStream.write(b);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (@NonNull byte[] b, int off, int len) throws IOException {

		this.outputStream.write(b, off, len);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flush () throws IOException {

		this.outputStream.flush();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close () throws IOException {

		try {
			if (this.outputStream != null) {
				this.outputStream.close();
				this.outputStream = null;
			}
		} catch (Exception ignore) {
			// クローズ失敗は握る
		} finally {
			this.response.afterResponse();
		}

	}

}
