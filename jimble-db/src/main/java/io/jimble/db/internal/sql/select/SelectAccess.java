package io.jimble.db.internal.sql.select;

import io.jimble.db.sql.SelectBuilder;

import java.util.List;
import java.util.function.Function;

/**
 * {@link SelectBuilder} の中身を jimble の中（DB）にだけ渡す（3.0.0）
 *
 * <p>
 * 位置ごとの結果のキーと、主キーだけを引く写しは、公開メソッドにするとアプリが頼れる形になるので、ここを通して渡す。
 * 渡し方は {@link SelectBuilder} が読み込まれたときに 1 回だけ決まる。
 * </p>
 */
public final class SelectAccess {

	/**
	 * 渡し方
	 *
	 * @param labels		直前の sql() で書いた、位置ごとの結果のキー（null の位置はドライバの列名。sql() の前なら null）
	 * @param cacheable		直前の sql() の SELECT 句が値を含まず、結果の列の読み方を覚えてよいか
	 * @param simple		主キーだけを SELECT する写し
	 */
	public record Access (Function<SelectBuilder, List<String>> labels, java.util.function.Predicate<SelectBuilder> cacheable, Function<SelectBuilder, SelectBuilder> simple) {}

	/* 渡し方 */
	private static volatile Access access;

	private SelectAccess () {}

	/**
	 * 渡し方を決める（{@link SelectBuilder} だけが呼ぶ。2 回目からは何もしない）
	 *
	 * @param value	渡し方
	 */
	public static synchronized void install (Access value) {

		Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		if (caller != SelectBuilder.class) {
			throw new IllegalStateException("SelectAccess.install は SelectBuilder だけが呼べます: " + caller.getName());
		}

		if (access == null) {
			access = value;
		}

	}

	/**
	 * 直前の sql() で書いた、位置ごとの結果のキー
	 *
	 * @param builder	ビルダー
	 * @return	キー（sql() の前なら null）
	 */
	public static List<String> labels (SelectBuilder builder) {

		return access(builder).labels().apply(builder);

	}

	/**
	 * 直前の sql() の SELECT 句が値を含まず、結果の列の読み方を覚えてよいか
	 *
	 * @param builder	ビルダー
	 * @return	覚えてよければ true
	 */
	public static boolean cacheable (SelectBuilder builder) {

		return access(builder).cacheable().test(builder);

	}

	/**
	 * 主キーだけを SELECT する写し
	 *
	 * @param builder	ビルダー
	 * @return	写し
	 */
	public static SelectBuilder simple (SelectBuilder builder) {

		return access(builder).simple().apply(builder);

	}

	private static Access access (SelectBuilder builder) {

		Access current = access;
		if (current == null) {
			// SelectBuilder を作れば読み込まれているはず
			throw new IllegalStateException("SelectAccess が決まっていません: " + builder);
		}
		return current;

	}

}
