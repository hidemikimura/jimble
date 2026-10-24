package io.jimble.converter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 型の引数を持つ型を渡す（D-307）
 *
 * <pre>{@code
 * List<Item> items = Converter.convert(rows, new TypeRef<List<Item>>() {});
 * }</pre>
 *
 * <p>無名の子クラスにして、型の引数を残す（Java は実行時に {@code List<Item>} の Item を消すため）。</p>
 *
 * @param <T>	型
 */
public abstract class TypeRef<T> {

	/* 型 */
	private final Type type;

	/**
	 * コンストラクタ（{@code new TypeRef<List<Item>>() {}} の形で作る）
	 */
	protected TypeRef () {

		Type superType = getClass().getGenericSuperclass();
		if (!(superType instanceof ParameterizedType parameterized)) {
			throw new IllegalStateException("TypeRef は new TypeRef<List<Item>>() {} の形で、型の引数を書いて作ってください");
		}
		this.type = parameterized.getActualTypeArguments()[0];

	}

	/**
	 * 型
	 *
	 * @return	型
	 */
	public Type type () {

		return type;

	}

}
