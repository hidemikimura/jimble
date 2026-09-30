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
 * {@code Content-Length} もここで落とす。本文の長さは {@link AppConnection} と {@code HttpServer} が
 * 自分で書き直すもので、<b>元の値を持っていくと本文の長さが合わなくなる。</b>
 * </p>
 *
 * <p>
 * <b>{@code Host} は落とさない</b>——ブラウザが叩いたホストをアプリへ引き継ぐ。
 * かつては {@code HttpClient} が付け直すので落としていたが、それで<b>アプリにはいつも
 * {@code 127.0.0.1:9100} が届いていた</b>（ホストで振り分けるアプリが開発のときだけ食い違う）。
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
