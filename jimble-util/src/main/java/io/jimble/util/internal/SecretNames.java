package io.jimble.util.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 秘密を表す名前と、クエリの伏せ方（D-306）
 *
 * <p>
 * <b>伏せる名前の一覧はここの1つだけ。</b>
 * 以前は HTTP クライアントのログ（D-240）と 422 の入力値（D-291）で別々に持っていて、
 * 片方にしか無い名前（{@code api_key} / {@code pin} など）があった。
 * アクセスログのクエリ（D-306）もここを使う。
 * </p>
 *
 * <ul>
 *   <li>{@link #CONTAINS} は名前に<b>含まれていれば</b>伏せる（{@code access_token}・{@code new_password}）</li>
 *   <li>{@link #EXACT} は名前（入れ子なら段のどれか）が<b>一致するときだけ</b>伏せる。
 *       {@code key} を含むで見ると {@code keyword} まで伏せてしまうため</li>
 * </ul>
 */
public final class SecretNames {

	/** 伏せた値 */
	public static final String MASKED = "***";

	/* 含まれていれば伏せる（小文字） */
	private static final List<String> CONTAINS = List.of(
		"password", "passwd", "secret", "token",
		"api_key", "api-key", "apikey", "card_number", "cardnumber"
	);

	/* 一致すれば伏せる（小文字） */
	private static final Set<String> EXACT = Set.of(
		"authorization", "proxy-authorization", "cookie", "set-cookie",
		"key", "sig", "signature", "cvc", "cvv", "pin", "otp", "code_verifier"
	);

	private SecretNames () {}

	/**
	 * 秘密を表す名前か
	 *
	 * @param name	名前（ヘッダ名・パラメータ名。{@code a[b]} / {@code a.b} の入れ子も可）
	 * @return	伏せるなら true
	 */
	public static boolean isSecret (String name) {

		return isSecret(name, Set.of());

	}

	/**
	 * 秘密を表す名前か（アプリが足した名前も見る）
	 *
	 * @param name	名前
	 * @param extra	足した名前（小文字）。名前か入れ子の段のどれかが一致したら伏せる
	 * @return	伏せるなら true
	 */
	public static boolean isSecret (String name, Set<String> extra) {

		if (name == null || name.isEmpty()) {
			return false;
		}

		String lower = name.toLowerCase(Locale.ROOT);

		for (String part : CONTAINS) {
			if (lower.contains(part)) {
				return true;
			}
		}

		// 入れ子（a[b][c] / a.b）は段ごとに見る。段が無ければ名前そのもの
		int start = 0;
		for (int i = 0; i <= lower.length(); i++) {
			if (i < lower.length() && !isSeparator(lower.charAt(i))) {
				continue;
			}
			if (i > start) {
				String segment = lower.substring(start, i);
				if (EXACT.contains(segment) || extra.contains(segment)) {
					return true;
				}
			}
			start = i + 1;
		}

		return false;

	}

	/**
	 * クエリ文字列の秘密の値を伏せる
	 *
	 * <p>
	 * <b>キーは残して値だけ {@value #MASKED} にする</b>（どのパラメータが来たかは追えるように）。
	 * 名前は<b>デコードしてから</b>比べる——{@code %74oken=...} ですり抜けさせないため。
	 * 伏せるものが無ければ、渡した文字列をそのまま返す（アクセスログは毎リクエスト通るので、作り直さない）。
	 * </p>
	 *
	 * @param query	クエリ文字列（{@code ?} のあと。エンコードされたまま）
	 * @param extra	足した名前（小文字）
	 * @return	伏せたクエリ文字列
	 */
	public static String maskQuery (String query, Set<String> extra) {

		if (query == null || query.indexOf('=') < 0) {
			return query;
		}

		StringBuilder sb = null;
		int start = 0;

		while (start <= query.length()) {

			int end = query.indexOf('&', start);
			if (end < 0) {
				end = query.length();
			}

			int equals = query.indexOf('=', start);
			if (equals >= 0 && equals < end && isSecret(decodeName(query.substring(start, equals)), extra)) {
				if (sb == null) {
					sb = new StringBuilder(query.length());
					sb.append(query, 0, start);
				}
				sb.append(query, start, equals + 1).append(MASKED);
			} else if (sb != null) {
				sb.append(query, start, end);
			}

			if (end < query.length() && sb != null) {
				sb.append('&');
			}

			start = end + 1;

		}

		return sb == null ? query : sb.toString();

	}

	/**
	 * 名前の区切り（入れ子）か
	 */
	private static boolean isSeparator (char c) {

		return c == '[' || c == ']' || c == '.';

	}

	/**
	 * 名前をデコードする（壊れたエンコードはそのまま）
	 */
	private static String decodeName (String name) {

		if (name.indexOf('%') < 0 && name.indexOf('+') < 0) {
			return name;
		}

		try {
			return URLDecoder.decode(name, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			return name;
		}

	}

}
