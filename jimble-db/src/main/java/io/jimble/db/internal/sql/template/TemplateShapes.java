package io.jimble.db.internal.sql.template;

import io.jimble.db.sql.BoundSelect;
import io.jimble.db.sql.SelectBuilder;

import java.util.function.Function;

/**
 * template の形を、jimble の中（SQL 結果キャッシュの依存を求めるところ）にだけ渡す（3.0.0）
 *
 * <p>
 * 形は template が写して持つビルダーで、同時に読まれる。公開メソッドにすると、
 * アプリが書き換えてスレッドをまたいだ組み立てを壊せるので、ここを通して読むだけにする。
 * 渡し方は {@link io.jimble.db.sql.SelectTemplate} が読み込まれたときに 1 回だけ決まる。
 * </p>
 */
public final class TemplateShapes {

	/**
	 * 渡し方
	 *
	 * @param shape		形
	 * @param labels		方言の、位置ごとの結果のキー（3.0.0）
	 * @param cacheable		方言の SELECT 句が値を含まず、結果の列の読み方を覚えてよいか（3.0.0）
	 */
	public record Access (Function<BoundSelect, SelectBuilder> shape, java.util.function.BiFunction<BoundSelect, io.jimble.db.dialect.Dialect, java.util.List<String>> labels,
		java.util.function.BiPredicate<BoundSelect, io.jimble.db.dialect.Dialect> cacheable) {}

	/* 渡し方 */
	private static volatile Access access;

	private TemplateShapes () {}

	/**
	 * 形の求め方を決める（{@link io.jimble.db.sql.SelectTemplate} だけが呼ぶ。2 回目からは何もしない）
	 *
	 * @param function	求め方
	 */
	public static synchronized void install (Access function) {

		// SelectTemplate のほかから決めさせない（先に呼ばれると、キャッシュの依存を違う形で求めてしまう）
		Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		if (caller != io.jimble.db.sql.SelectTemplate.class) {
			throw new IllegalStateException("TemplateShapes.install は SelectTemplate だけが呼べます: " + caller.getName());
		}

		if (access == null) {
			access = function;
		}

	}

	/**
	 * 形（読むだけ。書き換えない）
	 *
	 * @param select	値を入れた SELECT
	 * @return	形
	 */
	public static SelectBuilder shape (BoundSelect select) {

		return access.shape().apply(select);

	}

	/**
	 * 方言の、位置ごとの結果のキー（3.0.0）
	 *
	 * @param select	値を入れた SELECT
	 * @param dialect	方言
	 * @return	キー
	 */
	public static java.util.List<String> labels (BoundSelect select, io.jimble.db.dialect.Dialect dialect) {

		return access.labels().apply(select, dialect);

	}

	/**
	 * 方言の SELECT 句が値を含まず、結果の列の読み方を覚えてよいか（3.0.0）
	 *
	 * @param select	値を入れた SELECT
	 * @param dialect	方言
	 * @return	覚えてよければ true
	 */
	public static boolean cacheable (BoundSelect select, io.jimble.db.dialect.Dialect dialect) {

		return access.cacheable().test(select, dialect);

	}

}
