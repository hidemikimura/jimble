package io.jimble.util.xml;

/**
 * XML 1.0 のエスケープ
 *
 * <p>
 * もとは commons-text の {@code StringEscapeUtils.escapeXml10} を呼んでいた。
 * 使っていたのがこれ1つだけで、<b>commons-text と commons-lang3 で 0.93MB</b> あったので、
 * 表を起こして置き換えた（要件 D-121）。答えは commons-text と 1 文字も変わらない。
 * </p>
 *
 * <h2>やること</h2>
 * <ul>
 *   <li>{@code " & ' < >} を実体参照にする</li>
 *   <li><b>XML 1.0 に書けない文字を捨てる</b>（{@code U+0000}〜{@code U+0008}、{@code U+000B}、
 *       {@code U+000C}、{@code U+000E}〜{@code U+001F}、{@code U+FFFE}、{@code U+FFFF}）。
 *       タブ・改行・復帰（{@code U+0009 U+000A U+000D}）は書けるので残す</li>
 *   <li>制御文字のうち<b>書けるが読みにくいもの</b>（{@code U+007F}〜{@code U+0084}、
 *       {@code U+0086}〜{@code U+009F}）は数値参照にする。
 *       {@code U+0085}（改行を表す NEL）だけはそのまま残す</li>
 * </ul>
 */
final class XmlEscape {

	private XmlEscape () {
	}

	/**
	 * XML 1.0 用にエスケープする
	 *
	 * @param value 文字列
	 * @return エスケープした文字列
	 */
	static String escape (String value) {

		if (value == null || value.isEmpty()) {
			return value;
		}

		StringBuilder sb = null;

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);
			String to = replacement(c);

			if (to == null) {
				if (sb != null) {
					sb.append(c);
				}
				continue;
			}

			// 直すところが1つでもあったときだけ作る
			if (sb == null) {
				sb = new StringBuilder(value.length() + 16);
				sb.append(value, 0, i);
			}

			sb.append(to);

		}

		return sb == null ? value : sb.toString();

	}

	/**
	 * 1文字の置き換え先を返す
	 *
	 * @param c 文字
	 * @return 置き換え先。そのままでよければ null
	 */
	private static String replacement (char c) {

		switch (c) {
			case '"': return "&quot;";
			case '&': return "&amp;";
			case '\'': return "&apos;";
			case '<': return "&lt;";
			case '>': return "&gt;";
			case '\t': case '\n': case '\r': return null;
			default: break;
		}

		// XML 1.0 に書けない
		if (c <= 0x1F || c == 0xFFFE || c == 0xFFFF) {
			return "";
		}

		// 書けるが読みにくい。U+0085 は改行なので残す
		if ((c >= 0x7F && c <= 0x84) || (c >= 0x86 && c <= 0x9F)) {
			return "&#" + ((int) c) + ";";
		}

		return null;

	}

}
