# jimble

Java 製の Web アプリケーションフレームワーク。

- Web サーバーは [Helidon WebServer](https://helidon.io/) を使い、ルーティングは `routing.any()` で受けて自作の Router で処理する
- **アノテーションと DI を使わない。**コードを上から辿れば処理が分かることを最優先にする
- Web / バッチ / MQ を同じ `Context` で扱う

**0.2.0 を Maven Central に公開している。ドキュメントは <https://jimble.io>。**

```kotlin
plugins {
	application
	// src/main/jte を compileJava の前に Java へ変換する
	id("io.jimble.jte") version "0.2.0"
	// 開発用のホットリロード（./gradlew jimbleRun）
	id("io.jimble.run") version "0.2.0"
}

dependencies {
	implementation("io.jimble:jimble-web:0.2.0")
}
```

Gradle プラグインは **Plugin Portal ではなく Maven Central** から取る。
`settings.gradle.kts` に1ブロック要る。

```kotlin
pluginManagement {
	repositories {
		mavenCentral()
	}
}
```

雛形を作るなら `jimble new` が上を全部書いた状態で出す。

| アーティファクト | 中身 |
|---|---|
| `io.jimble:jimble-web` | Router / Request / Response / Session / SSE / WebSocket |
| `io.jimble:jimble-db` | SQL ビルダー / DB / マイグレーション / コード生成 |
| `io.jimble:jimble-util` | Data / Log / Conf / Dson / 各種ユーティリティ |
| `io.jimble:jimble-core` | Context / Executor |
| `io.jimble:jimble-batch` | バッチ / DB スケジューラ |
| `io.jimble:jimble-mq` | MQ（DB キュー） |
| `io.jimble:jimble-batch-manager` | バッチ管理画面 |
| `io.jimble:jimble-mcp` | MCP サーバー |
| `io.jimble:jimble-cli` | `jimble new` / migrate / codegen |

`jimble-web` を入れれば `jimble-db` / `jimble-util` / `jimble-core` は付いてくる。

---

ドキュメントは `docs/` にある。

> **このリポジトリには `docs/` を含めていない。**
> 設計メモに社内フレームワーク（jooby_base）の棚卸しが混ざっているため、
> 公開範囲を検討中である。決まったものから順に載せる。
> そのため `./gradlew :jimble-docs:site` は 0 ページになる（ビルドは通る）。


| ドキュメント | 内容 |
|---|---|
| `docs/requirements.md` | 要件定義 |
| `docs/design-m1.md` | M1 設計書 |
| `docs/design-m2.md` | M2 設計・実装メモ / `WebRequest`・`WebResponse` 調査 |
| `docs/design-m3.md` | M3 設計・作業メモ（移送の記録） |
| `docs/design-m4.md` | M4 設計・作業メモ（周辺ランタイム） |
| `docs/design-m5.md` | M5 設計・作業メモ（テンプレート / 静的配信） |
| `docs/design-m6.md` | M6 設計・作業メモ（AsyncData） |
| `docs/design-m7.md` | M7 設計・作業メモ（バッチ / MQ） |
| `docs/design-m8.md` | M8 設計・作業メモ（棚卸し / CLI / Phase 1 の締め） |
| `docs/design-m9.md` | M9 設計・作業メモ（SSE / MCP / WebSocket / ドキュメント / Maven Central / jimble.io） |
| `docs/design-m10.md` | M10 設計・作業メモ（AsyncData の先読み） |
| `docs/publishing.md` | **Maven Central への公開手順** |
| `docs/design-protocols.md` | WebSocket / SSE / MCP / gRPC の検討書 |
| `docs/site/` | **ドキュメントサイトの原稿**（`./gradlew :jimble-docs:site`） |
| `docs/probe-helidon.md` | Helidon 4.5.4 + Java 25 疎通確認 |
| `docs/jooby-base-inventory.md` | jooby_base 棚卸し |
| `docs/extends-jooby-analysis.md` | `ExtendsJooby` / `AppContext` 精読 |
| `docs/sample-app-analysis.md` | サンプルアプリ（RSS まとめ）調査 |

---

## 必要なもの

- **JDK 25**
- **Gradle 9 系**

## 初回セットアップ

Gradle Wrapper（9.7.1）はリポジトリに入っている。`./gradlew` をそのまま使う。

## ビルド

```bash
./gradlew build
```

JDK 25 が自動検出されない場合は、パスを渡す。

```bash
./gradlew build -Dorg.gradle.java.installations.paths=/path/to/jdk-25
```

## テスト

```bash
./gradlew test
```

### DB 結合テスト

実 DB が要るテストは `@Tag("db")` を付けてあり、**通常の `build` では実行されない**。
開発用 DB に接続して動かす。

```bash
./gradlew :jimble-db:dbTest
```

接続先は `jimble-db/src/test/resources/application.dbtest.conf`。
環境変数で上書きできる。

```bash
JIMBLE_TEST_DB_URL="jdbc:mariadb://127.0.0.1:3306/jimble_test?..." \
JIMBLE_TEST_DB_USER=jimble \
JIMBLE_TEST_DB_PASSWORD=jimble \
  ./gradlew :jimble-db:dbTest
```

---

## マイグレーション

SQL は `conf/migration/<スキーマ名>/` に置く。ファイル名の**自然順**に適用される。

```sql
# --- !Ups
create table site (
	id   bigint unsigned auto_increment primary key,
	name varchar(250) null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

# --- !Downs
drop table site;
```

SQL で書けない移行は Java で書く。**クラスパス走査はしないので明示的に登録する。**

```java
public class V20260901FillUserKana extends AbstractCodeMigration {

	@Override
	protected int versionYyyyMmDd () { return 20260901; }

	@Override
	protected void execute () {
		...
		addExecuteInfo("updated", count);
	}
}
```

```java
public static void main (String[] args) {
	CodeMigration.add(new V20260901FillUserKana());
	Migration.install();
	...
}
```

設定は次のとおり。**失敗したらアプリは起動しない。**

```hocon
migration {
  on_startup = auto        # auto | true | false（既定 auto = ローカル以外で適用）
  down = false             # 既定は down を実行しない
  lock_timeout_seconds = 60
}
```

ローカルでは起動時に何もしない。**スキーマを変えたら Gradle を回す**（下の「Gradle プラグイン」）。

---

## コード生成

DB のスキーマからテーブル定義クラスと型付きアクセサを生成する。**生成物はリポジトリにコミットする**（F-G-13）。

```bash
./gradlew migrate
./gradlew codegen
```

CI や本番デプロイからは Gradle を通さず CLI を直接叩ける。

```bash
java -cp <クラスパス> io.jimble.db.cli.JimbleDbCli migrate
java -cp <クラスパス> io.jimble.db.cli.JimbleDbCli codegen src/main/java
```

生成物はこうなる。

```java
public class GenItem extends Table {

	/* 商品ID */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	/* 商品コード */
	public static final Column code = new Column(instance(), "code", java.lang.String.class, false, null, false);

	/* 列一覧（生成時に確定。実行時のリフレクションはしない） */
	private static final List<Column> COLUMNS = List.of(id, code);

	...
}
```

jimble の管理テーブル（`migration` / `db_lock` など）は生成対象から外れる。

```hocon
codegen {
  package = "db"                       # 生成先パッケージ（既定 "db"）
  exclude_tables = ["legacy_import"]   # さらに外したいもの
}
```


---

## Gradle プラグイン

```kotlin
plugins {
	application
	id("io.jimble.db")
}
```

**ローカルでは `migrate` → `codegen` → `compileJava` が連鎖する**（要件 F-G-07）。
ローカル以外のビルドは `compileJava` だけを行うので、CI は DB に繋がなくてよい。

| タスク | 内容 |
|---|---|
| `migrate` | 未適用のマイグレーションを適用する |
| `codegen` | テーブル定義のコードを生成する（`migrate` のあと） |
| `codegenCheck` | コミットされている生成物がスキーマと一致するかを確かめる |

生成物はリポジトリにコミットする決まりなので（F-G-13）、**CI では `codegenCheck` を回す。**
スキーマを変えたのに生成し直すのを忘れると落ちる。

連鎖するかどうかは次の順で決まる。**環境判定に依存しない指定ができる。**

```kotlin
jimble {
	sourceRoot = "src/main/java"   // 生成先（既定）
	autoGenerate = true            // 1. ここに直接書く
	env = "local"                  // 3. これが local なら連鎖する（既定）
}
```

```bash
./gradlew build -Pjimble.autoGenerate=true   # 2. プロパティ
```

どのタスクも `io.jimble.db.cli.JimbleDbCli` を起動するだけで、
**ロジックは Gradle 側に置いていない。**CI からは CLI を直接叩ける（F-G-08）。

### テンプレート

```kotlin
plugins {
	application
	id("io.jimble.jte")
}
```

| タスク | 内容 |
|---|---|
| `generateJte` | `src/main/jte` を Java に変換する（`compileJava` の前） |
| `generateTestJte` | `src/test/jte` を Java に変換する |

**`.class` ではなく `.java` を出して、アプリと一緒にコンパイルする。**
テンプレートの型の間違いが**アプリのコンパイルエラーとして出る**（要件 F-W-10 / O-13）。

実行時コンパイルには対応しない。これは設定ではなく**依存関係で保証している。**
アプリが持つのは `gg.jte:jte-runtime` だけで、**コンパイラを含む `gg.jte:jte` は
このプラグインしか持たない。**

```java
context.response().putData("title", "ブログ");
context.response().view("blog/posts.jte");
```

```
@import io.jimble.util.data.Data
@param Data data

<h1>${data.getString("title")}</h1>
```

### ホットリロード

```kotlin
plugins {
	application
	id("io.jimble.db")
	id("io.jimble.jte")
	id("io.jimble.run")
}

jimbleRun {
	mainClass = "blog.BlogApp"
}
```

```bash
./gradlew :examples:blog:jimbleRun
```

`http://localhost:9000` を開くと、**その時点のソースで**アプリが動く。
直してリロードすれば、作り直してから応える。

```
[jimbleRun] 変わりました: examples/blog/src/main/jte/blog/posts.jte
[jimbleRun] 次のリクエストで入れ替えます
[jimbleRun] 入れ替えます
[jimbleRun] 作り直しました (5454ms)
[jimbleRun] アプリが起きました (1641ms)
```

- **保存では何もしない。**次のリクエストが来たときに作り直して入れ替え、
  そのリクエストを待たせてから返す。連続して保存しても再起動は1回で、
  それでいて「リロードしたときには必ず最新」になる
- **アプリは別プロセス**で動く（要件 F-X-02 / D-48）。
  止まりきらないスレッドが次の起動と二重に動くことがない。
  アプリだけをプロジェクトのツールチェーンで動かせる
- **ビルドに失敗したらブラウザに出す。**コンソールを見に行かなくてよい

| 名前 | 既定 | 内容 |
|---|---|---|
| `mainClass` | （必須） | アプリの `main` を持つクラス |
| `port` | 9000 | ブラウザで開くポート |
| `appPort` | `port + 100` | アプリが待ち受けるポート |
| `buildTasks` | `<このプロジェクト>:classes` | 作り直しに流すタスク |
| `watchDirs` / `excludeDirs` / `watchExtensions` | （下記） | 見張るところ |
| `restartMode` | `on_request` | `on_request` / `immediate` |

既定で見張るのはこのプロジェクトの `src` と `conf`、拡張子は
`.java .jte .html .js .css .conf .xml .properties .yml .sql`。

`classes` には `codegen` と `generateJte` が繋がっているので、
テンプレートも生成コードもここで作り直される。

## CLI

```bash
./gradlew :jimble-cli:installDist
export PATH="$PWD/jimble-cli/build/install/jimble/bin:$PATH"
```

```bash
jimble new my-blog
cd my-blog
gradle wrapper          # 1回だけ
./gradlew run
```

```
$ curl http://localhost:9000/hello
{"message":"hello, jimble"}
```

**手を入れずにビルドして起動できるものが出る。**DB も Redis も要らない。
使うようになったら、`build.gradle.kts` と `application.conf` の
コメントを外していく形にしてある。

| コマンド | 内容 |
|---|---|
| `jimble new <name>` | プロジェクトの雛形を作る |
| `jimble migrate` | 未適用のマイグレーションを適用する |
| `jimble codegen <ソースルート>` | テーブル定義のコードを生成する |
| `jimble version` | 版を表示する |

`migrate` と `codegen` はアプリの設定とマイグレーションの SQL を
クラスパスから探す。プロジェクトの中では `./gradlew migrate` を使う。

**要るもの：Java 25 と Gradle 9 以上。**
Gradle 8 は Java 25 の上では動かない（ツールチェーンとして使うのは問題ない）。

### 手元で試すとき

公開済みの版（0.2.0）でよければ、何も要らない。
**手元で直した jimble を試すとき**は、生成したプロジェクトが
ローカルの Maven リポジトリを先に見るので、先に publish しておく。

```bash
./gradlew publishToMavenLocal
./gradlew -p gradle-plugin publishToMavenLocal
```

`view()` にモデルを渡さないとレスポンス自身がモデルになるので、
**`Accept: application/json` で叩けば同じ口が JSON を返す。**

---

## サンプル

| サンプル | 内容 |
|---|---|
| `examples/hello` | DB なし。ルーティングとエラーハンドラだけ |
| `examples/blog` | **機能を一通り通すもの。**「これが動く＝その機能が動く」 |

`examples/blog` を動かすには DB が要る。

```bash
./gradlew :examples:blog:build -Pjimble.autoGenerate=true
./gradlew :examples:blog:run
```

| ルート | 通しているもの |
|---|---|
| `GET /` | jte / DB |
| `GET /posts` | JSON |
| `GET /posts/{id}` | AsyncList（記事→コメントの遅延読み込み） |
| `POST /posts` | トランザクション + MQ（ロールバックすればキューも消える） |
| `PUT /posts/{id}` | 全部置き換える |
| `PATCH /posts/{id}` | 送った項目だけ変える |
| `DELETE /posts/{id}` | 消す |
| `OPTIONS /posts` | CORS のプリフライト |
| `GET /posts.csv` | ストリーミング + CSV（全件をメモリに載せない） |
| `GET /form` / `POST /form` | CSRF / Flash / Cookie / ファイルアップロード |
| `GET /posts/reindex` | SSE（進捗を1件ずつ流す） |
| `ws://.../ws/posts` | WebSocket（新しい記事が流れてくる） |
| `POST /mcp` | MCP サーバー（`search_posts` / `create_post` / `blog://latest`） |

```bash
curl -XPOST -d '{"title":"はじめての記事","body":"本文です","published":true}' localhost:9000/posts
curl localhost:9000/posts
curl localhost:9000/posts.csv
open http://localhost:9000/form
```

バッチ・DB スケジューラ・MQ ワーカー・バッチ管理画面は
`BlogBatch` / `BlogScheduler` / `blog.mq` / `blog.batch` にある。

**サンプルが動き続けること**は `./gradlew :examples:blog:dbTest` で確かめる
（実サーバーを起動して 15 本叩く。要件 NF-T-06）。


---

## MCP サーバー

```java
public class BlogMcp extends McpController {

	{
		tool("search_posts", SearchPostsTool::new);
		tool("create_post", CreatePostTool::new);

		resource("blog://latest", LatestPostsResource::new);
	}

}
```

```java
install(BlogMcp::new);   // POST /mcp が生える
```

**上から読めば、このサーバーが何を公開しているかが全部分かる。**
アノテーションもクラスパスの走査も無い（原則1・原則2）。
ツールは `Supplier` なので**呼ばれるたびに1つ作る**（原則3）。

```bash
curl -s localhost:9000/mcp \
  -H 'Content-Type: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/list' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

対応する仕様は **2026-07-28 の1版のみ**（Streamable HTTP）。
`Origin` の検証、ヘッダと本文の突き合わせ、通知の 202、
知らないメソッドの 404 + `-32601` まで実装してある。

**ツールの失敗は例外にしない。**`ToolResult.error(...)` で返す。
JSON-RPC のエラーにすると<b>モデルには直しようがない</b>。

---

## SSE

```java
try (SseStream sse = context.response().sse()) {
	sse.send("progress", new Data().putData("percent", 10));
	sse.send("done", new Data().putData("ok", true));
}
```

**終わらないループを書かないこと。**
相手が切ったことは検知できず、書き込みが止まったまま帰ってこないことがある
（Java のソケットに書き込みタイムアウトが無い）。
1本の寿命には上限がある（`sse.max_duration_seconds`。既定 300 秒）。
`isOpen()` が見ているのは**上限と明示的な close だけ**で、相手の生死ではない。

SSE はもともと繋ぎ直す前提の仕組みなので（開いたときに `retry:` を送る）、
**有限のぶんを送って閉じる**のが正しい形である。

## WebSocket

```java
ws("/chat", ChatHandler::new);
```

```java
public class ChatHandler implements WsHandler {

	@Override
	public boolean onUpgrade (WsSession session) {
		// 認証はここで通す。Cookie が読めるのはこの時点だけ。断ると 403
		return session.cookie("token").equals(expected());
	}

	@Override
	public void onMessage (WsContext context, String message) {
		context.session().send("echo: " + message);
	}

}
```

- **Context はメッセージごとに1つ**（接続ごとではない）。MQ と同じ形。
  接続に紐づけたいものは `session.attributes()` に置く
- **ルートは HTTP と同じ表に載る。**起動時の一覧に `WS /chat` と出るし、
  同じパスを2回書けば起動時に落ちる
- **接続の一覧はフレームワークが持たない。**持つと複数台構成で
  「自分の台に繋いでいる人にしか届かない」ものになり、
  **1台で動かしているうちは正しく見えてしまう。**
  配信が要るなら MQ か Redis の Pub/Sub を挟む

---

---

## モジュール

| モジュール | 内容 | 状態 |
|---|---|---|
| `jimble-core` | HTTP を知らない層（`Context` / `Executor` / `AppLifecycle`） | **完了** |
| `jimble-util` | `Data` / `Log` / `Conf` / 型変換 / `Dson` / 各種ユーティリティ | **完了** |
| `jimble-db` | SQL ビルダー / `DB` / マイグレーション / コード生成 | **完了** |
| `jimble-web` | Router / Dispatcher / Request / Response / helidon 結線 | **完了** |
| `gradle-plugin` | `migrate` / `codegen` / jte 変換 / ホットリロードの Gradle タスク | **完了** |
| `jimble-batch` | バッチ実行基盤 + DB スケジューラ（cron） | **完了** |
| `jimble-mq` | MQ 実行基盤（DB キュー / リトライ / デッドレター） | **完了** |
| `jimble-batch-manager` | バッチ管理画面（Basic 認証つき） | **完了** |
| `jimble-cli` | プロジェクト雛形生成（`jimble new`）と migrate / codegen の入口 | **完了** |
| `jimble-mcp` | MCP サーバー（Streamable HTTP / 仕様 2026-07-28） | **完了** |
| `jimble-docs` | ドキュメントサイトの生成（jte + commonmark） | **完了** |

依存は一方向：`web → db → util → core`。`core` は HTTP も `Data` もログも設定も知らない。

---

## 現在の状態

**M1〜M8 完了 = Phase 1 完了。M9（SSE / MCP / WebSocket / ドキュメント / Maven Central / jimble.io）完了。M10（AsyncData の先読み）完了。**
**0.2.0 を Maven Central に公開済み。ドキュメントは <https://jimble.io> で公開済み。**
`examples/hello` と `examples/blog` が動き、テスト 514 件（+ 実 DB / Redis 結合テスト 159 件）が通る。
`jimble new` で作ったプロジェクトが、手を入れずにビルドして起動する。
機能カバレッジ表（要件 10.2）は、Phase 2 に回した2行を除いてすべて「済」。
機能カバレッジ表（要件 10.2）の「未」は解消した。

```bash
./gradlew :examples:hello:run
```

```bash
curl http://localhost:9000/hello                    # hello, jimble
curl http://localhost:9000/users/42                 # user id = 42
curl "http://localhost:9000/search/a%2Fb"           # keyword = a/b   ← %2F がパス区切りに化けない
curl http://localhost:9000/files/css/main.css       # file = css/main.css
curl http://localhost:9000/nope                     # エラー: 404 ...
curl http://localhost:9000/admin/secret             # エラー: 401 認証が必要です
curl -H 'X-Token: secret' http://localhost:9000/admin/secret   # 秘密のページ
```

### 実装済み

**jimble-core**

| クラス | 内容 |
|---|---|
| `Context` | 実行コンテキスト。`AutoCloseable` / `ScopedValue`（サブクラスが束ねる値を追加できる） |
| `BatchContext` / `MqContext` | バッチ・MQ の実行コンテキスト。**HTTP の偽物を作らずに成立する** |
| `ScopeCache` | 実行スコープキャッシュ。`null` も「取得済み」として記憶する |
| `Executor` / `AbstractExecutor` | 処理実行とキャンセル |
| `AppLifecycle` | 起動モード（WEB / BATCH）と停止フラグ |

**jimble-util**

| クラス | 内容 |
|---|---|
| `Data` | `LinkedHashMap<String, Object>` 継承。`Column` 版と `String` 版の取得・設定 API。**`toString()` は値を出さない要約**（F-D-26）|
| `AsyncData` / `AsyncList` | 遅延読み込み。**触った枝だけ読む。**比較も `toString()` も読み込みを起こさない |
| `Log` | 出力先（`Sink`）を差し替えられる。実行ID・SQL 実行回数・実行時間を `Context` から自動で付ける |
| `Conf` | HOCON（`application.conf` に `application.<env>.conf` を重ねる） |
| ほか | 型変換 / `Dson` / 文字列 / 日時 / URL / IO / CSV / XML / 暗号 / スレッド / ネットワーク / HTTP クライアント / `Paging` |

**jimble-db**

| クラス | 内容 |
|---|---|
| `DB` / `Column` / `Table` | SQL ビルダー、トランザクション、キャッシュ、分散ロック |
| `Migration` | SQL マイグレーション（`# --- !Ups` / `# --- !Downs`）。`Migration.install()` で起動時に適用する |
| `CodeMigration` / `AbstractCodeMigration` | Java で書くマイグレーション。**クラスパス走査はせず明示登録**（D-19） |
| `Generator` | スキーマからテーブル定義クラスと型付きアクセサを生成。**列一覧は静的に出力**（D-17） |

**jimble-batch**

| クラス | 内容 |
|---|---|
| `AbstractBatch` | `batchName()` と `execute(BatchArgs)` を書く。履歴・同時実行・中断は基底が持つ |
| `BatchRegistry` | **明示登録**（F-B-09）。`BatchRegistry.add(MyBatch::new)`。クラスパス走査はしない |
| `BatchExecutor` | CLI から起動する。実行されなかった理由が `BatchResult` で返る |
| `DbScheduler` | cron でバッチを動かす。**DB だけで動く**（Redis 不要。F-B-10）|
| `SchedulerControl` | スケジューラの入り切りと稼働状況 |

**jimble-mq**

| クラス | 内容 |
|---|---|
| `MqQueue` | DB をキューにする。`FOR UPDATE SKIP LOCKED` で1件ずつ取る |
| `MqExecutor` | `key()` と `execute(DB, Data)` を書く。**メッセージごとに1つ作られる** |
| `MqRegistry` | **明示登録**（F-M-07）。`MqRegistry.add(MyExecutor::new)` |

**jimble-batch-manager**

| クラス | 内容 |
|---|---|
| `BatchManagerController` | バッチ一覧・履歴・実行状況の画面と API。**認証情報が無ければ何も生えない** |
| `JimbleDbCli` | `migrate` / `codegen` の CLI。Gradle タスクもこれを呼ぶ |

**jimble-web**

| クラス | 内容 |
|---|---|
| `Controller` / `Router` | 小文字のルート定義 DSL（`path` / `before` / `after` / `error` / `get` / `post` / `put` / `patch` / `delete` / `head` / `options` / `trace` / `any` / `install`） |
| `RouteTree` | ルートツリーとマッチング。`jooby_base` からの移植で**既存実装の欠陥3点を修正**した |
| `PathSegments` | **生のパスを分割してからセグメントごとにデコードする**（`%2F` によるルート取り違えの防止） |
| `AttributeKey` | 既定値を必須にした型付きルート属性 |
| `JimbleApp` / `Dispatcher` / `Stage` | リクエスト処理。「送信済みなら打ち切る」判定を `Stage` 1箇所に集約 |
| `JimbleServer` | helidon-webserver 4.5.4 の起動と `routing.any()` 結線 |
| `Request` / `Response` | `Data` 継承。`jooby_base` の `WebRequest` / `WebResponse` を本移送したもの |
| `Cookies` / `Flash` / `Csrf` | 署名つき Cookie、次リクエストだけ残る値、CSRF |
| `Session` / `SessionStore` | DB / Redis / Cookie / なし の4種。保存は明示 |
| `ValidationRule` / `ValidationExecutor` | 標準バリデータ 14 種。失敗したら後続をキャンセル |
| `CorsHandler` / `BasicAuth` / `BotBlocker` | `before` に挿すガード |
| `AssetHandler` | 静的ファイル配信。ETag / 条件付き GET / jar とディレクトリ両対応 |
| `SpaHandler` / `MpaHandler` | SPA / MPA 配信。通常のルートと共存する |
| `ReverseProxy` | 別のサーバーへ流す。`any()` で全メソッドを1回で登録（F-R-21） |
| `Templates` / `JteEngine` | jte でのテンプレート描画。**事前コンパイル済みのみ**（F-W-10） |
| `RequestSource` / `ResponseSink` | **helidon を知っている唯一の接点。**サーバーを差し替えるときはこの2つの実装だけを書き換える |

## ドキュメントサイト

```bash
./gradlew :jimble-docs:site
```

`docs/site/ja/*.md` を読み、`docs/site/build/` に静的 HTML を出す（20 ページ）。

- **jimble 自身で作っている**（jte + commonmark）。外部の CDN には繋がない
- **コード片は実コードから抜く。**`// docs:begin <名前>` 〜 `// docs:end` の印を付け、
  Markdown 側では ```` ```java snippet=名前 ```` と書く
- **印が消えたらサイトのビルドが落ちる**（要件 NF-D-03）。ドキュメントが黙って古くならない
- 検索は索引を配って素の JavaScript で絞る。検索エンジンを足さない

```
docs/site/
	ja/*.md        原稿（front matter で title / summary / section / order）
	snippets/      コンパイルされないコード片（置き場所が無いものだけ）
	static/        site.css / search.js
	build/         出力
```

英語（`docs/site/en/`）は原稿を置けば出る。中身がある言語だけ切り替えリンクが出る。

出力は <https://github.com/hidemikimura/jimble-document> の `dist/` へ置き換えて push する。
Cloudflare Workers がそれを配る。手順はあちらの README にある。

---

## Maven Central へ公開する

```bash
export JIMBLE_SIGNING_KEY="$(gpg --armor --export-secret-keys <鍵ID>)"
export JIMBLE_SIGNING_PASSWORD='...'
export JIMBLE_CENTRAL_USERNAME='...'
export JIMBLE_CENTRAL_PASSWORD='...'

./gradlew centralBundle  -Pjimble.version=0.2.0   # 署名つきの zip を作る
./gradlew centralUpload  -Pjimble.version=0.2.0   # Portal へ送る（公開はまだ）
./gradlew centralStatus                           # 検証の結果を見る
./gradlew centralRelease                          # 公開する（取り消せない）
./gradlew centralDrop                             # やめる
```

- 公開先は **Central Portal**。Sonatype に公式の Gradle プラグインが無いので、
  **REST API を直に叩いている**（外部プラグインを足さない。要件 D-60）
- **鍵もトークンも環境変数だけ。**無い環境では署名を飛ばしてビルドが通る
- `-Pjimble.version` を渡さないと `0.2.0-SNAPSHOT`（次の版のスナップショット）になり、`centralUpload` は止まる
  （Central は `-SNAPSHOT` を受け付けず、公開したものは消せない）
- Gradle プラグインは **Maven Central のマーカー**で配る。Plugin Portal には出さない（D-22）

手順の全体（アカウント / `io.jimble` の名前空間 / **jimble.io の DNS TXT** / GPG 鍵 /
トークン）は `docs/publishing.md`。

---

### 次

**Phase 2。**英語版（NF-D-06）/ ドキュメント反映の自動化 / レートリミット（F-R-15）。
gRPC は入れない（依存が約5倍になるため。`docs/design-protocols.md`）。

---

## ライセンス

Apache License 2.0（`LICENSE`）。

同梱している第三者のものは `NOTICE` にまとめてある。
ボット判定の一覧（`crawler-user-agents.json`）は
[monperrus/crawler-user-agents](https://github.com/monperrus/crawler-user-agents)（MIT）をそのまま使っている。
