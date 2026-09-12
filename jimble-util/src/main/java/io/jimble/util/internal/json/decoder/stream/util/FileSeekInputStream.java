package io.jimble.util.internal.json.decoder.stream.util;

import java.io.*;
import java.nio.charset.Charset;

/**
 * ファイル入力ストリーム.
 * 
 * @author DN
 */
public class FileSeekInputStream implements IInputStream {

	/** ストリーム. */
	private Reader reader;

	/** 1つ前のバッファ. */
	private int before = 0;

	/** 戻り判定. */
	private boolean isReturn = false;

	/**
	 * コンストラクタ.
	 * 
	 * @param r 入力リーダー
	 */
	public FileSeekInputStream(Reader r) {
		this.reader = r;
	}

	/**
	 * コンストラクタ.
	 * 
	 * @param is 入力ストリーム
	 * @param charset 文字コード
	 */
	public FileSeekInputStream(InputStream is, Charset charset) {
		this.reader = new BufferedReader(new InputStreamReader(is, charset));
	}

	/**
	 * コンストラクタ.
	 * 
	 * @param f ファイル
	 * @throws Exception 例外
	 */
	public FileSeekInputStream(File f, Charset charset) throws Exception {
		this.reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), charset));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int readInt() {

		if (isReturn) {
			isReturn = false;
			return before;
		}

		try {
			return before = this.reader.read();
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void returnPos() {
		isReturn = true;
	}

	/**
	 * 文字ストリームを閉じる.
	 */
	public void close() {
		try {
			this.reader.close();
		} catch (Exception e) {
		}
	}

}
