package io.jimble.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON を書く（D-307）
 */
class JsonWriteTest {

	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

	private static final JsonWriteOptions TOKYO_OPTIONS = JsonWriteOptions.builder().zone(TOKYO).build();

	private static Map<String, Object> map (Object... keyValues) {

		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((String) keyValues[i], keyValues[i + 1]);
		}
		return map;

	}

	@Nested
	@DisplayName("形と値")
	class Values {

		@Test
		@DisplayName("Map は入れた順、List・配列・Set はその順で書く")
		void containers () {

			Map<String, Object> value = map("b", 1, "a", List.of(1, 2), "n", new int[] {3, 4}, "s", new TreeSet<>(Set.of("y", "x")), "e", map());

			assertEquals("{\"b\":1,\"a\":[1,2],\"n\":[3,4],\"s\":[\"x\",\"y\"],\"e\":{}}", Json.write(value));

		}

		@Test
		@DisplayName("数・真偽・null")
		void scalars () {

			assertEquals("[1,-9223372036854775808,9223372036854775807,1.5,0.1,1.0E20,1.50,true,false,null]",
				Json.write(new Object[] {1, Long.MIN_VALUE, Long.MAX_VALUE, 1.5, 0.1f, 1e20, new BigDecimal("1.50"), true, false, null}));

		}

		@Test
		@DisplayName("一番外がスカラーや null でも書く")
		void scalarRoot () {

			assertEquals("\"x\"", Json.write("x"));
			assertEquals("1", Json.write(1));
			assertEquals("null", Json.write(null));

		}

		@Test
		@DisplayName("Map の null のキーは書かない。文字でないキーは文字にする")
		void mapKeys () {

			Map<Object, Object> value = new LinkedHashMap<>();
			value.put(null, 1);
			value.put(2, "two");

			assertEquals("{\"2\":\"two\"}", Json.write(value));

		}

		@Test
		@DisplayName("writeNulls(false) なら null の項目を書かない（配列の中の null は書く）")
		void skipNulls () {

			JsonWriteOptions options = JsonWriteOptions.builder().writeNulls(false).build();

			assertEquals("{\"a\":1,\"l\":[null]}", Json.write(map("a", 1, "b", null, "l", java.util.Arrays.asList((Object) null)), options));

		}

		@Test
		@DisplayName("enum は名前、Optional は中身、例外は toString")
		void others () {

			assertEquals("[\"SECONDS\",\"x\",null,\"java.lang.IllegalStateException: boom\"]",
				Json.write(java.util.Arrays.asList(java.util.concurrent.TimeUnit.SECONDS, Optional.of("x"), Optional.empty(), new IllegalStateException("boom"))));

		}

	}

	@Nested
	@DisplayName("Bean と record")
	class Beans {

		@Test
		@DisplayName("record はコンポーネントを宣言した順に書く")
		void record () {

			assertEquals("{\"zeta\":1,\"alpha\":\"a\",\"inner\":{\"zeta\":2,\"alpha\":\"b\",\"inner\":null}}",
				Json.write(new Rec(1, "a", new Rec(2, "b", null))));

		}

		@Test
		@DisplayName("Bean は親クラスから、宣言した順に書く。getter と setter の組は getter で読む")
		void bean () {

			assertEquals("{\"id\":7,\"name\":\"NAME\",\"count\":3}", Json.write(new Child()));

		}

	}

	@Nested
	@DisplayName("文字列のエスケープ")
	class Escapes {

		@Test
		@DisplayName("既定：\" \\ / と制御文字、U+2028 / U+2029。日本語と絵文字はそのまま")
		void defaults () {

			assertEquals("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0001\\u007F\\u2028あ😀<>\"",
				Json.write("\"\\/\b\f\n\r\t\u0001\u007F あ😀<>"));

		}

		@Test
		@DisplayName("escapeSlash(false) / escapeHtml(true) / dropSupplementary(true)")
		void options () {

			JsonWriteOptions options = JsonWriteOptions.builder().escapeSlash(false).escapeHtml(true).dropSupplementary(true).build();

			assertEquals("\"a/b\\u003C\\u003Ec\"", Json.write("a/b<>😀c", options));

		}

	}

	@Nested
	@DisplayName("日時")
	class DateTimes {

		@Test
		@DisplayName("既定はサーバーの時間帯（ここでは指定）の yyyy/MM/dd HH:mm:ss。/ はエスケープしない")
		void defaults () {

			assertEquals("[\"1970/01/01 09:00:00\",\"1970/01/01 09:00:00\",\"2026/10/24 19:00:00\",\"2026/01/02 03:04:05\"]",
				Json.write(List.of(new Date(0), Instant.EPOCH, OffsetDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneOffset.UTC),
					LocalDateTime.of(2026, 1, 2, 3, 4, 5, 999)), TOKYO_OPTIONS));

		}

		@Test
		@DisplayName("keepOffset(true) なら時差つきの ISO 8601（時差を持たない LocalDateTime は変わらない）")
		void keepOffset () {

			JsonWriteOptions options = TOKYO_OPTIONS.toBuilder().keepOffset(true).build();

			assertEquals("[\"1970-01-01T09:00:00+09:00\",\"2026-10-24T10:00:00Z\",\"2026/01/02 03:04:05\"]",
				Json.write(List.of(new Date(0), OffsetDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneOffset.UTC),
					LocalDateTime.of(2026, 1, 2, 3, 4, 5)), options));

		}

		@Test
		@DisplayName("datePrefix を前に付けられる")
		void prefix () {

			JsonWriteOptions options = TOKYO_OPTIONS.toBuilder().datePrefix("date::").build();

			assertEquals("\"date::1970/01/01 09:00:00\"", Json.write(new Date(0), options));

		}

	}

	@Nested
	@DisplayName("循環と深さ")
	class Cycles {

		private Map<String, Object> cyclic () {

			Map<String, Object> root = new LinkedHashMap<>();
			List<Object> children = new ArrayList<>();
			root.put("children", children);
			children.add(root);
			return root;

		}

		@Test
		@DisplayName("既定は例外で、どこで循環したかが載る")
		void throwsByDefault () {

			JsonException e = assertThrows(JsonException.class, () -> Json.write(cyclic()));

			assertTrue(e.getMessage().contains("$.children[0]"), e.getMessage());

		}

		@Test
		@DisplayName("EMPTY なら {} / []、NULL なら null を書いて続ける")
		void emptyAndNull () {

			assertEquals("{\"children\":[{}]}", Json.write(cyclic(), JsonWriteOptions.builder().cycle(JsonCycle.EMPTY).build()));
			assertEquals("{\"children\":[null]}", Json.write(cyclic(), JsonWriteOptions.builder().cycle(JsonCycle.NULL).build()));

		}

		@Test
		@DisplayName("同じものが別の枝に2回出てくるのは循環ではない（両方に書く）")
		void sharedIsNotCycle () {

			Map<String, Object> shared = map("v", 1);

			assertEquals("{\"a\":{\"v\":1},\"b\":{\"v\":1}}", Json.write(map("a", shared, "b", shared)));

		}

		@Test
		@DisplayName("深すぎれば、循環の設定によらず例外")
		void tooDeep () {

			Object value = 1;
			for (int i = 0; i < 600; i++) {
				value = List.of(value);
			}
			Object deep = value;

			assertThrows(JsonException.class, () -> Json.write(deep, JsonWriteOptions.builder().cycle(JsonCycle.EMPTY).build()));

		}

	}

	@Nested
	@DisplayName("字下げ・書き先・足した書き方")
	class Misc {

		@Test
		@DisplayName("字下げはタブで、: の後ろに空白を入れない")
		void indent () {

			JsonWriteOptions options = JsonWriteOptions.builder().indent(true).build();

			assertEquals("{\n\t\"a\":[\n\t\t1,\n\t\t2\n\t],\n\t\"b\":{}\n}", Json.write(map("a", List.of(1, 2), "b", map()), options));

		}

		@Test
		@DisplayName("Writer と OutputStream に書ける（溜め置きより大きくても）")
		void sinks () {

			Map<String, Object> value = map("s", "あ".repeat(20_000));

			StringWriter writer = new StringWriter();
			Json.write(value, writer, JsonWriteOptions.DEFAULT);

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			Json.write(value, stream, StandardCharsets.UTF_8, JsonWriteOptions.DEFAULT);

			assertEquals(Json.write(value), writer.toString());
			assertEquals(Json.write(value), stream.toString(StandardCharsets.UTF_8));

		}

		@Test
		@DisplayName("足した書き方は、決まった型より先に効く。plain で決まった書き方に戻せる")
		void typeWriters () {

			JsonWriteOptions options = JsonWriteOptions.builder()
				.typeWriter(Rec.class, (value, out) -> out.string("rec:" + ((Rec) value).alpha()))
				.typeWriter(HashMap.class, (value, out) -> out.plain(value))
				.build();

			Map<String, Object> plain = new HashMap<>(Map.of("k", 1));

			assertEquals("[\"rec:a\",{\"k\":1}]", Json.write(List.of(new Rec(1, "a", null), plain), options));

		}

		@Test
		@DisplayName("書いたものを読むと元に戻る")
		void roundTrip () {

			Map<String, Object> value = map("s", "a\"b\\c/d\n あ😀", "n", List.of(1, 2L + Integer.MAX_VALUE, 1.5, true), "m", map("x", null));

			assertEquals(value, Json.read(Json.write(value)));

		}

	}

	/** record */
	record Rec (int zeta, String alpha, Rec inner) {
	}

	/** 親 */
	public static class Parent {

		/** 親の項目 */
		public int id = 7;

	}

	/** 子（getter と setter の組がある項目は getter で読む） */
	public static class Child extends Parent {

		private String name = "name";

		/** 項目 */
		public int count = 3;

		/** @return	大文字にした名前 */
		public String getName () {
			return name.toUpperCase(java.util.Locale.ROOT);
		}

		/** @param name	名前 */
		public void setName (String name) {
			this.name = name;
		}

	}

}
