package io.jimble.util.csv;

import de.siegmar.fastcsv.reader.CsvRecord;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.io.FileCharDetecter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * CSV読み込み
 */
public class CsvReader implements Closeable, AutoCloseable {

	/* 初回読み込み */
	private boolean isFirstRead = true;

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
	 * @throws Exception 例外
	 */
	public CsvReader(File csvFile) throws Exception {

		this(csvFile, 1, 2);

	}

	/**
	 * コンストラクタ
	 *
	 * @param csvFile	CSVファイル
	 * @param charset	文字コード
	 * @throws Exception 例外
	 */
	public CsvReader(File csvFile, String charset) throws Exception {

		this(new FileInputStream(csvFile), 1, 2, charset);

	}

	/**
	 * コンストラクタ
	 *
	 * @param csvFile     CSVファイル
	 * @param headerRowNo ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo   ボディ行番号
	 * @throws Exception 例外
	 */
	public CsvReader(File csvFile, int headerRowNo, int bodyRowNo) throws Exception {

		this(new FileReader(csvFile, Charset.forName(FileCharDetecter.detector(csvFile, "SHIFT-JIS"))), headerRowNo, bodyRowNo);

	}

	/**
	 * コンストラクタ
	 *
	 * @param is 			CSVファイル
	 * @param charset		文字コード
	 * @throws Exception 例外
	 */
	public CsvReader(InputStream is, String charset) throws Exception {

		this(new InputStreamReader(is, Charset.forName(charset)), 1, 2);

	}

	/**
	 * コンストラクタ
	 *
	 * @param is 			CSVファイル
	 * @param headerRowNo	ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo		ボディ行番号
	 * @param charset		文字コード
	 * @throws Exception 例外
	 */
	public CsvReader(InputStream is, int headerRowNo, int bodyRowNo, String charset) throws Exception {

		this(new InputStreamReader(is, Charset.forName(charset)), headerRowNo, bodyRowNo);

	}

	/**
	 * コンストラクタ
	 *
	 * @param isr 			CSVファイル
	 * @throws Exception 例外
	 */
	public CsvReader(Reader isr) throws Exception {

		this(isr, 1, 2);

	}

	/**
	 * コンストラクタ
	 *
	 * @param isr 			CSVファイル
	 * @param headerRowNo	ヘッダー行番号(0=ヘッダーなし)
	 * @param bodyRowNo		ボディ行番号
	 * @throws Exception 例外
	 */
	public CsvReader(Reader isr, int headerRowNo, int bodyRowNo) throws Exception {

		BufferedReader bufferedReader = new BufferedReader(isr);
		csvReader = de.siegmar.fastcsv.reader.CsvReader.builder().ofCsvRecord(bufferedReader);
		csvIterator = csvReader.iterator();

		if (headerRowNo > 0) {
			for (int i = 0; i < headerRowNo; i++) {
				if (csvIterator.hasNext()) {
					header = removeBom(csvIterator.next().getFields());
				}
			}
		}

		if (bodyRowNo > headerRowNo + 1) {
			for (int i = 0; i < bodyRowNo - (headerRowNo + 1); i++) {
				if (csvIterator.hasNext()) {
					csvIterator.next();
					isFirstRead = false;
				}
			}
		}

	}

	/**
	 * 次の行に移動する
	 *
	 * @return 行が存在する場合 = true
	 * @throws Exception 例外
	 */
	public boolean next () throws Exception {

		if (!csvIterator.hasNext()) {
			return false;
		}
		record = removeBom(csvIterator.next().getFields());
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
	public void close () throws IOException {

		if (csvReader != null) {
			csvReader.close();
			csvReader = null;
		}

	}

	/**
	 * BOMを削除する
	 *
	 * @param lines	lines
	 * @return	lines
	 */
	private List<String> removeBom (List<String> lines) {

		if (lines == null || lines.isEmpty() || lines.getFirst() == null) {
			return lines;
		}

		if (!isFirstRead) {
			return lines;
		}

		isFirstRead = false;

		String first = lines.getFirst();
		if (first.startsWith("\uFEFF")) {
			List<String> res = new ArrayList<>();

			boolean isFirst = true;
			for (String s : lines) {
				if (isFirst) {
					res.add(s.substring(1));
					isFirst = false;
				} else {
					res.add(s);
				}
			}

			return res;
		}

		return lines;

	}

}
