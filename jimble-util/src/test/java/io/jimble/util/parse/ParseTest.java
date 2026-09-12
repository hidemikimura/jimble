package io.jimble.util.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 例外を出さない数値変換（要件 F-U-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>読めなかったときに 0 を返す。</b>だから
 * <b>「本当に 0 だった」と「読めなかった」の区別が付かない</b>——
 * 金額や件数をここで受けてはいけない、という<b>使い方の境目</b>を
 * 見えるようにしておくのがここの仕事である。
 * </p>
 */
class ParseTest {

	@Test
	@DisplayName("読めるものは読む")
	void plainNumbers () {

		assertEquals(123, Parse.parseInt("123"));
		assertEquals(-123, Parse.parseInt("-123"));
		assertEquals(123L, Parse.parseLong("123"));
		assertEquals(1.5f, Parse.parseFloat("1.5"));
		assertEquals(1.5d, Parse.parseDouble("1.5"));

	}

	@Test
	@DisplayName("整数で受けるとき、小数点から先は捨てる（四捨五入しない）")
	void theFractionIsDropped () {

		/*
		 * <b>切り捨てである。</b>{@code "1.9"} は 2 ではなく <b>1</b> になる。
		 * 丸めたい場合はここを通さないこと。
		 */
		assertEquals(1, Parse.parseInt("1.9"));
		assertEquals(1L, Parse.parseLong("1.9"));

		// 負の数も「小数点の手前まで」なので、0 のほうへ寄る
		assertEquals(-1, Parse.parseInt("-1.9"));

	}

	@Test
	@DisplayName("読めないものは 0（例外は出ない）")
	void unreadableIsZero () {

		/*
		 * <b>ここが「読めなかった」と「本当に 0」の区別が付かないところである。</b>
		 * 入力の検証に使ってはいけない——<b>検証は {@code ValidationRule} の仕事</b>で、
		 * これは<b>すでに検証を通った値を取り出すため</b>のものである。
		 */
		assertEquals(0, Parse.parseInt("abc"));
		assertEquals(0, Parse.parseInt(""));
		assertEquals(0, Parse.parseInt(null));
		assertEquals(0L, Parse.parseLong("abc"));
		assertEquals(0f, Parse.parseFloat("abc"));
		assertEquals(0d, Parse.parseDouble("abc"));

		// 桁があふれても 0
		assertEquals(0, Parse.parseInt("99999999999999999999"));

	}

	@Test
	@DisplayName("小数のほうは小数点を捨てない")
	void floatKeepsTheFraction () {

		assertEquals(1.9f, Parse.parseFloat("1.9"));
		assertEquals(1.9d, Parse.parseDouble("1.9"));

	}

	@Test
	@DisplayName("日付は読めれば Date、読めなければ null")
	void dateIsNullWhenUnreadable () {

		assertNotNull(Parse.parseDate("2026-09-12"));
		assertNotNull(Parse.parseDate(new Date()));

		assertNull(Parse.parseDate("abc"));
		assertNull(Parse.parseDate(null));

	}

	// region ここで固定していないこと

	/*
	 * - <b>{@code parseFloat} / {@code parseDouble} が {@code "NaN"} や
	 *   {@code "Infinity"} を読むこと</b>は固定していない。
	 *   {@code Double.parseDouble} をそのまま通しているためで、
	 *   <b>入力の検証には使わない</b>という前提でそのままにしている
	 *   （検証のほうは要件 D-161 で締めた）
	 * - <b>どの書式の日付が読めるか</b>も見ていない。
	 *   {@code Convertor} の一覧（30 通りほど）が決めており、
	 *   ここで並べると<b>一覧を直すたびに落ちる</b>
	 */

	// endregion

}
