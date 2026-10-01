package io.jimble.web.spa;

import io.jimble.web.context.WebContext;

/**
 * index.html を書き換える
 *
 * <p>
 * SPA は同じ index.html を返すので、<b>クローラや OGP のために
 * パスごとに title や meta を差し替えたい</b>ことがある。その口。
 * </p>
 *
 * <pre>
 * spa.route("/items/{id}", (context, indexHtml) -&gt;
 *     indexHtml.replace("&lt;title&gt;&lt;/title&gt;", "&lt;title&gt;" + SpaRewriter.escapeHtml(title(context)) + "&lt;/title&gt;"));
 * </pre>
 *
 * <p>
 * <b>差し込む値は {@link #escapeHtml(String)} を通す</b>（D-212）。パスの値はデコード済みなので、
 * {@code /items/%3Cscript%3E...} がそのまま HTML になる。かつてのドキュメントの例は、エスケープせずに差し込んでいた。
 * </p>
 *
 * <p>
 * 書き換えたページは {@code Cache-Control: private,no-cache} で返す（共有キャッシュに置かせない。
 * 書き換えにリクエストごとの値が入りうるため）。
 * </p>
 */
@FunctionalInterface
public interface SpaRewriter {

	/**
	 * 書き換える
	 *
	 * @param context	コンテキスト
	 * @param indexHtml	もとの index.html
	 * @return	返す HTML
	 */
	String rewrite (WebContext context, String indexHtml);

	/**
	 * HTML の本文と属性の値に差し込めるようにエスケープする（D-212）
	 *
	 * @param value	値（null は空文字）
	 * @return	{@code & < > " '} を文字参照にしたもの
	 */
	static String escapeHtml (String value) {

		if (value == null) {
			return "";
		}

		StringBuilder sb = new StringBuilder(value.length() + 16);

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '&' -> sb.append("&amp;");
				case '<' -> sb.append("&lt;");
				case '>' -> sb.append("&gt;");
				case '"' -> sb.append("&quot;");
				case '\'' -> sb.append("&#39;");
				default -> sb.append(c);
			}
		}

		return sb.toString();

	}

}
