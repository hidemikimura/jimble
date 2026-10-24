package io.jimble.util.json;

import io.jimble.util.data.Data;
import io.jimble.util.internal.json.stream.writer.JsonHashWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守る約束：書き出しの型と深さ（D-307。jimble-json に替えてから）
 *
 * <p>
 * 作り直す前は、ここにある型を書くと JSON が丸ごと消えるか途中で切れていた
 * （{@code Dson.encodes} は空文字、{@code getJsonString} は {@code {}}、{@code outputJsonString} は途中まで）。
 * 深さ 50 を超える入れ子は、黙って {@code {}} / {@code []} にしていた。
 * </p>
 */
class DsonWriteTypesContractTest {

	private TimeZone saved;

	@BeforeEach
	void fixZone () {

		saved = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

	}

	@AfterEach
	void restoreZone () {

		TimeZone.setDefault(saved);

	}

	/**
	 * 3つの入口（encodes / getJsonString / outputJsonString）が、どれも同じ JSON を書くこと
	 */
	private static void assertWritten (Object value, String expected) {

		Data data = new Data().putData("x", 1).putData("d", value);
		String json = "{\"x\":1,\"d\":" + expected + "}";

		assertEquals(json, Dson.encodes(data));
		assertEquals(json, data.getJsonString());

		StringWriter writer = new StringWriter();
		data.outputJsonString(writer);
		assertEquals(json, writer.toString());

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		data.outputJsonString(stream);
		assertEquals(json, stream.toString(StandardCharsets.UTF_8));

	}

	@Nested
	@DisplayName("日時（サーバーの時間帯にそろえる。/ はエスケープしない）")
	class DateTimes {

		@Test
		@DisplayName("LocalDate と java.sql.Date は yyyy/MM/dd")
		void dates () {

			assertWritten(LocalDate.of(2026, 1, 2), "\"2026/01/02\"");
			assertWritten(new java.sql.Date(0), "\"1970/01/01\"");

		}

		@Test
		@DisplayName("LocalTime と java.sql.Time は HH:mm:ss")
		void times () {

			assertWritten(LocalTime.of(9, 8, 7), "\"09:08:07\"");
			assertWritten(new java.sql.Time(0), "\"09:00:00\"");

		}

		@Test
		@DisplayName("Instant / OffsetDateTime / ZonedDateTime / Calendar は、サーバーの時間帯の yyyy/MM/dd HH:mm:ss")
		void instants () {

			assertWritten(Instant.EPOCH, "\"1970/01/01 09:00:00\"");
			assertWritten(OffsetDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneOffset.UTC), "\"2026/10/24 19:00:00\"");
			assertWritten(ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneOffset.UTC), "\"2026/10/24 19:00:00\"");

			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(0);
			assertWritten(calendar, "\"1970/01/01 09:00:00\"");

		}

		@Test
		@DisplayName("Duration と Period は ISO 8601")
		void durations () {

			assertWritten(Duration.ofMinutes(90), "\"PT1H30M\"");
			assertWritten(Period.of(1, 2, 0), "\"P1Y2M\"");

		}

	}

	@Nested
	@DisplayName("そのほかの型")
	class Others {

		@Test
		@DisplayName("UUID は文字列")
		void uuid () {

			assertWritten(UUID.fromString("00000000-0000-0000-0000-000000000001"), "\"00000000-0000-0000-0000-000000000001\"");

		}

		@Test
		@DisplayName("Optional は中身。空なら null")
		void optional () {

			assertWritten(Optional.of("x"), "\"x\"");
			assertWritten(Optional.empty(), "null");

		}

		@Test
		@DisplayName("AtomicInteger のような Number は数")
		void atomicNumber () {

			assertWritten(new AtomicInteger(3), "3");

		}

		@Test
		@DisplayName("フィールドの無い new Object() は {}")
		void plainObject () {

			assertWritten(new Object(), "{}");

		}

		@Test
		@DisplayName("JsonHashWriter に LocalDate を渡しても壊れない")
		void streamWriter () throws Exception {

			StringWriter out = new StringWriter();
			try (java.io.BufferedWriter buffered = new java.io.BufferedWriter(out);
				JsonHashWriter hash = new JsonHashWriter(buffered)) {
				hash.write("d", LocalDate.of(2026, 1, 2));
				hash.write("z", 1);
			}

			assertEquals("{\"d\":\"2026/01/02\",\"z\":1}", out.toString());

		}

	}

	@Nested
	@DisplayName("Bean のキーの順番")
	class BeanOrder {

		@Test
		@DisplayName("宣言した順（親クラスから）。JVM を起動し直しても変わらない")
		void declarationOrder () {

			assertEquals("{\"id\":1,\"zeta\":\"z\",\"alpha\":\"a\",\"middle\":2}", Dson.encodes(new Child()));

		}

	}

	@Nested
	@DisplayName("入れ子の深さ")
	class Depth {

		private Object nested (int depth) {

			Object value = 1;
			for (int i = 0; i < depth; i++) {
				List<Object> list = new ArrayList<>();
				list.add(value);
				value = list;
			}
			return value;

		}

		@Test
		@DisplayName("50 段を超えても、黙って切らずに最後まで書く（作り直す前は 51 段目から [] になっていた）")
		void deepIsWritten () {

			String json = Dson.encodes(nested(60));

			assertEquals("[".repeat(60) + "1" + "]".repeat(60), json);

		}

		@Test
		@DisplayName("60 段の JSON を読んで書き戻すと、同じになる")
		void roundTrip () {

			String json = "[".repeat(60) + "1" + "]".repeat(60);

			assertEquals(json, Dson.encodes(Dson.decodes(json, List.class)));

		}

		@Test
		@DisplayName("512 段を超えると書かない（Dson.encodes は空文字で、エラーをログに出す）")
		void tooDeep () {

			assertEquals("", Dson.encodes(nested(600)));

			Dson dson = new Dson();
			assertEquals("", dson.encode(nested(600)));
			assertTrue(dson.isError());

		}

	}

	/** 親クラス */
	public static class Parent {

		/** 親の項目 */
		public int id = 1;

	}

	/** 子クラス（宣言した順が、名前の順と違う） */
	public static class Child extends Parent {

		/** 項目 */
		public String zeta = "z";

		/** 項目 */
		public String alpha = "a";

		/** 項目 */
		public int middle = 2;

	}

}
