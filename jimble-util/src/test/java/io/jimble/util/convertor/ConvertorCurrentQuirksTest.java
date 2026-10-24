package io.jimble.util.convertor;

import io.jimble.util.convertor.ConvertorFixtures.Child;
import io.jimble.util.convertor.ConvertorFixtures.Item;
import io.jimble.util.convertor.ConvertorFixtures.LineItem;
import io.jimble.util.convertor.ConvertorFixtures.Node;
import io.jimble.util.convertor.ConvertorFixtures.Order;
import io.jimble.util.convertor.ConvertorFixtures.SetterOnly;
import io.jimble.util.convertor.ConvertorFixtures.Status;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.jimble.util.convertor.ConvertorFixtures.conv;
import static io.jimble.util.convertor.ConvertorFixtures.data;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	@DisplayName("Bean への書き込み")
	class BeanWrites {

		@Test
		@DisplayName("現状: setter しか無い項目には値が入らない")
		void 現状_setterしか無い項目に値が入らない () {

			// PropertyUtil.java:441 で、変換した値を Method#invoke の可変長引数（Object[]）として渡しているので
			// ClassCastException になり、それを黙って捨てている。作り直しで setter から書けるようにする予定
			SetterOnly res = conv(Map.of("label", "L"), SetterOnly.class);
			assertNull(res.hidden());

		}

		@Test
		@DisplayName("現状: 親クラスの private の項目には書けない（読むことはできる）")
		void 現状_親クラスのprivate項目に書けない () {

			// 書くほうは自分のクラスの項目しか探さない（getField / getDeclaredField）が、
			// 読むほう（getFieldNames）は親までたどる。作り直しで読み書きを揃える予定
			Child res = conv(Map.of("parentName", "P", "childName", "C"), Child.class);
			assertNull(res.parentName());
			assertEquals("C", res.childName());
			List<String> names = PropertyUtil.getFieldNames(Child.class).stream()
				.map(PropertyUtil.MethodFieldInfo::getFieldName).toList();
			assertTrue(names.contains("parentName"), names.toString());

		}

		@Test
		@DisplayName("現状: int の項目に \"abc\" を入れると例外にならず 0")
		void 現状_int項目にabcで0 () {

			// 作り直しで、読めない値は例外にする予定（Data の getInt と同じ規則。D-195）
			Item res = conv(Map.of("qty", "abc"), Item.class);
			assertEquals(0, res.qty);

		}

		@Test
		@DisplayName("現状: int の項目に \"1.5\" を入れると例外にならず 0")
		void 現状_int項目に小数で0 () {

			// 作り直しで、小数を整数に読むのは例外にする予定（D-195 と揃える）
			Item res = conv(Map.of("qty", "1.5"), Item.class);
			assertEquals(0, res.qty);

		}

	}

	@Nested
	@DisplayName("整数")
	class Integers {

		@Test
		@DisplayName("現状: \"1.5\" → Integer は null、int は 0")
		void 現状_小数の文字は整数でnullか0 () {

			// 作り直しで例外にする予定（D-195 と揃える）
			Integer boxed = conv("1.5", Integer.class);
			int primitive = conv("1.5", int.class);
			assertNull(boxed);
			assertEquals(0, primitive);

		}

		@Test
		@DisplayName("現状: \"abc\" → Integer は null、int は 0")
		void 現状_abcは整数でnullか0 () {

			// 作り直しで例外にする予定
			Integer boxed = conv("abc", Integer.class);
			int primitive = conv("abc", int.class);
			assertNull(boxed);
			assertEquals(0, primitive);

		}

		@Test
		@DisplayName("現状: 3e9（Long）→ Integer は桁あふれする")
		void 現状_桁あふれ () {

			// 作り直しで例外にする予定
			Integer res = conv(3_000_000_000L, Integer.class);
			assertEquals(-1_294_967_296, res);

		}

		@Test
		@DisplayName("現状: 70000 → Short、300 → Byte も桁あふれする")
		void 現状_ShortとByteの桁あふれ () {

			// 作り直しで例外にする予定
			Short s = conv(70_000, Short.class);
			Byte b = conv(300, Byte.class);
			assertEquals((short) 4464, s);
			assertEquals((byte) 44, b);

		}

		@Test
		@DisplayName("現状: 1.9（Double）→ Integer は切り捨てて 1")
		void 現状_小数の数は切り捨て () {

			// 作り直しで例外にする予定（黙って丸めない）
			Integer res = conv(1.9d, Integer.class);
			assertEquals(1, res);

		}

		@Test
		@DisplayName("現状: \"1:00\" → Integer は ':' を消して 100")
		void 現状_コロンを消す () {

			// IntegerConvertor が ':' を取り除いてから読んでいる（時刻の名残）。作り直しで例外にする予定
			Integer res = conv("1:00", Integer.class);
			assertEquals(100, res);

		}

		@Test
		@DisplayName("現状: \" 12 \"（前後に空白）→ Integer は null")
		void 現状_空白は落とさない () {

			// Data#getInt は前後の空白を落として 12 と読む。作り直しで Data と揃える予定
			Integer res = conv(" 12 ", Integer.class);
			assertNull(res);

		}

		@Test
		@DisplayName("現状: \"1.5\" → Long は null")
		void 現状_小数の文字はLongでnull () {

			// 作り直しで例外にする予定
			Long res = conv("1.5", Long.class);
			assertNull(res);

		}

	}

	@Nested
	@DisplayName("小数")
	class Decimals {

		@Test
		@DisplayName("現状: \"abc\" → BigDecimal は 0（null でも例外でもない）")
		void 現状_abcはBigDecimalで0 () {

			// 作り直しで例外にする予定
			BigDecimal res = conv("abc", BigDecimal.class);
			assertEquals(BigDecimal.ZERO, res);

		}

		@Test
		@DisplayName("現状: \"abc\" → Double は null、double は 0.0")
		void 現状_abcはDoubleでnullか0 () {

			// 作り直しで例外にする予定
			Double boxed = conv("abc", Double.class);
			double primitive = conv("abc", double.class);
			assertNull(boxed);
			assertEquals(0.0d, primitive);

		}

	}

	@Nested
	@DisplayName("真偽値・文字・enum")
	class FlagsAndEnums {

		@Test
		@DisplayName("現状: \"no\" → Boolean は true")
		void 現状_noがtrue () {

			// 空・"0"・"false" 以外の文字は全部 true。作り直しで true / false / 1 / 0 以外は例外にする予定（D-161 / D-195）
			Boolean boxed = conv("no", Boolean.class);
			boolean primitive = conv("no", boolean.class);
			assertTrue(boxed);
			assertTrue(primitive);

		}

		@Test
		@DisplayName("現状: \"yes\" → Boolean は true")
		void 現状_yesがtrue () {

			// 作り直しで例外にする予定（Data#getBoolean は "yes" を例外にする）
			Boolean res = conv("yes", Boolean.class);
			assertTrue(res);

		}

		@Test
		@DisplayName("現状: 2 → Boolean は false")
		void 現状_2がfalse () {

			// 数は 1 だけが true。作り直しで 1 / 0 以外は例外にする予定
			Boolean res = conv(2, Boolean.class);
			assertFalse(res);

		}

		@Test
		@DisplayName("現状: \"ab\" → Character は先頭の 'a'")
		void 現状_文字は先頭だけ () {

			// 作り直しで、1文字でないものは例外にする予定
			Character res = conv("ab", Character.class);
			assertEquals('a', res);

		}

		@Test
		@DisplayName("現状: 合わない名前の enum は null")
		void 現状_合わないenumはnull () {

			// 作り直しで例外にする予定
			Status res = conv("nope", Status.class);
			assertNull(res);

		}

		@Test
		@DisplayName("現状: 数（序数）からは enum にならず null")
		void 現状_数からenumはnull () {

			// 作り直しで、合わない名前と同じく例外にする予定
			Status res = conv(0, Status.class);
			assertNull(res);

		}

	}

	@Nested
	@DisplayName("日付（時間帯は " + ConvertorFixtures.ZONE + " に固定）")
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class Dates {

		/** 2024-01-15 00:00 JST */
		private static final long JAN_15 = 1_705_244_400_000L;

		/** 1時間 */
		private static final long HOUR = 3_600_000L;

		@BeforeAll
		void fixZone () {
			ConvertorFixtures.fixZone();
		}

		@AfterAll
		void restoreZone () {
			ConvertorFixtures.restoreZone();
		}

		@Test
		@DisplayName("現状: \"2024-13-45\" は繰り上がって 2025-02-14 として通る")
		void 現状_ありえない日付が繰り上がる () {

			// SimpleDateFormat が lenient のまま。作り直しで例外にする予定
			Date res = conv("2024-13-45", Date.class);
			assertEquals(1_739_458_800_000L, res.getTime());

		}

		@Test
		@DisplayName("現状: \"2024-02-30\" は 2024-03-01 として通る")
		void 現状_2月30日が通る () {

			// 作り直しで例外にする予定
			Date res = conv("2024-02-30", Date.class);
			assertEquals(1_709_218_800_000L, res.getTime());

		}

		@Test
		@DisplayName("現状: \"2024-01-15abc\" は後ろのゴミを無視して通る")
		void 現状_後ろのゴミを無視 () {

			// 作り直しで例外にする予定（全体一致にする）
			Date res = conv("2024-01-15abc", Date.class);
			assertEquals(JAN_15, res.getTime());

		}

		@Test
		@DisplayName("現状: \"...T10:00:00.000Z\" の Z（UTC）を無視して、いまの時間帯の 10 時にする")
		void 現状_Zの時差を無視 () {

			// 'Z' を文字として読み飛ばしている。正しくは UTC の 10 時（JST の 19 時）。作り直しで UTC として読む予定
			Date res = conv("2024-01-15T10:00:00.000Z", Date.class);
			assertEquals(JAN_15 + 10 * HOUR, res.getTime());

		}

		@Test
		@DisplayName("現状: Instant に \"...T10:00:00Z\" を渡しても Z を無視する")
		void 現状_InstantでもZを無視 () {

			// 作り直しで UTC として読む予定（Instant.parse と同じ結果にする）
			Instant res = conv("2024-01-15T10:00:00Z", Instant.class);
			assertEquals(Instant.parse("2024-01-15T01:00:00Z"), res);

		}

		@Test
		@DisplayName("現状: 日付として読めない文字は null")
		void 現状_読めない日付はnull () {

			// Data#getDate はこれを例外にしている。作り直しで例外にする予定
			Date res = conv("abc", Date.class);
			assertNull(res);

		}

		@Test
		@DisplayName("現状: LocalDate には変換できない（InaccessibleObjectException）")
		void 現状_LocalDateに変換できない () {

			// 専用の変換が無く、Bean として String の中身に触ろうとして落ちる。作り直しで java.time を読めるようにする予定
			assertThrows(RuntimeException.class, () -> conv("2024-01-15", LocalDate.class));

		}

		@Test
		@DisplayName("現状: LocalDateTime には変換できない")
		void 現状_LocalDateTimeに変換できない () {

			// 作り直しで読めるようにする予定
			assertThrows(RuntimeException.class, () -> conv("2024-01-15T10:00:00", LocalDateTime.class));

		}

	}

	@Nested
	@DisplayName("record")
	class Records {

		@Test
		@DisplayName("現状: record の List<LineItem> の中身は Map のまま（record にならない）")
		void 現状_recordの一覧の中身がMapのまま () {

			// record の部品の型引数を見ていない（Bean の項目なら見る）。作り直しで要素の型まで変換する予定
			Map<String, Object> line = new HashMap<>();
			line.put("code", "A");
			line.put("qty", 1);
			Map<String, Object> src = new HashMap<>();
			src.put("name", "n");
			src.put("lines", new ArrayList<>(List.of(line)));
			Order res = conv(src, Order.class);
			Object first = ((List<?>) res.lines()).get(0);
			assertInstanceOf(Map.class, first);
			assertFalse(first instanceof LineItem);

		}

		@Test
		@DisplayName("現状: record の List<LineItem> の要素が Map.of（変更できない Map）だと NullPointerException")
		void 現状_recordの一覧の要素がMapOfだとNPE () {

			// 要素を Object として写すときに Map.of の型を new しようとして null になり、put で落ちる。
			// 作り直しで要素を LineItem にする（落ちない）予定
			Map<String, Object> src = Map.of("lines", List.of(Map.of("code", "A")));
			assertThrows(NullPointerException.class, () -> conv(src, Order.class));

		}

		@Test
		@DisplayName("現状: record の無い String の部品は null でなく空文字")
		void 現状_recordの無いStringは空文字 () {

			// 無い部品を newInstance(String.class) で埋めている。作り直しで null にする予定
			Order res = conv(Map.of("count", 1), Order.class);
			assertEquals("", res.name());

		}

	}

	@Nested
	@DisplayName("Data・byte[]")
	class DataAndBytes {

		@Test
		@DisplayName("現状: byte[]（JSON）→ Data に 0..n のキー（各 byte）が混ざる")
		void 現状_byte配列からDataに番号のキーが混ざる () {

			// JSON として読んだあと、配列としても Map に写している。作り直しで JSON として読んだものだけにする予定
			byte[] json = "{\"a\":1}".getBytes();
			Data res = conv(json, Data.class);
			assertEquals(1, res.getInt("a"));
			assertEquals(json.length + 1, res.size());
			assertTrue(res.containsKey("0"));
			assertEquals((byte) '{', res.get("0"));

		}

		@Test
		@DisplayName("現状: JSON でない文字 → Data は例外にならず空の Data")
		void 現状_読めない文字は空のData () {

			// Data#getData は例外にしている。作り直しで例外にする予定
			Data res = conv("abc", Data.class);
			assertTrue(res.isEmpty());

		}

		@Test
		@DisplayName("現状: 文字 → byte[] は byte に読めない 1 要素（0）になる")
		void 現状_文字からbyte配列が0 () {

			// 文字を1要素の配列として扱い、"abc" を byte に読んで 0 にしている。作り直しで UTF-8 の byte 列にするか例外にする予定
			byte[] res = conv("abc", byte[].class);
			assertArrayEquals(new byte[] {0}, res);

		}

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
		@DisplayName("現状: 自分を含む Map を Data にすると StackOverflowError")
		void 現状_自分を含むMapでStackOverflow () {

			// Map の値を Object として写すところ（ObjectConvertor）が循環を見ていない。作り直しで循環を止める予定
			Map<String, Object> self = new HashMap<>();
			self.put("name", "a");
			self.put("self", self);
			assertThrows(StackOverflowError.class, () -> conv(self, Data.class));

		}

		@Test
		@DisplayName("現状: 型が合っていても List / Map / Data は写しを作る（同じものを返さない）")
		void 現状_一覧とMapは写しを作る () {

			// 型が合っている値はそのまま返すのが約束だが、List と Map だけ除いている。作り直しで揃えるかを決める予定
			List<String> list = new ArrayList<>(List.of("a"));
			Map<String, Object> map = new HashMap<>(Map.of("a", 1));
			Data dt = data("a", 1);
			List<String> listRes = conv(list, List.class);
			Map<String, Object> mapRes = conv(map, Map.class);
			Data dataRes = conv(dt, Data.class);
			assertNotSame(list, listRes);
			assertNotSame(map, mapRes);
			assertNotSame(dt, dataRes);
			assertEquals(list, listRes);

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
	@DisplayName("文字から作れてしまう型")
	class FromString {

		@Test
		@DisplayName("現状: 文字から Class を作れる（Class.forName）")
		void 現状_文字からClass () {

			// 外から来た名前でクラスを読み込めてしまう。作り直しで変換の対象から外す予定
			Class<?> res = conv("java.lang.String", Class.class);
			assertEquals(String.class, res);

		}

		@Test
		@DisplayName("現状: 文字から InetAddress を作れる（名前なら DNS を引く）")
		void 現状_文字からInetAddress () {

			// 名前解決が起きないよう IP の文字で確かめる。名前を渡すと DNS を引く。作り直しで対象から外す予定
			InetAddress res = conv("127.0.0.1", InetAddress.class);
			assertTrue(res.isLoopbackAddress());

		}

		@Test
		@DisplayName("現状: 文字から Pattern を作れる（正規表現をそのまま compile）")
		void 現状_文字からPattern () {

			// 外から来た正規表現をそのまま使えてしまう（ReDoS）。作り直しで対象から外す予定
			Pattern res = conv("a+", Pattern.class);
			assertEquals("a+", res.pattern());

		}

		@Test
		@DisplayName("現状: UUID には変換できない")
		void 現状_UUIDに変換できない () {

			// 専用の変換が無い。作り直しで読めるようにするかを決める予定
			assertThrows(RuntimeException.class, () -> conv("123e4567-e89b-12d3-a456-426614174000", UUID.class));

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
		@DisplayName("現状: 知らないキーの名前を、項目名の変換（FIELD_NAME）で上限なしに覚え続ける（D-226 の残り）")
		void 現状_知らないキーの名前を覚え続ける () throws Exception {

			// D-226 で NO_FIELD_CACHE には上限を置いたが、キー → 項目名の変換の覚えには上限が無い。
			// 作り直しで、覚える数に上限を置く（か覚えない）予定
			Field field = PropertyUtil.class.getDeclaredField("FIELD_NAME");
			field.setAccessible(true);
			Map<?, ?> fieldNames = (Map<?, ?>) field.get(null);
			int before = fieldNames.size();
			int count = PropertyUtil.MAX_NO_FIELD_CACHE * 2;
			String prefix = "unknown_" + UUID.randomUUID() + "_";
			Data request = new Data();
			for (int i = 0; i < count; i++) {
				request.put(prefix + i, i);
			}

			request.convertClass(Item.class);

			assertTrue(fieldNames.size() - before >= count
				, "覚えた数: " + (fieldNames.size() - before) + "（上限 " + PropertyUtil.MAX_NO_FIELD_CACHE + "）");

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
