package io.jimble.util.http.httpclient;

import io.jimble.util.http.util.HttpProxy;
import io.jimble.util.io.FileCharDetecter;
import io.jimble.util.json.Dson;
import io.jimble.util.parse.Parse;
import io.jimble.util.string.StringUtil;
import io.jimble.util.thread.ThreadUtil;
import io.jimble.util.xml.XmlData;
import io.jimble.util.xml.XmlParser;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import org.brotli.dec.BrotliInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

/**
 * HTTPリクエスト基底
 */
public abstract class AbstractHttpExecutor<E extends AbstractHttpExecutor<E>> {

	// region ログデータ

	/* その他ログ情報 */
	private final Data otherLogData = new Data();

	/**
	 * 自分を返す
	 *
	 * <p>
	 * <b>メソッドチェーンのために、派生クラスの型で自分を返す。</b>
	 * {@code this} が {@code E} であることは型からは証明できないので、
	 * どうしても無検査キャストになる。
	 * <b>キャストはここ1つに閉じる。</b>
	 * 呼ぶところごとに書くと、同じ無検査キャストが 30 か所以上に散り、
	 * <b>本当に危ないキャストが警告の山に埋もれる</b>。
	 * </p>
	 * <p>
	 * 安全である理由は型パラメータの縛り（{@code E extends AbstractHttpExecutor<E>}）と、
	 * <b>派生クラスが自分自身を {@code E} に渡している</b>ことである
	 * （{@code class HttpGetExecutor extends AbstractHttpExecutor<HttpGetExecutor>}）。
	 * 他人の型を渡した派生クラスを作るとここで {@code ClassCastException} になる。
	 * </p>
	 *
	 * @return	自身のインスタンス
	 */
	@SuppressWarnings("unchecked")
	protected final E self () {

		return (E) this;

	}

	/**
	 * その他ログ情報を設定する
	 *
	 * @param key	キー
	 * @param value	値
	 * @return	自身のインスタンス
	 */
	public E putLogData (String key, Object value) {

		otherLogData.put(key, value);
		return self();

	}

	/**
	 * ログデータを取得する
	 *
	 * @return	ログデータ
	 */
	public Data logData () {

		Data res = new Data();

		// リクエスト
		{
			Data request = res.getDataOptional("http_executor_log").getDataOptional("request");
			request.put("version", getVersion());
			request.put("url", getUrl());
			request.put("method", getMethod());
			request.put("headers", getHeaders());
			request.put("charset", getCharset());
		}

		// レスポンス
		{
			Data response = res.getDataOptional("http_executor_log").getDataOptional("response");
			response.put("code", responseCode);
			response.put("headers", getResponseHeader());
			response.put("charset", getResponseCharset());
			response.put("time_ms", getExecuteTime());
		}

		// その他
		res.getDataOptional("http_executor_log").put("other", otherLogData);

		return res;

	}

	// endregion

	// region ローカルアドレス

	/* ローカルアドレス */
	protected InetAddress localAddress = null;

	/**
	 * ローカルアドレスを設定する
	 *
	 * @param ipV4	IP(v4)
	 */
	public E setLocalAddress (String ipV4) {

		try {
			localAddress = InetAddress.getByName(ipV4);
		} catch (Exception ex) {
			Log.error(ex);
		}
		return self();

	}

	/**
	 * ローカルアドレスを設定する
	 *
	 * @param localAddress	ローカルアドレス
	 */
	public E setLocalAddress (InetAddress localAddress) {

		this.localAddress = localAddress;
		return self();

	}

	/**
	 * ローカルアドレスを取得する
	 *
	 * @return	ローカルアドレス
	 */
	public InetAddress getLocalAddress () {

		return this.localAddress;

	}

	// endregion

	// region version

	/* version */
	protected HttpClient.Version version = HttpClient.Version.HTTP_1_1;

	/**
	 * versionを取得する
	 *
	 * @return  version
	 */
	public HttpClient.Version getVersion () {

		return version;

	}

	/**
	 * versionを設定する
	 *
	 * @param version   version
	 */
	public E setVersion (HttpClient.Version version) {

		this.version = version;
		return self();

	}

	// endregion

	// region URL

	/* URL */
	protected String url = null;

	/**
	 * URLを取得する
	 *
	 * @return URL
	 */
	public String getUrl () {

		return url;
	}

	/**
	 * URLを設定する
	 *
	 * @param url URL
	 */
	public E setUrl (String url) {

		this.url = url;
		return self();
	}

	// endregion

	// region メソッド

	/**
	 * メソッドを取得する
	 *
	 * @return  メソッド
	 */
	public abstract String getMethod ();

	// endregion

	// region 文字コード

	/* 文字コード */
	private String charset = "UTF-8";

	/**
	 * 文字コードを取得する
	 *
	 * @return  文字コード
	 */
	public String getCharset () {

		return this.charset;

	}

	/**
	 * 文字コードを設定する
	 *
	 * @param charset 文字コード
	 */
	public E setCharset (String charset) {

		this.charset = charset;
		return self();

	}

	// endregion

	// region リクエストヘッダ

	/* 禁止ヘッダ */
	private static final List<String> NO_SUPPORTED_HEADER = new ArrayList<>();
	static {
		NO_SUPPORTED_HEADER.add("connection");
		NO_SUPPORTED_HEADER.add("content-length");
		NO_SUPPORTED_HEADER.add("expect");
		NO_SUPPORTED_HEADER.add("host");
		NO_SUPPORTED_HEADER.add("upgrade");
		if (Runtime.version().feature() < 16) {
			NO_SUPPORTED_HEADER.add("date");
			NO_SUPPORTED_HEADER.add("from");
			NO_SUPPORTED_HEADER.add("via");
			NO_SUPPORTED_HEADER.add("warning");
		}
	}

	/* リクエストヘッダ */
	protected Map<String, String> headers = new LinkedHashMap<>();

	/**
	 * リクエストヘッダを取得する
	 *
	 * @return リクエストヘッダ
	 */
	public Map<String, String> getHeaders () {

		return headers;
	}

	/**
	 * リクエストヘッダを設定する
	 *
	 * @param headers リクエストヘッダ
	 */
	public E setHeaders (Map<String, String> headers) {

		this.headers = headers;
		return self();
	}

	/**
	 * リクエストヘッダを追加する
	 *
	 * @param key   リクエストヘッダキー
	 * @param value リクエストヘッダ値
	 */
	public E addHeader (String key, String value) {
		headers.put(key, value);
		return self();
	}

	/**
	 * リクエストヘッダを削除する
	 *
	 * @param key   リクエストヘッダキー
	 */
	public E removeHeader (String key) {
		headers.remove(key);
		return self();
	}

	/**
	 * mac chromeのデフォルトヘッダを設定する
	 */
	public E setMacChromeHeader () {
		addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
		addHeader("accept-Encoding", "gzip, deflate, br, zstd");
		addHeader("accept-language", "ja,en-US;q=0.9,en;q=0.8");
		addHeader("cache-control", "0");
		addHeader("priority", "u=0, i");
		addHeader("sec-ch-ua", "\"Chromium\";v=\"136\", \"Google Chrome\";v=\"136\", \"Not.A/Brand\";v=\"99\"");
		addHeader("sec-ch-ua-mobile", "?0");
		addHeader("sec-ch-ua-platform", "\"macOS\"");
		addHeader("sec-fetch-dest", "document");
		addHeader("sec-fetch-mode", "navigate");
		addHeader("sec-fetch-site", "cross-site");
		addHeader("sec-fetch-user", "?1");
		addHeader("upgrade-insecure-requests", "1");
		addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36");
		return self();
	}

	// endregion

	// region Basic認証ヘッダーを追加する

	/**
	 * Basic認証ヘッダーを追加する
	 *
	 * @param id        ID
	 * @param password  パスワード
	 */
	public E addBasicAuthorizationHeader (String id, String password) {

		return addHeader(
			"Authorization"
			, StringUtil.base64Encode(id + ":" + password, Charset.forName(charset))
		);

	}

	// endregion

	// region プロキシ

	/* プロキシ */
	protected HttpProxy proxy = null;

	/**
	 * プロキシを取得する
	 *
	 * @return プロキシ
	 */
	public HttpProxy getProxy () {

		return proxy;
	}

	/**
	 * プロキシを設定する
	 *
	 * @param proxy プロキシ
	 */
	public E setProxy (HttpProxy proxy) {

		this.proxy = proxy;
		return self();
	}

	// endregion

	// region タイムアウト(ms)

	/* タイムアウト(ms) */
	protected long timeout = 30000;

	/**
	 * タイムアウト(ms)を取得する
	 *
	 * @return タイムアウト(ms)
	 */
	public long getTimeout () {

		return timeout;
	}

	/**
	 * タイムアウト(ms)を設定する
	 *
	 * @param timeout タイムアウト(ms)
	 */
	public E setTimeout (long timeout) {

		this.timeout = timeout;
		return self();
	}

	// endregion

	// region レスポンスボディをストリームで保持する

	/* レスポンスボディをストリームで保持する */
	protected boolean isKeepResponseBodyStream = false;

	/**
	 * レスポンスボディをストリームで保持する
	 *
	 * @return	レスポンスボディをストリームで保持する場合 = true
	 */
	public boolean isKeepResponseBodyStream () {

		return isKeepResponseBodyStream;

	}

	/**
	 * レスポンスボディをストリームで保持するかどうか設定する
	 *
	 * @param isKeepResponseBodyStream	レスポンスボディをストリームで保持する場合 = true
	 */
	public E setKeepResponseBodyStream (boolean isKeepResponseBodyStream) {

		this.isKeepResponseBodyStream = isKeepResponseBodyStream;
		return self();

	}

	// endregion

	// region レスポンスボディストリーム

	/* レスポンスボディストリーム */
	private InputStream responseBodyStream = null;

	/**
	 * レスポンスボディストリーム
	 *
	 * @return	レスポンスボディストリーム
	 */
	public InputStream responseBodyStream () {

		return this.responseBodyStream;

	}

	// endregion

	// region リダイレクト許可

	/* リダイレクト許可 */
	protected boolean isEnableRedirect = true;

	/**
	 * リダイレクト許可を取得する
	 *
	 * @return リダイレクト許可
	 */
	public boolean isEnableRedirect () {

		return isEnableRedirect;
	}

	/**
	 * リダイレクト許可を設定する
	 *
	 * @param isEnableRedirect リダイレクト許可
	 */
	public E setEnableRedirect (boolean isEnableRedirect) {

		this.isEnableRedirect = isEnableRedirect;
		return self();
	}

	// endregion

	// region SSLエラー無視

	/* SSLエラー無視 */
	protected boolean isIgnoreSslError = false;

	/**
	 * SSLエラー無視を取得する
	 *
	 * @return SSLエラー無視
	 */
	public boolean isIgnoreSslError () {

		return this.isIgnoreSslError;
	}

	/**
	 * SSLエラー無視を設定する
	 *
	 * @param isIgnoreSslError SSLエラー無視
	 */
	public E setIgnoreSslError (boolean isIgnoreSslError) {

		this.isIgnoreSslError = isIgnoreSslError;
		return self();
	}

	// endregion

	// region スリープ(ms)

	/* スリープ(ms) */
	protected long sleep = 0;

	/**
	 * スリープ(ms)を取得する
	 *
	 * @return スリープ(ms)
	 */
	public long getSleep () {

		return this.sleep;
	}

	/**
	 * スリープ(ms)を設定する
	 *
	 * @param sleep スリープ(ms)
	 */
	public E setSleep (long sleep) {

		this.sleep = sleep;
		return self();
	}

	// endregion

	// region コンテンツ出力ファイル

	/* コンテンツ出力ファイル */
	private File contentOutputFile = null;

	/**
	 * コンテンツ出力ファイルを取得する
	 *
	 * @return コンテンツ出力ファイル
	 */
	public File getContentOutputFile () {

		return this.contentOutputFile;
	}

	/**
	 * コンテンツ出力ファイルを設定する
	 *
	 * @param contentOutputFile コンテンツ出力ファイル
	 */
	public E setContentOutputFile (File contentOutputFile) {

		this.contentOutputFile = contentOutputFile;
		return self();
	}

	// endregion

	// region Cookie利用

	/* Cookie利用 */
	private boolean useCookie = true;

	/**
	 * Cookie利用判定
	 *
	 * @return 利用する場合 = true
	 */
	private boolean isUseCookie () {

		return useCookie;

	}

	/**
	 * Cookie利用を設定する
	 *
	 * @param useCookie 利用する場合 = true
	 */
	public E setUseCookie (boolean useCookie) {

		this.useCookie = useCookie;
		return self();

	}

	// endregion

	// region Cookie管理

	/* Cookie管理 */
	private CookieManager cookieManager = new CookieManager();

	/* 追加Cookie */
	private Map<String, String> requestCookieMap = new LinkedHashMap<>();

	private Map<String, String> requestedCookieMap = new LinkedHashMap<>();

	public Map<String, String> getRequestedCookieMap () {

		return requestedCookieMap;

	}

	/**
	 * Cookie管理を取得する
	 *
	 * @return Cookie管理
	 */
	public CookieManager getCookieManager () {

		return cookieManager;

	}

	/**
	 * Cookie管理を設定する
	 *
	 * @param cookieManager Cookie管理
	 */
	public E setCookieManager (CookieManager cookieManager) {

		this.cookieManager = cookieManager;
		return self();

	}

	/**
	 * Cookieを設定する
	 *
	 * @param key   キー
	 * @param value 値
	 */
	public E addCookie (String key, String value) {

		requestCookieMap.put(key, value);
		return self();

	}

	// endregion

	// region ボディを取得するコンテンツタイプ

	/* ボディを取得するコンテンツタイプ */
	private List<String> enableBodyContentType = new ArrayList<>();

	/**
	 * ボディを取得するコンテンツタイプを追加する
	 *
	 * @param contentType	ボディを取得するコンテンツタイプ
	 */
	public E addEnableBodyContentType (String contentType) {

		this.enableBodyContentType.add(contentType);
		return self();

	}

	// endregion


	// region レスポンスURL

	/* レスポンスURL */
	public String responseUrl = null;

	// endregion

	// region レスポンスコード

	/* レスポンスコード. */
	public int responseCode = 0;

	// endregion

	// region コンテンツ長

	/* コンテンツ長. */
	public long contentLength = 0;

	// endregion

	// region コンテンツバイナリ

	protected byte[] contentBinary = null;

	/**
	 * コンテンツバイナリを取得する
	 *
	 * @return コンテンツバイナリ
	 */
	public byte[] getContentBinary () {

		if (contentOutputFile != null && contentBinary == null) {
			try {
				contentBinary = Files.readAllBytes(contentOutputFile.toPath());
			} catch (Exception ex) {
				return contentBinary;
			}
		}

		return contentBinary;

	}

	// endregion

	// region コンテンツ文字コード

	/**
	 * コンテンツ文字コードを取得する
	 *
	 * @return コンテンツ文字コード
	 */
	public String getContentTextCharset () {

		try {
			return FileCharDetecter.detector(new ByteArrayInputStream(getContentBinary()), "UTF-8");
		} catch (Exception ex) {
			return "UTF-8";
		}

	}

	// endregion

	// region コンテンツテキスト

	private String contentText = null;

	/**
	 * コンテンツテキストを取得する.
	 *
	 * @return コンテンツテキスト
	 */
	public String getContentText () {

		if (contentText != null) {
			return contentText;
		}

		try {
			String charset = getResponseCharset();
			if (charset == null) {
				return getContentText(this.charset);
			}
			return getContentText(charset);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * コンテンツテキストを取得する.
	 *
	 * @param charset 文字コード
	 * @return コンテンツテキスト
	 */
	public String getContentText (String charset) {

		if (contentText != null) {
			return contentText;
		}

		if (charset == null) {
			charset = this.charset;
		}
		if (charset == null) {
			charset = "UTF-8";
		}
		if (charset.isEmpty()) {
			charset = "UTF-8";
		}

		try {
			return new String(getContentBinary(), charset);
		} catch (Exception ex) {
			return "";
		}

	}

	// endregion

	// region コンテンツJSONオブジェクトを取得する

	/**
	 * コンテンツJSONオブジェクトを取得する
	 *
	 * @return コンテンツJSONオブジェクト
	 */
	public Data getContentJson () {

		try {
			String charset = getResponseCharset();
			if (charset == null) {
				return getContentJson(this.charset);
			}
			return getContentJson(charset);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * コンテンツJSONオブジェクトを取得する
	 *
	 * @param charset 文字コード
	 * @return コンテンツJSONオブジェクト
	 */
	public Data getContentJson (String charset) {

		if (charset == null) {
			charset = this.charset;
		}
		if (charset == null) {
			charset = "UTF-8";
		}

		try {
			String jsonString = getContentText(charset);
			return Dson.decodes(jsonString, Data.class);
		} catch (Exception ex) {
			return null;
		}

	}

	// endregion

	// region コンテンツXMLオブジェクトを取得する

	/**
	 * コンテンツXMLオブジェクトを取得する
	 *
	 * @return	コンテンツXMLオブジェクト
	 */
	public XmlData getContentXml () {

		try {
			String charset = getResponseCharset();
			if (charset == null) {
				return getContentXml(this.charset);
			}
			return getContentXml(charset);
		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * コンテンツXMLオブジェクトを取得する
	 *
	 * @param charset 文字コード
	 * @return	コンテンツXMLオブジェクト
	 */
	public XmlData getContentXml (String charset) {

		if (charset == null) {
			charset = this.charset;
		}
		if (charset == null) {
			charset = "UTF-8";
		}

		try {
			String xmlString = getContentText(charset);
			return XmlParser.parse(xmlString);
		} catch (Exception ex) {
			return null;
		}

	}

	// endregion

	// region レスポンスヘッダ

	/* レスポンスヘッダ. */
	private final HashMap<String, List<String>> responseHeader = new HashMap<String, List<String>>();

	/**
	 * レスポンスヘッダを追加する.
	 *
	 * @param key   キー
	 * @param value 値
	 */
	public void addResponseHeader (String key, String value) {

		if (key != null) {
			key = key.toLowerCase();
		}

		List<String> list = responseHeader.get(key);

		if (list == null) {
			list = new ArrayList<String>();
			responseHeader.put(key, list);
		}

		list.add(value);

	}

	/**
	 * レスポンスヘッダを削除する.
	 *
	 * @param key キー
	 */
	public void removeResponseHeader (String key) {

		responseHeader.remove(key);

	}

	/**
	 * レスポンスヘッダをクリアする.
	 */
	public void clearResponseHeader () {

		responseHeader.clear();

	}

	/**
	 * レスポンスヘッダを取得する
	 *
	 * @return レスポンスヘッダ
	 */
	public HashMap<String, List<String>> getResponseHeader () {

		return this.responseHeader;

	}

	/**
	 * レスポンスヘッダを取得する.
	 *
	 * @param key キー
	 * @return 値
	 */
	public String getResponseHeader (String key) {

		if (key != null) {
			key = key.toLowerCase();
		}

		List<String> list = responseHeader.get(key);

		if (list == null || list.size() == 0) {
			return null;
		}

		return list.get(0);

	}

	/**
	 * レスポンスヘッダを取得する.
	 *
	 * @param key キー
	 * @return 値
	 */
	public List<String> getResponseHeaderList (String key) {

		if (key != null) {
			key = key.toLowerCase();
		}

		return responseHeader.get(key);

	}

	// endregion


	// region レスポンスのContent-Typeを取得する

	/**
	 * レスポンスのContent-Typeを取得する
	 *
	 * @return 指定がない場合 = null
	 */
	public String getResponseContentType () {

		String type = getResponseHeader("content-type");
		if (type == null || type.isEmpty()) {
			return this.charset;
		}

		type = type.trim();

		int index = type.indexOf(';');
		if (index < 0) {
			return type;
		}

		return type.substring(0, index);

	}

	// endregion

	// region レスポンスの文字コードを取得する

	/**
	 * レスポンスの文字コードを取得する
	 *
	 * @return 指定がない場合 = null
	 */
	public String getResponseCharset () {

		String type = getResponseHeader("content-type");
		if (type == null || type.isEmpty()) {
			return this.charset;
		}

		type = type.trim();

		int index = type.indexOf("charset=");
		if (index < 0) {
			return this.charset;
		}

		return type.substring(index + 8).trim();

	}

	// endregion

	// region エラー

	/* エラー */
	public boolean isError = false;

	// endregion

	// region エラー内容

	/* エラー内容 */
	public Exception errorException = null;

	// endregion


	// region 実行時間

	private long startTime = 0;

	private long executeTime = 0;

	public long getExecuteTime () {

		return executeTime;

	}

	// endregion


	/**
	 * リクエストビルダーを取得する
	 *
	 * @return リクエストビルダー
	 */
	protected abstract HttpRequest.Builder createRequestBuilder ();

	// region リクエストを実行する

	// region HttpClientキャッシュ

	private static final ReentrantLock lockHttpClient = new ReentrantLock(true);
	private static final Map<String, HttpClient> httpClientCacheMap = new HashMap<>();

	private HttpClient createHttpClient () {

		try {

			StringBuilder key = new StringBuilder();
			if (proxy != null) {
				key.append(":proxy:");
				key.append(proxy.host);
				key.append(":");
				key.append(String.valueOf(proxy.port));
				if (proxy.id != null && !proxy.id.isEmpty()
					&& proxy.pass != null && !proxy.pass.isEmpty()) {
					key.append(":");
					key.append(proxy.id);
					key.append(":");
					key.append(proxy.pass);
				}
			}

			if (this.isIgnoreSslError) {
				key.append(":ignoreSslError:true");
			}

			if (this.timeout > 0) {
				key.append(":timeout:");
				key.append(String.valueOf(this.timeout));
			}

			key.append(":enableRedirect:");
			if (this.isEnableRedirect) {
				key.append("always");
			} else {
				key.append("never");
			}

			if (this.version != null) {
				key.append(":version:");
				key.append(this.version.name());
			}

			if (this.localAddress != null) {
				key.append(":localAddress:");
				key.append(this.localAddress.getHostAddress());
			}

			String keyString = key.toString();

			if (httpClientCacheMap.containsKey(keyString)) {
				return httpClientCacheMap.get(keyString);
			}

			lockHttpClient.lock();
			try {

				if (httpClientCacheMap.containsKey(keyString)) {
					return httpClientCacheMap.get(keyString);
				}

				HttpClient httpClient = newHttpClient();
				httpClientCacheMap.put(keyString, httpClient);

				return httpClient;

			} catch (Exception ex) {

				Log.error(ex);
				return newHttpClient();

			} finally {

				lockHttpClient.unlock();

			}

		} catch (Exception ex) {

			return newHttpClient();

		}

	}

	private HttpClient newHttpClient () {

		// 接続情報を作成する
		HttpClient.Builder builder = HttpClient.newBuilder();

		// バージョン
		if (this.version != null) {
			builder.version(this.version);
		}

		// プロキシ
		if (proxy != null) {
			builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host, proxy.port)));
			if (proxy.id != null && !proxy.id.isEmpty()
				&& proxy.pass != null && !proxy.pass.isEmpty()) {
				System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
				System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
				builder.authenticator(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication () {
						return new PasswordAuthentication(proxy.id, proxy.pass.toCharArray());
					}
				});
			}
		}

		// SSLエラー無視
		if (this.isIgnoreSslError) {
			System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
			try {
				SSLContext sslContext = SSLContext.getInstance("SSL");
				sslContext.init(null, new TrustManager[]{new MyX509TrustManager()}, new java.security.SecureRandom());
				builder.sslContext(sslContext);
			} catch (Exception ex) {
			}
		}

		// タイムアウト設定
		if (this.timeout > 0) {
			builder.connectTimeout(Duration.ofMillis(this.timeout));
		}

		// リダイレクトを追う
		if (this.isEnableRedirect) {
			builder.followRedirects(Redirect.ALWAYS);
		} else {
			builder.followRedirects(Redirect.NEVER);
		}

		// ローカルアドレス
		if (this.localAddress != null) {
			builder.localAddress(this.localAddress);
		}

		return builder.build();

	}

	// endregion

	/**
	 * リクエストを実行する
	 *
	 * @return Executer
	 */
	public E execute () {

		// URLなし
		if (url == null || url.length() == 0) {
			this.isError = true;
			this.errorException = new Exception("URL is empty.");
			return self();
		}

		// スリープ
		if (this.sleep > 0) {
			ThreadUtil.sleep(this.sleep);
		}

		// HttpClient
		HttpClient client = null;
		// リクエスト
		HttpRequest.Builder requestBuilder = null;
		try {

			// HttpClient
			client = createHttpClient();
			// リクエストを作成する
			requestBuilder = createRequestBuilder();
			// URL
			requestBuilder.uri(URI.create(this.url));
			// タイムアウト
			if (this.timeout > 0) {
				requestBuilder.timeout(Duration.ofMillis(this.timeout));
			}
			// ヘッダー
			if (this.headers != null) {
				for (Map.Entry<String, String> entry : this.headers.entrySet()) {
					if (!NO_SUPPORTED_HEADER.contains(entry.getKey().toLowerCase())) {
						requestBuilder.header(entry.getKey(), entry.getValue());
					}
				}
			}
			// Cookie
			if (this.useCookie) {
				requestedCookieMap.clear();
				try {
					Map<String, List<String>> cookies = cookieManager.get(URI.create(getUrl()), new LinkedHashMap<>());
					for (Map.Entry<String, List<String>> entry : cookies.entrySet()) {
						for (String value : entry.getValue()) {
							if (value == null || value.isEmpty()) {
								continue;
							}
							requestBuilder.header(entry.getKey(), value);
							// requestedCookieMap: {connect.sid=s%3Aabc123, theme=dark}
							for (String pair : value.split(";")) {
								int separator = pair.indexOf('=');
								if (separator > 0) {
									requestedCookieMap.put(pair.substring(0, separator).trim(), pair.substring(separator + 1).trim());
								}
							}
						}
					}
				} catch (Exception ex) {}
				if (!requestCookieMap.isEmpty()) {
					for (String key : requestCookieMap.keySet()) {
						requestBuilder.header("Cookie", key + "=" + requestCookieMap.get(key));
						requestedCookieMap.put(key, requestCookieMap.get(key));
					}
					requestCookieMap.clear();
				}
			}

			// 前回の情報をクリア
			clearResponseHeader();
			this.contentLength = 0;

		} catch (Exception ex) {

			Log.error(ex, logData());
			this.isError = true;
			this.errorException = ex;

			return self();

		}

		// リクエストする
		try {

			startTime = System.currentTimeMillis();

			HttpResponse<?> httpResponse;
			if (contentOutputFile != null) {
				// ファイル出力
				HttpResponse<Path> response = client.send(requestBuilder.build(), BodyHandlers.ofFile(contentOutputFile.toPath()));
				setResponseCharset(response.headers());
				this.contentLength = this.contentOutputFile.length();
				httpResponse = response;
			} else if (isKeepResponseBodyStream) {
				// ストリーム保持
				HttpResponse<InputStream> response = client.send(requestBuilder.build(), BodyHandlers.ofInputStream());
				setResponseCharset(response.headers());
				this.responseBodyStream = response.body();
				httpResponse = response;
			} else {
				// メモリ読み込み
				HttpResponse<InputStream> response = client.send(requestBuilder.build(), BodyHandlers.ofInputStream());
				setResponseCharset(response.headers());
				if (isEnableBody(response.headers())) {
					try (
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						BufferedInputStream bis = getResponseInputStream(response)
					) {
						this.contentLength = 0;

						int length;
						byte[] buffer = new byte[2048];
						while ((length = bis.read(buffer, 0, 2048)) > -1) {
							os.write(buffer, 0, length);
							this.contentLength += length;
						}

						this.contentText = os.toString(this.charset);
					} catch (Exception ignore) {}
				} else {
					/*
					 * 本文は読まないが、<b>閉じないと接続が返らない</b>。
					 * try-with-resources に空の本体を書くと
					 * 「宣言した資源を使っていない」警告になるので、素直に閉じる。
					 */
					try {
						response.body().close();
					} catch (Exception ignore) {}
				}
				httpResponse = response;
			}

			executeTime = System.currentTimeMillis() - startTime;

			// レスポンスURL
			try {
				this.responseUrl = httpResponse.uri().toString();
			} catch (Exception ignore) {}
			// レスポンスコード
			this.responseCode = httpResponse.statusCode();
			// レスポンスヘッダー
			for (Map.Entry<String, List<String>> entry : httpResponse.headers().map().entrySet()) {
				for (String value : entry.getValue()) {
					addResponseHeader(entry.getKey(), value);
					if ("content-length".equalsIgnoreCase(entry.getKey())) {
						this.contentLength = Parse.parseLong(value);
					}
				}
			}
			// Cookie
			if (useCookie) {
				try {
					cookieManager.put(URI.create(getUrl()), responseHeader);
				} catch (Exception ex) {}
			}

		} catch (Exception ex) {

			Log.error(ex, logData());
			this.isError = true;
			this.errorException = ex;

		}

		return self();

	}

	private boolean isEnableBody (HttpHeaders headers) {

		if (enableBodyContentType.isEmpty()) {
			return true;
		}

		List<String> list = getResponseHeaderValue(headers, "Content-Type");
		for (String ct : list) {
			for (String enableCt : enableBodyContentType) {
				if (ct.toLowerCase().contains(enableCt.toLowerCase())) {
					return true;
				}
			}
		}

		return false;

	}

	private BufferedInputStream getResponseInputStream (HttpResponse<InputStream> response) throws Exception {

		List<String> contentEncoding = getResponseHeaderValue(response.headers(), "Content-Encoding");
		if (contentEncoding.contains("gzip")) {
			return new BufferedInputStream(new GZIPInputStream(response.body()));
		} else if (contentEncoding.contains("deflate")) {
			return new BufferedInputStream(new DeflaterInputStream(response.body()));
		} else if (contentEncoding.contains("br")) {
			return new BufferedInputStream(new BrotliInputStream(response.body()));
		} else if (contentEncoding.contains("zstd")) {
			return new BufferedInputStream(new io.airlift.compress.v3.zstd.ZstdInputStream(response.body()));
//			return new BufferedInputStream(new ZstdInputStream(response.body()));
		}

		return new BufferedInputStream(response.body());

	}

	private void setResponseCharset (HttpHeaders headers) {

		List<String> charsetList = getResponseHeaderValue(headers, "Content-Type");
		String charset = "";
		if (!charsetList.isEmpty()) {
			String contentType = charsetList.get(0);
			contentType = contentType.trim();

			int index = contentType.indexOf("charset=");
			if (index >= 0) {
				charset = contentType.substring(index + 8).trim();
			}
		}

		if (charset.isEmpty()) {
			if (this.charset == null || this.charset.isEmpty()) {
				this.charset = "UTF-8";
			}
		} else {
			this.charset = charset;
		}

	}

	private List<String> getResponseHeaderValue (HttpHeaders headers, String type) {

		for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {

			if (entry.getKey().equalsIgnoreCase(type)) {
				return entry.getValue();
			}

		}

		return new ArrayList<>();

	}

	// endregion


	// region MyX509TrustManager

	static class MyX509TrustManager implements X509TrustManager {

		public void checkClientTrusted (java.security.cert.X509Certificate[] chain, String authType) throws
			CertificateException {

		}

		public void checkServerTrusted (java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {

		}

		public java.security.cert.X509Certificate[] getAcceptedIssuers () {

			return new java.security.cert.X509Certificate[]{};
		}

	}

	// endregion

}
