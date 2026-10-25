package io.jimble.util.date;

import io.jimble.converter.ConversionException;
import io.jimble.util.csv.CsvReader;
import io.jimble.util.data.Data;
import io.jimble.util.data.DataConversionException;
import io.jimble.util.internal.convertor.Conversions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守る約束：ゼロ埋めしていない日付も読む（2.5.12 で読めて、2.7.0 で読めなくなっていたもの）
 *
 * <p>
 * 内閣府の祝日 CSV（{@code 1955/1/1}）・日本語の Excel で保存した CSV（{@code 2024/1/5}）・画面や API から来る値。
 * ありえない日付と後ろの余分は、2.7.0 のまま断る。
 * </p>
 */
class UnpaddedDateReadTest {

	private static final String READABLE = """
		2024/01/05|2024-01-05T00:00
		2024/01/05 09:00:00|2024-01-05T09:00
		2024-01-05 09:00|2024-01-05T09:00
		20240105|2024-01-05T00:00
		2024年01月15日|2024-01-15T00:00
		2024/1/5|2024-01-05T00:00
		2024/1/05|2024-01-05T00:00
		2024/01/5|2024-01-05T00:00
		2024-1-5|2024-01-05T00:00
		2024/1/5 9:00|2024-01-05T09:00
		2024/1/5 9:00:00|2024-01-05T09:00
		2024年1月5日|2024-01-05T00:00
		1955/1/1|1955-01-01T00:00
		2027/11/3|2027-11-03T00:00
		""";

	private static Date date (LocalDateTime local) {
		return Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = READABLE)
	@DisplayName("Conversions.convert で Date にできる")
	void conversions (String text, LocalDateTime expected) {

		assertEquals(date(expected), Conversions.convert(text, Date.class));

	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = READABLE)
	@DisplayName("DateUtil.parseDate で読める")
	void parseDate (String text, LocalDateTime expected) {

		assertEquals(date(expected), DateUtil.parseDate(text));

	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = READABLE)
	@DisplayName("Data#getDate で読める")
	void dataGetDate (String text, LocalDateTime expected) {

		Data data = new Data();
		data.put("d", text);
		assertEquals(date(expected), data.getDate("d"));

	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = READABLE)
	@DisplayName("CsvReader#getDate で読める")
	void csvGetDate (String text, LocalDateTime expected) throws Exception {

		try (CsvReader csv = new CsvReader(new StringReader("国民の祝日・休日月日,国民の祝日・休日名称\n" + text + ",元日\n"))) {

			assertTrue(csv.next());
			assertEquals(date(expected), csv.getDate("国民の祝日・休日月日"));

		}

	}

	@ParameterizedTest
	@ValueSource(strings = {"2024-02-30", "2024-01-15abc", "2024/2/30", "2024/1/5abc", "2024/1/32", "2024/1/5 25:00"})
	@DisplayName("ありえない日付・後ろの余分は、これまでどおり断る（Conversions・Data は例外、DateUtil・CsvReader は null）")
	void rejectsBadDates (String text) throws Exception {

		assertThrows(ConversionException.class, () -> Conversions.convert(text, Date.class));
		assertNull(DateUtil.parseDate(text));

		Data data = new Data();
		data.put("d", text);
		assertThrows(DataConversionException.class, () -> data.getDate("d"));

		try (CsvReader csv = new CsvReader(new StringReader("d\n" + text + "\n"))) {
			assertTrue(csv.next());
			assertNull(csv.getDate("d"));
		}

	}

}
