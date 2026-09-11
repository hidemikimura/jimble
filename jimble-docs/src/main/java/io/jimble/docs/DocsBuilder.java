package io.jimble.docs;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.jimble.util.data.Data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * ドキュメントサイトを作る（要件 NF-D-01）
 *
 * <pre>
 * ./gradlew :jimble-docs:site
 * </pre>
 *
 * <p>
 * <b>npm も外部の SSG も使わない。</b>
 * jimble のテンプレート（jte）で静的 HTML を吐くだけである。
 * 「ドキュメントサイト自体が jimble で動いている」ことが、そのまま実例になる。
 * </p>
 *
 * <p>
 * 読む人のブラウザで動く JavaScript は<b>検索の 40 行だけ</b>で、
 * 外部の CDN には1つも繋がない。
 * </p>
 */
public final class DocsBuilder {

	/** 言語 */
	private static final List<String> LANGUAGES = List.of("ja", "en");

	/** 既定の言語 */
	private static final String DEFAULT_LANGUAGE = "ja";

	/** 公開先。sitemap と canonical に使う */
	private static final String SITE_URL = "https://jimble.io";


	private DocsBuilder () {}

	/**
	 * エントリポイント
	 *
	 * @param args	{@code <リポジトリのルート> <出力先>}
	 * @throws Exception	作れなかった場合
	 */
	public static void main (String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("使い方: DocsBuilder <リポジトリのルート> <出力先>");
			System.exit(1);
			return;
		}

		Path root = Path.of(args[0]);
		Path out = Path.of(args[1]);

		build(root, out);

	}

	/**
	 * 作る
	 *
	 * @param root	リポジトリのルート
	 * @param out	出力先
	 * @throws IOException	作れなかった場合
	 */
	public static void build (Path root, Path out) throws IOException {

		long start = System.currentTimeMillis();

		/*
		 * 1. 実コードからコード片を集める（要件 NF-D-03）。
		 *    サンプルとテストの両方から抜く。
		 *    テストから抜けるのが大きい。「動く証拠」がそのまま載る。
		 */
		Snippets snippets = Snippets.collect(List.of(
			root.resolve("examples")
			, root.resolve("jimble-web/src/test")
			, root.resolve("jimble-db/src/test")
			, root.resolve("jimble-util/src/test")
			, root.resolve("jimble-mcp/src/test")
			, root.resolve("docs/site/snippets")
		));

		System.out.println("コード片: %d 件".formatted(snippets.size()));


		TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Html);

		if (Files.exists(out)) {
			delete(out);
		}

		Files.createDirectories(out);

		/*
		 * 2. 先に全部の言語を読む。
		 *    言語の切り替えリンクは「中身がある言語」だけ出す。
		 *    まだ無い言語へのリンクを出すと、そこが 404 になる。
		 */
		Map<String, List<Page>> byLanguage = new LinkedHashMap<>();

		for (String language : LANGUAGES) {

			Path source = root.resolve("docs/site").resolve(language);

			if (!Files.isDirectory(source)) {
				continue;
			}

			/*
			 * 注記の見出し（補足 / 注意 …）は言語で変わるので、
			 * Markdown も言語ごとに作る（要件 NF-D-06）。
			 */
			List<Page> pages = readAll(source, new Markdown(snippets, language), language);

			if (pages.isEmpty()) {
				continue;
			}

			byLanguage.put(language, pages);

		}

		List<String> languages = List.copyOf(byLanguage.keySet());

		int total = 0;

		for (Map.Entry<String, List<Page>> entry : byLanguage.entrySet()) {

			String language = entry.getKey();
			List<Page> pages = entry.getValue();

			SiteNav nav = new SiteNav(pages, Texts.sections(language));

			Path target = out.resolve(language);
			Files.createDirectories(target);

			for (Page page : pages) {
				write(engine, target.resolve(page.slug() + ".html"), page, nav, language, languages);
				writeMarkdown(target.resolve(page.slug() + ".md"), page, language);
			}

			writeSearchIndex(target, pages);
			writeLlms(target, pages, language);

			total += pages.size();

			System.out.println("%s: %d ページ".formatted(language, pages.size()));

		}

		copyStatic(root.resolve("docs/site/static"), out.resolve("static"));

		writeLlmsIndex(out, byLanguage);

		// / に来た人を既定の言語へ送る
		Files.writeString(out.resolve("index.html"), """
			<!doctype html>
			<html lang="%1$s">
			<head>
			<meta charset="utf-8">
			<meta http-equiv="refresh" content="0; url=./%1$s/">
			<link rel="canonical" href="./%1$s/">
			<title>jimble</title>
			</head>
			<body><a href="./%1$s/">jimble</a></body>
			</html>
			""".formatted(DEFAULT_LANGUAGE), StandardCharsets.UTF_8);

		writeHosting(out, byLanguage);

		System.out.println("できました: %s（%d ページ / %dms）"
			.formatted(out.toAbsolutePath(), total, System.currentTimeMillis() - start));

		/*
		 * リンクは拡張子なし（/ja/routing）である。Cloudflare Pages がそれを
		 * ja/routing.html に対応させる。ファイルを直接開くとリンクが辿れないので、
		 * 手元で見るときはサーバー越しにする。
		 */
		System.out.println("手元で見る: cd %s && python3 -m http.server 8080".formatted(out));

	}

	// region 読む

	/**
	 * ページを読む
	 *
	 * @param source	置き場所
	 * @param markdown	変換するもの
	 * @return	ページ
	 * @throws IOException	読めなかった場合
	 */
	private static List<Page> readAll (Path source, Markdown markdown, String language) throws IOException {

		List<Page> pages = new ArrayList<>();

		try (Stream<Path> paths = Files.list(source)) {

			List<Path> files = paths
				.filter(path -> path.getFileName().toString().endsWith(".md"))
				.sorted()
				.toList();

			for (Path file : files) {
				pages.add(read(file, markdown, language));
			}

		}

		return pages;

	}

	/**
	 * 1ページ読む
	 *
	 * @param file		ファイル
	 * @param markdown	変換するもの
	 * @param language	言語
	 * @return	ページ
	 * @throws IOException	読めなかった場合
	 */
	private static Page read (Path file, Markdown markdown, String language) throws IOException {

		String content = Files.readString(file, StandardCharsets.UTF_8);

		Map<String, String> front = new LinkedHashMap<>();
		String body = content;

		/*
		 * front matter。--- で囲んだ key: value。
		 * YAML のパーサは足さない（3種類しか読まない）。
		 */
		if (content.startsWith("---\n")) {

			int end = content.indexOf("\n---\n", 4);

			if (end > 0) {

				for (String line : content.substring(4, end).split("\n")) {

					int colon = line.indexOf(':');

					if (colon > 0) {
						front.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
					}

				}

				body = content.substring(end + 5);

			}

		}

		String slug = file.getFileName().toString().replaceFirst("\\.md$", "");

		String title = front.getOrDefault("title", slug);

		if (front.get("title") == null) {
			throw new IllegalStateException("title がありません: " + file);
		}

		return new Page(
			slug
			, title
			, front.getOrDefault("summary", "")
			, front.getOrDefault("section", Texts.otherSection(language))
			, Integer.parseInt(front.getOrDefault("order", "999"))
			, markdown.toHtml(body)
			, markdown.headings(body)
			, Markdown.plainText(body)
			, markdown.toMarkdown(body)
		);

	}

	// endregion

	// region 書く

	/**
	 * 1ページ書く
	 *
	 * @param engine	テンプレート
	 * @param target	出力先
	 * @param page		ページ
	 * @param nav		目次
	 * @param language	言語
	 * @param languages	中身がある言語（切り替えリンクに出すもの）
	 * @throws IOException	書けなかった場合
	 */
	private static void write (
		TemplateEngine engine, Path target, Page page, SiteNav nav
		, String language, List<String> languages) throws IOException {

		Map<String, Object> model = new LinkedHashMap<>();
		model.put("page", page);
		model.put("nav", nav);
		model.put("language", language);
		model.put("languages", languages);
		model.put("text", Texts.ui(language));

		StringOutput output = new StringOutput();
		engine.render("docs/page.jte", model, output);

		Files.writeString(target, output.toString(), StandardCharsets.UTF_8);

	}

	/**
	 * ページ1枚を Markdown で書く（要件 NF-D-09）
	 *
	 * <p>
	 * <b>HTML と同じ場所に、同じ名前で置く。</b>
	 * {@code /ja/db} を読んだ人が {@code /ja/db.md} を推測できる形にしておくと、
	 * <b>目次を引き直さずに素の文字へ辿り着ける</b>。
	 * </p>
	 *
	 * <p>
	 * front matter は出さない。代わりに<b>元のページの URL</b>を先頭に置く——
	 * <b>切り出して貼られたときに、どこから来たか分からなくなる</b>のを防ぐため。
	 * </p>
	 *
	 * @param target	出力先
	 * @param page		ページ
	 * @param language	言語
	 * @throws IOException	書けなかった場合
	 */
	private static void writeMarkdown (Path target, Page page, String language) throws IOException {

		String header = "<!-- %s/%s/%s -->\n".formatted(SITE_URL, language, page.slug());

		Files.writeString(target, header + page.markdown(), StandardCharsets.UTF_8);

	}

	/**
	 * 言語ごとの {@code llms.txt} と {@code llms-full.txt} を書く（要件 NF-D-09）
	 *
	 * <p>
	 * <b>{@code llms.txt} は目次だけ、{@code llms-full.txt} は全文。</b>
	 * 目次を読んで要るページだけ取りに来る道と、1回で全部持っていく道の
	 * <b>両方を残す</b>——相手の道具がどちらを選ぶかは、こちらでは決められない。
	 * </p>
	 *
	 * <p>
	 * <b>並びは目次と同じ</b>（{@link SiteNav}）。
	 * ここで別の順序を持つと、<b>ページを足したときに2か所直すことになる</b>。
	 * </p>
	 *
	 * @param target	出力先
	 * @param pages		ページ
	 * @param language	言語
	 * @throws IOException	書けなかった場合
	 */
	private static void writeLlms (Path target, List<Page> pages, String language) throws IOException {

		SiteNav nav = new SiteNav(pages, Texts.sections(language));

		String summary = pages.stream()
			.filter(page -> "index".equals(page.slug()))
			.map(Page::summary)
			.filter(text -> !text.isEmpty())
			.findFirst()
			.orElse("A web framework for Java");

		StringBuilder index = new StringBuilder("# jimble\n\n> " + summary + "\n");
		StringBuilder full = new StringBuilder("# jimble\n\n> " + summary + "\n");

		for (Map.Entry<String, List<Page>> section : nav.sections().entrySet()) {

			index.append("\n## ").append(section.getKey()).append("\n\n");

			for (Page page : section.getValue()) {

				index.append("- [%s](%s/%s/%s.md)%s\n".formatted(
					page.title(), SITE_URL, language, page.slug()
					, page.summary().isEmpty() ? "" : ": " + page.summary()));

				full.append("\n\n---\n\n<!-- %s/%s/%s -->\n\n"
					.formatted(SITE_URL, language, page.slug()));
				full.append(page.markdown());

			}

		}

		Files.writeString(target.resolve("llms.txt"), index.toString(), StandardCharsets.UTF_8);
		Files.writeString(target.resolve("llms-full.txt"), full.toString(), StandardCharsets.UTF_8);

	}

	/**
	 * 根の {@code llms.txt} を書く（要件 NF-D-09）
	 *
	 * <p>
	 * <b>言語を選ばせるだけ。</b>ここに全ページを並べると、
	 * <b>日本語と英語が混ざった目次</b>になり、どちらを読めばよいか分からなくなる。
	 * </p>
	 *
	 * @param out			出力先
	 * @param byLanguage	言語ごとのページ
	 * @throws IOException	書けなかった場合
	 */
	private static void writeLlmsIndex (Path out, Map<String, List<Page>> byLanguage) throws IOException {

		StringBuilder index = new StringBuilder("""
			# jimble

			> Java の Web アプリケーションフレームワーク。注釈も DI も使わない。
			> A web framework for Java. No annotations, no dependency injection.

			## Docs

			""");

		for (Map.Entry<String, List<Page>> entry : byLanguage.entrySet()) {
			index.append("- [%s](%s/%s/llms.txt): %d pages. Full text: %s/%s/llms-full.txt\n"
				.formatted(entry.getKey(), SITE_URL, entry.getKey(), entry.getValue().size()
					, SITE_URL, entry.getKey()));
		}

		index.append("""

			## Notes

			- Every page is also served as Markdown at the same path with a `.md` suffix
			  (for example %s/ja/routing.md).
			- Source: https://github.com/hidemikimura/jimble
			""".formatted(SITE_URL));

		Files.writeString(out.resolve("llms.txt"), index.toString(), StandardCharsets.UTF_8);

	}

	/**
	 * 検索の索引を書く
	 *
	 * <p>
	 * <b>検索エンジンを足さない。</b>30 ページ程度なら、
	 * 索引を丸ごと配って素の JavaScript で絞り込めば足りる。
	 * </p>
	 *
	 * @param target	出力先
	 * @param pages		ページ
	 * @throws IOException	書けなかった場合
	 */
	private static void writeSearchIndex (Path target, List<Page> pages) throws IOException {

		List<Data> entries = new ArrayList<>();

		for (Page page : pages) {

			entries.add(new Data()
				.putData("slug", page.slug())
				.putData("title", page.title())
				.putData("summary", page.summary())
				.putData("text", page.text()));

		}

		Data index = new Data();
		index.put("pages", entries);

		Files.writeString(target.resolve("search-index.json"), index.getJsonString(), StandardCharsets.UTF_8);

	}

	/**
	 * 配る側が要るものを書く
	 *
	 * <p>
	 * Cloudflare Pages（要件 NF-D-01）に載せる前提のファイルを一緒に出す。
	 * <b>手で足さない。</b>手で足すと、生成し直すたびに消えて、
	 * 「なぜか転送されない」「なぜか検索に出ない」が起きる。
	 * </p>
	 *
	 * <ul>
	 *   <li>{@code _redirects} … / を既定の言語へ送る</li>
	 *   <li>{@code _headers} … 安全側のヘッダとキャッシュ。*.pages.dev は検索避け</li>
	 *   <li>{@code sitemap.xml} / {@code robots.txt}</li>
	 *   <li>{@code 404.html}</li>
	 * </ul>
	 *
	 * @param out			出力先
	 * @param byLanguage	言語ごとのページ
	 * @throws IOException	書けなかった場合
	 */
	private static void writeHosting (Path out, Map<String, List<Page>> byLanguage) throws IOException {

		/*
		 * / に来た人を既定の言語へ送る。
		 * index.html にも meta refresh を置いてあるが、
		 * そちらは「ファイルを直接開いたとき」のための保険である。
		 */
		Files.writeString(out.resolve("_redirects"), """
			/    /%1$s/    302
			""".formatted(DEFAULT_LANGUAGE), StandardCharsets.UTF_8);

		/*
		 * 外部に一切繋がないサイトなので、CSP は self だけで足りる。
		 * static は名前に版が入っていないので、長く持たせない
		 * （持たせると、直したのに古い CSS が出続ける）。
		 */
		Files.writeString(out.resolve("_headers"), """
			/*
			  X-Content-Type-Options: nosniff
			  X-Frame-Options: DENY
			  Referrer-Policy: strict-origin-when-cross-origin
			  Content-Security-Policy: default-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'

			/static/*
			  Cache-Control: public, max-age=600

			# AI に読ませる素の文字（要件 NF-D-09）。
			# * は / をまたぐので、これだけで /ja/db.md も入る。
			# ブラウザで開いたときに「ダウンロード」にならないよう text/plain にする。
			/*.md
			  Content-Type: text/plain; charset=utf-8

			/*.txt
			  Content-Type: text/plain; charset=utf-8

			https://:project.workers.dev/*
			  X-Robots-Tag: noindex

			https://:project.pages.dev/*
			  X-Robots-Tag: noindex
			""", StandardCharsets.UTF_8);

		/* sitemap */
		StringBuilder sitemap = new StringBuilder("""
			<?xml version="1.0" encoding="UTF-8"?>
			<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
			""");

		for (Map.Entry<String, List<Page>> entry : byLanguage.entrySet()) {
			for (Page page : entry.getValue()) {
				/*
				 * 拡張子は付けない。Cloudflare Pages は .html を落とした形へ
				 * 307 で正規化するので、付けると sitemap の全件が転送になる。
				 */
				sitemap.append("\t<url><loc>%s/%s/%s</loc></url>%n"
					.formatted(SITE_URL, entry.getKey(), page.slug()));
			}
		}

		sitemap.append("</urlset>\n");

		Files.writeString(out.resolve("sitemap.xml"), sitemap.toString(), StandardCharsets.UTF_8);

		Files.writeString(out.resolve("robots.txt"), """
			User-agent: *
			Allow: /

			Sitemap: %s/sitemap.xml
			""".formatted(SITE_URL), StandardCharsets.UTF_8);

		/* 404。Cloudflare Pages は見つからないパスでこれを返す */
		Files.writeString(out.resolve("404.html"), """
			<!doctype html>
			<html lang="%1$s">
			<head>
			<meta charset="utf-8">
			<meta name="viewport" content="width=device-width, initial-scale=1">
			<meta name="robots" content="noindex">
			<title>ページがありません - jimble</title>
			<link rel="stylesheet" href="/static/site.css">
			</head>
			<body>
			<article class="notfound">
			<h1>ページがありません</h1>
			<p>お探しのページは、移動したか、もう無いようです。</p>
			<p><a href="/%1$s/">jimble のドキュメントへ</a></p>
			</article>
			</body>
			</html>
			""".formatted(DEFAULT_LANGUAGE), StandardCharsets.UTF_8);

	}

	/**
	 * 静的ファイルを写す
	 *
	 * @param from	元
	 * @param to	先
	 * @throws IOException	写せなかった場合
	 */
	private static void copyStatic (Path from, Path to) throws IOException {

		if (!Files.isDirectory(from)) {
			return;
		}

		Files.createDirectories(to);

		try (Stream<Path> paths = Files.list(from)) {

			for (Path path : paths.toList()) {
				Files.copy(path, to.resolve(path.getFileName())
					, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}

		}

	}

	/**
	 * 消す
	 *
	 * @param path	パス
	 * @throws IOException	消せなかった場合
	 */
	private static void delete (Path path) throws IOException {

		try (Stream<Path> paths = Files.walk(path)) {

			for (Path target : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(target);
			}

		}

	}

	// endregion

}
