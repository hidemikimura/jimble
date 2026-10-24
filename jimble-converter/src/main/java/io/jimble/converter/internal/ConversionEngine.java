package io.jimble.converter.internal;

import io.jimble.converter.ConversionException;
import io.jimble.converter.ConverterOptions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 値を型にする（D-307）
 *
 * <p>
 * <b>使い捨て。</b>1回の変換に1つ作る（循環参照を見つけるための記録を持つ）。
 * </p>
 *
 * <ul>
 *   <li>Map / Bean / record → Bean / record：キーで項目に入れる（知らないキーは飛ばす）</li>
 *   <li>Bean / record → Map：項目を宣言した順に入れる（値は写さない）</li>
 *   <li>コレクション・配列 → コレクション・配列：要素を1つずつ型にする。1つの値は、要素1つのものとして扱う</li>
 *   <li>要素1つのコレクション → 1つの値：その要素。2つ以上は断る</li>
 *   <li>型が合っている値は、写さずにそのまま返す</li>
 * </ul>
 */
public final class ConversionEngine {

	/* 引数の無いコンストラクタ（コレクションと Map を作る） */
	private static final ClassValue<MethodHandle> NO_ARG = new ClassValue<>() {
		@Override
		protected MethodHandle computeValue (Class<?> type) {
			try {
				return MethodHandles.publicLookup().findConstructor(type, MethodType.methodType(void.class))
					.asType(MethodType.methodType(Object.class));
			} catch (ReflectiveOperationException ex) {
				return null;
			}
		}
	};

	/* 変換のしかた */
	private final ConverterOptions options;

	/* 時差の無い日時を補う時間帯（null ならサーバーの時間帯。ZoneId.systemDefault() は呼ぶたびに TimeZone を写すので、日時を扱うときだけ引く） */
	private final ZoneId zone;

	/* 変換している途中の元の値（循環参照を見つけるため。入れ物に入るときに作る） */
	private Object[] stack;

	/* いまの場所（String / Integer） */
	private Object[] path;

	/* いまの深さ */
	private int depth;

	/**
	 * コンストラクタ
	 *
	 * @param options	変換のしかた
	 */
	public ConversionEngine (ConverterOptions options) {

		this.options = options;
		this.zone = options.zone();

	}

	/**
	 * 値を型にする
	 *
	 * @param value	値
	 * @param type	型
	 * @return	型にした値
	 * @throws ConversionException	できなかったとき（ゆるい変換なら、読めない1つの値は null / 0 になる）
	 */
	public Object convert (Object value, Type type) {

		return convert(value, ReadPlan.of(type));

	}

	private Object convert (Object value, ReadPlan plan) {

		if (value == null) {
			return plan.kind == ReadPlan.Kind.OPTIONAL ? Optional.empty() : Coercion.defaultValue(plan.raw);
		}

		return switch (plan.kind) {
			case ANY -> value;
			case SCALAR -> scalar(value, plan.raw);
			case OPTIONAL -> Optional.ofNullable(convert(value instanceof Optional<?> o ? o.orElse(null) : value, plan.element));
			case ARRAY -> array(value, plan);
			case COLLECTION -> collection(value, plan);
			case MAP -> map(value, plan);
			case OBJECT -> object(value, plan);
		};

	}

	// region 1つの値

	private Object scalar (Object value, Class<?> raw) {

		// 要素1つのコレクション・配列は、その要素（2つ以上は断る）
		if (value instanceof Collection<?> || value.getClass().isArray()) {
			List<Object> items = elements(value);
			if (items.isEmpty()) {
				return Coercion.defaultValue(raw);
			}
			if (items.size() > 1) {
				if (options.lenient()) {
					return Coercion.defaultValue(raw);
				}
				throw error(raw.getSimpleName() + " にできません: 要素が " + items.size() + " つあります", null);
			}
			value = items.getFirst();
			if (value == null) {
				return Coercion.defaultValue(raw);
			}
		}

		if (value instanceof Optional<?> o) {
			return o.isEmpty() ? Coercion.defaultValue(raw) : scalar(o.get(), raw);
		}

		if (options.isUnsafe(raw) && !raw.isInstance(value)) {
			throw error(raw.getName() + " は、危ない型なので作りません（ConverterOptions.allowUnsafeTypes(true) で作れます）", null);
		}

		try {
			return Coercion.coerce(value, raw, zone);
		} catch (IllegalArgumentException | ArithmeticException ex) {
			if (options.lenient()) {
				return Coercion.defaultValue(raw);
			}
			throw error(raw.getSimpleName() + " にできません: " + ex.getMessage(), ex);
		}

	}

	// endregion

	// region 入れ物

	private Object array (Object value, ReadPlan plan) {

		ReadPlan elementPlan = plan.element;

		if (plan.raw.isInstance(value) && elementPlan.kind != ReadPlan.Kind.OBJECT) {
			return value;
		}

		List<Object> items = elements(value);
		Object array = Array.newInstance(elementPlan.raw, items.size());

		enter(value);
		for (int i = 0; i < items.size(); i++) {
			path[depth - 1] = i;
			Object item = convert(items.get(i), elementPlan);
			if (item == null && elementPlan.raw.isPrimitive()) {
				item = Coercion.defaultValue(elementPlan.raw);
			}
			Array.set(array, i, item);
		}
		exit();

		return array;

	}

	@SuppressWarnings("unchecked")
	private Object collection (Object value, ReadPlan plan) {

		// 型が合っていて、要素の型が決まっていない（か、要素がもう合っている）なら写さない
		if (plan.raw.isInstance(value) && (plan.element.kind == ReadPlan.Kind.ANY || allMatch((Collection<?>) value, plan.element.raw))) {
			return value;
		}

		Collection<Object> result = (Collection<Object>) newContainer(plan.raw);
		List<Object> items = elements(value);

		enter(value);
		for (int i = 0; i < items.size(); i++) {
			path[depth - 1] = i;
			result.add(convert(items.get(i), plan.element));
		}
		exit();

		return result;

	}

	private static boolean allMatch (Collection<?> items, Class<?> type) {

		if (type == Object.class) {
			return true;
		}
		for (Object item : items) {
			if (item != null && !type.isInstance(item)) {
				return false;
			}
		}
		return true;

	}

	/**
	 * 要素を並べる（コレクション・配列・Iterable。1つの値は要素1つ）
	 */
	private List<Object> elements (Object value) {

		if (value instanceof List<?> list) {
			return new ArrayList<>(list);
		}
		if (value instanceof Collection<?> collection) {
			return new ArrayList<>(collection);
		}
		if (value.getClass().isArray()) {
			int n = Array.getLength(value);
			List<Object> items = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				items.add(Array.get(value, i));
			}
			return items;
		}
		if (value instanceof Iterable<?> iterable) {
			List<Object> items = new ArrayList<>();
			for (Iterator<?> it = iterable.iterator(); it.hasNext();) {
				items.add(it.next());
			}
			return items;
		}
		if (value instanceof Map<?, ?>) {
			throw error("オブジェクト（Map）を、配列にはできません", null);
		}
		List<Object> single = new ArrayList<>(1);
		single.add(value);
		return single;

	}

	@SuppressWarnings("unchecked")
	private Object map (Object value, ReadPlan plan) {

		// 型が合っていて、値の型が決まっていないなら写さない
		if (plan.raw.isInstance(value) && plan.untypedValues && (plan.keyClass == Object.class || plan.keyClass == String.class)) {
			return value;
		}

		Map<Object, Object> result = (plan.raw == Map.class || plan.raw == LinkedHashMap.class) && plan.untypedValues
			? (Map<Object, Object>) (Map<?, ?>) options.mapFactory().get()
			: (Map<Object, Object>) newContainer(plan.raw);

		if (!enter(value)) {
			return null;
		}

		if (value instanceof Map<?, ?> source) {
			for (Map.Entry<?, ?> entry : source.entrySet()) {
				Object key = entry.getKey();
				if (key == null) {
					continue;
				}
				path[depth - 1] = String.valueOf(key);
				Object k = plan.keyClass == Object.class || plan.keyClass == String.class && key instanceof String
					? key
					: scalar(key, plan.keyClass == Object.class ? String.class : plan.keyClass);
				result.put(k, convert(entry.getValue(), plan.element));
			}
		} else if (isBean(value.getClass())) {
			BeanProperties properties = BeanProperties.of(value.getClass());
			for (int i = 0; i < properties.names.length; i++) {
				path[depth - 1] = properties.names[i];
				result.put(properties.names[i], convert(get(value, properties, i), plan.element));
			}
		} else {
			exit();
			throw error("オブジェクト（Map）にできません: " + value.getClass().getName(), null);
		}

		exit();
		return result;

	}

	/**
	 * コレクションか Map を作る（インターフェースなら決まったものを）
	 */
	private Object newContainer (Class<?> raw) {

		if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
			if (SortedSet.class.isAssignableFrom(raw)) {
				return new TreeSet<>();
			}
			if (Set.class.isAssignableFrom(raw)) {
				return new LinkedHashSet<>();
			}
			if (Queue.class.isAssignableFrom(raw)) {
				return new ArrayDeque<>();
			}
			if (SortedMap.class.isAssignableFrom(raw)) {
				return new TreeMap<>();
			}
			if (Map.class.isAssignableFrom(raw)) {
				return new LinkedHashMap<>();
			}
			return new ArrayList<>();
		}

		MethodHandle constructor = NO_ARG.get(raw);
		if (constructor == null) {
			throw error(raw.getName() + " に引数の無い public のコンストラクタがありません", null);
		}

		try {
			return (Object) constructor.invokeExact();
		} catch (Throwable ex) {
			throw error(raw.getName() + " を作れませんでした: " + ex, ex);
		}

	}

	// endregion

	// region Bean と record

	private Object object (Object value, ReadPlan plan) {

		Class<?> raw = plan.raw;

		if (raw.isInstance(value)) {
			return value;
		}

		BindingModel model = BindingModel.of(raw);
		if (model.unsupported != null) {
			throw error(model.unsupported, null);
		}

		Map<TypeVariable<?>, Type> bindings = plan.type instanceof Class<?> && raw.getTypeParameters().length == 0
			&& !(raw.getGenericSuperclass() instanceof java.lang.reflect.ParameterizedType)
			? Map.of()
			: Types.bindings(plan.type);

		if (!(value instanceof Map<?, ?>) && !isBean(value.getClass())) {
			throw error(raw.getSimpleName() + " にできません: " + describe(value), null);
		}

		if (!enter(value)) {
			return null;
		}

		Object result = model.record ? toRecord(value, raw, model, bindings) : toBean(value, raw, model, bindings);

		exit();
		return result;

	}

	private Object toBean (Object value, Class<?> raw, BindingModel model, Map<TypeVariable<?>, Type> bindings) {

		Object bean;
		try {
			bean = (Object) model.factory.invokeExact();
		} catch (Throwable ex) {
			throw error(raw.getName() + " を作れませんでした: " + ex, ex);
		}

		boolean snakeCase = options.snakeCaseKeys();

		forEachEntry(value, (key, v) -> {

			BindingModel.Property property = model.property(key, snakeCase);
			if (property == null) {
				unknownKey(raw, key);
				return;
			}

			Object converted = property.plan() != null ? convert(v, property.plan()) : convert(v, ReadPlan.of(Types.resolve(property.type(), bindings)));
			try {
				property.setter().invokeExact(bean, converted);
			} catch (Throwable ex) {
				throw error(raw.getSimpleName() + "." + key + " に入れられませんでした: " + ex, ex);
			}

		});

		return bean;

	}

	private Object toRecord (Object value, Class<?> raw, BindingModel model, Map<TypeVariable<?>, Type> bindings) {

		int count = model.componentClasses.length;
		Object[] args = new Object[count];
		for (int i = 0; i < count; i++) {
			args[i] = Coercion.defaultValue(model.componentClasses[i]);
		}

		boolean snakeCase = options.snakeCaseKeys();

		forEachEntry(value, (key, v) -> {

			int index = model.component(key, snakeCase);
			if (index < 0) {
				unknownKey(raw, key);
				return;
			}

			ReadPlan componentPlan = model.componentPlans[index];
			Object converted = componentPlan != null ? convert(v, componentPlan) : convert(v, ReadPlan.of(Types.resolve(model.componentTypes[index], bindings)));
			if (converted == null && model.componentClasses[index].isPrimitive()) {
				converted = Coercion.defaultValue(model.componentClasses[index]);
			}
			args[index] = converted;

		});

		try {
			return (Object) model.factory.invokeExact(args);
		} catch (RuntimeException ex) {
			throw error(raw.getSimpleName() + " を作れませんでした（コンストラクタが断りました）: " + ex.getMessage(), ex);
		} catch (Throwable ex) {
			throw error(raw.getSimpleName() + " を作れませんでした: " + ex, ex);
		}

	}

	/**
	 * 元の値のキーと値を1つずつ渡す（Map ならエントリ、Bean / record なら項目）
	 */
	private void forEachEntry (Object value, java.util.function.BiConsumer<String, Object> action) {

		if (value instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				String key = String.valueOf(entry.getKey());
				path[depth - 1] = key;
				action.accept(key, entry.getValue());
			}
			return;
		}

		BeanProperties properties = BeanProperties.of(value.getClass());
		for (int i = 0; i < properties.names.length; i++) {
			path[depth - 1] = properties.names[i];
			action.accept(properties.names[i], get(value, properties, i));
		}

	}

	private void unknownKey (Class<?> raw, String key) {

		if (options.rejectUnknownKeys()) {
			throw error(raw.getSimpleName() + " に「" + key + "」はありません", null);
		}

	}

	private Object get (Object bean, BeanProperties properties, int i) {

		try {
			return (Object) properties.getters[i].invokeExact(bean);
		} catch (RuntimeException | Error ex) {
			throw ex;
		} catch (Throwable ex) {
			throw error("項目「" + properties.names[i] + "」を読めませんでした: " + ex, ex);
		}

	}

	/**
	 * Bean / record として中を読む型か（JDK のクラス・コレクション・Map・スカラーではない）
	 */
	private static boolean isBean (Class<?> type) {

		if (type.isRecord()) {
			return true;
		}
		if (type.isPrimitive() || type.isArray() || type.isEnum() || Map.class.isAssignableFrom(type)
			|| Collection.class.isAssignableFrom(type) || Number.class.isAssignableFrom(type)
			|| CharSequence.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
			return false;
		}
		String name = type.getName();
		return !(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun."));

	}

	// endregion

	// region 循環と場所

	/**
	 * 入れ物・Bean の中に入る（循環参照を見る）
	 *
	 * @return	入った場合 = true（循環を null にする設定で、循環を見つけたら false）
	 */
	private boolean enter (Object source) {

		for (int i = 0; i < depth; i++) {
			if (stack[i] == source) {
				if (options.cycleToNull()) {
					return false;
				}
				throw error("循環参照があります", null);
			}
		}

		if (stack == null) {
			stack = new Object[16];
			path = new Object[16];
		} else if (depth == stack.length) {
			stack = java.util.Arrays.copyOf(stack, depth * 2);
			path = java.util.Arrays.copyOf(path, depth * 2);
		}

		stack[depth] = source;
		path[depth] = null;
		depth++;
		return true;

	}

	private void exit () {

		depth--;
		stack[depth] = null;
		path[depth] = null;

	}

	private ConversionException error (String message, Throwable cause) {

		StringBuilder sb = new StringBuilder("$");
		for (int i = 0; i < depth; i++) {
			Object key = path[i];
			if (key instanceof Integer index) {
				sb.append('[').append(index).append(']');
			} else if (key != null) {
				sb.append('.').append(key);
			}
		}

		return new ConversionException(sb.toString(), message, cause);

	}

	private static String describe (Object value) {

		String text = String.valueOf(value);
		return value.getClass().getSimpleName() + "「" + (text.length() > 40 ? text.substring(0, 40) + "…" : text) + "」";

	}

	// endregion

}
