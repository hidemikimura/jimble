package io.jimble.util.string;

import com.ibm.icu.text.Transliterator;

/**
 * 文字列変換ユーティリティ
 */
public class IcuUtil {

	/**
	 * カタカナに変換
	 *
	 * @param src 文字
	 * @return カタカナ
	 */
	public static String convertToKatakana (String src) {

		return transliterate(src, "Hiragana-Katakana");

	}

	/**
	 * ひらがなに変換
	 *
	 * @param src 文字
	 * @return ひらがな
	 */
	public static String convertToHiragana (String src) {

		return transliterate(src, "Katakana-Hiragana");

	}

	/**
	 * 半角に変換
	 *
	 * @param src 文字
	 * @return 半角文字
	 */
	public static String convertHankaku (String src) {

		return transliterate(src, "Fullwidth-Halfwidth");

	}

	/**
	 * 全角に変換
	 *
	 * @param src 文字
	 * @return 全角文字
	 */
	public static String convertZenkaku (String src) {

		return transliterate(src, "Halfwidth-Fullwidth");

	}

	/**
	 * 指定ルールで変換する
	 *
	 * @param src 文字
	 * @param id  ルール
	 * @return 変換後の文字
	 */
	private static String transliterate (String src, String id) {

		if (src == null || src.isEmpty()) {
			return "";
		}

		return Transliterator.getInstance(id).transliterate(src);

	}

}
