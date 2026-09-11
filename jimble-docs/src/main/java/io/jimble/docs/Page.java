package io.jimble.docs;

import java.util.List;

/**
 * ページ1枚
 *
 * @param slug		URL に出る名前（{@code getting-started}）
 * @param title		見出し
 * @param summary	一覧に出す1行
 * @param section	どのまとまりに入るか
 * @param order		まとまりの中での並び
 * @param html		本文
 * @param headings	見出しの一覧（そのページの目次）
 * @param text		検索用の素の文字
 * @param markdown	AI に読ませる Markdown（要件 NF-D-09）
 */
public record Page(
	String slug
	, String title
	, String summary
	, String section
	, int order
	, String html
	, List<Heading> headings
	, String text
	, String markdown
) {

	/**
	 * 見出し1つ
	 *
	 * @param id	リンク先
	 * @param text	中身
	 * @param level	深さ（2 か 3）
	 */
	public record Heading(String id, String text, int level) {}

}
