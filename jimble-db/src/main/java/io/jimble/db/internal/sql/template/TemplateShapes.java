package io.jimble.db.internal.sql.template;

import io.jimble.db.sql.BoundSelect;
import io.jimble.db.sql.SelectBuilder;

import java.util.function.Function;

/**
 * template の形を、jimble の中（SQL 結果キャッシュの依存を求めるところ）にだけ渡す（2.8.0）
 *
 * <p>
 * 形は template が写して持つビルダーで、同時に読まれる。公開メソッドにすると、
 * アプリが書き換えてスレッドをまたいだ組み立てを壊せるので、ここを通して読むだけにする。
 * 渡し方は {@link io.jimble.db.sql.SelectTemplate} が読み込まれたときに 1 回だけ決まる。
 * </p>
 */
public final class TemplateShapes {

	/* 形の求め方 */
	private static volatile Function<BoundSelect, SelectBuilder> shape;

	private TemplateShapes () {}

	/**
	 * 形の求め方を決める（{@link io.jimble.db.sql.SelectTemplate} だけが呼ぶ。2 回目からは何もしない）
	 *
	 * @param function	求め方
	 */
	public static synchronized void install (Function<BoundSelect, SelectBuilder> function) {

		// SelectTemplate のほかから決めさせない（先に呼ばれると、キャッシュの依存を違う形で求めてしまう）
		Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		if (caller != io.jimble.db.sql.SelectTemplate.class) {
			throw new IllegalStateException("TemplateShapes.install は SelectTemplate だけが呼べます: " + caller.getName());
		}

		if (shape == null) {
			shape = function;
		}

	}

	/**
	 * 形（読むだけ。書き換えない）
	 *
	 * @param select	値を入れた SELECT
	 * @return	形
	 */
	public static SelectBuilder shape (BoundSelect select) {

		return shape.apply(select);

	}

}
