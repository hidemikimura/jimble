@import io.jimble.util.data.Data

@param Data data

<%--
	トップ（要件 F-W-08 / F-W-10）

	<b>&lt;html&gt; を書いていない。</b>枠はレイアウトが持っている。
	ここにあるのは、このページにしかない部分だけである。
--%>

@template.layout.page(data = data, content = @`

	@template.tag.card(title = "このサンプルで見られるもの", body = @`
		<ul>
			<li><a href="/requests">申請一覧</a> — 同じレイアウトを使うもう1枚</li>
			<li><a href="/assets/app.css">/assets/app.css</a> — 静的配信（Etag つき）</li>
			<li><a href="/app/">申請アプリ</a> — SPA（どのパスでも index.html）</li>
			<li><a href="/guide/">手引き</a> — MPA（静的サイトをそのまま配る）</li>
			<li><a href="/api/echo">/api/echo</a> — 別ポートへ転送（リバースプロキシ）</li>
		</ul>
	`)

	@template.tag.card(title = "レイアウトと部品", note = "この箱そのものが部品です（tag/card.jte）", body = @`
		<p>
			枠は <code>layout/page.jte</code> の1枚だけにあります。
			ページ側は中身だけを書きます。
		</p>
	`)

`)
