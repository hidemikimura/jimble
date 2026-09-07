package io.jimble.web.proxy;

import java.util.Set;

/**
 * 転送してはいけないヘッダ
 *
 * <p>
 * RFC 9110 でいう hop-by-hop ヘッダ。<b>1つの接続の中でだけ意味を持つ</b>ので、
 * 別の接続に持ち越してはいけない。
 * </p>
 *
 * <p>
 * 移送元はリクエストのヘッダを<b>まるごと転送先へ渡し、
 * 転送先の応答ヘッダもまるごとクライアントへ返していた。</b>
 * これで起きること：
 * </p>
 *
 * <ul>
 *   <li>{@code Content-Length} をそのまま返すと、本文の長さが変わったときに<b>応答が壊れる</b></li>
 *   <li>{@code Transfer-Encoding: chunked} を返すと、<b>二重にチャンク化される</b></li>
 *   <li>{@code Host} を渡すと、転送先が名前ベースの仮想ホストのときに<b>別のサイトが返る</b></li>
 *   <li>{@code Connection} を渡すと、転送先との接続の扱いをクライアントが決めることになる</li>
 * </ul>
 */
public final class HopByHopHeaders {

	/** 転送しないヘッダ（小文字） */
	public static final Set<String> NAMES = Set.of(
		"connection"
		, "keep-alive"
		, "proxy-authenticate"
		, "proxy-authorization"
		, "te"
		, "trailer"
		, "transfer-encoding"
		, "upgrade"
		// 本体を張り替えるので、長さと符号化は自分で決める
		, "content-length"
		, "content-encoding"
		// 転送先には転送先向けの Host を送る
		, "host"
		// 送信側が勝手に決めるとおかしくなる
		, "expect"
	);

	private HopByHopHeaders () {}

	/**
	 * 転送してよいか
	 *
	 * @param name	ヘッダ名
	 * @return	転送してよい場合 = true
	 */
	public static boolean isForwardable (String name) {

		return name != null && !NAMES.contains(name.toLowerCase());

	}

}
