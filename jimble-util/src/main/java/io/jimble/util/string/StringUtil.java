package io.jimble.util.string;

import com.google.common.base.CharMatcher;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 文字列操作ユーティリティ.
 */
public class StringUtil {

	public static void main (String[] args) {

		System.out.println();

	}

	/**
	 * 改行を置換する
	 *
	 * @param sb   文字列
	 * @param dest 置換後文字列
	 * @return 文字列
	 */
	public static String replaceLineSeparator (StringBuilder sb, String dest) {

		replaceAll(sb, "\r\n", dest);
		replaceAll(sb, "\r", dest);
		replaceAll(sb, "\n", dest);

		return sb.toString();

	}

	/**
	 * 改行を置換する
	 *
	 * @param value 文字列
	 * @param dest  置換後文字列
	 * @return 文字列
	 */
	public static String replaceLineSeparator (String value, String dest) {

		StringBuilder sb = new StringBuilder(value);

		replaceAll(sb, "\r\n", dest);
		replaceAll(sb, "\r", dest);
		replaceAll(sb, "\n", dest);

		return sb.toString();

	}

	/**
	 * 文字列を置換する
	 *
	 * @param sb   文字列
	 * @param src  置換対象文字列
	 * @param dest 置換後文字列
	 */
	public static void replace (StringBuilder sb, String src, String dest) {

		int index = sb.indexOf(src);
		if (index > -1) {
			sb.replace(index, index + src.length(), dest);
		}

	}

	/**
	 * 文字列を全て置換する
	 *
	 * @param sb   文字列
	 * @param src  置換対象文字列
	 * @param dest 置換後文字列
	 */
	public static void replaceAll (StringBuilder sb, String src, String dest) {

		int srcLength = src.length();
		int destLength = dest.length();
		int start = 0;
		int index;
		while ((index = sb.indexOf(src, start)) > -1) {
			sb.replace(index, index + srcLength, dest);
			start = index + destLength;
		}

	}

	/**
	 * 文字列を置換する
	 *
	 * @param value 文字列
	 * @param src   置換対象文字列
	 * @param dest  置換後文字列
	 * @return 文字列
	 */
	public static String replace (String value, String src, String dest) {

		if (value == null || value.isEmpty()) {
			return value;
		}

		StringBuilder sb = new StringBuilder(value);

		int index = sb.indexOf(src);
		if (index > -1) {
			sb.replace(index, index + src.length(), dest);
		}

		return sb.toString();

	}

	/**
	 * 文字列を全て置換する
	 *
	 * @param value 文字列
	 * @param src   置換対象文字列
	 * @param dest  置換後文字列
	 * @return 文字列
	 */
	public static String replaceAll (String value, String src, String dest) {

		if (value == null || value.isEmpty()) {
			return value;
		}

		StringBuilder sb = new StringBuilder(value);

		int srcLength = src.length();
		int destLength = dest.length();
		int start = 0;
		int index;
		while ((index = sb.indexOf(src, start)) > -1) {
			sb.replace(index, index + srcLength, dest);
			start = index + destLength;
		}

		return sb.toString();

	}

	/**
	 * 制御文字を除去する
	 *
	 * @param value 文字列
	 * @return 文字列
	 */
	public static String removeControlCharacter (String value) {

		return value.replaceAll("\\p{C}", "");

	}

	/* 改行、タブ以外の制御文字マッチャー */
	private static final CharMatcher CC_W_NLT = CharMatcher.javaIsoControl().and(CharMatcher.anyOf("\r\n\t").negate());

	/**
	 * 改行、タブ以外の制御文字を除去する
	 *
	 * @param value 文字列
	 * @return  文字列
	 */
	public static String removeControlCharacterWithoutNewLineAndTab (String value) {

		return CC_W_NLT.removeFrom(value);

	}

	/**
	 * 文字列中の検索文字列が指定回数出現するまでの文字列を抽出する
	 *
	 * @param value  文字列
	 * @param target 検索文字列
	 * @param count  回数
	 * @return 文字列
	 */
	public static String extract (String value, String target, int count) {

		StringBuilder sb = new StringBuilder();
		String[] values = value.split(Pattern.quote(target), -1);

		int max = Math.min(count, values.length);

		for (int i = 0; i < max; i++) {

			if (i > 0) {
				sb.append(target);
			}

			sb.append(values[i]);

		}

		if (max < values.length) {
			sb.append(target);
		}

		return sb.toString();

	}

	/**
	 * 文字列中に検索文字列が何個あるか数える
	 *
	 * @param value  文字列
	 * @param target 検索文字列
	 * @return 個数
	 */
	public static int count (String value, String target) {

		int start = 0;
		int counter = 0;

		while (start < value.length()) {

			start = value.indexOf(target, start);
			if (start < 0) {
				break;
			}
			start++;

			counter++;

		}

		return counter;

	}

	/**
	 * 文字列中の全角数値を半角数値に変換する
	 *
	 * @param value 文字列
	 * @return 文字列
	 */
	public static String numberZenToHan (String value) {

		if (value == null || value.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (char c : value.toCharArray()) {

			if (c >= 0xFF10 && c <= 0xFF19) {
				sb.append((char) (c - 0xFEE0));
			} else {
				sb.append(c);
			}

		}

		return sb.toString();

	}

	/**
	 * 数値より前の文字列を取得する
	 *
	 * @param value 文字列
	 * @return 文字列
	 */
	public static String beforeNumber (String value) {

		if (value == null || value.isEmpty()) {
			return "";
		}

		String v = numberZenToHan(value);

		StringBuilder sb = new StringBuilder();
		for (char c : v.toCharArray()) {

			if ('0' <= c && c <= '9') {
				return sb.toString();
			}

			sb.append(c);

		}

		return sb.toString();

	}

	/**
	 * 全角文字が含まれるか判定する
	 *
	 * @param value 文字列
	 * @return 全角文字が含まれる場合 = true
	 */
	public static boolean containsZen (String value) {

		if (value == null || value.isEmpty()) {
			return false;
		}

		for (char c : value.toCharArray()) {

			if (String.valueOf(c).getBytes().length > 1) {
				return true;
			}

		}

		return false;

	}

	/**
	 * アルファベットのみ判定
	 *
	 * @param value 文字列
	 * @return アルファベットのみの場合 = true
	 */
	public static boolean isAlphabetOnly (String value) {

		if (value == null || value.isEmpty()) {
			return false;
		}

		for (char c : value.toCharArray()) {

			if (c > 255) {
				return false;
			}

		}

		return true;

	}

	/**
	 * 文字列分割
	 *
	 * @param value     文字列
	 * @param delimiter 区切り文字
	 * @return 結果
	 */
	public static List<String> split (String value, String delimiter) {

		String[] values = value.split(Pattern.quote(delimiter), -1);

		List<String> list = new ArrayList<>();
		for (String v : values) {
			list.add(v);
		}

		return list;

	}

	/**
	 * 文字列切り取り.
	 *
	 * @param value  文字列
	 * @param length 桁数
	 * @return 文字列
	 */
	public static String substring (String value, int length) {

		if (value == null || value.length() == 0) {
			return "";
		}

		if (value.length() < length) {
			return value;
		}

		return value.substring(0, length);

	}

	/**
	 * 文字列切り取り.
	 *
	 * @param value  文字列
	 * @param length 桁数
	 * @param suffix 省略文字
	 * @return 文字列
	 */
	public static String substring (String value, int length, String suffix) {

		if (value == null || value.length() == 0) {
			return "";
		}

		if (value.length() < length) {
			return value;
		}

		return value.substring(0, length) + suffix;

	}


	/**
	 * 文字列連結.
	 *
	 * @param strings 連結対象文字列配列
	 * @return 文字列
	 */
	public static String concat (String... strings) {

		if (strings == null) {
			return "";
		}

		StringBuilder res = new StringBuilder();
		for (String s : strings) {
			res.append(s);
		}

		return res.toString();

	}

	/**
	 * 文字列を結合する
	 *
	 * @param delimiter	区切り文字
	 * @param values	文字列
	 * @return	文字列
	 */
	public static String join (String delimiter, String... values) {

		if (values == null || values.length == 0) {
			return "";
		}

		StringBuilder res = new StringBuilder();

		for (String value : values) {
			if (delimiter != null && res.length() > 0) {
				res.append(delimiter);
			}
			res.append(value);
		}

		return res.toString();

	}

	/**
	 * 文字列連結
	 *
	 * @param delimiter 区切り文字
	 * @param list      文字列のリスト
	 * @return 文字列
	 */
	public static String concat (String delimiter, List<String> list) {

		if (list == null) {
			return "";
		}

		StringBuilder res = new StringBuilder();
		for (String s : list) {

			if (delimiter != null && res.length() > 0) {
				res.append(delimiter);
			}

			res.append(s);
		}

		return res.toString();

	}

	/**
	 * 日付文字列を取得する.
	 *
	 * @param date   日付
	 * @param format フォーマット
	 * @return 日付文字列
	 */
	public static String dateString (Date date, String format) {

		if (date == null) {
			return "";
		}

		SimpleDateFormat sdf = new SimpleDateFormat(format);

		return sdf.format(date);

	}

	/**
	 * 数値フォーマット.
	 *
	 * @param value  値
	 * @param format フォーマット
	 * @return 数値文字列
	 */
	public static String numberFormat (double value, String format) {

		try {

			return String.format(format, value);

		} catch (Exception ex) {

			return "0";

		}

	}

	/**
	 * 数値フォーマット.
	 *
	 * @param value  値
	 * @param format フォーマット
	 * @return 数値文字列
	 */
	public static String numberFormat (long value, String format) {

		try {

			return String.format(format, value);

		} catch (Exception ex) {

			return "0";

		}

	}

	/**
	 * パスワードを生成する
	 *
	 * @param length 桁数
	 * @return パスワード
	 */
	public static String createPassword (int length) {

		return createPassword(length, false);

	}

	/**
	 * パスワードを生成する
	 *
	 * @param length 桁数
	 * @return パスワード
	 */
	public static String createPassword (int length, boolean useSign) {

		if (length <= 0) {
			return "";
		}

		//アルファベット大文字小文字のスタイル(normal/lowerCase/upperCase)
		String style = "normal";

		//生成処理
		StringBuilder result = new StringBuilder();
		//パスワードに使用する文字を格納
		StringBuilder source = new StringBuilder();
		//数字
		for (int i = 0x30; i < 0x3A; i++) {
			source.append((char) i);
		}
		//記号
		if (useSign) {
			for (int i = 0x21; i < 0x30; i++) {
				source.append((char) i);
			}
		}
		//アルファベット小文字
		switch (style) {
			case "lowerCase":
				break;
			default:
				for (int i = 0x41; i < 0x5b; i++) {
					source.append((char) i);
				}
				break;
		}
		//アルファベット大文字
		switch (style) {
			case "upperCase":
				break;
			default:
				for (int i = 0x61; i < 0x7b; i++) {
					source.append((char) i);
				}
				break;
		}

		int sourceLength = source.length();
		SplittableRandom random = new SplittableRandom();
		while (result.length() < length) {
			result.append(source.charAt(Math.abs(random.nextInt()) % sourceLength));
		}

		return result.toString();

	}

	/**
	 * 文字列をトリムする
	 *
	 * @param value	文字列
	 * @return	文字列
	 */
	public static String trim (String value) {

		if (value == null) {
			return value;
		}

		return value.trim();

	}

	// region 数値を62進数に変換する

	/**
	 * 数値を62進数に変換する.
	 *
	 * @param value 数値
	 * @return 62進数
	 */
	public static String toBase62String (long value) {

		if (value == 0) {
			return "0";
		}

		boolean isMinus = false;
		if (value < 0) {
			isMinus = true;
			value *= -1;
		}

		long val = value;

		StringBuilder sb = new StringBuilder();
		while (val > 0) {

			int mod = (int) (val % 62);
			if (mod < 10) {
				// 数字
				sb.append(mod);
			} else if (mod < 36) {
				// 英小文字 a = 97
				mod += 87;
				sb.append((char) mod);
			} else {
				// 英大文字 A = 65
				mod += 29;
				sb.append((char) mod);
			}

			val = val / 62;

		}

		if (isMinus) {
			sb.append("_");
		}

		return sb.reverse().toString();

	}

	// endregion

	/**
	 * 改行続きを削除する
	 *
	 * @param text	文字列
	 * @return	文字列
	 */
	public static String trimBlankLine (String text) {

		String result = text.replaceAll("(\n|\r|\n\r|\r\n){2,}", "\n");
		result = result.replaceAll("[ \t\\x0B\f] + (\n|\r|\n\r|\r\n)", "");
		if (result.substring(result.length() - 1).equals("\n")) {
			result = result.substring(0, result.length() - 1);
		}

		return result;

	}

	/**
	 * 一意な文字列を作成する
	 *
	 * @return	一意な文字列
	 */
	public static String uniqueString () {

		return UUID.randomUUID() + "-" + System.currentTimeMillis();

	}

	/**
	 * 文字列をBase64文字列に変換する
	 *
	 * @param value 文字列
	 * @return  Base64文字列
	 */
	public static String base64Encode (String value) {

		return base64Encode(value, StandardCharsets.UTF_8);

	}

	/**
	 * 文字列をBase64文字列に変換する
	 *
	 * @param value 文字列
	 * @return  Base64文字列
	 */
	public static String base64Encode (String value, Charset charset) {

		return new String(Base64.getEncoder().encode(value.getBytes(charset)), charset);

	}

	/**
	 * Base64文字列を文字列に変換する
	 *
	 * @param value Base64文字列
	 * @return  文字列
	 */
	public static String base64Decode (String value) {

		return base64Decode(value, StandardCharsets.UTF_8);

	}

	/**
	 * Base64文字列を文字列に変換する
	 *
	 * @param value Base64文字列
	 * @return  文字列
	 */
	public static String base64Decode (String value, Charset charset) {

		return new String(Base64.getDecoder().decode(value.getBytes(charset)), charset);

	}

	/**
	 * ランダムな指定桁の数値文字列を取得する
	 *
	 * @param length    桁数
	 * @return  文字列
	 */
	public static String randomNumberString (int length) {

		long upperBound = (long) Math.pow(10, length);
		return String.format("%0" + length + "d",new Random().nextLong(upperBound));

	}

	/**
	 * 4byte文字を削除する
	 *
	 * @param src   文字列
	 * @return  文字列
	 */
	public static String remove4byteCharacter (String src) {

		return src.replaceAll("[^\\u0000-\\uFFFF]", "");

	}

}
