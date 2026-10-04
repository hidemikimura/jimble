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
	max_request_size     = 10MiB      # リクエスト本文の上限
	max_header_size      = 16KiB      # ヘッダ全体の上限
	idle_timeout = 60s
	trust_proxy          = false      # X-Forwarded-* を信じるか
	compression          = true       # 応答を gzip で返すか
	access_log           = true       # アクセスログを出すか（[ログ](./log)）
	bot_access_log       = true       # ボットのアクセスログを分けるか
	strict_routes        = false      # 一生呼ばれないルートを例外にするか（CI では true に）

	shutdown_grace   = 0s      # 止め始めてから新規を断つまで（秒）
	shutdown_timeout = 15s     # 処理中を待つ上限（秒）

	backlog            = 1024  # 受け付け待ちの接続を OS に持たせる数
	write_queue_length = 0     # 応答を書き出す列の長さ。0/1 は「列を作らない」
	smart_async_writes = false # 列があるとき、空いていればその場で書く
}
```

## 接続の受け付けと書き出し

**`backlog`** は、受け付けが追いつかないあいだ **OS が代わりに持ってくれる接続の数**です。
超えた分は **OS が断ちます**——アプリまで届かないので、**ログには1行も出ません**。
上げるのは「短時間にどっと来る」使い方（起動直後・キャンペーン・再接続の集中）で、
**捌く速さは変わりません**。詰まりを待たせるだけです。

> [!NOTE]
> **OS 側の上限にも頭を押さえられます**（Linux の `somaxconn`）。
> ここを大きくしても、そちらが小さければそちらで切られます。

**`write_queue_length`** は、応答を書き出す列の長さです。
**0 か 1 なら列を作らず**、その場で書き切ります（既定）。
2 以上にすると別のスレッドが書き出すので、**遅い相手に書いているあいだ、処理のほうが先に進めます**。
ただし**列に積んだ分はメモリに載る**ので、大きくすれば速くなるものではありません。

**`smart_async_writes`** は、列があるときに
「いつも列に積む」か「**空いていればその場で書き、混んできたら列に積む**」かの切り替えです。

> [!WARN]
> **`smart_async_writes` は単独では効きません。**
> `write_queue_length` が **2 以上**でないと、helidon はこの値を読みません（列が無いからです）。
> **`write_queue_length` が 0 か 1 のまま `smart_async_writes = true` と書くと、起動時に落ちます**——
> 黙って無視すると、「効いているのに速くならない」としか見えなくなるためです。

いま何が選ばれたかは起動ログに出ます。

```
サーバー設定（接続）: 接続待ち=1024 / 書き出し列=作らない（その場で書く）
サーバー設定（接続）: 接続待ち=1024 / 書き出し列=32（空いていればその場で書く）
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
server {
	trust_proxy      = true
	trusted_proxies  = ["10.0.0.0/8"]   # 中継の IP / CIDR。書けば、ここから来たときだけヘッダを見る
	client_ip_header = ""                # "CF-Connecting-IP" / "X-Real-IP" など。書いたときだけ信じる
}
```

`true` にすると `proxyAddress()` が次の順で送信元を返します。

1. `trusted_proxies` を書いていて、接続元がそこに入っていなければ、**接続元**（ヘッダは名乗りにすぎない）
2. `client_ip_header` を書いていれば、そのヘッダの値
3. `X-Forwarded-For` を**右から**見て、`trusted_proxies` に入っていない最初のもの（書いていなければ**右端**）
4. どれも無ければ接続元

> [!TRAP]
> **`X-Forwarded-For` の左端は、クライアントが好きに名乗れます。**
> nginx・ALB・jimble の ReverseProxy は、クライアントが送ってきた値の**後ろに足す**からです。
> 2.2.2 までは左端と、`CF-Connecting-IP` / `X-Real-IP` を無条件に信じていたので、
> 名乗る値を変えるだけで IP ごとのレート制限をすり抜けられました。
> 中継が2段以上（CDN → ロードバランサ など）なら、`trusted_proxies` に中継の範囲を書いてください。

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

### 動いている印（`RUNNING_PID_{ポート}`）

待ち受けを始めると、**アプリの jar があるディレクトリ**に
`RUNNING_PID_8080` のような名前のファイルを置きます。中身はプロセス ID です。
`stop()` が済むと消えます（SIGTERM でも消えます）。

```bash
kill $(cat /opt/app/RUNNING_PID_8080)   # 止める
test -f /opt/app/RUNNING_PID_8080       # 動いているか
```

- 名前にポート番号が入るので、同じディレクトリから複数立てても互いの印を消しません
- **jar から動いていないとき（`jimbleRun`・テスト）は置きません**。置く場所が「jar のディレクトリ」なので、無いものの隣には置けません
- 前回の印が残っていたら（`kill -9` や電源断）warn を出して上書きします。**起動は止めません**
- 書けなくても（ディレクトリが読み取り専用など）warn を出すだけで、待ち受けは続けます

## jimble をプロキシにする

逆に、jimble から別のサーバーへ流すこともできます。

```java
install(() -> ReverseProxy.mount("/api", "http://backend:8080"));
```

`X-Forwarded-For` は**既存の値に足します**（上書きしません）。
タイムアウトは `proxy.connect_timeout`（5秒）と `proxy.request_timeout`（30秒）で、
転送に失敗したら **502** を返します。

| | 待ちの上限 |
| --- | --- |
| 繋ぐまで | `connect_timeout` |
| リクエストを送るあいだ | `request_timeout` のあいだ1バイトも進まなければ切る |
| 送り終えてから、応答のヘッダが届くまで | 全体で `request_timeout`（2.2.4 から。それまでは1回の読み込みごとだったので、少しずつ返されると終わらなかった） |
| 応答の本文 | 1回の読み込みごとに `request_timeout`。全体は `proxy.body_timeout`（既定 0 ＝ 上限なし）か、`ReverseProxy#bodyTimeout` |

本文の全体を既定で切らないのは、大きなダウンロードや SSE を途中で切らないためです。
パスはセグメントごとにエンコードし直して送り、**`.` / `..` を含むパスは 400** で断ります
（ベース URL のパスの外へ出させないため）。

### nginx の proxy_set_header などに当たるもの

設定したプロキシを `mount` に渡します。**設定は `mount` の前に済ませてください**（リクエストを受けたあとに変えると例外です）。

```java
install(() -> ReverseProxy.mount("/shop", new ReverseProxy("http://shop:9000")
	.preserveHost()                                     // proxy_set_header Host $http_host
	.setHeader("X-App", "front")                        // proxy_set_header X-App front
	.setHeader("X-User", context -> userId(context))    // 値をリクエストから作る（null なら送らない）
	.removeHeader("Cookie")                             // proxy_set_header Cookie ""
	.hideResponseHeader("X-Powered-By")                 // proxy_hide_header X-Powered-By
	.redirect("http://shop.internal/", "/shop/")        // proxy_redirect http://shop.internal/ /shop/
	.cookieDomain("shop.internal", "example.com")       // proxy_cookie_domain shop.internal example.com
	.cookiePath("/", "/shop/")));                       // proxy_cookie_path / /shop/
```

| | nginx と同じ既定 |
| --- | --- |
| `Host` | **転送先のもの**を送ります（`proxy_set_header Host $proxy_host`）。元の `Host` は `X-Forwarded-Host` / `X-Forwarded-Port` で渡します。転送先が **Host で振り分ける**なら `preserveHost()` を付けてください |
| `Location` / `Refresh` | **転送先の URL で始まるものを、ブラウザから見えるパスに書き換えます**（`proxy_redirect default`。`http://backend:8080/moved` → `/api/moved`）。相対の値（`/moved`）はそのままです。切るなら `noRedirectRewrite()` |
| 転送先との接続 | 1回ごとに切ります（keepalive は持ちません）。`https://` の転送先は証明書のホスト名を確かめます |
| 圧縮 | 転送先が gzip で返した本文は、そのまま返します（もう一度圧縮しません） |
