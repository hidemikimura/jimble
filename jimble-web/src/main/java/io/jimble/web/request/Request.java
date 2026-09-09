package io.jimble.web.request;

import io.jimble.util.conf.Conf;
import io.jimble.util.bot.BotUtil2;
import io.jimble.util.convertor.UploadFile;
import io.jimble.util.useragent.UserAgentInfo;
import io.jimble.util.useragent.UserAgentUtil;
import io.jimble.util.data.Data;
import io.jimble.util.paging.Paging;
import io.jimble.web.context.WebContext;
import io.jimble.web.cookie.Cookies;
import io.jimble.web.flash.Flash;
import io.jimble.web.http.RequestSource;
import io.jimble.web.server.ServerConf;
import io.jimble.util.convertor.UploadFile;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * リクエスト情報
 */
public class Request extends Data {

	/* Context */
	private final WebContext context;

	/* 入力口（HTTP サーバー実装を包む） */
	private final RequestSource source;

	/**
	 * コンストラクタ
	 *
	 * @param context	Context
	 * @param source	入力口
	 */
	public Request (WebContext context, RequestSource source) {

		this.context = context;
		this.source = source;

	}


	/**
	 * 入力口
	 *
	 * <p>本文をそのまま流したいとき（リバースプロキシなど）に使う。</p>
	 *
	 * @return	入力口
	 */
	public RequestSource source () {

		return source;

	}


	// region リクエスト情報

	/**
	 * リクエスト情報
	 *
	 * @return	リクエスト情報
	 */
	public Data request() {

		if (!containsKey("request")) {
			Data data = getDataOptional("request");
			if (source != null) {
				data.put("address", source.remoteAddress());
				data.put("method", source.method());
				data.put("url", source.url());
				data.put("protocol", source.protocol());
				data.put("scheme", source.scheme());
				data.put("host", source.host());
				data.put("port", source.port());
				data.put("path", source.path());
			}
		}

		return getDataOptional("request");

	}

	/**
	 * アドレス
	 *
	 * @return	アドレス
	 */
	public String address () {

		header();
		return request().getStringOptional("address");

	}

	/**
	 * メソッド
	 *
	 * @return	メソッド
	 */
	public String method () {

		return request().getStringOptional("method");

	}

	/**
	 * URL
	 *
	 * @return	URL
	 */
	public String url () {

		header();
		return request().getStringOptional("url");

	}

	/**
	 * プロトコル
	 *
	 * @return	プロトコル
	 */
	public String protocol () {

		return request().getStringOptional("protocol");

	}

	/**
	 * ホスト
	 *
	 * @return	ホスト
	 */
	public String host () {

		return request().getStringOptional("host");

	}

	/**
	 * ポート
	 *
	 * @return	ポート
	 */
	public int port () {

		return request().getInt("port");

	}

	/**
	 * パス（生。パーセントエンコードされたまま）
	 *
	 * <p><b>ルーティングはこちらを使う</b>（要件 F-R-23）。</p>
	 *
	 * @return	生のパス
	 */
	public String rawPath () {

		return source == null ? "" : source.rawPath();

	}

	/**
	 * クエリ文字列
	 *
	 * @return	クエリ文字列
	 */
	public String query () {

		return source == null ? "" : source.query();

	}

	/**
	 * パス
	 *
	 * @return	パス
	 */
	public String path () {

		return request().getStringOptional("path");

	}

	// endregion

	// region ヘッダー

	private static final String ADDRESS_LOW = "address";
	private static final String X_REAL_IP = "X-Real-IP";
	private static final String X_REAL_IP_LOW = X_REAL_IP.toLowerCase();
	private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
	private static final String CF_CONNECTING_IP_LOW = CF_CONNECTING_IP.toLowerCase();
	private static final String X_FORWARDED_FOR = "X-Forwarded-For";
	private static final String X_FORWARDED_FOR_LOW = X_FORWARDED_FOR.toLowerCase();
	private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
	private static final String X_FORWARDED_PROTO_LOW = X_FORWARDED_PROTO.toLowerCase();
	private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
	private static final String X_FORWARDED_HOST_LOW = X_FORWARDED_HOST.toLowerCase();
	private static final String X_FORWARDED_PORT = "X-Forwarded-Port";
	private static final String X_FORWARDED_PORT_LOW = X_FORWARDED_PORT.toLowerCase();
	public String proxyAddress () {

		/*
		 * 要件 F-H-02。
		 *
		 * <b>既定では信じない。</b>X-Forwarded-For はクライアントが名乗るだけの値なので、
		 * ロードバランサの後ろに置いていない構成で信じると、
		 * 送信元をいくらでも偽れる（IP でのアクセス制限やレートリミットが効かなくなる）。
		 *
		 * 移送元は proxyAddress() が実際には接続元をそのまま返しており、
		 * プロキシ越しのクライアント IP を取る手段がなかった。
		 */
		if (!ServerConf.trustProxy()) {
			return address();
		}

		Data proxy = proxyHeader();

		// 信頼できるものから順に見る
		String cloudflare = proxy.getStringOptional(CF_CONNECTING_IP_LOW);
		if (!cloudflare.isEmpty()) {
			return cloudflare;
		}

		String realIp = proxy.getStringOptional(X_REAL_IP_LOW);
		if (!realIp.isEmpty()) {
			return realIp;
		}

		String forwardedFor = proxy.getStringOptional(X_FORWARDED_FOR_LOW);
		if (!forwardedFor.isEmpty()) {
			// 「クライアント, プロキシ1, プロキシ2」の先頭がクライアント
			int comma = forwardedFor.indexOf(',');
			return (comma < 0 ? forwardedFor : forwardedFor.substring(0, comma)).trim();
		}

		return address();

	}
	public Data proxyHeader () {

		if (!containsKey("proxy_header")) {
			header();
		}

		return getDataOptional("proxy_header");

	}
	public void parseProxyHeader () {

		Data data = getDataOptional("proxy_header");

		if (source == null) {
			return;
		}

		Map<String, String> headers = source.headers();

		data.put(ADDRESS_LOW, source.remoteAddress());
		data.put(X_REAL_IP_LOW, headers.getOrDefault(X_REAL_IP_LOW, ""));
		data.put(CF_CONNECTING_IP_LOW, headers.getOrDefault(CF_CONNECTING_IP_LOW, ""));
		data.put(X_FORWARDED_FOR_LOW, headers.getOrDefault(X_FORWARDED_FOR_LOW, ""));
		data.put(X_FORWARDED_PROTO_LOW, headers.getOrDefault(X_FORWARDED_PROTO_LOW, source.scheme()));
		data.put(X_FORWARDED_HOST_LOW, headers.getOrDefault(X_FORWARDED_HOST_LOW, source.host()));

	}

	/**
	 * ヘッダー情報
	 *
	 * @return	ヘッダー情報
	 */
	public Data header () {

		if (!containsKey("header")) {
			Data data = getDataOptional("header");
			if (context != null) {
				data.putAll(source.headers());
				parseProxyHeader();
			}
		}

		return getDataOptional("header");

	}

	/**
	 * Scheme
	 *
	 * @return  Scheme
	 */
	public String scheme () {

		header();
		return request().getStringOptional("scheme");

	}

	/**
	 * Accept
	 *
	 * @return	Accept
	 */
	public String accept () {

		return header().getStringOptional("accept");

	}

	/**
	 * Accept値の判定値を設定する
	 */
	private void setAcceptValue () {

		Data header = header();
		if (header.containsKey("accept-json")) {
			return;
		}

		header.put("accept-json", isJsonAccepted(accept()));

	}

	/**
	 * Accept が JSON を名指ししているか
	 *
	 * <p>
	 * <b>付いているパラメータを落としてから比べる。</b>
	 * {@code Accept: application/json;q=0.9} や
	 * {@code Accept: application/json, text/plain} のように、
	 * <b>JSON を名指ししているのに素通りしていた</b>（丸ごと1つの文字列として
	 * {@code application/json} と比べていたため）。
	 * </p>
	 *
	 * <p>
	 * <b>{@code q=0} は「要らない」の意味</b>なので、書いてあっても false にする。
	 * </p>
	 *
	 * <p>
	 * <b>{@code *&#47;*} は true にしない。</b>「何でもいい」であって
	 * 「JSON がいい」ではない。ここを true にすると、
	 * <b>ビュー（画面）を返すはずのルートが JSON を返す</b>ようになる——
	 * {@code curl} の既定も、ブラウザが画像を取りにくるときも {@code *&#47;*} である。
	 * </p>
	 *
	 * @param accept	Accept ヘッダの値
	 * @return	名指ししていれば true
	 */
	static boolean isJsonAccepted (String accept) {

		if (accept == null || accept.isEmpty()) {
			return false;
		}

		for (String entry : accept.split(Pattern.quote(","))) {

			String[] parts = entry.split(Pattern.quote(";"));
			String type = parts[0].trim();

			if (!"application/json".equalsIgnoreCase(type)
				&& !"text/javascript".equalsIgnoreCase(type)) {
				continue;
			}

			if (isRefused(parts)) {
				continue;
			}

			return true;

		}

		return false;

	}

	/**
	 * q=0（要らない）が付いているか
	 *
	 * @param parts	";" で割った並び（先頭は型）
	 * @return	要らないと書いてあれば true
	 */
	private static boolean isRefused (String[] parts) {

		for (int index = 1; index < parts.length; index++) {

			String parameter = parts[index].trim();

			if (!parameter.regionMatches(true, 0, "q=", 0, 2)) {
				continue;
			}

			try {
				return Double.parseDouble(parameter.substring(2).trim()) <= 0;
			} catch (NumberFormatException ex) {
				// 読めない q は無視する（付いていないのと同じ）
				return false;
			}

		}

		return false;

	}

	/**
	 * JSONリクエスト判定
	 *
	 * @return	結果
	 */
	public boolean acceptJson () {

		setAcceptValue();
		return header().getBoolean("accept-json");

	}

	/**
	 * Accept-Encoding
	 *
	 * @return	Accept-Encoding
	 */
	public String acceptEncoding () {

		return header().getStringOptional("accept-encoding");

	}

	/**
	 * Accept-Language
	 *
	 * @return	Accept-Language
	 */
	public String acceptLanguage () {

		return header().getStringOptional("accept-language");

	}

	/**
	 * Cache-Control
	 *
	 * @return	Cache-Control
	 */
	public String cacheControl () {

		return header().getStringOptional("cache-control");

	}

	/**
	 * Connection
	 *
	 * @return	Connection
	 */
	public String connection () {

		return header().getStringOptional("connection");

	}

	/**
	 * Content-Length
	 *
	 * @return	Content-Length
	 */
	public long contentLength () {

		return header().getLong("content-length");

	}

	/**
	 * Content-Type
	 *
	 * @return	Content-Type
	 */
	public String contentType () {

		String contentType = header().getStringOptional("content-type");
		int index = contentType.indexOf(';');
		if (index > 0) {
			return contentType.substring(0, index);
		}

		return contentType;

	}

	/**
	 * Origin
	 *
	 * @return	Origin
	 */
	public String origin () {

		return header().getStringOptional("origin");

	}

	/**
	 * Referer
	 *
	 * @return	Referer
	 */
	public String referer () {

		return header().getStringOptional("referer");

	}

	/**
	 * Sec-Ch-Ua
	 *
	 * @return	Sec-Ch-Ua
	 */
	public String secChUa () {

		return header().getStringOptional("sec-ch-ua");

	}

	/**
	 * Sec-Ch-Ua-Mobile
	 *
	 * @return	Sec-Ch--Ua-Mobile
	 */
	public String secChUaMobile () {

		return header().getStringOptional("sec-ch-ua-mobile");

	}

	/**
	 * Sec-Ch-Ua-Platform
	 *
	 * @return	Sec-Ch-Ua-Platform
	 */
	public String secChUaPlatform () {

		return header().getStringOptional("sec-ch-ua-platform");

	}

	/**
	 * Sec-Fetch-Dest
	 *
	 * @return	Sec-Fetch-Dest
	 */
	public String secFetchDest () {

		return header().getStringOptional("sec-fetch-dest");

	}

	/**
	 * Sec-Fetch-Mode
	 *
	 * @return	Sec-Fetch-Mode
	 */
	public String secFetchMode () {

		return header().getStringOptional("sec-fetch-mode");

	}

	/**
	 * Sec-Fetch-Site
	 *
	 * @return	Sec-Fetch-Site
	 */
	public String secFetchSite () {

		return header().getStringOptional("sec-fetch-site");

	}

	/**
	 * Sec-Fetch-User
	 *
	 * @return	Sec-Fetch-User
	 */
	public String secFetchUser () {

		return header().getStringOptional("sec-fetch-user");

	}

	/**
	 * Upgrade-Insecure-Requests
	 *
	 * @return	Upgrade-Insecure-Requests
	 */
	public String upgradeInsecureRequests () {

		return header().getStringOptional("upgrade-insecure-requests");

	}

	/* UserAgent情報 */
	private UserAgentInfo userAgentInfo = null;

	/**
	 * User-Agent
	 *
	 * @return	User-Agent
	 */
	public UserAgentInfo userAgent () {

		if (userAgentInfo == null) {
			userAgentInfo = UserAgentUtil.parse(header().getStringOptional("user-agent"));
		}

		return userAgentInfo;

	}

	/**
	 * CSRF Token
	 *
	 * @return  Csrf-Token
	 */
	public String csrfToken () {

		return header().getStringOptional("csrf-token");

	}

	// endregion

	// region Cookie

	/**
	 * Cookieを解析する
	 */
	private void parseCookie () {

		if (containsKey("cookie")) {
			return;
		}

		Data unsignCookie = getDataOptional("cookie_unsign");
		Data cookie = getDataOptional("cookie");

		if (source == null) {
			return;
		}

		// 生の値（署名を検証していない）
		unsignCookie.putAll(source.cookies());

		/*
		 * 署名を検証した値。検証に落ちたものは入らない。
		 * flash は Cookies が消費するので、ここには出さない。
		 */
		Cookies cookies = context.cookies();
		for (String key : cookies.data().keySet()) {
			if (!key.startsWith(Flash.PREFIX)) {
				cookie.put(key, cookies.get(key));
			}
		}

	}

	/**
	 * Cookie情報
	 *
	 * @return	Cookie情報
	 */
	public Data cookie () {

		parseCookie();

		return getDataOptional("cookie");

	}

	/**
	 * Cookie
	 *
	 * @param name	名前
	 * @return	Cookie
	 */
	public String cookie (String name) {

		return cookie().getStringOptional(name);

	}

	/**
	 * unsign Cookie
	 *
	 * @return	Cookie情報
	 */
	public Data unsignCookie () {

		parseCookie();

		return getDataOptional("cookie_unsign");

	}

	/**
	 * unsign Cookie
	 *
	 * @param name	名前
	 * @return	Cookie
	 */
	public String unsignCookie (String name) {

		return unsignCookie().getStringOptional(name);

	}

	// endregion

	// region Session

	/**
	 * Session情報
	 *
	 * @return	Session情報
	 */
	public Data session () {

		/*
		 * セッション本体は context.session()。ここは「リクエストの一部として読む」入口で、
		 * バリデーションやテンプレートに渡すために Data として見せているだけ。
		 * 保持はしない（session().put() の結果がここから見えないと混乱するため）。
		 */
		return context.session().data();

	}

	/**
	 * Session
	 *
	 * @param name	名前
	 * @return	Session
	 */
	public String session (String name) {

		return session().getStringOptional(name);

	}

	// endregion

	// region Flash

	/**
	 * Flash情報
	 *
	 * @return	Flash情報
	 */
	public Data flash () {

		parseCookie();

		return getDataOptional("flash");

	}

	/**
	 * Flash
	 *
	 * @param name	名前
	 * @return	Flash
	 */
	public String flash (String name) {

		return flash().getStringOptional(name);

	}

	// endregion

	// region ボディ情報

	/**
	 * ボディ情報
	 *
	 * @return	ボディ情報
	 */
	public Data body () {

		if (!containsKey("body")) {
			Data body = getDataOptional("body");
			// パス
			{
				Data data = body.getDataOptional("path");
				if (context != null && context.route() != null) {
					// パス変数はセグメントごとにデコード済み（要件 F-R-23）
					data.putAll(context.route().variables().values());
				}
			}
			// クエリ
			{
				Data data = body.getDataOptional("query");
				if (source != null) {
					data.putAll(source.queryParams());
				}
			}
			// フォーム
			{
				Data data = body.getDataOptional("form");
				if (source != null) {
					data.putAll(source.formParams());
				}
			}
			// ファイル
			{
				Data data = body.getDataOptional("file");
				if (source != null) {
					for (UploadFile uploadFile : source.files()) {
						List<Object> list = data.getObjectListOptional(uploadFile.name);
						list.add(uploadFile);
						data.putData(uploadFile.name, list);
					}
				}
			}
			// ボディ
			if (source != null) {
				if ("application/json".equalsIgnoreCase(contentType())) {
					try {
						String text = source.bodyText(StandardCharsets.UTF_8);
						body.put("text", text);
						Data json = Data.fromJsonString(text);
						body.put("json", json);
					} catch (Exception ignore) {}
				} else if (contentType().toLowerCase().startsWith("text/")) {
					try {
						body.put("text", source.bodyText(StandardCharsets.UTF_8));
					} catch (Exception ex) {}
				}
			}
		}

		return getDataOptional("body");

	}

	/**
	 * path
	 *
	 * @return	path
	 */
	public Data bodyPath () {

		return body().getDataOptional("path");

	}

	/**
	 * query
	 *
	 * @return	query
	 */
	public Data bodyQuery () {

		return body().getDataOptional("query");

	}

	/**
	 * form
	 *
	 * @return	form
	 */
	public Data bodyForm () {

		return body().getDataOptional("form");

	}

	/**
	 * file
	 *
	 * @return	file
	 */
	public Data bodyFile () {

		return body().getDataOptional("file");

	}

	/**
	 * text
	 *
	 * @return	text
	 */
	public String bodyText () {

		return body().getStringOptional("text");

	}

	/**
	 * json
	 *
	 * @return	json
	 */
	public Data bodyJson () {

		return body().getDataOptional("json");

	}

	/* all */
	private Data allParameter = null;

	/**
	 * all（パスパラメータ、クエリパラメータ、フォームパラメータ、JSON）
	 *
	 * @return  all
	 */
	public Data bodyAll () {

		if (this.allParameter == null) {
			this.allParameter = NestedParameterParser.parse(this);
		}

		return this.allParameter;

	}

	// endregion

	// region Botアクセス判定

	/* Botアクセス判定 */
	private Boolean isBotAccess = null;

	/**
	 * Botアクセス判定
	 *
	 * @return	Botの場合 = true
	 */
	public boolean isBotAccess () {

		if (isBotAccess == null) {
			isBotAccess = BotUtil2.isBot(address(), userAgent().ua);
		}

		return isBotAccess;

	}

	// endregion

	// region ページング情報

	/* ページング情報 */
	private Paging paging = null;

	/**
	 * ページング指定あり判定
	 *
	 * @return	ページング指定ありの場合 = true
	 */
	public boolean hasPaging () {

		if (paging != null) {
			return true;
		}

		return Paging.hasPaging(bodyAll());

	}

	/**
	 * ページング情報
	 *
	 * @return  ページング情報
	 */
	public Paging paging () {

		return paging(0);

	}

	/**
	 * ページング情報
	 *
	 * @param per 取得件数
	 * @return  ページング情報
	 */
	public Paging paging (long per) {


		if (paging == null) {
			paging = new Paging();
			paging.load(bodyAll(), per);
			context.response().put("paging", paging);
		}

		return paging;

	}

	// endregion

}
