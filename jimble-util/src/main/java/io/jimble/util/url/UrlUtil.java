package io.jimble.util.url;

import io.jimble.util.internal.charset.CharDetecter;

import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URLユーティリティ
 */
public class UrlUtil {

	/**
	 * URLのホストがIPアドレスか判定する
	 *
	 * @param url   URL
	 * @return  IPアドレスの場合 = true
	 */
	public static boolean isIPUrl (String url) {

		try {

			URI uri = new URI(url);

			String host = uri.getHost();

			if (host == null) {
				return false;
			}

			/*
			 * <b>IPv6 は角かっこ付きで返ってくる</b>（http://[::1]/ なら "[::1]"）。
			 * 外さないと IPv6 が一度も当たらない。<b>ここは実際に当たっていなかった</b>（要件 D-121）
			 */
			if (host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
				host = host.substring(1, host.length() - 1);
			}

			return IpAddress.isV4(host) || IpAddress.isV6(host);

		} catch (Exception ex) {

			return false;

		}

	}

	// region PunycodeのURLを戻す

	/**
	 * PunycodeのURLを戻す
	 *
	 * @param url	URL
	 * @return	URL
	 */
	public static String punycodeToUrl (String url) {

		if (!url.startsWith("http")) {
			return url;
		}

		String scheme = "";
		String basic = "";
		String domain = "";
		String port = "";
		String other = "";

		{
			int index = url.indexOf("://");
			scheme = url.substring(0, index + 3);
		}
		{
			int index = url.indexOf("/", scheme.length());
			if (index > -1) {
				other = url.substring(index);
			}
		}
		{
			String host = url.substring(scheme.length(), url.length() - other.length());
			int index = host.indexOf("/");
			if (index > -1) {
				host = host.substring(0, index);
			}
			index = host.indexOf('@');
			if (index > -1) {
				basic = host.substring(0, index + 1);
			}
			index = host.indexOf(':', basic.length());
			if (index > -1) {
				port = host.substring(index);
			}
			domain = host.substring(basic.length(), host.length() - port.length());
		}

		domain = punycodeToDomain(domain);

		return scheme + basic + domain + port + other;

	}

	// endregion

	// region Punycodeのドメインを戻す

	/**
	 * Punycodeのドメインを戻す
	 *
	 * @param domain	ドメイン
	 * @return	ドメイン
	 */
	public static String punycodeToDomain (String domain) {

		try {

			return IDN.toUnicode(domain);

		} catch (Exception ex) {

			return domain;

		}

	}

	// endregion

	// region URLをPunycodeに変換する

	/**
	 * URLをPunycodeに変換する
	 *
	 * @param url	URL
	 * @return	URL
	 */
	public static String urlToPunycode (String url) {

		if (!url.startsWith("http")) {
			return url;
		}

		String scheme = "";
		String basic = "";
		String domain = "";
		String port = "";
		String other = "";

		{
			int index = url.indexOf("://");
			scheme = url.substring(0, index + 3);
		}
		{
			int index = url.indexOf("/", scheme.length());
			if (index > -1) {
				other = url.substring(index);
			}
		}
		{
			String host = url.substring(scheme.length(), url.length() - other.length());
			int index = host.indexOf("/");
			if (index > -1) {
				host = host.substring(0, index);
			}
			index = host.indexOf('@');
			if (index > -1) {
				basic = host.substring(0, index + 1);
			}
			index = host.indexOf(':', basic.length());
			if (index > -1) {
				port = host.substring(index);
			}
			domain = host.substring(basic.length(), host.length() - port.length());
		}

		domain = domainToPunycode(domain);

		return scheme + basic + domain + port + other;

	}

	// endregion

	// region ドメインをPunycodeに変換する

	/**
	 * ドメインをPunycodeに変換する.
	 *
	 * @param domain	ドメイン
	 * @return	Punycode
	 */
	public static String domainToPunycode (String domain) {

		try {

			return IDN.toASCII(domain);

		} catch (Exception ex) {

			return domain;

		}

	}

	// endregion

	// region URLからドメインを取得する

	/**
	 * URLからドメインを取得する
	 *
	 * @param url	URL
	 * @return	ドメイン
	 */
	public static String getDomain (String url) {

		try {

			String scheme = "";
			String basic = "";
			String domain = "";
			String port = "";

			{
				int index = url.indexOf("://");
				scheme = url.substring(0, index + 3);
			}
			{
				String host = url.substring(scheme.length());
				int index = host.indexOf("/");
				if (index > -1) {
					host = host.substring(0, index);
				}
				index = host.indexOf('@');
				if (index > -1) {
					basic = host.substring(0, index + 1);
				}
				index = host.indexOf(':', basic.length());
				if (index > -1) {
					port = host.substring(index);
				}
				domain = host.substring(basic.length(), host.length() - port.length());
			}

			return domain;

		} catch (Exception ex) {

			return "";

		}

	}

	// endregion

	// region URLからwwwなしドメインを取得する

	/**
	 * URLからwwwなしドメインを取得する
	 *
	 * @param url	URL
	 * @return	wwwなしドメイン
	 */
	public static String getDomainNoWWW (String url) {

		String host = getDomain(url);

		if (host.toLowerCase().startsWith("www.")) {
			host = host.substring(4);
		}

		return host;

	}

	// endregion

	// region URLからルートドメインを取得する

	/**
	 * URLからルートドメインを取得する
	 *
	 * @param url	URL
	 * @return	ルートドメイン
	 */
	public static String getRootDomain (String url) {

		String domain = getDomain(url);
		try {

			String top = PublicSuffix.topDomainUnderRegistrySuffix(domain);
			return top == null ? domain : top;

		} catch (Exception ex) {

			return domain;

		}

	}

	// endregion

	// region URLからドメインのレジストラサフィックスを取得する

	/**
	 * URLからドメインのレジストラサフィックスを取得する
	 * 例）google.co.jp > co.jp
	 *
	 * @param url	URL
	 * @return	ドメインのレジストラサフィックス
	 */
	public static String getDomainRegistrySuffix (String url) {

		String domain = getDomain(url);
		try {

			String suffix = PublicSuffix.registrySuffix(domain);
			return suffix == null ? domain : suffix;

		} catch (Exception ex) {

			return domain;

		}

	}

	// endregion

	// region URLを送れる形に整える

	/**
	 * URL の分解（RFC 3986 付録B）
	 *
	 * <p>
	 * scheme / authority / path / query / fragment の5つに割る。
	 * <b>{@code java.net.URL} にも {@code java.net.URI} にも解析させない。</b>
	 * {@code URL} は非推奨で、{@code URI} は<b>直したい入力を入口で落とす</b>
	 * （空白・{@code |}・生の日本語）。
	 * </p>
	 */
	private static final Pattern URL_PARTS = Pattern.compile(
		"^(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$");

	/** {@code authority} の分解（{@code [userinfo@]host[:port]}） */
	private static final Pattern AUTHORITY_PARTS = Pattern.compile(
		"^(?:([^@]*)@)?(\\[[^\\]]*\\]|[^:]*)(?::(\\d*))?$");

	/** どの部分でもそのまま置ける文字（RFC 3986 の unreserved + sub-delims） */
	private static final String UNRESERVED_SUB_DELIMS =
		"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=";

	/** パスに置ける文字 */
	private static final String PATH_SAFE = UNRESERVED_SUB_DELIMS + ":@/";

	/** クエリと素片に置ける文字 */
	private static final String QUERY_SAFE = UNRESERVED_SUB_DELIMS + ":@/?";

	/** userinfo に置ける文字 */
	private static final String USERINFO_SAFE = UNRESERVED_SUB_DELIMS + ":";

	/** 16進 */
	private static final String HEX = "0123456789ABCDEF";

	/**
	 * URL を送れる形に整える
	 *
	 * <p>
	 * <b>未エンコードの URL でも、エンコード済みの URL でも、同じ答えになる。</b>
	 * 2回通しても結果は変わらない（べき等）。
	 * どちらを渡されるか呼ぶ側が分からないところで使う。
	 * </p>
	 *
	 * <pre>
	 * https://example.com/a b/c?q=x y  →  https://example.com/a%20b/c?q=x%20y
	 * https://example.com/a%20b/c      →  https://example.com/a%20b/c   （そのまま）
	 * https://例え.com/あ               →  https://xn--r8jz45g.com/%E3%81%82
	 * https://example.com/100%         →  https://example.com/100%25
	 * </pre>
	 *
	 * <h4>どうやってべき等にしているか</h4>
	 * <p>
	 * <b>{@code %} のあとに16進が2桁続いていたら、そこは「もうエンコードしてある」とみなして触らない。</b>
	 * 続いていなければ {@code %25} にする。
	 * {@code %25} はもう「16進2桁が続いている」形なので、2回目以降は触られない。
	 * </p>
	 * <p>
	 * <b>この読み替えは当てずっぽうではないが、万能でもない。</b>
	 * 「{@code %20} という<b>文字列そのもの</b>をパスに入れたい」場合だけは区別できない
	 * （エンコード済みとみなして素通しする）。
	 * その用途があるなら、この関数を通さずに組み立てること。
	 * </p>
	 *
	 * <h4>ホストは punycode にする</h4>
	 * <p>
	 * <b>ホスト名をパーセントエンコードしてはいけない。</b>
	 * DNS はそれを引けない。{@code 例え.com} は {@code xn--r8jz45g.com} にする。
	 * </p>
	 *
	 * <h4>整えられなかったら、渡されたものをそのまま返す</h4>
	 * <p>
	 * 例外にしない。URL を整える処理で落ちると、
	 * <b>本来の失敗（繋がらない、404）より前で止まって</b>原因が見えなくなる。
	 * </p>
	 *
	 * @param url	URL（未エンコードでもエンコード済みでもよい）
	 * @return	整えた URL。整えられなければ渡されたもの
	 */
	public static String normalizeUrl (String url) {

		if (url == null || url.isEmpty()) {
			return url;
		}

		try {

			Matcher parts = URL_PARTS.matcher(url);

			if (!parts.matches()) {
				return url;
			}

			String scheme = parts.group(1);
			String authority = parts.group(2);
			String path = parts.group(3);
			String query = parts.group(4);
			String fragment = parts.group(5);

			StringBuilder sb = new StringBuilder();

			if (scheme != null) {
				sb.append(scheme.toLowerCase()).append(':');
			}

			if (authority != null) {
				sb.append("//").append(normalizeAuthority(authority));
			}

			sb.append(encodePart(path, PATH_SAFE));

			if (query != null) {
				sb.append('?').append(encodePart(query, QUERY_SAFE));
			}

			if (fragment != null) {
				sb.append('#').append(encodePart(fragment, QUERY_SAFE));
			}

			return sb.toString();

		} catch (Exception ex) {

			return url;

		}

	}

	/**
	 * {@code authority} を整える
	 *
	 * @param authority	{@code [userinfo@]host[:port]}
	 * @return	整えたもの
	 */
	private static String normalizeAuthority (String authority) {

		Matcher m = AUTHORITY_PARTS.matcher(authority);

		if (!m.matches()) {
			return authority;
		}

		String userinfo = m.group(1);
		String host = m.group(2);
		String port = m.group(3);

		StringBuilder sb = new StringBuilder();

		if (userinfo != null) {
			sb.append(encodePart(userinfo, USERINFO_SAFE)).append('@');
		}

		sb.append(toAsciiHost(host));

		if (port != null && !port.isEmpty()) {
			sb.append(':').append(port);
		}

		return sb.toString();

	}

	/**
	 * ホストを ASCII にする
	 *
	 * <p>IPv6（{@code [::1]}）はそのまま。</p>
	 *
	 * @param host	ホスト
	 * @return	ASCII のホスト
	 */
	private static String toAsciiHost (String host) {

		if (host == null || host.isEmpty() || host.startsWith("[")) {
			return host;
		}

		try {
			return IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase();
		} catch (Exception ex) {
			// 引けない名前でも、ここで落として URL ごと捨てるほどではない
			return host.toLowerCase();
		}

	}

	/**
	 * 1つの部分をパーセントエンコードする
	 *
	 * <p>
	 * <b>すでに {@code %XX} になっているところは触らない</b>（べき等にするため）。
	 * 16進は大文字に揃える（{@code %e3} と {@code %E3} で答えが変わらないように）。
	 * </p>
	 *
	 * @param value	部分
	 * @param safe	そのまま置いてよい文字
	 * @return	エンコードしたもの
	 */
	private static String encodePart (String value, String safe) {

		if (value == null || value.isEmpty()) {
			return value;
		}

		StringBuilder sb = new StringBuilder(value.length() + 16);

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);

			if (c == '%' && i + 2 < value.length()
				&& isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {

				sb.append('%')
					.append(Character.toUpperCase(value.charAt(i + 1)))
					.append(Character.toUpperCase(value.charAt(i + 2)));
				i += 2;
				continue;

			}

			if (c < 0x80 && safe.indexOf(c) >= 0) {
				sb.append(c);
				continue;
			}

			// ここだけ UTF-8 のバイト列にして %XX にする（サロゲートペアも1文字として拾う）
			int codePoint = value.codePointAt(i);
			i += Character.charCount(codePoint) - 1;

			for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
				sb.append('%')
					.append(HEX.charAt((b >> 4) & 0x0F))
					.append(HEX.charAt(b & 0x0F));
			}

		}

		return sb.toString();

	}

	/**
	 * 16進の1桁か
	 *
	 * @param c	文字
	 * @return	16進なら true
	 */
	private static boolean isHex (char c) {

		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');

	}

	// endregion

	// region URLをエンコードする

	/**
	 * URLをエンコードする
	 *
	 * <p>
	 * <b>新しく書くなら {@link #normalizeUrl(String)} を使うこと。</b>
	 * こちらは移送元から持ってきたもので、2つ困ったところがある。
	 * </p>
	 * <ul>
	 *   <li><b>パスの空白を {@code +} にする</b>（{@code /a b} → {@code /a+b}）。
	 *       {@code +} が空白を表すのは<b>フォームの書式だけ</b>で、
	 *       パスの {@code +} はプラス記号である。サーバーは {@code a+b} という名前を探す</li>
	 *   <li><b>いったんデコードしてから組み立て直す</b>ので、
	 *       {@code %2B} と {@code +} のような<b>「元がどちらだったか」が消える</b></li>
	 * </ul>
	 * <p>ホストを punycode にするところは正しい（{@code fullUrlEncode} はここを間違えている）。</p>
	 *
	 * @param urlString	URL
	 * @return	URL
	 */
	public static String urlToEncodeUrl (String urlString) {

		return urlToEncodeUrl(urlString, "UTF-8");

	}

	/**
	 * URLをエンコードする
	 *
	 * @param urlString	URL
	 * @param charset	文字コード
	 * @return	URL
	 */
	public static String urlToEncodeUrl (String urlString, String charset) {

		String _urlString = urlString;

		try {

			// ハッシュ
			String urlHash = "";
			int index = _urlString.indexOf('#');
			if (index > -1) {
				urlHash = _urlString.substring(index + 1);
				_urlString = _urlString.substring(0, index);
				urlHash = "#" + UrlUtil.urlEncode(urlHash, charset, true);
			}

			// クエリ
			String urlQuery = "";
			index = _urlString.indexOf('?');
			if (index > -1) {
				urlQuery = _urlString.substring(index + 1);
				_urlString = _urlString.substring(0, index);
				String[] queries = urlQuery.split(Pattern.quote("&"), -1);

				StringBuilder sbQuery = new StringBuilder();
				for (int i = 0; i < queries.length; i++) {
					if (i > 0) {
						sbQuery.append("&");
					}
					int _index = queries[i].indexOf('=');
					if (_index > 0) {
						sbQuery.append(UrlUtil.urlEncode(queries[i].substring(0, _index), charset, true));
						sbQuery.append("=");
						if (queries[i].length() > _index + 1) {
							sbQuery.append(UrlUtil.urlEncode(queries[i].substring(_index + 1), charset, true));
						}
					} else {
						sbQuery.append(UrlUtil.urlEncode(queries[i], charset, true));
					}
				}
				urlQuery = "?" + sbQuery.toString();
			}

			// パス
			String urlPath = "";
			index = _urlString.indexOf("://");
			int index2 = _urlString.indexOf("/", index + 3);
			if (index2 > -1) {
				urlPath = _urlString.substring(index2 + 1);
				_urlString = _urlString.substring(0, index2);

				String[] paths = urlPath.split(Pattern.quote("/"), -1);

				StringBuilder sbPath = new StringBuilder();
				for (int i = 0; i < paths.length; i++) {
					if (i > 0) {
						sbPath.append("/");
					}
					if (paths[i].length() > 0) {
						sbPath.append(UrlUtil.urlEncode(paths[i], charset, true));
					}
				}
				urlPath = "/" + sbPath.toString();
			}

			return UrlUtil.urlToPunycode(_urlString + urlPath + urlQuery + urlHash);

		} catch (Exception ex) {

			return urlString;

		}

	}

	// endregion

	/**
	 * URL の中の使えない文字をエンコードする
	 *
	 * <p>
	 * <b>まだエンコードされていない URL を渡すこと。</b>
	 * 空白や日本語のように、そのままでは送れない文字を {@code %XX} に直す。
	 * 組み立て直せなかったときは<b>渡されたものをそのまま返す</b>（例外にしない）。
	 * </p>
	 *
	 * <pre>
	 * https://example.com/a b/c?q=x y  →  https://example.com/a%20b/c?q=x%20y
	 * https://例え.com/あ?q=あ          →  https://%E4%BE%8B%E3%81%88.com/%E3%81%82?q=%E3%81%82
	 * </pre>
	 *
	 * <h4>エンコード済みの URL を渡してはいけない</h4>
	 * <p>
	 * <b>{@code %} がもう一度エンコードされて二重になる。</b>
	 * </p>
	 * <pre>
	 * https://example.com/%E3%81%82  →  https://example.com/%25E3%2581%2582
	 * </pre>
	 * <p>
	 * 「もうエンコードしてあるか」は文字列からは決められない
	 * （{@code %25} を<b>そう書きたかった</b>場合と区別が付かない）ので、
	 * 直していない。<b>呼ぶ側が、生の URL だけを渡すこと。</b>
	 * </p>
	 *
	 * <h4>{@code new URL(String)} を使っている理由</h4>
	 * <p>
	 * 非推奨で、代わりに {@code URI.create(...).toURL()} が案内される。
	 * <b>そのままでは置き換えられない。</b>
	 * {@code URI} は RFC どおりに厳しく、
	 * <b>このメソッドが直したい入力（空白・{@code |}・生の日本語）を
	 * 入口で落とす</b>。{@code URL} の解析だけが緩い。
	 * {@code URL.of(URI, handler)}（Java 20 以降）も内部で {@code URI} を要るので同じである。
	 * </p>
	 * <p>
	 * <b>置き換えるなら、解析を {@code URL} に任せない。</b>
	 * RFC 3986 付録B の正規表現
	 * （{@code ^(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?$}）で
	 * scheme / authority / path / query / fragment に割り、
	 * 多引数の {@code URI} コンストラクタに渡してエンコードさせればよい。
	 * 確かめた範囲では出力は変わらず、
	 * <b>いま例外で諦めている相対パスと {@code mailto:} も通るようになる</b>。
	 * 二重エンコードだけはそれでも残る。
	 * </p>
	 * <p>
	 * <b>いまは誰も呼んでいない</b>（{@code jooby_base} から移送しただけで、
	 * jimble にも移送元のアプリにも呼び出しは無い）。
	 * </p>
	 * <p>
	 * <b>新しく書くなら {@link #normalizeUrl(String)} を使うこと。</b>
	 * ここに書いた3つ（二重エンコード・ホストのパーセントエンコード・
	 * 相対パスと {@code mailto:} で諦める）を全部直してある。
	 * </p>
	 *
	 * @param url	URL（未エンコード）
	 * @return	エンコードした URL。組み立て直せなければ渡されたものをそのまま
	 */
	@SuppressWarnings("deprecation")
	public static String fullUrlEncode (String url) {

		try {
			URL urlObject = new URL(url);
			URI uri = new URI(
				urlObject.getProtocol()
				, urlObject.getAuthority()
				, urlObject.getPath()
				, urlObject.getQuery()
				, urlObject.getRef()
			);

			return uri.toASCIIString();
		} catch (Exception ex) {
			return url;
		}

	}

	// region 文字列をURLエンコードする

	/**
	 * 文字列をURLエンコードする
	 *
	 * @param value	文字列
	 * @return	文字列
	 */
	public static String urlEncode (String value) {

		return urlEncode(value, "UTF-8");

	}

	/**
	 * 文字列をURLエンコードする
	 *
	 * @param value		文字列
	 * @param charset	文字コード
	 * @return	文字列
	 */
	public static String urlEncode (String value, String charset) {

		return urlEncode(value, charset, false);

	}

	/**
	 * 文字列をURLエンコードする
	 *
	 * @param value			文字列
	 * @param charset		文字コード
	 * @param beforeDecode	事前処理としてデコードする場合 = true
	 * @return	文字列
	 */
	public static String urlEncode (String value, String charset, boolean beforeDecode) {

		return urlEncode(value, charset, beforeDecode, null);

	}

	/**
	 * 文字列をURLエンコードする
	 *
	 * @param value			文字列
	 * @param charset		文字コード
	 * @param beforeDecode	事前処理としてデコードする場合 = true
	 * @param srcCharset	URLエンコードされた文字列の文字コード
	 * @return	文字列
	 */
	public static String urlEncode (String value, String charset, boolean beforeDecode, String srcCharset) {

		try {

			if (beforeDecode) {
				if (srcCharset == null || srcCharset.isEmpty()) {
					String _srcCharset = CharDetecter.detectorUrlEncodeString(value, charset);
					return URLEncoder.encode(URLDecoder.decode(value, _srcCharset), charset);
				}
				return URLEncoder.encode(URLDecoder.decode(value, srcCharset), charset);
			}

			return URLEncoder.encode(value, charset);

		} catch (Exception ex) {

			return value;

		}

	}

	// endregion

	// region 文字列をURLデコードする

	/**
	 * 文字列をURLデコードする
	 *
	 * @param value	文字列
	 * @return	文字列
	 */
	public static String urlDecode (String value) {

		return urlDecode(value, "UTF-8");

	}

	/**
	 * 文字列をURLデコードする
	 *
	 * @param value		文字列
	 * @param charset	文字コード
	 * @return	文字列
	 */
	public static String urlDecode (String value, String charset) {

		try {

			return URLDecoder.decode(value, charset);

		} catch (Exception ex) {

			return value;

		}

	}

	// endregion

	/**
	 * クエリを解析する
	 *
	 * @param url     URL
	 * @param charset 文字コード
	 * @return 結果
	 */
	public static List<String[]> parseQuery (String url, String charset) {

		List<String[]> result = new ArrayList<>();
		if (!url.contains("?")) {
			return result;
		}

		String encodeUrl = urlToEncodeUrl(urlDecode(url, charset), charset);

		String query = encodeUrl.substring(encodeUrl.indexOf('?') + 1);
		String[] queries = query.split("&");
		for (String q : queries) {

			int index = q.indexOf('=');
			if (index <= 0) {
				continue;
			}

			String[] pair = new String[]{
				urlDecode(q.substring(0, index), charset)
				, urlDecode(q.substring(index + 1), charset)
			};

			result.add(pair);

		}

		return result;

	}

	public static String addParam (String url, String paramName, String paramValue) {

		if (!url.startsWith("http:") && !url.startsWith("https:")) {
			return url;
		}

		if (paramName == null || paramName.isEmpty()) {
			return url;
		}

		try {

			if (paramValue == null) {
				paramValue = "";
			}

			URI oldUri = new URI(url);

			String newQuery = oldUri.getQuery();
			if (newQuery == null) {
				newQuery = paramName + "=" + paramValue;
			} else {
				newQuery += "&" + paramName + "=" + paramValue;
			}

			return new URI(oldUri.getScheme(), oldUri.getAuthority(), oldUri.getPath(), newQuery, oldUri.getFragment()).toString();

		} catch (Exception ex) {

			return url;

		}

	}

}
