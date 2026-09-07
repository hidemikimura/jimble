package io.jimble.util.comparator;

import java.util.Comparator;

/**
 * 名前Comparatorクラス.
 */
public class NameComparator implements Comparator<String> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int compare(String o1, String o2) {

		if (o1 == null && o2 == null) {
			return 0;
		}

		if (o1 == null && o2 != null) {
			return -1;
		}

		if (o1 != null && o2 == null) {
			return 1;
		}

		try {

			return strCmpLogical(o1, o2);

		} catch (Exception ex) {

			return o1.compareTo(o2);

		}

	}

	/**
	 * 文字列を自然順序順で比較する.
	 *
	 * @param x 文字列
	 * @param y 文字列
	 * @return 結果
	 */
	private static int strCmpLogical(String x, String y) {

		if (x.length() == 0 && y.length() == 0) {
			return 0;
		}

		int i = 0;
		int j = 0;
		int iMax = x.length();
		int jMax = y.length();
		int d;

		// 共通部分を読み飛ばす
		while (compareChar(x.charAt(i), y.charAt(j)) == 0) {
			i++;
			j++;
			if (i >= iMax || j > jMax) break;
		}

		// 比較する
		int in = i + 1;
		int jn = j + 1;
		boolean xIsDec = ('0' <= x.charAt(i)) && (x.charAt(i) <= '9');
		boolean yIsDec = ('0' <= y.charAt(j)) && (y.charAt(j) <= '9');
		while (true) {

			// 文字列長チェック
			if (i >= iMax) {
				if (j >= jMax) {
					return 0;
				}
				return -1;
			} else {
				if (j >= jMax) {
					return 1;
				}
			}

			// 次のトークンまでを調べる
			while (in < iMax) {
				if (xIsDec) {
					if (('0' > x.charAt(in)) || (x.charAt(in) > '9')) break; // 数値でないとき
				} else {
					if (('0' <= x.charAt(in)) && (x.charAt(in) <= '9')) break; // 数値のとき
				}
				in++;
			}
			while (jn < jMax) {
				if (yIsDec) {
					if (('0' > y.charAt(jn)) || (y.charAt(jn) > '9')) break; // 数値でないとき
				} else {
					if (('0' <= y.charAt(jn)) && (y.charAt(jn) <= '9')) break; // 数値のとき
				}
				jn++;
			}

			// 比較
			long lenx = in - i;
			long leny = jn - j;
			if (xIsDec && yIsDec) {
				// 数値比較
				if (lenx > leny) {
					return 1;
				} else if (lenx < leny) {
					return -1;
				} else {
					for (; i != in; i++, j++) {
						if (x.charAt(i) > y.charAt(j)) {
							return 1;
						} else if (x.charAt(i) < y.charAt(j)) {
							return -1;
						}
					}
				}
			} else {
				// 文字列比較
				if (lenx < leny) {
					for (; i != in; i++, j++) {
						d = compareChar(x.charAt(i), y.charAt(j));
						if (d != 0) {
							return d;
						}
					}
				} else {
					for (; j != jn; i++, j++) {
						d = compareChar(x.charAt(i), y.charAt(j));
						if (d != 0) {
							return d;
						}
					}
				}
			}
			xIsDec = !xIsDec;
			yIsDec = !yIsDec;

		}

	}

	/**
	 * 文字を自然順序順に比較する.
	 *
	 * @param px 文字
	 * @param py 文字
	 * @return 結果
	 */
	private static int compareChar(char px, char py) {

		int toSmall = ('a' - 'A');
		boolean xIsLarge = ('A' <= px) && (px <= 'Z');
		boolean yIsLarge = ('A' <= py) && (py <= 'Z');
		if (xIsLarge && !yIsLarge) {
			int sx = (px) + toSmall;
			// xが大文字のとき
			if (sx > py) {
				return 1;
			} else if (sx < py) {
				return -1;
			}
			return 0;
		} else if (!(xIsLarge) && yIsLarge) {
			int sy = (py) + toSmall;
			// yが大文字の時
			if (px > sy) {
				return 1;
			} else if (px < sy) {
				return -1;
			}
			return 0;
		} else {
			if (px > py) {
				return 1;
			} else if (px < py) {
				return -1;
			}
			return 0;
		}

	}

}
