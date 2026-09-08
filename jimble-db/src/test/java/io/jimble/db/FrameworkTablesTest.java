package io.jimble.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jimble 自身が作るテーブルの名前（D-68）
 *
 * <p>
 * <b>定数を足して {@code ALL} に足し忘れる</b>のが、この仕組みの唯一の抜け道である。
 * 抜けると、そのテーブルがアプリのテーブル定義クラスとして生成される。
 * ここで塞ぐ。
 * </p>
 */
class FrameworkTablesTest {

	/**
	 * 定数は全部 ALL に入っている
	 */
	@Test
	@DisplayName("定数を足して ALL に足し忘れていない")
	void allContainsEveryConstant () throws Exception {

		List<String> missing = new ArrayList<>();
		int count = 0;

		for (Field field : FrameworkTables.class.getDeclaredFields()) {

			if (!Modifier.isPublic(field.getModifiers())
				|| !Modifier.isStatic(field.getModifiers())
				|| field.getType() != String.class) {
				continue;
			}

			count++;

			String name = (String) field.get(null);

			if (!FrameworkTables.ALL.contains(name)) {
				missing.add(field.getName() + " (" + name + ")");
			}

		}

		assertTrue(count > 0, "定数が1つも見つからない（テストの前提が崩れている）");
		assertTrue(missing.isEmpty(), "FrameworkTables.ALL に入っていません: " + missing);
		assertEquals(count, FrameworkTables.ALL.size(), "ALL に定数以外の名前が混ざっています");

	}

}
