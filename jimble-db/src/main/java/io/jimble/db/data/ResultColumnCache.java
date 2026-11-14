package io.jimble.db.data;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 結果の列の読み方を、データソースと SQL の字面ごとに覚える（3.0.0）
 *
 * <p>
 * 2.x までは結果ごとに {@code ResultSetMetaData} から列の名前と型を聞いていて、主キー 1 行の SELECT を投げる CPU の
 * 1 割ほどがそこだった（列名を読むたびにドライバが文字列を作る）。同じ SQL の結果は同じ列が並ぶので、1 回読んだものを使い回す。
 * </p>
 *
 * <ul>
 *   <li>使う前に、<b>列の数・ビルダーが書いた位置ごとのキー・列ごとの JDBC の型</b>が覚えたときと同じか確かめ、違えば読み直す
 *       （JDBC の型を聞くのは、ドライバの中で文字列を作らない軽い呼び出し。重いのは列名と型名を聞くほう）</li>
 *   <li><b>SELECT 句が値（?）を含む結果は覚えない</b>（{@code SELECT ? AS v} は値で列の型が変わる）</li>
 *   <li>JDBC の型が同じまま型名だけが変わる DDL（MySQL の TEXT と JSON など）は、{@code DBUtil.load} で読み直すまで古い読み方のまま</li>
 *   <li>データソースごとに {@value #MAX_PER_SOURCE} 件まで。超えたら、そのデータソースの分を全部忘れて覚え直す（値はためない）</li>
 * </ul>
 */
final class ResultColumnCache {

	/* データソースごとに覚える数の上限 */
	static final int MAX_PER_SOURCE = 4096;

	/* データソース → SQL → 列の読み方 */
	private static final Map<String, Map<String, ResultColumns>> CACHE = new ConcurrentHashMap<>();

	private ResultColumnCache () {}

	/**
	 * 覚えた読み方（列の数・位置ごとのキーが違えば null）
	 *
	 * @param source		データソース
	 * @param sql			SQL
	 * @param labels		ビルダーが書いた位置ごとのキー（無ければ null）
	 * @param columnCount	今の結果の列の数
	 * @return	読み方
	 */
	static ResultColumns get (String source, String sql, List<String> labels, int columnCount) {

		Map<String, ResultColumns> bySql = CACHE.get(source);
		if (bySql == null) {
			return null;
		}

		ResultColumns columns = bySql.get(sql);
		if (columns == null || columns.keys().size() != columnCount || !Objects.equals(columns.labels(), labels)) {
			return null;
		}

		return columns;

	}

	/**
	 * 覚える
	 *
	 * @param source	データソース
	 * @param sql		SQL
	 * @param columns	読み方
	 */
	static void put (String source, String sql, ResultColumns columns) {

		Map<String, ResultColumns> bySql = CACHE.computeIfAbsent(source, s -> new ConcurrentHashMap<>());

		if (bySql.size() >= MAX_PER_SOURCE) {
			bySql.clear();
		}

		bySql.put(sql, columns);

	}

	/**
	 * 全部忘れる
	 */
	static void clear () {

		CACHE.clear();

	}

}
