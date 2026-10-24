package io.jimble.util.convertor;

import io.jimble.util.convertor.ConvertorFixtures.Child;
import io.jimble.util.convertor.ConvertorFixtures.Node;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.jimble.util.convertor.ConvertorFixtures.conv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convertor の今の挙動の記録（作り直しで変える予定のもの）
 *
 * <p>
 * <b>ここに書いた挙動は守る約束ではない。</b>jimble-converter への作り直しで変える予定で、
 * 変えたときにこのテストが落ちることで「変わった」と分かるようにしてある。
 * 変えたら、テストを新しい挙動に書き換えて {@link ConvertorContractTest} に移すか、消す。
 * 守る約束は {@link ConvertorContractTest} にある。
 * </p>
 */
class ConvertorCurrentQuirksTest {

	@Nested
	@DisplayName("真偽値・文字・enum")
	class FlagsAndEnums {

		@Test
		@DisplayName("現状: \"yes\" → Boolean は true")
		void 現状_yesがtrue () {

			// 作り直しで例外にする予定（Data#getBoolean は "yes" を例外にする）
			Boolean res = conv("yes", Boolean.class);
			assertTrue(res);

		}

	}

	@Nested
	@DisplayName("Data・byte[]")
	class DataAndBytes {

		@Test
		@DisplayName("現状: Bean → Data で、入れ子の Bean は Data にならずそのまま入る")
		void 現状_入れ子のBeanはそのまま () {

			// 作り直しで入れ子も Data にするかを決める予定
			Node child = new Node();
			child.name = "b";
			Node parent = new Node();
			parent.name = "a";
			parent.next = child;
			Data res = conv(parent, Data.class);
			assertSame(child, res.get("next"));

		}

		@Test
		@DisplayName("現状: 1つの値 → List は1要素の一覧に包む")
		void 現状_1つの値を一覧に包む () {

			// Data#getDataList は例外にしている。作り直しで揃えるかを決める予定
			List<Object> res = conv(5, List.class);
			assertEquals(List.of(5), res);

		}

	}

	@Nested
	@DisplayName("PropertyUtil")
	class PropertyUtilQuirks {

		@Test
		@DisplayName("現状: toString(一覧 / Map / 配列) は先頭の要素だけ")
		void 現状_toStringは先頭だけ () {

			// 作り直しで、窓口として残すかを決める予定
			assertEquals("a", PropertyUtil.toString(List.of("a", "b")));
			assertEquals("v", PropertyUtil.toString(Map.of("k", "v")));
			assertEquals("x", PropertyUtil.toString(new String[] {"x", "y"}));
			assertEquals("1", PropertyUtil.toString(new int[] {1, 2}));
			assertNull(PropertyUtil.toString(List.of()));

		}

		@Test
		@DisplayName("現状: toString(byte[]) は文字にならず先頭の byte の数（\"104\"）")
		void 現状_toStringのbyte配列 () {

			// 配列の先頭を取る処理が byte[] の処理より先にあるので、byte[] の分岐に届かない。作り直しで直す予定
			assertEquals("104", PropertyUtil.toString("hi".getBytes()));

		}

		@Test
		@DisplayName("現状: toString(空の配列) は配列の toString（型名@ハッシュ）")
		void 現状_toStringの空の配列 () {

			// 作り直しで null にする予定（空の一覧と揃える）
			assertTrue(PropertyUtil.toString(new String[] {}).startsWith("[Ljava.lang.String;@"));

		}

		@Test
		@DisplayName("現状: newInstance(long / double) は Integer の 0")
		void 現状_newInstanceのlongがInteger () {

			// 作り直しで、型に合った 0L / 0.0 にする予定
			assertEquals(Integer.valueOf(0), PropertyUtil.newInstance(long.class));
			assertEquals(Integer.valueOf(0), PropertyUtil.newInstance(double.class));

		}

		@Test
		@DisplayName("現状: newInstance(String) は空文字")
		void 現状_newInstanceのStringが空文字 () {

			// record の無い部品が空文字になる元。作り直しで null にする予定
			assertEquals("", PropertyUtil.newInstance(String.class));

		}

		@Test
		@DisplayName("現状: getFieldNames は親クラスの private の項目も返す（書けないのに読める）")
		void 現状_getFieldNamesは親のprivateも返す () {

			// 親の private の項目も返す（書けないのに読める）。作り直しで読み書きを揃える予定
			List<String> names = PropertyUtil.getFieldNames(Child.class).stream()
				.map(PropertyUtil.MethodFieldInfo::getFieldName).toList();
			assertEquals(List.of("parentPublic", "childName", "parentName"), names);

		}

	}

}
