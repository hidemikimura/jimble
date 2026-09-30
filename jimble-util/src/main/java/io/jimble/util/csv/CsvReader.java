package io.jimble.util.csv;

import de.siegmar.fastcsv.reader.CsvRecord;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.io.FileCharDetecter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * CSV読み込み
 *
 * <h2>BOM</h2>
 * <p>
 * <b>文字コードが UTF 系なら、先頭の BOM は FastCSV が読み捨てる</b>（{@code detectBomHeader}）。
 * BOM があれば、その BOM が示す文字コードで読む（UTF-8 と指定しても、UTF-16LE の BOM なら UTF-16LE）。
 * </p>
 *
 * <p>
 * <b>{@link Reader} を渡したときは、FastCSV の BOM 検出が効かない</b>（もう文字になっているので）。
 * そのときは、先頭の {@code U+FEFF} を1文字だけ読み捨てる——文字にしたときに BOM を残す文字コード
 * （Java の UTF-8 がそう）で開いた {@link Reader} だと、見出しの1つ目が「U+FEFF のついた id」になり、
 * {@code getString("id")} が引けなくなる。
 * </p>
 */
public class CsvReader implements Closeable, AutoCloseable {

	/* ヘッダー */
	private List<String> header = null;

	/* データ */
	private List<String> record = null;

	/* CSVリーダー */
	private de.siegmar.fastcsv.reader.CsvReader<CsvRecord> csvReader;
	private final Iterator<CsvRecord> csvIterator;

	/**
	 * コンストラクタ
	 *
	 * @param csvFile CSVファイル
	 */
	public CsvReader(File csvFile) {

		this(csvFile, 1, 2);

	}

	/**
	 * コンストラクタ
	 *
	 * @param csvFile	CSVファイル
	 * @param charset	文字コード
	 */
	public CsvReader(File csvFile, String charset) {

		this(openStream(csvFile), 1, 2, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * <p>文字コードは中身から推し量る（分からなければ Shift_JIS）。</p>
	 *
	 * @param csvFile     CSVファイル
	 * @param headerRowNo ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo   ボディ行番号
	 */
	public CsvReader(File csvFile, int headerRowNo, int bodyRowNo) {

		this(new Source(openDetected(csvFile)), headerRowNo, bodyRowNo);

	}

	/**
	 * コンストラクタ
	 *
	 * @param is 			CSVファイル
	 * @param charset		文字コード
	 */
	public CsvReader(InputStream is, String charset) {

		this(is, 1, 2, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * @param is 			CSVファイル
	 * @param headerRowNo	ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo		ボディ行番号
	 * @param charset		文字コード
	 */
	public CsvReader(InputStream is, int headerRowNo, int bodyRowNo, String charset) {

		this(new Source(open(is, Charset.forName(charset))), headerRowNo, bodyRowNo);

	}

	/**
	 * コンストラクタ
	 *
	 * @param isr 			CSVファイル
	 */
	public CsvReader(Reader isr) {

		this(isr, 1, 2);

	}

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * <b>FastCSV の BOM 検出は効かない</b>（もう文字になっている）。先頭の {@code U+FEFF} を1文字だけ読み捨てる。
	 * 文字コードが分かっているなら、{@link #CsvReader(InputStream, int, int, String)} を使うほうがよい。
	 * </p>
	 *
	 * @param isr 			CSVファイル
	 * @param headerRowNo	ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo		ボディ行番号
	 */
	public CsvReader(Reader isr, int headerRowNo, int bodyRowNo) {

		this(new Source(de.siegmar.fastcsv.reader.CsvReader.builder().ofCsvRecord(skipBom(isr))), headerRowNo, bodyRowNo);

	}

	/**
	 * コンストラクタ（中身）
	 *
	 * <p>
	 * <b>引数に FastCSV の型を出さない</b>（{@link Source} で包む）。javac は、呼び出し先のコンストラクタを決めるとき、
	 * 引数の数が合うものを {@code private} も含めて全部比べ、その引数の型のクラスを読みにいく。
	 * FastCSV はアプリのコンパイル時のクラスパスに無い（{@code implementation} の依存）ので、ここに FastCSV の型があると、
	 * アプリの {@code new CsvReader(file, 1, 2)} が「de.siegmar.fastcsv.reader.CsvReader のクラス・ファイルが見つかりません」
	 * で落ちた（2.1.4）。
	 * </p>
	 *
	 * @param source		FastCSV のリーダー（包んだもの）
	 * @param headerRowNo	ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo		ボディ行番号
	 */
	private CsvReader(Source source, int headerRowNo, int bodyRowNo) {

		this.csvReader = source.reader();
		this.csvIterator = csvReader.iterator();

		if (headerRowNo > 0) {
			for (int i = 0; i < headerRowNo; i++) {
				if (csvIterator.hasNext()) {
					header = csvIterator.next().getFields();
				}
			}
		}

		if (bodyRowNo > headerRowNo + 1) {
			for (int i = 0; i < bodyRowNo - (headerRowNo + 1); i++) {
				if (csvIterator.hasNext()) {
					csvIterator.next();
				}
			}
		}

	}

	/**
	 * 次の行に移動する
	 *
	 * @return 行が存在する場合 = true
	 */
	public boolean next () {

		if (!csvIterator.hasNext()) {
			return false;
		}
		record = csvIterator.next().getFields();
		return record != null;

	}

	/**
	 * ヘッダー行を取得する
	 *
	 * @return ヘッダー行
	 */
	public List<String> getHeader () {

		return header;

	}

	/**
	 * データ行を取得する
	 *
	 * @return データ行
	 */
	public List<String> getRecord () {

		return record;

	}

	// region getString

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public String getString (String key) {

		return getString(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public String getString (int index) {

		if (index < 0) {
			return null;
		}

		return record.get(index);

	}

	// endregion


	// region getByte

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public byte getByte (String key) {

		return getByte(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public byte getByte (int index) {

		try {
			return Convertor.convert(null, getString(index), byte.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	// endregion

	// region getShort

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public short getShort (String key) {

		return getShort(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public short getShort (int index) {

		try {
			return Convertor.convert(null, getString(index), short.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	// endregion

	// region getInt

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public int getInt (String key) {

		return getInt(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public int getInt (int index) {

		try {
			return Convertor.convert(null, getString(index), int.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	// endregion

	// region getLong

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public long getLong (String key) {

		return getLong(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public long getLong (int index) {

		try {
			return Convertor.convert(null, getString(index), long.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	// endregion

	// region getFloat

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public float getFloat (String key) {

		return getFloat(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public float getFloat (int index) {

		try {
			return Convertor.convert(null, getString(index), float.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	// endregion

	// region getDouble

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public double getDouble (String key) {

		return getDouble(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public double getDouble (int index) {

		try {
			return Convertor.convert(null, getString(index), double.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	// endregion

	// region getBoolean

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public boolean getBoolean (String key) {

		return getBoolean(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public boolean getBoolean (int index) {

		try {
			return Convertor.convert(null, getString(index), boolean.class);
		} catch (Exception ex) {
			return false;
		}

	}

	// endregion

	// region getDate

	/**
	 * 指定ヘッダーのデータを取得する
	 *
	 * @param key ヘッダー
	 * @return データ
	 */
	public Date getDate (String key) {

		return getDate(getKeyIndex(key));

	}

	/**
	 * 指定位置のデータを取得する
	 *
	 * @param index 位置
	 * @return データ
	 */
	public Date getDate (int index) {

		try {
			return Convertor.convert(null, getString(index), Date.class);
		} catch (Exception ex) {
			return null;
		}

	}

	// endregion

	// region ヘッダーの位置を取得する

	private final Map<String, Integer> headerKeyMap = new HashMap<>();

	/**
	 * ヘッダーの位置を取得する
	 *
	 * @param key ヘッダー
	 * @return 位置
	 */
	public int getKeyIndex (String key) {

		if (header == null) {
			return -1;
		}

		if (headerKeyMap.containsKey(key)) {
			return headerKeyMap.get(key);
		}

		int index = header.indexOf(key);
		headerKeyMap.put(key, index);

		return index;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close () {

		if (csvReader != null) {
			try {
				csvReader.close();
			} catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			} finally {
				csvReader = null;
			}
		}

	}

	/*
	 * ファイルを開く（コンストラクタの this(...) の中で使うので、ここで包む。要件 D-197）
	 */
	private static InputStream openStream (File csvFile) {

		try {
			return new FileInputStream(csvFile);
		} catch (IOException ex) {
			throw new java.io.UncheckedIOException("CSV を開けませんでした: " + csvFile, ex);
		}

	}

	/*
	 * 文字コードを中身から推し量る（分からなければ Shift_JIS）
	 */
	private static Charset detect (File csvFile) {

		try {
			return Charset.forName(FileCharDetecter.detector(csvFile, "SHIFT-JIS"));
		} catch (Exception ex) {
			throw io.jimble.util.internal.Unchecked.of("CSV_001", "CSV を開けませんでした: " + csvFile, ex);
		}

	}

	/*
	 * 文字コードを推し量ってから開く（推し量るのを先にする。失敗したときに開いたファイルを残さない）
	 */
	private static de.siegmar.fastcsv.reader.CsvReader<CsvRecord> openDetected (File csvFile) {

		Charset charset = detect(csvFile);

		return open(openStream(csvFile), charset);

	}

	/**
	 * バイトのまま FastCSV に渡す
	 *
	 * <p>
	 * <b>UTF 系なら BOM を FastCSV に読み捨てさせる</b>（{@code detectBomHeader}）。
	 * BOM の検出は {@link InputStream} を渡したときにしか効かないので、{@link Reader} にしてから渡さない。
	 * </p>
	 *
	 * @param is		入力
	 * @param charset	文字コード
	 * @return	FastCSV のリーダー
	 */
	private static de.siegmar.fastcsv.reader.CsvReader<CsvRecord> open (InputStream is, Charset charset) {

		return de.siegmar.fastcsv.reader.CsvReader.builder()
			.detectBomHeader(CsvCharsets.isUtf(charset))
			.ofCsvRecord(is, charset);

	}

	/**
	 * 先頭の U+FEFF を1文字だけ読み捨てる（{@link Reader} を渡されたとき）
	 *
	 * @param reader	入力
	 * @return	読み捨てたあとの入力
	 */
	private static Reader skipBom (Reader reader) {

		PushbackReader pushback = new PushbackReader(reader instanceof BufferedReader ? reader : new BufferedReader(reader), 1);

		try {

			int first = pushback.read();

			if (first >= 0 && first != '\uFEFF') {
				pushback.unread(first);
			}

		} catch (IOException ex) {
			throw new java.io.UncheckedIOException("CSV を読めませんでした", ex);
		}

		return pushback;

	}

	/**
	 * FastCSV のリーダーを包むもの（非公開のコンストラクタの引数に FastCSV の型を出さないため）
	 *
	 * <p>
	 * javac は呼び出し先を決めるときにこのクラスは読むが、中のフィールドの型（FastCSV）までは読まない。
	 * </p>
	 *
	 * @param reader	FastCSV のリーダー
	 */
	private record Source (de.siegmar.fastcsv.reader.CsvReader<CsvRecord> reader) {}

}
