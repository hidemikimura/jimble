package io.jimble.util.csv;

import de.siegmar.fastcsv.writer.LineDelimiter;
import de.siegmar.fastcsv.writer.QuoteStrategies;
import io.jimble.util.convertor.Convertor;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * CSV書き込み
 */
public class CsvWriter implements Closeable, AutoCloseable {

	/* 文字コード */
	private String charset = "SHIFT-JIS";

	/* 改行コード */
	private LineDelimiter lineSeparator = LineDelimiter.CRLF;

	/* レコードセパレータ */
	private char recordSeparator = ',';

	/* クォート */
	private char quoteCharacter = '"';

	/* 出力 */
	private Writer writer = null;

	/* CSVライター */
	private de.siegmar.fastcsv.writer.CsvWriter csvWriter;

	/* ボディ出力済み判定 */
	private boolean bodyWritten = false;

	/**
	 * ボディ出力済み判定
	 *
	 * @return	ボディ出力済み判定
	 */
	public boolean isBodyWritten() {

		return bodyWritten;

	}

	/**
	 * ボディ出力済み判定を設定する
	 *
	 * @param bodyWritten	ボディ出力済み判定
	 */
	public void setBodyWritten(boolean bodyWritten) {

		this.bodyWritten = bodyWritten;

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputFile    ファイル
	 * @throws Exception    例外
	 */
	public CsvWriter(File outputFile) throws IOException {

		this.writer = new FileWriter(outputFile, Charset.forName(charset));

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputFile    ファイル
	 * @param charset       文字コード
	 * @throws IOException  例外
	 */
	public CsvWriter(File outputFile, String charset) throws IOException {

		this.charset = charset;
		this.writer = new FileWriter(outputFile, Charset.forName(charset));

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputStream  ストリーム
	 * @throws IOException  例外
	 */
	public CsvWriter(OutputStream outputStream) throws IOException {

		this.writer = new OutputStreamWriter(outputStream, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputStream  ストリーム
	 * @param charset       文字コード
	 * @throws IOException  例外
	 */
	public CsvWriter(OutputStream outputStream, String charset) throws IOException {

		this.charset = charset;
		this.writer = new OutputStreamWriter(outputStream, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputStreamWriter    ライター
	 * @throws IOException          例外
	 */
	public CsvWriter(OutputStreamWriter outputStreamWriter) throws IOException {

		this.writer = outputStreamWriter;

	}

	/**
	 * 改行コードを設定する
	 *
	 * @return	CsvWriter
	 */
	public CsvWriter setLineSeparatorCRLF () {

		this.lineSeparator = LineDelimiter.CRLF;
		return this;

	}

	/**
	 * 改行コードを設定する
	 *
	 * @return	CsvWriter
	 */
	public CsvWriter setLineSeparatorCR () {

		this.lineSeparator = LineDelimiter.CR;
		return this;

	}

	/**
	 * 改行コードを設定する
	 *
	 * @return	CsvWriter
	 */
	public CsvWriter setLineSeparatorLF () {

		this.lineSeparator = LineDelimiter.LF;
		return this;

	}

	/**
	 * レコードセパレータを設定する
	 *
	 * @param recordSeparator   レコードセパレータ
	 * @return  CsvWriter
	 */
	public CsvWriter setRecordSeparator (char recordSeparator) {

		this.recordSeparator = recordSeparator;
		return this;

	}

	/**
	 * クォートを設定する
	 *
	 * @param quoteCharacter    クォート
	 * @return  CsvWriter
	 */
	public CsvWriter setQuoteCharacter (char quoteCharacter) {

		this.quoteCharacter = quoteCharacter;
		return this;

	}

	/**
	 * CSV出力を作成する
	 *
	 * @throws Exception 例外
	 */
	private void createCsvWriter () throws Exception {

		if (csvWriter == null) {

			this.csvWriter = de.siegmar.fastcsv.writer.CsvWriter.builder()
				.fieldSeparator(recordSeparator)
				.quoteCharacter(quoteCharacter)
				.quoteStrategy(QuoteStrategies.ALWAYS)
				.lineDelimiter(lineSeparator)
				.build(writer);

		}

	}

	/**
	 * 行を書き込む
	 *
	 * @param record 行
	 */
	public void writeLine (Object... record) throws Exception {

		createCsvWriter();

		List<Object> tempRecord = extractParams(record);
		List<String> _record = new ArrayList<>();
		for (Object o : tempRecord) {
			if (o == null) {
				_record.add("");
			} else {
				try {
					_record.add(Convertor.convert(null, o, String.class));
				} catch (Exception ex) {
					_record.add(o.toString());
				}
			}
		}

		csvWriter.writeRecord(_record.toArray(new String[0]));

	}

	/**
	 * パラメータを展開する
	 *
	 * @param _params パラメータ
	 * @return 展開済みパラメータ
	 */
	private List<Object> extractParams (Object[] _params) {

		if (_params == null || _params.length == 0) {
			return new ArrayList<>();
		}

		List<Object> params = new ArrayList<>();

		for (Object param : _params) {

			if (param == null) {
				params.add(null);
			} else if (param instanceof Collection<?>) {
				params.addAll(extractParams(((Collection<?>) param).toArray()));
			} else if (param.getClass().isArray()) {
				params.addAll(Arrays.asList((Object[]) param));
			} else {
				params.add(param);
			}

		}

		return params;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close () throws IOException {

		if (csvWriter != null) {
			csvWriter.flush();
			csvWriter.close();
			csvWriter = null;
		}

	}

}
