package io.jimble.util.internal;

/**
 * 例外と警告に付ける引き先（要件 D-189）
 *
 * <p>
 * <b>AI はログだけを見て直しに行く。</b>「何が起きたか」と「直し方」に加えて、
 * <b>そのまま取れる説明の場所</b>があれば、推測で直さずに済む。
 * jimble.io の各ページは Markdown のまま取れる（{@code https://jimble.io/ja/<名前>.md}）ので、それを付ける。
 * </p>
 *
 * <p>
 * 付ける先のページが実在することは {@code DocsLinkTest}（jimble-docs）が確かめる。
 * </p>
 */
public final class Docs {

	/** 引き先の頭 */
	public static final String BASE = "https://jimble.io/ja/";

	private Docs () {
	}

	/**
	 * ページの URL
	 *
	 * @param page	ページの名前（{@code config} の形。拡張子なし）
	 * @return	URL（{@code https://jimble.io/ja/config.md}）
	 */
	public static String url (String page) {

		return BASE + page + ".md";

	}

	/**
	 * 文の最後に付ける形
	 *
	 * @param page	ページの名前
	 * @return	{@code （詳しく: https://jimble.io/ja/config.md）}
	 */
	public static String see (String page) {

		return "（詳しく: " + url(page) + "）";

	}

}
