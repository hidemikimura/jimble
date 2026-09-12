package io.jimble.util.internal.array;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * 配列を型を問わず扱う（要件 D-162）
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * <b>{@code Class#isArray()} は {@code long[]} にも true を返すが、
 * {@code long[]} は {@code Object[]} ではない。</b>
 * だから
 * </p>
 *
 * <pre>
 * if (value.getClass().isArray()) {
 *     Object[] array = (Object[]) value;	// long[] はここで落ちる
 * }
 * </pre>
 *
 * <p>
 * という書き方は、<b>受け付けるつもりで書いた分岐が、素の配列だけ必ず落とす</b>。
 * 出るのは {@code class [J cannot be cast to class [Ljava.lang.Object;} で、
 * <b>読んでも何を直せばよいか分からない</b>。
 * </p>
 *
 * <p>
 * 棚卸ししたら<b>枠組みの中に同じ書き方が 12 か所</b>あった——
 * {@code IN} 句、パラメータの展開、ログ、CSV、マージ。<b>全部ここに寄せた。</b>
 * </p>
 */
public final class ArrayUtil {

	/**
	 * コンストラクタ
	 *
	 * <p>持ち物は無い。</p>
	 */
	private ArrayUtil () {
	}

	/**
	 * 配列の長さ
	 *
	 * @param array	配列（{@code null} なら 0）
	 * @return	長さ
	 */
	public static int length (Object array) {

		return array == null ? 0 : Array.getLength(array);

	}

	/**
	 * 配列の1つ
	 *
	 * @param array	配列
	 * @param index	位置
	 * @return	中身（素の型は箱に入って返る）
	 */
	public static Object get (Object array, int index) {

		return Array.get(array, index);

	}

	/**
	 * 配列を一覧にする
	 *
	 * <p><b>素の型は箱に入って返る</b>（{@code long} なら {@code Long}）。</p>
	 *
	 * @param array	配列（{@code null} なら空）
	 * @return	一覧
	 */
	public static List<Object> toList (Object array) {

		if (array == null) {
			return new ArrayList<>();
		}

		int length = Array.getLength(array);

		List<Object> res = new ArrayList<>(length);

		for (int i = 0; i < length; i++) {
			res.add(Array.get(array, i));
		}

		return res;

	}

}
