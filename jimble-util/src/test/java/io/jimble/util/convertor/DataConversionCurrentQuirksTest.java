package io.jimble.util.convertor;

import io.jimble.util.convertor.ConvertorFixtures.Checked;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.jimble.util.convertor.ConvertorFixtures.data;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

	}

	@Nested
	@DisplayName("一覧の getter")
	class Lists {

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

}
