---
title: jimble とは
summary: Java 25 と Helidon の上に載る、注釈もDIもリフレクションも使わない Web フレームワーク
section: はじめに
order: 1
---

# jimble とは

jimble は Java の Web フレームワークです。Helidon の Níma（仮想スレッド）の上に載り、
ルーティング・DB・テンプレート・バッチ・MQ・SSE・WebSocket・MCP をひとまとまりで持ちます。

```java snippet=hello-route
```

## 何が違うのか

ほとんどのフレームワークは「書く量を減らすこと」を目指します。
jimble が目指すのは **読む人が上から順に追えること** です。

- **注釈がない。** `@Controller` も `@Inject` も `@Transactional` もありません。
  ルートは `get("/hello", ...)` と書いてあるところにしかありません。
- **DI コンテナがない。** オブジェクトは `new` します。誰が誰を作るかはコードに出ています。
- **起動時にクラスパスを走査しない。** 起動が速く、ネイティブイメージとも喧嘩しません。
  そのかわり、使うものは自分で `install()` します。

この3つを守ると、「なぜこれが動くのか分からない」という時間が消えます。
かわりに数行だけ多く書きます。jimble はそちらのほうが安いと考えています。

詳しくは [考え方](./principles) を読んでください。

## いま何ができるか

| できること | 見るところ |
| --- | --- |
| HTTP のルーティング、パスパラメータ、フィルタ | [ルーティング](./routing) |
| リクエストの読み取りと検証、レスポンスの組み立て | [リクエストとレスポンス](./request-response) |
| ファイルアップロードとダウンロード | [ファイルアップロード](./upload) |
| 入力の検証、ページング | [検証とページング](./validation) |
| 例外・未マッチ・検証失敗の扱い | [エラー処理](./errors) |
| jte テンプレート（コンパイル済み） | [テンプレート](./view) |
| 静的ファイル・SPA・MPA の配信 | [静的ファイルと SPA](./assets) |
| セッション・CSRF・Flash・署名付き Cookie | [セッションと安全側の既定](./session-security) |
| レートリミット（ルート単位・IP 単位） | [レートリミット](./ratelimit) |
| 仮想スレッド、何がどこで動くか、止め方 | [実行モデル](./execution) |
| SQL DSL、テーブル定義の生成 | [DB を使う](./db) |
| マイグレーションとコード生成の流れ | [マイグレーションとコード生成](./codegen) |
| トランザクション | [トランザクション](./transaction) |
| キャッシュ（DB / メモリ / Redis）と分散ロック | [キャッシュとロック](./cache) |
| cron で回るバッチ、管理画面 | [バッチ](./batch) |
| DB を使ったキュー | [MQ](./mq) |
| Server-Sent Events | [SSE](./sse) |
| WebSocket | [WebSocket](./websocket) |
| Model Context Protocol のサーバー | [MCP](./mcp) |
| CLI の入れ方、雛形の生成、ホットリロード | [jimble コマンド](./cli) / [ホットリロード](./hot-reload) |
| Gradle のタスクと設定 | [Gradle プラグイン](./gradle) |
| テストの書き方（DB を使うぶんの分け方も） | [テスト](./testing) |
| Data・JSON・HTTP クライアント・CSV・暗号 | [ユーティリティ](./util) |
| ポート・上限・プロキシ・圧縮 | [サーバー設定](./server) |
| アクセスログ、実行 ID、logback の設定 | [ログ](./log) |

## 版について

**このサイトは、いちばん新しいリリースの内容です。**
反映はリリースのときだけなので、ここに書いてあることは<b>公開されている版で動きます</b>。

公開されているものは
[Maven Central](https://repo.maven.apache.org/maven2/io/jimble/) にあります。
依存の座標と、そのまま動くビルドファイルは [Gradle プラグイン](./gradle) に、
ソースは [GitHub](https://github.com/hidemikimura/jimble) にあります。

## まず動かす

[5分で最初のエンドポイント](./quickstart) へ進んでください。
