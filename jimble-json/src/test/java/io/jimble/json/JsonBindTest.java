package io.jimble.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON を型に読む（D-307）
 */
class JsonBindTest {

	/** record */
	record Item (String name, int qty, BigDecimal price) {
	}

	/** record（入れ子・コレクション・Optional） */
	record Order (long id, List<Item> items, Map<String, Integer> counts, Optional<String> note, Set<String> tags, LocalDate date) {
	}

	/** Bean */
	public static class Customer {

		private String name;
		private int age;
		private boolean active;
		private Status status;
		private List<Item> history;
		private String setterCalled = "no";

		/** @param name	名前（setter があればこちらで入る） */
		public void setName (String name) {
			this.name = name == null ? null : name.strip();
			this.setterCalled = "yes";
		}

		String name () {
			return name;
		}

	}

	/** 親クラスの private フィールド */
	public static class Base {

		private String baseValue;

		String baseValue () {
			return baseValue;
		}

	}

	/** 子クラス */
	public static class Derived extends Base {

		private int own;

	}

	/** 型変数を持つ Bean */
	public static class Box<T> {

		private T value;
		private List<T> values;

	}

	/** enum */
	enum Status { ACTIVE, STOPPED }

	@Nested
	@DisplayName("record と Bean")
	class Objects {

		@Test
		@DisplayName("record は正準のコンストラクタで作る（入れ子・List・Map・Optional・Set・日付も）")
		void record () {

			Order order = Json.read("""
				{"id": 7, "items": [{"name":"a","qty":2,"price":1.50}], "counts": {"x": 1},
				 "note": "hi", "tags": ["b","a","b"], "date": "2026/10/24"}
				""", Order.class);

			assertEquals(7, order.id());
			assertEquals(List.of(new Item("a", 2, new BigDecimal("1.50"))), order.items());
			assertEquals(Map.of("x", 1), order.counts());
			assertEquals(Optional.of("hi"), order.note());
			assertEquals(List.of("b", "a"), List.copyOf(order.tags()));
			assertEquals(LocalDate.of(2026, 10, 24), order.date());

		}

		@Test
		@DisplayName("JSON に無いコンポーネントは null / 0 / Optional.empty()")
		void recordDefaults () {

			Order order = Json.read("{}", Order.class);

			assertEquals(0, order.id());
			assertNull(order.items());
			assertNull(order.date());

			assertEquals(Optional.empty(), Json.read("{\"note\":null}", Order.class).note());

		}

		@Test
		@DisplayName("Bean は setter（無ければフィールド）に入れる。知らないキーは飛ばす")
		void bean () {

			Customer customer = Json.read("{\"name\":\"  jimble \",\"age\":\"30\",\"active\":\"yes\",\"status\":\"active\","
				+ "\"history\":[{\"name\":\"x\",\"qty\":1}],\"unknown\":{\"deep\":[1,2]}}", Customer.class);

			assertEquals("jimble", customer.name());
			assertEquals("yes", customer.setterCalled);
			assertEquals(30, customer.age);
			assertTrue(customer.active);
			assertEquals(Status.ACTIVE, customer.status);
			assertEquals("x", customer.history.getFirst().name());

		}

		@Test
		@DisplayName("親クラスの private フィールドにも入れる")
		void superclassField () {

			Derived derived = Json.read("{\"baseValue\":\"b\",\"own\":3}", Derived.class);

			assertEquals("b", derived.baseValue());
			assertEquals(3, derived.own);

		}

		@Test
		@DisplayName("snakeCaseKeys なら user_name を userName にも当てる")
		void snakeCase () {

			record User (String userName, int loginCount) {
			}

			JsonReadOptions options = JsonReadOptions.builder().snakeCaseKeys(true).build();

			assertEquals(new User("a", 2), Json.read("{\"user_name\":\"a\",\"login-count\":2}", User.class, options));
			assertEquals(new User(null, 0), Json.read("{\"user_name\":\"a\"}", User.class));

		}

		@Test
		@DisplayName("型変数は TypeRef から解く")
		void generics () {

			Box<Item> box = Json.read("{\"value\":{\"name\":\"a\",\"qty\":1},\"values\":[{\"name\":\"b\"}]}", new TypeRef<Box<Item>>() {});

			assertEquals("a", box.value.name());
			assertEquals("b", box.values.getFirst().name());

		}

	}

	@Nested
	@DisplayName("入れ物とスカラー")
	class Containers {

		@Test
		@DisplayName("List<record> / 配列 / Map<String, record>")
		void containers () {

			List<Item> list = Json.read("[{\"name\":\"a\"},{\"name\":\"b\"}]", new TypeRef<List<Item>>() {});
			assertEquals(List.of("a", "b"), list.stream().map(Item::name).toList());

			assertArrayEquals(new int[] {1, 2, 3}, Json.read("[1,\"2\",3.0]", int[].class));

			Map<String, Item> map = Json.read("{\"k\":{\"name\":\"a\"}}", new TypeRef<Map<String, Item>>() {});
			assertEquals("a", map.get("k").name());

		}

		@Test
		@DisplayName("Object / 型の無い Map は木で読む")
		void untyped () {

			Map<?, ?> map = Json.read("{\"a\":[1,{\"b\":true}]}", Map.class);

			assertEquals(List.of(1, Map.of("b", true)), map.get("a"));

		}

		@Test
		@DisplayName("スカラーの一番外も読める。空なら null")
		void scalars () {

			assertEquals(42, Json.read("\"42\"", int.class));
			assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), Json.read("\"00000000-0000-0000-0000-000000000001\"", UUID.class));
			assertNull(Json.read("", Item.class));

		}

	}

	@Nested
	@DisplayName("値の変換（受け付ける）")
	class Accepts {

		record Numbers (int i, long l, Integer boxed, double d, BigDecimal b, boolean flag) {
		}

		@Test
		@DisplayName("全角の数字・3 桁ごとのカンマ・前後の空白・小数点以下が 0 の小数・空文字")
		void looseNumbers () {

			assertEquals(new Numbers(12, 1234, null, 1.5, new BigDecimal("1234.5"), false),
				Json.read("{\"i\":\"１２\",\"l\":\"1,234\",\"boxed\":\"\",\"d\":\" 1.5 \",\"b\":\"1,234.5\",\"flag\":\"\"}", Numbers.class));
			assertEquals(1, Json.read("{\"i\":1.0}", Numbers.class).i());

		}

		@ParameterizedTest
		@ValueSource(strings = {"true", "\"true\"", "\"TRUE\"", "1", "\"1\"", "\"yes\"", "\"on\""})
		@DisplayName("真偽の書き方")
		void booleans (String json) {

			assertTrue(Json.read("{\"flag\":" + json + "}", Numbers.class).flag());

		}

		@Test
		@DisplayName("日時：yyyy/MM/dd HH:mm:ss・ISO・時差つき・epoch ミリ秒")
		void dateTimes () {

			record Times (LocalDateTime local, OffsetDateTime offset, Instant instant, java.util.Date date) {
			}

			Times times = Json.read("{\"local\":\"2026/10/24 10:00:00\",\"offset\":\"2026-10-24T10:00:00+09:00\","
				+ "\"instant\":\"2026-10-24T01:00:00Z\",\"date\":0}", Times.class);

			assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), times.local());
			assertEquals(OffsetDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9)), times.offset());
			assertEquals(Instant.parse("2026-10-24T01:00:00Z"), times.instant());
			assertEquals(0L, times.date().getTime());

		}

	}

	@Nested
	@DisplayName("値の変換（断る。場所と位置つき）")
	class Rejects {

		record Numbers (int i, Status status, LocalDate date, boolean flag) {
		}

		@ParameterizedTest
		@ValueSource(strings = {
			"{\"i\":1.5}", "{\"i\":\"1.5\"}", "{\"i\":3000000000}", "{\"i\":\"abc\"}", "{\"i\":\"1,23\"}", "{\"i\":true}", "{\"i\":[1]}",
			"{\"status\":\"UNKNOWN\"}", "{\"date\":\"2024-02-30\"}", "{\"date\":\"2024-01-15abc\"}", "{\"flag\":\"maybe\"}", "{\"flag\":2}"
		})
		@DisplayName("値が変わる・読めない・型が違う")
		void rejects (String json) {

			assertThrows(JsonException.class, () -> Json.read(json, Numbers.class));

		}

		@Test
		@DisplayName("例外に場所（$.items[1].qty）と行・文字の位置が載る")
		void path () {

			JsonException e = assertThrows(JsonException.class,
				() -> Json.read("{\"id\":1,\n\"items\":[{\"qty\":1},{\"qty\":2.5}]}", Order.class));

			assertTrue(e.getMessage().contains("$.items[1].qty"), e.getMessage());
			assertEquals(2, e.line());

		}

		@Test
		@DisplayName("record のコンストラクタが断れば、場所つきの例外")
		void recordValidation () {

			record Positive (int value) {
				Positive {
					if (value <= 0) {
						throw new IllegalArgumentException("正の数にしてください");
					}
				}
			}

			JsonException e = assertThrows(JsonException.class, () -> Json.read("{\"value\":-1}", Positive.class));

			assertTrue(e.getMessage().contains("正の数にしてください"), e.getMessage());
			assertInstanceOf(Positive.class, Json.read("{\"value\":1}", Positive.class));

		}

	}

}
