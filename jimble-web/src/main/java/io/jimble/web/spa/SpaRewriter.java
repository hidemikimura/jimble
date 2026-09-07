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
 *     indexHtml.replace("&lt;title&gt;&lt;/title&gt;", "&lt;title&gt;" + title(context) + "&lt;/title&gt;"));
 * </pre>
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

}
