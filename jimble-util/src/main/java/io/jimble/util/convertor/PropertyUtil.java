package io.jimble.util.convertor;

import io.jimble.util.convertor.util.UserListConvertor;
import io.jimble.util.convertor.util.UserMapConvertor;
import io.jimble.util.data.Data;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * プロパティユーティリティクラス.
 *
 * @author DN
 */
public final class PropertyUtil {

	// region destがsrcに変換可能か判定する

	private static final Map<Class<?>, Map<Class<?>, Boolean>> isAssignableFromMap = new ConcurrentHashMap<>();

	/**
	 * destがsrcに変換可能か判定する.
	 *
	 * @param src  Class
	 * @param dest Class
	 * @return 変換可能な場合 true
	 */
	public static boolean isAssignableFrom (Class<?> src, Class<?> dest) {

		return src.isAssignableFrom(dest);

//		if (isAssignableFromMap.containsKey(src)) {
//			if (isAssignableFromMap.get(src).containsKey(dest)) {
//				return isAssignableFromMap.get(src).get(dest);
//			}
//		}
//
//		if (!isAssignableFromMap.containsKey(src)) {
//			isAssignableFromMap.put(src, new ConcurrentHashMap<>());
//		}
//		boolean res = src.isAssignableFrom(dest);
//		isAssignableFromMap.get(src).put(dest, res);
//
//		return res;

	}

	// endregion

	// region オブジェクトの文字列表現を取得する

	/**
	 * オブジェクトの文字列表現を取得する.
	 *
	 * @param obj オブジェクト
	 * @return 文字列
	 */
	public static String toString (Object obj) {

		if (obj == null) {
			return null;
		}

		if (obj instanceof String) {
			return (String) obj;
		}

//		if (obj instanceof Collection<?>
//			|| obj instanceof Map<?,?>) {
//			return Dson.encodes(obj);
//		}

		if (obj instanceof Data json) {
			return json.getJsonString();
		}

		if (obj instanceof Collection<?>) {

			Collection<?> coll = (Collection<?>) obj;
			Iterator<?> it = coll.iterator();
			if (!it.hasNext()) {
				return null;
			}
			Object o = it.next();
			return toString(o);

		}

		if (obj instanceof Map<?,?>) {

			Map<?, ?> map = (Map<?, ?>) obj;
			if (map.isEmpty()) {
				return null;
			}

			return toString(map.values().iterator().next());

		}

		if (obj.getClass().isArray() && Array.getLength(obj) > 0) {

			obj = Array.get(obj, 0);

			if (obj == null) {
				return null;
			}

		}

		if (obj instanceof byte[]) {
			return new String((byte[]) obj);
		}

		return obj.toString();

	}

	// endregion

	/**
	 * ユーザーマップキャッシュ.
	 */
	private static final Map<Class<?>, IConvertor<?>> USER_MAP_CLASS = new ConcurrentHashMap<>();

	/**
	 * ユーザーマップ.
	 */
	private static final Set<Class<?>> USER_MAP_CLASS_NULL = new CopyOnWriteArraySet<>();

	/**
	 * Mapクラスのインスタンス化可能クラスを取得する.
	 *
	 * @param cls ユーザーマップクラス
	 * @return Mapクラス
	 */
	public static IConvertor<?> getMapInstanceClassCache (Class<?> cls) {

		if (USER_MAP_CLASS_NULL.contains(cls)) {
			return null;
		}

		if (USER_MAP_CLASS.containsKey(cls)) {
			return USER_MAP_CLASS.get(cls);
		}

		Class<?> c = getMapInstanceClass(cls);

		if (c == null) {
			USER_MAP_CLASS_NULL.add(cls);
		} else {
			USER_MAP_CLASS.put(cls, new UserMapConvertor(c));
		}

		return USER_MAP_CLASS.get(cls);

	}

	/**
	 * Mapクラスのインスタンス化可能クラスを取得する.
	 *
	 * @param cls ユーザーマップクラス
	 * @return Mapクラス
	 */
	private static Class<?> getMapInstanceClass (Class<?> cls) {

		if (Map.class.equals(cls)) {
			return HashMap.class;
		}

		if (!isAssignableFrom(Map.class, cls)) {
			return null;
		}

		int mod = cls.getModifiers();
		if (!Modifier.isAbstract(mod) && !Modifier.isInterface(mod)) {
			return cls;
		}

		Class<?> res = getMapInstanceClass(cls.getSuperclass());
		if (res != null) {
			return res;
		}

		Class<?>[] iCls = cls.getInterfaces();
		for (Class<?> c : iCls) {
			res = getMapInstanceClass(c);
			if (res != null) {
				return res;
			}
		}

		return HashMap.class;

	}

	/**
	 * ユーザーリストキャッシュ.
	 */
	private static final Map<Class<?>, IConvertor<?>> USER_LIST_CLASS = new ConcurrentHashMap<>();

	/**
	 * ユーザーマップ.
	 */
	private static final Set<Class<?>> USER_LIST_CLASS_NULL = new CopyOnWriteArraySet<>();

	/**
	 * Listクラスのインスタンス化可能クラスを取得する.
	 *
	 * @param cls ユーザーマップクラス
	 * @return Mapクラス
	 */
	public static IConvertor<?> getListInstanceClassCache (Class<?> cls) {

		if (USER_LIST_CLASS_NULL.contains(cls)) {
			return null;
		}

		if (USER_LIST_CLASS.containsKey(cls)) {
			return USER_LIST_CLASS.get(cls);
		}

		Class<?> c = getListInstanceClass(cls);

		if (c == null) {
			USER_LIST_CLASS_NULL.add(cls);
		} else {
			USER_LIST_CLASS.put(cls, new UserListConvertor(c));
		}

		return USER_LIST_CLASS.get(cls);

	}

	/**
	 * Listクラスのインスタンス化可能クラスを取得する.
	 *
	 * @param cls ユーザーリストクラス
	 * @return Listクラス
	 */
	private static Class<?> getListInstanceClass (Class<?> cls) {

		if (List.class.equals(cls)) {
			return ArrayList.class;
		}

		if (!isAssignableFrom(List.class, cls)) {
			return null;
		}

		int mod = cls.getModifiers();
		if (!Modifier.isAbstract(mod) && !Modifier.isInterface(mod)) {
			return cls;
		}

		Class<?> res = getMapInstanceClass(cls.getSuperclass());
		if (res != null) {
			return res;
		}

		Class<?>[] iCls = cls.getInterfaces();
		for (Class<?> c : iCls) {
			res = getListInstanceClass(c);
			if (res != null) {
				return res;
			}
		}

		return ArrayList.class;

	}

	/**
	 * 単一フィールドキャッシュ.
	 */
	private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

	/**
	 * フィールドなしキャッシュ.
	 */
	private static final Map<Class<?>, Set<String>> NO_FIELD_CACHE = new ConcurrentHashMap<>();

	/**
	 * 単一メソッドキャッシュ.
	 */
	private static final Map<Class<?>, Map<String, SetterMethodInfo>> METHOD_CACHE = new ConcurrentHashMap<>();

	/**
	 * メソッドなしキャッシュ.
	 */
	private static final Map<Class<?>, Set<String>> NO_METHOD_CACHE = new ConcurrentHashMap<>();

	/**
	 * オブジェクトにプロパティを設定する.
	 *
	 * @param conf      設定情報
	 * @param src       オブジェクト
	 * @param fieldName プロパティ名
	 * @param value     値
	 * @throws Exception 例外
	 */
	/*
	 * 無検査キャスト：Map の中身の型までは確かめられない。
	 * <b>キーが String の Map である前提</b>で1つ入れるだけである
	 * （プロパティ名をキーにする、というのがこのメソッドの仕事）。
	 * 違う Map を渡されると put のところで落ちる。
	 */
	@SuppressWarnings("unchecked")
	public static void setProperty (Configration conf, Object src, String fieldName, Object value) throws Exception {

		Class<?> cls = src.getClass();

		if (src instanceof Map<?, ?>) {

			Map<String, Object> bean = (Map<String, Object>) src;
			if (value instanceof Map<?, ?>) {
				try {
					bean.put(fieldName, Convertor.convert(conf, value, Data.class));
				} catch (Exception ex) {
					bean.put(fieldName, value);
				}
			} else if (value instanceof List<?>) {
				try {
					bean.put(fieldName, Convertor.convert(conf, value, ArrayList.class));
				} catch (Exception ex) {
					bean.put(fieldName, value);
				}
			} else {
				bean.put(fieldName, value);
			}
			return;

		}

		if (!NO_FIELD_CACHE.containsKey(cls) || !NO_FIELD_CACHE.get(cls).contains(fieldName)) {

			try {

				if (!FIELD_CACHE.containsKey(cls)) {
					FIELD_CACHE.put(cls, new ConcurrentHashMap<>());
				}

				Map<String, Field> map = FIELD_CACHE.get(cls);

				if (!map.containsKey(fieldName)) {
					try {
						Field f = cls.getField(fieldName);
						f.setAccessible(true);
						map.put(fieldName, f);
					} catch (Exception ex) {
						Field f = cls.getDeclaredField(fieldName);
						f.setAccessible(true);
						map.put(fieldName, f);
					}
				}

				Field f = map.get(fieldName);
				Class<?>[] fieldClasses = getFieldClasses(src.getClass(), f);
				f.set(src, Convertor.convert(conf, value, fieldClasses));

			} catch (NoSuchFieldException | SecurityException e) {

				if (!NO_FIELD_CACHE.containsKey(cls)) {
					NO_FIELD_CACHE.put(cls, new HashSet<>());
				}

				NO_FIELD_CACHE.get(cls).add(fieldName);

			} catch (Exception e) {

				// 後からカラム追加して既存レコードのカラムがnullのときにプリミティブだとnullを受け付けないので IllegalArgumentException になる
				// IllegalArgumentException、IllegalAccessException は Field が存在するので NO_FIELD_CACHE に登録しない

			}

		}

		if (!NO_METHOD_CACHE.containsKey(cls) || !NO_METHOD_CACHE.get(cls).contains(fieldName)) {

			try {

				if (!METHOD_CACHE.containsKey(cls)) {
					METHOD_CACHE.put(cls, new ConcurrentHashMap<>());
				}

				Map<String, SetterMethodInfo> map = METHOD_CACHE.get(cls);

				if (!map.containsKey(fieldName)) {
					String setterName = createSetterName(fieldName);
					boolean isExists = false;
					Method[] methods = cls.getMethods();
					for (Method m : methods) {
						if (setterName.equals(m.getName()) && m.getParameterTypes().length == 1) {
							isExists = true;
							m.setAccessible(true);
							SetterMethodInfo info = new SetterMethodInfo();
							info.setMethod(m);
							info.setParameterClasses(getMethodClasses(cls, m));
							map.put(fieldName, info);
							break;
						}
					}
					if (!isExists) {
						methods = cls.getDeclaredMethods();
						for (Method m : methods) {
							if (setterName.equals(m.getName()) && m.getParameterTypes().length == 1) {
								m.setAccessible(true);
								SetterMethodInfo info = new SetterMethodInfo();
								info.setMethod(m);
								info.setParameterClasses(getMethodClasses(cls, m));
								map.put(fieldName, info);
								break;
							}
						}
					}
				}

				SetterMethodInfo m = map.get(fieldName);

				if (m != null) {
					m.getMethod().invoke(src, Convertor.convert(conf, value, m.getParameterClasses()));
				} else {
					throw new Exception();
				}

			} catch (NoSuchFieldException | SecurityException e) {

				if (!NO_METHOD_CACHE.containsKey(cls)) {
					NO_METHOD_CACHE.put(cls, new HashSet<>());
				}

				NO_METHOD_CACHE.get(cls).add(fieldName);

			} catch (Exception e) {


			}

		}

	}

	/**
	 * 空パラメータ.
	 */
	private static final Constructor<?>[] NULL_PARAM_CONSTRUCTOR = new Constructor<?>[]{};

	/**
	 * クラスコンストラクタマップ.
	 */
	private static final Map<Class<?>, Constructor<?>[]> CONSTRUCTOR_MAP = new ConcurrentHashMap<>();

	/**
	 * コンストラクタキャッシュ.
	 */
	private static final Map<Class<?>, ConstructorInfo> CONSTRUCTOR_INS_NO_MAP = new ConcurrentHashMap<>();

	/**
	 * コンストラクタキャッシュ.
	 */
	private static final Map<Class<?>, ConstructorInfo> CONSTRUCTOR_INS_MAP = new ConcurrentHashMap<>();

	/**
	 * クラスコンストラクタを取得する.
	 *
	 * @param cls クラス
	 * @return クラスコンストラクタ
	 */
	public static Constructor<?>[] getConstructors (Class<?> cls) {

		if (!CONSTRUCTOR_MAP.containsKey(cls)) {
			List<Constructor<?>> list = new ArrayList<>();

			Constructor<?>[] array = cls.getConstructors();
			if (array != null) {
				for (Constructor<?> c : array) {
					list.add(c);
				}
			}
			array = cls.getDeclaredConstructors();
			if (array != null) {
				for (Constructor<?> c : array) {
					list.add(c);
				}
			}
			CONSTRUCTOR_MAP.put(cls, list.toArray(NULL_PARAM_CONSTRUCTOR));
		}

		return CONSTRUCTOR_MAP.get(cls);

	}

	/**
	 * オブジェクトNullパラメータ.
	 */
	private static final Object[] NULL_OBJ_PARAM = new Object[]{};

	/**
	 * インスタンスを作成する.
	 *
	 * @param cls クラス
	 * @return インスタンス
	 */
	public static Object newInstance (Class<?> cls) {

		if (cls.equals(boolean.class)) {
			return false;
		}
		if (cls.equals(byte.class)) {
			return 0;
		}
		if (cls.equals(short.class)) {
			return 0;
		}
		if (cls.equals(int.class)) {
			return 0;
		}
		if (cls.equals(long.class)) {
			return 0;
		}
		if (cls.equals(float.class)) {
			return 0;
		}
		if (cls.equals(double.class)) {
			return 0;
		}

		if (cls.equals(Data.class)) {
			return new Data();
		}

		if ("java.util.Collections$SingletonList".equals(cls.getName())) {
			return new ArrayList<>();
		}

		if ("java.util.Collections$SingletonMap".equals(cls.getName())) {
			return new HashMap<>();
		}

		try {

			if (CONSTRUCTOR_INS_NO_MAP.containsKey(cls)) {
				return CONSTRUCTOR_INS_NO_MAP.get(cls).constructor.newInstance(NULL_OBJ_PARAM);
			} else if (CONSTRUCTOR_INS_MAP.containsKey(cls)) {
				return CONSTRUCTOR_INS_MAP.get(cls).constructor.newInstance(CONSTRUCTOR_INS_MAP.get(cls).params);
			}

			if (cls.isRecord()) {
				try {
					RecordComponent[] recordComponents = cls.getRecordComponents();
					Class<?>[] parameterTypes = new Class<?>[recordComponents.length];
					Object[] params = new Object[recordComponents.length];
					for (int i = 0; i < recordComponents.length; i++) {
						parameterTypes[i] = recordComponents[i].getType();
						params[i] = newInstance(parameterTypes[i]);
					}
					Constructor<?> constructor = cls.getDeclaredConstructor(parameterTypes);
					constructor.setAccessible(true);
					return constructor.newInstance(params);
				} catch (Exception ex) {}
			} else {
				Class<?> dClass = cls.getEnclosingClass();

				Constructor<?>[] constructors = getConstructors(cls);
				for (Constructor<?> constructor : constructors) {
					if (constructor.getParameterTypes().length == 0) {
						try {
							constructor.setAccessible(true);
							Object res = constructor.newInstance(NULL_OBJ_PARAM);
							CONSTRUCTOR_INS_NO_MAP.put(cls, new ConstructorInfo(constructor, null));
							return res;
						} catch (Exception e) {
						}
					} else if (dClass != null && constructor.getParameterTypes().length == 1) {
						try {
							constructor.setAccessible(true);
							Object params = newInstance(dClass);
							Object res = constructor.newInstance(params);
							CONSTRUCTOR_INS_MAP.put(cls, new ConstructorInfo(constructor, params));
							return res;
						} catch (Exception e) {
						}
					} else if (constructor.getParameterTypes().length > 0) {
						try {
							constructor.setAccessible(true);
							Object[] params = new Object[constructor.getParameterTypes().length];
							return constructor.newInstance(params);
						} catch (Exception e) {
						}
					}
				}
			}

		} catch (Exception e) {
		}

		return null;

	}

	/**
	 * フィールド名キャッシュ.
	 */
	private static final Map<String, String> FIELD_NAME = new ConcurrentHashMap<>();

	/**
	 * Field名を取得する.
	 *
	 * @param key 名前
	 * @return Field名
	 */
	public static String createFieldName (String key, Configration conf) {

		if (!FIELD_NAME.containsKey(key)) {

			Object confObj = null;
			if ((confObj = conf.get(ConvertorConfigKeys.CONVERT_FIELDNAME_DELIMITER)) != null && confObj instanceof char[]) {

				char[] delimiters = (char[]) confObj;

				StringBuilder sb = new StringBuilder();

				List<String> keys = split(key, delimiters);

				boolean isUseDelemiter = keys.size() > 1;

				for (int i = 0; i < keys.size(); i++) {
					String k = keys.get(i);
					if (isUseDelemiter) {
						sb.append((i == 0 && sb.length() == 0) ? k.substring(0, 1).toLowerCase() : k.substring(0, 1).toUpperCase());
						sb.append(k.substring(1).toLowerCase());
					} else {
						boolean isExistsUpper = false;
						boolean isExistsLower = false;
						char[] cs = k.toCharArray();
						for (char c : cs) {

							if ('A' <= c && c <= 'Z') {
								isExistsUpper = true;
								if (isExistsLower) {
									break;
								}
							}

							if ('a' <= c && c <= 'z') {
								isExistsLower = true;
								if (isExistsUpper) {
									break;
								}
							}

						}
						if (isExistsLower && isExistsUpper) {

							sb.append(k.substring(0, 1).toLowerCase());
							sb.append(k.substring(1));

						} else {

							sb.append(k.toLowerCase());

						}
					}
				}

				FIELD_NAME.put(key, sb.toString());

			} else {

				FIELD_NAME.put(key, key);

			}

		}

		return FIELD_NAME.get(key);
	}

	/**
	 * Setter名キャッシュ.
	 */
	private static final Map<String, String> SETTER_NAME = new ConcurrentHashMap<>();

	/**
	 * SetterMethod名を取得する.
	 *
	 * @param fieldName Field名
	 * @return SetterMethod名
	 */
	public static String createSetterName (String fieldName) {

		if (!SETTER_NAME.containsKey(fieldName)) {
			StringBuilder sb = new StringBuilder("set");

			sb.append(fieldName.substring(0, 1).toUpperCase());

			if (fieldName.length() > 1) {
				sb.append(fieldName.substring(1));
			}

			SETTER_NAME.put(fieldName, sb.toString());
		}

		return SETTER_NAME.get(fieldName);
	}

	/**
	 * Getterメソッドキャッシュ.
	 */
	private static final Map<Class<?>, List<MethodFieldInfo>> GETTER_METHODS = new ConcurrentHashMap<>();

	/**
	 * フィールド名一覧を取得する.
	 *
	 * @param cls クラス
	 * @return フィールド名一覧
	 */
	public static List<MethodFieldInfo> getFieldNames (Class<?> cls) {

		if (!GETTER_METHODS.containsKey(cls)) {

			List<MethodFieldInfo> res = new ArrayList<>();

			List<Method> ms = new ArrayList<>();
			HashSet<Method> methodSet = new HashSet<>();
			for (Method m : cls.getMethods()) {
				if (methodSet.add(m)) {
					ms.add(m);
				}
			}
			for (Method m : cls.getDeclaredMethods()) {
				if (methodSet.add(m)) {
					ms.add(m);
				}
			}

			Set<String> names = new HashSet<>();
			for (Method m : ms) {

				String nm = m.getName();
				String field = null;
				if (nm.startsWith("get")) {
					String setterName = "set" + nm.substring(3);
					for (Method tm : ms) {
						if (setterName.equals(tm.getName()) && tm.getParameterTypes().length == 1) {
							if (nm.length() == 4) {
								field = nm.substring(3).toLowerCase();
							} else if (nm.length() > 3) {
								field = nm.substring(3, 4).toLowerCase() + nm.substring(4);
							}
							break;
						}
					}
				} else if (nm.startsWith("is")) {
					String setterName = "set" + nm.substring(2);
					for (Method tm : ms) {
						if (setterName.equals(tm.getName()) && tm.getParameterTypes().length == 1) {
							if (nm.length() == 3) {
								field = nm.substring(2).toLowerCase();
							} else if (nm.length() > 2) {
								field = nm.substring(2, 3).toLowerCase() + nm.substring(3);
							}
							break;
						}
					}
				}
				if (field != null) {
					if (names.add(field)) {
						m.setAccessible(true);
						MethodFieldInfo info = new MethodFieldInfo();
						info.setFieldName(field);
						info.setMethod(m);
						res.add(info);
					}
				}
			}

			Field[] fs = getFields(cls);
			for (Field f : fs) {
				/*
				 * static は「その値の中身」ではないので、プロパティとして扱わない。
				 *
				 * 移送元は含めていたので、JSON にすると
				 * public static final の定数まで出ていた。実際、Paging を返す API の応答に
				 * KEY_NAME_PAGE や DEFAULT_PER が並んでいた。
				 *
				 * 落とすのはここ（プロパティ一覧）だけにする。getFields() 自体は
				 * enum 定数の解決などにも使われていて、そちらは static が要る。
				 */
				if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() && names.add(f.getName())) {
					f.setAccessible(true);
					MethodFieldInfo info = new MethodFieldInfo();
					info.setFieldName(f.getName());
					info.setField(f);
					res.add(info);
				}
			}

			GETTER_METHODS.put(cls, res);
		}

		return GETTER_METHODS.get(cls);

	}

	/**
	 * クラスフィールドマップ.
	 */
	private static final Map<Class<?>, Field[]> FIELD_MAP = new ConcurrentHashMap<>();

	/**
	 * クラスフィールドを取得する.
	 *
	 * @param cls クラス
	 * @return クラスフィールド
	 */
	public static Field[] getFields (Class<?> cls) {

		if (!FIELD_MAP.containsKey(cls)) {
			List<Field> list = new ArrayList<>();
			HashSet<String> fieldSet = new HashSet<>();
			for (Field f : cls.getFields()) {
				if (fieldSet.add(f.getName())) {
					list.add(f);
				}
			}
			for (Field f : cls.getDeclaredFields()) {
				if (fieldSet.add(f.getName())) {
					list.add(f);
				}
			}
			{
				Class<?> superClass = cls;
				while ((superClass = superClass.getSuperclass()) != null) {
					for (Field f : superClass.getDeclaredFields()) {
						if (fieldSet.add(f.getName())) {
							list.add(f);
						}
					}
				}
			}
			Field[] fields = new Field[list.size()];
			for (int i = 0; i < list.size(); i++) {
				fields[i] = list.get(i);
			}
			FIELD_MAP.put(cls, fields);
		}
		return FIELD_MAP.get(cls);
	}

	/**
	 * 空パラメータ.
	 */
	private static final Class<?>[] NULL_CLASS_PARAMS = new Class<?>[]{};

	/**
	 * フィールド型キャッシュ.
	 */
	private static Map<Class<?>, Map<String, Class<?>[]>> FIELDCLASS_MAP = new ConcurrentHashMap<>();

	/**
	 * フィールドの型一覧を取得する.
	 *
	 * @param cls   クラス
	 * @param field フィールド
	 * @return 型一覧
	 */
	private static Class<?>[] getFieldClasses (Class<?> cls, Field field) {

		if (cls == null || field == null) {
			return null;
		}

		if (!FIELDCLASS_MAP.containsKey(cls)) {
			FIELDCLASS_MAP.put(cls, new ConcurrentHashMap<>());
		}

		Map<String, Class<?>[]> map = FIELDCLASS_MAP.get(cls);

		if (!map.containsKey(field.getName())) {

			Class<?>[] fieldClasses = null;

			if (field.getGenericType() instanceof ParameterizedType) {
				ParameterizedType pType = (ParameterizedType) field.getGenericType();
				List<Class<?>> classes = getTypes(pType);
				fieldClasses = classes.toArray(NULL_CLASS_PARAMS);
			} else {
				try {
					Class<?> c = field.getType();
					if (isAssignableFrom(Map.class, c)) {
						fieldClasses = new Class<?>[]{c, Object.class, Object.class};
					} else if (isAssignableFrom(Iterable.class, c)) {
						fieldClasses = new Class<?>[]{c, Object.class};
					} else {
						fieldClasses = new Class<?>[]{c};
					}
				} catch (Exception e) {
					fieldClasses = new Class<?>[]{Object.class};
				}
			}

			map.put(field.getName(), fieldClasses);

		}

		return map.get(field.getName());

	}

	/**
	 * メソッド型キャッシュ.
	 */
	private static Map<Class<?>, Map<String, Class<?>[]>> METHODCLASS_MAP = new ConcurrentHashMap<>();

	/**
	 * メソッドのパラメータ型一覧を取得する.
	 *
	 * @param cls    クラス
	 * @param method メソッド
	 * @return 型一覧
	 */
	private static Class<?>[] getMethodClasses (Class<?> cls, Method method) {

		if (cls == null || method == null) {
			return null;
		}

		if (!METHODCLASS_MAP.containsKey(cls)) {
			METHODCLASS_MAP.put(cls, new ConcurrentHashMap<>());
		}

		Map<String, Class<?>[]> map = METHODCLASS_MAP.get(cls);

		if (!map.containsKey(method.getName())) {

			Class<?>[] methodClasses = null;

			Type[] types = method.getGenericParameterTypes();
			if (types != null && types[0] instanceof ParameterizedType) {
				ParameterizedType pType = (ParameterizedType) types[0];
				List<Class<?>> classes = getTypes(pType);
				methodClasses = classes.toArray(NULL_CLASS_PARAMS);
			} else {
				try {
					Class<?> c = method.getParameterTypes()[0];
					if (isAssignableFrom(Map.class, c)) {
						methodClasses = new Class<?>[]{c, Object.class, Object.class};
					} else if (isAssignableFrom(Iterable.class, c)) {
						methodClasses = new Class<?>[]{c, Object.class};
					} else {
						methodClasses = new Class<?>[]{c};
					}
				} catch (Exception e) {
					methodClasses = new Class<?>[]{Object.class};
				}
			}

			map.put(method.getName(), methodClasses);

		}

		return map.get(method.getName());

	}

	/**
	 * ParameterizedTypeから型一覧を取得する.
	 *
	 * @param pType ParameterizedType
	 * @return 型一覧
	 */
	private static List<Class<?>> getTypes (ParameterizedType pType) {

		List<Class<?>> res = new ArrayList<>();
		res.add((Class<?>) pType.getRawType());

		Type[] types = pType.getActualTypeArguments();
		for (Type t : types) {
			if (t instanceof ParameterizedType) {
				res.addAll(getTypes((ParameterizedType) t));
			} else {
				try {
					Class<?> c = (Class<?>) t;
					res.add(c);
					if (isAssignableFrom(Map.class, c)) {
						res.add(Object.class);
						res.add(Object.class);
					} else if (isAssignableFrom(Iterable.class, c)) {
						res.add(Object.class);
					}
				} catch (Exception e) {
					res.add(Object.class);
				}
			}
		}

		return res;
	}

	/**
	 * 文字列を区切り文字で区切る.
	 *
	 * @param key        文字列
	 * @param delimiters 区切り文字一覧
	 * @return 文字列一覧
	 */
	private static List<String> split (String key, char[] delimiters) {

		List<String> keys = new ArrayList<>();
		List<Integer> indexs = new ArrayList<>();
		for (char d : delimiters) {
			int index = -1;
			while ((index = key.indexOf(d, index + 1)) > -1) {
				indexs.add(index);
			}
		}
		Collections.sort(indexs);

		if (indexs.size() > 0 && indexs.get(0) > 0) {
			keys.add(key.substring(0, indexs.get(0)));
		}
		for (int i = 0; i < indexs.size() - 1; i++) {
			int start = indexs.get(i) + 1;
			int end = indexs.get(i + 1);
			if (start < end) {
				keys.add(key.substring(start, end));
			}
		}
		if (indexs.size() > 0 && indexs.get(indexs.size() - 1) < key.length() - 1) {
			keys.add(key.substring(indexs.get(indexs.size() - 1) + 1));
		}

		if (keys.size() == 0) {
			keys.add(key);
		}

		return keys;
	}

	/**
	 * フィールドを取得する
	 *
	 * @param clazz		クラス
	 * @param fieldName	フィールド名
	 * @return	フィールド
	 */
	public static Field getFieldFromClass (Class<?> clazz, String fieldName) throws NoSuchFieldException {

		Field field = null;
		while (clazz != null) {
			try {
				field = clazz.getDeclaredField(fieldName);
				field.setAccessible(true);
				break;
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}

		if (field == null) {
			throw new NoSuchFieldException();
		}

		return field;

	}

	/**
	 * コンストラクタ.
	 */
	private PropertyUtil () {
		// 隠蔽
	}

	/**
	 * コンストラクタキャッシュ情報.
	 *
	 * @author DN
	 */
	static class ConstructorInfo {

		/**
		 * コンストラクタ
		 */
		public ConstructorInfo (Constructor<?> constructor, Object params) {

			this.constructor = constructor;
			this.params = params;
		}

		/**
		 * コンストラクタ.
		 */
		public Constructor<?> constructor;

		/**
		 * パラメータ.
		 */
		public Object params;

	}

	/**
	 * メソッドフィールドキャッシュ情報.
	 *
	 * @author DN
	 */
	public static class MethodFieldInfo {

		/**
		 * フィールド名.
		 */
		private String fieldName;

		/**
		 * メソッド.
		 */
		private Method method;

		/**
		 * フィールド.
		 */
		private Field field;

		/**
		 * メソッド使用判定.
		 */
		private boolean isMethod = true;

		/**
		 * フィールド使用判定.
		 */
		private boolean isField = true;

		/**
		 * フィールド名を取得する.
		 *
		 * @return フィールド名
		 */
		public String getFieldName () {

			return fieldName;
		}

		/**
		 * フィールド名を設定する.
		 *
		 * @param fieldName フィールド名
		 */
		public void setFieldName (String fieldName) {

			this.fieldName = fieldName;
		}

		/**
		 * メソッドを取得する.
		 *
		 * @return メソッド
		 */
		public Method getMethod () {

			return method;
		}

		/**
		 * メソッドを設定する.
		 *
		 * @param method メソッド
		 */
		public void setMethod (Method method) {

			this.method = method;
		}

		/**
		 * フィールドを取得する.
		 *
		 * @return フィールド
		 */
		public Field getField () {

			return field;
		}

		/**
		 * フィールドを設定する.
		 *
		 * @param field フィールド
		 */
		public void setField (Field field) {

			this.field = field;
		}

		/**
		 * プロパティを取得する.
		 *
		 * @param src オブジェクト
		 * @return プロパティ
		 */
		public Object getProperty (Object src) {

			if (method != null && isMethod) {

				try {
					return method.invoke(src);
				} catch (Exception e) {
					isMethod = false;
				}

			}

			if (field != null && isField) {

				try {
					return field.get(src);
				} catch (Exception e) {
					isField = false;
				}

			}

			return null;

		}

	}

	/**
	 * Setterメソッド情報.
	 *
	 * @author DN
	 */
	public static class SetterMethodInfo {

		/**
		 * メソッド.
		 */
		private Method method;

		/**
		 * 引数クラス一覧.
		 */
		private Class<?>[] parameterClasses;

		/**
		 * メソッドを取得する.
		 *
		 * @return メソッド
		 */
		public Method getMethod () {

			return method;
		}

		/**
		 * メソッドを設定する.
		 *
		 * @param method メソッド
		 */
		public void setMethod (Method method) {

			this.method = method;
		}

		/**
		 * 引数クラス一覧を取得する.
		 *
		 * @return 引数クラス一覧
		 */
		public Class<?>[] getParameterClasses () {

			return parameterClasses;
		}

		/**
		 * 引数クラス一覧を設定する.
		 *
		 * @param parameterClasses 引数クラス一覧
		 */
		public void setParameterClasses (Class<?>[] parameterClasses) {

			this.parameterClasses = parameterClasses;
		}

	}

}
