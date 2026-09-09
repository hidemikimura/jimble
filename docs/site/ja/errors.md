---
title: エラー処理
summary: 例外・未マッチ・検証失敗。どこで拾われ、何が返るか
section: 基本
order: 5
---

# エラー処理

エラーが表に出る道は3本あります。**通る場所が違います。**

| 何が起きたか | 誰が拾うか | 既定の返り |
| --- | --- | --- |
| 例外が投げられた | `error(...)` フック | ステータスは例外しだい。本文はアプリが作る |
| ルートに当たらなかった | **いちばん外側の** `error(...)` | 404 |
| 検証に失敗した | `ValidationExecutor`（`error` は通らない） | 422 と `validation` の JSON |

## error の書き方

```java
error((context, cause, statusCode) -> {
	context.response().code(statusCode).send("エラー: " + statusCode);
});
```

引数は3つです。**例外だけでなくステータスコードも渡ってきます。**
`cause` から毎回コードを引き直さなくて済むようにするためです。

```java
void handle (WebContext context, Throwable cause, int statusCode) throws Exception;
```

## 効く範囲

`before` / `after` と同じで、**書いたブロックに付きます**（パスのノードではありません）。
実行は<b>内側から外側へ</b>。詳しくは [ルーティング](./routing) を見てください。

```java snippet=error-fallthrough
```

`/admin/x` に来ると、内側 → 外側の順に呼ばれます。
**内側が例外を投げても外側に進みます**（下の「ハンドラ自身が失敗したら」）。

> [!NOTE]
> 未マッチ（404）のときだけ扱いが違います。**いちばん外側のスコープに直接書いた `error` だけ**が呼ばれます。
> どのルートにも当たっていないので、「内側」が決まらないためです。
> `path("/admin", ...)` の中に書いた `error` は、`/admin/nope` でも呼ばれません。

## ステータスコードの決まり方

`JimbleApp#resolveStatusCode(Throwable)` が決めます。既定はこれだけです。

| 例外 | コード |
| --- | --- |
| `HttpException` | `statusCode()` の値 |
| `NotFoundException`（`HttpException` の子） | 404 |
| そのほか全部 | **500** |

```java snippet=error-http-status
```

自分の例外にコードを割り当てるなら上書きします。

```java snippet=error-status-resolve
```

> [!WARN]
> `CodeException`（`io.jimble.util.exception.CodeException`）は **HTTP のステータスには効きません。**
> DB のエラー（`db.getError()`）とバリデータの中で使う検査例外で、投げれば 500 です。

## 本文は誰が作るか

順番はこうです。

1. `context.response().code(statusCode)` を**先に**入れる（ハンドラが上書きできる）
2. `error(...)` を内側から順に呼ぶ。**送った時点で止まる**
3. 誰も本文を用意していなければ、**既定のエラー応答**を入れる
4. `response().send()` を呼ぶ

4 が `send(statusCode)` ではなく `send()` なのが要点です。
ハンドラが `json(...)` などで組み立てた中身を、**捨てずに送る**ためです。

```java
error((context, cause, statusCode) -> {

	// API なら JSON、画面なら HTML
	if (context.request().acceptJson()) {
		context.response().json("error", cause.getMessage());
		return;
	}

	context.response().send("エラー: %d %s".formatted(statusCode, cause.getMessage()));

});
```

> [!WARN]
> この例のように `cause.getMessage()` を本文に出すのは、**中身次第では外に見せてはいけないもの**です。
> 500 のときは DB のエラー文や内部のパスがそのまま入ります。
> `Throwable` をそのまま `json(...)` に渡すと、**スタックトレースが丸ごと出ます**。
> 既定のエラー応答（次の節）はどちらも載せませんが、**自分で書いたぶんは自分で抑えてください**。

## 何も書かなかったとき

`error(...)` を1つも書いていない、あるいは書いたけれど本文を作らなかったとき、
jimble が**決まった形**を返します。

`Accept` が JSON を名指ししていれば JSON です。

```json
{"error": {"status": 404, "message": "Not Found"}}
```

そうでなければ、短いテキストです（`Content-Type: text/plain; charset=UTF-8`）。

```
404 Not Found
```

- **`message` は RFC 9110 の短い語**です（`Not Found` / `Internal Server Error`）。
  ステータス行やクライアントのライブラリに出てくる語と同じにしてあります
- **原因は入りません。** 例外のメッセージも SQL もスタックトレースも載せません。
  原因はログに出ています（500 番台は `Log.error`）
- **`*/*` はテキスト**です。「何でもいい」であって「JSON がいい」ではありません
- **`error(...)` で組み立てていれば、そちらが勝ちます。** 送信まで済んでいなくてもです

`acceptJson()` は `Accept` が `application/json` か `text/javascript` を
**名指ししているか**を見ます。`application/json;q=0.9` のようにパラメータが付いていても効きます。
`q=0`（要らない）と `*/*`（何でもいい）は名指しに数えません。

## 405 と 404 は分かれます

パスは合っていて**メソッドだけ違う**ときは 405 で、`Allow` が付きます。

```
$ curl -i -X POST http://localhost:9000/hello
HTTP/1.1 405 Method Not Allowed
Allow: GET
```

404 は「そんなものは無い」、405 は「あるが、その呼び方ではない」です。
一緒にすると、`post` と書くべきところを `get` と書いただけの間違いが
「パスが違う」に見えてしまいます。

## ハンドラ自身が失敗したら

**握って次（外側）へ進みます。** エラー処理の失敗で何も返せなくなるほうが困るからです。
失敗は `エラーハンドラで例外が発生しました` としてログに出ます。

## ログの出しかた

| ステータス | ログ |
| --- | --- |
| 500 番台 | `Log.error`（スタックトレースつき） |
| それ以外（404 を含む） | `Log.debug` |

> [!TIP]
> 404 をエラーログに出さないのは意図的です。
> クローラや古いリンクで毎日何千件も出るものを `error` に混ぜると、
> **本物の 500 が埋もれます。**

`after` と `onComplete` の中で例外が出た場合も、握ってログに出します（レスポンスは返ります）。

## 検証の失敗は error を通らない

`ValidationExecutor` は**例外を投げません。**
自分をキャンセルして、後続の Executor を捨て、そのまま返します。

```java
context.response().putForm(context.request().bodyAll());
context.response().json("validation", errors);
context.response().code(422);
```

返る形はこうです。入力値も一緒に返るので、画面を組み直せます。

```json
{
  "validation": { "title": ["入力してください"] },
  "title": "",
  "body": "..."
}
```

> [!TRAP]
> **`error(...)` に検証エラーの整形を書いても呼ばれません。**
> 422 の見た目を変えたいときは `ValidationExecutor` 側（`onCancel`）を見てください。

## アプリ全体で差し替えられるもの

`JimbleApp` で上書きできるのは3つだけです。

| メソッド | いつ呼ばれるか |
| --- | --- |
| `protected void onRequest (WebContext context) throws Exception` | 全リクエストの最初。**未マッチでも呼ばれる。**ここで送れば以降は走らない |
| `protected void onComplete (WebContext context)` | 全リクエストの最後。例外が出ても必ず呼ばれる |
| `protected int resolveStatusCode (Throwable cause)` | 例外 → ステータスコード |

ディスパッチャそのものは差し替えられません（`final`）。
道を1本にしておくためです。
