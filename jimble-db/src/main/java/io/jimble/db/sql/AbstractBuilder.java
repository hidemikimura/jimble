package io.jimble.db.sql;

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
							if (arrayValue != null && arrayValue.size() >= 2) {
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
							break;
					}
				}

			}

		}

		return res;

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
			Collections.addAll(res, (Object[]) value);
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
			Object[] values = (Object[]) value;
			if (values.length == 0) {
				return null;
			} else {
				return values[0];
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
