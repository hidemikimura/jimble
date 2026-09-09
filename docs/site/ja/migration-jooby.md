---
title: jooby からの移行
summary: 何がそのまま動き、何を書き換えるか
section: そのほか
order: 3
---

# jooby からの移行

jimble のルーティング DSL は jooby に寄せてあります。
**多くのルート定義はそのまま動きます。** 書き換えが要るのは、その周りです。

## そのまま動くもの

```java
get("/posts", ctx -> ...);
post("/posts", ctx -> ...);
path("/admin", () -> { ... });
before(...);
after(...);
```

メソッド名は jooby と同じ小文字です。`path()` の入れ子も同じです。

**ただし `before` / `after` / `error` の効く範囲が違います。**
jooby はパスに付きますが、jimble は**書いたブロックの中だけ**に効きます。
同じ `/admin` でも、別のブロックで登録したルートには効きません
（[フィルタの効く範囲](./routing)）。
「`/admin` 配下は全部認証」に頼っている場合は、`/admin` のルートが
1か所にまとまっているか確かめてください。

## 書き換えるもの

### Context

| jooby | jimble |
| --- | --- |
| `ctx.path("id").value()` | `context.route().variables().get("id")` |
| `ctx.query("q").value()` | `context.request().bodyQuery().getString("q")` |
| `ctx.form("name").value()` | `context.request().bodyForm().getString("name")` |
| `ctx.body(Foo.class)` | `context.request().bodyJson()` → `Data` |
| `ctx.send("text")` | `context.response().send("text")` |
| `ctx.render(obj)` | `context.response().json(...)` / `.view(...)` |
| `ctx.setResponseCode(201)` | `context.response().code(201)` |
| `ctx.sessionOrNull()` | `context.session()` |

jimble は**入力を型付きのクラスに詰め直しません**。`Data` のまま扱います。
リフレクションでのバインドをやめたためです。値の検証は
[ValidationRules](./validation) でやります。

### DI をやめる

```java
// jooby
@Inject
public PostController (PostService service) { ... }
```

```java
// jimble
install(PostController::new);
```

依存は `new` するか、`static` のメソッドを呼びます。
「差し替えたい」場合は、コンストラクタで渡してください。

```java
install(() -> new PostController(new PostService(db)));
```

### 注釈をやめる

| jooby | jimble |
| --- | --- |
| `@GET @Path("/x")` | `get("/x", ...)` |
| `@Transactional` | `try (DBTransaction transaction = ...)` |
| `@Inject` | コンストラクタで渡す |

### 起動

```java
// jooby
public class App extends Jooby {
	{ get("/", ctx -> "hello"); }
	public static void main (String[] args) { runApp(args, App::new); }
}
```

```java
// jimble
public class App extends JimbleApp {
	{ get("/", context -> context.response().send("hello")); }
	public static void main (String[] args) { JimbleServer.start(new App()); }
}
```

**ハンドラは値を返しません。** `context.response()` に組み立てて送ります。
戻り値でレンダリングを分岐させると、何が返るかがコードから読めなくなるためです。

### 設定

`application.conf` の書式は同じ HOCON です。**キーの名前が変わります。**

| jooby | jimble |
| --- | --- |
| `server.port` | `server.port`（同じ） |
| `application.env` | `-Djimble.env=<env>` |
| `jooby.*` | 無くなりました |

`jooby.*` のキーは**読まれません。エラーも出ません**。
設定ファイルを持ってくるときは、必ず起動ログの構成行を確認してください。

### テンプレート

jooby の jte モジュールを使っていたなら、テンプレートはそのままです。
`io.jimble.jte` プラグインに置き換えてください。

渡すモデルは `Data` になります。`@param Data data` で受けます。

## 移行の順番

1. `build.gradle.kts` を差し替える（`io.jimble.jte` / `io.jimble.run`）
2. `App.java` を `JimbleApp` にする。ルート定義は貼るだけ
3. ハンドラの中を `context.request()` / `context.response()` に書き換える
4. `@Inject` を `install()` とコンストラクタに置き換える
5. `@Transactional` を `try (DBTransaction ...)` に置き換える
6. 起動して、**ルート一覧と構成行を確認する**

## 移行しなくてよいもの

DB 層はそのままです。`SQL` / `Column` / `Table` / `Data` / `DB` の使い方は変わりません。
生成されたテーブル定義のクラスも、そのまま使えます。
