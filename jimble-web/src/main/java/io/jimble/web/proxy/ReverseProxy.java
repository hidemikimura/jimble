package io.jimble.web.proxy;

import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Controller;
import io.jimble.web.router.Handler;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * リバースプロキシ（要件 F-R-14）
 *
 * <p>
 * 受けたリクエストをそのまま別のサーバーへ流し、応答をそのまま返す。
 * </p>
 *
 * <pre>
 * // /api/** を http://backend:8080/** に流す
 * install(() -&gt; ReverseProxy.mount("/api", "http://backend:8080"));
 *
 * // nginx の proxy_set_header / proxy_hide_header / proxy_redirect / proxy_cookie_* に当たるもの
 * install(() -&gt; ReverseProxy.mount("/shop", new ReverseProxy("http://shop:9000")
 *     .preserveHost()                                     // proxy_set_header Host $http_host
 *     .setHeader("X-App", "front")                        // proxy_set_header X-App front
 *     .setHeader("X-User", context -&gt; userId(context))  // 値をリクエストから作る
 *     .removeHeader("Cookie")                             // proxy_set_header Cookie ""
 *     .hideResponseHeader("X-Powered-By")                 // proxy_hide_header X-Powered-By
 *     .redirect("http://shop.internal/", "/shop/")        // proxy_redirect http://shop.internal/ /shop/
 *     .cookieDomain("shop.internal", "example.com")       // proxy_cookie_domain
 *     .cookiePath("/", "/shop/")));                       // proxy_cookie_path
 * </pre>
 *
 * <p>
 * <b>全メソッドを1回で登録する</b>（要件 F-R-21）。移送元は 14 回手書きしていた。
 * </p>
 *
 * <h2>nginx と同じにしたところ</h2>
 * <ul>
 *   <li><b>{@code Host} は既定で転送先のもの</b>（{@code proxy_set_header Host $proxy_host}）。
 *       元の {@code Host} は {@code X-Forwarded-Host} で渡す。そのまま渡すなら {@link #preserveHost()}</li>
 *   <li><b>{@code Location} / {@code Refresh} は既定で書き換える</b>（{@code proxy_redirect default}）。
 *       転送先の URL で始まるものを、ブラウザから見えるパスに直す。切るなら {@link #noRedirectRewrite()}</li>
 *   <li><b>転送先との接続は1回ごとに切る</b>（nginx の既定。keepalive は持たない）</li>
 *   <li><b>{@code Content-Encoding} はそのまま渡す</b>（圧縮した本文を、もう一度圧縮しない）</li>
 * </ul>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>ステータスコードを返していなかった。</b>移送元はヘッダと本文だけを写しており、
 *       <b>転送先が 404 でも 500 でもクライアントには 200 が返っていた。</b>
 *       これがいちばん影響が大きい</li>
 *   <li><b>hop-by-hop ヘッダをそのまま転送していた</b>（{@link HopByHopHeaders} 参照）</li>
 *   <li><b>{@code X-Forwarded-Proto} に {@code HTTP/1.1} を入れていた。</b>
 *       {@code ctx.getProtocol()} はプロトコルのバージョンであって scheme ではない。
 *       転送先が「HTTPS かどうか」を判定できない</li>
 *   <li><b>{@code X-Forwarded-For} を上書きしていた。</b>多段になったとき経路が消える。
 *       既存の値があれば後ろに足す</li>
 *   <li><b>タイムアウトが無かった。</b>転送先が応答しないとスレッドが張り付く</li>
 * </ol>
 *
 * <h2>組み立ては使う前に</h2>
 * <p>
 * 設定（{@link #preserveHost()} など）は {@code mount} の前に済ませる。<b>1度でもリクエストを受けたあとに変えると例外</b>——
 * 1つのインスタンスを全リクエストで使い回すので、途中で変えると、どのリクエストにどちらが効いたか分からなくなる。
 * </p>
 */
public final class ReverseProxy implements Handler {

	/** 本文を送らないメソッド */
	private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "DELETE", "OPTIONS", "TRACE");

	/** 転送先が応答しないときのステータスコード */
	public static final int BAD_GATEWAY = 502;

	/** 転送できないパス（{@code .} / {@code ..} を含む） */
	public static final int BAD_REQUEST = 400;

	/* 転送先のベース URL */
	private final String forwardBaseUrl;

	/* 転送先（分解したもの） */
	private final URI forwardUri;

	/* 繋ぐまでの待ち */
	private final Duration connectTimeout;

	/* 応答待ちの上限 */
	private final Duration requestTimeout;

	/* 元の Host をそのまま渡すか */
	private boolean preserveHost = false;

	/* 足す・上書きするリクエストヘッダ（名前 → 値を作るもの。null を返したら送らない） */
	private final Map<String, Function<WebContext, String>> setHeaders = new LinkedHashMap<>();

	/* 送らないリクエストヘッダ（小文字） */
	private final List<String> removeHeaders = new ArrayList<>();

	/* 返さない応答ヘッダ（小文字） */
	private final List<String> hideResponseHeaders = new ArrayList<>();

	/* Location / Refresh の書き換え（前から順に、最初に当たったもの） */
	private final List<String[]> redirects = new ArrayList<>();

	/* 転送先の URL で始まるものを書き換えるか（proxy_redirect default） */
	private boolean redirectDefault = true;

	/* Set-Cookie の Domain の書き換え */
	private final List<String[]> cookieDomains = new ArrayList<>();

	/* Set-Cookie の Path の書き換え */
	private final List<String[]> cookiePaths = new ArrayList<>();

	/* 1度でも使ったか（使ったあとは設定を変えさせない） */
	private volatile boolean used = false;

	/**
	 * コンストラクタ
	 *
	 * @param forwardBaseUrl	転送先のベース URL（例 {@code http://backend:8080}）
	 */
	public ReverseProxy (String forwardBaseUrl) {

		this(forwardBaseUrl, ProxyConf.connectTimeout(), ProxyConf.requestTimeout());

	}

	/**
	 * コンストラクタ
	 *
	 * @param forwardBaseUrl	転送先のベース URL
	 * @param connectTimeout	接続の上限
	 * @param requestTimeout	応答待ちの上限
	 */
	public ReverseProxy (String forwardBaseUrl, Duration connectTimeout, Duration requestTimeout) {

		this.forwardBaseUrl = trimTrailingSlash(forwardBaseUrl);
		this.forwardUri = URI.create(this.forwardBaseUrl.isEmpty() ? "http://invalid" : this.forwardBaseUrl);
		this.connectTimeout = connectTimeout;
		this.requestTimeout = requestTimeout;

		String scheme = forwardUri.getScheme() == null ? "" : forwardUri.getScheme().toLowerCase(Locale.ROOT);

		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IllegalArgumentException("転送先は http:// か https:// で書いてください: " + forwardBaseUrl);
		}

	}

	// region 設定（nginx の proxy_set_header ほか）

	/**
	 * 元の {@code Host} をそのまま渡す（{@code proxy_set_header Host $http_host}）
	 *
	 * <p>
	 * 既定では転送先の {@code Host}（{@code backend:8080}）を送る。転送先が <b>Host で振り分ける</b>
	 * （ドメインでショップを決める、など）なら、これを付ける。
	 * </p>
	 *
	 * @return	ReverseProxy
	 */
	public ReverseProxy preserveHost () {

		refuseAfterUse("preserveHost");
		this.preserveHost = true;
		return this;

	}

	/**
	 * リクエストヘッダを足す・上書きする（{@code proxy_set_header 名前 値}）
	 *
	 * <p>{@code Host} も決められる。元のリクエストに同じ名前があれば置き換える。</p>
	 *
	 * @param name	ヘッダ名
	 * @param value	値
	 * @return	ReverseProxy
	 */
	public ReverseProxy setHeader (String name, String value) {

		return setHeader(name, context -> value);

	}

	/**
	 * リクエストヘッダを、リクエストから作った値で足す・上書きする
	 *
	 * <p>{@code null} か空文字を返したら、そのヘッダは送らない（nginx の空の値と同じ）。</p>
	 *
	 * @param name	ヘッダ名
	 * @param value	値を作るもの
	 * @return	ReverseProxy
	 */
	public ReverseProxy setHeader (String name, Function<WebContext, String> value) {

		refuseAfterUse("setHeader");
		checkHeaderName(name);
		this.setHeaders.put(name, value);
		return this;

	}

	/**
	 * リクエストヘッダを送らない（{@code proxy_set_header 名前 ""}）
	 *
	 * @param name	ヘッダ名
	 * @return	ReverseProxy
	 */
	public ReverseProxy removeHeader (String name) {

		refuseAfterUse("removeHeader");
		checkHeaderName(name);
		this.removeHeaders.add(name.toLowerCase(Locale.ROOT));
		return this;

	}

	/**
	 * 転送先の応答ヘッダを返さない（{@code proxy_hide_header 名前}）
	 *
	 * @param name	ヘッダ名
	 * @return	ReverseProxy
	 */
	public ReverseProxy hideResponseHeader (String name) {

		refuseAfterUse("hideResponseHeader");
		checkHeaderName(name);
		this.hideResponseHeaders.add(name.toLowerCase(Locale.ROOT));
		return this;

	}

	/**
	 * {@code Location} / {@code Refresh} の書き換えを足す（{@code proxy_redirect 元 先}）
	 *
	 * <p>
	 * {@code from} で始まる値を、{@code to} で始まるように置き換える。足した順に見て、最初に当たったものを使う。
	 * 既定の書き換え（{@link #noRedirectRewrite()} で切れる）より先に見る。
	 * </p>
	 *
	 * @param from	元（例 {@code http://shop.internal/}）
	 * @param to	先（例 {@code /shop/}）
	 * @return	ReverseProxy
	 */
	public ReverseProxy redirect (String from, String to) {

		refuseAfterUse("redirect");
		requireNonEmpty(from, "redirect の元");
		this.redirects.add(new String[]{ from, to == null ? "" : to });
		return this;

	}

	/**
	 * 既定の {@code Location} / {@code Refresh} の書き換えを切る（{@code proxy_redirect off}）
	 *
	 * <p>{@link #redirect(String, String)} で足したものは、切ったあとも効く。</p>
	 *
	 * @return	ReverseProxy
	 */
	public ReverseProxy noRedirectRewrite () {

		refuseAfterUse("noRedirectRewrite");
		this.redirectDefault = false;
		return this;

	}

	/**
	 * {@code Set-Cookie} の {@code Domain} を書き換える（{@code proxy_cookie_domain 元 先}）
	 *
	 * <p>大文字小文字は問わない。先頭の {@code .} は無視して比べる。</p>
	 *
	 * @param from	元
	 * @param to	先
	 * @return	ReverseProxy
	 */
	public ReverseProxy cookieDomain (String from, String to) {

		refuseAfterUse("cookieDomain");
		requireNonEmpty(from, "cookieDomain の元");
		this.cookieDomains.add(new String[]{ from, to == null ? "" : to });
		return this;

	}

	/**
	 * {@code Set-Cookie} の {@code Path} を書き換える（{@code proxy_cookie_path 元 先}）
	 *
	 * <p>{@code from} で始まる Path を、{@code to} で始まるように置き換える。</p>
	 *
	 * @param from	元（例 {@code /}）
	 * @param to	先（例 {@code /shop/}）
	 * @return	ReverseProxy
	 */
	public ReverseProxy cookiePath (String from, String to) {

		refuseAfterUse("cookiePath");
		requireNonEmpty(from, "cookiePath の元");
		this.cookiePaths.add(new String[]{ from, to == null ? "" : to });
		return this;

	}

	/**
	 * 使ったことにする（テストから。設定を変えさせないことを確かめる）
	 */
	void markUsedForTest () {

		used = true;

	}

	/**
	 * 使い始めたあとの設定の変更を止める
	 */
	private void refuseAfterUse (String what) {

		if (used) {
			throw new IllegalStateException("リクエストを受けたあとで " + what + " は変えられません（mount の前に設定してください）");
		}

	}

	/**
	 * ヘッダ名を確かめる（改行を入れさせない）
	 */
	private static void checkHeaderName (String name) {

		requireNonEmpty(name, "ヘッダ名");

		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c <= ' ' || c == ':' || c >= 0x7f) {
				throw new IllegalArgumentException("ヘッダ名に使えない文字があります: " + name);
			}
		}

	}

	/**
	 * 空でないこと
	 */
	private static void requireNonEmpty (String value, String what) {

		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(what + "が空です");
		}

	}

	// endregion

	@Override
	public void handle (WebContext context) {

		used = true;

		String target = target(context);

		if (target == null) {
			context.response().send(BAD_REQUEST);
			return;
		}

		UpstreamConnection response;

		try {

			response = send(context, target);

		} catch (Exception ex) {

			Log.warn("リバースプロキシに失敗しました: %s / %s".formatted(forwardBaseUrl, ex.getMessage()));
			context.response().send(BAD_GATEWAY);
			return;

		}

		try (response) {

			sendResponse(context, response);

		} catch (Exception ex) {

			// もう送り始めているかもしれない。送っていなければ 502
			Log.warn("リバースプロキシの応答を返せませんでした: %s / %s".formatted(forwardBaseUrl, ex.getMessage()));

			if (!context.response().isSent()) {
				context.response().send(BAD_GATEWAY);
			}

		}

	}

	// region リクエスト

	/**
	 * 転送先へ送る
	 */
	private UpstreamConnection send (WebContext context, String target) throws Exception {

		String method = context.request().method();

		Map<String, List<String>> headers = requestHeaders(context);

		InputStream body = null;
		long contentLength = -1;

		if (!BODYLESS_METHODS.contains(method)) {

			body = context.request().source().bodyStream();

			String length = firstHeader(context, "content-length");
			String transferEncoding = firstHeader(context, "transfer-encoding");

			String contentEncoding = firstHeader(context, "content-encoding");
			boolean encoded = contentEncoding != null && !contentEncoding.isBlank()
				&& !"identity".equalsIgnoreCase(contentEncoding.trim());

			if (encoded) {
				/*
				 * <b>圧縮された本文は、長さを決めずに送る</b>（D-244）。受け取った Content-Length は圧縮したままの長さで、
				 * かつてはそれを本文の長さにしていたので、解かれた本文と長さが合わずに 502 になった
				 */
				contentLength = -1;
			} else if (length != null && !length.isEmpty()) {
				contentLength = Long.parseLong(length.trim());
			} else if (transferEncoding == null || transferEncoding.isEmpty()) {
				// 長さも chunked も無い = 本文なし。POST に長さが無いと断るサーバーもあるので 0 と書く
				contentLength = 0;
			}

		}

		boolean secure = "https".equalsIgnoreCase(forwardUri.getScheme());
		int port = forwardUri.getPort() > 0 ? forwardUri.getPort() : (secure ? 443 : 80);

		return UpstreamConnection.send(secure, forwardUri.getHost(), port, connectTimeout, requestTimeout
			, method, target, headers, body, contentLength);

	}

	/**
	 * デコード済みのパスを、セグメントごとにエンコードし直す（D-203）
	 *
	 * @param decoded	デコード済みのパス（先頭の {@code /} なし）
	 * @return	{@code /} で始まるパス。{@code .} / {@code ..} を含めば null
	 */
	static String encodePath (String decoded) {

		StringBuilder sb = new StringBuilder();

		for (String segment : decoded.split("/", -1)) {

			if (".".equals(segment) || "..".equals(segment)) {
				return null;
			}

			sb.append('/').append(encodeSegment(segment));

		}

		return sb.toString();

	}

	/**
	 * パスの1セグメントをエンコードする（RFC 3986 の pchar 以外を %XX に）
	 *
	 * @param segment	セグメント
	 * @return	エンコードしたもの
	 */
	private static String encodeSegment (String segment) {

		StringBuilder sb = new StringBuilder();

		for (byte b : segment.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {

			int c = b & 0xff;

			if (isPchar(c)) {
				sb.append((char) c);
			} else {
				sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0xf]);
			}

		}

		return sb.toString();

	}

	/** 16進の字 */
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	/**
	 * そのまま書いてよい字か（unreserved / sub-delims / ":" / "@"）
	 *
	 * @param c	字（0〜255）
	 * @return	そのままでよければ true
	 */
	private static boolean isPchar (int c) {

		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
			|| "-._~!$&'()*+,;=:@".indexOf(c) >= 0;

	}

	/**
	 * 転送先のパス（ベース URL のパス + ワイルドカード + クエリ）
	 *
	 * <p>
	 * <b>ワイルドカードはセグメントごとにエンコードし直す</b>（D-203）。ワイルドカードの値は
	 * <b>パーセントデコード済み</b>なので、かつてはそのまま要求行に書いていた。
	 * {@code %0d%0a} が改行になって<b>転送先への2本目のリクエストを差し込め</b>、
	 * {@code %2e%2e} が {@code ..} になって<b>ベース URL のパスの外へ出られた</b>。
	 * {@code .} / {@code ..} のセグメントは、エンコードしても転送先が辿るので断る。
	 * </p>
	 *
	 * @return	パス。転送できないパスなら null
	 */
	String target (WebContext context) {

		String basePath = forwardUri.getRawPath() == null ? "" : forwardUri.getRawPath();

		String wildcard = context.route() == null ? "" : context.route().variables().wildcard();
		String path = wildcard == null || wildcard.isEmpty() ? "" : encodePath(wildcard);

		if (path == null) {
			return null;
		}

		String target = basePath + path;

		if (target.isEmpty()) {
			target = "/";
		}

		String query = context.request().query();

		return target + (query == null || query.isEmpty() ? "" : "?" + query);

	}

	/**
	 * 送るヘッダ
	 *
	 * <p>
	 * <b>来た行のまま送る</b>（{@code headerValues()}）。{@code headers()} は同じ名前の行を {@code ", "} で繋いだ1本なので、
	 * 2行の {@code Cookie} を渡すと {@code "a=1, b=2"} という、区切りの違う1行になっていた。
	 * </p>
	 */
	private Map<String, List<String>> requestHeaders (WebContext context) {

		Map<String, List<String>> headers = new LinkedHashMap<>();

		/*
		 * <b>Connection が指名したヘッダも、その1区間だけのもの</b>（RFC 9110 7.6.1。D-243）。
		 * かつては決まった名前（Keep-Alive など）しか落とさず、指名されたヘッダを転送先へ渡していた
		 */
		java.util.Set<String> nominated = new java.util.HashSet<>();

		for (Map.Entry<String, List<String>> entry : context.request().source().headerValues().entrySet()) {
			if ("connection".equalsIgnoreCase(entry.getKey())) {
				for (String value : entry.getValue()) {
					for (String token : value.split(",")) {
						if (!token.isBlank()) {
							nominated.add(token.trim().toLowerCase(Locale.ROOT));
						}
					}
				}
			}
		}

		for (Map.Entry<String, List<String>> entry : context.request().source().headerValues().entrySet()) {

			if (!HopByHopHeaders.isForwardable(entry.getKey())
				|| nominated.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
				continue;
			}

			headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));

		}

		/*
		 * <b>Helidon が解いた本文には、Content-Encoding を付けない</b>（D-244）。gzip / deflate の本文は
		 * 受けたところで解かれているので、そのまま付けると転送先が平文をもう一度解こうとして壊れる
		 */
		List<String> encodings = headers.get(findKey(headers, "content-encoding"));
		if (encodings != null && encodings.stream().allMatch(ReverseProxy::isDecodedByServer)) {
			remove(headers, "content-encoding");
		}

		// Host（既定は転送先の。preserveHost なら元の）
		String originalHost = originalHost(context);
		put(headers, "Host", preserveHost && !originalHost.isEmpty() ? originalHost : forwardHost());

		addForwardedHeaders(context, headers, originalHost);

		// 最後に、アプリが決めたもの（Host も含めて上書きできる）
		for (Map.Entry<String, Function<WebContext, String>> entry : setHeaders.entrySet()) {

			String value = entry.getValue().apply(context);

			remove(headers, entry.getKey());

			if (value != null && !value.isEmpty()) {
				if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
					throw new IllegalArgumentException("ヘッダ " + entry.getKey() + " の値に改行があります");
				}
				headers.put(entry.getKey(), new ArrayList<>(List.of(value)));
			}

		}

		// 消すと決めたものは、足したあとでも消す
		for (String name : removeHeaders) {
			remove(headers, name);
		}

		return headers;

	}

	/**
	 * 受けたサーバー（Helidon）が解く圧縮か
	 */
	private static boolean isDecodedByServer (String encoding) {

		String value = encoding == null ? "" : encoding.trim().toLowerCase(Locale.ROOT);

		return value.equals("gzip") || value.equals("x-gzip") || value.equals("deflate");

	}

	/**
	 * 大文字小文字を見ずにキーを探す
	 */
	private static String findKey (Map<String, List<String>> headers, String name) {

		for (String key : headers.keySet()) {
			if (key.equalsIgnoreCase(name)) {
				return key;
			}
		}

		return name;

	}

	/**
	 * X-Forwarded-* を足す
	 */
	private void addForwardedHeaders (WebContext context, Map<String, List<String>> headers, String originalHost) {

		/*
		 * <b>既存の値の後ろに足す</b>。移送元は上書きしていたので、
		 * 多段のプロキシを通すと途中の経路が消えた。
		 * X-Forwarded-For は名乗るだけの値なので、使う側は信じる段数を決めること（要件 F-H-02）。
		 * クライアントが送ってきた X-Forwarded-For がそのまま残り、
		 * その後ろにこのサーバーが見た接続元を足す。
		 */

		// 移送元は getProtocol()（= HTTP/1.1）を入れていた
		put(headers, "X-Forwarded-Proto", context.request().scheme());

		// ポートまで含めたいので Host ヘッダを使う
		put(headers, "X-Forwarded-Host", originalHost.isEmpty() ? context.request().host() : originalHost);

		put(headers, "X-Forwarded-Port", String.valueOf(context.request().port()));

		put(headers, "X-Real-IP", context.request().address());

		// 多段のときに経路が消えないよう、既存の値の後ろに足す
		String existing = context.request().header().getStringOptional("x-forwarded-for");
		String forwardedFor = existing.isEmpty()
			? context.request().address()
			: existing + ", " + context.request().address();

		put(headers, "X-Forwarded-For", forwardedFor);

	}

	/**
	 * 元の Host ヘッダ（無ければ空）
	 */
	private static String originalHost (WebContext context) {

		return context.request().header().getStringOptional("host");

	}

	/**
	 * 転送先の Host（既定のポートなら付けない）
	 */
	private String forwardHost () {

		int port = forwardUri.getPort();
		boolean secure = "https".equalsIgnoreCase(forwardUri.getScheme());

		if (port < 0 || (secure && port == 443) || (!secure && port == 80)) {
			return forwardUri.getHost();
		}

		return forwardUri.getHost() + ":" + port;

	}

	/**
	 * リクエストの最初の値（大文字小文字を問わない）
	 */
	private static String firstHeader (WebContext context, String name) {

		for (Map.Entry<String, List<String>> entry : context.request().source().headerValues().entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
				return entry.getValue().getFirst();
			}
		}

		return null;

	}

	/**
	 * 置き換える（大文字小文字を問わない）
	 */
	private static void put (Map<String, List<String>> headers, String name, String value) {

		remove(headers, name);
		headers.put(name, new ArrayList<>(List.of(value)));

	}

	/**
	 * 消す（大文字小文字を問わない）
	 */
	private static void remove (Map<String, List<String>> headers, String name) {

		headers.keySet().removeIf(key -> key.equalsIgnoreCase(name));

	}

	// endregion

	// region レスポンス

	/**
	 * 転送先の応答を返す
	 */
	private void sendResponse (WebContext context, UpstreamConnection response) throws Exception {

		// 移送元はここが抜けていて、転送先が何を返しても 200 になっていた
		context.response().code(response.status());

		String visiblePrefix = visiblePrefix(context);

		for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {

			String name = entry.getKey();
			String lower = name.toLowerCase(Locale.ROOT);

			if (!HopByHopHeaders.isForwardable(name) || hideResponseHeaders.contains(lower)) {
				continue;
			}

			for (String value : entry.getValue()) {

				String rewritten = switch (lower) {
					case "location" -> rewriteRedirect(value, visiblePrefix);
					case "refresh" -> rewriteRefresh(value, visiblePrefix);
					case "set-cookie" -> rewriteCookie(value);
					default -> value;
				};

				// <b>足す</b>（Set-Cookie のように同じ名前が何行もある。上書きすると最後の1つしか届かない）
				context.response().addResponseHeader(name, rewritten);

			}

		}

		// 本文を持てないステータスに本文を書くと、サーバー実装によっては例外になる
		if (isBodyless(response.status()) || "HEAD".equals(context.request().method())) {
			context.response().send(response.status());
			return;
		}

		context.response().send(response.body());

	}

	/**
	 * ブラウザから見えるプロキシの入口（{@code /api/x} を受けたなら {@code /api}）
	 */
	private static String visiblePrefix (WebContext context) {

		String path = context.request().path();
		String wildcard = context.route() == null ? "" : context.route().variables().wildcard();

		if (path == null) {
			return "";
		}

		if (wildcard != null && !wildcard.isEmpty() && path.endsWith("/" + wildcard)) {
			path = path.substring(0, path.length() - wildcard.length() - 1);
		}

		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

	}

	/**
	 * Location を書き換える
	 *
	 * <p>
	 * (1) {@link #redirect(String, String)} で足したもの（前から順）、
	 * (2) 既定：転送先の URL（{@code http://backend:8080}）で始まるものを、ブラウザから見えるパス（{@code /api}）に。
	 * <b>相対の値（{@code /moved}）はそのまま</b>（nginx の {@code proxy_redirect default} と同じ）。
	 * </p>
	 */
	String rewriteRedirect (String value, String visiblePrefix) {

		for (String[] rule : redirects) {
			if (value.startsWith(rule[0])) {
				return rule[1] + value.substring(rule[0].length());
			}
		}

		if (!redirectDefault || forwardBaseUrl.isEmpty()) {
			return value;
		}

		if (value.equals(forwardBaseUrl)) {
			return visiblePrefix.isEmpty() ? "/" : visiblePrefix;
		}

		if (value.startsWith(forwardBaseUrl + "/") || value.startsWith(forwardBaseUrl + "?")) {
			String rest = value.substring(forwardBaseUrl.length());
			return (visiblePrefix + rest).isEmpty() ? "/" : visiblePrefix + rest;
		}

		return value;

	}

	/**
	 * Refresh（{@code 5; url=http://...}）の url を書き換える
	 */
	private String rewriteRefresh (String value, String visiblePrefix) {

		int at = value.toLowerCase(Locale.ROOT).indexOf("url=");

		if (at < 0) {
			return value;
		}

		String head = value.substring(0, at + 4);
		String url = value.substring(at + 4).trim();

		return head + rewriteRedirect(url, visiblePrefix);

	}

	/**
	 * Set-Cookie の Domain / Path を書き換える
	 */
	String rewriteCookie (String value) {

		if (cookieDomains.isEmpty() && cookiePaths.isEmpty()) {
			return value;
		}

		String[] parts = value.split(";", -1);
		StringBuilder out = new StringBuilder(parts[0]);

		for (int i = 1; i < parts.length; i++) {

			String part = parts[i];
			String trimmed = part.trim();
			int eq = trimmed.indexOf('=');
			String attr = (eq < 0 ? trimmed : trimmed.substring(0, eq)).trim();
			String attrValue = eq < 0 ? "" : trimmed.substring(eq + 1).trim();

			if ("domain".equalsIgnoreCase(attr)) {
				String replaced = rewriteDomain(attrValue);
				out.append(replaced.isEmpty() ? "" : "; " + attr + "=" + replaced);
			} else if ("path".equalsIgnoreCase(attr)) {
				out.append("; ").append(attr).append('=').append(rewritePath(attrValue));
			} else {
				out.append(';').append(part);
			}

		}

		return out.toString();

	}

	/**
	 * Domain を書き換える（空を返したら Domain を消す）
	 */
	private String rewriteDomain (String domain) {

		String bare = domain.startsWith(".") ? domain.substring(1) : domain;

		for (String[] rule : cookieDomains) {
			String from = rule[0].startsWith(".") ? rule[0].substring(1) : rule[0];
			if (bare.equalsIgnoreCase(from)) {
				return rule[1];
			}
		}

		return domain;

	}

	/**
	 * Path を書き換える
	 */
	private String rewritePath (String path) {

		for (String[] rule : cookiePaths) {
			if (path.startsWith(rule[0])) {
				return rule[1] + path.substring(rule[0].length());
			}
		}

		return path;

	}

	private static boolean isBodyless (int statusCode) {

		return statusCode < 200 || statusCode == 204 || statusCode == 304;

	}

	// endregion

	private static String trimTrailingSlash (String url) {

		if (url == null || url.isEmpty()) {
			return "";
		}

		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

	}

	// region ルートへの組み込み

	/**
	 * ルートに組み込む
	 *
	 * @param routePath			受けるパス（{@code /api}）
	 * @param forwardBaseUrl	転送先のベース URL
	 * @return	Controller
	 */
	public static Controller mount (String routePath, String forwardBaseUrl) {

		return mount(routePath, new ReverseProxy(forwardBaseUrl));

	}

	/**
	 * 設定したプロキシを組み込む（{@link #preserveHost()} などを使うとき）
	 *
	 * @param routePath	受けるパス（{@code /api}）
	 * @param proxy		設定を済ませたプロキシ
	 * @return	Controller
	 */
	public static Controller mount (String routePath, ReverseProxy proxy) {

		// 1つだけ作って使い回す（設定は作ったときに決まっている）
		return mount(routePath, context -> proxy);

	}

	/**
	 * リクエストごとに転送先を選んで組み込む
	 *
	 * @param routePath	受けるパス
	 * @param factory	リクエストから転送先を決めるもの。作り置きしたものを返すこと
	 * @return	Controller
	 */
	public static Controller mount (String routePath, Function<WebContext, ReverseProxy> factory) {

		return new ReverseProxyController(routePath, factory);

	}

	// endregion

}
