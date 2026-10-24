package io.jimble.converter.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bean / record に読み込む手順（D-307）
 *
 * <p>
 * <b>クラスごとに1回だけ調べて覚える</b>（{@link ClassValue}）。JSON のキーの名前は覚えない
 * （外から来た名前で表を大きくしない。D-226 と同じ問題を作らない）。
 * </p>
 *
 * <ul>
 *   <li>record：正準のコンストラクタに、コンポーネントの順で渡す。JSON に無いコンポーネントは null（プリミティブは 0 / false）</li>
 *   <li>Bean：引数の無いコンストラクタで作り、setter（{@code setX}）があればそれで、無ければフィールドに入れる
 *       （親クラスの private フィールドも。static と final は入れない）</li>
 * </ul>
 */
final class BindingModel {

	/* 型を Object にそろえた setter（invokeExact で呼ぶ） */
	private static final MethodType SETTER_TYPE = MethodType.methodType(void.class, Object.class, Object.class);

	private static final ClassValue<BindingModel> CACHE = new ClassValue<>() {
		@Override
		protected BindingModel computeValue (Class<?> type) {
			return inspect(type);
		}
	};

	/** record か */
	final boolean record;

	/** 作るもの（record なら Object[] を受けるコンストラクタ、Bean なら引数なし） */
	final MethodHandle factory;

	/** record のコンポーネント（名前 → 何番目か） */
	final Map<String, Integer> componentIndex;

	/** record のコンポーネントの型（宣言の型。型変数のまま） */
	final Type[] componentTypes;

	/** record のコンポーネントのクラス */
	final Class<?>[] componentClasses;

	/** record のコンポーネントの読み方（型変数が残っていれば null） */
	final ReadPlan[] componentPlans;

	/** Bean の項目（名前 → 入れ方） */
	final Map<String, Property> properties;

	/** 作れない理由（作れるなら null） */
	final String unsupported;

	/**
	 * Bean の項目
	 *
	 * @param type		型（宣言の型。型変数のまま）
	 * @param setter	入れるもの（Object, Object）→ void
	 * @param plan		読み方（型変数が残っていれば null。読むときに解く）
	 */
	record Property (Type type, MethodHandle setter, ReadPlan plan) {

		Property (Type type, MethodHandle setter) {
			this(type, setter, ReadPlan.hasTypeVariable(type) ? null : ReadPlan.of(type));
		}

	}

	private BindingModel (boolean record, MethodHandle factory, Map<String, Integer> componentIndex, Type[] componentTypes,
		Class<?>[] componentClasses, Map<String, Property> properties, String unsupported) {

		this.record = record;
		this.factory = factory;
		this.componentIndex = componentIndex;
		this.componentTypes = componentTypes;
		this.componentClasses = componentClasses;
		this.componentPlans = new ReadPlan[componentTypes.length];
		for (int i = 0; i < componentTypes.length; i++) {
			this.componentPlans[i] = ReadPlan.hasTypeVariable(componentTypes[i]) ? null : ReadPlan.of(componentTypes[i]);
		}
		this.properties = properties;
		this.unsupported = unsupported;

	}

	/**
	 * クラスの手順
	 *
	 * @param type	クラス
	 * @return	手順
	 */
	static BindingModel of (Class<?> type) {

		return CACHE.get(type);

	}

	/**
	 * JSON のキーに当たる項目を探す
	 *
	 * @param key		キー
	 * @param snakeCase	見つからなければ、{@code user_name} / {@code user-name} を {@code userName} にしても探すか
	 * @return	項目（無ければ null）
	 */
	Property property (String key, boolean snakeCase) {

		Property found = properties.get(key);
		if (found == null && snakeCase) {
			found = properties.get(camelCase(key));
		}
		return found;

	}

	/**
	 * JSON のキーに当たる record のコンポーネント
	 *
	 * @return	何番目か（無ければ -1）
	 */
	int component (String key, boolean snakeCase) {

		Integer index = componentIndex.get(key);
		if (index == null && snakeCase) {
			index = componentIndex.get(camelCase(key));
		}
		return index == null ? -1 : index;

	}

	/**
	 * {@code user_name} / {@code user-name} / {@code user name} を {@code userName} にする
	 */
	static String camelCase (String key) {

		StringBuilder sb = new StringBuilder(key.length());
		boolean upper = false;
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c == '_' || c == '-' || c == ' ') {
				upper = sb.length() > 0;
				continue;
			}
			sb.append(upper ? Character.toUpperCase(c) : sb.length() == 0 ? Character.toLowerCase(c) : c);
			upper = false;
		}
		return sb.toString();

	}

	private static BindingModel inspect (Class<?> type) {

		MethodHandles.Lookup lookup = MethodHandles.lookup();

		if (type.isRecord()) {
			return inspectRecord(type, lookup);
		}

		if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
			return unsupported(type.getName() + " は抽象クラスかインターフェースなので作れません");
		}

		MethodHandle factory;
		try {
			Constructor<?> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			factory = lookup.unreflectConstructor(constructor).asType(MethodType.methodType(Object.class));
		} catch (NoSuchMethodException ex) {
			return unsupported(type.getName() + " に引数の無いコンストラクタがありません");
		} catch (RuntimeException | IllegalAccessException ex) {
			return unsupported(type.getName() + " のコンストラクタに触れません: " + ex.getMessage());
		}

		Map<String, Property> properties = new HashMap<>();

		// フィールド（親クラスから。子の同じ名前が勝つ）
		List<Class<?>> chain = new ArrayList<>();
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			chain.addFirst(c);
		}
		for (Class<?> c : chain) {
			for (Field field : c.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				// transient は入れない（名前は外から来るので、残しておきたい作業用の値を書き換えさせない。D-226）
				if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
					continue;
				}
				try {
					field.setAccessible(true);
					properties.put(field.getName(), new Property(field.getGenericType(), lookup.unreflectSetter(field).asType(SETTER_TYPE)));
				} catch (RuntimeException | IllegalAccessException ex) {
					// 触れないフィールドは入れない
				}
			}
		}

		// setter（あればフィールドより優先する。値の確かめを setter に書いていることがあるため）
		for (Class<?> c : chain) {
			for (Method method : c.getDeclaredMethods()) {
				if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1 || method.isBridge()) {
					continue;
				}
				String name = method.getName();
				if (!name.startsWith("set") || name.length() == 3) {
					continue;
				}
				String suffix = name.substring(3);
				String property = suffix.length() == 1
					? suffix.toLowerCase(Locale.ROOT)
					: Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
				try {
					method.setAccessible(true);
					properties.put(property, new Property(method.getGenericParameterTypes()[0], lookup.unreflect(method).asType(SETTER_TYPE)));
				} catch (RuntimeException | IllegalAccessException ex) {
					// 触れない setter は使わない（フィールドがあればそちら）
				}
			}
		}

		return new BindingModel(false, factory, Map.of(), new Type[0], new Class<?>[0], Map.copyOf(properties), null);

	}

	private static BindingModel inspectRecord (Class<?> type, MethodHandles.Lookup lookup) {

		RecordComponent[] components = type.getRecordComponents();
		Class<?>[] classes = new Class<?>[components.length];
		Type[] types = new Type[components.length];
		Map<String, Integer> index = new HashMap<>();

		for (int i = 0; i < components.length; i++) {
			classes[i] = components[i].getType();
			types[i] = components[i].getGenericType();
			index.put(components[i].getName(), i);
		}

		try {
			Constructor<?> constructor = type.getDeclaredConstructor(classes);
			constructor.setAccessible(true);
			MethodHandle factory = lookup.unreflectConstructor(constructor)
				.asType(MethodType.methodType(Object.class, classes))
				.asSpreader(Object[].class, classes.length);
			return new BindingModel(true, factory, Map.copyOf(index), types, classes, Map.of(), null);
		} catch (NoSuchMethodException | IllegalAccessException | RuntimeException ex) {
			return unsupported(type.getName() + " の正準のコンストラクタに触れません: " + ex.getMessage());
		}

	}

	private static BindingModel unsupported (String reason) {

		return new BindingModel(false, null, Map.of(), new Type[0], new Class<?>[0], Map.of(), reason);

	}

}
