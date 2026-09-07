package io.jimble.db.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * マイグレーション SQL の解析
 *
 * <p>
 * DB に触らない純粋な処理だけを集めてある。<b>DB なしで単体テストできる</b>ようにするため。
 * </p>
 */
public final class MigrationSql {

	/** up の開始マーカー */
	public static final String MARKER_UPS = "# --- !Ups";

	/** down の開始マーカー */
	public static final String MARKER_DOWNS = "# --- !Downs";

	private MigrationSql () {}

	/**
	 * SQL を up と down に分割する
	 *
	 * <p>
	 * {@value #MARKER_UPS} がなければ全体を up とみなす。
	 * {@value #MARKER_DOWNS} がなければ down は空文字になる。
	 * </p>
	 *
	 * @param sql	SQL 全文
	 * @return	{@code [up, down]}（どちらも null にはならない）
	 */
	public static String[] toUpDown (String sql) {

		String[] res = new String[]{"", ""};

		if (sql == null) {
			return res;
		}

		int downStart = sql.indexOf(MARKER_DOWNS);

		{
			int start = sql.indexOf(MARKER_UPS);
			if (start < 0) {
				start = 0;
			} else {
				start += MARKER_UPS.length();
			}
			if (downStart < 0) {
				res[0] = sql.substring(start).trim();
			} else {
				res[0] = sql.substring(start, downStart).trim();
			}
		}

		if (downStart >= 0) {
			res[1] = sql.substring(downStart + MARKER_DOWNS.length()).trim();
		}

		return res;

	}

	/**
	 * 複数の SQL を「;」で分割する
	 *
	 * <p>
	 * 文字列リテラル（シングルクォート）の中の「;」では分割しない。
	 * バックスラッシュによるエスケープも見る。
	 * </p>
	 *
	 * @param sqls	SQL（複数文）
	 * @return	SQL 1文ずつのリスト（空文は含まない）
	 */
	public static List<String> split (String sqls) {

		List<String> res = new ArrayList<>();

		if (sqls == null) {
			return res;
		}

		boolean inQuote = false;
		boolean beforeEscape = false;
		StringBuilder sb = new StringBuilder();

		for (char c : sqls.toCharArray()) {

			sb.append(c);

			if (c == '\'') {
				if (!beforeEscape) {
					inQuote = !inQuote;
				}
				beforeEscape = false;
			} else if (c == '\\') {
				// エスケープはリテラルの中だけで意味を持つ
				beforeEscape = inQuote;
			} else if (c == ';') {
				if (!inQuote) {
					addIfNotBlank(res, sb.toString());
					sb.setLength(0);
				}
				beforeEscape = false;
			} else {
				beforeEscape = false;
			}

		}

		addIfNotBlank(res, sb.toString());

		return res;

	}

	/**
	 * 空でなければ追加する
	 *
	 * <p>
	 * 移送元は「;」だけの断片や末尾の空白も1文として実行していた。
	 * <b>空振りの実行がマイグレーション履歴に残っていた</b>ので、ここで落とす。
	 * </p>
	 *
	 * @param list	追加先
	 * @param sql	SQL
	 */
	private static void addIfNotBlank (List<String> list, String sql) {

		String trimmed = sql.trim();
		if (trimmed.isEmpty() || ";".equals(trimmed)) {
			return;
		}

		list.add(trimmed);

	}

}
