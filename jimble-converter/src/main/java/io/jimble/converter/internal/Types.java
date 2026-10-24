package io.jimble.converter.internal;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;

/**
 * 型（{@link Type}）の扱い（D-307。jimble-json と同じもの。互いに依存しないため写して持つ）
 *
 * <p>{@code List<Item>} の要素の型や、{@code Box<T>} の T を、読むときに解く。</p>
 */
final class Types {

	private Types () {
	}

	/**
	 * 型のクラス
	 *
	 * @param type	型
	 * @return	クラス（解けなければ Object）
	 */
	static Class<?> raw (Type type) {

		if (type instanceof Class<?> c) {
			return c;
		}
		if (type instanceof ParameterizedType p) {
			return raw(p.getRawType());
		}
		if (type instanceof GenericArrayType a) {
			return java.lang.reflect.Array.newInstance(raw(a.getGenericComponentType()), 0).getClass();
		}
		if (type instanceof WildcardType w) {
			return raw(w.getUpperBounds()[0]);
		}
		if (type instanceof TypeVariable<?> v) {
			return raw(v.getBounds()[0]);
		}
		return Object.class;

	}

	/**
	 * 型の引数（{@code List<Item>} なら Item）
	 *
	 * @param type	型
	 * @param index	何番目か
	 * @return	引数（無ければ Object）
	 */
	static Type argument (Type type, int index) {

		if (type instanceof ParameterizedType p && p.getActualTypeArguments().length > index) {
			return p.getActualTypeArguments()[index];
		}
		return Object.class;

	}

	/**
	 * 配列の要素の型
	 */
	static Type component (Type type) {

		if (type instanceof GenericArrayType a) {
			return a.getGenericComponentType();
		}
		return raw(type).getComponentType();

	}

	/**
	 * クラスの型変数に、実際の型を当てる表を作る（親クラスの宣言もたどる）
	 *
	 * @param type	読む型（{@code Box<Item>} など）
	 * @return	型変数 → 実際の型
	 */
	static Map<TypeVariable<?>, Type> bindings (Type type) {

		Map<TypeVariable<?>, Type> map = new HashMap<>();
		collect(type, map);
		return map;

	}

	private static void collect (Type type, Map<TypeVariable<?>, Type> map) {

		Class<?> raw = raw(type);
		if (raw == Object.class || raw == null) {
			return;
		}

		if (type instanceof ParameterizedType p) {
			TypeVariable<?>[] variables = raw.getTypeParameters();
			Type[] actual = p.getActualTypeArguments();
			for (int i = 0; i < variables.length && i < actual.length; i++) {
				map.put(variables[i], resolve(actual[i], map));
			}
		}

		Type superType = raw.getGenericSuperclass();
		if (superType != null) {
			collect(superType, map);
		}

	}

	/**
	 * 型の中の型変数を、表で置き換える
	 *
	 * @param type		型
	 * @param bindings	型変数 → 実際の型
	 * @return	置き換えた型
	 */
	static Type resolve (Type type, Map<TypeVariable<?>, Type> bindings) {

		if (bindings.isEmpty()) {
			return type;
		}

		if (type instanceof TypeVariable<?> v) {
			Type bound = bindings.get(v);
			return bound != null ? bound : type;
		}

		if (type instanceof ParameterizedType p) {
			Type[] args = p.getActualTypeArguments().clone();
			boolean changed = false;
			for (int i = 0; i < args.length; i++) {
				Type resolved = resolve(args[i], bindings);
				changed |= resolved != args[i];
				args[i] = resolved;
			}
			return changed ? new Parameterized(raw(p.getRawType()), args) : p;
		}

		if (type instanceof GenericArrayType a) {
			Type component = resolve(a.getGenericComponentType(), bindings);
			return java.lang.reflect.Array.newInstance(raw(component), 0).getClass();
		}

		return type;

	}

	/**
	 * 型変数を置き換えた {@code ParameterizedType}
	 */
	record Parameterized (Class<?> rawType, Type[] arguments) implements ParameterizedType {

		@Override
		public Type[] getActualTypeArguments () {
			return arguments.clone();
		}

		@Override
		public Type getRawType () {
			return rawType;
		}

		@Override
		public Type getOwnerType () {
			return null;
		}

	}

}
