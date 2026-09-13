package io.jimble.util.internal.json.formatter;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.formatter.array.ArrayFormatter;
import io.jimble.util.internal.json.formatter.lang.*;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;
import io.jimble.util.internal.json.formatter.time.LocalDateTimeFormatter;
import io.jimble.util.internal.json.formatter.util.*;
import io.jimble.util.data.Data;

import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * JSON文字列作成クラス.
 *
 * @author DN
 */
public final class Formatter {

	/**
	 * オブジェクトをJSON文字列に変換する.
	 *
	 * @param src オブジェクト
	 * @return JSON文字列
	 * @throws Exception 例外
	 */
	public static String format (Object src) throws Exception {

		return format(src, new Configration());
	}

	/**
	 * オブジェクトをJSON文字列に変換する.
	 *
	 * @param src  オブジェクト
	 * @param conf 設定情報
	 * @return JSON文字列
	 * @throws Exception 例外
	 */
	public static String format (Object src, Configration conf) throws Exception {

		return format(src, true, conf);
	}

	/**
	 * オブジェクトをJSON文字列に変換する.
	 *
	 * @param src       オブジェクト
	 * @param useCustom カスタムフォーマットオブジェクト使用判定
	 * @param conf      設定情報
	 * @return JSON文字列
	 * @throws Exception 例外
	 */
	public static String format (Object src, boolean useCustom, Configration conf) throws Exception {

		if (src == null) {
			return "";
		}

		try (
			OutputStreamWriterWrapper writer = new OutputStreamWriterWrapper(new StringWriter())
		) {
			format(writer, src, useCustom, conf);
			return writer.toString();
		} catch (Exception ex) {
			throw ex;
		}

	}

	/**
	 * オブジェクトをJSON文字列に変換し出力する.
	 *
	 * @param stream  出力先
	 * @param charset 文字コード
	 * @param src     オブジェクト
	 * @param conf    設定情報
	 * @throws Exception Exception 例外
	 */
	public static void format (OutputStream stream, Charset charset, Object src, Configration conf) throws Exception {

		format(stream, charset, src, true, conf);
	}

	/**
	 * オブジェクトをJSON文字列に変換し出力する.
	 *
	 * @param stream    出力先
	 * @param charset   文字コード
	 * @param src       オブジェクト
	 * @param useCustom カスタムフォーマットオブジェクト使用判定
	 * @param conf      設定情報
	 * @throws Exception Exception 例外
	 */
	public static void format (OutputStream stream, Charset charset, Object src, boolean useCustom, Configration conf) throws Exception {

		if (src == null) {
			return;
		}

		boolean isAutoClose = true;
		if (conf != null) {
			isAutoClose = conf.isAutoClose();
		}

		try (
			OutputStreamWriterWrapper writer = new OutputStreamWriterWrapper(stream, charset).setAutoClose(isAutoClose)
		) {
			format(writer, src, useCustom, conf);
		} catch (Exception ex) {
		}

	}

	/**
	 * オブジェクトをJSON文字列に変換し出力する.
	 *
	 * @param writer 出力先
	 * @param src    オブジェクト
	 * @param conf   設定情報
	 * @throws Exception Exception 例外
	 */
	public static void format (Writer writer, Object src, Configration conf) throws Exception {

		format(writer, src, true, conf);
	}

	/**
	 * オブジェクトをJSON文字列に変換し出力する.
	 *
	 * @param wr        出力先
	 * @param src       オブジェクト
	 * @param useCustom カスタムフォーマットオブジェクト使用判定
	 * @param conf      設定情報
	 * @throws Exception Exception 例外
	 */
	public static void format (Writer wr, Object src, boolean useCustom, Configration conf) throws Exception {

		if (src == null) {
			return;
		}

		boolean isAutoClose = true;
		if (conf != null) {
			isAutoClose = conf.isAutoClose();
		}

		try (
			OutputStreamWriterWrapper writer = new OutputStreamWriterWrapper(wr).setAutoClose(isAutoClose)
		) {
			format(writer, src, useCustom, conf);
		} catch (Exception ex) {
		}

	}

	/**
	 * オブジェクトをJSON文字列に変換し出力する.
	 *
	 * @param writer 出力ラッパ
	 * @param src    オブジェクト
	 * @param conf   設定情報
	 * @return 使用フォーマッタ
	 * @throws Exception Exception 例外
	 */
	public static IFormatter format (OutputStreamWriterWrapper writer, Object src, Configration conf) throws Exception {

		return format(writer, src, true, conf);
	}

	/**
	 * オブジェクトをJSON文字列に変換し出力する.
	 *
	 * @param writer    出力ラッパ
	 * @param src       オブジェクト
	 * @param useCustom カスタムフォーマットオブジェクト使用判定
	 * @param conf      設定情報
	 * @return 使用フォーマッタ
	 * @throws Exception Exception 例外
	 */
	public static IFormatter format (OutputStreamWriterWrapper writer, Object src, boolean useCustom, Configration conf) throws Exception {

		if (conf == null) {
			conf = new Configration();
		} else {
			conf.clearHashSet();
		}

		conf.isClearHashSet(false);

		if (src == null) {
			if (conf.isOutputNullValue()) {
				NullFormatter.INSTANCE.format(writer, conf, null);
			}
			return null;
		}

		// 文字列にするオブジェクトのクラスを取得する
		Class<?> cls = src.getClass();

		// デフォルトのフォーマッタを取得する
		IFormatter formatter = FORMATTER_MAP.get(cls);
		if (formatter == null && src instanceof Map) {
			formatter = MapFormatter.INSTANCE;
		}

		if (formatter == null && Throwable.class.isAssignableFrom(cls)) {
			formatter = FORMATTER_MAP.get(Throwable.class);
		}

		if (useCustom && conf != null) {
			// カスタムフォーマッタを使用する場合
			Object confObj = null;
			if ((confObj = conf.get(cls)) instanceof IFormatter) {
				// カスタムフォーマッタを取得する
				formatter = (IFormatter) confObj;
			}
		}

		if (formatter == null && cls.isEnum()) {
			// Enumの場合
			formatter = EnumFormatter.INSTANCE;
		}

		if (formatter == null) {
			// Beanの場合
			formatter = BeanFormatter.INSTANCE;
		}

		formatter.format(writer, conf, src);

		return formatter;

	}

	/**
	 * フォーマットクラスマップ.
	 */
	private static final Map<Class<?>, IFormatter> FORMATTER_MAP = new HashMap<>();
	static {
		// array
		FORMATTER_MAP.put(Byte[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(byte[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Short[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(short[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Integer[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(int[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Long[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(long[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Float[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(float[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Double[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(double[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Character[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(char[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(String[].class, ArrayFormatter.INSTANCE);
		FORMATTER_MAP.put(Object[].class, ArrayFormatter.INSTANCE);

		// java.io
		FORMATTER_MAP.put(File.class, StringFormatter.INSTANCE);

		// java.lang
		FORMATTER_MAP.put(Number.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Byte.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(byte.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Short.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(short.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Integer.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(int.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Long.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(long.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Float.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(float.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Double.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(double.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(Boolean.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(boolean.class, NumberFormatter.INSTANCE);

		FORMATTER_MAP.put(Character.class, StringFormatter.INSTANCE);
		FORMATTER_MAP.put(char.class, StringFormatter.INSTANCE);
		FORMATTER_MAP.put(String.class, StringFormatter.INSTANCE);
		FORMATTER_MAP.put(StringBuffer.class, StringFormatter.INSTANCE);
		FORMATTER_MAP.put(StringBuilder.class, StringFormatter.INSTANCE);

		FORMATTER_MAP.put(Iterable.class, IterableFormatter.INSTANCE);

		FORMATTER_MAP.put(Class.class, StringFormatter.INSTANCE);

		FORMATTER_MAP.put(Throwable.class, ThrowableFormatter.INSTANCE);

		// java.math
		FORMATTER_MAP.put(BigDecimal.class, NumberFormatter.INSTANCE);
		FORMATTER_MAP.put(BigInteger.class, NumberFormatter.INSTANCE);

		// java.net
		FORMATTER_MAP.put(InetAddress.class, StringFormatter.INSTANCE);
		FORMATTER_MAP.put(URI.class, StringFormatter.INSTANCE);
		FORMATTER_MAP.put(URL.class, StringFormatter.INSTANCE);

		// java.nio
		FORMATTER_MAP.put(Charset.class, StringFormatter.INSTANCE);

		// java.sql
		FORMATTER_MAP.put(java.sql.Date.class, DateFormatter.INSTANCE);
		FORMATTER_MAP.put(java.sql.Timestamp.class, DateFormatter.INSTANCE);

		// java.util
		FORMATTER_MAP.put(Calendar.class, DateFormatter.INSTANCE);
		FORMATTER_MAP.put(Date.class, DateFormatter.INSTANCE);

		FORMATTER_MAP.put(Collection.class, IterableFormatter.INSTANCE);

		FORMATTER_MAP.put(List.class, ListFormatter.INSTANCE);
		FORMATTER_MAP.put(ArrayList.class, ListFormatter.INSTANCE);
		FORMATTER_MAP.put(LinkedList.class, ListFormatter.INSTANCE);
		FORMATTER_MAP.put(Stack.class, ListFormatter.INSTANCE);
		FORMATTER_MAP.put(Vector.class, ListFormatter.INSTANCE);

		FORMATTER_MAP.put(Set.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(HashSet.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(SortedSet.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(TreeSet.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(LinkedHashSet.class, IterableFormatter.INSTANCE);

		FORMATTER_MAP.put(Queue.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(LinkedBlockingQueue.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(ConcurrentLinkedQueue.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(PriorityBlockingQueue.class, IterableFormatter.INSTANCE);
		FORMATTER_MAP.put(PriorityQueue.class, IterableFormatter.INSTANCE);

		FORMATTER_MAP.put(Map.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(AbstractMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(HashMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(LinkedHashMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(SortedMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(TreeMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(WeakHashMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(ConcurrentMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(ConcurrentHashMap.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(Hashtable.class, MapFormatter.INSTANCE);
		FORMATTER_MAP.put(Properties.class, MapFormatter.INSTANCE);

		FORMATTER_MAP.put(Enumeration.class, EnumerationFormatter.INSTANCE);
		FORMATTER_MAP.put(StringTokenizer.class, EnumerationFormatter.INSTANCE);

		// java.util.regex
		FORMATTER_MAP.put(Pattern.class, StringFormatter.INSTANCE);

		// java.time.LocalDateTime
		FORMATTER_MAP.put(LocalDateTime.class, LocalDateTimeFormatter.INSTANCE);

		// Data
		FORMATTER_MAP.put(Data.class, MapFormatter.INSTANCE);

		// NOTE Formatterを充実させる

	}

}
