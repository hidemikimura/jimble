package io.jimble.db.internal.sql.query.where.condition;

/**
 * LIKE の中の {@code %} と {@code _} を逃がす（要件 D-162）
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * <b>{@code contains} / {@code starts_with} / {@code ends_with} に渡すのは、
 * たいてい利用者が検索欄に打った文字である。</b>
 * そのまま {@code LIKE} に載せると、<b>打った記号が命令として効く</b>——
 * </p>
 *
 * <ul>
 *   <li>{@code 50%} を探すと {@code LIKE '%50%%'} になり、<b>「50 で始まる何か」まで拾う</b></li>
 *   <li>{@code a_c} は {@code _} が「任意の1文字」なので <b>{@code abc} に当たる</b></li>
 *   <li>先頭に {@code %} を打たれると<b>索引が効かなくなる</b>——
 *       大きい表なら、それだけで応答が返らなくなる</li>
 * </ul>
 *
 * <p>
 * <b>SQL の注入ではない</b>（値はパラメータで渡している）。
 * <b>出るのは「思ったのと違う行が返る」ことと「遅くなる」ことだけ</b>で、
 * 例外もエラーも出ない。
 * </p>
 *
 * <h2>なぜ {@code !} で逃がすのか</h2>
 * <p>
 * <b>{@code \} は製品と設定で意味が変わる。</b>
 * MySQL は {@code NO_BACKSLASH_ESCAPES} が入っているかどうかで
 * {@code ESCAPE '\'} の書き方が変わってしまう。
 * <b>{@code !} なら、どの製品でも、どの設定でも同じに読める。</b>
 * </p>
 *
 * <p>
 * <b>生のパターンを書きたいときは {@code like(...)} を使うこと</b>——
 * あちらは値をそのまま渡す。
 * </p>
 */
final class LikeEscape {

	/** 逃がすときに前に付ける文字 */
	static final char CHAR = '!';

	/** SQL に足す {@code ESCAPE} 句 */
	static final String CLAUSE = " ESCAPE '!'";

	/**
	 * コンストラクタ
	 *
	 * <p>持ち物は無い。</p>
	 */
	private LikeEscape () {
	}

	/**
	 * {@code %} と {@code _}（と逃がし文字そのもの）を逃がす
	 *
	 * @param value	検索語
	 * @return	逃がしたもの
	 */
	static String escape (String value) {

		if (value == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder(value.length() + 4);

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);

			/*
			 * <b>逃がし文字そのものも逃がす。</b>
			 * そうしないと、{@code !} を打った人の検索が
			 * <b>次の1文字を食べる</b>。
			 */
			if (c == '%' || c == '_' || c == CHAR) {
				sb.append(CHAR);
			}

			sb.append(c);

		}

		return sb.toString();

	}

}
