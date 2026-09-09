package io.jimble.util.url;

/**
 * IP アドレスの書き方の判定
 *
 * <p>
 * もとは commons-validator の {@code InetAddressValidator} を呼んでいた。
 * 使っていたのがこれ1つだけで、<b>commons-beanutils / commons-digester /
 * commons-collections / commons-logging という古い4本を連れて合計 1.25MB</b> あったので、
 * 自前に置き換えた（要件 D-121）。答えは commons-validator と変わらない。
 * </p>
 *
 * <p>
 * <b>名前は引かない。</b>ここでやるのは<b>書き方が IP アドレスかどうか</b>だけで、
 * {@link java.net.InetAddress} のように名前解決へ出ていかない
 * （出ていくと、判定のつもりが<b>ネットワーク待ちになる</b>）。
 * </p>
 */
final class IpAddress {

	private IpAddress () {
	}

	/**
	 * IPv4 アドレスの書き方か
	 *
	 * <p>
	 * 点で区切った4つの 0〜255 だけを通す。<b>頭の 0 は通さない</b>
	 * （{@code 010.1.1.1} は8進数と読む処理系があり、<b>読み手によって別のアドレスになる</b>）。
	 * </p>
	 *
	 * @param value 文字列
	 * @return IPv4 アドレスなら true
	 */
	static boolean isV4 (String value) {

		if (value == null || value.isEmpty()) {
			return false;
		}

		int part = 0;
		int i = 0;

		while (i < value.length()) {

			if (part == 4) {
				return false;
			}

			int start = i;

			while (i < value.length() && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
				i++;
			}

			int len = i - start;

			if (len == 0 || len > 3) {
				return false;
			}

			// 頭の 0
			if (len > 1 && value.charAt(start) == '0') {
				return false;
			}

			if (Integer.parseInt(value, start, i, 10) > 255) {
				return false;
			}

			part++;

			if (i < value.length()) {
				if (value.charAt(i) != '.') {
					return false;
				}
				i++;
				// 末尾の「.」
				if (i == value.length()) {
					return false;
				}
			}

		}

		return part == 4;

	}

	/**
	 * IPv6 アドレスの書き方か
	 *
	 * <p>
	 * {@code ::} の省略、末尾の IPv4 記法（{@code ::ffff:192.168.0.1}）、
	 * ゾーン（{@code fe80::1%eth0}）、プレフィックス長（{@code 2001:db8::/32}）を通す。
	 * <b>角かっこは付けない</b>
	 * （URL から取り出したホストは {@code [::1]} の形なので、呼ぶ側で外すこと）。
	 * </p>
	 *
	 * @param value 文字列
	 * @return IPv6 アドレスなら true
	 */
	static boolean isV6 (String value) {

		if (value == null || value.isEmpty()) {
			return false;
		}

		// プレフィックス長（/32）。<b>ゾーンより後ろに書く</b>
		int slash = value.indexOf('/');
		if (slash >= 0) {

			if (value.indexOf('/', slash + 1) >= 0) {
				return false;
			}

			if (!isPrefixLength(value.substring(slash + 1))) {
				return false;
			}

			value = value.substring(0, slash);

		}

		// ゾーン（%eth0 / %1）
		int percent = value.indexOf('%');
		if (percent >= 0) {

			if (!isZone(value.substring(percent + 1))) {
				return false;
			}

			value = value.substring(0, percent);

		}

		/*
		 * 末尾が IPv4 記法なら、16bit 2つぶんに書き換えてから数える。
		 * <b>ここで数を足すだけにすると、直前の「:」が余って弾いてしまう</b>
		 */
		int lastColon = value.lastIndexOf(':');

		if (lastColon >= 0 && value.indexOf('.', lastColon) >= 0) {

			if (!isV4(value.substring(lastColon + 1))) {
				return false;
			}

			value = value.substring(0, lastColon + 1) + "0:0";

		}

		// 「::」は1回だけ
		int skip = value.indexOf("::");
		if (skip >= 0 && value.indexOf("::", skip + 1) >= 0) {
			return false;
		}

		int groups = 0;
		int i = 0;

		while (i < value.length()) {

			if (value.charAt(i) == ':') {
				// 先頭の「:」は「::」の一部でなければならない
				if (i == 0 && (value.length() < 2 || value.charAt(1) != ':')) {
					return false;
				}
				i++;
				continue;
			}

			int start = i;

			while (i < value.length() && isHex(value.charAt(i))) {
				i++;
			}

			int len = i - start;

			if (len == 0 || len > 4) {
				return false;
			}

			groups++;

			if (i < value.length() && value.charAt(i) != ':') {
				return false;
			}

		}

		// 末尾が「:」で終わるのは「::」のときだけ
		if (value.endsWith(":") && !value.endsWith("::")) {
			return false;
		}

		if (skip >= 0) {
			// 省略があるなら、書いてある数は8未満（8個書いてあるなら省略する意味が無い）
			return groups < 8;
		}

		return groups == 8;

	}

	/**
	 * プレフィックス長として読めるか（0〜128 の数字）
	 *
	 * @param value 文字列
	 * @return 読めるなら true
	 */
	private static boolean isPrefixLength (String value) {

		if (value.isEmpty() || value.length() > 3) {
			return false;
		}

		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) < '0' || value.charAt(i) > '9') {
				return false;
			}
		}

		return Integer.parseInt(value) <= 128;

	}

	/**
	 * ゾーンとして読めるか
	 *
	 * <p>
	 * ゾーンの書き方は処理系ごとに違うので、<b>書けない文字だけを決めてある</b>
	 * （空白・{@code /}・{@code %}）。
	 * </p>
	 *
	 * @param value 文字列
	 * @return 読めるなら true
	 */
	private static boolean isZone (String value) {

		if (value.isEmpty()) {
			return false;
		}

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);

			if (Character.isWhitespace(c) || c == '/' || c == '%') {
				return false;
			}

		}

		return true;

	}

	/**
	 * 16進数の1桁か
	 *
	 * @param c 文字
	 * @return 16進数なら true
	 */
	private static boolean isHex (char c) {

		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');

	}

}
