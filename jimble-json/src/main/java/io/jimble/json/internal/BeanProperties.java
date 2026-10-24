package io.jimble.json.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bean / record の書き出す項目（D-307）
 *
 * <p>
 * <b>クラスごとに1回だけ調べて覚える</b>（{@link ClassValue}。クラスが捨てられれば一緒に消える）。
 * </p>
 *
 * <h2>どの項目を書くか（作り直す前と同じ）</h2>
 * <ul>
 *   <li>record：コンポーネント（宣言した順。アクセサで読む）</li>
 *   <li>Bean：static でないフィールド（private も。親クラスから順に、宣言した順）と、
 *       getter と setter の組（{@code getX} / {@code isX} と {@code setX}）。同じ名前なら getter で読む。
 *       フィールドの無い getter と setter の組は、フィールドのあとに名前の順で並べる</li>
 * </ul>
 * <p>
 * <b>順番を決める。</b>作り直す前は {@code Class.getMethods()} の順に頼っていて、JVM を起動するたびに変わった。
 * </p>
 */
final class BeanProperties {

	/* 型を Object にそろえた読み手（invokeExact で呼ぶため） */
	private static final MethodType GETTER_TYPE = MethodType.methodType(Object.class, Object.class);

	/* クラスごとの項目 */
	private static final ClassValue<BeanProperties> CACHE = new ClassValue<>() {
		@Override
		protected BeanProperties computeValue (Class<?> type) {
			return inspect(type);
		}
	};

	/* 項目の名前 */
	final String[] names;

	/* 項目を読むもの（Object → Object） */
	final MethodHandle[] getters;

	private BeanProperties (List<String> names, List<MethodHandle> getters) {

		this.names = names.toArray(String[]::new);
		this.getters = getters.toArray(MethodHandle[]::new);

	}

	/**
	 * クラスの項目
	 *
	 * @param type	クラス
	 * @return	項目
	 */
	static BeanProperties of (Class<?> type) {

		return CACHE.get(type);

	}

	/**
	 * クラスを調べる
	 */
	private static BeanProperties inspect (Class<?> type) {

		MethodHandles.Lookup lookup = MethodHandles.lookup();

		if (type.isRecord()) {
			List<String> names = new ArrayList<>();
			List<MethodHandle> getters = new ArrayList<>();
			for (RecordComponent component : type.getRecordComponents()) {
				MethodHandle getter = unreflect(lookup, component.getAccessor());
				if (getter != null) {
					names.add(component.getName());
					getters.add(getter);
				}
			}
			return new BeanProperties(names, getters);
		}

		// getter と setter の組（名前 → getter）
		Map<String, Method> pairs = getterSetterPairs(type);

		Map<String, MethodHandle> ordered = new LinkedHashMap<>();

		// フィールド（親クラスから順に、宣言した順）
		List<Class<?>> chain = new ArrayList<>();
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			chain.addFirst(c);
		}
		for (Class<?> c : chain) {
			for (Field field : c.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || ordered.containsKey(field.getName())) {
					continue;
				}
				Method getter = pairs.remove(field.getName());
				MethodHandle handle = getter != null ? unreflect(lookup, getter) : unreflect(lookup, field);
				if (handle != null) {
					ordered.put(field.getName(), handle);
				}
			}
		}

		// フィールドの無い getter と setter の組（名前の順）
		for (Map.Entry<String, Method> pair : new TreeMap<>(pairs).entrySet()) {
			MethodHandle handle = unreflect(lookup, pair.getValue());
			if (handle != null) {
				ordered.putIfAbsent(pair.getKey(), handle);
			}
		}

		return new BeanProperties(new ArrayList<>(ordered.keySet()), new ArrayList<>(ordered.values()));

	}

	/**
	 * getter と setter の組を集める（名前 → getter）
	 */
	private static Map<String, Method> getterSetterPairs (Class<?> type) {

		Map<String, Method> pairs = new LinkedHashMap<>();

		List<Method> methods = new ArrayList<>(List.of(type.getMethods()));
		for (Method method : type.getDeclaredMethods()) {
			if (!methods.contains(method)) {
				methods.add(method);
			}
		}

		for (Method method : methods) {

			if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
				continue;
			}

			String name = method.getName();
			String suffix;
			if (name.startsWith("get") && name.length() > 3) {
				suffix = name.substring(3);
			} else if (name.startsWith("is") && name.length() > 2) {
				suffix = name.substring(2);
			} else {
				continue;
			}

			if (!hasSetter(methods, "set" + suffix)) {
				continue;
			}

			String field = suffix.length() == 1
				? suffix.toLowerCase(java.util.Locale.ROOT)
				: Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);

			pairs.putIfAbsent(field, method);

		}

		return pairs;

	}

	private static boolean hasSetter (List<Method> methods, String setter) {

		for (Method method : methods) {
			if (method.getParameterCount() == 1 && method.getName().equals(setter)) {
				return true;
			}
		}
		return false;

	}

	/**
	 * 読むものを作る（触れなければ null。モジュールで閉じたクラスなど）
	 */
	private static MethodHandle unreflect (MethodHandles.Lookup lookup, Method method) {

		try {
			method.setAccessible(true);
			return lookup.unreflect(method).asType(GETTER_TYPE);
		} catch (RuntimeException | IllegalAccessException ex) {
			return null;
		}

	}

	private static MethodHandle unreflect (MethodHandles.Lookup lookup, Field field) {

		try {
			field.setAccessible(true);
			return lookup.unreflectGetter(field).asType(GETTER_TYPE);
		} catch (RuntimeException | IllegalAccessException ex) {
			return null;
		}

	}

}
