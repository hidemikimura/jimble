package io.jimble.json.internal;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * 型をどう読むか（D-307）
 *
 * <p>
 * <b>型ごとに1回だけ決めて、項目に持たせる。</b>値を1つ読むたびに型を調べ直さない
 * （クラスの名前を比べたり、型の引数の配列を写したりしない）。
 * </p>
 */
final class ReadPlan {

	/**
	 * 読み方の種類
	 */
	enum Kind {

		/** 型の決まっていない値（木で読む） */
		ANY,

		/** 1つの値（文字列・数・真偽・enum・日時など） */
		SCALAR,

		/** Optional */
		OPTIONAL,

		/** 配列 */
		ARRAY,

		/** コレクション */
		COLLECTION,

		/** Map */
		MAP,

		/** Bean / record */
		OBJECT

	}

	/* クラスの種類 */
	private static final ClassValue<Kind> KINDS = new ClassValue<>() {
		@Override
		protected Kind computeValue (Class<?> type) {
			return classify(type);
		}
	};

	/** 種類 */
	final Kind kind;

	/** クラス */
	final Class<?> raw;

	/** 型（Bean / record の型変数を解くため） */
	final Type type;

	/** 要素（配列・コレクション・Optional）か、Map の値の読み方 */
	final ReadPlan element;

	/** Map のキーのクラス */
	final Class<?> keyClass;

	/** Map の値の型が決まっていないか */
	final boolean untypedValues;

	private ReadPlan (Kind kind, Class<?> raw, Type type, ReadPlan element, Class<?> keyClass, boolean untypedValues) {

		this.kind = kind;
		this.raw = raw;
		this.type = type;
		this.element = element;
		this.keyClass = keyClass;
		this.untypedValues = untypedValues;

	}

	/**
	 * 型の読み方を作る
	 *
	 * @param type	型（型変数は解いてあること）
	 * @return	読み方
	 */
	static ReadPlan of (Type type) {

		// 型の引数を持たないクラスは、クラスごとに1回だけ作って覚える
		if (type instanceof Class<?> c && !c.isArray()) {
			return CLASS_PLANS.get(c);
		}

		return build(type);

	}

	/* 型の引数を持たないクラスの読み方 */
	private static final ClassValue<ReadPlan> CLASS_PLANS = new ClassValue<>() {
		@Override
		protected ReadPlan computeValue (Class<?> type) {
			return build(type);
		}
	};

	private static ReadPlan build (Type type) {

		Class<?> raw = Types.raw(type);
		Kind kind = KINDS.get(raw);

		return switch (kind) {
			case OPTIONAL -> new ReadPlan(kind, raw, type, of(Types.argument(type, 0)), null, false);
			case ARRAY -> new ReadPlan(kind, raw, type, of(Types.component(type)), null, false);
			case COLLECTION -> new ReadPlan(kind, raw, type, of(Types.argument(type, 0)), null, false);
			case MAP -> {
				Type valueType = Types.argument(type, 1);
				yield new ReadPlan(kind, raw, type, of(valueType), Types.raw(Types.argument(type, 0)), Types.raw(valueType) == Object.class);
			}
			default -> new ReadPlan(kind, raw, type, null, null, false);
		};

	}

	/**
	 * 型に型変数が残っているか（残っていれば、読むときに解く）
	 */
	static boolean hasTypeVariable (Type type) {

		if (type instanceof TypeVariable<?>) {
			return true;
		}
		if (type instanceof ParameterizedType p) {
			for (Type argument : p.getActualTypeArguments()) {
				if (hasTypeVariable(argument)) {
					return true;
				}
			}
			return false;
		}
		if (type instanceof GenericArrayType a) {
			return hasTypeVariable(a.getGenericComponentType());
		}
		if (type instanceof WildcardType w) {
			return hasTypeVariable(w.getUpperBounds()[0]);
		}
		return false;

	}

	private static Kind classify (Class<?> raw) {

		if (raw == Object.class) {
			return Kind.ANY;
		}
		if (raw == Optional.class) {
			return Kind.OPTIONAL;
		}
		if (raw.isArray()) {
			return Kind.ARRAY;
		}
		if (Collection.class.isAssignableFrom(raw) || raw == Iterable.class) {
			return Kind.COLLECTION;
		}
		if (Map.class.isAssignableFrom(raw)) {
			return Kind.MAP;
		}
		if (raw.isPrimitive() || raw == String.class || raw == CharSequence.class || raw == Boolean.class
			|| raw == Character.class || Number.class.isAssignableFrom(raw) || raw.isEnum()
			|| raw == java.util.UUID.class || raw == java.net.URI.class
			|| raw.getName().startsWith("java.time.") || Date.class.isAssignableFrom(raw) || Calendar.class.isAssignableFrom(raw)) {
			return Kind.SCALAR;
		}
		return Kind.OBJECT;

	}

}
