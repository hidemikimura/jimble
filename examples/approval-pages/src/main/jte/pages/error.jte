@import io.jimble.util.data.Data

@param Data data

<%--
	見つからなかったとき

	<b>エラー画面もレイアウトを共有する。</b>
	共有していないと、404 だけ見た目が違うサイトになる。
--%>

@template.layout.page(data = data, content = @`

	@template.tag.card(title = data.getString("message"), body = @`
		<p><a href="/">ホームへ戻る</a></p>
	`)

`)
