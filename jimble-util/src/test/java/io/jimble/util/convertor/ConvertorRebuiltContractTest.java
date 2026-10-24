package io.jimble.util.convertor;

import io.jimble.converter.ConversionException;
import io.jimble.util.convertor.ConvertorFixtures.Checked;
import io.jimble.util.convertor.ConvertorFixtures.Child;
import io.jimble.util.convertor.ConvertorFixtures.Item;
import io.jimble.util.convertor.ConvertorFixtures.LineItem;
import io.jimble.util.convertor.ConvertorFixtures.Order;
import io.jimble.util.convertor.ConvertorFixtures.SetterOnly;
import io.jimble.util.convertor.ConvertorFixtures.Status;
import io.jimble.util.data.Data;
import io.jimble.util.data.DataConversionException;
import io.jimble.util.json.JsonParseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 守る約束：jimble-converter に替えてからの Convertor と Data の変換（D-307）
 *
 * <p>
 * <b>値が変わってしまう変換は、黙って null / 0 にせず例外にする。</b>
 * 作り直す前は、読めない値を null や 0 にし、桁あふれ・切り捨て・ありえない日付の繰り上げを黙って通していた
 * （その記録は {@link ConvertorCurrentQuirksTest} / {@link DataConversionCurrentQuirksTest} にあったもの）。
 * </p>
 */
class ConvertorRebuiltContractTest {

	@Nested
	@DisplayName("Bean への書き込み")
	class BeanWrites {

		@Test
		@DisplayName("setter しか無い項目にも setter で入れる")
		void setterOnly () {

			SetterOnly res = conv(Map.of("label", "L"), SetterOnly.class);
			assertEquals("L", res.hidden());

		}

		@Test
		@DisplayName("親クラスの private の項目にも書く（読み書きが揃う）")
		void superclassField () {

			Child res = conv(Map.of("parentName", "P", "childName", "C"), Child.class);
			assertEquals("P", res.parentName());
			assertEquals("C", res.childName());

		}

		@ParameterizedTest
		@ValueSource(strings = {"abc", "1.5"})
		@DisplayName("int の項目に読めない値・小数を入れると、場所つきの例外（作り直す前は 0）")
		void rejectsBadInt (String value) {

			ConversionException ex = assertThrows(ConversionException.class, () -> conv(Map.of("qty", value), Item.class));
			assertEquals("$.qty", ex.path());

		}

	}

	@Nested
	@DisplayName("数")
	class Numbers {

		@ParameterizedTest
		@ValueSource(strings = {"1.5", "abc", "1:00"})
		@DisplayName("整数に読めない文字は例外（作り直す前は null / 0、\"1:00\" は 100）")
		void rejectsBadIntegerText (String text) {

			assertThrows(ConversionException.class, () -> conv(text, Integer.class));
			assertThrows(ConversionException.class, () -> conv(text, int.class));
			assertThrows(ConversionException.class, () -> conv(text, Long.class));

		}

		@Test
		@DisplayName("桁あふれ・小数の切り捨ては例外（作り直す前は黙って桁あふれ・切り捨て）")
		void rejectsOverflowAndTruncation () {

			assertThrows(ConversionException.class, () -> conv(3_000_000_000L, Integer.class));
			assertThrows(ConversionException.class, () -> conv(70_000, Short.class));
			assertThrows(ConversionException.class, () -> conv(300, Byte.class));
			assertThrows(ConversionException.class, () -> conv(1.9d, Integer.class));

		}

		@Test
		@DisplayName("前後の空白は落として読む（Data#getInt と同じ。作り直す前は null）")
		void stripsBlanks () {

			assertEquals(12, (Integer) conv(" 12 ", Integer.class));

		}

		@Test
		@DisplayName("小数に読めない文字は例外（作り直す前は BigDecimal なら 0、Double なら null）")
		void rejectsBadDecimalText () {

			assertThrows(ConversionException.class, () -> conv("abc", BigDecimal.class));
			assertThrows(ConversionException.class, () -> conv("abc", Double.class));
			assertThrows(ConversionException.class, () -> conv("abc", double.class));

		}

	}

	@Nested
	@DisplayName("真偽値・文字・enum")
	class FlagsAndEnums {

		@Test
		@DisplayName("\"no\" → false、2 → 例外（作り直す前は \"no\" が true、2 が false）")
		void booleans () {

			assertFalse((Boolean) conv("no", Boolean.class));
			assertFalse((boolean) conv("no", boolean.class));
			assertThrows(ConversionException.class, () -> conv(2, Boolean.class));

		}

		@Test
		@DisplayName("1文字でない文字 → Character は例外（作り直す前は先頭の文字）")
		void character () {

			assertThrows(ConversionException.class, () -> conv("ab", Character.class));

		}

		@Test
		@DisplayName("合わない名前・数 → enum は例外（作り直す前は null）")
		void enums () {

			assertThrows(ConversionException.class, () -> conv("nope", Status.class));
			assertThrows(ConversionException.class, () -> conv(0, Status.class));

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

		@ParameterizedTest
		@ValueSource(strings = {"2024-13-45", "2024-02-30", "2024-01-15abc", "abc"})
		@DisplayName("ありえない日付・後ろのゴミ・読めない文字は例外（作り直す前は繰り上げ・無視・null）")
		void rejectsBadDates (String text) {

			assertThrows(ConversionException.class, () -> conv(text, Date.class));

		}

		@Test
		@DisplayName("\"...Z\" は UTC として読む（作り直す前は Z を無視して、いまの時間帯の時刻にしていた）")
		void readsOffset () {

			Date date = conv("2024-01-15T10:00:00.000Z", Date.class);
			assertEquals(JAN_15 + 19 * HOUR, date.getTime());
			assertEquals(Instant.parse("2024-01-15T10:00:00Z"), conv("2024-01-15T10:00:00Z", Instant.class));

		}

		@Test
		@DisplayName("LocalDate / LocalDateTime にできる（作り直す前は変換できなかった）")
		void javaTime () {

			assertEquals(LocalDate.of(2024, 1, 15), conv("2024-01-15", LocalDate.class));
			assertEquals(LocalDateTime.of(2024, 1, 15, 10, 0), conv("2024-01-15T10:00:00", LocalDateTime.class));

		}

	}

	@Nested
	@DisplayName("record")
	class Records {

		@Test
		@DisplayName("record の List<LineItem> の要素も LineItem にする（Map.of の要素でも落ちない）")
		void listElements () {

			Map<String, Object> line = new HashMap<>();
			line.put("code", "A");
			line.put("qty", 1);
			Order res = conv(Map.of("name", "n", "lines", new ArrayList<>(List.of(line))), Order.class);
			assertEquals(List.of(new LineItem("A", 1)), res.lines());

			Order immutable = conv(Map.of("lines", List.of(Map.of("code", "A"))), Order.class);
			assertInstanceOf(LineItem.class, immutable.lines().getFirst());

		}

		@Test
		@DisplayName("無い String の部品は null（作り直す前は空文字で、record の検査をすり抜けていた）")
		void missingString () {

			assertNull(((Order) conv(Map.of("count", 1), Order.class)).name());
			Checked checked = new Data().convertClass(Checked.class);
			assertNull(checked.name());

		}

	}

	@Nested
	@DisplayName("Data・byte[]")
	class DataAndBytes {

		@Test
		@DisplayName("byte[]（UTF-8 の JSON）→ Data は JSON として読んだものだけ（作り直す前は各 byte が 0..n のキーで混ざった）")
		void bytesToData () {

			Data res = conv("{\"a\":1}".getBytes(StandardCharsets.UTF_8), Data.class);
			assertEquals(data("a", 1), res);

		}

		@Test
		@DisplayName("JSON でない文字 → Data は例外（Data#getData と同じ。作り直す前は空の Data）")
		void textToData () {

			assertThrows(JsonParseException.class, () -> conv("abc", Data.class));

		}

		@Test
		@DisplayName("文字 → byte[] は UTF-8 の byte 列（作り直す前は [0]）")
		void textToBytes () {

			assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), conv("abc", byte[].class));
			assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), data("b", "abc").getByteArray("b"));

		}

		@Test
		@DisplayName("自分を含む Map を Data にしても落ちない（作り直す前は StackOverflowError）")
		void cycle () {

			Map<String, Object> self = new HashMap<>();
			self.put("name", "a");
			self.put("self", self);
			Data res = conv(self, Data.class);
			assertEquals("a", res.getString("name"));

		}

		@Test
		@DisplayName("型が合っていても List / Map / Data は写しを返す（返した値を書き換えても元は変わらない）")
		void copiesContainers () {

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

	}

	@Nested
	@DisplayName("文字から作らない型")
	class UnsafeTypes {

		@Test
		@DisplayName("Class / InetAddress / Pattern は文字から作らない（クラスの読み込み・DNS・ReDoS を外から起こせた）")
		void unsafe () {

			assertThrows(ConversionException.class, () -> conv("java.lang.String", Class.class));
			assertThrows(ConversionException.class, () -> conv("127.0.0.1", InetAddress.class));
			assertThrows(ConversionException.class, () -> conv("a+", Pattern.class));

		}

		@Test
		@DisplayName("UUID にできる（作り直す前は変換できなかった）")
		void uuid () {

			String text = "123e4567-e89b-12d3-a456-426614174000";
			assertEquals(UUID.fromString(text), conv(text, UUID.class));

		}

	}

	@Nested
	@DisplayName("Data の getter")
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class DataGetters {

		@BeforeAll
		void fixZone () {
			ConvertorFixtures.fixZone();
		}

		@AfterAll
		void restoreZone () {
			ConvertorFixtures.restoreZone();
		}

		@Test
		@DisplayName("一覧の getter は、読めない要素・桁あふれ・小数の切り捨てを例外にする（getInt と揃う。D-195）")
		void lists () {

			assertThrows(DataConversionException.class, () -> data("l", List.of(3_000_000_000L)).getIntList("l"));
			assertThrows(DataConversionException.class, () -> data("l", List.of("x", 1.9d, "7")).getIntList("l"));
			assertThrows(DataConversionException.class, () -> data("l", List.of("x", "1.5")).getDoubleList("l"));
			assertThrows(DataConversionException.class, () -> data("l", List.of("a", "b")).getDataList("l"));

		}

		@Test
		@DisplayName("getByteArray は桁あふれを例外にする（作り直す前は 300 が 44）")
		void byteArray () {

			assertThrows(DataConversionException.class, () -> data("b", List.of(1, 300)).getByteArray("b"));

		}

		@ParameterizedTest
		@ValueSource(strings = {"2024-13-45", "2024-01-15abc"})
		@DisplayName("getDate は、ありえない日付・後ろのゴミを例外にする（D-161 で残していたもの）")
		void rejectsBadDates (String text) {

			assertThrows(DataConversionException.class, () -> data("d", text).getDate("d"));

		}

		@Test
		@DisplayName("getDate は \"...Z\" を UTC として読む")
		void readsOffset () {

			assertEquals(1_705_244_400_000L + 19 * 3_600_000L, data("d", "2024-01-15T10:00:00.000Z").getDate("d").getTime());

		}

	}

	@Test
	@DisplayName("知らないキーの名前を覚えない（D-226 の残り。作り直す前は FIELD_NAME に上限なしに覚え続けた）")
	void doesNotRememberUnknownKeys () throws Exception {

		Field field = PropertyUtil.class.getDeclaredField("FIELD_NAME");
		field.setAccessible(true);
		Map<?, ?> fieldNames = (Map<?, ?>) field.get(null);
		int before = fieldNames.size();
		Data request = new Data();
		String prefix = "unknown_" + UUID.randomUUID() + "_";
		for (int i = 0; i < 100; i++) {
			request.put(prefix + i, i);
		}

		request.convertClass(Item.class);

		assertEquals(before, fieldNames.size());

	}

}
