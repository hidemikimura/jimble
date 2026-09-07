package io.jimble.docs;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import org.commonmark.renderer.NodeRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Markdown を HTML にする
 *
 * <p>
 * コードブロックだけ自前で描く。理由は2つ。
 * </p>
 *
 * <ol>
 *   <li><b>{@code snippet=名前} を実コードで置き換える</b>（要件 NF-D-03）</li>
 *   <li><b>色付けを生成時に済ませる</b>（{@link Highlighter}）</li>
 * </ol>
 */
final class Markdown {

	/* パーサ */
	private final Parser parser;

	/* レンダラ */
	private final HtmlRenderer renderer;

	/* 抜いてきたコード */
	private final Snippets snippets;

	/**
	 * コンストラクタ
	 *
	 * @param snippets	抜いてきたコード
	 */
	Markdown (Snippets snippets) {

		this.snippets = snippets;

		List<org.commonmark.Extension> extensions = List.of(TablesExtension.create());

		this.parser = Parser.builder().extensions(extensions).build();

		this.renderer = HtmlRenderer.builder()
			.extensions(extensions)
			.nodeRendererFactory(CodeRenderer::new)
			.build();

	}

	/**
	 * 変換する
	 *
	 * @param markdown	Markdown
	 * @return	HTML
	 */
	String toHtml (String markdown) {

		Node document = parser.parse(markdown);

		addHeadingIds(document);

		return renderer.render(document);

	}

	/**
	 * 見出しを集める
	 *
	 * @param markdown	Markdown
	 * @return	見出し
	 */
	List<Page.Heading> headings (String markdown) {

		Node document = parser.parse(markdown);

		List<Page.Heading> headings = new ArrayList<>();
		Map<String, Integer> used = new LinkedHashMap<>();

		document.accept(new AbstractVisitor() {

			@Override
			public void visit (Heading heading) {

				if (heading.getLevel() < 2 || heading.getLevel() > 3) {
					return;
				}

				String text = textOf(heading);

				headings.add(new Page.Heading(idOf(text, used), text, heading.getLevel()));

			}

		});

		return headings;

	}

	/**
	 * 検索用に素の文字だけ取り出す
	 *
	 * @param markdown	Markdown
	 * @return	文字
	 */
	static String plainText (String markdown) {

		StringBuilder out = new StringBuilder();

		for (String line : markdown.split("\n")) {

			String trimmed = line.strip();

			// コードブロックと表の区切りは検索に要らない
			if (trimmed.startsWith("```") || trimmed.startsWith("|---") || trimmed.startsWith("---")) {
				continue;
			}

			out.append(trimmed.replaceAll("[#*`>|]", " ")).append(' ');

		}

		return out.toString().replaceAll("\\s+", " ").strip();

	}

	// region 見出しの id

	/**
	 * 見出しに id を付ける
	 *
	 * @param document	文書
	 */
	private void addHeadingIds (Node document) {

		Map<String, Integer> used = new LinkedHashMap<>();

		document.accept(new AbstractVisitor() {

			@Override
			public void visit (Heading heading) {

				heading.setSourceSpans(heading.getSourceSpans());

				// commonmark の Heading に id は無いので、独自属性として持たせる
				HeadingId.set(heading, idOf(textOf(heading), used));

			}

		});

	}

	/**
	 * 見出しの文字
	 *
	 * @param node	ノード
	 * @return	文字
	 */
	private static String textOf (Node node) {

		StringBuilder out = new StringBuilder();

		node.accept(new AbstractVisitor() {

			@Override
			public void visit (Text text) {

				out.append(text.getLiteral());

			}

			@Override
			public void visit (Code code) {

				out.append(code.getLiteral());

			}

		});

		return out.toString().strip();

	}

	/**
	 * 見出しから id を作る
	 *
	 * <p>
	 * <b>日本語をそのまま id にする。</b>ローマ字に直すと元の見出しと繋がらず、
	 * 目次のリンクを手で直すことになる。URL は percent-encoding されるが、
	 * ブラウザは表示のときに戻す。
	 * </p>
	 *
	 * @param text	見出し
	 * @param used	すでに使った id
	 * @return	id
	 */
	private static String idOf (String text, Map<String, Integer> used) {

		String base = text.toLowerCase(Locale.ROOT)
			.replaceAll("[\\s　]+", "-")
			.replaceAll("[^\\p{L}\\p{N}_-]", "");

		if (base.isEmpty()) {
			base = "section";
		}

		int count = used.merge(base, 1, Integer::sum);

		return count == 1 ? base : base + "-" + count;

	}

	// endregion

	/**
	 * コードブロックを自前で描く
	 */
	private final class CodeRenderer implements NodeRenderer {

		/* 出し先 */
		private final HtmlWriter writer;

		/**
		 * コンストラクタ
		 *
		 * @param context	文脈
		 */
		private CodeRenderer (HtmlNodeRendererContext context) {

			this.writer = context.getWriter();

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Set<Class<? extends Node>> getNodeTypes () {

			return Set.of(FencedCodeBlock.class, Heading.class);

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void render (Node node) {

			if (node instanceof Heading heading) {
				renderHeading(heading);
				return;
			}

			renderCode((FencedCodeBlock) node);

		}

		/**
		 * 見出し
		 *
		 * @param heading	見出し
		 */
		private void renderHeading (Heading heading) {

			String tag = "h" + heading.getLevel();
			String id = HeadingId.get(heading);

			writer.line();
			writer.tag(tag + (id == null ? "" : " id=\"" + Highlighter.escape(id) + "\""));

			// 中身はそのまま描く
			Node child = heading.getFirstChild();
			while (child != null) {
				Node next = child.getNext();
				renderChild(child);
				child = next;
			}

			if (id != null) {
				writer.raw("<a class=\"anchor\" href=\"#" + Highlighter.escape(id) + "\">#</a>");
			}

			writer.tag("/" + tag);
			writer.line();

		}

		/**
		 * 見出しの中身
		 *
		 * @param child	子
		 */
		private void renderChild (Node child) {

			if (child instanceof Text text) {
				writer.text(text.getLiteral());
				return;
			}

			if (child instanceof Code code) {
				writer.tag("code");
				writer.text(code.getLiteral());
				writer.tag("/code");
				return;
			}

			// 太字などは見出しでは使わない
			writer.text(textOf(child));

		}

		/**
		 * コードブロック
		 *
		 * @param block	ブロック
		 */
		private void renderCode (FencedCodeBlock block) {

			String info = block.getInfo() == null ? "" : block.getInfo().strip();

			String language = info.isEmpty() ? null : info.split("\\s+")[0];
			String body = block.getLiteral();

			// snippet=名前 があれば、実コードで置き換える（要件 NF-D-03）
			String snippet = attribute(info, "snippet");

			if (snippet != null) {
				body = snippets.get(snippet);
			}

			writer.line();
			writer.raw("<pre class=\"code\"><code class=\"lang-%s\">"
				.formatted(language == null ? "text" : Highlighter.escape(language)));
			writer.raw(Highlighter.highlight(body, language));
			writer.raw("</code></pre>");
			writer.line();

		}

		/**
		 * ``` の行から属性を読む
		 *
		 * @param info	行
		 * @param name	名前
		 * @return	値。無ければ null
		 */
		private String attribute (String info, String name) {

			for (String part : info.split("\\s+")) {

				String prefix = name + "=";

				if (part.startsWith(prefix)) {
					return part.substring(prefix.length());
				}

			}

			return null;

		}

	}

	/**
	 * 見出しの id を持ち回るための入れ物
	 */
	private static final class HeadingId {

		/** commonmark のノードに好きな値を持たせる口 */
		private static final Map<Heading, String> IDS = new java.util.IdentityHashMap<>();

		private HeadingId () {}

		/**
		 * 覚える
		 *
		 * @param heading	見出し
		 * @param id		id
		 */
		static void set (Heading heading, String id) {

			IDS.put(heading, id);

		}

		/**
		 * 引く
		 *
		 * @param heading	見出し
		 * @return	id
		 */
		static String get (Heading heading) {

			return IDS.get(heading);

		}

	}

}
