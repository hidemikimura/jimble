package io.jimble.util.url;

import com.google.common.net.InternetDomainName;
import io.jimble.util.charset.CharDetecter;
import org.apache.commons.validator.routines.InetAddressValidator;

import java.net.*;
import java.util.ArrayList;
import java.util.List;
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

			InetAddressValidator validator = InetAddressValidator.getInstance();

			if (validator.isValidInet4Address(uri.getHost())
				|| validator.isValidInet6Address(uri.getHost())) {
				return true;
			}

			return false;

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

			InternetDomainName domainName = InternetDomainName.from(domain);
			return domainName.topDomainUnderRegistrySuffix().toString();

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

			InternetDomainName domainName = InternetDomainName.from(domain);
			return domainName.registrySuffix().toString();

		} catch (Exception ex) {

			return domain;

		}

	}

	// endregion

	// region URLをエンコードする

	/**
	 * URLをエンコードする
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
