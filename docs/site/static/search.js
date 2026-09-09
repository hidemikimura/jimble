/*
 * 検索（要件 NF-D-01）
 *
 * 検索エンジンを足していない。ページ数が30程度なら、
 * 索引を丸ごと配って絞り込むだけで足りる。
 * 外部の CDN にも繋がない。
 */
(function () {

	var input = document.getElementById('q');
	var results = document.getElementById('results');

	if (!input || !results) {
		return;
	}

	var pages = null;

	function load () {

		if (pages !== null) {
			return Promise.resolve(pages);
		}

		return fetch('search-index.json')
			.then(function (response) { return response.json(); })
			.then(function (index) { pages = index.pages || []; return pages; })
			.catch(function () { pages = []; return pages; });

	}

	function escape (text) {
		return text.replace(/[&<>]/g, function (c) {
			return { '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c];
		});
	}

	/* 前後を少し付けて、どこに当たったかを見せる */
	function excerpt (text, needle) {

		var at = text.toLowerCase().indexOf(needle);

		if (at < 0) {
			return '';
		}

		var from = Math.max(0, at - 30);
		var to = Math.min(text.length, at + needle.length + 50);

		return (from > 0 ? '…' : '') + text.slice(from, to) + (to < text.length ? '…' : '');

	}

	/* 題 > 概要 > 本文 の順に強い */
	function score (page, needle) {

		if (page.title.toLowerCase() === needle) { return 4; }
		if (page.title.toLowerCase().indexOf(needle) >= 0) { return 3; }
		if (page.summary.toLowerCase().indexOf(needle) >= 0) { return 2; }

		return 1;

	}

	function render (matches, needle) {

		results.innerHTML = '';

		if (matches.length === 0) {
			results.hidden = true;
			return;
		}

		matches.slice(0, 8).forEach(function (page) {

			var li = document.createElement('li');
			var a = document.createElement('a');

			/*
			 * 拡張子は付けない。
			 * Cloudflare Pages は /ja/x.html を /ja/x へ 307 で正規化するので、
			 * 付けるとクリックのたびに1回よけいに往復する。
			 */
			a.href = page.slug;
			a.innerHTML = '<strong>' + escape(page.title) + '</strong>'
				+ '<span>' + escape(excerpt(page.text, needle) || page.summary) + '</span>';

			li.appendChild(a);
			results.appendChild(li);

		});

		results.hidden = false;

	}

	input.addEventListener('input', function () {

		var needle = input.value.trim().toLowerCase();

		if (needle.length < 2) {
			results.hidden = true;
			return;
		}

		load().then(function (list) {

			/*
			 * 題に当たったものを上に出す。
			 * 「トランザクション」と打った人が探しているのは、
			 * その言葉が出てくるページではなく、その題のページである。
			 */
			var hits = list.filter(function (page) {
				return page.title.toLowerCase().indexOf(needle) >= 0
					|| page.summary.toLowerCase().indexOf(needle) >= 0
					|| page.text.toLowerCase().indexOf(needle) >= 0;
			});

			hits.sort(function (a, b) {
				return score(b, needle) - score(a, needle);
			});

			render(hits, needle);

		});

	});

	document.addEventListener('click', function (event) {
		if (!results.contains(event.target) && event.target !== input) {
			results.hidden = true;
		}
	});

	document.addEventListener('keydown', function (event) {
		if (event.key === 'Escape') {
			results.hidden = true;
			input.blur();
		}
		/* / で検索へ */
		if (event.key === '/' && document.activeElement !== input) {
			event.preventDefault();
			input.focus();
		}
	});

})();
