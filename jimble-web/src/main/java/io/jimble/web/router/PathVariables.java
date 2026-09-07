package io.jimble.web.router;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * パス変数
 *
 * <p>
 * {@code /{id}} の値と、ワイルドカード {@code /*} にマッチした残りを持つ。
 * ワイルドカードのキーは {@value #WILDCARD}。
 * </p>
 */
public final class PathVariables {

	/** ワイルドカードのキー */
	public static final String WILDCARD = "*";

	/* 値 */
	private final Map<String, String> values;

	/**
	 * コンストラクタ
	 *
	 * @param values	値
	 */
	PathVariables (Map<String, String> values) {

		this.values = values;

	}

	/**
	 * 空のパス変数を作る
	 *
	 * @return	パス変数
	 */
	static PathVariables empty () {

		return new PathVariables(new LinkedHashMap<>());

	}

	/**
	 * 値を取得する
	 *
	 * @param name	変数名
	 * @return	値。無ければ null
	 */
	public String get (String name) {

		return values.get(name);

	}

	/**
	 * ワイルドカードにマッチした残り
	 *
	 * @return	残り。無ければ null
	 */
	public String wildcard () {

		return values.get(WILDCARD);

	}

	/**
	 * 全ての値
	 *
	 * @return	値
	 */
	public Map<String, String> values () {

		return Map.copyOf(values);

	}

	/**
	 * 値を設定する
	 *
	 * @param name	変数名
	 * @param value	値
	 */
	void put (String name, String value) {

		values.put(name, value);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return values.toString();

	}

}
