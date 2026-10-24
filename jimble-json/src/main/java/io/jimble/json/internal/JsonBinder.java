package io.jimble.json.internal;

import io.jimble.json.JsonException;
import io.jimble.json.JsonReadOptions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
 * JSON を型（Bean / record / List / Map / 配列 / スカラー）に読む（D-307）
 *
 * <p>
 * <b>木を作らずに、読みながら入れる。</b>作り直す前は、JSON をいったん Data の木にしてから、
 * 型変換（Convertor）でもう一度まるごと写していた。
 * </p>
 *
 * <ul>
 *   <li>知らないキーは読み飛ばす（例外にしない）</li>
 *   <li>値は {@link Coercion} の決まりで項目の型にする。できなければ、場所（{@code $.items[0].price}）と行・文字の位置を付けて例外</li>
 *   <li>型の決まっていない値（Object・{@code Map<String, Object>} の値）は、木（{@link JsonReadOptions#mapFactory()} の Map）で読む</li>
 * </ul>
 */
public final class JsonBinder {

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

	/* 読み手 */
	private final JsonParser parser;

	/* 読み方 */
	private final JsonReadOptions options;

	/* 時差の無い日時を補う時間帯 */
	private final ZoneId zone;

	/* いまの場所（例外に載せる。String / Integer） */
	private Object[] path = new Object[16];

	/* いまの場所の深さ */
	private int pathDepth;

	/**
	 * コンストラクタ
	 *
	 * @param buf		読む文字
	 * @param start		読み始める位置
	 * @param end		読む範囲の終わり
	 * @param options	読み方
	 */
	public JsonBinder (char[] buf, int start, int end, JsonReadOptions options) {

		this.parser = new JsonParser(buf, start, end, options);
		this.options = options;
		this.zone = ZoneId.systemDefault();

	}

	/**
	 * 一番外の値を、型に読む
	 *
	 * @param type	型
	 * @return	読んだ値（空なら null）
	 * @throws JsonException	読めなかったとき・型にできなかったとき
	 */
	public Object readDocument (Type type) {

		if (!parser.begin()) {
			return null;
		}

		Object value = read(type);
		parser.finish();
		return value;

	}

	/**
	 * 値を1つ、型に読む（型変数が残っていれば解いてから）
	 */
	private Object read (Type type) {

		return read(ReadPlan.of(type));

	}

	/**
	 * 値を1つ、読み方に従って読む
	 */
	private Object read (ReadPlan plan) {

		if (parser.readNull()) {
			return plan.kind == ReadPlan.Kind.OPTIONAL ? Optional.empty() : Coercion.defaultValue(plan.raw);
		}

		return switch (plan.kind) {
			case ANY -> parser.readValue();
			case SCALAR -> readScalar(plan.raw);
			case OPTIONAL -> Optional.ofNullable(read(plan.element));
			case ARRAY -> readArray(plan.element);
			case COLLECTION -> readCollection(plan.raw, plan.element);
			case MAP -> readMap(plan);
			case OBJECT -> readObject(plan.raw, plan.type);
		};

	}

	// region スカラー

	private Object readScalar (Class<?> raw) {

		int at = parser.position();
		Object value = raw == String.class || raw == CharSequence.class || raw == char.class || raw == Character.class
			? parser.readValueKeepingStrings()
			: raw == java.math.BigDecimal.class || raw == java.math.BigInteger.class
			? parser.readValueKeepingNumberText()
			: parser.readValue();

		if (value instanceof Map || value instanceof List) {
			throw error("値が " + raw.getSimpleName() + " ではなく" + (value instanceof Map ? "オブジェクト" : "配列") + "です", at);
		}

		try {
			return Coercion.coerce(value, raw, zone);
		} catch (IllegalArgumentException | ArithmeticException ex) {
			throw error(raw.getSimpleName() + " にできません: " + ex.getMessage(), at);
		}

	}

	// endregion

	// region 入れ物

	private Object readArray (ReadPlan elementPlan) {

		Class<?> component = elementPlan.raw;
		List<Object> items = new ArrayList<>();
		readElements(elementPlan, items);

		Object array = Array.newInstance(component, items.size());
		for (int i = 0; i < items.size(); i++) {
			Object item = items.get(i);
			if (item == null && component.isPrimitive()) {
				item = Coercion.defaultValue(component);
			}
			Array.set(array, i, item);
		}
		return array;

	}

	@SuppressWarnings("unchecked")
	private Object readCollection (Class<?> raw, ReadPlan elementPlan) {

		Collection<Object> collection = (Collection<Object>) newContainer(raw);
		readElements(elementPlan, collection);
		return collection;

	}

	private void readElements (ReadPlan elementPlan, Collection<Object> into) {

		if (!parser.open('[', ']', "配列（[）")) {
			return;
		}

		int index = 0;
		push(0);

		do {
			path[pathDepth - 1] = index++;
			into.add(read(elementPlan));
		} while (parser.next(']'));

		pop();

	}

	@SuppressWarnings("unchecked")
	private Object readMap (ReadPlan plan) {

		Class<?> raw = plan.raw;

		// 値の型が決まっていない Map は、木と同じ Map（読み方の mapFactory）にする
		Map<Object, Object> map = (raw == Map.class || raw == LinkedHashMap.class) && plan.untypedValues
			? (Map<Object, Object>) (Map<?, ?>) options.mapFactory().get()
			: (Map<Object, Object>) newContainer(raw);

		Class<?> keyClass = plan.keyClass;

		if (!parser.open('{', '}', "オブジェクト（{）")) {
			return map;
		}

		push("");

		do {
			int at = parser.position();
			String key = parser.readKey();
			path[pathDepth - 1] = key;

			Object k = key;
			if (keyClass != Object.class && keyClass != String.class) {
				try {
					k = Coercion.coerce(key, keyClass, zone);
				} catch (IllegalArgumentException | ArithmeticException ex) {
					throw error("キーを " + keyClass.getSimpleName() + " にできません: " + ex.getMessage(), at);
				}
			}

			map.put(k, read(plan.element));
		} while (parser.next('}'));

		pop();
		return map;

	}

	/**
	 * コレクションか Map を作る（インターフェースなら決まったものを）
	 */
	private Object newContainer (Class<?> raw) {

		if (raw.isInterface() || java.lang.reflect.Modifier.isAbstract(raw.getModifiers())) {
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
			throw error(raw.getName() + " に引数の無い public のコンストラクタがありません", parser.position());
		}

		try {
			return (Object) constructor.invokeExact();
		} catch (Throwable ex) {
			throw error(raw.getName() + " を作れませんでした: " + ex, parser.position());
		}

	}

	// endregion

	// region Bean と record

	private Object readObject (Class<?> raw, Type type) {

		BindingModel model = BindingModel.of(raw);

		if (model.unsupported != null) {
			throw error(model.unsupported, parser.position());
		}

		java.util.Map<TypeVariable<?>, Type> bindings = type instanceof Class<?> && raw.getTypeParameters().length == 0
			&& !(raw.getGenericSuperclass() instanceof java.lang.reflect.ParameterizedType)
			? java.util.Map.of()
			: Types.bindings(type);

		return model.record ? readRecord(raw, model, bindings) : readBean(raw, model, bindings);

	}

	private Object readBean (Class<?> raw, BindingModel model, java.util.Map<TypeVariable<?>, Type> bindings) {

		Object bean;
		try {
			bean = (Object) model.factory.invokeExact();
		} catch (Throwable ex) {
			throw error(raw.getName() + " を作れませんでした: " + ex, parser.position());
		}

		if (!parser.open('{', '}', raw.getSimpleName() + " のオブジェクト（{）")) {
			return bean;
		}

		push("");
		boolean snakeCase = options.snakeCaseKeys();

		do {
			String key = parser.readKey();
			path[pathDepth - 1] = key;

			BindingModel.Property property = model.property(key, snakeCase);
			if (property == null) {
				// 知らないキーは読み飛ばす
				parser.readValue();
				continue;
			}

			int at = parser.position();
			Object value = property.plan() != null ? read(property.plan()) : read(Types.resolve(property.type(), bindings));
			try {
				property.setter().invokeExact(bean, value);
			} catch (RuntimeException ex) {
				throw error(raw.getSimpleName() + "." + key + " に入れられませんでした: " + ex, at);
			} catch (Throwable ex) {
				throw error(raw.getSimpleName() + "." + key + " に入れられませんでした: " + ex, at);
			}
		} while (parser.next('}'));

		pop();
		return bean;

	}

	private Object readRecord (Class<?> raw, BindingModel model, java.util.Map<TypeVariable<?>, Type> bindings) {

		int count = model.componentClasses.length;
		Object[] args = new Object[count];
		for (int i = 0; i < count; i++) {
			args[i] = Coercion.defaultValue(model.componentClasses[i]);
		}

		int start = parser.position();

		if (parser.open('{', '}', raw.getSimpleName() + " のオブジェクト（{）")) {

			push("");
			boolean snakeCase = options.snakeCaseKeys();

			do {
				String key = parser.readKey();
				path[pathDepth - 1] = key;

				int index = model.component(key, snakeCase);
				if (index < 0) {
					parser.readValue();
					continue;
				}

				ReadPlan componentPlan = model.componentPlans[index];
				args[index] = componentPlan != null ? read(componentPlan) : read(Types.resolve(model.componentTypes[index], bindings));
			} while (parser.next('}'));

			pop();

		}

		try {
			return (Object) model.factory.invokeExact(args);
		} catch (RuntimeException ex) {
			throw error(raw.getSimpleName() + " を作れませんでした（コンストラクタが断りました）: " + ex.getMessage(), start);
		} catch (Throwable ex) {
			throw error(raw.getSimpleName() + " を作れませんでした: " + ex, start);
		}

	}

	// endregion

	// region 場所

	private void push (Object key) {

		if (pathDepth == path.length) {
			path = java.util.Arrays.copyOf(path, pathDepth * 2);
		}
		path[pathDepth++] = key;

	}

	private void pop () {

		path[--pathDepth] = null;

	}

	/**
	 * 場所（{@code $.items[0].price}）と行・文字の位置を付けた例外
	 */
	private JsonException error (String message, int at) {

		StringBuilder sb = new StringBuilder("$");
		for (int i = 0; i < pathDepth; i++) {
			Object key = path[i];
			if (key instanceof Integer index) {
				sb.append('[').append(index).append(']');
			} else if (key != null && !"".equals(key)) {
				sb.append('.').append(key);
			}
		}

		return parser.errorAt(sb + ": " + message, at);

	}

	// endregion

}
