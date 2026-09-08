package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import io.jimble.util.data.TableNest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * テーブルネストの組み直し（要件 F-A-11）
 *
 * <p>
 * <b>どのキーがどのテーブルの列なのかは、生の読み込みデータが知っている。</b>
 * {@code load()} が返す {@link Data} は SELECT の結果そのもので、
 * テーブル名でネストしている（要件 F-D-02）。
 * </p>
 *
 * <pre>
 * {"post": {"id": 1, "title": "こんにちは"}}
 *   ↑ テーブル名          ↑ 列名
 * </pre>
 *
 * <p>
 * ここから「テーブル名 → 列名」の対応を作り、ノードが持っている値を並べ替える。
 * <b>実装側に新しく書かせるものは無い。</b>
 * {@code table()} のような宣言を足す案もあったが、
 * <b>書き忘れると黙ってネストしなくなる</b>ので採らなかった
 * （「書いたのに効かない」の逆で、「書き忘れたのに気づけない」）。
 * </p>
 *
 * <h2>列でないキーは動かさない</h2>
 * <p>
 * {@code setRelationData} が足した子（{@link Async}）や計算値は、
 * 生データの列に無いので<b>常に最上位に残る</b>。
 * </p>
 */
final class TableShape {

	/**
	 * コンストラクタ
	 */
	private TableShape () {

	}

	/**
	 * テーブル名 → 列名 を作る
	 *
	 * <p>
	 * <b>値が {@link Data} のトップレベルキーだけをテーブルとみなす。</b>
	 * 自由 SQL や集約でテーブルネストしていない結果（値がスカラー）は
	 * テーブルとして拾わないので、組み直しの対象にならない。
	 * </p>
	 *
	 * @param raw	生の読み込みデータ
	 * @return	テーブル名 → 列名（1つも無ければ空）
	 */
	static Map<String, Set<String>> tablesOf (Data raw) {

		Map<String, Set<String>> tables = new LinkedHashMap<>();

		if (raw == null) {
			return tables;
		}

		for (Map.Entry<String, Object> entry : raw.entrySet()) {

			/*
			 * Async は対象外。
			 * 生データに遅延読み込みのノードが入っていることは普通は無いが、
			 * 入っていたときに keySet() を呼ぶと読み込みが走る（要件 F-A-04）。
			 */
			if (!(entry.getValue() instanceof Data value) || entry.getValue() instanceof Async) {
				continue;
			}

			tables.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(value.keySet());

		}

		return tables;

	}

	/**
	 * テーブル名 → 列名 を作る（複数行ぶんをまとめる）
	 *
	 * @param rows	生の読み込みデータ
	 * @return	テーブル名 → 列名（1つも無ければ空）
	 */
	static Map<String, Set<String>> tablesOf (List<Data> rows) {

		Map<String, Set<String>> tables = new LinkedHashMap<>();

		if (rows == null) {
			return tables;
		}

		for (Data row : rows) {
			for (Map.Entry<String, Set<String>> entry : tablesOf(row).entrySet()) {
				tables.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
			}
		}

		return tables;

	}

	/**
	 * 組み直す
	 *
	 * @param node		組み直すもの（{@code AsyncData} 自身、または要素の {@link Data}）
	 * @param tables	テーブル名 → 列名
	 * @param nest		どちらの形にするか
	 * @return	組み直した {@link Data}。組み直せなければ {@code node} そのもの
	 */
	static Data reshape (Data node, Map<String, Set<String>> tables, TableNest nest) {

		if (node == null || tables.isEmpty() || nest == TableNest.AS_IS) {
			return node;
		}

		return nest == TableNest.ON ? nest(node, tables) : flat(node, tables);

	}

	/**
	 * テーブル名でネストする
	 *
	 * <p>
	 * <b>並び順は元のまま。</b>テーブルは、その最初の列が現れた位置に置く。
	 * </p>
	 *
	 * <p>
	 * <b>元の {@link Data} には一切書き込まない。</b>
	 * {@code extractTableData} は生データの入れ子を<b>同じインスタンスのまま</b>返すので、
	 * それを組み立て先にすると<b>生データを書き換えてしまう</b>。
	 * JOIN していて同じ列名が2つのテーブルにあると、
	 * <b>あとから出力した形が元のノードにも残る。</b>必ず写して積む。
	 * </p>
	 *
	 * @param node		組み直すもの
	 * @param tables	テーブル名 → 列名
	 * @return	組み直した {@link Data}
	 */
	private static Data nest (Data node, Map<String, Set<String>> tables) {

		Set<String> occupied = occupiedTables(node, tables);

		Data result = new Data();

		for (Map.Entry<String, Object> entry : entries(node)) {

			String key = entry.getKey();
			Object value = entry.getValue();

			// すでにテーブル名でネストして持っている
			if (isTableHolder(key, value, tables)) {

				Data copy = new Data();
				copy.putAll((Data) value);
				result.put(key, copy);

				continue;

			}

			String table = tableOf(key, tables);

			/*
			 * 列でない（子・計算値）か、テーブル名をほかのものが使っている。
			 * <b>どちらの場合も最上位に残す。</b>
			 * 潰すくらいなら、まとめないほうがよい。
			 */
			if (table == null || occupied.contains(table)) {
				result.put(key, value);
				continue;
			}

			result.getDataOptional(table).put(key, value);

		}

		return result;

	}

	/**
	 * テーブル名を外して平らにする
	 *
	 * <p>
	 * <b>列でないキー（子・計算値）を潰さない。</b>
	 * テーブルに同じ名前の列があっても、最上位にあるものが残る。
	 * </p>
	 *
	 * @param node		組み直すもの
	 * @param tables	テーブル名 → 列名
	 * @return	組み直した {@link Data}
	 */
	private static Data flat (Data node, Map<String, Set<String>> tables) {

		List<Map.Entry<String, Object>> entries = entries(node);

		// 最上位に残るもの（テーブルの中身で上書きしない）
		Set<String> reserved = new LinkedHashSet<>();

		for (Map.Entry<String, Object> entry : entries) {
			if (!isTableHolder(entry.getKey(), entry.getValue(), tables)) {
				reserved.add(entry.getKey());
			}
		}

		Data result = new Data();

		for (Map.Entry<String, Object> entry : entries) {

			String key = entry.getKey();
			Object value = entry.getValue();

			if (!isTableHolder(key, value, tables)) {
				result.put(key, value);
				continue;
			}

			for (Map.Entry<String, Object> column : ((Data) value).entrySet()) {
				if (!reserved.contains(column.getKey())) {
					result.put(column.getKey(), column.getValue());
				}
			}

		}

		return result;

	}

	/**
	 * テーブル名でネストして持っているキーか
	 *
	 * @param key		キー
	 * @param value		値
	 * @param tables	テーブル名 → 列名
	 * @return	そうなら true
	 */
	private static boolean isTableHolder (String key, Object value, Map<String, Set<String>> tables) {

		return tables.containsKey(key) && value instanceof Data && !(value instanceof Async);

	}

	/**
	 * ほかのものが使っているテーブル名
	 *
	 * <p>
	 * {@code setRelationData} が<b>テーブルと同じ名前で子を置いている</b>ことがある。
	 * そこへ列をまとめると<b>子が消える</b>ので、そのテーブルはまとめない。
	 * </p>
	 *
	 * @param node		組み直すもの
	 * @param tables	テーブル名 → 列名
	 * @return	使われているテーブル名
	 */
	private static Set<String> occupiedTables (Data node, Map<String, Set<String>> tables) {

		Set<String> occupied = new LinkedHashSet<>();

		for (String table : tables.keySet()) {

			if (!node.containsKey(table)) {
				continue;
			}

			if (!isTableHolder(table, node.get(table), tables)) {
				occupied.add(table);
			}

		}

		return occupied;

	}

	/**
	 * どのテーブルの列か
	 *
	 * <p>
	 * <b>JOIN していて同じ列名が2つのテーブルにあるときは、先に出てきたほうにする。</b>
	 * その場合、平らに持っている時点ですでに片方で上書きされていて、
	 * どちらの値かは分からなくなっている。
	 * </p>
	 *
	 * @param key		キー
	 * @param tables	テーブル名 → 列名
	 * @return	テーブル名。列でなければ null
	 */
	private static String tableOf (String key, Map<String, Set<String>> tables) {

		for (Map.Entry<String, Set<String>> entry : tables.entrySet()) {
			if (entry.getValue().contains(key)) {
				return entry.getKey();
			}
		}

		return null;

	}

	/**
	 * 走査用にエントリを写し取る
	 *
	 * <p>
	 * 組み直しの途中で元を触らないよう、先に取り出しておく。
	 * </p>
	 *
	 * @param node	組み直すもの
	 * @return	エントリ
	 */
	private static List<Map.Entry<String, Object>> entries (Data node) {

		return List.copyOf(node.entrySet());

	}

}
