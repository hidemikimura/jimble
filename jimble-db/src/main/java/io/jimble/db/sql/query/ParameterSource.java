package io.jimble.db.sql.query;

import io.jimble.db.internal.sql.query.parameter.Parameter;

import java.util.List;

/**
 * SQL のパラメータを持つ部品（SELECT・WHERE・FROM・ORDER BY の共通の親。2.7.5）
 *
 * <p>
 * {@link #collectParameters(List)} の既定の形をここに 1 つだけ置く。
 * 子のインターフェースごとに置くと、2 つ以上を実装するクラス（列や関数）で既定がぶつかり、
 * アプリが書いた同じ形のクラスもコンパイルできなくなる。
 * </p>
 */
public interface ParameterSource {

	/**
	 * パラメータ存在判定
	 *
	 * @return	存在する場合 = true
	 */
	boolean hasParameter();

	/**
	 * パラメータ取得
	 *
	 * @return	パラメータ
	 */
	Object getParameter();

	/**
	 * パラメータを平らにして足す
	 *
	 * <p>
	 * {@code hasParameter()} が true なら {@code getParameter()} を平らにして {@code out} に足す。
	 * jimble の部品は上書きして、木を 1 回たどるだけで済ませている
	 * （2.7.4 までは {@code hasParameter()} と {@code getParameter()} で 2 回たどり、入れ子のリストを作ってから平らにしていた）。
	 * 自分で実装したクラスは上書きしなくてよい。
	 * </p>
	 *
	 * @param out	足す先
	 */
	default void collectParameters (List<Object> out) {

		collectByGetParameter(this, out);

	}

	/**
	 * {@code hasParameter()} / {@code getParameter()} を通して足す（既定の形）
	 *
	 * <p>
	 * jimble の部品は {@link #collectParameters(List)} を上書きして速い道を通るが、
	 * <b>アプリがその部品を継承して {@code hasParameter()} / {@code getParameter()} を上書きしていたら、こちらを通す</b>
	 * （速い道は上書きした値を見ないので、パラメータが抜ける）。
	 * </p>
	 *
	 * @param source	部品
	 * @param out		足す先
	 */
	static void collectByGetParameter (ParameterSource source, List<Object> out) {

		if (source.hasParameter()) {
			Parameter.flattenInto(source.getParameter(), out);
		}

	}

}
