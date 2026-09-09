package io.jimble.db.dialect;

/**
 * SQL の字面の決まり（要件 F-D-30 / F-G-05）
 *
 * <p>
 * <b>SQL を「読む」ときだけに要るもの</b>を集めてある。
 * マイグレーションの SQL を「;」で1文ずつに切り分けるとき
 * （{@code MigrationSql#split}）、どこからどこまでが文字列やコメントなのかが
 * <b>製品で違う</b>ので、それを外から見えるようにしたものである。
 * </p>
 *
 * <p>
 * 決まりを間違えると、切り分けの結果が黙って変わる。たとえば
 * {@code insert into t values ('c:\');} は、バックスラッシュをエスケープとして読むと
 * <b>そこで文字列が終わらないことになり、後ろの SQL が全部1文につながる</b>。
 * </p>
 *
 * @param backslashEscape		文字列リテラルの中でバックスラッシュがエスケープになるか。
 *								MySQL は なる。<b>PostgreSQL は既定で ならない</b>
 *								（{@code standard_conforming_strings = on}。ただし
 *								{@code E'...'} と書いたときだけ なる）
 * @param hashComment			{@code #} から行末までがコメントになるか。MySQL だけ
 * @param backtickQuote			{@code `} で囲めるか（識別子）。MySQL だけ。
 *								<b>PostgreSQL でこれを囲みとして読むと、書き間違えた
 *								バックティック1つで後ろの SQL が全部くっつく</b>
 * @param executableComment		{@code /*!...} が<b>実行されるコメント</b>になるか。
 *								MySQL / MariaDB だけ（{@code /*!40101 ...} / {@code /*M!100301 ...}）。
 *								コメントに見えて中身が動くので、捨ててはいけない
 * @param dashCommentNeedsSpace	{@code --} のあとに空白が要るか。<b>MySQL は要る</b>
 *								（{@code 1--2} は「1 引く マイナス2」であってコメントではない）。
 *								PostgreSQL は要らない
 * @param dollarQuote			{@code $tag$ ... $tag$} が文字列になるか。PostgreSQL だけ
 *								（関数の本体や {@code DO} ブロックがこの形で書かれる）
 * @param nestedBlockComment	{@code /*} … <!-- -->{@code *}{@code /} が入れ子にできるか。PostgreSQL だけ
 */
public record SqlSyntax(
	boolean backslashEscape
	, boolean hashComment
	, boolean dashCommentNeedsSpace
	, boolean backtickQuote
	, boolean executableComment
	, boolean dollarQuote
	, boolean nestedBlockComment
) {

	/** MySQL / MariaDB の決まり */
	public static final SqlSyntax MYSQL =
		new SqlSyntax(true, true, true, true, true, false, false);

	/** PostgreSQL の決まり */
	public static final SqlSyntax POSTGRESQL =
		new SqlSyntax(false, false, false, false, false, true, true);

}
