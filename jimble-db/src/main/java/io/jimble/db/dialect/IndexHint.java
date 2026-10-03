package io.jimble.db.dialect;

/**
 * インデックスのヒントの種類（MySQL。D-265）
 *
 * <p>PostgreSQL にはインデックスのヒントが無い（組み立てたところで {@link DialectException}）。</p>
 */
public enum IndexHint {

	/** このインデックスを使わせる（テーブル全体を読むより必ずこちら。{@code FORCE INDEX}） */
	FORCE("FORCE INDEX"),

	/** このインデックスの中から選ばせる（{@code USE INDEX}） */
	USE("USE INDEX"),

	/** このインデックスを使わせない（{@code IGNORE INDEX}） */
	IGNORE("IGNORE INDEX");

	private final String keyword;

	IndexHint (String keyword) {

		this.keyword = keyword;

	}

	/**
	 * SQL に書く語
	 *
	 * @return	{@code FORCE INDEX} など
	 */
	public String keyword () {

		return keyword;

	}

}
