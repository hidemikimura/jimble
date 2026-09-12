---
title: サーバー設定
summary: ポート・待ち受けアドレス・上限・プロキシ・圧縮
section: 開発
order: 6
---

# サーバー設定

```conf
server {
	host                 = ""         # 待ち受けるアドレス。空なら全部
	port                 = 9000
	max_request_size     = 10485760   # リクエスト本文の上限（10MiB）
	max_header_size      = 16384      # ヘッダ全体の上限（16KiB）
	idle_timeout = 60s
	trust_proxy          = false      # X-Forwarded-* を信じるか
	compression          = true       # 応答を gzip で返すか
	access_log           = true       # アクセスログを出すか（[ログ](./log)）
	bot_access_log       = true       # ボットのアクセスログを分けるか
	strict_routes        = false      # 一生呼ばれないルートを例外にするか（CI では true に）

	shutdown_grace   = 0s      # 止め始めてから新規を断つまで（秒）
	shutdown_timeout = 15s     # 処理中を待つ上限（秒）
}
```

**書かなければ上の既定で動きます。**いま何が効いているかは起動ログに出ます。

```
jimble を起動しました: http://localhost:9000
サーバー設定: 待受=全部 / 本文上限=10485760byte / ヘッダ上限=16384byte / アイドル=60秒 / 圧縮=true / プロキシ信頼=false
```

## ポート

**ポートだけはシステムプロパティでも指定できます。**コンテナで「設定ファイルは触らずポートだけ変える」ためです。

```bash
java -Djimble.server.port=8080 -jar app.jar
```

優先順は `-Djimble.server.port` &gt; `server.port` &gt; 9000 です。

> [!TIP]
> `JimbleServer.start(app, 0)` にすると**空いているポートを自動で取ります。**
> テストはこれを使ってください（固定にすると、並べて走らせたときだけ落ちます）。
> 実際のポートは `server.port()` で取れます。

## 待ち受けるアドレス

**空なら全部のアドレスで待ちます。**外から見えてはいけないものは絞ってください。

```conf
server { host = "127.0.0.1" }
```

> [!TIP]
> 手元で動かす MCP サーバーはこれが要ります（[MCP](./mcp)）。
> **ファイアウォールに頼ると、設定を忘れたときに黙って公開されます。**

## 上限

| | 上限 | 超えたら |
| --- | --- | --- |
| リクエスト本文 | `server.max_request_size`（10MiB） | **413** |
| ヘッダ全体 | `server.max_header_size`（16KiB） | 接続ごと切られる |
| 何もしない接続 | `server.idle_timeout`（60秒） | 閉じる |

アップロードにはこれとは別の上限があります（[ファイルアップロード](./upload)）。

> [!WARN]
> **`upload.max_total_size` が `server.max_request_size` を超えていると起動時に落ちます。**
> 本文は先にこちらで切られるので、超えた分には届かないためです。
> 大きいものを受けるなら、**両方を上げてください**（既定はどちらも 10MiB）。

読み取り／書き込みのタイムアウトはありません。

## プロキシの後ろに置く

**既定では `X-Forwarded-*` を信じません。**信じるのは、前段のプロキシを必ず通ると分かってからです。

```conf
server { trust_proxy = true }
```

`true` にすると `proxyAddress()` が次の順で送信元を返します。

1. `CF-Connecting-IP`
2. `X-Real-IP`
3. `X-Forwarded-For` の**先頭**
4. どれも無ければ接続元

> [!TRAP]
> **`trust_proxy` を true にしても `address()` は変わりません。**
> `address()` は常に**TCP の接続元**（＝ロードバランサ）です。
> 送信元で判断するところは `proxyAddress()` を使ってください。

> [!TRAP]
> **ボット判定（`isBotAccess()`）は `address()` を見ます。**
> プロキシの後ろでは、判定に使う IP がロードバランサのものになります。

> [!TRAP]
> **`scheme()` も `X-Forwarded-Proto` を見ません。**
> TLS を前段で終端していると、アプリからは `http` に見えます。
> リダイレクト先を組み立てるときに気をつけてください。

> [!WARN]
> **直接叩ける状態で `true` にしないでください。**
> ヘッダは誰でも付けられるので、**送信元をいくらでも偽れます。**
> 信頼する upstream を IP で絞る仕組みはありません（真偽値1つです）。

## 圧縮

`server.compression`（既定 `true`）で **on / off** します。
何を圧縮するかはサーバー（Helidon）が `Accept-Encoding` を見て決めます。

> [!NOTE]
> **圧縮レベルや、種類ごとの除外はありません。**細かく制御したいときは前段でやってください。

## ボットを弾く

判定だけならアクセスログの分離で足ります（[ログ](./log)）。**弾く**なら宣言します。

```java
before(BotBlocker.forbidden());                          // 403 を返す
before(BotBlocker.of(context -> context.response().redirect("/")));   // 好きに返す
```

> [!NOTE]
> **既定の「弾く」動作は用意していません。**検索エンジンまで弾くと困るので、
> 何を返すかはアプリが決める形にしてあります。

## 無いもの

| | |
| --- | --- |
| **TLS / HTTPS** | 前段（ロードバランサ・nginx）で終端してください |
| **HTTP/2** | 依存に入れていません |
| **ヘルスチェックのルート** | jimble は用意しません（下の「止める」を参照） |

## 止める

**いきなり止めません。**`stop()` はこの順に進みます。

1. **「止め始めた」ことにする** — `Shutdown.isStopping()` が true になる。**普通のリクエストはまだ受ける**
2. `server.shutdown_grace` 待つ（既定 0）
3. **新しいリクエストを断つ**（503）
4. 処理中のものが終わるのを `server.shutdown_timeout`（既定 15 秒）まで待つ
5. サーバーを止める

**SIGTERM を受けたら自動で走ります**（コンテナはこれを送って待ちます）。

### ヘルスチェックを先に落とす

jimble はヘルスチェックのルートを用意しません。**こう書いてください。**

```java
get("/health_check", context ->
	context.response().send(Shutdown.isStopping() ? 503 : 200));
```

> [!TIP]
> ロードバランサがこの台を外すまでには時間がかかります。
> その間に来たリクエストを 503 にすると、**外から見たらエラー**です。
> だから 1 と 3 の間に猶予を置けるようにしてあります。
> ヘルスチェックの間隔 × 失敗回数ぶん（例：2秒 × 3回 → `shutdown_grace = 10s`）を入れてください。

> [!WARN]
> **待ちきれなかったら、残ったまま止めます。**
> そのときは `処理中のリクエストが N 件残ったまま停止します` が warn に出ます。
> 止まらないほうが困る（コンテナに強制終了される）ためです。

> [!NOTE]
> 自分で立てたスレッドも一緒に止めたいなら `Shutdown.add(...)` に預けてください
> （[実行モデル](./execution)）。

## jimble をプロキシにする

逆に、jimble から別のサーバーへ流すこともできます。

```java
install(() -> ReverseProxy.mount("/api", "http://backend:8080"));
```

`X-Forwarded-For` は**既存の値に足します**（上書きしません）。
タイムアウトは `proxy.connect_timeout`（5秒）と `proxy.request_timeout`（30秒）で、
転送に失敗したら **502** を返します。
