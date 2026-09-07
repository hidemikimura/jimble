package io.jimble.util.comparator;

import java.util.Comparator;

/**
 * 文字数Comparatorクラス.
 */
public class LengthComparator implements Comparator<String> {

	private boolean asc = true;

	public LengthComparator () {

	}

	public LengthComparator (boolean asc) {

		this.asc = asc;

	}

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

			if (o1.length() < o2.length()) {
				return asc ? -1 : 1;
			}

			if (o1.length() > o2.length()) {
				return asc ? 1 : -1;
			}

			return 0;

		} catch (Exception ex) {

			return 0;

		}

	}

}
