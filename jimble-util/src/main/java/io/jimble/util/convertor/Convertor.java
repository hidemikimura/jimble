package io.jimble.util.convertor;

import io.jimble.util.convertor.array.ArrayConvertor;
import io.jimble.util.convertor.data.DataConvertor;
import io.jimble.util.convertor.io.FileConvertor;
import io.jimble.util.convertor.lang.*;
import io.jimble.util.convertor.lang.exception.ExceptionConvertor;
import io.jimble.util.convertor.math.BigDecimalConvertor;
import io.jimble.util.convertor.math.BigIntegerConvertor;
import io.jimble.util.convertor.net.InetAddressConvertor;
import io.jimble.util.convertor.net.URIConvertor;
import io.jimble.util.convertor.net.URLConvertor;
import io.jimble.util.convertor.nio.CharsetConvertor;
import io.jimble.util.convertor.org.w3c.dom.DocumentConvertor;
import io.jimble.util.convertor.sql.SqlDateConvertor;
import io.jimble.util.convertor.sql.SqlTimestampConvertor;
import io.jimble.util.convertor.time.InstantConvertor;
import io.jimble.util.convertor.util.*;
import io.jimble.util.convertor.util.regex.PatternConvertor;
import io.jimble.util.data.Data;
import org.w3c.dom.Document;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 変換クラス.
 *
 * @author DN
 */
public final class Convertor {

	/**
	 * オブジェクトを変換する.<br>
	 * 型パラメータを指定する場合は必ず全ての型パラメータを指定してください.<br>
	 * 例) Map<String, Map<String, Integer>> → new Class< ? >[]{ Map.class, String.class, Map.class, String.class, Integer.class }
	 *
	 * @param conf        設定情報
	 * @param src         変換前オブジェクト
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @return 変換後オブジェクト
	 * @throws Exception 例外
	 */
	public static <T> T convert (Configration conf, Object src, Class<?>... destClasses) throws Exception {

		return convert(conf, src, true, destClasses);
	}

	/**
	 * オブジェクトを変換する.<br>
	 * 型パラメータを指定する場合は必ず全ての型パラメータを指定してください.<br>
	 * 例) Map<String, Map<String, Integer>> → new Class< ? >[]{ Map.class, String.class, Map.class, String.class, Integer.class }
	 *
	 * @param conf        設定情報
	 * @param src         変換前オブジェクト
	 * @param useCustom   カスタム変換オブジェクト使用判定
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @return 変換後オブジェクト
	 * @throws Exception 例外
	 */
	@SuppressWarnings("unchecked")
	public static <T> T convert (Configration conf, Object src, boolean useCustom, Class<?>... destClasses) throws Exception {

		if (src == null) {
			return null;
		}

		if (conf == null) {
			conf = new Configration();
		}

		if (destClasses == null || destClasses.length == 0) {
			destClasses = new Class<?>[]{Object.class};
		}

		if (destClasses.length == 1 && PropertyUtil.isAssignableFrom(destClasses[0], src.getClass())) {
			if (!PropertyUtil.isAssignableFrom(List.class, src.getClass())
				&& !PropertyUtil.isAssignableFrom(Map.class, src.getClass())) {
				return (T) src;
			}
		}

		return (T) getConvertor(conf, useCustom, destClasses[0]).convert(conf, src, destClasses);
	}

	/**
	 * 変換インスタンスを取得する.
	 *
	 * @param conf      設定情報
	 * @param useCustom カスタム変換オブジェクト使用判定
	 * @param cls       変換希望クラス
	 * @return 変換インスタンス
	 */
	public static IConvertor<?> getConvertor (Configration conf, boolean useCustom, Class<?> cls) {

		IConvertor<?> convertor = CONVERTOR_MAP.get(cls);

		if (useCustom && conf != null) {
			Object confObj = null;
			if ((confObj = conf.get(cls)) instanceof IConvertor<?>) {
				convertor = (IConvertor<?>) confObj;
			}
		}

		if (convertor == null && cls.isRecord()) {
			convertor = RecordConvertor.INSTANCE;
		}

		if (convertor == null && cls.isEnum()) {
			convertor = EnumConvertor.INSTANCE;
		}

		if (convertor == null && PropertyUtil.isAssignableFrom(Data.class, cls)) {
			convertor = DataConvertor.INSTANCE;
		}

		if (convertor == null) {
			convertor = PropertyUtil.getMapInstanceClassCache(cls);
		}

		if (convertor == null) {
			convertor = PropertyUtil.getListInstanceClassCache(cls);
		}

		if (convertor == null
			&& (conf == null || conf.isOutputUnknown)) {
			convertor = BeanConvertor.INSTANCE;
		}

		if (convertor == null) {
			convertor = EmptyConvertor.INSTANCE;
		}

		return convertor;

	}

	/**
	 * 変換定義を追加する.
	 *
	 * @param clz       変換対象クラス
	 * @param convertor 変換処理オブジェクト
	 */
	public static void addConvertor (Class<?> clz, IConvertor<?> convertor) {

		CONVERTOR_MAP.put(clz, convertor);

	}

	/**
	 * 変換クラスマップ.
	 */
	private static final Map<Class<?>, IConvertor<?>> CONVERTOR_MAP = new HashMap<>();
	static {
		// array
		CONVERTOR_MAP.put(Byte[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(byte[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Short[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(short[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Integer[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(int[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Long[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(long[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Float[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(float[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Double[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(double[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Character[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(char[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(String[].class, ArrayConvertor.INSTANCE);
		CONVERTOR_MAP.put(Object[].class, ArrayConvertor.INSTANCE);

		// java.io
		CONVERTOR_MAP.put(File.class, FileConvertor.INSTANCE);

		// java.lang
		CONVERTOR_MAP.put(Number.class, BigDecimalConvertor.INSTANCE);
		CONVERTOR_MAP.put(Byte.class, ByteConvertor.INSTANCE);
		CONVERTOR_MAP.put(byte.class, ByteConvertor.INSTANCE);
		CONVERTOR_MAP.put(Short.class, ShortConvertor.INSTANCE);
		CONVERTOR_MAP.put(short.class, ShortConvertor.INSTANCE);
		CONVERTOR_MAP.put(Integer.class, IntegerConvertor.INSTANCE);
		CONVERTOR_MAP.put(int.class, IntegerConvertor.INSTANCE);
		CONVERTOR_MAP.put(Long.class, LongConvertor.INSTANCE);
		CONVERTOR_MAP.put(long.class, LongConvertor.INSTANCE);
		CONVERTOR_MAP.put(Float.class, FloatConvertor.INSTANCE);
		CONVERTOR_MAP.put(float.class, FloatConvertor.INSTANCE);
		CONVERTOR_MAP.put(Double.class, DoubleConvertor.INSTANCE);
		CONVERTOR_MAP.put(double.class, DoubleConvertor.INSTANCE);
		CONVERTOR_MAP.put(Boolean.class, BooleanConvertor.INSTANCE);
		CONVERTOR_MAP.put(boolean.class, BooleanConvertor.INSTANCE);
		CONVERTOR_MAP.put(Character.class, CharacterConvertor.INSTANCE);
		CONVERTOR_MAP.put(char.class, CharacterConvertor.INSTANCE);
		CONVERTOR_MAP.put(String.class, StringConvertor.INSTANCE);
		CONVERTOR_MAP.put(StringBuffer.class, StringBufferConvertor.INSTANCE);
		CONVERTOR_MAP.put(StringBuilder.class, StringBuilderConvertor.INSTANCE);
		CONVERTOR_MAP.put(Object.class, ObjectConvertor.INSTANCE);
		CONVERTOR_MAP.put(Iterable.class, ArrayListConvertor.INSTANCE);

		CONVERTOR_MAP.put(Exception.class, ExceptionConvertor.INSTANCE);

		CONVERTOR_MAP.put(Class.class, ClassConvertor.INSTANCE);

		// java.math
		CONVERTOR_MAP.put(BigDecimal.class, BigDecimalConvertor.INSTANCE);
		CONVERTOR_MAP.put(BigInteger.class, BigIntegerConvertor.INSTANCE);

		// java.net
		CONVERTOR_MAP.put(InetAddress.class, InetAddressConvertor.INSTANCE);
		CONVERTOR_MAP.put(URI.class, URIConvertor.INSTANCE);
		CONVERTOR_MAP.put(URL.class, URLConvertor.INSTANCE);

		// java.nio
		CONVERTOR_MAP.put(Charset.class, CharsetConvertor.INSTANCE);

		// java.sql
		CONVERTOR_MAP.put(java.sql.Date.class, SqlDateConvertor.INSTANCE);
		CONVERTOR_MAP.put(java.sql.Timestamp.class, SqlTimestampConvertor.INSTANCE);

		// java.util
		CONVERTOR_MAP.put(Calendar.class, CalendarConvertor.INSTANCE);
		CONVERTOR_MAP.put(Date.class, DateConvertor.INSTANCE);

		CONVERTOR_MAP.put(Collection.class, ArrayListConvertor.INSTANCE);
		CONVERTOR_MAP.put(List.class, ArrayListConvertor.INSTANCE);
		CONVERTOR_MAP.put(ArrayList.class, ArrayListConvertor.INSTANCE);
		CONVERTOR_MAP.put(LinkedList.class, LinkedListConvertor.INSTANCE);
		CONVERTOR_MAP.put(Stack.class, StackConvertor.INSTANCE);
		CONVERTOR_MAP.put(Vector.class, VectorConvertor.INSTANCE);

		CONVERTOR_MAP.put(Set.class, LinkedHashSetConvertor.INSTANCE);
		CONVERTOR_MAP.put(HashSet.class, HashSetConvertor.INSTANCE);
		CONVERTOR_MAP.put(SortedSet.class, TreeSetConvertor.INSTANCE);
		CONVERTOR_MAP.put(TreeSet.class, TreeSetConvertor.INSTANCE);
		CONVERTOR_MAP.put(LinkedHashSet.class, LinkedHashSetConvertor.INSTANCE);

		CONVERTOR_MAP.put(Queue.class, LinkedBlockingQueueConvertor.INSTANCE);
		CONVERTOR_MAP.put(LinkedBlockingQueue.class, LinkedBlockingQueueConvertor.INSTANCE);
		CONVERTOR_MAP.put(ConcurrentLinkedQueue.class, ConcurrentLinkedQueueConvertor.INSTANCE);
		CONVERTOR_MAP.put(PriorityBlockingQueue.class, PriorityBlockingQueueConvertor.INSTANCE);
		CONVERTOR_MAP.put(PriorityQueue.class, PriorityQueueConvertor.INSTANCE);

		CONVERTOR_MAP.put(Map.class, LinkedHashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(AbstractMap.class, HashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(HashMap.class, HashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(LinkedHashMap.class, LinkedHashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(SortedMap.class, TreeMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(TreeMap.class, TreeMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(WeakHashMap.class, WeakHashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(ConcurrentMap.class, ConcurrentHashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(ConcurrentHashMap.class, ConcurrentHashMapConvertor.INSTANCE);
		CONVERTOR_MAP.put(Hashtable.class, HashtableConvertor.INSTANCE);
		CONVERTOR_MAP.put(Properties.class, PropertiesConvertor.INSTANCE);

		// java.time.Instant
		CONVERTOR_MAP.put(Instant.class, InstantConvertor.INSTANCE);

		// java.util.regex
		CONVERTOR_MAP.put(Pattern.class, PatternConvertor.INSTANCE);

		// org.w3c.dom
		CONVERTOR_MAP.put(Document.class, DocumentConvertor.INSTANCE);

		// original
		CONVERTOR_MAP.put(Data.class, DataConvertor.INSTANCE);

		// NOTE CONVERTORを充実させる


		// Jooby用

	}

}
