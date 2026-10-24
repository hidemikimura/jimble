package io.jimble.util.convertor;

import io.jimble.util.convertor.ConvertorFixtures.Checked;
import io.jimble.util.convertor.ConvertorFixtures.Child;
import io.jimble.util.convertor.ConvertorFixtures.Holder;
import io.jimble.util.convertor.ConvertorFixtures.Item;
import io.jimble.util.convertor.ConvertorFixtures.LineItem;
import io.jimble.util.convertor.ConvertorFixtures.Member;
import io.jimble.util.convertor.ConvertorFixtures.Node;
import io.jimble.util.convertor.ConvertorFixtures.Order;
import io.jimble.util.convertor.ConvertorFixtures.Pair;
import io.jimble.util.convertor.ConvertorFixtures.Status;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.jimble.util.convertor.ConvertorFixtures.conv;
import static io.jimble.util.convertor.ConvertorFixtures.data;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convertor の守る約束（作り直したあとも同じでなければならない挙動）
 *
 * <p>
 * 型変換を新モジュール jimble-converter に置き換えても、{@link Convertor} は互換の窓口として残る。
 * ここに書いたことは<b>作り直しのあとも通らなければならない</b>。
 * 今の挙動のうち、作り直しで変える予定のものは {@link ConvertorCurrentQuirksTest} に分けてある。
 * </p>
 */
// deprecated にした Convertor が守る約束を確かめる
@SuppressWarnings("deprecation")
class ConvertorContractTest {

	@Nested
	@DisplayName("型が合っている値・null")
	class AsIs {

		@Test
		@DisplayName("型が合っている String は同じものを返す")
		void sameString () {

			String src = new String("x");
			String res = conv(src, String.class);
			assertSame(src, res);

		}

		@Test
		@DisplayName("型が合っている Integer は同じものを返す")
		void sameInteger () {

			Integer src = 1_234_567;
			Integer res = conv(src, Integer.class);
			assertSame(src, res);

		}

		@Test
		@DisplayName("型が合っている Bean は同じものを返す（写しを作らない）")
		void sameBean () {

			Item src = new Item();
			Item res = conv(src, Item.class);
			assertSame(src, res);

		}

		@Test
		@DisplayName("enum を同じ enum に変換すると同じ定数を返す")
		void sameEnum () {

			Status res = conv(Status.draft, Status.class);
			assertSame(Status.draft, res);

		}

		@Test
		@DisplayName("null は null（変換先が int でも null）")
		void nullIsNull () {

			Integer boxed = conv(null, Integer.class);
			Object primitive = conv(null, int.class);
			assertNull(boxed);
			assertNull(primitive);

		}

		@Test
		@DisplayName("変換先を渡さないとそのまま返す")
		void noDestination () {

			Object res = conv("x");
			assertEquals("x", res);

		}

		@Test
		@DisplayName("設定を渡す形と渡さない形は同じ結果になる")
		void withConfiguration () {

			Integer withConf = Convertor.convert(new Configration(), "12", Integer.class);
			Integer withCustom = Convertor.convert(new Configration(), "12", true, Integer.class);
			assertEquals(12, withConf);
			assertEquals(12, withCustom);

		}

	}

	@Nested
	@DisplayName("数（桁あふれしない範囲）")
	class Numbers {

		@Test
		@DisplayName("\"123\" → Integer 123")
		void stringToInteger () {

			Integer res = conv("123", Integer.class);
			assertEquals(123, res);

		}

		@Test
		@DisplayName("\"123\" → int 123")
		void stringToPrimitiveInt () {

			int res = conv("123", int.class);
			assertEquals(123, res);

		}

		@Test
		@DisplayName("\"-3\" → Integer -3、\"007\" → 7")
		void signedAndLeadingZero () {

			Integer negative = conv("-3", Integer.class);
			Integer zeros = conv("007", Integer.class);
			assertEquals(-3, negative);
			assertEquals(7, zeros);

		}

		@Test
		@DisplayName("Integer → Long")
		void integerToLong () {

			Long res = conv(5, Long.class);
			assertEquals(5L, res);

		}

		@Test
		@DisplayName("範囲内の Long → Integer")
		void longToInteger () {

			Integer res = conv(12L, Integer.class);
			assertEquals(12, res);

		}

		@Test
		@DisplayName("整数の BigDecimal → Integer")
		void bigDecimalToInteger () {

			Integer res = conv(new BigDecimal("42"), Integer.class);
			assertEquals(42, res);

		}

		@Test
		@DisplayName("小数部が 0 の Double → Long")
		void wholeDoubleToLong () {

			Long res = conv(2.0d, Long.class);
			assertEquals(2L, res);

		}

		@Test
		@DisplayName("Long の最大値の文字 → Long")
		void longMax () {

			Long res = conv(String.valueOf(Long.MAX_VALUE), Long.class);
			assertEquals(Long.MAX_VALUE, res);

		}

		@Test
		@DisplayName("\"12\" → Short、12 → Byte")
		void shortAndByte () {

			Short s = conv("12", Short.class);
			Byte b = conv(12, Byte.class);
			assertEquals((short) 12, s);
			assertEquals((byte) 12, b);

		}

		@Test
		@DisplayName("Integer → Double、BigDecimal → Double")
		void toDouble () {

			Double fromInt = conv(3, Double.class);
			Double fromDecimal = conv(new BigDecimal("2.5"), Double.class);
			assertEquals(3.0d, fromInt);
			assertEquals(2.5d, fromDecimal);

		}

		@Test
		@DisplayName("Float 0.1f → Double は 0.1（2進の誤差を持ち込まない）")
		void floatToDoubleKeepsDecimal () {

			Double res = conv(0.1f, Double.class);
			assertEquals(0.1d, res);

		}

		@Test
		@DisplayName("\"1.5\" → Double 1.5、Float 1.5")
		void stringToFloating () {

			Double d = conv("1.5", Double.class);
			Float f = conv("1.5", Float.class);
			assertEquals(1.5d, d);
			assertEquals(1.5f, f);

		}

		@Test
		@DisplayName("Double 2.5 → Float 2.5")
		void doubleToFloat () {

			Float res = conv(2.5d, Float.class);
			assertEquals(2.5f, res);

		}

		@Test
		@DisplayName("\"1.50\" → BigDecimal は桁（scale）を保つ")
		void stringToBigDecimalKeepsScale () {

			BigDecimal res = conv("1.50", BigDecimal.class);
			assertEquals(new BigDecimal("1.50"), res);

		}

		@Test
		@DisplayName("Double 0.1 → BigDecimal は 0.1（2進の誤差を持ち込まない）")
		void doubleToBigDecimal () {

			BigDecimal res = conv(0.1d, BigDecimal.class);
			assertEquals(new BigDecimal("0.1"), res);

		}

		@Test
		@DisplayName("Long → BigDecimal、Integer → BigDecimal")
		void integralToBigDecimal () {

			BigDecimal fromLong = conv(123_456_789_012L, BigDecimal.class);
			BigDecimal fromInt = conv(7, BigDecimal.class);
			assertEquals(new BigDecimal("123456789012"), fromLong);
			assertEquals(new BigDecimal("7"), fromInt);

		}

		@Test
		@DisplayName("\"12\" / 12L → BigInteger")
		void toBigInteger () {

			BigInteger fromString = conv("12", BigInteger.class);
			BigInteger fromLong = conv(12L, BigInteger.class);
			assertEquals(BigInteger.valueOf(12), fromString);
			assertEquals(BigInteger.valueOf(12), fromLong);

		}

		@Test
		@DisplayName("Number を指定すると BigDecimal になる")
		void numberIsBigDecimal () {

			Number res = conv("12", Number.class);
			assertEquals(new BigDecimal("12"), res);

		}

		@Test
		@DisplayName("true → Integer は 1")
		void booleanToInteger () {

			Integer res = conv(true, Integer.class);
			assertEquals(1, res);

		}

	}

	@Nested
	@DisplayName("文字・真偽値・enum")
	class TextAndFlags {

		@Test
		@DisplayName("数 → String")
		void numberToString () {

			String i = conv(12, String.class);
			String d = conv(new BigDecimal("1.50"), String.class);
			assertEquals("12", i);
			assertEquals("1.50", d);

		}

		@Test
		@DisplayName("Boolean → String、enum → 名前")
		void flagsToString () {

			String b = conv(true, String.class);
			String e = conv(Status.draft, String.class);
			assertEquals("true", b);
			assertEquals("draft", e);

		}

		@Test
		@DisplayName("\"true\" → true、\"false\" / \"FALSE\" / \"0\" → false")
		void stringToBoolean () {

			Boolean t = conv("true", Boolean.class);
			Boolean f = conv("false", Boolean.class);
			Boolean upper = conv("FALSE", Boolean.class);
			Boolean zero = conv("0", Boolean.class);
			assertTrue(t);
			assertFalse(f);
			assertFalse(upper);
			assertFalse(zero);

		}

		@Test
		@DisplayName("1 → true、0 → false")
		void numberToBoolean () {

			Boolean one = conv(1, Boolean.class);
			Boolean zero = conv(0, Boolean.class);
			assertTrue(one);
			assertFalse(zero);

		}

		@Test
		@DisplayName("enum は名前から作る")
		void enumByName () {

			Status res = conv("published", Status.class);
			assertEquals(Status.published, res);

		}

		@Test
		@DisplayName("enum の名前は大小を問わない")
		void enumIgnoresCase () {

			Status res = conv("DRAFT", Status.class);
			assertEquals(Status.draft, res);

		}

	}

	@Nested
	@DisplayName("Map → Bean")
	class MapToBean {

		private Map<String, Object> member () {

			Map<String, Object> m = new LinkedHashMap<>();
			m.put("name", "きむら");
			m.put("age", "42");
			m.put("status", "published");
			m.put("items", List.of(Map.of("code", "A", "qty", "3"), data("code", "B", "qty", 4)));
			return m;

		}

		@Test
		@DisplayName("private の項目に、型を合わせて書く")
		void privateFields () {

			Member res = conv(member(), Member.class);
			assertEquals("きむら", res.getName());
			assertEquals(42, res.getAge());

		}

		@Test
		@DisplayName("Map にあってクラスに無いキーは、例外にせず飛ばす")
		void unknownKeyIsSkipped () {

			Map<String, Object> m = member();
			m.put("unknown", "zz");
			Member res = assertDoesNotThrow(() -> ConvertorFixtures.<Member>conv(m, Member.class));
			assertEquals("きむら", res.getName());

		}

		@Test
		@DisplayName("enum の項目は名前から作る")
		void enumField () {

			Member res = conv(member(), Member.class);
			assertEquals(Status.published, res.getStatus());

		}

		@Test
		@DisplayName("List<Bean> の項目は、Map の要素も Data の要素も Bean にする")
		void nestedBeanList () {

			Member res = conv(member(), Member.class);
			assertEquals(2, res.getItems().size());
			assertInstanceOf(Item.class, res.getItems().get(0));
			assertEquals("A", res.getItems().get(0).code);
			assertEquals(3, res.getItems().get(0).qty);
			assertEquals("B", res.getItems().get(1).code);
			assertEquals(4, res.getItems().get(1).qty);

		}

		@Test
		@DisplayName("int の項目に null が来ても例外にせず 0 のまま")
		void nullIntoPrimitive () {

			Map<String, Object> m = new HashMap<>();
			m.put("age", null);
			m.put("name", "a");
			Member res = conv(m, Member.class);
			assertEquals(0, res.getAge());
			assertEquals("a", res.getName());

		}

		@Test
		@DisplayName("public の項目に書く")
		void publicFields () {

			Item res = conv(Map.of("code", "Z", "qty", 2), Item.class);
			assertEquals("Z", res.code);
			assertEquals(2, res.qty);

		}

		@Test
		@DisplayName("getter / setter の組がある項目に書く")
		void getterSetterPair () {

			Pair res = conv(Map.of("left", "L", "right", "9"), Pair.class);
			assertEquals("L", res.getLeft());
			assertEquals(9, res.right);

		}

		@Test
		@DisplayName("子クラスの自分の項目と、親の public の項目に書く")
		void childAndParentPublic () {

			Child res = conv(Map.of("childName", "C", "parentPublic", "U"), Child.class);
			assertEquals("C", res.childName());
			assertEquals("U", res.parentPublic);

		}

		@Test
		@DisplayName("Map<String, Integer> の項目は値の型を合わせる")
		void typedMapField () {

			Holder res = conv(Map.of("counts", Map.of("a", "3")), Holder.class);
			assertEquals(Map.of("a", 3), res.counts);

		}

		@Test
		@DisplayName("Set<String> の項目は重複を除いて順を保つ")
		void setField () {

			Holder res = conv(Map.of("tags", List.of("x", "x", "y")), Holder.class);
			assertEquals(new LinkedHashSet<>(List.of("x", "y")), res.tags);
			assertEquals(List.of("x", "y"), new ArrayList<>(res.tags));

		}

		@Test
		@DisplayName("int[] の項目は文字の一覧から作る")
		void intArrayField () {

			Holder res = conv(Map.of("nums", List.of("1", "2")), Holder.class);
			assertArrayEquals(new int[] {1, 2}, res.nums);

		}

		@Test
		@DisplayName("Data の項目は Map から Data にする")
		void dataField () {

			Holder res = conv(Map.of("extra", Map.of("k", "v")), Holder.class);
			assertInstanceOf(Data.class, res.extra);
			assertEquals("v", res.extra.getString("k"));

		}

		@Test
		@DisplayName("List<Data> の項目は Map の要素を Data にする")
		void dataListField () {

			Holder res = conv(Map.of("rows", List.of(Map.of("a", 1))), Holder.class);
			assertInstanceOf(Data.class, res.rows.get(0));
			assertEquals(1, res.rows.get(0).getInt("a"));

		}

		@Test
		@DisplayName("Bean の List<record> の項目は record にする")
		void recordListFieldInBean () {

			Map<String, Object> line = new HashMap<>();
			line.put("code", "A");
			line.put("qty", 1);
			Holder res = conv(Map.of("lines", List.of(line)), Holder.class);
			assertEquals(new LineItem("A", 1), res.lines.get(0));

		}

	}

	@Nested
	@DisplayName("record")
	class Records {

		@Test
		@DisplayName("Map → record（型を合わせる）")
		void fromMap () {

			LineItem res = conv(Map.of("code", "A", "qty", "5"), LineItem.class);
			assertEquals(new LineItem("A", 5), res);

		}

		@Test
		@DisplayName("Map にあって record に無いキーは、例外にせず飛ばす")
		void unknownKeyIsSkipped () {

			LineItem res = conv(Map.of("code", "A", "qty", 1, "extra", "zz"), LineItem.class);
			assertEquals(new LineItem("A", 1), res);

		}

		@Test
		@DisplayName("無いキーは int なら 0、Integer なら null、List なら null")
		void missingComponents () {

			Order res = conv(Map.of("name", "n"), Order.class);
			assertEquals("n", res.name());
			assertEquals(0, res.count());
			assertNull(res.boxed());
			assertNull(res.lines());

		}

		@Test
		@DisplayName("Data → record")
		void fromData () {

			Order res = data("name", "n", "count", 3).convertClass(Order.class);
			assertEquals("n", res.name());
			assertEquals(3, res.count());

		}

		@Test
		@DisplayName("Bean → record（同じ名前の項目を読む）")
		void fromBean () {

			Item item = new Item();
			item.code = "C";
			item.qty = 2;
			LineItem res = conv(item, LineItem.class);
			assertEquals(new LineItem("C", 2), res);

		}

		@Test
		@DisplayName("record の組み立てで例外が起きたら、Convertor.convert は RuntimeException を投げる")
		void constructorFailureThrows () {

			assertThrows(RuntimeException.class, () -> conv(Map.of("name", "bad"), Checked.class));

		}

	}

	@Nested
	@DisplayName("Bean → Data / Map")
	class BeanToMap {

		@Test
		@DisplayName("Bean → Data は項目名をキーにする")
		void beanToData () {

			Item item = new Item();
			item.code = "C";
			item.qty = 2;
			Data res = conv(item, Data.class);
			assertEquals("C", res.getString("code"));
			assertEquals(2, res.getInt("qty"));

		}

		@Test
		@DisplayName("Bean → Map は順を保つ Map にする")
		void beanToMap () {

			Item item = new Item();
			item.code = "C";
			item.qty = 2;
			Map<?, ?> res = conv(item, Map.class);
			assertInstanceOf(LinkedHashMap.class, res);
			assertEquals(Map.of("code", "C", "qty", 2), res);

		}

		@Test
		@DisplayName("JSON のオブジェクトの文字 → Data")
		void jsonToData () {

			Data res = conv("{\"a\":1}", Data.class);
			assertEquals(1, res.getInt("a"));
			assertEquals(1, res.size());

		}

	}

	@Nested
	@DisplayName("一覧・Map")
	class Collections {

		@Test
		@DisplayName("List<Data> → List<Bean>")
		void dataListToBeanList () {

			List<Data> src = List.of(data("code", "X", "qty", 9));
			List<Item> res = conv(src, List.class, Item.class);
			assertInstanceOf(ArrayList.class, res);
			assertInstanceOf(Item.class, res.get(0));
			assertEquals("X", res.get(0).code);
			assertEquals(9, res.get(0).qty);

		}

		@Test
		@DisplayName("List<String> → List<Integer>")
		void stringListToIntegerList () {

			List<Integer> res = conv(List.of("1", "2"), List.class, Integer.class);
			assertEquals(List.of(1, 2), res);

		}

		@Test
		@DisplayName("JSON の配列の文字 → List<Integer>")
		void jsonArrayToList () {

			List<Integer> res = conv("[1,2]", List.class, Integer.class);
			assertEquals(List.of(1, 2), res);

		}

		@Test
		@DisplayName("Map → Map<String, Integer> は値の型を合わせる")
		void typedMap () {

			Map<String, Integer> res = conv(Map.of("a", "1"), Map.class, String.class, Integer.class);
			assertEquals(Map.of("a", 1), res);

		}

		@Test
		@DisplayName("List → Set は重複を除いて順を保つ")
		void listToSet () {

			Set<String> res = conv(List.of("a", "a", "b"), Set.class);
			assertEquals(List.of("a", "b"), new ArrayList<>(res));

		}

		@Test
		@DisplayName("List<String> → int[]")
		void listToIntArray () {

			int[] res = conv(List.of("1", "2"), int[].class);
			assertArrayEquals(new int[] {1, 2}, res);

		}

	}

	@Nested
	@DisplayName("循環参照")
	class Cycles {

		@Test
		@DisplayName("自分を指す Bean を Data にしても例外にならない")
		void selfReferencingBeanToData () {

			Node node = new Node();
			node.name = "a";
			node.next = node;
			Data res = assertDoesNotThrow(() -> ConvertorFixtures.<Data>conv(node, Data.class));
			assertEquals("a", res.getString("name"));

		}

		@Test
		@DisplayName("自分を含む Data を Bean にしても例外にならず、循環のところは null")
		void selfReferencingDataToBean () {

			Data src = data("name", "a");
			src.put("next", src);
			Node res = assertDoesNotThrow(() -> ConvertorFixtures.<Node>conv(src, Node.class));
			assertEquals("a", res.name);
			assertNull(res.next);

		}

		@Test
		@DisplayName("同じ型が入れ子になっていても（循環でなければ）全部変換する")
		void nestedSameTypeIsNotCycle () {

			Node res = conv(Map.of("name", "a", "next", Map.of("name", "b")), Node.class);
			assertEquals("a", res.name);
			assertEquals("b", res.next.name);

		}

	}

	@Nested
	@DisplayName("日付（時間帯は " + ConvertorFixtures.ZONE + " に固定）")
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class Dates {

		/** 2024-01-15 00:00 JST */
		private static final long JAN_15 = 1_705_244_400_000L;

		@BeforeAll
		void fixZone () {
			ConvertorFixtures.fixZone();
		}

		@AfterAll
		void restoreZone () {
			ConvertorFixtures.restoreZone();
		}

		@Test
		@DisplayName("\"2024-01-15\" → その日の 0 時")
		void isoDate () {

			Date res = conv("2024-01-15", Date.class);
			assertEquals(JAN_15, res.getTime());

		}

		@Test
		@DisplayName("\"2024-01-15 10:20:30\" と \"2024/01/15 10:20:30\" → 同じ時刻")
		void dateTimeWithSeconds () {

			Date hyphen = conv("2024-01-15 10:20:30", Date.class);
			Date slash = conv("2024/01/15 10:20:30", Date.class);
			long expected = JAN_15 + ((10 * 60 + 20) * 60 + 30) * 1000L;
			assertEquals(expected, hyphen.getTime());
			assertEquals(expected, slash.getTime());

		}

		@Test
		@DisplayName("\"2024年01月15日\" → その日の 0 時")
		void japaneseDate () {

			Date res = conv("2024年01月15日", Date.class);
			assertEquals(JAN_15, res.getTime());

		}

		@Test
		@DisplayName("数（ミリ秒）・Instant・LocalDateTime → Date")
		void fromTemporal () {

			Date fromLong = conv(0L, Date.class);
			Date fromInstant = conv(Instant.ofEpochMilli(5), Date.class);
			Date fromLocal = conv(LocalDateTime.of(2024, 1, 15, 0, 0), Date.class);
			assertEquals(0L, fromLong.getTime());
			assertEquals(5L, fromInstant.getTime());
			assertEquals(JAN_15, fromLocal.getTime());

		}

		@Test
		@DisplayName("\"2024-01-15\" → java.sql.Date")
		void sqlDate () {

			java.sql.Date res = conv("2024-01-15", java.sql.Date.class);
			assertEquals(JAN_15, res.getTime());

		}

		@Test
		@DisplayName("Date → String は yyyy/MM/dd HH:mm:ss（いまの時間帯で）")
		void dateToString () {

			String res = conv(new Date(0), String.class);
			assertEquals("1970/01/01 09:00:00", res);

		}

		@Test
		@DisplayName("Bean の Date の項目は文字から作る")
		void dateFieldInBean () {

			Member res = conv(Map.of("joinedAt", "2024-01-15"), Member.class);
			assertEquals(JAN_15, res.getJoinedAt().getTime());

		}

	}

	@Nested
	@DisplayName("PropertyUtil の外から使われている入口")
	class PropertyUtilEntries {

		@Test
		@DisplayName("toString：null は null、String はそのまま、Data は JSON")
		void toStringBasics () {

			assertNull(PropertyUtil.toString(null));
			assertEquals("x", PropertyUtil.toString("x"));
			assertEquals("{\"a\":1}", PropertyUtil.toString(data("a", 1)));

		}

		@Test
		@DisplayName("toString：数・真偽値は toString の結果")
		void toStringScalars () {

			assertEquals("12", PropertyUtil.toString(12));
			assertEquals("true", PropertyUtil.toString(true));

		}

		@Test
		@DisplayName("getFieldNames：getter / setter の組と、static でない項目を返す")
		void fieldNames () {

			List<String> names = PropertyUtil.getFieldNames(Pair.class).stream()
				.map(PropertyUtil.MethodFieldInfo::getFieldName).toList();
			assertTrue(names.containsAll(List.of("left", "right")), names.toString());
			assertFalse(names.contains("SHARED"), names.toString());

		}

		@Test
		@DisplayName("getFieldNames：record は部品の名前を返す")
		void recordFieldNames () {

			List<String> names = PropertyUtil.getFieldNames(LineItem.class).stream()
				.map(PropertyUtil.MethodFieldInfo::getFieldName).toList();
			assertEquals(List.of("code", "qty"), names);

		}

		@Test
		@DisplayName("MethodFieldInfo.getProperty：private の項目も読める")
		void getProperty () {

			Pair pair = new Pair();
			pair.setLeft("L");
			PropertyUtil.MethodFieldInfo info = PropertyUtil.getFieldNames(Pair.class).stream()
				.filter(i -> "left".equals(i.getFieldName())).findFirst().orElseThrow();
			assertEquals("L", info.getProperty(pair));

		}

		@Test
		@DisplayName("newInstance：引数なしで作れるクラス・Data・boolean")
		void newInstance () {

			assertInstanceOf(Item.class, PropertyUtil.newInstance(Item.class));
			assertInstanceOf(Data.class, PropertyUtil.newInstance(Data.class));
			assertEquals(false, PropertyUtil.newInstance(boolean.class));

		}

		@Test
		@DisplayName("newInstance：record は既定値で作る（int は 0、Integer / List は null）")
		void newInstanceRecord () {

			Object res = PropertyUtil.newInstance(Order.class);
			Order order = assertInstanceOf(Order.class, res);
			assertEquals(0, order.count());
			assertNull(order.boxed());
			assertNull(order.lines());

		}

		@Test
		@DisplayName("isAssignableFrom：Class#isAssignableFrom と同じ向き")
		void isAssignableFrom () {

			assertTrue(PropertyUtil.isAssignableFrom(Number.class, Integer.class));
			assertFalse(PropertyUtil.isAssignableFrom(Integer.class, Number.class));
			assertTrue(PropertyUtil.isAssignableFrom(Map.class, Data.class));

		}

		@Test
		@DisplayName("newInstance：作れないもの（Integer / インターフェイス）は null")
		void newInstanceUnavailable () {

			assertNull(PropertyUtil.newInstance(Integer.class));
			assertNull(PropertyUtil.newInstance(List.class));

		}

	}

}
