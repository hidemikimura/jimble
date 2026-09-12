package io.jimble.web.spa;

import io.jimble.web.assets.AssetConf;
import io.jimble.web.assets.AssetHandler;
import io.jimble.web.assets.AssetPath;
import io.jimble.web.assets.Resources;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * SPA 配信（要件 F-W-17 / F-W-20）
 *
 * <p>
 * リクエストされたファイルがあればそれを返し、<b>無ければ index.html を返す。</b>
 * クライアント側のルーターに任せるための定番の形。
 * </p>
 *
 * <pre>
 * install(() -&gt; SpaHandler.mount("/app", "app"));
 * </pre>
 *
 * <p>index.html をパスごとに書き換えたいときは {@link SpaRouter} を渡す。</p>
 *
 * <pre>
 * install(() -&gt; SpaHandler.mount("/app", "app", spa -&gt; spa
 *     .route("/items/{id}", (context, html) -&gt; html.replace("&lt;!--title--&gt;", title(context)))
 * ));
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>{@code ../} を検出しても止めていなかった。</b>
 *       {@code if (path.contains("../")) { Log.error(...) }} とログを出すだけで、
 *       <b>そのまま配信に進んでいた。</b>404 で止める</li>
 *   <li><b>index.html のキャッシュが {@code HashMap} だった。</b>
 *       リクエストごとに書き込むので、同時アクセスで壊れる</li>
 *   <li>パス解決のキャッシュに上限が無かった（{@link Resources} 参照）</li>
 * </ol>
 */
public final class SpaHandler implements Handler {

	/** index のファイル名 */
	public static final String INDEX = "index.html";

	/** index.html のキャッシュ上限 */
	public static final int MAX_INDEX_CACHE_SIZE = 64;

	/* ベースディレクトリ */
	private final String baseDir;

	/* SPA ルーター（無ければ null） */
	private final SpaRouter router;

	/* 静的ファイルの配信 */
	private final AssetHandler assets;

	/* index.html の中身 */
	private final Map<String, String> indexCache = new ConcurrentHashMap<>();

	/**
	 * コンストラクタ
	 *
	 * @param baseDir	クラスパス上のベースディレクトリ
	 * @param router	SPA ルーター（無ければ null）
	 */
	public SpaHandler (String baseDir, SpaRouter router) {

		this.baseDir = AssetPath.normalizeBase(baseDir);
		this.router = router;
		this.assets = new AssetHandler(baseDir);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		String requestPath = context.route() == null ? "" : context.route().variables().wildcard();

		String resource = AssetPath.resolve(baseDir, trimTrailingSlash(requestPath));

		if (resource == null) {
			// 移送元はログを出すだけで配信に進んでいた
			context.response().send(404);
			return;
		}

		// 実ファイルがあればそれを返す
		if (Resources.isFile(resource)) {
			assets.send(context, resource);
			return;
		}

		String index = findIndex(resource);

		if (index == null) {
			context.response().send(404);
			return;
		}

		if (router != null && sendRewritten(context, index)) {
			return;
		}

		assets.send(context, index);

	}

	// region index.html

	/**
	 * index.html を探す
	 *
	 * <p>
	 * リクエストのパスから上へ順に {@code index.html} を探し、
	 * 最後はベースディレクトリ直下を見る。
	 * </p>
	 *
	 * @param resource	リクエストのリソースパス
	 * @return	index.html のパス（無ければ null）
	 */
	private String findIndex (String resource) {

		String base = baseDir.isEmpty() ? "" : "/" + baseDir;
		String current = resource;

		while (current.length() > base.length()) {

			String candidate = current + "/" + INDEX;
			if (Resources.isFile(candidate)) {
				return candidate;
			}

			int slash = current.lastIndexOf('/');
			if (slash <= 0) {
				break;
			}
			current = current.substring(0, slash);

		}

		String root = base + "/" + INDEX;

		return Resources.isFile(root) ? root : null;

	}

	/**
	 * 書き換えた index.html を返す
	 *
	 * @param context	コンテキスト
	 * @param index		index.html のパス
	 * @return	返した場合 = true
	 */
	private boolean sendRewritten (WebContext context, String index) {

		/*
		 * 生のパスを渡す。ツリーがセグメントに割ってからデコードするので、
		 * %2F を含む値がセグメントの区切りに化けない（本体のルーティングと同じ）。
		 */
		SpaRoute route = router.match(context, context.request().rawPath());

		if (route == null) {
			return false;
		}

		String html = readIndex(index);

		if (html == null) {
			return false;
		}

		String rewritten = route.rewriter().rewrite(context, html);

		// index.html は毎回確認させる（中身が変わりうる）
		context.response().setResponseHeader("Cache-Control",
			"public,max-age=%d,must-revalidate".formatted(AssetConf.maxAge().toSeconds()));
		context.response().send(rewritten, "text/html", StandardCharsets.UTF_8);

		return true;

	}

	/**
	 * index.html を読む（キャッシュする）
	 *
	 * @param index	index.html のパス
	 * @return	中身（読めなければ null）
	 */
	private String readIndex (String index) {

		String cached = indexCache.get(index);
		if (cached != null) {
			return cached;
		}

		try (InputStream in = SpaHandler.class.getResourceAsStream(index)) {

			if (in == null) {
				return null;
			}

			String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);

			if (indexCache.size() >= MAX_INDEX_CACHE_SIZE) {
				indexCache.clear();
			}
			indexCache.put(index, html);

			return html;

		} catch (Exception ex) {

			return null;

		}

	}

	// endregion

	/**
	 * 末尾の「/」を落とす
	 *
	 * @param path	パス
	 * @return	落としたもの
	 */
	private static String trimTrailingSlash (String path) {

		if (path == null) {
			return "";
		}

		String value = path;
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}

		return value;

	}

	// region ルートへの組み込み

	/**
	 * ルートに組み込む
	 *
	 * @param routePath	ルートのパス
	 * @param baseDir	クラスパス上のベースディレクトリ
	 * @return	コントローラ
	 */
	public static SpaController mount (String routePath, String baseDir) {

		return new SpaController(routePath, baseDir, null);

	}

	/**
	 * ルートに組み込む（index.html の書き換えつき）
	 *
	 * @param routePath	ルートのパス
	 * @param baseDir	クラスパス上のベースディレクトリ
	 * @param setup		SPA ルーターの組み立て
	 * @return	コントローラ
	 */
	public static SpaController mount (String routePath, String baseDir, Consumer<SpaRouter> setup) {

		SpaRouter router = new SpaRouter();
		setup.accept(router);

		return new SpaController(routePath, baseDir, router);

	}

	// endregion

}
