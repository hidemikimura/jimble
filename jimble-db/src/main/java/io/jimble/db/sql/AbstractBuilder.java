package io.jimble.db.sql;

import io.jimble.util.internal.array.ArrayUtil;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.parse.Parse;
import io.jimble.util.data.Data;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.db.sql.definition.table.TemporaryTable;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.internal.sql.query.dsl.where.Match;
import io.jimble.db.sql.query.where.IWhere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builder
 */
public abstract class AbstractBuilder<E extends AbstractBuilder> implements IBuilder {

	/**
	 * Dataからクエリに適用する
	 *
	 * @param data  Data
	 * @return  E
	 */
	public abstract E apply (Data data);

	/**
	 * クエリからWhereListを作成する
	 *
	 * @param query クエリ
	 * @return  WhereList
	 */
	protected List<IWhere> whereList (Data query) {

		List<IWhere> res = new ArrayList<>();

		if (query == null || !query.containsKey("where")) {
			return res;
		}
		Data qData = query.getData("where");

		for (String tableName : qData.keySet()) {

			TemporaryTable table = new TemporaryTable(tableName);

			Data tableData = qData.getDataOptional(tableName);
			for (String columnQuery : tableData.keySet()) {

				Object value = tableData.getObject(columnQuery);

				String[] querys = columnQuery.split(Pattern.quote("|"));

				/*
				 * 空の値は例外（要件 D-194）。1.x は {@code = NULL} になって黙って0件だった——
				 * 画面の検索欄を空で送ると「該当なし」になる。空なら条件ごと入れないこと。
				 */
				String op = querys.length == 1 ? "eq" : querys[1];
				if (!"is_null".equals(op) && !"is_not_null".equals(op) && !"between".equals(op)
					&& !"in".equals(op) && !"not_in".equals(op) && singleValue(value) == null) {
					throw new SqlBuildException(
						"where の %s.%s が空です（空の値との比較はどの行にも当たりません）。"
							.formatted(tableName, columnQuery)
							+ "条件にしないならキーごと入れない、NULL を探すなら \"%s|is_null\": true と書いてください"
							.formatted(querys[0]));
				}
				if (querys.length == 1) {
					res.add(new TemporaryColumn(
							table
							, querys[0]
						).eq(singleValue(value))
					);
				} else {
					switch (querys[1]) {
						case "between":
							List<Object> arrayValue = arrayValue(value);
							/*
							 * 2つ揃わなければ例外（要件 D-190）。1.4 までは条件ごと黙って捨てていたので、
							 * SELECT は<b>全件</b>を返していた。
							 */
							if (arrayValue == null || arrayValue.size() != 2) {
								throw new SqlBuildException(
									columnQuery + " には [開始, 終了] の2つを渡してください: " + value);
							}
							{
								res.add(new TemporaryColumn(
										table
										, querys[0]
									).between(
										Parse.parseDate(arrayValue.get(0))
										, Parse.parseDate(arrayValue.get(1))
									)
								);
							}
							break;
						case "contains":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).contains(singleValue(value))
							);
							break;
						case "ends_with":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).ends_with(singleValue(value))
							);
							break;
						case "eq":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).eq(singleValue(value))
							);
							break;
						case "ge":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).ge(singleValue(value))
							);
							break;
						case "gt":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).gt(singleValue(value))
							);
							break;
						case "in":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).in(arrayValue(value))
							);
							break;
						case "is_not_null":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).is_not_null()
							);
							break;
						case "is_null":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).is_null()
							);
							break;
						case "le":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).le(singleValue(value))
							);
							break;
						case "like":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).like(singleValue(value))
							);
							break;
						case "lt":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).lt(singleValue(value))
							);
							break;
						case "not":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).not(singleValue(value))
							);
							break;
						case "not_in":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).not_in(arrayValue(value))
							);
							break;
						case "not_like":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).not_like(singleValue(value))
							);
							break;
						case "starts_with":
							res.add(new TemporaryColumn(
									table
									, querys[0]
								).starts_with(singleValue(value))
							);
							break;
						case "match":
							{
								Match match = Dsl.match(new TemporaryColumn(
									table
									, querys[0]
								));
								match.against(String.valueOf(singleValue(value)));
								if (querys.length > 2) {
									if (querys[2].equalsIgnoreCase(Match.SearchModifier.IN_NATURAL_LANGUAGE_MODE.searchModifier())) {
										res.add(match.inNaturalLanguageMode());
									} else if (querys[2].equalsIgnoreCase(Match.SearchModifier.IN_NATURAL_LANGUAGE_MODE_WITH_QUERY_EXPANSION.searchModifier())) {
										res.add(match.inNaturalLanguageModeWithQueryExpansion());
									} else if (querys[2].equalsIgnoreCase(Match.SearchModifier.IN_BOOLEAN_MODE.searchModifier())) {
										res.add(match.inBooleanMode());
									} else if (querys[2].equalsIgnoreCase(Match.SearchModifier.WITH_QUERY_EXPANSION.searchModifier())) {
										res.add(match.withQueryExpansion());
									}
								} else {
									res.add(match.defaultMode());
								}
							}
							break;
						default:
							/*
							 * <b>知らない語は落とす（D-173）。</b>
							 *
							 * かつては {@code break;} だけで、<b>その条件が黙って消えていた</b>。
							 * 消えた先が {@code DELETE} や {@code UPDATE} だと、
							 * <b>条件が1つも残らず WHERE 無しの文になる</b>——
							 * {@code {"id|gte": 3}} と書いた（正しくは {@code ge}）だけで、
							 * 表が丸ごと消える。SQL は通るし、例外も出ない。
							 *
							 * 同じメソッドの {@code {"id|in": []}} には
							 * 「黙って0件になるのを避ける」と理由が書いてあるのに、
							 * こちらには何も書かれていなかった。
							 */
							throw new SqlBuildException(
								"where に知らない演算子があります: \"%s\"（%s.%s）"
									.formatted(querys[1], tableName, querys[0]));
					}
				}

			}

		}

		return res;

	}

	/**
	 * 包む形か確かめる（要件 D-194）
	 *
	 * <p>
	 * {@code where(Data)} / {@code set(Data)} / {@code value(Data)} は
	 * {@code {"where": ...}} / {@code {"set": ...}} / {@code {"value": ...}} の形を読む。
	 * 1.x は包むキーが無いと<b>黙って何もしなかった</b>——{@code where} なら条件が付かずに全件、
	 * {@code set} なら何も更新されない。空でない Data で包むキーが無ければ例外にする。
	 * {@code apply(Data)} はいくつかの句をまとめて読むので、ここを通らない。
	 * </p>
	 *
	 * @param data		渡された Data
	 * @param key		包むキー
	 * @param method	呼ばれたメソッド（エラーに出す）
	 * @param flat		平らな形で渡したいときの書き方（無ければ null）
	 */
	protected static void requireWrapped (Data data, String key, String method, String flat) {

		if (data == null || data.isEmpty() || data.containsKey(key)) {
			return;
		}

		throw new SqlBuildException(
			"%s は {\"%s\": {\"テーブル名\": {...}}} の形を読みます。\"%s\" キーがありません（キー: %s）%s"
				.formatted(method, key, key, data.keySet(), flat == null ? "" : "。平らな行なら " + flat + " を使ってください")
				+ io.jimble.util.internal.Docs.see("sql"));

	}

	/**
	 * オブジェクトの複数オブジェクトを取得する
	 *
	 * @param value オブジェクト
	 * @return  複数オブジェクト
	 */
	protected List<Object> arrayValue (Object value) {

		if (value == null) {
			return null;
		}

		List<Object> res = new ArrayList<>();

		/*
		 * <b>空は空のまま返す。</b>null に潰してはいけない（要件 F-D-07）。
		 *
		 * 潰すと in(null) になり、SQL は IN (?) ＋ NULL のバインドになる。
		 * IN (NULL) はどの行にも当たらないので、
		 * <b>{"id|in": []} が黙って 0 件になる</b>。例外も DB のエラーも出ない。
		 * 空のまま渡せば In が組み立て時に落とす。
		 * （between は size() >= 2 で見ているので、空でも影響しない）
		 */
		if (value.getClass().isArray()) {
			/*
			 * <b>{@code (Object[])} にキャストしない（要件 D-162）。</b>
			 * {@code long[]} は {@code Object[]} ではないので、
			 * キャストすると<b>素の配列だけ必ず落ちる</b>。
			 */
			res.addAll(ArrayUtil.toList(value));
		} else if (value instanceof List<?> list) {
			res.addAll(list);
		} else if (value instanceof Data data) {
			res.add(data);
		} else if (value instanceof Map<?,?> map) {
			try {
				res.add(Convertor.convert(null, value, Data.class));
			} catch (Exception ex) {
				return null;
			}
		} else if (value instanceof String str) {
			String[] values = str.replaceAll("\\R", "|").split(Pattern.quote("|"));
			Collections.addAll(res, values);
		}

		return res;

	}

	/**
	 * オブジェクトの単一オブジェクトを取得する
	 *
	 * @param value オブジェクト
	 * @return  単一オブジェクト
	 */
	protected Object singleValue (Object value) {

		if (value == null) {
			return null;
		}

		if (value.getClass().isArray()) {
			if (ArrayUtil.length(value) == 0) {
				return null;
			} else {
				return ArrayUtil.get(value, 0);
			}
		} else if (value instanceof List<?> list) {
			if (list.isEmpty()) {
				return null;
			} else {
				return list.getFirst();
			}
		} else if (value instanceof Data) {
			return value;
		} else if (value instanceof Map<?,?> map) {
			try {
				return Convertor.convert(null, value, Data.class);
			} catch (Exception ex) {
				return null;
			}
		}

		return value;

	}

}
