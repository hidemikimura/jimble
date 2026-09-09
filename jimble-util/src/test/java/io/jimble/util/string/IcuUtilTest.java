package io.jimble.util.string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * かな・全角半角の変換（{@link IcuUtil}）
 *
 * <p>
 * ICU4J をやめて表に置き換えたときの答えを固定してある（要件 D-120）。
 * <b>置き換えの前後で答えが変わっても例外は出ず、文字が変わるだけ</b>なので、
 * ここで押さえておかないと気づけない。
 * </p>
 *
 * <p>
 * 置き換えのときは、ICU が返していたものと<b>129 万通りを突き合わせて</b>確かめた
 * （BMP の全 1 文字、全 1 文字 × 濁点6種、かな・記号だけを混ぜた文字列 20 万件）。
 * 食い違いは<b>下の「ICU と変えたところ」だけ</b>である。
 * </p>
 *
 * <h2>ICU と変えたところ</h2>
 * <ul>
 *   <li><b>{@code convertToHiragana} は「ー」を残す</b>（ICU は {@code コーヒー} → {@code こおひい}）</li>
 *   <li><b>無関係な文字の Unicode 正規化をしない</b>（ICU はチベット文字を合成し、結合文字を並べ替えていた）</li>
 * </ul>
 */
class IcuUtilTest {

	// region カタカナにする

	@Test
	@DisplayName("ひらがなをカタカナにする")
	void toKatakana () {

		assertEquals("アイウエオ", IcuUtil.convertToKatakana("あいうえお"));
		assertEquals("ガギグゲゴ", IcuUtil.convertToKatakana("がぎぐげご"));
		assertEquals("キョウ", IcuUtil.convertToKatakana("きょう"));
		assertEquals("ヴ", IcuUtil.convertToKatakana("ゔ"));
		assertEquals("ヽヾ", IcuUtil.convertToKatakana("ゝゞ"));

		// かな以外はそのまま
		assertEquals("東京都ABCａｂｃ", IcuUtil.convertToKatakana("東京都ABCａｂｃ"));

		// 長音符はそのまま
		assertEquals("コーヒー", IcuUtil.convertToKatakana("こーひー"));

	}

	@Test
	@DisplayName("半角カナもカタカナにする")
	void toKatakanaFromHalfwidth () {

		assertEquals("コーヒー", IcuUtil.convertToKatakana("ｺｰﾋｰ"));
		assertEquals("パソコン", IcuUtil.convertToKatakana("ﾊﾟｿｺﾝ"));
		assertEquals("。「」、・", IcuUtil.convertToKatakana("｡｢｣､･"));

	}

	@Test
	@DisplayName("濁点・半濁点を1文字にまとめる")
	void compose () {

		// 見た目が同じで中身が違うので、ここは番号で書く。
		//   U+3099 U+309A  結合文字の濁点・半濁点（か + ゙）
		//   U+309B U+309C  離れた濁点・半濁点（か ゛）
		//   U+FF9E U+FF9F  半角の濁点・半濁点

		// 結合文字はまとめる
		assertEquals("ガ", IcuUtil.convertToKatakana("\u304B\u3099"));
		assertEquals("パ", IcuUtil.convertToKatakana("\u306F\u309A"));

		// 半角の濁点もまとめる
		assertEquals("ガ", IcuUtil.convertToKatakana("\u304B\uFF9E"));
		assertEquals("ガ", IcuUtil.convertToKatakana("\uFF76\uFF9E"));
		assertEquals("ガ", IcuUtil.convertToKatakana("\u30AB\uFF9E"));

		// <b>離れた ゛ ゜ はまとめない</b>（ICU と同じ）
		assertEquals("\u30AB\u309B", IcuUtil.convertToKatakana("\u304B\u309B"));
		assertEquals("\u30F3\u309B", IcuUtil.convertToKatakana("\u3093\u309B"));

		// 濁点の付かない字に濁点が来ても、落とさずそのまま残す
		assertEquals("\u30F3\u3099", IcuUtil.convertToKatakana("\u3093\u3099"));

		// もともと1文字のものは、そのまま1文字
		assertEquals("ガ", IcuUtil.convertToKatakana("が"));

	}

	@Test
	@DisplayName("囲みカタカナと単位の合成文字を開く")
	void expand () {

		assertEquals("ア", IcuUtil.convertToKatakana("㋐"));
		assertEquals("ヲ", IcuUtil.convertToKatakana("㋾"));
		assertEquals("アパート", IcuUtil.convertToKatakana("㌀"));
		assertEquals("パーセント", IcuUtil.convertToKatakana("㌫"));
		assertEquals("ワット", IcuUtil.convertToKatakana("㍗"));
		assertEquals("ヨリ", IcuUtil.convertToKatakana("ゟ"));
		assertEquals("コト", IcuUtil.convertToKatakana("ヿ"));

	}

	@Test
	@DisplayName("ゕ ゖ は動かさない（ICU と同じ）")
	void smallKa () {

		assertEquals("ゕゖ", IcuUtil.convertToKatakana("ゕゖ"));

	}

	// endregion

	// region ひらがなにする

	@Test
	@DisplayName("カタカナをひらがなにする")
	void toHiragana () {

		assertEquals("あいうえお", IcuUtil.convertToHiragana("アイウエオ"));
		assertEquals("がぎぐげご", IcuUtil.convertToHiragana("ガギグゲゴ"));
		assertEquals("ゔ", IcuUtil.convertToHiragana("ヴ"));
		assertEquals("ぱそこん", IcuUtil.convertToHiragana("ﾊﾟｿｺﾝ"));
		assertEquals("あぱーと", IcuUtil.convertToHiragana("㌀"));

	}

	@Test
	@DisplayName("「ー」は残す（ICU は母音に開いていた）")
	void keepsLongVowel () {

		// ICU は こおひい を返していた
		assertEquals("こーひー", IcuUtil.convertToHiragana("コーヒー"));
		assertEquals("さーばー", IcuUtil.convertToHiragana("サーバー"));
		assertEquals("かーど１枚", IcuUtil.convertToHiragana("カード１枚"));

	}

	@Test
	@DisplayName("ヵ ヶ は か け にする（ICU と同じ）")
	void largeKa () {

		assertEquals("け月", IcuUtil.convertToHiragana("ヶ月"));
		assertEquals("か所", IcuUtil.convertToHiragana("ヵ所"));

	}

	@Test
	@DisplayName("ひらがなに無い濁点付きカタカナは、濁点を分けて返す")
	void noHiraganaForm () {

		// ヷ ヸ ヹ ヺ にあたるひらがなは無い
		assertEquals("\u308F\u3099", IcuUtil.convertToHiragana("ヷ"));
		assertEquals("\u3092\u3099", IcuUtil.convertToHiragana("ヺ"));

	}

	@Test
	@DisplayName("カタカナ → ひらがな → カタカナ で元に戻る")
	void roundTrip () {

		for (String s : new String[]{"コーヒー", "サーバー", "ソフトウェア", "キムラ ヒデミ", "ヴィヴァルディ", "ガッコウ"}) {
			assertEquals(s, IcuUtil.convertToKatakana(IcuUtil.convertToHiragana(s)), s);
		}

	}

	// endregion

	// region 半角にする

	@Test
	@DisplayName("全角を半角にする")
	void toHankaku () {

		assertEquals("ABC abc 123", IcuUtil.convertHankaku("ＡＢＣ　ａｂｃ　１２３"));
		assertEquals("!\"#$%&'()", IcuUtil.convertHankaku("！＂＃＄％＆＇（）"));
		assertEquals("ｱｲｳｴｵ", IcuUtil.convertHankaku("アイウエオ"));
		assertEquals("\uFF76\uFF9E\uFF77\uFF9E\uFF8A\uFF9F", IcuUtil.convertHankaku("ガギパ"));
		assertEquals("ｺｰﾋｰ", IcuUtil.convertHankaku("コーヒー"));
		assertEquals("｡｢｣､･", IcuUtil.convertHankaku("。「」、・"));
		assertEquals("¥¢£", IcuUtil.convertHankaku("￥￠￡"));
		assertEquals("￩￪￫￬", IcuUtil.convertHankaku("←↑→↓"));

		// ひらがなと漢字はそのまま
		assertEquals("あいう漢字", IcuUtil.convertHankaku("あいう漢字"));

	}

	@Test
	@DisplayName("半角に無い字はそのまま（ヶ ヮ ヰ）")
	void noHalfwidthForm () {

		assertEquals("ヶヮヰヱ", IcuUtil.convertHankaku("ヶヮヰヱ"));

	}

	// endregion

	// region 全角にする

	@Test
	@DisplayName("半角を全角にする")
	void toZenkaku () {

		assertEquals("ＡＢＣ　ａｂｃ　１２３", IcuUtil.convertZenkaku("ABC abc 123"));
		assertEquals("アイウエオ", IcuUtil.convertZenkaku("ｱｲｳｴｵ"));
		assertEquals("コーヒー", IcuUtil.convertZenkaku("ｺｰﾋｰ"));
		assertEquals("。「」、・", IcuUtil.convertZenkaku("｡｢｣､･"));
		assertEquals("￥￠￡", IcuUtil.convertZenkaku("¥¢£"));

		// ひらがなと漢字はそのまま
		assertEquals("あいう漢字", IcuUtil.convertZenkaku("あいう漢字"));

	}

	@Test
	@DisplayName("半角カナと半角濁点は1文字にまとめる")
	void zenkakuCompose () {

		assertEquals("ガギパ", IcuUtil.convertZenkaku("\uFF76\uFF9E\uFF77\uFF9E\uFF8A\uFF9F"));
		assertEquals("ヴ", IcuUtil.convertZenkaku("ｳﾞ"));
		assertEquals("パソコン", IcuUtil.convertZenkaku("ﾊﾟｿｺﾝ"));

		// <b>全角のカナに付いた濁点はまとめない</b>（ICU と同じ）。まとめるのは「半角カナ＋半角濁点」の組だけ
		assertEquals("\u30AB\u3099", IcuUtil.convertZenkaku("\u30AB\uFF9E"));

		// まとまらない組み合わせは、濁点だけ全角（結合文字）にして残す
		assertEquals("\u30F3\u3099", IcuUtil.convertZenkaku("\uFF9D\uFF9E"));

	}

	// endregion

	// region 入口

	@Test
	@DisplayName("null と空文字は空文字")
	void empty () {

		assertEquals("", IcuUtil.convertToKatakana(null));
		assertEquals("", IcuUtil.convertToHiragana(null));
		assertEquals("", IcuUtil.convertHankaku(null));
		assertEquals("", IcuUtil.convertZenkaku(null));

		assertEquals("", IcuUtil.convertToKatakana(""));
		assertEquals("", IcuUtil.convertToHiragana(""));
		assertEquals("", IcuUtil.convertHankaku(""));
		assertEquals("", IcuUtil.convertZenkaku(""));

	}

	// endregion

}
