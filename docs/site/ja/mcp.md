---
title: MCP
summary: Model Context Protocol のサーバーを立てる（2026-07-28 / Streamable HTTP と stdio）
section: プロトコル
order: 3
---

# MCP

アプリの機能を、AI から呼べる形で公開します。
jimble が実装しているのは **2026-07-28** 版で、
トランスポートは **Streamable HTTP** と **stdio** の2つです。

## 公開するものを並べる

```java
public class BlogMcp extends McpController {

	{
```

```java snippet=mcp-controller
```

```java
	}

}
```

```java
install(BlogMcp::new);
```

**上から読めば、このサーバーが何を公開しているかが全部分かります。**
注釈もクラスパスの走査もありません。Java の MCP 実装はたいてい注釈で宣言して
起動時に走査しますが、それだと「どのクラスが拾われているか」が実行するまで分かりません。

## ツールを書く

```java snippet=mcp-tool
```

`description()` は**モデルが読みます**。人間向けのコメントではありません。
いつ使うか、そして**何ができないか**を書いてください。
「過去や予報は返せない」の一文が、無駄な呼び出しを減らします。

入力の形は JSON Schema で宣言します。

```java snippet=mcp-input-schema
```

## 失敗の返し方

失敗には2種類あります。

| どちらか | 返し方 |
| --- | --- |
| モデルが読んで直せる（引数が変、対象が無い） | `ToolResult.error("...")` |
| 直せない（DB が落ちている、設定が無い） | 例外を投げる |

`ToolResult.error()` は `isError` を立てて**正常な応答として**返します。
モデルはそれを読んで、引数を変えてもう一度呼べます。
例外にしてしまうと、モデルには「壊れた」としか見えません。

## すでにある API をそのまま出す

API を先に作って、あとから MCP でも提供することがあります。
そのときツールの中に同じ処理をもう一度書くと、**必ずどちらかが古くなります**。

`RouteTool` は、**登録済みのルートをそのままツールにします**。

```java
public class BlogMcp extends McpController {

	{
		tool("list_posts", RouteTool.of("GET", "/api/posts")
			.description("記事の一覧を返す。page で頁を指定する")
			.input(JsonSchema.object()
				.integer("page", "ページ番号（1から）").min(1)));

		tool("get_post", RouteTool.of("GET", "/api/posts/{id}")
			.description("記事を1件返す")
			.input(JsonSchema.object()
				.string("id", "記事ID").required()));

		tool("create_post", RouteTool.of("POST", "/api/posts")
			.description("記事を1件登録する")
			.input(JsonSchema.object()
				.string("title", "題名").required()
				.string("body", "本文").required()));
	}

}
```

HTTP は通りません。ディスパッチャが**そのルートを直接呼びます**。

> [!note]
> **`before` も `after` も効きます。**
> 認証を `before` に置いている API は、MCP から呼んでも認証を通ります
> （MCP リクエストのヘッダと Cookie をそのまま引き継ぎます）。
> 内部専用の別経路を作らないので、**認証を足したときに片方だけ忘れる**ことがありません。

### 引数がどこへ行くか

| 引数 | 行き先 |
| --- | --- |
| パスの `{name}` と同じ名前 | パスに埋まる |
| 残り（`GET` / `DELETE` / `HEAD`） | クエリ文字列 |
| 残り（それ以外） | JSON の本文 |

パスの `{name}` は `input(...)` に書いて `required()` を付けてください。
書かないと、モデルは何を渡せばいいか分かりません。

ワイルドカード（`/files/*`）を含むルートは**登録した時点で断ります**。
埋める手立てが無いまま通すと、エラーも出ずに毎回 404 になるためです。

### 結果がどうなるか

| API が返したもの | ツールの結果 |
| --- | --- |
| 2xx で JSON のオブジェクト | `structuredContent` と本文テキストの両方 |
| 2xx でそれ以外 | 本文テキスト |
| 4xx | `isError`（**本文をそのまま渡す**） |
| 5xx | `isError`（**本文は渡さない**） |

4xx の本文は API がクライアントに読ませるために書いたものなので、
そのままモデルに渡せば直せます（「その記事はありません: 999」）。

5xx の本文は誰にも読ませるつもりで書かれていません。
エラーハンドラ次第でスタックトレースが入るので、**渡しません**（ログには残ります）。

### 気をつけること

- アップロードを受ける API は呼べません（一時ファイルの持ち主が曖昧になるため、ファイルは空です）
- 大きなファイルを返す API は、そのぶんメモリに載ります
- 圧縮したまま返す API（`response().cache(...)`）は、読めないので `isError` になります

> [!trap]
> **ドメイン層を共有できるなら、そちらが先です。**
> `RouteTool` は「検証・整形・権限まで含めて **API として組み上がったもの**を
> そのまま出したい」ときの道具です。
> 内側は**外側とは別のトランザクション**になるので、
> 1つのツールで複数の API を呼んでまとめてコミットしたいときは使えません。

### ツールを通さずに呼ぶ

同じ仕組みは、ふつうのハンドラからも使えます。

```java
CallResponse response = context.dispatcher().call(context
	, CallRequest.of("GET", "/api/posts").query("page", "2"));

Data json = response.json();
```

入れ子には上限（8）があります。
自分を呼ぶルートを作ると無限に潜るので、そこで止めて例外にします。

## リソースとプロンプト

```java
resource("blog://latest", LatestPostsResource::new);
prompt("summarize", SummarizePrompt::new);
```

リソースは読み取り専用のデータ、プロンプトは定型の指示です。

## 標準入出力で動かす

HTTP を立てずに、**クライアントにプロセスを起こしてもらう**形でも動きます。
手元で使う道具や、ポートを開けたくないところで使います。

```java
public class BlogStdio {

	public static void main (String[] args) throws Exception {

		Bootstrap.load();

		App app = new App();

		McpStdio.run(app, app.mcp().registry());

	}

}
```

```json
{
	"mcpServers": {
		"blog": {
			"command": "java",
			"args": ["-cp", "app.jar", "blog.BlogStdio"]
		}
	}
}
```

**ポートは開きませんが、ルート表は組みます。**
`RouteTool`（すでにある API をそのまま出すもの）も `before` の認証も、
HTTP のときとまったく同じに動きます。

アプリを渡さない `McpStdio.run(registry)` もあります。
こちらは `RouteTool` が使えません（ルート表が無いので、呼ばれたらその旨を返します）。

### 標準出力に何も書かないでください

仕様は「**サーバーは標準出力に MCP のメッセージ以外を書いてはならない**」と決めています。
ログが1行混ざるだけで、クライアントは「壊れた JSON が来た」として接続を切ります。

`McpStdio.run` は、これを**気をつけて避けるのではなく、書けなくします**。
本物の標準出力は `McpStdio` だけが持ち、`System.out` は標準エラーに差し替えます。
アプリが `System.out.println` を書いても、logback がそこへ出しても、
全部が標準エラーへ流れます（クライアントは標準エラーを無視してよいと決まっています）。

**終わり方は標準入力が閉じたときです。** 開いている購読をきれいに閉じてから、
`Shutdown` に預けたものを止めます。

## 変わったことを知らせる

クライアントは `subscriptions/listen` で購読を開きます。
これは**終わらない要求**で、HTTP なら SSE、stdio なら同じ標準出力に流れます。

アプリ側は、変わったときに1行呼ぶだけです。

```java
postDao.save(post);

McpNotify.resourceUpdated("blog://posts/" + post.getId());
```

- **頼まれたものにしか届きません。** 購読していない URI は飛ばします
- 誰も購読していなければ何も起きません
- 開いた直後に `notifications/subscriptions/acknowledged` が返ります。
  ここに**受け付けた種類だけ**が入るので、クライアントは頼んだものと突き合わせられます

**流せるのはリソースの2つだけです**（`resources/updated` と `resources/list_changed`）。
ツールとプロンプトは起動時に明示登録するので、動いているあいだに増えも減りもしません。
「対応している」と答えて一生届かないほうが困るので、
`toolsListChanged` を頼まれても `acknowledged` には入れません。

## 一覧が多いとき

`tools/list` などは、多いとページに分かれます。

```json
{"jsonrpc":"2.0","id":1,"method":"resources/list","params":{"cursor":"..."}}
```

- 続きがあるときだけ `nextCursor` が付きます
- **既定は 100 件です。** それ以下しか登録していないアプリの応答は今までと変わりません
- カーソルは**中身を読まないでください**（不透明な文字列です）
- **読めないカーソルは断ります**（`-32602`）。黙って先頭に戻すと、
  クライアントは同じページを永遠に読み続けることになります

```conf
mcp {
	page_size = 100
}
```

## 口は1本

HTTP で公開されるのは `POST /mcp` の1本だけです。

- **GET も DELETE も 405 で断ります。** どちらも 2026-07-28 で仕様から消えました
- **セッションはありません。** `Mcp-Session-Id` は使いません
- **サーバーから要求は出しません。** 送るのは応答だけです

パスを変えるなら設定します。

```conf
mcp {
	path            = "/mcp"
	name            = "blog"
	version         = "1.0.0"
	allowed_origins = ["https://example.com"]
	page_size       = 100
	instructions    = "記事の検索と投稿ができます"
}
```

## Origin を必ず設定してください

`allowed_origins` を設定しないと、`Origin` ヘッダの検査ができません。
ブラウザから叩ける MCP サーバーは、**DNS リバインディングの的になります**。
ローカルで動かすサーバーほど危険です（`localhost` は誰の手元にもあります）。

## つなぐ

```json
{
	"mcpServers": {
		"blog": {
			"type": "http",
			"url": "http://localhost:9000/mcp"
		}
	}
}
```

クライアントは最初に `server/discover` を投げて、
話せる版と、そのサーバーが何を持っているかを見ます。
**この呼び出しにだけは版のヘッダが要りません**（版を知るための呼び出しなので）。
