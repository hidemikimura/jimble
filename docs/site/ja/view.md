---
title: テンプレート
summary: jte。コンパイルされるので型が効く
section: 基本
order: 6
---

# テンプレート

jimble のテンプレートは [jte](https://jte.gg/) です。
文字列を実行時に解釈するのではなく、**Java にコンパイルします**。

```java snippet=view-route
```

`putData()` で入れたものが `Data` として渡り、`view()` でテンプレート名を指定します。

## テンプレート側

`src/main/jte/blog/posts.jte`:

```jte
@import db.blog_example.table.post.Post
@import io.jimble.util.data.Data
@import java.util.List

@param Data data

!{List<Data> posts = data.getDataList("posts");}

<h1>${data.getString("title")}</h1>

@for(Data post : posts)
	<b>${post.getString(Post.title)}</b>
	<p>${post.getString(Post.body)}</p>
@endfor
```

- `${ }` は **HTML エスケープされます**。生で出すには `$unsafe{ }` を使います
- `@param` と `@import` があるので、テンプレートの中でも型が効きます
- `Post.title` のような `Column` で引けるので、テンプレートに文字列のキーを撒かずに済みます

## 枠を共有する

ページごとに `<html>` から書くと、**ヘッダを直すのに全部を直すことになります。**
枠は1枚のレイアウトに閉じ込めて、ページは中身だけを書きます。

`src/main/jte/layout/page.jte`:

```jte
@import gg.jte.Content
@import io.jimble.util.data.Data

@param Data data
@param Content content

<!doctype html>
<html lang="ja">
<head>
	<title>${data.getString("title")}</title>
	<link rel="stylesheet" href="/assets/app.css">
</head>
<body>
	@template.tag.nav(current = data.getString("nav"))

	<main>${content}</main>
</body>
</html>
```

`src/main/jte/pages/home.jte`:

```jte
@import io.jimble.util.data.Data

@param Data data

@template.layout.page(data = data, content = @`
	<p>ここが中身です。</p>
`)
```

`Content` が「あとで描くもの」です。`@` とバッククォートで囲んだかたまりを渡すと、
レイアウトの `${content}` の位置に入ります。

**呼び出し名はディレクトリ構成そのままです。**
`src/main/jte/layout/page.jte` なら `@template.layout.page(...)`、
`src/main/jte/tag/nav.jte` なら `@template.tag.nav(...)`。

## 部品

同じ仕組みで、使い回す部品も作れます。

`src/main/jte/tag/card.jte`:

```jte
@import gg.jte.Content

@param String title
@param Content body
@param String note = ""

<section class="card">
	<h2>${title}</h2>
	${body}
	@if(!note.isEmpty())
		<p class="note">${note}</p>
	@endif
</section>
```

- **`@param` には既定値を書けます**（`= ""`）。書かなければ呼ぶ側は省略できます
- **部品は `Data` ではなく、必要なものだけを受け取るほうが使い回せます。**
  `Data` を渡すと、呼ぶ側が「どのキーが要るのか」をテンプレートの中身を読まないと分かりません

実際に動いているものは `examples/approval-pages` にあります。

> [!TIP]
> **エラー画面もレイアウトを共有してください。**
> 共有していないと、404 のときだけ見た目の違うサイトになります。

## コメント

**jte のコメントは `<%--` と `--%>` です。**

```jte
<%-- ここはコメント。出力に出ません --%>
```

> [!WARNING]
> **`@*` と `*@` で囲む書き方はコメントになりません。**
> そのまま出力され、しかも**中に書いた `@template.` は実行されます**。
> レイアウトの中に、そのレイアウト自身の呼び出し例をコメントのつもりで書くと、
> **無限再帰して `StackOverflowError` になります**（実際に踏みました）。
>
> **コメントは入れ子にできません。**閉じの記号を説明文の中に書くと、そこで終わります。
>
> **HTML のコメント（`<!-- -->`）も既定では出力から消えます。**
> ブラウザや外の道具に残したい印は、テンプレートではなく静的ファイル側に置いてください。

## ビルド

`io.jimble.jte` プラグインを入れると、`compileJava` の前に
`src/main/jte` を Java に変換します。

```kotlin
plugins {
	application
	id("io.jimble.jte")
}
```

**テンプレートの間違いはビルドで落ちます。** 実行時に404を見てから気づくことはありません。

設定できることは [Gradle プラグイン](./gradle) にあります。

コンパイラ（`gg.jte:jte`）はビルドのときだけ使い、
アプリの実行時クラスパスには `gg.jte:jte-runtime` しか入りません。

## ホットリロード

`./gradlew jimbleRun` で起動していれば、`.jte` を直した時点で
変換とビルドが走り、次のリクエストで反映されます。
