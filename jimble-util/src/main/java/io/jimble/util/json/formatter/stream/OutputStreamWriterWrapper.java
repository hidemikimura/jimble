package io.jimble.util.json.formatter.stream;

import io.jimble.util.convertor.Configration;

import java.io.*;
import java.nio.charset.Charset;

/**
 * 出力ラッパークラス.
 *
 * @author oibore
 */
public class OutputStreamWriterWrapper implements Closeable {

	/**
	 * 出力オブジェクト.
	 */
	private Writer writer;

	/**
	 * バッファ.
	 */
	private char[] buff;

	/**
	 * バッファ長.
	 */
	private static final int buffLength = 1024;

	/**
	 * オフセット.
	 */
	private int offset = 0;

	/**
	 * 自動クローズ
	 */
	private boolean isAutoClose = true;

	/**
	 * タブセット.
	 */
	protected static final String[] TABS = new String[25];
	static {
		for (int i = 0; i < TABS.length; i++) {
			TABS[i] = createString('\t', i);
		}
	}

	/**
	 * コンストラクタ.
	 */
	protected OutputStreamWriterWrapper () {

	}

	/**
	 * コンストラクタ.
	 *
	 * @param writer 出力オブジェクト
	 */
	public OutputStreamWriterWrapper (Writer writer) {

		this.writer = writer;
		this.buff = new char[buffLength];
	}

	/**
	 * コンストラクタ.
	 *
	 * @param stream 出力オブジェクト
	 */
	public OutputStreamWriterWrapper (OutputStream stream, Charset charset) {

		this.writer = new OutputStreamWriter(stream, charset);
		this.buff = new char[buffLength];
	}

	/**
	 * 自動クローズを設定する
	 *
	 * @param isAutoClose 自動でクローズする場合 = true
	 */
	public OutputStreamWriterWrapper setAutoClose (boolean isAutoClose) {

		this.isAutoClose = isAutoClose;
		return this;

	}

	/**
	 * タブ出力.
	 *
	 * @param conf 設定情報
	 * @throws Exception 例外
	 */
	public void writelt (Configration conf) throws Exception {

		if (!conf.isOutputIndent) {
			return;
		}
		int h = conf.Hierarchy;
		if (h < 0) {
			h = 0;
		} else if (h >= TABS.length) {
			h = TABS.length - 1;
		}
		write(TABS[h], 0, h);
	}

	/**
	 * タブ出力.
	 *
	 * @param conf 設定情報
	 * @param h    階層
	 * @throws Exception 例外
	 */
	public void writelt (Configration conf, int h) throws Exception {

		if (!conf.isOutputIndent) {
			return;
		}
		if (h < 0) {
			h = 0;
		} else if (h >= TABS.length) {
			h = TABS.length - 1;
		}
		write(TABS[h], 0, h);
	}

	/**
	 * 改行出力.
	 *
	 * @param conf 設定情報
	 * @throws Exception 例外
	 */
	public void writeln (Configration conf) throws Exception {

		if (!conf.isOutputIndent) {
			return;
		}
		write('\n');
	}

	/**
	 * 出力処理.
	 *
	 * @param value 値
	 * @throws Exception 例外
	 */
	public void write (String value) throws Exception {

		write(value, 0, value.length());

	}

	/**
	 * 出力処理.
	 *
	 * @param value 値
	 * @param s     開始位置
	 * @param e     終了位置
	 * @throws Exception 例外
	 */
	public void write (String value, int s, int e) throws Exception {

		int length = e - s;

		if (this.offset + length < buffLength) {

			value.getChars(s, e, this.buff, this.offset);
			this.offset += length;

		} else {

			this.writer.write(this.buff, 0, this.offset);

			if (length < buffLength) {

				value.getChars(s, e, this.buff, 0);
				this.offset = length;

			} else {

				this.writer.write(value, s, length);
				this.offset = 0;

			}
		}

	}

	/**
	 * 出力処理.
	 *
	 * @param c 値
	 * @throws Exception 例外
	 */
	public void write (char c) throws Exception {

		if (this.offset + 1 >= buffLength) {
			this.writer.write(this.buff, 0, this.offset);
			this.offset = 0;
		}

		this.buff[this.offset++] = c;

	}

	/**
	 * ストリームの文字列表現を取得する.
	 */
	@Override
	public String toString () {

		flush();
		return this.writer.toString();

	}

	/**
	 * フラッシュ.
	 */
	public void flush () {

		try {
			if (this.offset > 0) {
				this.writer.write(this.buff, 0, this.offset);
				this.offset = 0;
			}
			this.writer.flush();
		} catch (Exception e) {
		}

	}

	/**
	 * 繰り返し文字列作成.
	 *
	 * @param c   文字
	 * @param len 長さ
	 * @return 文字列
	 */
	private static String createString (char c, int len) {

		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < len; i++) {
			sb.append(c);
		}
		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close () throws IOException {

		try {
			flush();
			if (isAutoClose) {
				this.writer.close();
			}
		} catch (Exception e) {
		}
		buff = null;

	}

}
