package io.jimble.web.request;

import io.jimble.util.conf.Conf;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * プロキシの後ろでのクライアントの IP（D-214）
 *
 * <pre>
 * server {
 *   trust_proxy      = true
 *   trusted_proxies  = ["10.0.0.0/8", "192.168.1.10"]   # 中継の IP / CIDR。書けば、ここから来たときだけヘッダを見る
 *   client_ip_header = ""                                # "CF-Connecting-IP" / "X-Real-IP" など。書いたときだけ信じる
 * }
 * </pre>
 *
 * <h2>2.2.2 までの問題</h2>
 * <ul>
 *   <li><b>{@code X-Forwarded-For} の左端</b>をクライアントとしていた。nginx・ALB・jimble の ReverseProxy は、
 *       クライアントが送ってきた値の<b>後ろに足す</b>ので、左端はクライアントが好きに名乗れる</li>
 *   <li>{@code CF-Connecting-IP} と {@code X-Real-IP} を、Cloudflare や nginx の後ろでなくても信じていた</li>
 * </ul>
 * <p>どちらも、名乗る値を毎回変えるだけで、IP ごとのレート制限や IP での制限をすり抜けられた。</p>
 *
 * <h2>いまの決め方</h2>
 * <ol>
 *   <li>{@code trusted_proxies} を書いていて、接続元がそこに入っていなければ、ヘッダは見ない（接続元を返す）</li>
 *   <li>{@code client_ip_header} を書いていれば、その値</li>
 *   <li>{@code X-Forwarded-For} を<b>右から</b>見て、{@code trusted_proxies} に入っていない最初のもの
 *       （書いていなければ右端。直前の中継が足した値）</li>
 * </ol>
 */
final class ClientIp {

	/** 設定キー：中継の IP / CIDR */
	static final String KEY_TRUSTED_PROXIES = "server.trusted_proxies";

	/** 設定キー：クライアントの IP を入れてくるヘッダ */
	static final String KEY_CLIENT_IP_HEADER = "server.client_ip_header";

	/* 設定を読んだもの（設定が入れ替わったら読み直す。リクエストごとに読むため） */
	private static volatile Cached cached;

	private record Cached (Conf from, List<Cidr> trusted, String header) {}

	private ClientIp () {}

	/**
	 * クライアントの IP を決める
	 *
	 * @param remote		接続元
	 * @param forwardedFor	X-Forwarded-For（無ければ空）
	 * @param headerValue	{@code client_ip_header} のヘッダの値を引く
	 * @return	IP
	 */
	static String resolve (String remote, String forwardedFor, java.util.function.Function<String, String> headerValue) {

		Cached conf = conf();

		if (!conf.trusted().isEmpty() && !contains(conf.trusted(), remote)) {
			// 直に来た相手が中継でなければ、ヘッダは名乗りにすぎない
			return remote;
		}

		if (!conf.header().isEmpty()) {
			String value = headerValue.apply(conf.header());
			if (value != null && !value.isBlank()) {
				int comma = value.indexOf(',');
				return (comma < 0 ? value : value.substring(0, comma)).trim();
			}
		}

		if (forwardedFor != null && !forwardedFor.isBlank()) {

			String[] hops = forwardedFor.split(",");

			for (int i = hops.length - 1; i >= 0; i--) {
				String hop = hops[i].trim();
				if (hop.isEmpty()) {
					continue;
				}
				if (conf.trusted().isEmpty() || !contains(conf.trusted(), hop) || i == 0) {
					return hop;
				}
			}

		}

		return remote;

	}

	private static Cached conf () {

		Conf current = Conf.conf();
		Cached snapshot = cached;

		if (snapshot != null && snapshot.from() == current) {
			return snapshot;
		}

		List<Cidr> trusted = new ArrayList<>();

		if (current.has(KEY_TRUSTED_PROXIES)) {
			for (String value : current.config().getStringList(KEY_TRUSTED_PROXIES)) {
				trusted.add(Cidr.parse(value));
			}
		}

		Cached fresh = new Cached(current, List.copyOf(trusted)
			, current.getString(KEY_CLIENT_IP_HEADER, "").trim().toLowerCase(Locale.ROOT));
		cached = fresh;

		return fresh;

	}

	private static boolean contains (List<Cidr> list, String ip) {

		InetAddress address = Cidr.literal(ip);

		if (address == null) {
			return false;
		}

		for (Cidr cidr : list) {
			if (cidr.contains(address)) {
				return true;
			}
		}

		return false;

	}

	/**
	 * IP の範囲
	 *
	 * @param network	先頭
	 * @param bits		前から何ビットを比べるか
	 */
	record Cidr (byte[] network, int bits) {

		/**
		 * 読む（{@code 10.0.0.0/8}、{@code ::1}、{@code 192.168.1.10}）
		 *
		 * @param value	値
		 * @return	範囲
		 * @throws IllegalStateException	読めない場合
		 */
		static Cidr parse (String value) {

			String text = value.trim();
			int slash = text.indexOf('/');

			InetAddress address = literal(slash < 0 ? text : text.substring(0, slash));

			if (address == null) {
				throw new IllegalStateException("%s に IP / CIDR でない値があります: %s".formatted(KEY_TRUSTED_PROXIES, value));
			}

			int max = address.getAddress().length * 8;
			int bits = slash < 0 ? max : Integer.parseInt(text.substring(slash + 1));

			if (bits < 0 || bits > max) {
				throw new IllegalStateException("%s の範囲が読めません: %s".formatted(KEY_TRUSTED_PROXIES, value));
			}

			return new Cidr(address.getAddress(), bits);

		}

		/**
		 * IP の字面だけを読む（<b>名前は引かない</b>。DNS を引くと、引いた先の IP で許可リストを通れてしまう）
		 *
		 * @param text	字面
		 * @return	アドレス。IP の字面でなければ null
		 */
		static InetAddress literal (String text) {

			if (text == null || text.isBlank()) {
				return null;
			}

			String value = text.trim();

			// [::1] と、IPv4 の :ポート を外す
			if (value.startsWith("[") && value.contains("]")) {
				value = value.substring(1, value.indexOf(']'));
			} else if (value.indexOf(':') > 0 && value.indexOf(':') == value.lastIndexOf(':') && value.indexOf('.') > 0) {
				value = value.substring(0, value.indexOf(':'));
			}

			try {
				return InetAddress.ofLiteral(value);
			} catch (IllegalArgumentException ex) {
				return null;
			}

		}

		boolean contains (InetAddress address) {

			byte[] target = address.getAddress();

			if (target.length != network.length) {
				return false;
			}

			int full = bits / 8;

			for (int i = 0; i < full; i++) {
				if (target[i] != network[i]) {
					return false;
				}
			}

			int rest = bits % 8;

			if (rest == 0) {
				return true;
			}

			int mask = (0xff << (8 - rest)) & 0xff;

			return (target[full] & mask) == (network[full] & mask);

		}

	}

}
