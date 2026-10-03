package io.jimble.util.mail;

import java.net.IDN;
import java.util.Locale;

/**
 * メールアドレスと、表示する名前（D-263）
 *
 * <p>
 * <b>作った時点で形を確かめる</b>。改行・山括弧・空白を含むものは断る（ヘッダの差し込みを止める）。
 * ローカル部（{@code @} の前）は ASCII だけ。ドメインは日本語のドメインも受け付け、Punycode に直す。
 * </p>
 *
 * @param address	メールアドレス（{@code hanako@example.co.jp}）
 * @param name		表示する名前（{@code 山田 花子}。空でもよい）
 */
public record MailAddress(String address, String name) {

	/** ローカル部の上限（RFC 5321） */
	private static final int MAX_LOCAL = 64;

	/** アドレスの上限（RFC 5321 の Path から < > を除いたもの） */
	private static final int MAX_ADDRESS = 254;

	/**
	 * 作る（形を確かめる）
	 *
	 * @param address	メールアドレス
	 * @param name		表示する名前（空でもよい）
	 * @throws MailException	形が違う場合
	 */
	public MailAddress {

		address = normalize(address);
		name = name == null ? "" : name.strip();

		if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
			throw new MailException("表示する名前に改行は入れられません: " + name.strip());
		}

	}

	/**
	 * アドレスだけで作る
	 *
	 * @param address	メールアドレス
	 * @return	アドレス
	 */
	public static MailAddress of (String address) {

		return new MailAddress(address, "");

	}

	/**
	 * 名前つきで作る
	 *
	 * @param address	メールアドレス
	 * @param name		表示する名前
	 * @return	アドレス
	 */
	public static MailAddress of (String address, String name) {

		return new MailAddress(address, name);

	}

	/**
	 * アドレスの形を確かめて、ドメインを ASCII（Punycode・小文字）にする
	 */
	private static String normalize (String address) {

		if (address == null || address.isBlank()) {
			throw new MailException("メールアドレスがありません");
		}

		String value = address.strip();
		int at = value.lastIndexOf('@');

		if (at <= 0 || at == value.length() - 1) {
			throw new MailException("メールアドレスの形ではありません: " + printable(value));
		}

		String local = value.substring(0, at);
		String domain = value.substring(at + 1);

		/*
		 * <b>ローカル部は、引用符を使わない形（dot-atom）だけ</b>を受け付ける。
		 * 改行・空白・山括弧・引用符・カンマなどが入らないので、ヘッダにもエンベロープにもそのまま書ける
		 */
		if (local.length() > MAX_LOCAL || !local.matches("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*")) {
			throw new MailException("メールアドレスのローカル部が使えない形です: " + printable(value));
		}

		String ascii;

		try {
			ascii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
		} catch (IllegalArgumentException ex) {
			throw new MailException("メールアドレスのドメインが使えない形です: " + printable(value), ex);
		}

		if (!ascii.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")) {
			throw new MailException("メールアドレスのドメインが使えない形です: " + printable(value));
		}

		String result = local + "@" + ascii;

		if (result.length() > MAX_ADDRESS) {
			throw new MailException("メールアドレスが長すぎます: " + printable(value));
		}

		return result;

	}

	/**
	 * ログやメッセージに出すため、改行を見える形にする
	 */
	private static String printable (String value) {

		return value.replace("\r", "\\r").replace("\n", "\\n");

	}

	@Override
	public String toString () {

		return name.isEmpty() ? address : "%s <%s>".formatted(name, address);

	}

}
