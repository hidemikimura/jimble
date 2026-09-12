package io.jimble.util.comparator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 並べ替えの2つ（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>どちらも公開しているのに、どこからも呼ばれておらず、テストも無かった。</b>
 * 1.0 を出せば<b>約束の対象になって消せなくなる</b>ので、
 * 何を約束したことになるのかを書き出しておく。
 * </p>
 *
 * <p>
 * <b>並べ替えの壊れ方は静かである。</b>例外は出ず、<b>並びが違うだけ</b>——
 * 「10 が 9 より前に来る」画面を見て、原因まで辿れる人は少ない。
 * </p>
 */
class ComparatorsTest {

	/**
	 * 並べ替えた結果
	 *
	 * @param comparator	並べ方
	 * @param values		値
	 * @return	並べ替えたもの
	 */
	private static List<String> sorted (Comparator<String> comparator, String... values) {

		List<String> list = new ArrayList<>(Arrays.asList(values));

		list.sort(comparator);

		return list;

	}

	@Test
	@DisplayName("NameComparator は数字を数として見る")
	void nameComparatorSortsNumbersAsNumbers () {

		/*
		 * <b>文字として並べると {@code 10} は {@code 9} より前に来る。</b>
		 * ファイル名や項目名を並べるところでは、たいていこれが困る。
		 */
		assertEquals(List.of("item1", "item2", "item9", "item10", "item100")
			, sorted(new NameComparator(), "item10", "item100", "item2", "item1", "item9"));

		// 文字としての並べ替えとは違う（違わなければ、このクラスは要らない）
		assertEquals(List.of("item1", "item10", "item100", "item2", "item9")
			, sorted(Comparator.naturalOrder(), "item10", "item100", "item2", "item1", "item9"));

	}

	@Test
	@DisplayName("NameComparator は null を先頭に置く")
	void nameComparatorPutsNullFirst () {

		List<String> list = new ArrayList<>(Arrays.asList("b", null, "a"));

		list.sort(new NameComparator());

		assertEquals(Arrays.asList(null, "a", "b"), list, "null で落ちるか、並びが変わりました");

	}

	@Test
	@DisplayName("LengthComparator は長さで並べる")
	void lengthComparatorSortsByLength () {

		assertEquals(List.of("あ", "いう", "えおか")
			, sorted(new LengthComparator(), "えおか", "あ", "いう"));

		// 引数で降順にできる
		assertEquals(List.of("えおか", "いう", "あ")
			, sorted(new LengthComparator(false), "えおか", "あ", "いう"));

	}

	@Test
	@DisplayName("LengthComparator も null で落ちない")
	void lengthComparatorHandlesNull () {

		List<String> list = new ArrayList<>(Arrays.asList("bb", null, "a"));

		list.sort(new LengthComparator());

		assertNull(list.getFirst(), "null が先頭に来ていません: " + list);

	}

	// region ここで固定していないこと

	/*
	 * - <b>同じ長さのときの並び</b>（{@code LengthComparator}）は固定していない。
	 *   長さだけを見る道具なので、そこは<b>元の並びのまま</b>でよい
	 * - <b>{@code NameComparator} の細かい規則</b>（記号や全角数字の扱い）も見ていない。
	 *   ここで並べると<b>実装をそのまま写す</b>ことになり、テストの意味が薄い
	 */

	// endregion

}
