package io.jimble.util.convertor;

import io.jimble.util.internal.convertor.array.ArrayConvertor;
import io.jimble.util.internal.convertor.data.DataConvertor;
import io.jimble.util.internal.convertor.io.FileConvertor;
import io.jimble.util.internal.convertor.lang.*;
import io.jimble.util.internal.convertor.lang.exception.ExceptionConvertor;
import io.jimble.util.internal.convertor.math.BigDecimalConvertor;
import io.jimble.util.internal.convertor.math.BigIntegerConvertor;
import io.jimble.util.internal.convertor.net.InetAddressConvertor;
import io.jimble.util.internal.convertor.net.URIConvertor;
import io.jimble.util.internal.convertor.net.URLConvertor;
import io.jimble.util.internal.convertor.nio.CharsetConvertor;
import io.jimble.util.internal.convertor.org.w3c.dom.DocumentConvertor;
import io.jimble.util.internal.convertor.sql.SqlDateConvertor;
import io.jimble.util.internal.convertor.sql.SqlTimestampConvertor;
import io.jimble.util.internal.convertor.time.InstantConvertor;
import io.jimble.util.internal.convertor.util.*;
import io.jimble.util.internal.convertor.util.regex.PatternConvertor;
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
	 * 例) {@code Map<String, Map<String, Integer>>} → {@code new Class<?>[]{ Map.class, String.class, Map.class, String.class, Integer.class }}
	 *
	 * @param conf        設定情報
	 * @param src         変換前オブジェクト
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @return 変換後オブジェクト
	 * @throws RuntimeException 変換できなかったとき（2.0。1.x は検査例外の Exception。要件 D-197）
	 */
	public static <T> T convert (Configration conf, Object src, Class<?>... destClasses) {

		return convert(conf, src, true, destClasses);
	}

	/**
	 * オブジェクトを変換する.<br>
	 * 型パラメータを指定する場合は必ず全ての型パラメータを指定してください.<br>
	 * 例) {@code Map<String, Map<String, Integer>>} → {@code new Class<?>[]{ Map.class, String.class, Map.class, String.class, Integer.class }}
	 *
	 * @param conf        設定情報
	 * @param src         変換前オブジェクト
	 * @param useCustom   カスタム変換オブジェクト使用判定
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=型パラメータ)
	 * @return 変換後オブジェクト
	 * @throws RuntimeException 変換できなかったとき（2.0。1.x は検査例外の Exception。要件 D-197）
	 */
	@SuppressWarnings("unchecked")
	public static <T> T convert (Configration conf, Object src, boolean useCustom, Class<?>... destClasses) {

		if (src == null) {
			return null;
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

		/*
		 * <b>変換は jimble-converter に任せる</b>（D-307）。古い変換器は、古い設定・足した変換器・XML のときだけ使う。
		 * 作り直す前は、変換できない値を黙って null や 0 にし、setter を呼ばず、日付を 31 通り総当たりしていた
		 */
		if (!usesLegacy(conf, useCustom, src, destClasses)) {
			return (T) convertWithConverter(conf, src, destClasses);
		}

		if (conf == null) {
			conf = new Configration();
		}

		try {
			return (T) getConvertor(conf, useCustom, destClasses[0]).convert(conf, src, destClasses);
		} catch (Exception ex) {
			// 検査例外は包む（2.0。1.x は throws Exception で、呼ぶ側に毎回 catch を書かせていた。要件 D-197）
			throw io.jimble.util.internal.Unchecked.of("CONVERT_001", "変換できませんでした", ex);
		}
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
			&& (conf == null || conf.isOutputUnknown())) {
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
		CUSTOMIZED.add(clz);

	}

	/* addConvertor で変換器を足した型（その型への変換は、古い変換器で行う） */
	private static final Set<Class<?>> CUSTOMIZED = ConcurrentHashMap.newKeySet();

	/** jimble-converter で変換するときの、既定の変換のしかた（循環参照は null。作り直す前と同じ） */
	private static final io.jimble.converter.ConverterOptions OPTIONS = io.jimble.converter.ConverterOptions.builder()
		.cycleToNull(true)
		.mapFactory(Data::new)
		.build();

	/**
	 * 古い変換器で変換するか
	 */
	private static boolean usesLegacy (Configration conf, boolean useCustom, Object src, Class<?>[] destClasses) {

		Class<?> dest = destClasses[0];

		if (CUSTOMIZED.contains(dest)) {
			return true;
		}

		// XML（Document）は jimble-converter に無い
		if (dest == Document.class || src instanceof Document) {
			return true;
		}

		// 型が合っている List / Map は写しを返す（作り直す前のまま。返した値を書き換えても元が変わらないことに頼るコードがある）
		if (destClasses.length == 1 && dest.isInstance(src) && (src instanceof List<?> || src instanceof Map<?, ?>)) {
			return true;
		}

		// Map・一覧 → 文字は、作り直す前のまま（Data は JSON、ほかは先頭の値）
		if ((dest == String.class || dest == CharSequence.class)
			&& (src instanceof Map<?, ?> || src instanceof Collection<?> || src != null && src.getClass().isArray() && !(src instanceof char[]))) {
			return true;
		}

		if (conf == null) {
			return false;
		}

		// 日付の書式を設定で渡しているとき
		if (conf.get(ConvertorConfigKeys.CONVERT_STRING_TO_DATE) != null || conf.get(ConvertorConfigKeys.CONVERT_DATE_TO_STRING) != null) {
			return true;
		}

		// 型ごとの変換器を設定で渡しているとき
		if (useCustom) {
			for (Object key : conf.keys()) {
				if (key instanceof Class<?> && conf.get(key) instanceof IConvertor<?>) {
					return true;
				}
			}
		}

		return false;

	}

	/**
	 * jimble-converter で変換する
	 */
	private static Object convertWithConverter (Configration conf, Object src, Class<?>[] destClasses) {

		Class<?> dest = destClasses[0];

		// 文字の JSON は、Data / List<Data> に読む（Data の getData / getDataList がこれに頼っている）
		if (src instanceof CharSequence text && destClasses.length == 1 && PropertyUtil.isAssignableFrom(Data.class, dest)) {
			return io.jimble.util.json.Dson.decodes(text.toString(), dest);
		}

		// byte[]（UTF-8 の JSON）→ Data も、JSON として読む（作り直す前は、各 byte が 0..n のキーで混ざっていた）
		if (src instanceof byte[] bytes && destClasses.length == 1 && PropertyUtil.isAssignableFrom(Data.class, dest)) {
			return io.jimble.util.json.Dson.decodes(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), dest);
		}

		// 文字 → byte[] は UTF-8 のバイト（作り直す前は [0] になっていた）
		if (src instanceof CharSequence text && dest == byte[].class) {
			return text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}

		// 文字の JSON の配列は、読んでから一覧・配列にする（Data の getIntList("[1,2]") など）
		if (src instanceof CharSequence text && (Collection.class.isAssignableFrom(dest) || dest.isArray() && dest != char[].class)
			&& text.toString().strip().startsWith("[")) {
			src = io.jimble.util.json.Dson.decodes(text.toString(), List.class);
		}

		// 空の配列 → Map は、空の Map（オブジェクトが空のときに [] を返す API がある。PHP の json_encode など）
		if (src instanceof Collection<?> collection && collection.isEmpty() && Map.class.isAssignableFrom(dest)) {
			src = new LinkedHashMap<String, Object>();
		}

		// Map → 一覧・配列は、値の一覧（作り直す前のまま。getDataOptional で入った空の Data を getStringList で読むなど）
		if (src instanceof Map<?, ?> map && (Collection.class.isAssignableFrom(dest) || dest.isArray())) {
			src = new ArrayList<>(map.values());
		}

		io.jimble.converter.ConverterOptions options = OPTIONS;
		if (conf != null && conf.get(ConvertorConfigKeys.CONVERT_FIELDNAME_DELIMITER) instanceof char[]) {
			options = OPTIONS.toBuilder().snakeCaseKeys(true).build();
		}

		int[] index = {0};
		java.lang.reflect.Type type = toType(destClasses, index);

		return io.jimble.converter.Converter.convert(src, type, options);

	}

	/**
	 * 平らに並べた型（{@code Map, String, List, Integer}）を、型の木（{@code Map<String, List<Integer>>}）に戻す
	 */
	private static java.lang.reflect.Type toType (Class<?>[] classes, int[] index) {

		Class<?> raw = classes[index[0]++];

		int arguments = Map.class.isAssignableFrom(raw) ? 2 : Collection.class.isAssignableFrom(raw) ? 1 : 0;
		if (arguments == 0 || index[0] >= classes.length) {
			return raw;
		}

		java.lang.reflect.Type[] args = new java.lang.reflect.Type[arguments];
		for (int i = 0; i < arguments; i++) {
			args[i] = index[0] < classes.length ? toType(classes, index) : Object.class;
		}

		return new FlatType(raw, args);

	}

	/**
	 * 型の木
	 */
	private record FlatType (Class<?> rawType, java.lang.reflect.Type[] arguments) implements java.lang.reflect.ParameterizedType {

		@Override
		public java.lang.reflect.Type[] getActualTypeArguments () {
			return arguments.clone();
		}

		@Override
		public java.lang.reflect.Type getRawType () {
			return rawType;
		}

		@Override
		public java.lang.reflect.Type getOwnerType () {
			return null;
		}

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
