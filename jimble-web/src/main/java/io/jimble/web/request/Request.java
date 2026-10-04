package io.jimble.web.request;

import io.jimble.web.router.PathSegments;
import io.jimble.util.conf.Conf;
import io.jimble.util.bot.BotUtil;
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
 *
 * <p>
 * <b>{@code Data} を継承しない</b>（2.0。要件 D-196）。1.x は {@code Data} の子だったので
 * {@code request().getString("id")} と書けたが、中身は「request」「header」「body」などの区分だけで、
 * <b>送られてきた値は入っていない</b>——いつも {@code null} だった。値は {@link #bodyAll()} / {@link #body()} などから読む。
 * </p>
 */
public final class Request {

	/* 区分ごとの入れ物（request / header / cookie / body …）。1.x は Request 自身が Data だった */
	private final Data store = new Data();

	/* Context */
	private final WebContext context;

	/* 入力口（HTTP サーバー実装を包む） */
	private final RequestSource source;

	/*
	 * よく使う3つは、それぞれで覚える（要件 D-169）。
	 *
	 * <b>{@link #request()} の Data を作らせないため。</b>
	 * あちらは 8 項目まとめて作るので<b>1リクエストあたり 960 byte</b> かかる——
	 * アクセスログが使うのは {@code method} {@code path} {@code query} の3つだけである。
	 *
	 * <b>代わりに、{@code request()} の Data を書き換えても、
	 * ここの戻りは変わらなくなった。</b>元から書き換える口として案内していないので、
	 * 変える側に倒している。
	 */
	private String method;

	/* 覚えておくパス（要件 D-169） */
	private String path;

	/* 覚えておくアドレス（要件 D-169） */
	private String address;

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

		if (!store.containsKey("request")) {
			Data data = store.getDataOptional("request");
			if (source != null) {
				data.put("address", address());
				data.put("method", method());
				data.put("url", source.url());
				data.put("protocol", source.protocol());
				data.put("scheme", effectiveScheme());
				data.put("host", source.host());
				data.put("port", source.port());
				/*
				 * <b>スラッシュを正規の形にして入れる（要件 D-166）。</b>
				 * ルーターは末尾と連続のスラッシュを前から無視している（要件 F-R-24）ので、
				 * ここで生のまま渡すと<b>ルーターが見たパスとアプリが見るパスが食い違う</b>——
				 * {@code before} フックがパス文字列で判定していると、
				 * <b>{@code //admin} だけがすり抜ける</b>。
				 */
				data.put("path", path());
			}
		}

		return store.getDataOptional("request");

	}

	/**
	 * アドレス
	 *
	 * @return	アドレス
	 */
	public String address () {

		header();

		if (address == null) {
			address = source == null ? "" : source.remoteAddress();
		}

		return address;

	}

	/**
	 * メソッド
	 *
	 * @return	メソッド
	 */
	public String method () {

		if (method == null) {
			method = source == null ? "" : source.method();
		}

		return method;

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
	 * パス（デコード済み・スラッシュは正規の形）
	 *
	 * <p>
	 * <b>ルーターが見たのと同じ形である（要件 D-166）。</b>
	 * 末尾のスラッシュは落ち、連続したスラッシュは1つになる——
	 * {@code //admin/} は {@code /admin} として返る。
	 * </p>
	 *
	 * <p>
	 * <b>大文字小文字はそのままである。</b>
	 * {@code router.ignore_case} を有効にすると {@code /ADMIN} が
	 * {@code /admin} のルートに当たるが、<b>ここに返るのは打たれたとおりの綴り</b>である
	 * （小文字に寄せると、パスパラメータの値まで変わってしまう）。
	 * <b>綴りまで揃えたいときは {@code router.redirect_to_canonical} を入れること</b>——
	 * ルートに書いた綴りへ 301 で寄せるので、アプリには1つの綴りしか来なくなる。
	 * </p>
	 *
	 * <p>受け取ったままのパスが要るときは {@link #rawPath()} を使う。</p>
	 *
	 * @return	パス
	 */
	public String path () {

		if (path == null) {
			path = source == null ? "" : PathSegments.canonicalRawPath(source.path());
		}

		return path;

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

		/*
		 * <b>X-Forwarded-For は右から見る</b>（D-214。{@link ClientIp}）。中継は、クライアントが送ってきた値の
		 * 後ろに足すので、左端はクライアントが好きに名乗れる。CF-Connecting-IP / X-Real-IP は、
		 * server.client_ip_header に書いたときだけ信じる。
		 */
		Data proxy = proxyHeader();

		return ClientIp.resolve(address(), proxy.getStringOptional(X_FORWARDED_FOR_LOW)
			, name -> header().getStringOptional(name));

	}

	public Data proxyHeader () {

		if (!store.containsKey("proxy_header")) {
			header();
		}

		return store.getDataOptional("proxy_header");

	}
	public void parseProxyHeader () {

		Data data = store.getDataOptional("proxy_header");

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

		if (!store.containsKey("header")) {
			Data data = store.getDataOptional("header");
			if (context != null) {
				data.putAll(source.headers());
				parseProxyHeader();
			}
		}

		return store.getDataOptional("header");

	}

	/**
	 * Scheme（{@code http} / {@code https}）
	 *
	 * <p>
	 * {@code server.trust_proxy = true} なら、前段のプロキシが付けた {@code X-Forwarded-Proto} を見る（D-292）。
	 * TLS をロードバランサで終端していると、接続そのものは {@code http} なので、2.5.2 までは
	 * <b>いつも {@code http} に見えていた</b>（HSTS のヘッダが出なかった）。
	 * </p>
	 *
	 * @return  Scheme
	 */
	public String scheme () {

		header();
		return request().getStringOptional("scheme");

	}

	/**
	 * HTTPS で来たか（{@link #scheme()} が {@code https}。プロキシの後ろなら {@code X-Forwarded-Proto} を見る）
	 *
	 * @return	HTTPS の場合 = true
	 */
	public boolean isSecure () {

		return "https".equalsIgnoreCase(scheme());

	}

	/**
	 * 見かけの Scheme を決める（D-292）
	 *
	 * <ul>
	 *   <li>{@code server.trust_proxy} が false なら、接続の Scheme</li>
	 *   <li>{@code server.trusted_proxies} を書いていて、直に来た相手が入っていなければ、接続の Scheme（ヘッダは名乗りにすぎない）</li>
	 *   <li>{@code X-Forwarded-Proto} が複数なら<b>右端</b>（いちばん近い中継が付けたもの。左はクライアントが名乗れる）。
	 *       {@code http} / {@code https} 以外なら、接続の Scheme</li>
	 * </ul>
	 */
	private String effectiveScheme () {

		String connection = source.scheme();

		if (!ServerConf.trustProxy() || !ClientIp.fromTrustedProxy(source.remoteAddress())) {
			return connection;
		}

		String forwarded = source.headers().getOrDefault(X_FORWARDED_PROTO_LOW, "");

		if (forwarded.isBlank()) {
			return connection;
		}

		int comma = forwarded.lastIndexOf(',');
		String last = (comma < 0 ? forwarded : forwarded.substring(comma + 1)).trim().toLowerCase(java.util.Locale.ROOT);

		return "http".equals(last) || "https".equals(last) ? last : connection;

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

		// 相手が送ってきた値なので、読めなくても止めない（0）
		try {
			return header().getLong("content-length");
		} catch (io.jimble.util.data.DataConversionException ex) {
			return 0;
		}

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
	 * 送られてきた {@code csrf-token} ヘッダ
	 *
	 * <p>
	 * <b>トークンを発行するものではない。</b>フォームに入れるトークンは
	 * {@link io.jimble.web.csrf.Csrf#token(io.jimble.web.context.WebContext)} で取る。
	 * </p>
	 *
	 * @return  ヘッダの値（無ければ空文字）
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

		if (store.containsKey("cookie")) {
			return;
		}

		Data unsignCookie = store.getDataOptional("cookie_unsign");
		Data cookie = store.getDataOptional("cookie");

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

		return store.getDataOptional("cookie");

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

		return store.getDataOptional("cookie_unsign");

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

		return store.getDataOptional("flash");

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
	 * @throws io.jimble.web.http.HttpException	本文が JSON（{@code application/json}）なのに読めないとき 400（2.0。1.x は空の Data）
	 */
	public Data body () {

		if (!store.containsKey("body")) {
			Data body = store.getDataOptional("body");
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
						List<Object> list = data.getObjectListOptional(uploadFile.name());
						list.add(uploadFile);
						data.putData(uploadFile.name(), list);
					}
				}
			}
			// ボディ
			if (source != null) {
				if ("application/json".equalsIgnoreCase(contentType())) {
					String text = null;
					try {
						text = source.bodyText(StandardCharsets.UTF_8);
					} catch (Exception ex) {
						malformedJson = "本文を読めませんでした: " + ex.getMessage();
					}
					if (text != null) {
						body.put("text", text);
						try {
							body.put("json", Data.fromJsonString(text));
						} catch (io.jimble.util.json.JsonParseException ex) {
							/*
							 * 壊れた JSON は 400（2.0。要件 D-196）。1.x は空の Data として扱ったので、
							 * 「送った値が全部無かった」ことになり、原因が見えなかった（1.5 は警告）。
							 */
							malformedJson = ex.getMessage();
						}
					}
				} else if (contentType().toLowerCase().startsWith("text/")) {
					try {
						body.put("text", source.bodyText(StandardCharsets.UTF_8));
					} catch (Exception ex) {}
				}
			}
		}

		if (malformedJson != null) {
			// 何度読んでも同じ答えにする（途中まで組んだ本文を返さない）
			throw new io.jimble.web.http.HttpException(400, "本文の JSON を読めません（" + malformedJson + "）");
		}

		return store.getDataOptional("body");

	}

	/* 本文の JSON を読めなかった理由（読めたら null） */
	private String malformedJson = null;

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
			isBotAccess = BotUtil.isBot(address(), userAgent().ua());
		}

		return isBotAccess;

	}

	// endregion

	// region ページング情報

	/* ページング情報 */
	private Paging paging = null;

	/* 最初に paging を作ったときの件数（0 は既定） */
	private long pagingPer = 0;

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
	 * @throws IllegalStateException	このリクエストで違う件数の paging をもう作っているとき
	 */
	public Paging paging (long per) {

		if (paging == null) {
			paging = new Paging();
			paging.load(bodyAll(), per);
			pagingPer = per;
			context.response().put("paging", paging);
		} else if (per != 0 && per != pagingPer) {
			/*
			 * 1度作ったら使い回すので、2度目の per は効かない。例外にする（2.0。1.5 は警告。要件 D-196）。
			 * 先に paging()（既定の件数）を呼んでいると、あとの paging(50) の 50 が黙って無視されていた。
			 */
			throw new IllegalStateException(
				"paging(" + per + ") の件数は効きません——このリクエストではもう paging("
					+ (pagingPer == 0 ? "" : String.valueOf(pagingPer)) + ") で作ってあります。"
					+ "件数を決めるなら最初の1回で渡してください"
					+ io.jimble.util.internal.Docs.see("request-response"));
		}

		return paging;

	}

	// endregion

}
