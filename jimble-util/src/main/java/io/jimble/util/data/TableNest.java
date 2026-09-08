package io.jimble.util.data;

/**
 * JSON にするときのテーブルネストの扱い（要件 F-A-11）
 *
 * <p>
 * SELECT の結果は<b>テーブル名でネストしている</b>（要件 F-D-02）。
 * </p>
 *
 * <pre>
 * {"post": {"id": 1, "title": "こんにちは"}}
 * </pre>
 *
 * <p>
 * これを {@code flattenTable} で平らにするか、{@code extractTableData} でそのまま持つかは
 * これまで {@code setData} の書き方で決まっていた。
 * <b>つまり、1つの {@code AsyncData} は1つの形しか出せなかった。</b>
 * </p>
 *
 * <p>
 * 管理画面 API はネストで、ショップ API はフラットで返したい、という場合、
 * <b>同じクラスを2つ書くことになる。</b>それを避けるために、
 * <b>形は出力するときに選ぶ</b>ことにした。
 * </p>
 *
 * <pre>
 * data.getJsonString(TableNest.ON);    // {"post": {"id": 1}, "comments": [...]}
 * data.getJsonString(TableNest.OFF);   // {"id": 1, "comments": [...]}
 * </pre>
 */
public enum TableNest {

	/**
	 * そのまま出す（既定）
	 *
	 * <p>
	 * {@code setData} が作った形をそのまま出す。<b>これまでと1バイトも変わらない。</b>
	 * </p>
	 */
	AS_IS,

	/**
	 * テーブル名でネストして出す
	 */
	ON,

	/**
	 * テーブル名を外して平らに出す
	 */
	OFF,

}
