---
name: jimble-web-response
description: jimble の Web で応答を返す・エラーを返す・静的ファイルを出すときに使う。text / json / view / redirect を組み立てて送るのは実行の終わり（send を書かない）、返し方を2種類積まない、outputStream と flush・tcp_no_delay、セキュリティヘッダ、error(...) の書き方（500 番台の中身を返さない・ValidationException の素通し）、例外とステータスコード、AssetHandler のキャッシュ、SpaRewriter を含む。
---

# jimble の Web で返す

全体の決まりと、ほかの skill への案内は `jimble-web`。

## 返す

```java
context.response().text("text");                   // 組み立てる。送信は実行の終わり
context.response().json("posts", list);            // 同じ
context.response().view("blog/posts.jte");
context.response().redirect("/");                  // 302
context.response().code(201).send();
```

`json()` は重ねて呼ぶと1つの JSON に足されていく。
**`view()` のページは、`Accept: application/json` で来てもテンプレートを描く**（2.2.3。それまではテンプレートに渡したデータを丸ごと JSON で返していた）。
JSON も返す口にしたいなら `json(...)` で別に返す（元に戻すなら `template.json_fallback = true`）。
**既定で `X-Content-Type-Options: nosniff`・`X-Frame-Options: SAMEORIGIN`・`Referrer-Policy` を付ける**（`security_headers`。自分で決めたヘッダは上書きしない）。
別のサイトの iframe に入れるページは、そのルートで `setResponseHeader("X-Frame-Options", ...)` を決めるか `security_headers.frame_options = ""`。

- **返し方を2種類積む（`json(...)` と `redirect(...)` など）と `IllegalStateException`。**どちらかに決めてから書く。同じ種類を重ねるのはよい
- **そのあとに `send()` は書かない**——組み立てておけば、ディスパッチャが実行の終わりに送る（`error` ハンドラの中でも同じ）。
  サンプルはどれも `json()` で終わっている。明示するのは**本文なしで終わらせたいとき**だけ（`code(204).send()`）
- **`send("...")` のように引数を渡すと、その場で送る**（組み立てない）。文字列を返すなら `text("...")`
- **`send()` を2回呼ぶとエラー。**`after` や `error` の中では `isSent()` を見てから触る

大きいものは `context.response().outputStream()` に直接書く。
**呼んだ時点でステータス・Cookie・Cache-Control が決まる**ので、`code(...)` やヘッダはその前に（送ったあとのヘッダ・Cookie は例外）。
書いたものは `flush()` するまで溜まる——届いたそばから見せたいなら書くたびに `flush()`。
分けて書く応答（chunked）は、**`server.tcp_no_delay`（既定 true。2.7.3）を `false` にすると、同じ接続の 2 回目から 1 回ごとに約 40ms 遅れる**
（Nagle と遅延 ACK。nginx の後ろでも起きる）。Nagle に頼りたい特別な場合を除き、`false` にしない。
`InputStream` は `send(in, "型")` に渡せば、続きが来ていないところで送り出す（中継に使える）。

## エラー

```java
throw new HttpException(401, "ログインしてください");
```

| 例外 | コード |
| --- | --- |
| `HttpException` | `statusCode()` の値 |
| `ValidationException` | 422（`validate(...)` が投げる） |
| `NotFoundException` | 404 |
| `SqlExecuteException` などそのほか | **500** |

`error(...)` は**画面へ飛ばすか JSON かをアプリが決める**ところ。
枠組み側（`Auth.guard` など）は投げるだけ。**未マッチ（404）で呼ばれるのは、いちばん外側の `error` だけ。**

```java
error((context, cause, statusCode) -> {
	if (cause instanceof ValidationException) {
		return;                            // 422 の本文 {"validation": {...}} は枠組みが付ける
	}
	// 500 番台は中身を返さない（DB の誤りや内部のパスが届く）。中身はログで見る
	context.response().code(statusCode).json("error"
		, statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました");
});
```

- **`error(...)` で 500 番台の `cause.getMessage()` を返さない。**DB の誤り（重複したキーの値）や内部のパスが相手に届く（上の例のとおり固定の文言にする）
- **`CodeException` は HTTP のコードに効かない**（非検査。投げれば 500）
- **`validate(...)` の `ValidationException` は `error` を通る。**`error` で本文を組むと、枠組みの
  `{"validation": ...}` は付かない——上の例のように素通しするか、`e.errors()` を自分で返す
- **`ValidationExecutor` の失敗は `error` を通らない**（`cancel()` で抜けて `onCancel` が 422 を返す）。見た目を変えるなら `onCancel` を見る
- 自分で `AbstractExecutor` を書くなら、**`cancel()` はそこで抜ける**（後ろの行は走らない。枠組みが `onCancel` を呼ぶ）

## 静的ファイルと SPA

- **`AssetHandler` は `.js` / `.css` / `.woff` / `.woff2` を1年キャッシュさせる**（`public,max-age=31536000,immutable`）。
  ファイル名にハッシュが入っているかは見ず、**拡張子だけ**で決める。`app.js` の中身を変えて置き直しても、
  一度読んだブラウザは**1年取りに来ない**（`immutable` なので、ふつうのリロードでは確かめにも来ない）。
  ファイル名にハッシュを入れる（`app.9f3a1c.js`）か、`<script src="/assets/app.js?v=2">` のように参照を変える。
  開発中は `application.local.conf` などで `assets.immutable_max_age = 0s`（**単位を書く。素の `0` は起動時に落ちる**）。
  そのほかの拡張子は `assets.max_age`（既定 `0s`）＋ `must-revalidate` で、毎回確かめに来る
- **静的ファイルの置き場に空（`""` / `"/"`）を渡すと起動時に落ちる**（2.5.2。それまではクラスパスの根から返し、`application.conf` や `.class` が取れた）。
  `AssetHandler.mount("/static", "assets")` のように専用のディレクトリを渡す
- **SPA の書き換え（`SpaRewriter`）で差し込む値は `SpaRewriter.escapeHtml(...)` を通す。**パスの値はデコード済みで、そのまま HTML になる

詳しくは <https://jimble.io/ja/request-response.md> / <https://jimble.io/ja/errors.md> / <https://jimble.io/ja/assets.md>。
