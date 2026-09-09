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
