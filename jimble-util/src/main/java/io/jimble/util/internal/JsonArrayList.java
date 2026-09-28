package io.jimble.util.internal;

import java.util.ArrayList;
import java.util.Collection;

/**
 * DB の JSON の列から読んだ配列（要件 D-189）
 *
 * <p>
 * <b>ふつうの {@link ArrayList} と同じに使える。</b>違うのは、{@code Data.getString} が
 * この値を見たときに「配列の先頭の要素だけを返している」と1度だけ言うことだけである
 * （AI も人も、JSON の文字が返ると思って {@code getString} する。返り値は変えない）。
 * </p>
 */
public class JsonArrayList extends ArrayList<Object> {

	private static final long serialVersionUID = 1L;

	/* getString されたことを1度だけ言うための印（プロセスで1つ） */
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * 初めて getString されたか（2度目からは false）
	 *
	 * @return	初めてなら true
	 */
	public static boolean firstGetString () {

		return WARNED.compareAndSet(false, true);

	}

	/**
	 * もう一度言えるようにする（テスト用）
	 */
	public static void resetWarning () {

		WARNED.set(false);

	}

	/**
	 * 空で作る（直列化のため）
	 */
	public JsonArrayList () {

		super();

	}

	/**
	 * 中身を写して作る
	 *
	 * @param values	中身
	 */
	public JsonArrayList (Collection<?> values) {

		super(values);

	}

}
