package io.jimble.util.parse;

import io.jimble.util.convertor.Convertor;

import java.util.Date;

/**
 * 汎用パースユーティリティ.
 */
public class Parse {

	/**
	 * 数値文字列をint型で取得する.
	 *
	 * @param str	数値文字列
	 * @return	値
	 */
	public static int parseInt(String str) {

		try {

			int index = str.indexOf('.');
			if (index >= 0) {
				str = str.substring(0, index);
			}

			return Integer.parseInt(str);

		} catch (Exception e) {

			return 0;

		}

	}

	/**
	 * 数値文字列をlong型で取得する.
	 *
	 * @param str	数値文字列
	 * @return	値
	 */
	public static long parseLong(String str) {

		try {

			int index = str.indexOf('.');
			if (index >= 0) {
				str = str.substring(0, index);
			}

			return Long.parseLong(str);

		} catch (Exception e) {

			return 0;

		}

	}

	/**
	 * 数値文字列をfloat型で取得する.
	 *
	 * @param str	数値文字列
	 * @return	値
	 */
	public static float parseFloat(String str) {

		try {

			return Float.parseFloat(str);

		} catch (Exception e) {

			return 0;

		}

	}

	/**
	 * 数値文字列をdouble型で取得する.
	 *
	 * @param str	数値文字列
	 * @return	値
	 */
	public static double parseDouble(String str) {

		try {

			return Double.parseDouble(str);

		} catch (Exception e) {

			return 0;

		}

	}

	/**
	 * 日付文字列をDate型で取得する.
	 *
	 * @param value	値
	 * @return	値
	 */
	public static Date parseDate (Object value) {

		try {

			return Convertor.convert(null, value, Date.class);

		} catch (Exception ex) {

			return null;

		}

	}

}
