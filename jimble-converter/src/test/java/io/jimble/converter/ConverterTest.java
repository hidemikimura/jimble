package io.jimble.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 型変換（D-307）
 */
class ConverterTest {

	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

	private static Map<String, Object> map (Object... keyValues) {

		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((String) keyValues[i], keyValues[i + 1]);
		}
		return map;

	}

	/** record */
	record Item (String name, int qty, BigDecimal price) {
	}

	/** record（入れ子） */
	record Order (long id, List<Item> items, Optional<String> note, LocalDate date) {
	}

	/** Bean */
	public static class Customer {

		private String name;
		private int age;
		private String setterCalled = "no";

		/** @param name	名前 */
		public void setName (String name) {
			this.name = name.strip();
			this.setterCalled = "yes";
		}

		/** @return	名前 */
		public String getName () {
			return name;
		}

		/** @return	年齢 */
		public int getAge () {
			return age;
		}

		/** @param age	年齢 */
		public void setAge (int age) {
			this.age = age;
		}

	}

	/** transient の項目を持つ Bean */
	public static class Holder {

		private String name;
		private transient String cache = "kept";

	}

	enum Status { ACTIVE, STOPPED }

	@Nested
	@DisplayName("1つの値（受け付ける）")
	class AcceptsScalars {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			" 12 |12", "１２|12", "1,234|1234", "－５|-5", "1.0|1"
		})
		@DisplayName("前後の空白・全角の数字・3 桁ごとのカンマ・小数点以下が 0 の小数")
		void integers (String text, int expected) {

			assertEquals(expected, Converter.convert(text, int.class));

		}

		@Test
		@DisplayName("数の型どうし（範囲の中）")
		void numbers () {

			assertEquals(3L, Converter.convert(3, long.class));
			assertEquals(3, Converter.convert(3L, Integer.class));
			assertEquals(1.5, Converter.convert("1.5", double.class));
			assertEquals(new BigDecimal("1.50"), Converter.convert("1.50", BigDecimal.class));
			assertEquals("12", Converter.convert(12, String.class));
			assertEquals(1, Converter.convert(true, int.class));
			assertEquals(0L, Converter.convert(false, Long.class));

		}

		@ParameterizedTest
		@CsvSource({"true,true", "TRUE,true", "1,true", "yes,true", "on,true", "false,false", "0,false", "no,false", "off,false"})
		@DisplayName("真偽の書き方（大文字小文字は問わない）")
		void booleans (String text, boolean expected) {

			assertEquals(expected, Converter.convert(text, boolean.class));

		}

		@Test
		@DisplayName("enum は名前（大文字小文字は問わない）。enum を文字にすると名前")
		void enums () {

			assertEquals(Status.ACTIVE, Converter.convert("active", Status.class));
			assertEquals("STOPPED", Converter.convert(Status.STOPPED, String.class));

		}

		@Test
		@DisplayName("日時：文字から・日時の型どうし・時差つき")
		void dateTimes () {

			ConverterOptions tokyo = ConverterOptions.builder().zone(TOKYO).build();

			assertEquals(LocalDate.of(2026, 10, 24), Converter.convert("2026/10/24", LocalDate.class));
			assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), Converter.convert("2026-10-24T10:00:00", LocalDateTime.class));
			assertEquals(LocalDateTime.of(2026, 10, 24, 19, 0), Converter.convert("2026-10-24T10:00:00Z", LocalDateTime.class, tokyo));
			assertEquals(LocalDate.of(1970, 1, 1), Converter.convert(new Date(0), LocalDate.class, tokyo));
			assertEquals(0L, Converter.convert(OffsetDateTime.parse("1970-01-01T09:00:00+09:00"), Date.class).getTime());
			assertEquals(LocalDate.of(2024, 1, 15), Converter.convert("2024年01月15日", LocalDate.class));
			assertEquals(LocalDateTime.of(2024, 1, 15, 10, 20, 30), Converter.convert("2024年01月15日 10時20分30秒", LocalDateTime.class));
			assertEquals(LocalDateTime.of(2024, 1, 15, 10, 20), Converter.convert("2024年01月15日 10:20", LocalDateTime.class));

		}

		@Test
		@DisplayName("空文字と null は、プリミティブなら 0 / false、それ以外は null")
		void blanks () {

			assertEquals(0, Converter.convert("", int.class));
			assertNull(Converter.convert("", Integer.class));
			assertEquals(false, Converter.convert(null, boolean.class));

		}

		@Test
		@DisplayName("要素1つのリストは、その要素（フォームの a[]=5 など）")
		void singleElementList () {

			assertEquals(5, Converter.convert(List.of("5"), int.class));

		}

	}

	@Nested
	@DisplayName("1つの値（断る。場所つき）")
	class RejectsScalars {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"1.5|int", "abc|int", "3000000000|int", "1,23|int", "maybe|boolean", "UNKNOWN|Status",
			"2024-13-45|LocalDate", "2024-02-30|LocalDate", "2024-01-15abc|LocalDate"
		})
		@DisplayName("値が変わる・読めない・ありえない")
		void rejects (String text, String typeName) {

			Class<?> type = switch (typeName) {
				case "int" -> int.class;
				case "boolean" -> boolean.class;
				case "Status" -> Status.class;
				default -> LocalDate.class;
			};

			ConversionException e = assertThrows(ConversionException.class, () -> Converter.convert(text, type));
			assertEquals("$", e.path());

		}

		@Test
		@DisplayName("要素が2つ以上のリストは、1つの値にしない")
		void multiElementList () {

			assertThrows(ConversionException.class, () -> Converter.convert(List.of("5", "6"), int.class));

		}

		@Test
		@DisplayName("ゆるい変換なら、読めない値は null / 0")
		void lenient () {

			assertEquals(0, Converter.convert("abc", int.class, ConverterOptions.LENIENT));
			assertNull(Converter.convert("1.5", Integer.class, ConverterOptions.LENIENT));
			assertNull(Converter.convert("x", Status.class, ConverterOptions.LENIENT));

		}

		@Test
		@DisplayName("危ない型（Class・InetAddress・Pattern・File）は、許さなければ作らない")
		void unsafeTypes () {

			assertThrows(ConversionException.class, () -> Converter.convert("java.lang.String", Class.class));
			assertThrows(ConversionException.class, () -> Converter.convert("(a+)+", Pattern.class));
			assertThrows(ConversionException.class, () -> Converter.convert("/etc/passwd", java.io.File.class));

			ConverterOptions allow = ConverterOptions.builder().allowUnsafeTypes(true).build();
			assertEquals(String.class, Converter.convert("java.lang.String", Class.class, allow));

		}

	}

	/** 足した変換のしかたで作る型 */
	record Money (long yen) {
	}

	/** Money を持つ record */
	record Price (String name, Money money) {
	}

	@Nested
	@DisplayName("型ごとの変換のしかた（typeConverter）")
	class TypeConverters {

		private final ConverterOptions options = ConverterOptions.builder()
			.typeConverter(Money.class, value -> new Money(Long.parseLong(value.toString().replace("円", ""))))
			.build();

		@Test
		@DisplayName("足した変換のしかたで作る（入れ子の項目でも）。値がもうその型なら呼ばない")
		void converts () {

			assertEquals(new Money(500), Converter.convert("500円", Money.class, options));
			assertEquals(new Price("a", new Money(300)), Converter.convert(map("name", "a", "money", "300円"), Price.class, options));

			Money money = new Money(1);
			assertSame(money, Converter.convert(money, Money.class, options));

		}

		@Test
		@DisplayName("IllegalArgumentException は場所つきの ConversionException（ゆるい変換なら null）")
		void failure () {

			ConversionException e = assertThrows(ConversionException.class,
				() -> Converter.convert(map("money", "abc"), Price.class, options));
			assertEquals("$.money", e.path());

			ConverterOptions lenient = options.toBuilder().lenient(true).build();
			assertNull(Converter.convert(map("money", "abc"), Price.class, lenient).money());

		}

		@Test
		@DisplayName("危ない型にも、足せば作れる（allowUnsafeTypes は要らない）")
		void unsafeType () {

			ConverterOptions pattern = ConverterOptions.builder()
				.typeConverter(Pattern.class, value -> Pattern.compile(Pattern.quote(value.toString())))
				.build();

			assertEquals("\\Qa+\\E", Converter.convert("a+", Pattern.class, pattern).pattern());

		}

	}

	@Nested
	@DisplayName("オブジェクト")
	class Objects {

		@Test
		@DisplayName("Map → record（入れ子・List・Optional・日付。知らないキーは飛ばす）")
		void mapToRecord () {

			Order order = Converter.convert(map(
				"id", "7", "items", List.of(map("name", "a", "qty", "2", "price", "1.50", "extra", 1)),
				"note", "hi", "date", "2026/10/24", "unknown", map("deep", 1)), Order.class);

			assertEquals(new Order(7, List.of(new Item("a", 2, new BigDecimal("1.50"))), Optional.of("hi"), LocalDate.of(2026, 10, 24)), order);

		}

		@Test
		@DisplayName("Map → Bean は setter で入れる")
		void mapToBean () {

			Customer customer = Converter.convert(map("name", " x ", "age", "30"), Customer.class);

			assertEquals("x", customer.getName());
			assertEquals("yes", customer.setterCalled);
			assertEquals(30, customer.getAge());

		}

		@Test
		@DisplayName("static / transient の項目には入れない（キーは外から来る。D-226）")
		void skipsTransient () {

			Holder holder = Converter.convert(map("cache", "pwned", "name", "a"), Holder.class);

			assertEquals("kept", holder.cache);
			assertEquals("a", holder.name);

		}

		@Test
		@DisplayName("rejectUnknownKeys なら、知らないキーで断る")
		void rejectUnknownKeys () {

			ConverterOptions strict = ConverterOptions.builder().rejectUnknownKeys(true).build();

			ConversionException e = assertThrows(ConversionException.class, () -> Converter.convert(map("name", "a", "nope", 1), Item.class, strict));
			assertTrue(e.getMessage().contains("nope"), e.getMessage());

		}

		@Test
		@DisplayName("Bean / record → Map は、項目を宣言した順に入れる")
		void beanToMap () {

			Map<?, ?> map = Converter.convert(new Item("a", 2, BigDecimal.ONE), Map.class);

			assertEquals(List.of("name", "qty", "price"), new ArrayList<>(map.keySet()));
			assertEquals(2, map.get("qty"));

		}

		@Test
		@DisplayName("Bean → record（項目の名前で写す）")
		void beanToRecord () {

			Customer customer = new Customer();
			customer.setName("c");
			customer.setAge(3);

			record Person (String name, int age) {
			}

			assertEquals(new Person("c", 3), Converter.convert(customer, Person.class));

		}

		@Test
		@DisplayName("型が合っていれば、写さずにそのまま返す")
		void sameType () {

			Item item = new Item("a", 1, null);
			List<String> list = List.of("x");

			assertSame(item, Converter.convert(item, Item.class));
			assertSame(list, Converter.convert(list, List.class));

		}

		@Test
		@DisplayName("List<Map> → List<record>、配列、Set")
		void containers () {

			List<Item> items = Converter.convert(List.of(map("name", "a"), map("name", "b")), new TypeRef<List<Item>>() {});
			assertEquals(List.of("a", "b"), items.stream().map(Item::name).toList());

			assertArrayEquals(new int[] {1, 2}, Converter.convert(List.of("1", 2), int[].class));
			assertEquals(Set.of(1, 2), Converter.convert(new String[] {"1", "2", "1"}, new TypeRef<Set<Integer>>() {}));

		}

		@Test
		@DisplayName("例外に場所（$.items[1].qty）が載る")
		void path () {

			ConversionException e = assertThrows(ConversionException.class, () -> Converter.convert(map(
				"items", List.of(map("qty", 1), map("qty", "1.5"))), Order.class));

			assertEquals("$.items[1].qty", e.path());

		}

		@Test
		@DisplayName("循環参照は例外。cycleToNull なら null にする（落ちない）")
		void cycles () {

			record Node (String name, Node next) {
			}

			Map<String, Object> self = map("name", "a");
			self.put("next", self);

			ConversionException e = assertThrows(ConversionException.class, () -> Converter.convert(self, Node.class));
			assertEquals("$.next", e.path());

			ConverterOptions toNull = ConverterOptions.builder().cycleToNull(true).build();
			assertEquals(new Node("a", null), Converter.convert(self, Node.class, toNull));

			Map<String, Object> converted = Converter.convert(self, new TypeRef<Map<String, Object>>() {}, toNull);
			assertSame(self, converted, "型の決まっていない Map はそのまま");

		}

	}

}
