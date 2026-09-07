package io.jimble.gradle.run;

import java.util.Locale;
import java.util.Set;

/**
 * 転送してはいけないヘッダ
 *
 * <p>
 * RFC 9110 の hop-by-hop ヘッダ。<b>1つ先の相手との約束</b>であって、
 * その先へ持っていくと食い違う。
 * </p>
 *
 * <p>
 * {@code Content-Length} と {@code Host} もここで落とす。
 * どちらも {@code HttpClient} と {@code HttpServer} が自分で付け直すもので、
 * <b>元の値を持っていくと本文の長さが合わなくなる。</b>
 * </p>
 */
final class HopByHopHeaders {

	/** 転送しないもの */
	private static final Set<String> BLOCKED = Set.of(
		"connection"
		, "keep-alive"
		, "proxy-authenticate"
		, "proxy-authorization"
		, "te"
		, "trailer"
		, "transfer-encoding"
		, "upgrade"
		, "host"
		, "content-length"
		, "expect"
	);

	private HopByHopHeaders () {}

	/**
	 * 転送してよいか
	 *
	 * @param name	ヘッダ名
	 * @return	転送してよい場合 = true
	 */
	static boolean isForwardable (String name) {

		return name != null && !BLOCKED.contains(name.toLowerCase(Locale.ROOT));

	}

}
