---
title: リクエストとレスポンス
summary: 入力の読み方、入れ子パラメータ、検証、返し方
section: 基本
order: 2
---

# リクエストとレスポンス

## 入力を読む

`context.request()` から取れます。どこから来た値かで分かれています。

| メソッド | 中身 |
| --- | --- |
| `bodyPath()` | パスパラメータ（`/users/{id}`） |
| `bodyQuery()` | クエリ文字列 |
| `bodyForm()` | `application/x-www-form-urlencoded` / `multipart` |
| `bodyJson()` | JSON の本文 |
| `bodyFile()` | アップロードされたファイル（[ファイルアップロード](./upload)） |
| `bodyAll()` | 上を全部重ねたもの |

`bodyAll()` の重ね順は **path → query → form → json** です。
あとのものが前を上書きします。

戻り値はどれも `Data`（`LinkedHashMap<String,Object>` の派生）です。
`getString` `getInt` `getLong` `getBoolean` `getData` `getDataList` などで取り出します。
**無いキーは `getString` なら `null`、`getInt` なら `0`、`getBoolean` なら `false`** です
（「無い」と「0」は区別できません。区別したいときは `getIntObject` などの Object 版か `isNull(key)`。
詳しくは [ユーティリティ](./util)）。

> [!TRAP]
> **`context.request()` から直接は読めません。**
> `Request` も `Data` なので `context.request().getString("title")` は<b>コンパイルが通り</b>ますが、
> 本文もクエリも入っていないので **`null` が返ります。**
> 上の表のどれか（ふつうは `bodyAll()`）を通してください。
>
> ```java
> Data input = context.request().bodyAll();
> String title = input.getString("title");
> ```
>
> **`getStringOptional` は「無ければ空文字」ですが、その空文字を `Data` に入れます。**
> 読んだだけでキーが増えるので、JSON にして返す直前やループの中では使わないでください
> （[ユーティリティ](./util)）。

## ヘッダと Cookie（同じ名前が2つ来たとき）

`context.request().source()` から読めます。

| メソッド | 中身 |
| --- | --- |
| `headers()` | ヘッダ。キーは小文字。**同じ名前が2行で来たら `", "` で繋ぎます**（`Cookie` だけ `"; "`） |
| `cookies()` | Cookie。**同じ名前が2つ来たら先頭だけ**です |
| `headerValues()` | ヘッダ。**繋ぐ前**（`Map<String, List<String>>`） |
| `cookieValues()` | Cookie。**捨てる前**（`Map<String, List<String>>`） |

ふつうは `headers()` / `cookies()` で足ります。
**2行で来たことそのものを知りたいとき**だけ `...Values()` です。

> [!TRAP]
> **同じ名前の Cookie は2つ来ることがあります。**
> 別のパスやドメインに同じ名前で置かれた場合です。
> **どちらが先頭になるかは Cookie の仕様では決まっていません。**
>
> セッション ID でこれが起きると、**ログインが不定期に外れます**——
> 例外は出ませんし、次のリクエストでは直っていることがあります。
> jimble は**2本以上来たら警告を出します**（値は出しません）。
> ログに出たら、`Path` / `Domain` を揃えて置き直してください。

## 入れ子のパラメータ

`.` と `[ ]` のどちらでも入れ子にできます。

```
user.name=taro
user[name]=taro          同じ
items[0].price=100
items[].price=100        末尾に足す
```

`[ ]` の中が数字なら添字、空なら追加、それ以外なら名前です。
JSON の本文と混ざったときは、フォームの値が JSON を上書きします。

## 検証する

```java snippet=validation
```

**エラーは最初の1件で止めません。** 全部集めてから返します。
入力し直す人にとって、1件ずつ言われるのがいちばん困るからです。

`ValidationMessages.toMessages(errors)` で、項目名 → メッセージの `Data` になります。
そのまま JSON で返すか、テンプレートに渡します。

ルールの一覧、ルート単位でかける `ValidationExecutor`、ページングは
[検証とページング](./validation) にまとめてあります。

## 返す

```java
context.response().send("text");                   // text/plain
context.response().json("posts", list);            // JSON
context.response().view("blog/posts.jte");         // テンプレート
context.response().redirect("/");                  // 302
context.response().download(file, "報告書.xlsx");  // ダウンロード
context.response().code(201).send();               // 本文なし
```

`json()` は組み立てるだけです。複数回呼ぶと、1つの JSON に足されていきます。

**`json()` のあとに `send()` を書く必要はありません。**
組み立てておけば、ディスパッチャが実行の終わりに送ります。
`send()` を明示するのは、**本文なしで終わらせたいとき**（`code(204).send()`）や、
テキストをそのまま返すときです。

```java snippet=json-route
```

**204 / 205 / 304 は本文を持てません**（HTTP の決まりです）。
`json(...)` などを積んだまま `code(204).send()` すると、**本文を捨てて、ステータスとヘッダだけを返します**
（Set-Cookie は届くので、ログアウトを 204 で返す形も使えます）。
捨てたときは WARN が出ます——積んだ本文が誰にも届かないのは、たいてい書き間違いだからです。
本文の中身はログに出しません。

**`redirect()` の飛び先が、いま処理しているリクエストと同じ URL なら `RedirectLoopException` が投げられます**（500）。
そのまま返すとブラウザが同じ URL を取りに来て、同じ処理が同じ飛び先を返す——止まりません。
例外は他と同じく `error(...)` に渡るので、画面へ飛ばすか JSON を返すかはそちらで決めます。
見るのは GET / HEAD のときだけで、`POST /login` から `/login` へ戻す（PRG）は止めません。
見つけられるのは自分自身へ戻る1段だけで、`/a → /b → /a` のように別のリクエストをまたぐものは分かりません。

## 大きいものを流す

全部をメモリに載せたくないときは `outputStream()` を直接使います。

```java
context.response().setResponseHeader("Content-Type", "text/csv; charset=UTF-8");

try (OutputStream out = context.response().outputStream()) {
	// 1行ずつ書く
}
```

**`outputStream()` を呼んだ時点でステータスとヘッダが決まります。**
`code(...)`・`cookies()` に積んだ Cookie・既定の `Cache-Control: no-store`（自分で決めていれば上書きしません）は、
その前に済ませてください。あとから変えても届きません。
204 / 205 / 304 のときは、書いたものは捨てられて WARN が出ます。

**書いたものは `flush()` するまで溜まります。**CSV のように最後まで書き切るものはそのままで構いませんが、
届いたそばから相手に見せたいとき（途中経過・ほかのサーバーの応答の中継）は、書くたびに `flush()` してください。

`InputStream` を渡すときは `send(in, "型")`（または `stream(...)`）です。
**続きがまだ届いていなければ（`available()` が 0 なら）、そこまでを送り出します。**
ファイルのように手元にそろっているものはまとめて書き、別のサーバーのストリーミング応答のように少しずつ届くものは、届いたそばから流れます。

```java
HttpResponse<InputStream> upstream = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
context.response().code(upstream.statusCode()).send(upstream.body(), "text/event-stream");
```

進捗を送りたいだけなら [SSE](./sse) のほうが簡単です。

## 二重送信

`send()` を2回呼ぶとエラーになります。
`isSent()` で送信済みかどうかを見られます。
`after` フィルタや `error` ハンドラの中では、これを見てから触ってください。
