package io.jimble.util.convertor;

import io.jimble.util.convertor.ConvertorFixtures.Item;
import io.jimble.util.convertor.ConvertorFixtures.LineItem;
import io.jimble.util.convertor.ConvertorFixtures.Status;
import io.jimble.util.data.Data;
import io.jimble.util.data.DataConversionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.jimble.util.convertor.ConvertorFixtures.data;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data の変換系の getter の守る約束（作り直したあとも同じでなければならない挙動）
 *
 * <p>
 * getInt / getLong / getDouble / getBigDecimal / getBoolean は Convertor を通らないが、
 * 作り直しで Convertor 側をこの規則（D-195）に揃えるので、今の挙動をここで固定する。
 * 今の挙動のうち、作り直しで変える予定のものは {@link DataConversionCurrentQuirksTest} に分けてある。
 * </p>
 */
class DataConversionContractTest {

	@Nested
	@DisplayName("D-195 数の厳しい読み方")
	class StrictNumbers {

		@Test
		@DisplayName("キーが無い・null・空文字・空白だけは 0")
		void missingIsZero () {

			Data d = data("nul", null, "empty", "", "blank", " ");
			for (String key : new String[] {"missing", "nul", "empty", "blank"}) {
				assertEquals(0, d.getInt(key), key);
				assertEquals(0L, d.getLong(key), key);
				assertEquals(0.0d, d.getDouble(key), key);
				assertEquals(BigDecimal.ZERO, d.getBigDecimal(key), key);
			}

		}

		@Test
		@DisplayName("Object 版はキーが無ければ null")
		void missingObjectIsNull () {

			Data d = data("empty", "");
			assertNull(d.getIntObject("missing"));
			assertNull(d.getLongObject("empty"));
			assertNull(d.getDoubleObject("missing"));

		}

		@Test
		@DisplayName("\"abc\" は getInt / getLong / getDouble / getBigDecimal で DataConversionException")
		void abcThrows () {

			Data d = data("x", "abc");
			assertThrows(DataConversionException.class, () -> d.getInt("x"));
			assertThrows(DataConversionException.class, () -> d.getLong("x"));
			assertThrows(DataConversionException.class, () -> d.getDouble("x"));
			assertThrows(DataConversionException.class, () -> d.getBigDecimal("x"));

		}

		@Test
		@DisplayName("例外はどのキーで失敗したかを持つ")
		void exceptionHasKey () {

			Data d = data("price", "abc");
			DataConversionException ex = assertThrows(DataConversionException.class, () -> d.getInt("price"));
			assertEquals("price", ex.key());

		}

		@Test
		@DisplayName("前後の空白は落として読む（\" 12 \" → 12）")
		void trims () {

			Data d = data("x", " 12 ");
			assertEquals(12, d.getInt("x"));
			assertEquals(12L, d.getLong("x"));
			assertEquals(new BigDecimal("12"), d.getBigDecimal("x"));

		}

		@Test
		@DisplayName("\"1.5\" を getInt すると例外（黙って丸めない）")
		void decimalStringToIntThrows () {

			Data d = data("x", "1.5");
			assertThrows(DataConversionException.class, () -> d.getInt("x"));

		}

		@Test
		@DisplayName("1.9f を getInt すると例外")
		void decimalNumberToIntThrows () {

			Data d = data("x", 1.9f);
			assertThrows(DataConversionException.class, () -> d.getInt("x"));

		}

		@Test
		@DisplayName("小数部が 0 の 1.0 は getInt / getLong で 1")
		void wholeDouble () {

			Data d = data("x", 1.0d);
			assertEquals(1, d.getInt("x"));
			assertEquals(1L, d.getLong("x"));

		}

		@Test
		@DisplayName("3e9 は getInt で例外（桁あふれ）、getLong では読める")
		void overflow () {

			Data d = data("x", 3_000_000_000L);
			assertThrows(DataConversionException.class, () -> d.getInt("x"));
			assertEquals(3_000_000_000L, d.getLong("x"));

		}

		@Test
		@DisplayName("\"1.5\" は getDouble で 1.5")
		void decimalToDouble () {

			assertEquals(1.5d, data("x", "1.5").getDouble("x"));

		}

		@Test
		@DisplayName("文字の \"NaN\" は getDouble で例外")
		void nanThrows () {

			Data d = data("x", "NaN");
			assertThrows(DataConversionException.class, () -> d.getDouble("x"));

		}

		@Test
		@DisplayName("getBigDecimal は桁（scale）を保つ（\"1.50\"）")
		void bigDecimalKeepsScale () {

			assertEquals(new BigDecimal("1.50"), data("x", "1.50").getBigDecimal("x"));

		}

		@Test
		@DisplayName("BigDecimal の値は同じものを返す")
		void bigDecimalAsIs () {

			BigDecimal v = new BigDecimal("3.14");
			assertSame(v, data("x", v).getBigDecimal("x"));

		}

	}

	@Nested
	@DisplayName("D-195 真偽値の厳しい読み方")
	class StrictBoolean {

		@Test
		@DisplayName("キーが無い・null・空文字は false（Object 版は null）")
		void missingIsFalse () {

			Data d = data("nul", null, "empty", "");
			assertFalse(d.getBoolean("missing"));
			assertFalse(d.getBoolean("nul"));
			assertFalse(d.getBoolean("empty"));
			assertNull(d.getBooleanObject("missing"));

		}

		@Test
		@DisplayName("\"true\" / \"TRUE\" / \"1\" / 1 は true")
		void trueValues () {

			Data d = data("a", "true", "b", "TRUE", "c", "1", "d", 1);
			assertTrue(d.getBoolean("a"));
			assertTrue(d.getBoolean("b"));
			assertTrue(d.getBoolean("c"));
			assertTrue(d.getBoolean("d"));

		}

		@Test
		@DisplayName("\"false\" / \"0\" / 0 は false")
		void falseValues () {

			Data d = data("a", "false", "b", "0", "c", 0);
			assertFalse(d.getBoolean("a"));
			assertFalse(d.getBoolean("b"));
			assertFalse(d.getBoolean("c"));

		}

		@Test
		@DisplayName("\"yes\" / \"no\" / 2 は例外")
		void otherValuesThrow () {

			Data d = data("yes", "yes", "no", "no", "two", 2);
			assertThrows(DataConversionException.class, () -> d.getBoolean("yes"));
			assertThrows(DataConversionException.class, () -> d.getBoolean("no"));
			assertThrows(DataConversionException.class, () -> d.getBoolean("two"));

		}

	}

	@Nested
	@DisplayName("getString（値が String でないとき）")
	class GetString {

		@Test
		@DisplayName("数は toString の結果（Integer / Long / Double / BigDecimal）")
		void numbers () {

			Data d = data("i", 5, "l", 12L, "d", 1.5d, "bd", new BigDecimal("1.50"));
			assertEquals("5", d.getString("i"));
			assertEquals("12", d.getString("l"));
			assertEquals("1.5", d.getString("d"));
			assertEquals("1.50", d.getString("bd"));

		}

		@Test
		@DisplayName("Boolean・enum は文字にする")
		void flags () {

			Data d = data("b", true, "e", Status.draft);
			assertEquals("true", d.getString("b"));
			assertEquals("draft", d.getString("e"));

		}

		@Test
		@DisplayName("入れ子の Data は JSON の文字")
		void nestedData () {

			Data d = data("m", data("a", 1));
			assertEquals("{\"a\":1}", d.getString("m"));

		}

		@Test
		@DisplayName("キーが無ければ null")
		void missing () {

			assertNull(new Data().getString("missing"));

		}

	}

	@Nested
	@DisplayName("getDate（時間帯は " + ConvertorFixtures.ZONE + " に固定）")
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class GetDate {

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
		@DisplayName("Date の値は同じものを返す")
		void sameDate () {

			Date v = new Date(0);
			assertSame(v, data("x", v).getDate("x"));

		}

		@Test
		@DisplayName("\"2024-01-15\" → その日の 0 時")
		void fromString () {

			assertEquals(JAN_15, data("x", "2024-01-15").getDate("x").getTime());

		}

		@Test
		@DisplayName("数（ミリ秒）・Instant → Date")
		void fromNumberAndInstant () {

			Data d = data("n", 1000L, "i", Instant.ofEpochMilli(7));
			assertEquals(1000L, d.getDate("n").getTime());
			assertEquals(7L, d.getDate("i").getTime());

		}

		@Test
		@DisplayName("キーが無い・空白だけは null")
		void missing () {

			Data d = data("blank", " ");
			assertNull(d.getDate("missing"));
			assertNull(d.getDate("blank"));

		}

		@Test
		@DisplayName("日付として読めない文字は例外")
		void unreadableThrows () {

			Data d = data("x", "abc");
			assertThrows(DataConversionException.class, () -> d.getDate("x"));

		}

	}

	@Nested
	@DisplayName("getData / getDataList")
	class GetData {

		@Test
		@DisplayName("getData：Data の値は同じものを返す")
		void sameData () {

			Data inner = data("a", 1);
			assertSame(inner, data("m", inner).getData("m"));

		}

		@Test
		@DisplayName("getData：Map は Data に置き換えて返す（返したものへの書き込みが元に届く）")
		void mapIsReplaced () {

			Data d = data("m", new HashMap<>(Map.of("k", "v")));
			Data res = d.getData("m");
			assertEquals("v", res.getString("k"));
			assertSame(res, d.get("m"));

		}

		@Test
		@DisplayName("getData：JSON の文字は Data にして返し、元の値は書き換えない")
		void jsonIsNotWrittenBack () {

			Data d = data("m", "{\"a\":1}");
			assertEquals(1, d.getData("m").getInt("a"));
			assertEquals("{\"a\":1}", d.get("m"));

		}

		@Test
		@DisplayName("getData：JSON でない文字・数は例外")
		void unreadableThrows () {

			Data d = data("s", "x", "i", 5);
			assertThrows(DataConversionException.class, () -> d.getData("s"));
			assertThrows(DataConversionException.class, () -> d.getData("i"));

		}

		@Test
		@DisplayName("getData：Bean は項目名をキーにした Data")
		void beanToData () {

			Item item = new Item();
			item.code = "C";
			item.qty = 2;
			Data res = data("b", item).getData("b");
			assertEquals("C", res.getString("code"));
			assertEquals(2, res.getInt("qty"));

		}

		@Test
		@DisplayName("getData：キーが無ければ null")
		void missing () {

			assertNull(new Data().getData("m"));

		}

		@Test
		@DisplayName("getDataList：Data だけの一覧は同じものを返す")
		void sameList () {

			List<Data> list = new ArrayList<>(List.of(data("a", 1)));
			assertSame(list, data("m", list).getDataList("m"));

		}

		@Test
		@DisplayName("getDataList：Map の一覧は Data の一覧に置き換えて返す")
		void mapListIsReplaced () {

			Data d = data("m", new ArrayList<>(List.of(new HashMap<>(Map.of("a", 1)))));
			List<Data> res = d.getDataList("m");
			assertInstanceOf(Data.class, res.get(0));
			assertEquals(1, res.get(0).getInt("a"));
			assertSame(res, d.get("m"));

		}

		@Test
		@DisplayName("getDataList：JSON の配列の文字を読む")
		void jsonArray () {

			List<Data> res = data("m", "[{\"a\":1}]").getDataList("m");
			assertEquals(1, res.size());
			assertEquals(1, res.get(0).getInt("a"));

		}

		@Test
		@DisplayName("getDataList：一覧でも配列でも文字でもない値は例外、キーが無ければ null")
		void scalarThrows () {

			Data d = data("i", 5);
			assertThrows(DataConversionException.class, () -> d.getDataList("i"));
			assertNull(d.getDataList("missing"));

		}

	}

	@Nested
	@DisplayName("getValue")
	class GetValue {

		@Test
		@DisplayName("戻り値の型に変換する（Integer → Long / String）")
		void convertsToReturnType () {

			Data d = data("i", 5);
			Long l = d.getValue("i");
			String s = d.getValue("i");
			assertEquals(5L, l);
			assertEquals("5", s);

		}

		@Test
		@DisplayName("キーが無ければ null")
		void missing () {

			Integer res = new Data().getValue("missing");
			assertNull(res);

		}

		@Test
		@DisplayName("読めない値は例外（\"x\" を Integer で）")
		void unreadableThrows () {

			Data d = data("s", "x");
			assertThrows(DataConversionException.class, () -> {
				Integer res = d.getValue("s");
				assertNull(res);
			});

		}

		@Test
		@DisplayName("合わない名前の enum は例外")
		void unknownEnumThrows () {

			Data d = data("s", "x");
			assertThrows(DataConversionException.class, () -> {
				Status res = d.getValue("s");
				assertNull(res);
			});

		}

	}

	@Nested
	@DisplayName("一覧の getter")
	class Lists {

		@Test
		@DisplayName("getObjectList：Map の一覧を Bean の一覧にする")
		void objectListOfBeans () {

			Data d = data("l", List.of(Map.of("code", "Z", "qty", "2")));
			List<Item> res = d.getObjectList("l", Item.class);
			assertEquals("Z", res.get(0).code);
			assertEquals(2, res.get(0).qty);

		}

		@Test
		@DisplayName("getObjectList：変換した写しを返し、元の値は書き換えない")
		void objectListNotWrittenBack () {

			List<Object> original = new ArrayList<>(List.of("1", "2"));
			Data d = data("l", original);
			List<Integer> res = d.getObjectList("l", Integer.class);
			assertEquals(List.of(1, 2), res);
			assertSame(original, d.get("l"));
			assertEquals(List.of("1", "2"), original);

		}

		@Test
		@DisplayName("getObjectList：キーが無ければ null")
		void objectListMissing () {

			assertNull(new Data().getObjectList("l", Item.class));

		}

		@Test
		@DisplayName("getIntList：文字の一覧を数にし、元の値は書き換えない")
		void intList () {

			List<Object> original = new ArrayList<>(List.of("1", "2"));
			Data d = data("l", original);
			assertEquals(List.of(1, 2), d.getIntList("l"));
			assertSame(original, d.get("l"));

		}

		@Test
		@DisplayName("getIntList：JSON の配列の文字を読む")
		void intListFromJson () {

			assertEquals(List.of(1, 2), data("l", "[1,2]").getIntList("l"));

		}

		@Test
		@DisplayName("getLongList：3e9 を読める")
		void longList () {

			assertEquals(List.of(1L, 3_000_000_000L), data("l", List.of(1, 3_000_000_000L)).getLongList("l"));

		}

		@Test
		@DisplayName("getStringList：数を文字にする、キーが無ければ null")
		void stringList () {

			Data d = data("l", List.of(1, "a", 1.5d));
			assertEquals(List.of("1", "a", "1.5"), d.getStringList("l"));
			assertNull(d.getStringList("missing"));

		}

	}

	@Nested
	@DisplayName("getByteArray")
	class ByteArray {

		@Test
		@DisplayName("byte[] の値は同じものを返す")
		void same () {

			byte[] v = {1, 2};
			assertSame(v, data("b", v).getByteArray("b"));

		}

		@Test
		@DisplayName("数の一覧を byte[] にする")
		void fromList () {

			assertArrayEquals(new byte[] {1, 2}, data("b", List.of(1, 2)).getByteArray("b"));

		}

		@Test
		@DisplayName("キーが無ければ null")
		void missing () {

			assertNull(new Data().getByteArray("b"));

		}

	}

	@Nested
	@DisplayName("convert / convertClass")
	class Convert {

		@Test
		@DisplayName("convert：戻り値の型の Bean にする")
		void convertToBean () {

			Item res = data("code", "Q", "qty", "3").convert();
			assertEquals("Q", res.code);
			assertEquals(3, res.qty);

		}

		@Test
		@DisplayName("convertClass：指定した Bean にする")
		void convertClassToBean () {

			Item res = data("code", "Q", "qty", 3, "unknown", "zz").convertClass(Item.class);
			assertEquals("Q", res.code);
			assertEquals(3, res.qty);

		}

		@Test
		@DisplayName("convertClass：record にする")
		void convertClassToRecord () {

			LineItem res = data("code", "Q", "qty", "3").convertClass(LineItem.class);
			assertEquals(new LineItem("Q", 3), res);

		}

		@Test
		@DisplayName("convertClass：型引数つきの Map にする（順を保つ）")
		void convertClassToTypedMap () {

			Map<String, String> res = data("code", "Q").convertClass(Map.class, String.class, String.class);
			assertInstanceOf(LinkedHashMap.class, res);
			assertEquals(Map.of("code", "Q"), res);

		}

	}

}
