package io.jimble.web.assets;

import io.jimble.util.hash.Hash;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;
import io.jimble.web.router.HttpMethods;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 静的ファイル配信（要件 F-W-18 / F-W-20）
 *
 * <p>
 * クラスパスから配る。<b>jar 実行でもディレクトリ実行でも同じように動く。</b>
 * </p>
 *
 * <pre>
 * // /assets/** を src/main/resources/assets/** から配る
 * install(() -&gt; AssetHandler.mount("/assets", "assets"));
 * </pre>
 *
 * <p>
 * <b>ルーティング本体には混ぜない</b>（要件 F-W-20）。
 * 移送元はこの処理が {@code ExtendsJooby}（2,021 行）に埋まっていた。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>ディレクトリトラバーサルを塞いだ。</b>移送元はワイルドカードの値を
 *       そのまま連結していた（{@link AssetPath} 参照）</li>
 *   <li><b>ETag を実装した。</b>移送元はコード全体がコメントアウトされていて、
 *       {@code etagMap} だけが残っていた。要件 F-W-18 は Etag を要求している</li>
 *   <li><b>Content-Type を対応表で決める。</b>{@code URLConnection} と
 *       {@code Files.probeContentType()} は環境に依存する（{@link ContentTypes} 参照）</li>
 *   <li><b>304 でも本文を読まない。</b>ヘッダだけ返す</li>
 * </ol>
 */
public final class AssetHandler implements Handler {

	/** HTTP 日付の書式 */
	private static final DateTimeFormatter HTTP_DATE =
		DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

	/* ベースディレクトリ */
	private final String baseDir;

	/**
	 * コンストラクタ
	 *
	 * @param baseDir	クラスパス上のベースディレクトリ（例 {@code assets}）
	 * @throws IllegalArgumentException	空（{@code ""} / {@code "/"}）の場合
	 */
	public AssetHandler (String baseDir) {

		this.baseDir = AssetPath.normalizeBase(baseDir);

		/*
		 * <b>空は断る</b>（D-277）。空だとクラスパスの根から返すので、
		 * {@code /static/application.conf}（鍵・DB のパスワード）や {@code .class} がそのまま取れた
		 */
		if (this.baseDir.isEmpty()) {
			throw new IllegalArgumentException("静的ファイルの置き場が空です。クラスパスの根を返すと設定ファイルやクラスまで取れてしまうので、"
				+ "専用のディレクトリ（例 \"assets\"）を渡してください: " + baseDir);
		}

	}

	/**
	 * ベースディレクトリ
	 *
	 * @return	ベースディレクトリ
	 */
	public String baseDir () {

		return baseDir;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		String requestPath = context.route() == null ? "" : context.route().variables().wildcard();

		String resource = AssetPath.resolve(baseDir, requestPath);

		if (resource == null) {
			// トラバーサルの試み。何があるかを教えないため 404 にする
			context.response().send(404);
			return;
		}

		send(context, resource);

	}

	/**
	 * 配信する
	 *
	 * @param context	コンテキスト
	 * @param resource	クラスパス上のリソースパス
	 */
	public void send (WebContext context, String resource) {

		URL url = AssetHandler.class.getResource(resource);

		/*
		 * <b>ファイルでないもの（ディレクトリ）は 404</b>（D-238）。クラスパスが file: のとき
		 * （IDE・jimbleRun・展開して置いたもの）、かつてはディレクトリの中身の一覧を返していた
		 */
		if (url == null || !Resources.isFile(resource)) {
			context.response().send(404);
			return;
		}

		try {

			Meta meta = meta(resource, url);
			long contentLength = meta.contentLength();

			applyCacheControl(context, resource);

			String etag = meta.etag();
			String lastModifiedText = meta.lastModifiedText();

			if (etag != null) {
				context.response().setResponseHeader("ETag", etag);
			}
			if (lastModifiedText != null) {
				context.response().setResponseHeader("Last-Modified", lastModifiedText);
			}

			if (isNotModified(context, etag, lastModifiedText)) {
				// 本文は読まない
				context.response().send(304);
				return;
			}

			context.response().setResponseHeader("Content-Type", ContentTypes.of(resource));

			if (HttpMethods.HEAD.equals(context.request().method())) {
				if (contentLength >= 0) {
					context.response().setResponseHeader("Content-Length", String.valueOf(contentLength));
				}
				context.response().send(200);
				return;
			}

			try (InputStream in = open(resource, url)) {
				if (contentLength >= 0) {
					context.response().send(in, ContentTypes.of(resource), contentLength);
				} else {
					context.response().send(in, ContentTypes.of(resource));
				}
			}

		} catch (Exception ex) {

			context.response().send(404);

		}

	}

	// region 中身の情報（D-286）

	/**
	 * 長さ・更新時刻・ETag
	 *
	 * @param contentLength		長さ（分からなければ負）
	 * @param etag				ETag（使わない設定なら null）
	 * @param lastModifiedText	Last-Modified（分からなければ null）
	 */
	record Meta (long contentLength, String etag, String lastModifiedText) {}

	/* jar の中のものの情報（jar の中身は変わらないので覚えておく） */
	private static final java.util.concurrent.ConcurrentHashMap<String, Meta> JAR_META = new java.util.concurrent.ConcurrentHashMap<>();

	/** 覚えておく上限（溢れたら全部捨てる。引き直すだけなので正しさは変わらない） */
	static final int JAR_META_LIMIT = 10_000;

	/**
	 * 長さ・更新時刻・ETag を引く
	 *
	 * <p>
	 * <b>jar の中のものは覚えておく</b>。2.5.1 までは毎回 {@code URLConnection} を
	 * キャッシュなし（{@code setUseCaches(false)}）で開いていたので、<b>1リクエストごとに jar を開き直し</b>
	 * （ファイルの stat と JDK 全体の ZipFile のロック）、ETag の MD5 も毎回作っていた。304 と HEAD では本文を読まないので、
	 * 開いた jar を閉じずに GC 任せにしていた。
	 * ファイルのクラスパス（IDE・jimbleRun）は書き換わるので、これまでどおり毎回見る。
	 * </p>
	 */
	Meta meta (String resource, URL url) throws java.io.IOException {

		boolean jar = "jar".equals(url.getProtocol());

		if (jar) {
			Meta cached = JAR_META.get(resource);
			if (cached != null && cached.etag() != null == AssetConf.etag()) {
				return cached;
			}
		}

		URLConnection connection = url.openConnection();
		connection.setUseCaches(false);

		long lastModified;
		long contentLength;

		try {
			lastModified = connection.getLastModified();
			contentLength = connection.getContentLengthLong();
		} finally {
			// 本文を読まないので、開いたものをここで閉じる（jar なら JarFile）
			if (connection instanceof java.net.JarURLConnection jarConnection) {
				try {
					jarConnection.getJarFile().close();
				} catch (java.io.IOException ignore) {
					// もう閉じている
				}
			}
		}

		Meta meta = new Meta(
			contentLength
			, etag(resource, lastModified, contentLength)
			, lastModified > 0 ? HTTP_DATE.format(Instant.ofEpochMilli(lastModified)) : null);

		if (jar) {
			if (JAR_META.size() >= JAR_META_LIMIT) {
				JAR_META.clear();
			}
			JAR_META.put(resource, meta);
		}

		return meta;

	}

	/**
	 * 本文を開く
	 *
	 * <p>jar の中のものは、クラスローダが開いたままにしている jar から読む（開き直さない）。</p>
	 */
	private static InputStream open (String resource, URL url) throws java.io.IOException {

		if ("jar".equals(url.getProtocol())) {
			InputStream in = AssetHandler.class.getResourceAsStream(resource);
			if (in != null) {
				return in;
			}
		}

		URLConnection connection = url.openConnection();
		connection.setUseCaches(false);

		return connection.getInputStream();

	}

	// endregion

	// region キャッシュ

	/**
	 * Cache-Control を設定する
	 *
	 * <p>
	 * 動的レスポンスは {@code no-store}（要件 F-X-07）だが、静的配信は別扱い。
	 * ここで上書きする。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param resource	リソースパス
	 */
	private void applyCacheControl (WebContext context, String resource) {

		String value = AssetPath.isImmutable(resource)
			? "public,max-age=%d,immutable".formatted(AssetConf.immutableMaxAge().toSeconds())
			: "public,max-age=%d,must-revalidate".formatted(AssetConf.maxAge().toSeconds());

		context.response().setResponseHeader("Cache-Control", value);

	}

	/**
	 * ETag を作る
	 *
	 * @param resource		リソースパス
	 * @param lastModified	最終更新（ミリ秒）
	 * @param contentLength	サイズ
	 * @return	ETag（使わない設定なら null）
	 */
	private String etag (String resource, long lastModified, long contentLength) {

		if (!AssetConf.etag()) {
			return null;
		}

		return "W/\"%s\"".formatted(Hash.md5("%s:%d:%d".formatted(resource, lastModified, contentLength)));

	}

	/**
	 * 304 を返してよいか（条件付き GET。要件 F-W-18）
	 *
	 * <p>
	 * <b>{@code If-None-Match} を先に見る。</b>両方送られてきた場合は
	 * ETag の判定が優先される（HTTP の決まり）。
	 * </p>
	 *
	 * @param context			コンテキスト
	 * @param etag				ETag
	 * @param lastModifiedText	Last-Modified
	 * @return	変更なしの場合 = true
	 */
	private boolean isNotModified (WebContext context, String etag, String lastModifiedText) {

		String ifNoneMatch = context.request().header().getStringOptional("if-none-match");

		if (!ifNoneMatch.isEmpty()) {
			return etag != null && matchesEtag(ifNoneMatch, etag);
		}

		if (!AssetConf.ifModifiedSince() || lastModifiedText == null) {
			return false;
		}

		return lastModifiedText.equalsIgnoreCase(
			context.request().header().getStringOptional("if-modified-since"));

	}

	/**
	 * If-None-Match が一致するか
	 *
	 * <p>{@code *} と、カンマ区切りの複数指定に対応する。</p>
	 *
	 * @param ifNoneMatch	If-None-Match
	 * @param etag			ETag
	 * @return	一致する場合 = true
	 */
	private boolean matchesEtag (String ifNoneMatch, String etag) {

		if ("*".equals(ifNoneMatch.trim())) {
			return true;
		}

		for (String candidate : ifNoneMatch.split(",")) {
			if (candidate.trim().equals(etag)) {
				return true;
			}
		}

		return false;

	}

	// endregion

	/**
	 * ルートに組み込む
	 *
	 * <p>
	 * {@code GET} と {@code HEAD} を登録する。
	 * </p>
	 *
	 * <pre>
	 * install(() -&gt; AssetHandler.mount("/assets", "assets"));
	 * </pre>
	 *
	 * @param routePath	ルートのパス（例 {@code /assets}）
	 * @param baseDir	クラスパス上のベースディレクトリ（例 {@code assets}）
	 * @return	コントローラ
	 */
	public static AssetController mount (String routePath, String baseDir) {

		return new AssetController(routePath, baseDir);

	}

}
