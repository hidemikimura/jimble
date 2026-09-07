@import io.jimble.docs.Page
@import io.jimble.docs.SiteNav
@import java.util.List
@import java.util.Map

@param Page page
@param SiteNav nav
@param String language
@param List<String> languages

!{Map<String, String> languageNames = Map.of("ja", "日本語", "en", "English");}

<!doctype html>
<html lang="${language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${page.title()} - jimble</title>
@if(!page.summary().isEmpty())
	<meta name="description" content="${page.summary()}">
@endif
<link rel="icon" href="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PHJlY3Qgd2lkdGg9IjY0IiBoZWlnaHQ9IjY0IiByeD0iMTIiIGZpbGw9IiMxYjFmMjQiLz48dGV4dCB4PSIzMiIgeT0iNDUiIGZvbnQtZmFtaWx5PSJIZWx2ZXRpY2EsQXJpYWwsc2Fucy1zZXJpZiIgZm9udC1zaXplPSI0MCIgZm9udC13ZWlnaHQ9IjcwMCIgZmlsbD0iI2ZmZmZmZiIgdGV4dC1hbmNob3I9Im1pZGRsZSI+ajwvdGV4dD48L3N2Zz4=">
<link rel="stylesheet" href="../static/site.css">
</head>
<body>

<a class="skip" href="#main">本文へ</a>

<header class="top">
	<a class="brand" href="./">jimble</a>
	<div class="search">
		<input type="search" id="q" placeholder="検索" autocomplete="off" aria-label="検索">
		<ul id="results" hidden></ul>
	</div>
	@if(languages.size() > 1)
		<nav class="lang">
			@for(String item : languages)
				<a href="../${item}/"
					aria-current="${item.equals(language) ? "page" : "false"}">${languageNames.getOrDefault(item, item)}</a>
			@endfor
		</nav>
	@endif
</header>

<div class="layout">

	<nav class="side" aria-label="目次">
		@for(Map.Entry<String, List<Page>> section : nav.sections().entrySet())
			<p class="section">${section.getKey()}</p>
			<ul>
				@for(Page item : section.getValue())
					<li>
						<a href="${item.slug()}"
						   class="${item.slug().equals(page.slug()) ? "current" : ""}">${item.title()}</a>
					</li>
				@endfor
			</ul>
		@endfor
	</nav>

	<main id="main">
		<article>
			$unsafe{page.html()}
		</article>

		!{Page previous = nav.before(page);}
		!{Page following = nav.after(page);}

		@if(previous != null || following != null)
			<nav class="pager" aria-label="前後のページ">
				@if(previous != null)
					<a class="prev" href="${previous.slug()}">
						<span>前</span>${previous.title()}
					</a>
				@endif
				@if(following != null)
					<a class="next" href="${following.slug()}">
						<span>次</span>${following.title()}
					</a>
				@endif
			</nav>
		@endif
	</main>

	@if(page.headings().size() > 1)
		<nav class="toc" aria-label="このページの中身">
			<p class="section">このページ</p>
			<ul>
				@for(Page.Heading heading : page.headings())
					<li class="level-${heading.level()}"><a href="#${heading.id()}">${heading.text()}</a></li>
				@endfor
			</ul>
		</nav>
	@endif

</div>

<footer class="bottom">
	<p>jimble — Java 製の Web アプリケーションフレームワーク / Apache-2.0</p>
	<p class="note">このサイトは jimble のテンプレート（jte）で作った静的 HTML です。外部の CDN には繋いでいません。</p>
</footer>

<script src="../static/search.js"></script>
<script src="../static/copy.js"></script>

</body>
</html>
