package io.jimble.db.migration;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.dialect.SqlSyntax;

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
		int upStart = sql.indexOf(MARKER_UPS);

		if (downStart >= 0 && upStart > downStart) {
			throw new MigrationException(
				"%s が %s より前にあります。上から順に書いてください".formatted(MARKER_DOWNS, MARKER_UPS));
		}

		{
			int start = upStart;
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
	 * 複数の SQL を「;」で分割する（既定の方言）
	 *
	 * <p>
	 * <b>どの製品か分かっているなら {@link #split(String, Dialect)} を使うこと。</b>
	 * こちらは主データソースの方言（{@link Dialects#defaultDialect()}）で読む。
	 * </p>
	 *
	 * @param sqls	SQL（複数文）
	 * @return	SQL 1文ずつのリスト
	 */
	public static List<String> split (String sqls) {

		return split(sqls, Dialects.defaultDialect());

	}

	/**
	 * 複数の SQL を「;」で分割する
	 *
	 * <p>
	 * 文字列・識別子・コメントの中の「;」では分割しない。
	 * <b>どこからどこまでがそれなのかは製品で違う</b>ので、方言に聞く
	 * （{@link Dialect#sqlSyntax()}）。
	 * </p>
	 *
	 * <table>
	 *   <caption>読み分けるもの</caption>
	 *   <tr><th>書き方</th><th>扱い</th></tr>
	 *   <tr><td>{@code '...'}</td><td>文字列。{@code ''} は文字列の中の「'」</td></tr>
	 *   <tr><td>{@code "..."} / {@code `...`}</td><td>同じように閉じるまで読む（識別子か文字列かは製品による）</td></tr>
	 *   <tr><td>{@code \}</td><td>MySQL では次の1文字を打ち消す。<b>PostgreSQL ではただの文字</b>（{@code E'...'} のときだけ打ち消す）</td></tr>
	 *   <tr><td>{@code $tag$...$tag$}</td><td>PostgreSQL の文字列（関数の本体や {@code DO} ブロック）</td></tr>
	 *   <tr><td>{@code --} / {@code #}</td><td>行末までコメント（{@code #} は MySQL だけ）</td></tr>
	 *   <tr><td><code>/*</code> … <code>*&#47;</code></td><td>コメント。PostgreSQL は入れ子にできる</td></tr>
	 * </table>
	 *
	 * <p>
	 * <b>コメントだけの断片は捨てる。</b>残すと「Query was empty」で落ちる（MySQL）か、
	 * 何もしない1文がマイグレーション履歴に残る。
	 * </p>
	 *
	 * @param sqls		SQL（複数文）
	 * @param dialect	方言（null なら既定）
	 * @return	SQL 1文ずつのリスト（空文は含まない）
	 */
	public static List<String> split (String sqls, Dialect dialect) {

		List<String> res = new ArrayList<>();

		if (sqls == null) {
			return res;
		}

		SqlSyntax syntax = (dialect == null ? Dialects.defaultDialect() : dialect).sqlSyntax();

		StringBuilder sb = new StringBuilder();
		boolean hasCode = false;
		int i = 0;

		while (i < sqls.length()) {

			char c = sqls.charAt(i);

			int skipped = skipComment(sqls, i, syntax, sb);
			if (skipped > i) {
				// /*! ... */ は MySQL では実行されるので、コメントだけとして捨てない
				hasCode = hasCode || isExecutableComment(sqls, i, syntax);
				i = skipped;
				continue;
			}

			skipped = skipDollarQuote(sqls, i, syntax, sb);
			if (skipped > i) {
				hasCode = true;
				i = skipped;
				continue;
			}

			if (c == '\'' || c == '"' || (syntax.backtickQuote() && c == '`')) {
				hasCode = true;
				i = skipQuoted(sqls, i, syntax, sb);
				continue;
			}

			sb.append(c);
			i++;

			if (c == ';') {
				if (hasCode) {
					addIfNotBlank(res, sb.toString());
				}
				sb.setLength(0);
				hasCode = false;
				continue;
			}

			if (!Character.isWhitespace(c)) {
				hasCode = true;
			}

		}

		if (hasCode) {
			addIfNotBlank(res, sb.toString());
		}

		return res;

	}

	/**
	 * コメントを読み飛ばす
	 *
	 * <p>
	 * <b>中身はそのまま残す</b>（消すと、マイグレーション履歴に残る SQL が
	 * 書いたものと違うものになる）。読み飛ばすのは「ここでは「;」で切らない」という意味である。
	 * </p>
	 *
	 * @param sqls		SQL
	 * @param start		いまの位置
	 * @param syntax	字面の決まり
	 * @param sb		書き出し先
	 * @return	次に読む位置（コメントでなければ {@code start} のまま）
	 */
	private static int skipComment (String sqls, int start, SqlSyntax syntax, StringBuilder sb) {

		char c = sqls.charAt(start);
		char next = start + 1 < sqls.length() ? sqls.charAt(start + 1) : '\0';

		if (isLineComment(sqls, start, syntax)) {

			int i = start;
			while (i < sqls.length() && sqls.charAt(i) != '\n' && sqls.charAt(i) != '\r') {
				sb.append(sqls.charAt(i));
				i++;
			}

			return i;

		}

		if (c != '/' || next != '*') {
			return start;
		}

		int depth = 1;
		int i = start + 2;
		sb.append("/*");

		while (i < sqls.length()) {

			char now = sqls.charAt(i);
			char after = i + 1 < sqls.length() ? sqls.charAt(i + 1) : '\0';

			if (syntax.nestedBlockComment() && now == '/' && after == '*') {
				depth++;
				sb.append("/*");
				i += 2;
				continue;
			}

			if (now == '*' && after == '/') {
				depth--;
				sb.append("*/");
				i += 2;
				if (depth == 0) {
					return i;
				}
				continue;
			}

			sb.append(now);
			i++;

		}

		// 閉じていない。残り全部がコメント
		return i;

	}

	/**
	 * 実行されるコメントか
	 *
	 * <p>
	 * MySQL の {@code /*!40101 ...} と MariaDB の {@code /*M!100301 ...} は
	 * <b>コメントの形をしているが実行される</b>。捨てると、その1文だけが黙って流れない。
	 * PostgreSQL ではただのコメントなので、捨ててよい。
	 * </p>
	 *
	 * @param sqls		SQL
	 * @param start		いまの位置
	 * @param syntax	字面の決まり
	 * @return	そうなら true
	 */
	private static boolean isExecutableComment (String sqls, int start, SqlSyntax syntax) {

		return syntax.executableComment()
			&& (sqls.startsWith("/*!", start) || sqls.startsWith("/*M!", start));

	}

	/**
	 * 行コメントの始まりか
	 *
	 * <p>
	 * <b>MySQL の {@code --} は、あとに空白が要る。</b>
	 * {@code select 1--2} は「1 引く マイナス2」であってコメントではない。
	 * PostgreSQL は空白が無くてもコメントになる。
	 * </p>
	 *
	 * @param sqls		SQL
	 * @param start		いまの位置
	 * @param syntax	字面の決まり
	 * @return	そうなら true
	 */
	private static boolean isLineComment (String sqls, int start, SqlSyntax syntax) {

		char c = sqls.charAt(start);

		if (syntax.hashComment() && c == '#') {
			return true;
		}

		if (c != '-' || start + 1 >= sqls.length() || sqls.charAt(start + 1) != '-') {
			return false;
		}

		if (!syntax.dashCommentNeedsSpace()) {
			return true;
		}

		// 「--」で終わっているなら、そのあとは何も無いのでコメントとみなす
		return start + 2 >= sqls.length() || Character.isWhitespace(sqls.charAt(start + 2));

	}

	/**
	 * ドル引用符（{@code $tag$ ... $tag$}）を読み飛ばす
	 *
	 * <p>
	 * PostgreSQL の関数の本体や {@code DO} ブロックはこの形で書く。
	 * <b>中に「;」がいくつも入る</b>ので、ここで切ると関数が壊れる。
	 * </p>
	 *
	 * @param sqls		SQL
	 * @param start		いまの位置
	 * @param syntax	字面の決まり
	 * @param sb		書き出し先
	 * @return	次に読む位置（ドル引用符でなければ {@code start} のまま）
	 */
	private static int skipDollarQuote (String sqls, int start, SqlSyntax syntax, StringBuilder sb) {

		if (!syntax.dollarQuote() || sqls.charAt(start) != '$' || !isTokenStart(sqls, start)) {
			return start;
		}

		String tag = dollarTag(sqls, start);
		if (tag == null) {
			return start;
		}

		int end = sqls.indexOf(tag, start + tag.length());

		if (end < 0) {
			// 閉じていない。残り全部が文字列
			sb.append(sqls, start, sqls.length());
			return sqls.length();
		}

		sb.append(sqls, start, end + tag.length());

		return end + tag.length();

	}

	/**
	 * ドル引用符の目印を取り出す
	 *
	 * <p>{@code $$} なら {@code $$}、{@code $body$} なら {@code $body$}。</p>
	 *
	 * @param sqls	SQL
	 * @param start	「$」の位置
	 * @return	目印（ドル引用符でなければ null）
	 */
	private static String dollarTag (String sqls, int start) {

		int i = start + 1;

		while (i < sqls.length()) {

			char c = sqls.charAt(i);

			if (c == '$') {
				return sqls.substring(start, i + 1);
			}

			/*
			 * 目印に使えるのは識別子と同じ文字で、数字では始まらない。
			 * 数字を許すと PostgreSQL の位置パラメータ（$1）が引用符の始まりに見える
			 */
			boolean usable = i == start + 1
				? Character.isLetter(c) || c == '_'
				: Character.isLetterOrDigit(c) || c == '_';

			if (!usable) {
				return null;
			}

			i++;

		}

		return null;

	}

	/**
	 * トークンの先頭か
	 *
	 * <p>
	 * PostgreSQL は識別子の中に {@code $} を書けるので、
	 * <b>{@code a$b$c} の {@code $b$} をドル引用符と読むと、
	 * そこから後ろの SQL が全部1つの文字列になる。</b>
	 * ドル引用符になれるのは、識別子の途中ではない {@code $} だけである。
	 * </p>
	 *
	 * @param sqls	SQL
	 * @param start	いまの位置
	 * @return	そうなら true
	 */
	private static boolean isTokenStart (String sqls, int start) {

		if (start == 0) {
			return true;
		}

		char before = sqls.charAt(start - 1);

		return !Character.isLetterOrDigit(before) && before != '_' && before != '$';

	}

	/**
	 * 引用符で囲まれたところを読み飛ばす
	 *
	 * @param sqls		SQL
	 * @param start		開きの引用符の位置
	 * @param syntax	字面の決まり
	 * @param sb		書き出し先
	 * @return	次に読む位置
	 */
	private static int skipQuoted (String sqls, int start, SqlSyntax syntax, StringBuilder sb) {

		char quote = sqls.charAt(start);
		boolean escape = syntax.backslashEscape() || isEscapeString(sqls, start);

		sb.append(quote);

		int i = start + 1;

		while (i < sqls.length()) {

			char c = sqls.charAt(i);

			if (escape && c == '\\' && i + 1 < sqls.length()) {
				sb.append(c).append(sqls.charAt(i + 1));
				i += 2;
				continue;
			}

			sb.append(c);
			i++;

			if (c != quote) {
				continue;
			}

			// 同じ引用符が2つ続くのは「中身としての引用符」
			if (i < sqls.length() && sqls.charAt(i) == quote) {
				sb.append(quote);
				i++;
				continue;
			}

			return i;

		}

		// 閉じていない。残り全部が文字列
		return i;

	}

	/**
	 * PostgreSQL の {@code E'...'} か
	 *
	 * <p>これだけはバックスラッシュがエスケープになる。</p>
	 *
	 * @param sqls	SQL
	 * @param start	「'」の位置
	 * @return	そうなら true
	 */
	private static boolean isEscapeString (String sqls, int start) {

		if (sqls.charAt(start) != '\'' || start == 0) {
			return false;
		}

		char before = sqls.charAt(start - 1);
		if (before != 'E' && before != 'e') {
			return false;
		}

		if (start == 1) {
			return true;
		}

		// 識別子の末尾の e ではないこと（table_e'...' のような形を除く）
		char beforeBefore = sqls.charAt(start - 2);

		return !Character.isLetterOrDigit(beforeBefore) && beforeBefore != '_';

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
