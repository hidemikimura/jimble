package io.jimble.util.data;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.json.Dson;
import io.jimble.util.data.async.Async;
import io.jimble.util.data.definition.IColumn;
import io.jimble.util.data.definition.ITable;

import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * データ
 */
public class Data extends LinkedHashMap<String, Object> {

	/*
	 * 直列化の版。
	 * SQL 結果キャッシュ（要件 F-D-28）が置き場に書き出すので、
	 * <b>クラスを直すたびに読めなくなる</b>ことがないよう固定する。
	 */
	private static final long serialVersionUID = 1L;

	// region キー存在判定

	/**
	 * キー存在判定
	 *
	 * @param column    IColumn
	 * @return  存在する場合 = true
	 */
	public boolean containsKey (IColumn column) {

		return getTableData(column).containsKey(column.name());

	}

	/**
	 * キー存在判定
	 *
	 * @param table    ITable
	 * @return  存在する場合 = true
	 */
	public boolean containsKey (ITable table) {

		return containsKey(table.name());

	}

	// endregion

	// region 値がnullか判定する

	/**
	 * 値がnullか判定する
	 *
	 * @param key	キー
	 * @return 存在しない or 値がnullの場合 = true
	 */
	public boolean isNull (String key) {

		if (!containsKey(key)) {
			return true;
		}

		return get(key) == null;

	}

	/**
	 * 値がnullか判定する
	 *
	 * @param column	IColumn
	 * @return 存在しない or 値がnullの場合 = true
	 */
	public boolean isNull (IColumn column) {

		if (!containsKey(column)) {
			return true;
		}

		return getObject(column) == null;

	}

	// endregion

	// region 文字列値が空か判定する

	/**
	 * 文字列値が空か判定する
	 *
	 * @param key	キー
	 * @return 存在しない or 値がnullの場合 or 値が空文字の場合 = true
	 */
	public boolean isStringEmpty (String key) {

		if (!containsKey(key)) {
			return true;
		}

		return getString(key) == null || getString(key).isEmpty();

	}

	/**
	 * 文字列値が空か判定する
	 *
	 * @param column	IColumn
	 * @return 存在しない or 値がnullの場合 or 値が空文字の場合 = true
	 */
	public boolean isStringEmpty (IColumn column) {

		if (!containsKey(column)) {
			return true;
		}

		return getString(column) == null || getString(column).isEmpty();

	}

	// endregion

	// region テーブルデータを取得する

	/**
	 * テーブルデータを取得する
	 *
	 * @param column    IColumn
	 * @return  Data
	 */
	private Data getTableData (IColumn column) {

		if (containsKey(column.table().name())) {
			return getData(column.table().name());
		}

		return this;

	}

	/**
	 * テーブルデータを取得する
	 *
	 * @param column    IColumn
	 * @return  Data
	 */
	private Data getTableDataOptional (IColumn column) {

		if (!containsKey(column.table().name())) {
			putData(column.table().name(), new Data());
		}

		return getData(column.table().name());

	}

	// endregion


	// region データを追加する

	/**
	 * データを追加する
	 *
	 * @param column    IColumn
	 * @param value     値
	 * @return  Data
	 */
	public Data putData (IColumn column, Object value) {

		getTableDataOptional(column).putData(column.name(), value);
		return this;

	}

	/**
	 * データを追加する
	 * テーブルネストされていればそこに、されてなければ直接追加する
	 *
	 * @param column	IColumn
	 * @param value		値
	 * @return	Data
	 */
	public Data putDataTakeCare (IColumn column, Object value) {

		if (!containsKey(column.table().name())) {
			putData(column.name(), value);
		} else {
			getTableDataOptional(column).putData(column.name(), value);
		}

		return this;

	}

	// endregion

	// region データを削除する

	/**
	 * データを削除する
	 *
	 * @param column	IColumn
	 * @return	Data
	 */
	public Data removeData (IColumn column) {

		getTableDataOptional(column).remove(column.name());
		return this;

	}

	// endregion

	// region 値を設定する

	/**
	 * 値を設定する
	 *
	 * @param key	キー
	 * @param value	値
	 * @return	自身
	 */
	public Data putData (String key, Object value) {

		put(key, value);
		return this;

	}

	/**
	 * 値を設定する
	 *
	 * @param map	マップ
	 * @return	自身
	 */
	public Data putAllData (Map<String, Object> map) {

		if (map != null) {
			putAll(map);
		}

		return this;

	}

	// endregion

	// endregion


	// region Data型で値を取得する

	/**
	 * Data型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	/*
	 * 無検査キャスト：Map の中身の型までは確かめられない。<b>キーは String である前提</b>で読み替える（JSON から作った Map は必ずそうなる）。違えば putAll のところで落ちる。
	 */
	@SuppressWarnings("unchecked")
	public Data getData (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);

			if (PropertyUtil.isAssignableFrom(Data.class, object.getClass())) {
				return (Data) object;
			}

			if (object instanceof Map<?, ?>) {
				try {
					Map<String, ?> map = (Map<String, ?>) object;
					Data res = new Data();
					res.putAll(map);
					put(key, res);
					return res;
				} catch (Exception ignore) {}
			}

			Data res = Convertor.convert(null, object, Data.class);
			put(key, res);
			return res;

		} catch (Exception ignore) {

			return null;

		}

	}

	/**
	 * Data型で値を取得する
	 * 値がない場合は生成して返す
	 *
	 * @param key	キー
	 * @return	値
	 */
	public Data getDataOptional (String key) {

		Data res = getData(key);
		if (res == null) {
			res = new Data();
			put(key, res);
		}

		return res;

	}

	/**
	 * Data型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Data getData (IColumn column) {

		return getTableData(column).getData(column.name());

	}

	/**
	 * Data型で値を取得する
	 * 値がない場合は生成して返す
	 *
	 * @param column    列
	 * @return	値
	 */
	public Data getDataOptional (IColumn column) {

		return getTableData(column).getDataOptional(column.name());

	}

	/**
	 * 対象テーブルのみのデータを抽出する
	 *
	 * @param table	テーブル
	 * @return	データ
	 */
	public Data extractTableData (ITable table) {

		Data res = new Data();
		Data tableData = getData(table);
		if (tableData == null) {
			return null;
		}

		res.put(table.name(), tableData);

		return res;

	}

	/**
	 * テーブルデータをフラットにする
	 *
	 * @return	データ
	 */
	public Data flattenTable (ITable table) {

		Data res = new Data();
		Data tableData = getData(table);
		if (tableData == null) {
			return null;
		}
		res.putAll(tableData);

		return res;

	}

	/**
	 * テーブルデータをフラットにする
	 *
	 * @return	データ
	 */
	public Data flattenTable (String tableName) {

		Data res = new Data();
		Data tableData = getData(tableName);
		if (tableData == null) {
			return null;
		}
		res.putAll(tableData);

		return res;

	}

	/**
	 * 値をフラットにする
	 *
	 * @return	Data
	 */
	public Data flattenValue () {

		Data res = new Data();
		for (String key : keySet()) {

			Object value = get(key);
			if (value instanceof List<?> list) {
				if (list.isEmpty()) {
					res.put(key, "");
				} else if (list.size() == 1) {
					res.put(key, list.getFirst());
				} else {
					res.put(key, list);
				}
			} else {
				res.put(key, value);
			}

		}

		return res;

	}

	/**
	 * テーブルデータをフラットにする
	 *
	 * @return	データ
	 */
	public Data flattenTable () {

		Data res = new Data();
		for (String key : keySet()) {
			Data tableData = getData(key);
			if (tableData != null) {
				res.putAll(tableData);
			}
		}

		return res;

	}

	/**
	 * Data型で値を取得する
	 *
	 * @param table    テーブル
	 * @return 値
	 */
	public Data getData (ITable table) {

		return getData(table.name());

	}

	/**
	 * Data型で値を取得する
	 * 値がない場合は生成して返す
	 *
	 * @param table    テーブル
	 * @return	値
	 */
	public Data getDataOptional (ITable table) {

		return getDataOptional(table.name());

	}

	// endregion

	// region List<Data>型で値を取得する

	/**
	 * {@code List<Data>}型で値を取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	/*
	 * 無検査キャスト：Map の中身の型までは確かめられない。<b>キーは String である前提</b>で読み替える（JSON から作った Map は必ずそうなる）。違えば putAll のところで落ちる。
	 */
	@SuppressWarnings("unchecked")
	public List<Data> getDataList (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof List<?> list) {
				List<Data> res = new ArrayList<>();

				for (Object o : list) {
					Data converted = null;
					if (PropertyUtil.isAssignableFrom(Data.class, o.getClass())) {
						converted = (Data) o;
					} else if (o instanceof Map<?,?>) {
						try {
							Map<String, ?> map = (Map<String, ?>) o;
							converted = (Data) PropertyUtil.newInstance(Data.class);
							if (converted != null) {
								converted.putAll(map);
								put(key, res);
							}
						} catch (Exception ex) {}
					}

					if (converted == null) {
						converted = Convertor.convert(null, o, Data.class);
					}

					res.add(converted);
				}

				put(key, res);

				return res;
			}

			List<Data> res = Convertor.convert(null, object, ArrayList.class, Data.class);
			put(key, res);
			return res;

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * {@code List<Data>}型で値を取得する
	 * 値がない場合は生成して返す
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Data> getDataListOptional (String key) {

		List<Data> res = getDataList(key);
		if (res == null) {
			res = new ArrayList<>();
			put(key, res);
		}

		return res;

	}

	/**
	 * {@code List<Data>}型で値を取得する
	 *
	 * @param column	列
	 * @return	値
	 */
	public List<Data> getDataList (IColumn column) {

		return getTableData(column).getDataList(column.name());

	}

	/**
	 * {@code List<Data>}型で値を取得する
	 * 値がない場合は生成して返す
	 *
	 * @param column	列
	 * @return	値
	 */
	public List<Data> getDataListOptional (IColumn column) {

		return getTableData(column).getDataListOptional(column.name());

	}

	// endregion

	// region byte型で値を取得する

	/**
	 * byte型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public byte getByte (String key) {

		Byte res = getByteObject(key);
		return res == null ? 0 : res;

	}

	/**
	 * byte型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Byte getByteObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Byte) {
				return (Byte) object;
			} else if (object instanceof Number) {
				return ((Number) object).byteValue();
			}

			return Convertor.convert(null, object, Byte.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * byte型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public byte getByte (IColumn column) {

		return getTableData(column).getByte(column.name());

	}

	/**
	 * byte型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Byte getByteObject (IColumn column) {

		return getTableData(column).getByteObject(column.name());

	}

	// endregion

	// region byte配列型で値を取得する

	/**
	 * byte型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public byte[] getByteArray (String key) {

		Object res = get(key);
		if (res == null) {
			return null;
		} else if (res instanceof byte[] val) {
			return val;
		}

		try {

			return Convertor.convert(null, res, byte[].class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * byte型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public byte[] getByteArray (IColumn column) {

		return getTableData(column).getByteArray(column.name());

	}

	// endregion

	// region short型で値を取得する

	/**
	 * short型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public short getShort (String key) {

		Short res = getShortObject(key);
		return res == null ? 0 : res;

	}

	/**
	 * short型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Short getShortObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Short) {
				return (Short) object;
			} else if (object instanceof Number) {
				return ((Number) object).shortValue();
			}

			return Convertor.convert(null, object, Short.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * short型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public short getShort (IColumn column) {

		return getTableData(column).getShort(column.name());

	}

	/**
	 * short型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Short getShortObject (IColumn column) {

		return getTableData(column).getShortObject(column.name());

	}

	// endregion

	// region int型で値を取得する

	/**
	 * int型で値を取得する
	 * 値がなかった場合、0を返します。
	 *
	 * @param key キー
	 * @return 値
	 */
	public int getInt (String key) {

		Integer res = getIntObject(key);
		return res == null ? 0 : res;

	}

	/**
	 * int型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Integer getIntObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Integer) {
				return (Integer) object;
			} else if (object instanceof Number) {
				return ((Number) object).intValue();
			}

			return Convertor.convert(null, object, Integer.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * int型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public int getInt (IColumn column) {

		return getTableData(column).getInt(column.name());

	}

	/**
	 * int型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Integer getIntObject (IColumn column) {

		return getTableData(column).getIntObject(column.name());

	}

	// endregion

	// region long型で値を取得する

	/**
	 * long型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public long getLong (String key) {

		Long res = getLongObject(key);
		return res == null ? 0 : res;

	}

	/**
	 * long型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Long getLongObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Long) {
				return (Long) object;
			} else if (object instanceof Number) {
				return ((Number) object).longValue();
			}

			return Convertor.convert(null, object, Long.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * long型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public long getLong (IColumn column) {

		return getTableData(column).getLong(column.name());

	}

	/**
	 * long型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Long getLongObject (IColumn column) {

		return getTableData(column).getLongObject(column.name());

	}

	// endregion

	// region float型で値を取得する

	/**
	 * float型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public float getFloat (String key) {

		Float res = getFloatObject(key);
		return res == null ? 0 : res;

	}

	/**
	 * float型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Float getFloatObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Float) {
				return (Float) object;
			} else if (object instanceof Number) {
				return ((Number) object).floatValue();
			}

			return Convertor.convert(null, object, Float.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * float型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public float getFloat (IColumn column) {

		return getTableData(column).getFloat(column.name());

	}

	/**
	 * float型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Float getFloatObject (IColumn column) {

		return getTableData(column).getFloatObject(column.name());

	}

	// endregion

	// region double型で値を取得する

	/**
	 * double型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public double getDouble (String key) {

		Double res = getDoubleObject(key);
		return res == null ? 0 : res;

	}

	/**
	 * double型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Double getDoubleObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Double) {
				return (Double) object;
			} else if (object instanceof Number) {
				return ((Number) object).doubleValue();
			}

			return Convertor.convert(null, object, Double.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * double型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public double getDouble (IColumn column) {

		return getTableData(column).getDouble(column.name());

	}

	/**
	 * double型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Double getDoubleObject (IColumn column) {

		return getTableData(column).getDoubleObject(column.name());

	}

	// endregion

	// region BigDecimal型で取得する

	/**
	 * BigDecimal型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public BigDecimal getBigDecimal (String key) {

		if (isNull(key)) {
			return new BigDecimal("0");
		}

		try {

			Object object = get(key);
			if (object instanceof BigDecimal) {
				return (BigDecimal) object;
			} else if (object instanceof Number) {
				return new BigDecimal(String.valueOf(object));
			}

			return Convertor.convert(null, object, BigDecimal.class);

		} catch (Exception ex) {

			return new BigDecimal("0");

		}

	}

	/**
	 * BigDecimal型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public BigDecimal getBigDecimal (IColumn column) {

		return getTableData(column).getBigDecimal(column.name());

	}

	// endregion

	// region boolean型で値を取得する

	/**
	 * boolean型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public boolean getBoolean (String key) {

		Boolean res = getBooleanObject(key);
		return res != null && res;

	}

	/**
	 * boolean型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Boolean getBooleanObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Boolean) {
				return (Boolean) object;
			} else if (object instanceof Number) {
				return ((Number) object).longValue() == 1;
			} else if (object instanceof String) {
				return "true".equalsIgnoreCase((String) object)
					|| "1".equalsIgnoreCase((String) object);
			}

			return Convertor.convert(null, object, Boolean.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * boolean型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public boolean getBoolean (IColumn column) {

		return getTableData(column).getTableData(column).getBoolean(column.name());

	}

	/**
	 * double型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Boolean getBooleanObject (IColumn column) {

		return getTableData(column).getBooleanObject(column.name());

	}

	// endregion

	// region 戻り値型で取得する

	/**
	 * 戻り値型で取得する
	 *
	 * @param key キー
	 * @param <T> 戻り値型
	 * @return 値
	 */
	@SuppressWarnings("unchecked")
	public <T> T getObject (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			return (T) get(key);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * 戻り値型で取得する
	 *
	 * @param column    列
	 * @param <T> 戻り値型
	 * @return 値
	 */
	@SuppressWarnings("unchecked")
	public <T> T getObject (IColumn column) {

		return getTableData(column).getObject(column.name());

	}

	// endregion

	// region 指定した型で取得する

	/**
	 * 指定した型で取得する
	 *
	 * @param key	キー
	 * @return	オブジェクト
	 */
	/*
	 * 可変長引数に総称型を取る。
	 *
	 * <b>渡された配列は読むだけで、中に何かを入れることはしない</b>
	 * （型を知るために getComponentType() を見る、あるいはそのまま次へ渡すだけ）。
	 * だから @SafeVarargs で正しい。書き込むようになったらこの印を外すこと。
	 *
	 * 印を付けるには final でなければならない。
	 * このメソッドを上書きしている派生クラスは無い（生成される Data も含めて）。
	 */
	@SafeVarargs
	public final <T> T getValue (String key, T...types) {

		try {

			Object value = get(key);
			return Convertor.convert(null, value, types.getClass().getComponentType());

		} catch (Exception ex) {

			return null;

		}

	}

	// endregion

	// region 指定した型で取得する

	/**
	 * 指定した型で取得する
	 *
	 * @param column	列
	 * @return	オブジェクト
	 */
	/*
	 * 可変長引数に総称型を取る。
	 *
	 * <b>渡された配列は読むだけで、中に何かを入れることはしない</b>
	 * （型を知るために getComponentType() を見る、あるいはそのまま次へ渡すだけ）。
	 * だから @SafeVarargs で正しい。書き込むようになったらこの印を外すこと。
	 *
	 * 印を付けるには final でなければならない。
	 * このメソッドを上書きしている派生クラスは無い（生成される Data も含めて）。
	 */
	@SafeVarargs
	public final <T> T getValue (IColumn column, T...types) {

		try {

			Object value = getTableData(column).get(column.name());
			return Convertor.convert(null, value, types.getClass().getComponentType());

		} catch (Exception ex) {

			return null;

		}

	}

	// endregion

	// region List<String>型で取得する

	/**
	 * {@code List<String>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<String> getStringList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, String.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<String>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<String> getStringListOptional (String key) {

		List<String> res = getStringList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<String>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<String> getStringList (IColumn column) {

		return getTableData(column).getStringList(column.name());

	}

	/**
	 * {@code List<String>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<String> getStringListOptional (IColumn column) {

		return getTableData(column).getStringListOptional(column.name());

	}

	// endregion

	// region List<T>型で取得する

	/**
	 * {@code List<T>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	/*
	 * 可変長引数に総称型を取る。
	 *
	 * <b>渡された配列は読むだけで、中に何かを入れることはしない</b>
	 * （型を知るために getComponentType() を見る、あるいはそのまま次へ渡すだけ）。
	 * だから @SafeVarargs で正しい。書き込むようになったらこの印を外すこと。
	 *
	 * 印を付けるには final でなければならない。
	 * このメソッドを上書きしている派生クラスは無い（生成される Data も含めて）。
	 *
	 * 受け取った配列を Arrays.asList で<b>読むだけ</b>のところで varargs 警告が出る。
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public final <T> List<T> getObjectList (String key, Class<T>...types) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			List<Class<?>> classList = new ArrayList<>();
			classList.add(List.class);
			if (types != null && types.length > 0) {
				classList.addAll(Arrays.asList(types));
			}
			List<T> res = Convertor.convert(null, object, classList.toArray(new Class<?>[]{}));
			put(key, res);
			return res;
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<T>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	/*
	 * 可変長引数に総称型を取る。
	 *
	 * <b>渡された配列は読むだけで、中に何かを入れることはしない</b>
	 * （型を知るために getComponentType() を見る、あるいはそのまま次へ渡すだけ）。
	 * だから @SafeVarargs で正しい。書き込むようになったらこの印を外すこと。
	 *
	 * 印を付けるには final でなければならない。
	 * このメソッドを上書きしている派生クラスは無い（生成される Data も含めて）。
	 */
	@SafeVarargs
	public final <T> List<T> getObjectListOptional (String key, Class<T>...types) {

		List<T> res = getObjectList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<T>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	/*
	 * 可変長引数に総称型を取る。
	 *
	 * <b>渡された配列は読むだけで、中に何かを入れることはしない</b>
	 * （型を知るために getComponentType() を見る、あるいはそのまま次へ渡すだけ）。
	 * だから @SafeVarargs で正しい。書き込むようになったらこの印を外すこと。
	 *
	 * 印を付けるには final でなければならない。
	 * このメソッドを上書きしている派生クラスは無い（生成される Data も含めて）。
	 *
	 * 受け取った配列を<b>そのまま次へ渡す</b>ところで varargs 警告が出る。
	 * 渡す先も @SafeVarargs（読むだけ）なので、ここは安全である。
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public final <T> List<T> getObjectList (IColumn column, Class<T>...types) {

		return getTableData(column).getObjectList(column.name(), types);

	}

	/**
	 * {@code List<T>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	/*
	 * 可変長引数に総称型を取る。
	 *
	 * <b>渡された配列は読むだけで、中に何かを入れることはしない</b>
	 * （型を知るために getComponentType() を見る、あるいはそのまま次へ渡すだけ）。
	 * だから @SafeVarargs で正しい。書き込むようになったらこの印を外すこと。
	 *
	 * 印を付けるには final でなければならない。
	 * このメソッドを上書きしている派生クラスは無い（生成される Data も含めて）。
	 *
	 * 受け取った配列を<b>そのまま次へ渡す</b>ところで varargs 警告が出る。
	 * 渡す先も @SafeVarargs（読むだけ）なので、ここは安全である。
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public final <T> List<T> getObjectListOptional (IColumn column, Class<T>...types) {

		return getTableData(column).getObjectListOptional(column.name(), types);

	}

	// endregion

	// region List<Byte>型で取得する

	/**
	 * {@code List<Byte>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Byte> getByteList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, Byte.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<Byte>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Byte> getByteListOptional (String key) {

		List<Byte> res = getByteList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<Byte>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Byte> getByteList (IColumn column) {

		return getTableData(column).getByteList(column.name());

	}

	/**
	 * {@code List<Byte>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Byte> getByteListOptional (IColumn column) {

		return getTableData(column).getByteListOptional(column.name());

	}

	// endregion

	// region List<Short>型で取得する

	/**
	 * {@code List<Short>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Short> getShortList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, Short.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<Short>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Short> getShortListOptional (String key) {

		List<Short> res = getShortList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<Short>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Short> getShortList (IColumn column) {

		return getTableData(column).getShortList(column.name());

	}

	/**
	 * {@code List<Short>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Short> getShortListOptional (IColumn column) {

		return getTableData(column).getShortListOptional(column.name());

	}

	// endregion

	// region List<Integer>型で取得する

	/**
	 * {@code List<Integer>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Integer> getIntList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, Integer.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<Integer>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Integer> getIntListOptional (String key) {

		List<Integer> res = getIntList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<Integer>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Integer> getIntList (IColumn column) {

		return getTableData(column).getIntList(column.name());

	}

	/**
	 * {@code List<Integer>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Integer> getIntListOptional (IColumn column) {

		return getTableData(column).getIntListOptional(column.name());

	}

	// endregion

	// region List<Long>型で取得する

	/**
	 * {@code List<Long>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Long> getLongList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, Long.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<Long>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Long> getLongListOptional (String key) {

		List<Long> res = getLongList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<Long>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Long> getLongList (IColumn column) {

		return getTableData(column).getLongList(column.name());

	}

	/**
	 * {@code List<Long>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Long> getLongListOptional (IColumn column) {

		return getTableData(column).getLongListOptional(column.name());

	}

	// endregion

	// region List<Float>型で取得する

	/**
	 * {@code List<Float>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Float> getFloatList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, Float.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<Long>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Float> getFloatListOptional (String key) {

		List<Float> res = getFloatList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<Float>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Float> getFloatList (IColumn column) {

		return getTableData(column).getFloatList(column.name());

	}

	/**
	 * {@code List<Long>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Float> getFloatListOptional (IColumn column) {

		return getTableData(column).getFloatListOptional(column.name());

	}

	// endregion

	// region List<Float>型で取得する

	/**
	 * {@code List<Double>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Double> getDoubleList (String key) {

		if (isNull(key)) {
			return null;
		}

		Object object = getObject(key);
		try {
			return Convertor.convert(null, object, List.class, Double.class);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * {@code List<Double>}型で取得する
	 *
	 * @param key	キー
	 * @return	値
	 */
	public List<Double> getDoubleListOptional (String key) {

		List<Double> res = getDoubleList(key);
		if (res == null) {
			res = new ArrayList<>();
		}
		put(key, res);

		return res;

	}

	/**
	 * {@code List<Double>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Double> getDoubleList (IColumn column) {

		return getTableData(column).getDoubleList(column.name());

	}

	/**
	 * {@code List<Double>}型で取得する
	 *
	 * @param column    列
	 * @return	値
	 */
	public List<Double> getDoubleListOptional (IColumn column) {

		return getTableData(column).getDoubleListOptional(column.name());

	}

	// endregion

	// region String型で値を取得する

	/**
	 * String型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public String getString (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof String) {
				return (String) object;
			}

			return Convertor.convert(null, object, String.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * String 型で値を取得する（無ければ空文字）
	 *
	 * <p>
	 * <b>読むだけである（D-173）。</b>かつては無かったときに {@code put(key, "")} していた——
	 * <b>1回読んだだけで、その {@code Data} の JSON に空のキーが増える</b>。
	 * リクエストのボディをログに出す、レスポンスをそのまま返す、
	 * ハッシュを取って比べる——<b>どれも「読んだかどうか」で結果が変わっていた</b>。
	 * </p>
	 *
	 * @param key キー
	 * @return 値（無ければ空文字）
	 */
	public String getStringOptional (String key) {

		String res = getString(key);

		return res == null ? "" : res;

	}

	/**
	 * String型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public String getString (IColumn column) {

		return getTableData(column).getString(column.name());

	}

	/**
	 * String型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public String getStringOptional (IColumn column) {

		return getTableData(column).getStringOptional(column.name());

	}

	// endregion

	// region enumで取得する

	/**
	 * enumで取得する
	 *
	 * @param key		キー
	 * @param enumType	enum型
	 * @return	enum
	 */
	/*
	 * 無検査キャスト：戻り値の型 {@code T} は呼び出し側が決める。<b>enum かどうかは実行時に確かめている</b>ので、ここで返すのは必ず enum である。{@code T} が違えば呼び出し側で ClassCastException になる（型を書いた側の間違い）。
	 */
	@SuppressWarnings("unchecked")
	public <T> T getEnum (String key, Class<? extends Enum<?>> enumType) {

		Object value = get(key);
		if (value == null) {
			return null;
		} else if (value.getClass().isEnum()) {
			return (T) value;
		}

		String textValue = getString(key);
		if (textValue == null || textValue.isEmpty()) {
			return null;
		}

		for (Object e : enumType.getEnumConstants()) {
			if (e instanceof Enum<?> eo) {
				if (textValue.equals(eo.name())) {
					return (T) eo;
				}
			}
		}

		return null;

	}

	/**
	 * enumで取得する
	 *
	 * @param column	列
	 * @param enumType	enum型
	 * @return	enum
	 */
	public <T> T getEnum (IColumn column, Class<? extends Enum<?>> enumType) {

		return getTableData(column).getEnum(column.name(), enumType);

	}

	// endregion

	// region Date型で値を取得する

	/**
	 * Date型で値を取得する
	 *
	 * @param key キー
	 * @return 値
	 */
	public Date getDate (String key) {

		if (isNull(key)) {
			return null;
		}

		try {

			Object object = get(key);
			if (object instanceof Date) {
				return (Date) object;
			}

			return Convertor.convert(null, object, Date.class);

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * Date型で値を取得する
	 *
	 * @param column    列
	 * @return 値
	 */
	public Date getDate (IColumn column) {

		return getTableData(column).getDate(column.name());

	}

	// endregion

	// region Date型のTimeを取得する

	/**
	 * Date型のTimeを取得する
	 *
	 * @param key キー
	 * @return Date型のTime
	 */
	public long getDateTime (String key) {

		Date date = getDate(key);
		if (date == null) {
			return 0;
		}

		return date.getTime();

	}

	/**
	 * Date型のTimeを取得する
	 *
	 * @param column    列
	 * @return Date型のTime
	 */
	public long getDateTime (IColumn column) {

		return getTableData(column).getDateTime(column.name());

	}

	// endregion

	// region 日付をフォーマットして取得する

	/**
	 * 日付をフォーマットして取得する
	 *
	 * @param key    キー
	 * @param format フォーマット
	 * @return 値
	 */
	public String getDateString (String key, String format) {

		if (isNull(key)) {
			return "";
		}

		try {

			Date value = getDate(key);

			if (value == null) {
				return "";
			}

			SimpleDateFormat sdf = new SimpleDateFormat(format);
			return sdf.format(value);

		} catch (Exception ex) {

			return "";

		}

	}

	/**
	 * 日付をフォーマットして取得する
	 *
	 * @param column    列
	 * @param format フォーマット
	 * @return 値
	 */
	public String getDateString (IColumn column, String format) {

		return getTableData(column).getDateString(column.name(), format);

	}

	// endregion

	// region 指定した型に変換する

	/**
	 * 指定した型に変換する
	 *
	 * @return オブジェクト
	 */
	@SuppressWarnings("unchecked")
	public <T> T convert (T...types) {

		try {

			return Convertor.convert(null, this, types.getClass().getComponentType());

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * 指定した型に変換する
	 *
	 * @param to  変換先クラス
	 * @return オブジェクト
	 */
	public <T> T convertClass (Class<?>...to) {

		try {

			return Convertor.convert(null, this, to);

		} catch (Exception ex) {

			return null;

		}

	}

	// endregion

	// region JSON文字列で取得する

	/**
	 * JSON文字列を取得する
	 *
	 * @return	JSON文字列
	 */
	public String getJsonString () {

		return getJsonString(false);

	}

	/**
	 * JSON文字列を取得する
	 *
	 * @param isPrettyPrint	整形表示
	 * @return	JSON文字列
	 */
	public String getJsonString (boolean isPrettyPrint) {

		return getJsonString(isPrettyPrint, TableNest.AS_IS);

	}

	/**
	 * JSON文字列を取得する（要件 F-A-11）
	 *
	 * <p>
	 * {@code AsyncData} / {@code AsyncList} を<b>テーブル名でネストするかどうか</b>を選ぶ。
	 * </p>
	 *
	 * <pre>
	 * data.getJsonString(TableNest.ON);    // {"post": {"id": 1}, "comments": [...]}
	 * data.getJsonString(TableNest.OFF);   // {"id": 1, "comments": [...]}
	 * </pre>
	 *
	 * @param tableNest	テーブルネストの扱い
	 * @return	JSON文字列
	 */
	public String getJsonString (TableNest tableNest) {

		return getJsonString(false, tableNest);

	}

	/**
	 * JSON文字列を取得する（要件 F-A-11）
	 *
	 * @param isPrettyPrint	整形表示
	 * @param tableNest		テーブルネストの扱い
	 * @return	JSON文字列
	 */
	public String getJsonString (boolean isPrettyPrint, TableNest tableNest) {

		Configration configration = new Configration();
		configration.isOutputIndent(isPrettyPrint);
		configration.tableNest(tableNest == null ? TableNest.AS_IS : tableNest);

		String res = Dson.encodes(configration, this);
		if (res == null || res.isEmpty()) {
			return "{}";
		}

		return res;

	}

	/**
	 * JSON文字列を出力する
	 *
	 * @param outputStream  出力ストリーム
	 */
	public void outputJsonString (OutputStream outputStream) {

		Dson.encodes(null, this, outputStream, "UTF-8");

	}

	/**
	 * JSON文字列を出力する
	 *
	 * @param outputStream  出力ストリーム
	 * @param isAutoClose   ストリーム自動クローズ
	 */
	public void outputJsonString (OutputStream outputStream, boolean isAutoClose) {

		Configration configration = new Configration();
		configration.isAutoClose(isAutoClose);
		Dson.encodes(configration, this, outputStream, "UTF-8");

	}

	/**
	 * JSON文字列を出力する
	 *
	 * @param outputStream  出力ストリーム
	 * @param configration  JSONフォーマット設定
	 */
	public void outputJsonString (OutputStream outputStream, Configration configration) {

		Dson.encodes(configration, this, outputStream, "UTF-8");

	}

	/**
	 * JSON文字列を出力する
	 *
	 * @param outputStream  ライター
	 */
	public void outputJsonString (Writer outputStream) {

		Dson.encodes(null, this, outputStream);

	}

	/**
	 * JSON文字列を出力する
	 *
	 * @param outputStream  ライター
	 * @param isAutoClose   ライター自動クローズ
	 */
	public void outputJsonString (Writer outputStream, boolean isAutoClose) {

		Configration configration = new Configration();
		configration.isAutoClose(isAutoClose);
		Dson.encodes(configration, this, outputStream);

	}

	/**
	 * JSON文字列を出力する
	 *
	 * @param outputStream  ライター
	 * @param configration  JSONフォーマット設定
	 */
	public void outputJsonString (Writer outputStream, Configration configration) {

		Dson.encodes(configration, this, outputStream);

	}

	// endregion


	// region JSON文字列からオブジェクトを生成する

	/**
	 * JSON文字列からオブジェクトを生成する
	 *
	 * @param jsonString	JSON文字列
	 * @return	オブジェクト
	 */
	public static Data fromJsonString (String jsonString) {

		return Dson.decodes(jsonString, Data.class);

	}

	// endregion


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {

		return summary();

	}

	/** 要約表示に出すキーの上限 */
	public static final int SUMMARY_MAX_KEYS = 20;

	/**
	 * 要約表示（要件 F-D-26 / O-3）
	 *
	 * <p>
	 * <b>値そのものは出さない。</b>キーと型だけを出す。
	 * </p>
	 *
	 * <p>移送元は {@code toString()} が {@link #getJsonString()} だった。これで起きること：</p>
	 *
	 * <ul>
	 *   <li><b>ログに1行出しただけで中身が全部出る。</b>
	 *       パスワードのハッシュでもトークンでも、そこに入っていれば出る</li>
	 *   <li>デバッガで変数を眺めただけで、数 MB の JSON 化が走る</li>
	 *   <li><b>{@code AsyncData} を含んでいると、そこで SQL が飛ぶ</b>（要件 F-D-27）</li>
	 * </ul>
	 *
	 * <p>JSON が要るときは {@link #getJsonString()} を呼ぶ。<b>そう書いたときだけ出る。</b></p>
	 *
	 * @return	要約
	 */
	public String summary () {

		StringBuilder text = new StringBuilder();

		text.append(getClass().getSimpleName()).append('(').append(size()).append("件)");

		if (isEmpty()) {
			return text.toString();
		}

		text.append(' ').append('{');

		int count = 0;

		for (Map.Entry<String, Object> entry : entrySet()) {

			if (count > 0) {
				text.append(", ");
			}

			if (count >= SUMMARY_MAX_KEYS) {
				text.append("...");
				break;
			}

			text.append(entry.getKey()).append('=').append(typeOf(entry.getValue()));

			count++;

		}

		return text.append('}').toString();

	}

	/**
	 * 要約に出す値の表し方
	 *
	 * <p>
	 * 遅延読み込みのものは<b>読み込み状態を出す</b>（要件 F-D-27）。
	 * それ以外は型名だけにする。
	 * </p>
	 *
	 * @param value	値
	 * @return	表示
	 */
	private static String typeOf (Object value) {

		if (value == null) {
			return "null";
		}

		if (value instanceof Async) {
			// 未読み込みなら読み込まない toString になっている
			return value.toString();
		}

		if (value instanceof Collection<?> collection) {
			return "%s(%d件)".formatted(value.getClass().getSimpleName(), collection.size());
		}

		return value.getClass().getSimpleName();

	}


	// region 触る前の口（要件 D-157）

	/**
	 * 中身に触る前に呼ばれる
	 *
	 * <p>
	 * <b>ここは何もしない。</b>
	 * {@link io.jimble.util.data.async.AsyncData} が<b>ここだけを上書きして</b>、
	 * 遅延読み込みを起こす。
	 * </p>
	 *
	 * <h4>なぜ1本にまとめたのか</h4>
	 * <p>
	 * <b>以前は {@code AsyncData} が読み取り 17 個を1つずつ上書きしていた。</b>
	 * 上書きし忘れた分だけ穴が開くので、
	 * {@code asyncData.remove("id")} や {@code computeIfAbsent} は
	 * <b>未読み込みの空マップを触って、黙って null を返していた。</b>
	 * </p>
	 *
	 * <p>
	 * <b>穴は増える一方だった。</b>Java 21 で {@code LinkedHashMap} が
	 * {@code SequencedMap} になり、{@code putFirst} / {@code pollLastEntry} などが
	 * <b>誰も上書きしないまま生えた</b>。
	 * <b>JDK が足すたびに穴が開く形</b>をやめるために、口を1本にした。
	 * </p>
	 *
	 * <p>
	 * <b>漏れは {@code DataAccessFunnelTest} が見張る。</b>
	 * {@code LinkedHashMap} の公開メソッドを反射で数えて、
	 * <b>ここを通っていないものが1つでもあれば落ちる。</b>
	 * </p>
	 *
	 * <h4>通していないもの</h4>
	 * <p>
	 * <b>{@code equals} / {@code hashCode} / {@code toString} は通さない。</b>
	 * デバッガやログが触るところなので、<b>覗いただけでクエリが飛ぶ</b>のは困る
	 * （{@code AsyncData} はこの3つを「未読み込み」と答える形で自分で持っている）。
	 * </p>
	 */
	protected void beforeAccess () {
	}

	/**
	 * すでに持っている値だけ（{@link #beforeAccess()} を通さない）
	 *
	 * <p>
	 * {@code AsyncData.loadedValues()} が「読み込みを起こさずに中を見る」ために使う。
	 * </p>
	 *
	 * @return	値
	 */
	protected final Collection<Object> rawValues () {

		/*
		 * <b>{@code super.values()} は使えない。</b>
		 * {@code LinkedHashMap.values()} は<b>中で {@code sequencedValues()} を呼ぶ</b>ので、
		 * こちらが上書きしたほうへ戻ってきて<b>口を通ってしまう</b>
		 * （読み込みを起こさないつもりの口が、読み込みを起こす）。
		 *
		 * {@code forEach} は連結リストを自分で辿るだけで、
		 * <b>ビューを1つも作らない</b>ので、ここから出ない。
		 */
		List<Object> values = new ArrayList<>(super.size());

		super.forEach((key, value) -> values.add(value));

		return values;

	}

	@Override
	public int size () {

		beforeAccess();
		return super.size();

	}

	@Override
	public boolean isEmpty () {

		beforeAccess();
		return super.isEmpty();

	}

	@Override
	public boolean containsKey (Object key) {

		beforeAccess();
		return super.containsKey(key);

	}

	@Override
	public boolean containsValue (Object value) {

		beforeAccess();
		return super.containsValue(value);

	}

	@Override
	public Object get (Object key) {

		beforeAccess();
		return super.get(key);

	}

	@Override
	public Object put (String key, Object value) {

		beforeAccess();
		return super.put(key, value);

	}

	@Override
	public Object remove (Object key) {

		beforeAccess();
		return super.remove(key);

	}

	@Override
	public void putAll (Map<? extends String, ? extends Object> map) {

		beforeAccess();
		super.putAll(map);

	}

	@Override
	public void clear () {

		beforeAccess();
		super.clear();

	}

	@Override
	public Set<String> keySet () {

		beforeAccess();
		return super.keySet();

	}

	@Override
	public Collection<Object> values () {

		beforeAccess();
		return super.values();

	}

	@Override
	public Set<Map.Entry<String, Object>> entrySet () {

		beforeAccess();
		return super.entrySet();

	}

	@Override
	public Object getOrDefault (Object key, Object defaultValue) {

		beforeAccess();
		return super.getOrDefault(key, defaultValue);

	}

	@Override
	public void forEach (BiConsumer<? super String, ? super Object> action) {

		beforeAccess();
		super.forEach(action);

	}

	@Override
	public void replaceAll (BiFunction<? super String, ? super Object, ? extends Object> function) {

		beforeAccess();
		super.replaceAll(function);

	}

	@Override
	public Object putIfAbsent (String key, Object value) {

		beforeAccess();
		return super.putIfAbsent(key, value);

	}

	@Override
	public boolean remove (Object key, Object value) {

		beforeAccess();
		return super.remove(key, value);

	}

	@Override
	public boolean replace (String key, Object oldValue, Object newValue) {

		beforeAccess();
		return super.replace(key, oldValue, newValue);

	}

	@Override
	public Object replace (String key, Object value) {

		beforeAccess();
		return super.replace(key, value);

	}

	@Override
	public Object computeIfAbsent (String key, Function<? super String, ? extends Object> mappingFunction) {

		beforeAccess();
		return super.computeIfAbsent(key, mappingFunction);

	}

	@Override
	public Object computeIfPresent (String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {

		beforeAccess();
		return super.computeIfPresent(key, remappingFunction);

	}

	@Override
	public Object compute (String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {

		beforeAccess();
		return super.compute(key, remappingFunction);

	}

	@Override
	public Object merge (String key, Object value, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {

		beforeAccess();
		return super.merge(key, value, remappingFunction);

	}

	@Override
	public Object putFirst (String key, Object value) {

		beforeAccess();
		return super.putFirst(key, value);

	}

	@Override
	public Object putLast (String key, Object value) {

		beforeAccess();
		return super.putLast(key, value);

	}

	@Override
	public Map.Entry<String, Object> firstEntry () {

		beforeAccess();
		return super.firstEntry();

	}

	@Override
	public Map.Entry<String, Object> lastEntry () {

		beforeAccess();
		return super.lastEntry();

	}

	@Override
	public Map.Entry<String, Object> pollFirstEntry () {

		beforeAccess();
		return super.pollFirstEntry();

	}

	@Override
	public Map.Entry<String, Object> pollLastEntry () {

		beforeAccess();
		return super.pollLastEntry();

	}

	@Override
	public SequencedMap<String, Object> reversed () {

		beforeAccess();
		return super.reversed();

	}

	@Override
	public SequencedSet<String> sequencedKeySet () {

		beforeAccess();
		return super.sequencedKeySet();

	}

	@Override
	public SequencedCollection<Object> sequencedValues () {

		beforeAccess();
		return super.sequencedValues();

	}

	@Override
	public SequencedSet<Map.Entry<String, Object>> sequencedEntrySet () {

		beforeAccess();
		return super.sequencedEntrySet();

	}

	@Override
	public Object clone () {

		beforeAccess();
		return super.clone();

	}

	// endregion

}
