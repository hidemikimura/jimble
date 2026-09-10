@import gg.jte.Content
@import io.jimble.util.data.Data

@param Data data
@param Content content

<%--
	共通の枠（要件 F-W-08 / F-W-10）

	中身を Content で受け取る。呼ぶ側は、名前つき引数の content に
	「@」＋バッククォートで囲んだかたまりを渡す。それが下の content の位置に入る。
	home.jte / requests.jte / error.jte がその書き方である。

	共通の枠をここ1枚に閉じ込めるのが目的なので、
	html から /html までをページ側に書かせない。
--%>

<%--
	コメントの書き方に注意（このファイルを書いていて踏んだ）。

	jte のコメントは、いま囲んでいるこの形だけである。
	「@」と「*」で囲む書き方はコメントにならず、そのまま出力される。
	しかも中に書いた @template. は実行されるので、
	レイアウトの説明としてレイアウト自身の呼び出し例を書くと無限再帰する。

	コメントは入れ子にできない。閉じの記号を説明文の中に書くと、
	そこでコメントが終わってしまう（これも踏んだ）。

	HTML のコメントも既定では出力から消える。
	ブラウザに残したい印は、jte ではなく静的ファイル側に置くこと
	（このサンプルの SPA の title の目印がそれである）。
--%>

<!doctype html>
<html lang="ja">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>${data.getString("title")} | 経費・購買の申請</title>

	<%--
		静的配信（要件 F-W-18）。
		この1本が /assets/app.css から src/main/resources/assets/app.css に当たる。
		拡張子が .css なので immutable 扱いになり、max-age が長く返る
	--%>
	<link rel="stylesheet" href="/assets/app.css">
</head>
<body>

@template.tag.nav(current = data.getString("nav"))

<main>
	<h1>${data.getString("title")}</h1>

	${content}
</main>

<footer>
	<p>jimble のサンプル（N-3 / approval-pages）</p>
</footer>

</body>
</html>
