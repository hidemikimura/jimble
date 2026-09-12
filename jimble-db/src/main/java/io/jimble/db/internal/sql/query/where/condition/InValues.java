package io.jimble.db.internal.sql.query.where.condition;

import io.jimble.db.sql.SqlBuildException;

import java.util.Collection;

/**
 * {@code IN} / {@code NOT IN} に渡された値の見張り（要件 F-D-07）
 *
 * <p>
 * {@link In} と {@link NotIn} は同じ書き出し方をする。
 * <b>見張りを片方にだけ足すと、もう片方だけが静かに壊れたままになる</b>ので、
 * ここに1つ置いて両方から呼ぶ。
 * </p>
 */
final class InValues {

	private InValues () {}

	/**
	 * 空のまま SQL にしようとしていないか
	 *
	 * <p>
	 * 空のコレクションを渡すと {@code IN ()} という<b>構文エラーの SQL</b> になる。
	 * 移送元はこれをそのまま DB に投げていたので、
	 * <b>DB のエラーメッセージから呼び出し箇所に辿り着けなかった</b>。
	 * 組み立てた時点で落とす。
	 * </p>
	 *
	 * <p>
	 * <b>「空なら条件ごと外す」ことはしない。</b>
	 * {@code in(空)} は「どれにも当たらない」であり、
	 * 条件を外すと<b>全件が返る</b>。取り違えると静かに全件消したり全件見せたりする。
	 * どちらの意味なのかは呼び出し側にしか分からないので、呼び出し側に決めさせる。
	 * </p>
	 *
	 * @param value		渡された値
	 * @param keyword	{@code IN} または {@code NOT IN}
	 */
	static void check (Object value, String keyword) {

		int size;

		if (value == null) {
			/*
			 * IN (NULL) はどの行にも当たらない。
			 * <b>0 件が返るだけで例外もエラーも出ない</b>ので、
			 * 一覧のつもりで null を渡した取り違えが表に出ない。
			 */
			size = 0;
		} else if (value instanceof Collection<?> list) {
			size = list.size();
		} else if (value.getClass().isArray()) {
			size = ((Object[]) value).length;
		} else {
			return;
		}

		if (size > 0) {
			return;
		}

		throw new SqlBuildException(
			("%s に空の一覧（または null）が渡されました。"
				+ "そのままでは壊れた SQL になります。"
				+ "「どれにも当たらない」なら 1 = 0 などを書き、"
				+ "「条件を付けない」なら呼び出し側で分岐してください")
				.formatted(keyword));

	}

}
