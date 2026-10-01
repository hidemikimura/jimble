package io.jimble.db.sql;

import io.jimble.util.data.Data;
import io.jimble.util.data.definition.IColumn;

import java.util.HashSet;
import java.util.Set;

/**
 * リクエストを渡してよい列（D-224）
 *
 * <p>
 * {@code apply(Data)} / {@code setRow(Data)} / {@code valueRow(Data)} は、<b>どの列名でも受け付ける</b>
 * （識別子はクォートするので SQL インジェクションにはならない）。リクエストをそのまま渡すと、
 * </p>
 * <ul>
 *   <li>{@code ?where[users][password_hash|starts_with]=$2a$10$a} のように、<b>画面に出していない列を1文字ずつ当てられる</b></li>
 *   <li>{@code setRow} に {@code role} や {@code is_admin} を足されて、<b>書き換えてはいけない列を書き換えられる</b></li>
 * </ul>
 * <p>
 * 許す列を渡す形（{@code apply(data, User.name, User.created_at)} など）を使うと、それ以外の列が来たら断る。
 * </p>
 */
final class AllowedColumns {

	private AllowedColumns () {}

	/**
	 * 許す列の名前（{@code テーブル.列}）
	 */
	private static Set<String> names (IColumn... allowed) {

		Set<String> names = new HashSet<>();

		if (allowed != null) {
			for (IColumn column : allowed) {
				names.add(column.table().name() + "." + column.name());
			}
		}

		return names;

	}

	/**
	 * {@code where} と {@code order} の列が、許したものだけか
	 *
	 * @param data		apply に渡すもの
	 * @param allowed	許す列
	 * @throws SqlBuildException	許していない列があるとき
	 */
	static void checkQuery (Data data, IColumn... allowed) {

		if (data == null) {
			return;
		}

		Set<String> names = names(allowed);

		for (String section : new String[] {"where", "order"}) {

			if (!data.containsKey(section)) {
				continue;
			}

			Data tables = data.getDataOptional(section);

			for (String table : tables.keySet()) {
				for (String key : tables.getDataOptional(table).keySet()) {
					String column = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
					if (!names.contains(table + "." + column)) {
						throw new SqlBuildException("%s に許していない列があります: %s.%s".formatted(section, table, column));
					}
				}
			}

		}

	}

	/**
	 * 行の列が、許したものだけか
	 *
	 * @param table		入れる先
	 * @param row		行
	 * @param allowed	許す列
	 * @throws SqlBuildException	許していない列があるとき
	 */
	static void checkRow (String table, Data row, IColumn... allowed) {

		if (row == null) {
			return;
		}

		Set<String> names = names(allowed);

		for (String column : row.keySet()) {
			if (!names.contains(table + "." + column)) {
				throw new SqlBuildException("許していない列があります: %s.%s".formatted(table, column));
			}
		}

	}

}
