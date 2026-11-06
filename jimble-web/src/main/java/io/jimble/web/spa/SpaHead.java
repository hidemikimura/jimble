package io.jimble.web.spa;

import io.jimble.util.json.Dson;
import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPA の index.html の {@code <head>} に、title・meta・OGP・canonical を入れる
 *
 * <p>
 * <b>テンプレートエンジンを使わない。</b>ビルドした index.html（Vite などが出したもの）をそのまま読み、
 * {@code <head>} の中に同じタグ（同じ title・同じ {@code name}・同じ {@code property}・canonical）があれば差し替え、
 * 無ければ {@code </head>} の前に足す。index.html に印や独自の属性を書かなくてよい。
 * </p>
 *
 * <pre>
 * install(() -&gt; SpaHandler.mount("/", "app", spa -&gt; spa
 *     .route("/items/{id}", SpaHead.rewriter(context -&gt; {
 *         Item item = items.find(context.request().bodyPath().getLong("id"));
 *         return SpaHead.of()
 *             .title(item.name())
 *             .description(item.summary())
 *             .canonical("https://shop.example/items/" + item.id())
 *             .og("type", "product")
 *             .og("image", item.imageUrl());
 *     }))
 * ));
 * </pre>
 *
 * <p>
 * <b>書き換えるのはサーバーが返す最初の HTML だけ</b>で、{@code <body>} にも JS にも触らない。
 * 2 ページ目からはブラウザの中で描くので、CSR の動きは変わらない。プレビューの Bot（Slack・X・LINE など）は
 * JS を動かさないので、ここで入れた OGP だけを見る。
 * </p>
 *
 * <p>
 * 値はすべてエスケープする（{@link SpaRewriter#escapeHtml(String)}。JSON-LD は {@code <} を {@code \u003c} にする）。
 * 同じものを足すと、あとから足したほうが勝つ。<b>足すたびに新しい {@code SpaHead} を返す</b>（元は変えない）。
 * </p>
 */
public final class SpaHead {

	/* 入れるタグ（足した順） */
	private final List<Tag> tags;

	private SpaHead (List<Tag> tags) {

		this.tags = List.copyOf(tags);

	}

	/**
	 * 空から作る
	 *
	 * @return	何も入れない SpaHead
	 */
	public static SpaHead of () {

		return new SpaHead(List.of());

	}

	/**
	 * SPA のパスの書き換えにする
	 *
	 * <p>{@code head} が null を返したら、index.html をそのまま返す（見つからないものなど）。</p>
	 *
	 * @param head	リクエストから SpaHead を作る
	 * @return	書き換え
	 */
	public static SpaRewriter rewriter (Function<WebContext, SpaHead> head) {

		return (context, html) -> {
			SpaHead value = head.apply(context);
			return value == null ? html : value.apply(html);
		};

	}

	// region 足す

	/**
	 * {@code <title>}
	 *
	 * @param title	題
	 * @return	足した SpaHead
	 */
	public SpaHead title (String title) {

		return with(new Tag(Kind.TITLE, "", "<title>" + SpaRewriter.escapeHtml(title) + "</title>"));

	}

	/**
	 * {@code <meta name="description">}
	 *
	 * @param description	説明
	 * @return	足した SpaHead
	 */
	public SpaHead description (String description) {

		return meta("description", description);

	}

	/**
	 * {@code <meta name="..." content="...">}（robots・keywords など）
	 *
	 * @param name		name
	 * @param content	content
	 * @return	足した SpaHead
	 */
	public SpaHead meta (String name, String content) {

		return with(new Tag(Kind.NAME, name, metaTag("name", name, content)));

	}

	/**
	 * {@code <meta property="og:..." content="...">}
	 *
	 * @param property	{@code og:} のあと（{@code title}・{@code image} など）
	 * @param content	content
	 * @return	足した SpaHead
	 */
	public SpaHead og (String property, String content) {

		return property("og:" + property, content);

	}

	/**
	 * {@code <meta name="twitter:..." content="...">}（X のカード。{@code name} で書く）
	 *
	 * @param name		{@code twitter:} のあと（{@code card} など）
	 * @param content	content
	 * @return	足した SpaHead
	 */
	public SpaHead twitter (String name, String content) {

		return meta("twitter:" + name, content);

	}

	/**
	 * {@code <meta property="..." content="...">}（{@code og:} 以外の property。{@code fb:app_id} など）
	 *
	 * @param property	property
	 * @param content	content
	 * @return	足した SpaHead
	 */
	public SpaHead property (String property, String content) {

		return with(new Tag(Kind.PROPERTY, property, metaTag("property", property, content)));

	}

	/**
	 * {@code <link rel="canonical" href="...">}
	 *
	 * @param url	正規の URL
	 * @return	足した SpaHead
	 */
	public SpaHead canonical (String url) {

		return with(new Tag(Kind.CANONICAL, "", "<link rel=\"canonical\" href=\"" + SpaRewriter.escapeHtml(url) + "\">"));

	}

	/**
	 * 構造化データ（{@code <script type="application/ld+json">}）
	 *
	 * <p>
	 * 文字列ならそのまま、ほかは JSON にする（{@code Data}・{@code Map} など）。
	 * いつも足す（差し替えない）。{@code </script>} で閉じられないよう、{@code <} を {@code \u003c} にする。
	 * </p>
	 *
	 * @param value	JSON の文字列か、JSON にするもの
	 * @return	足した SpaHead
	 */
	public SpaHead jsonLd (Object value) {

		String json = value instanceof String text ? text : Dson.encodes(value);

		return with(new Tag(Kind.JSON_LD, "",
			"<script type=\"application/ld+json\">" + json.replace("<", "\\u003c") + "</script>"));

	}

	// endregion

	/**
	 * index.html に入れる
	 *
	 * <p>{@code </head>} が無ければ、そのまま返す。</p>
	 *
	 * @param html	index.html
	 * @return	入れたもの
	 */
	public String apply (String html) {

		int headEnd = html.toLowerCase(Locale.ROOT).indexOf("</head>");

		if (headEnd < 0 || tags.isEmpty()) {
			return html;
		}

		String head = html.substring(0, headEnd);
		StringBuilder added = new StringBuilder();

		for (Tag tag : latestOnly()) {

			Matcher existing = tag.kind() == Kind.JSON_LD ? null : tag.kind().pattern(tag.key()).matcher(head);

			if (existing != null && existing.find()) {
				head = head.substring(0, existing.start()) + tag.html() + head.substring(existing.end());
			} else {
				added.append(tag.html());
			}

		}

		return head + added + html.substring(headEnd);

	}

	/**
	 * 入れるタグ（同じものは、あとから足したほうだけ）
	 *
	 * @return	タグ
	 */
	List<Tag> latestOnly () {

		List<Tag> result = new ArrayList<>();

		for (Tag tag : tags) {
			if (tag.kind() != Kind.JSON_LD) {
				result.removeIf(other -> other.kind() == tag.kind() && other.key().equalsIgnoreCase(tag.key()));
			}
			result.add(tag);
		}

		return result;

	}

	private SpaHead with (Tag tag) {

		List<Tag> next = new ArrayList<>(tags);
		next.add(tag);

		return new SpaHead(next);

	}

	private static String metaTag (String attribute, String key, String content) {

		return "<meta " + attribute + "=\"" + SpaRewriter.escapeHtml(key) + "\" content=\"" + SpaRewriter.escapeHtml(content) + "\">";

	}

	/**
	 * タグ
	 *
	 * @param kind	種類
	 * @param key	同じものを見分ける名前（name・property。title と canonical は空）
	 * @param html	入れる HTML
	 */
	record Tag (Kind kind, String key, String html) {}

	/**
	 * タグの種類と、index.html にある同じタグの探し方
	 */
	enum Kind {

		TITLE, NAME, PROPERTY, CANONICAL, JSON_LD;

		/* 属性の値（"..." か '...'） */
		private static final String VALUE = "\\s*=\\s*([\"'])%s\\1";

		/**
		 * index.html にある同じタグを探す
		 *
		 * @param key	name・property
		 * @return	正規表現
		 */
		Pattern pattern (String key) {

			int flags = Pattern.CASE_INSENSITIVE;

			return switch (this) {
				case TITLE -> Pattern.compile("<title\\b[^>]*>.*?</title\\s*>", flags | Pattern.DOTALL);
				case NAME -> Pattern.compile("<meta\\b[^>]*\\bname" + VALUE.formatted(Pattern.quote(key)) + "[^>]*>", flags);
				case PROPERTY -> Pattern.compile("<meta\\b[^>]*\\bproperty" + VALUE.formatted(Pattern.quote(key)) + "[^>]*>", flags);
				case CANONICAL -> Pattern.compile("<link\\b[^>]*\\brel" + VALUE.formatted("canonical") + "[^>]*>", flags);
				case JSON_LD -> throw new IllegalStateException("JSON-LD は探さない（いつも足す）");
			};

		}

	}

}
