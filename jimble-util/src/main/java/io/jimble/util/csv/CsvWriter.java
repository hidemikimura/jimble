package io.jimble.util.csv;

import io.jimble.util.internal.array.ArrayUtil;
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
 *
 * <h2>BOM</h2>
 * <p>
 * {@link #setWithBom(boolean)} で、書き始めに BOM を付けられる（Excel に UTF-8 の CSV を読ませるとき）。
 * <b>文字コードが UTF 系のときだけ効く</b>。Shift_JIS などでは何もしない（BOM というものが無い）。
 * </p>
 */
public class CsvWriter implements Closeable, AutoCloseable {

	/* 文字コード */
	private String charset = "SHIFT-JIS";

	/* BOM を付けるか（UTF 系のときだけ効く） */
	private boolean withBom = false;

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

	/* 1行でも書いたか */
	private boolean bodyWritten = false;

	/**
	 * 1行でも書いたか
	 *
	 * <p>
	 * <b>以前は誰も見ていない旗だった（要件 D-161）。</b>
	 * 立てるのも外すのも呼ぶ側の仕事で、<b>枠組みは一度も読んでいなかった</b>——
	 * 値を入れる {@code setBodyWritten} は消し、いまは
	 * {@link #writeLine(Object...)} が自分で立てる。
	 * </p>
	 *
	 * @return	1行でも書いた場合 = true
	 */
	public boolean isBodyWritten() {

		return bodyWritten;

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputFile    ファイル
	 */
	public CsvWriter(File outputFile) {

		this.writer = openWriter(outputFile, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputFile    ファイル
	 * @param charset       文字コード
	 */
	public CsvWriter(File outputFile, String charset) {

		this.charset = charset;
		this.writer = openWriter(outputFile, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputStream  ストリーム
	 */
	public CsvWriter(OutputStream outputStream) {

		this.writer = new OutputStreamWriter(outputStream, Charset.forName(charset));

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputStream  ストリーム
	 * @param charset       文字コード
	 */
	public CsvWriter(OutputStream outputStream, String charset) {

		this.charset = charset;
		this.writer = new OutputStreamWriter(outputStream, Charset.forName(charset));

	}

	/**
	 * コンストラクタ
	 *
	 * @param outputStreamWriter    ライター
	 */
	public CsvWriter(OutputStreamWriter outputStreamWriter) {

		/*
		 * 文字コードは渡されたライターのもの（BOM を付けるかの判定に使う）。
		 * getEncoding() は歴史的な名前（UTF8 など）を返すので、Charset を通して正式な名前にする
		 */
		this.charset = Charset.forName(outputStreamWriter.getEncoding()).name();
		this.writer = outputStreamWriter;

	}

	/**
	 * 書き始めたあとの設定変更を止める
	 *
	 * <p>
	 * <b>組み立ては1回きりである（要件 D-161）。</b>
	 * 1行でも書いたあとに区切りや引用符を変えても<b>何も起きなかった</b>——
	 * 出来上がった CSV は<b>指定したはずの形になっていない</b>のに、
	 * 例外もログも出ない。
	 * </p>
	 *
	 * @param what	変えようとしたもの
	 */
	private void refuseAfterWriting (String what) {

		if (bodyWritten) {
			throw new IllegalStateException(
				"書き始めたあとで " + what + " は変えられません（書き出す前に設定してください）");
		}

	}

	/**
	 * 改行コードを設定する
	 *
	 * @return	CsvWriter
	 */
	public CsvWriter setLineSeparatorCRLF () {

		refuseAfterWriting("改行コード");
		this.lineSeparator = LineDelimiter.CRLF;
		return this;

	}

	/**
	 * 改行コードを設定する
	 *
	 * @return	CsvWriter
	 */
	public CsvWriter setLineSeparatorCR () {

		refuseAfterWriting("改行コード");
		this.lineSeparator = LineDelimiter.CR;
		return this;

	}

	/**
	 * 改行コードを設定する
	 *
	 * @return	CsvWriter
	 */
	public CsvWriter setLineSeparatorLF () {

		refuseAfterWriting("改行コード");
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

		refuseAfterWriting("区切り文字");
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

		refuseAfterWriting("引用符");
		this.quoteCharacter = quoteCharacter;
		return this;

	}

	/**
	 * 書き始めに BOM を付けるか
	 *
	 * <p>
	 * <b>文字コードが UTF 系（UTF-8 / UTF-16BE / UTF-16LE / UTF-32 …）のときだけ効く。</b>
	 * それ以外では何もしない。{@code UTF-16}（向きの指定なし）は、Java が自分で BOM を書くので、
	 * 付けても付けなくても BOM は1つ付く。
	 * </p>
	 *
	 * @param withBom	付ける場合 = true
	 * @return	CsvWriter
	 */
	public CsvWriter setWithBom (boolean withBom) {

		refuseAfterWriting("BOM");
		this.withBom = withBom;
		return this;

	}

	/**
	 * BOM を書く（頼まれていて、UTF 系で、Java が自分で書かないとき）
	 */
	private void writeBomIfWanted () {

		Charset cs = Charset.forName(charset);

		if (!withBom || !CsvCharsets.isUtf(cs) || CsvCharsets.encoderWritesBom(cs)) {
			return;
		}

		try {
			// U+FEFF を文字として書けば、ライターの文字コードで BOM のバイト列になる（UTF-8 なら EF BB BF）
			writer.write('\uFEFF');
		} catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}

	}

	/**
	 * CSV出力を作成する
	 *
	 */
	private void createCsvWriter () {

		if (csvWriter == null) {

			writeBomIfWanted();

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
	public void writeLine (Object... record) {

		createCsvWriter();

		bodyWritten = true;

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
				params.addAll(extractParams(ArrayUtil.toList(param).toArray()));
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
	public void close () {

		/*
		 * <b>1行も書かずに閉じても、開いたものを閉じる。</b>かつては csvWriter を作っていないと何もせず、
		 * ファイルを開いたままにしていた。BOM を頼まれていれば、中身が空でも BOM だけは書く。
		 */
		if (csvWriter == null && writer != null) {
			try {
				writeBomIfWanted();
				writer.close();
			} catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			} finally {
				writer = null;
			}
			return;
		}

		if (csvWriter != null) {
			try {
				csvWriter.flush();
				csvWriter.close();
			} catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			} finally {
				csvWriter = null;
				writer = null;
			}
		}

	}

	/*
	 * 書き出し先を開く（要件 D-197）
	 */
	private static Writer openWriter (File outputFile, String charset) {

		try {
			return new FileWriter(outputFile, Charset.forName(charset));
		} catch (IOException ex) {
			throw new java.io.UncheckedIOException("CSV を開けませんでした: " + outputFile, ex);
		}

	}

}
