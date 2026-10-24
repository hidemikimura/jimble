package io.jimble.util.convertor;

import io.jimble.util.convertor.ConvertorFixtures.Checked;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.jimble.util.convertor.ConvertorFixtures.data;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data の変換系の getter の今の挙動の記録（作り直しで変える予定のもの）
 *
 * <p>
 * <b>ここに書いた挙動は守る約束ではない。</b>jimble-converter への作り直しで変える予定で、
 * 変えたときにこのテストが落ちることで「変わった」と分かるようにしてある。
 * 守る約束は {@link DataConversionContractTest} にある。
 * </p>
 */
class DataConversionCurrentQuirksTest {

	@Nested
	@DisplayName("convert / convertClass")
	class Convert {

		@Test
		@DisplayName("現状: convertClass は変換の例外を null にする")
		void 現状_convertClassが例外をnullにする () {

			// Convertor.convert は例外を投げる（record の組み立てに失敗）が、convertClass は握りつぶして null を返す。
			// 作り直しで例外を投げる（D-195 の getValue と揃える）予定
			Checked res = data("name", "bad").convertClass(Checked.class);
			assertNull(res);
			assertThrows(RuntimeException.class, () -> ConvertorFixtures.conv(data("name", "bad"), Checked.class));

		}

		@Test
		@DisplayName("現状: convert も変換の例外を null にする")
		void 現状_convertが例外をnullにする () {

			// 作り直しで例外を投げる予定
			Checked res = data("name", "bad").convert();
			assertNull(res);

		}

		@Test
		@DisplayName("現状: convertClass で record の無い String の部品は空文字（record の検査をすり抜ける）")
		void 現状_convertClassでrecordの無いStringが空文字 () {

			// 作り直しで null にする予定（record の検査が効くようにする）
			Checked res = new Data().convertClass(Checked.class);
			assertEquals("", res.name());

		}

	}

	@Nested
	@DisplayName("一覧の getter")
	class Lists {

		@Test
		@DisplayName("現状: getIntList は 3e9 を桁あふれさせる")
		void 現状_getIntListが桁あふれする () {

			// getInt は例外にする（D-195）。作り直しで getIntList も例外にする予定
			List<Integer> res = data("l", List.of(3_000_000_000L)).getIntList("l");
			assertEquals(List.of(-1_294_967_296), res);

		}

		@Test
		@DisplayName("現状: getIntList は読めない要素を null、小数を切り捨てにする")
		void 現状_getIntListが読めない要素をnullにする () {

			// 作り直しで例外にする予定
			List<Integer> res = data("l", List.of("x", 1.9d, "7")).getIntList("l");
			assertEquals(Arrays.asList(null, 1, 7), res);

		}

		@Test
		@DisplayName("現状: getDoubleList は読めない要素を null にする")
		void 現状_getDoubleListが読めない要素をnullにする () {

			// 作り直しで例外にする予定
			List<Double> res = data("l", List.of("x", "1.5")).getDoubleList("l");
			assertEquals(Arrays.asList(null, 1.5d), res);

		}

		@Test
		@DisplayName("現状: getIntList は1つの値を1要素の一覧に包む")
		void 現状_getIntListが1つの値を包む () {

			// getDataList は一覧でない値を例外にする。作り直しで揃えるかを決める予定
			assertEquals(List.of(5), data("l", 5).getIntList("l"));

		}

		@Test
		@DisplayName("現状: getStringList は JSON でない文字を1要素の一覧に包む")
		void 現状_getStringListが文字を包む () {

			// 作り直しで揃えるかを決める予定
			assertEquals(List.of("abc"), data("l", "abc").getStringList("l"));

		}

		@Test
		@DisplayName("現状: getDataList は文字の一覧を空の Data の一覧にする")
		void 現状_getDataListが文字を空のDataにする () {

			// 文字の要素を Data に変換すると空の Data になる。作り直しで例外にする予定
			List<Data> res = data("l", List.of("a", "b")).getDataList("l");
			assertEquals(2, res.size());
			assertTrue(res.get(0).isEmpty());
			assertTrue(res.get(1).isEmpty());

		}

	}

	@Nested
	@DisplayName("getByteArray")
	class ByteArray {

		@Test
		@DisplayName("現状: getByteArray は文字を [0] にする")
		void 現状_getByteArrayが文字を0にする () {

			// 作り直しで UTF-8 の byte 列にするか例外にする予定
			assertArrayEquals(new byte[] {0}, data("b", "abc").getByteArray("b"));

		}

		@Test
		@DisplayName("現状: getByteArray は 300 を桁あふれさせて 44 にする")
		void 現状_getByteArrayが桁あふれする () {

			// 作り直しで例外にする予定
			assertArrayEquals(new byte[] {1, 44}, data("b", List.of(1, 300)).getByteArray("b"));

		}

	}

	@Nested
	@DisplayName("getString")
	class GetString {

		@Test
		@DisplayName("現状: getString は一覧の先頭の要素だけを返す")
		void 現状_getStringが一覧の先頭だけ () {

			// JsonArrayList のときだけ警告を出す（D-189）。作り直しで JSON の文字にするか例外にするかを決める予定
			assertEquals("a", data("l", List.of("a", "b")).getString("l"));

		}

		@Test
		@DisplayName("現状: getString は Map の先頭の値だけを返す")
		void 現状_getStringがMapの先頭の値だけ () {

			// 入れ子の Data なら JSON の文字になるのに、ただの Map だと先頭の値になる。作り直しで揃える予定
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("a", "first");
			m.put("b", "second");
			assertEquals("first", data("m", m).getString("m"));

		}

		@Test
		@DisplayName("現状: getString は空の一覧を null にする")
		void 現状_getStringが空の一覧をnull () {

			// 作り直しで揃える予定
			assertNull(data("l", List.of()).getString("l"));

		}

	}

	@Nested
	@DisplayName("getDate（時間帯は " + ConvertorFixtures.ZONE + " に固定）")
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class GetDate {

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
		@DisplayName("現状: getDate は \"2024-13-45\" を繰り上げて通す")
		void 現状_getDateがありえない日付を通す () {

			// D-161 で「意図して残した」もの。作り直しで例外にする予定
			assertEquals(1_739_458_800_000L, data("d", "2024-13-45").getDate("d").getTime());

		}

		@Test
		@DisplayName("現状: getDate は \"2024-01-15abc\" を通す")
		void 現状_getDateが後ろのゴミを通す () {

			// 作り直しで例外にする予定
			assertEquals(JAN_15, data("d", "2024-01-15abc").getDate("d").getTime());

		}

		@Test
		@DisplayName("現状: getDate は \"...Z\" の時差を無視する")
		void 現状_getDateがZの時差を無視する () {

			// 正しくは UTC の 10 時（JST の 19 時）。作り直しで UTC として読む予定
			assertEquals(JAN_15 + 10 * HOUR, data("d", "2024-01-15T10:00:00.000Z").getDate("d").getTime());

		}

	}

}
