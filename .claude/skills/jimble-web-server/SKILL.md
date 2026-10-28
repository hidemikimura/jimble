---
name: jimble-web-server
description: jimble の Web をロードバランサやプロキシの後ろに置く・外と繋ぐときに使う。trust_proxy と X-Forwarded-For / X-Forwarded-Proto（送り元の IP・isSecure・HSTS）、IPUtil.inRange、流量制限（RateLimit は宣言ごと）、CORS、リバースプロキシ（ReverseProxy・タイムアウト）、アクセスログで伏せる値、MCP サーバー（/mcp の認証）を含む。
---

# jimble の Web をプロキシの後ろに置く・外と繋ぐ

全体の決まりと、ほかの skill への案内は `jimble-web`。

## プロキシの後ろ

- **ロードバランサの後ろで `trust_proxy = true` にするなら、`X-Forwarded-For` は右から読む**（2.2.3）。
  中継が2段以上なら `server.trusted_proxies`（IP / CIDR）を書く。`CF-Connecting-IP` / `X-Real-IP` は
  `server.client_ip_header` に書いたときだけ信じる。`RateLimit.perIp` はここで決めた IP で数える
- **`trust_proxy = true` なら、`request().scheme()` と `isSecure()` は `X-Forwarded-Proto` を見る**（2.5.3。`trusted_proxies` を書けばそこからの接続だけ、値が複数なら右端）。
  TLS をロードバランサで終端していても **`security_headers.hsts` が出るようになる**（2.5.2 までは一度も出ていなかった）
- **`IPUtil.inRange(対象, 範囲)` の対象は IP の字面だけ**（2.2.4。名前は DNS で引かず、範囲外）。送り元の IP は `context.request().address()` を渡す

## 流量制限と CORS

- **流量制限は宣言ごとに数える**（2.5.2。それまではログインの `perIp(5, 1m)` と `/api` の `perIp(60, 1m)` が同じ枠を分け合い、ログインの制限が効いていなかった）
- **`new Cors().addAllowOrigin("*")` と `allowCredentials(true)` は一緒に使えない**（例外。2.2.3）。資格情報つきなら、オリジンを並べる

## リバースプロキシ

- **`ReverseProxy` は `.` / `..` を含むパスを 400 で断る**（2.2.2）。転送先の `Host` やヘッダを決めるなら `preserveHost()` /
  `setHeader(...)`、`Location` の書き換えは既定で入る（`noRedirectRewrite()` で外す）（2.2.0）
- **`proxy.request_timeout`（既定 30s）は「送り終えてから応答のヘッダが届くまで」の全体の上限**（2.2.4。それまでは1回の読み込みごと）。
  ヘッダを返すまで長くかかる転送先なら上げる。本文の全体の上限は `proxy.body_timeout` / `ReverseProxy#bodyTimeout`（既定 0 ＝ 上限なし。
  ダウンロードや SSE を通すルートでは 0 のまま）

## アクセスログ

**トークンなどの秘密はパスに入れない**（`/reset/{token}` ではなく `/reset?token=...`）。アクセスログはクエリの `token` / `password` / `api_key` などの値を `***` にする（2.5.12）が、
パスは伏せられない。アプリ固有の名前は `server.access_log_mask = ["invite"]` で足す。
アクセスログを `AsyncAppender` に替えたのは 2.5.2 の `jimble new` の雛形から（前の版で作ったアプリは書き換える。`log.md`）。

## MCP サーバー

**`/mcp` には認証が無い。**`McpController` を継承したクラスの初期化ブロックに `before(...)` を書く
（`Origin` の検査はブラウザしか止めない）。**別のブロックの `path("/mcp", () -> before(...))` は効かない**（フィルタは書いたブロックのルートにしか付かない）。

詳しくは <https://jimble.io/ja/server.md> / <https://jimble.io/ja/ratelimit.md> / <https://jimble.io/ja/mcp.md> / <https://jimble.io/ja/log.md>。
