---
title: テスト
summary: 本物のサーバーを立てて叩く。DB を使うテストは分ける
section: 開発
order: 4
---

# テスト

jimble に**テスト専用のモジュールやアノテーションはありません。**
JUnit 5 と `java.net.http.HttpClient` だけで書きます。

道具はこれだけです。

| やること | 使うもの |
| --- | --- |
| サーバーを立てる | `JimbleServer.start(app, 0)`（**ポート 0 で空いているところ**） |
| 止める | `server.stop()` |
| HTTP を投げる | JDK の `HttpClient` |
| 設定を差し替える | `Conf.replace(config)` / `Conf.reload()` |
| DB を使う | `DBUtil.load(...)` / `Migration.install()` / `DBUtil.stop()` |

## 立てて、叩く

**これが基本形です。** 実際に Helidon が起動するので、
ルーティング・フィルタ・エラー処理・Content-Type まで通しで確かめられます。

```java snippet=test-server
```

ポートを `0` にするのが要点です。固定にすると、
**他のテストと同時に走ったときだけ落ちます。**

```java
@Test
void hello () throws Exception {
	assertEquals("hello", request("GET", "/hello").body());
}
```

> [!TIP]
> `HttpClient` に `CookieManager` を持たせると、セッション・CSRF・Flash まで通ります。
> リダイレクトを検証したいときは `followRedirects(HttpClient.Redirect.NEVER)` にしてください
> （既定では追ってしまい、302 を見られません）。

## HTTP を通さずにルータだけ叩く

サーバーを立てずに `Dispatcher` を直接呼ぶこともできます。速いぶん、
Helidon 側の挙動（ストリーム送信や圧縮）は確かめられません。

```java snippet=test-dispatcher
```

> [!WARN]
> `Fakes`（上のコードの `FakeRequestSource` / `FakeResponseSink`）は
> **jimble のテストの中にあるもので、公開していません。**
> アプリから同じことをするなら `RequestSource` / `ResponseSink` を自分で実装します。
> 手間に見合うことは少ないので、**まずは上の「立てて、叩く」を使ってください。**

## DB を使うテスト

### 分ける

DB が要るテストには印を付けて、**普段のビルドからは外します。**
繋ぎ先が無い環境（CI やクローンした直後）でビルドが赤くなるのを避けるためです。

```java
@Tag("db")
class PostIntegrationTest { ... }
```

アプリ側の `build.gradle.kts` にこう足します。

```kotlin
tasks.withType<Test>().configureEach {
	useJUnitPlatform { excludeTags("db") }
}

tasks.register<Test>("dbTest") {
	group = "verification"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform { includeTags("db") }

	// 設定を dbtest に切り替える
	systemProperty("jimble.env", "dbtest")

	// 前のテストが TRUNCATE した結果を見てしまわないよう、毎回走らせる
	outputs.upToDateWhen { false }
}
```

`conf/application.dbtest.conf` を置いて、繋ぎ先を書きます。
**共通の設定を読むのは1行目の `include` です**（[設定](./config)）。

```conf
include "application.conf"

db {
	main {
		url      = "jdbc:mariadb://127.0.0.1:3306/app_test"
		url      = ${?TEST_DB_URL}
	}
}
```

### 立ち上げる

```java snippet=test-db-app
```

順番が決まっています。

1. **`Conf.reload()`** — 環境（`jimble.env`）は最初に読まれた時点で決まっているので、読み直す
2. `Migration.install()` — テーブルを作る
3. `DBUtil.load(...)` — 接続する
4. `JimbleServer.start(app, 0)`

後始末は `@AfterAll` の `DBUtil.stop()`、
各テストの前に `TRUNCATE TABLE ...` です。

> [!TRAP]
> **テストごとに自動でロールバックする仕組みはありません。**
> 書いたものは残ります。`@BeforeEach` で消してください。

### 並べて走らせない

> [!WARN]
> DB を使うテストを並列で走らせると、**あるテストの `TRUNCATE` が別のテストの行を消します。**
> 「たまに落ちる」でしか出ないので、原因を追うのに時間を取られます。
> Gradle の共有サービス（`maxParallelUsages = 1`）で直列にするのが確実です。

## 設定を差し替える

```java
// 既存の設定に重ねる（ふつうはこちら）
Conf.replace(ConfigFactory
	.parseString("upload.max_file_size = 10")
	.withFallback(Conf.conf().config()));
```

> [!TRAP]
> `Conf.replace` は**グローバルで、元に戻す口がありません。**
> 差し替えたテストは `@AfterEach` で `Conf.reload()` してください。
> 忘れると、**あとから走ったテストだけが落ちます**（しかも実行順しだいで変わります）。

## jimble 自身のテストを動かす

```bash
./gradlew build                 # DB 不要のぶん
./gradlew :jimble-db:dbTest     # 実 MySQL / MariaDB を使うぶん
./gradlew :jimble-db:pgTest     # 同じテストを実 PostgreSQL に流す
```

`dbTest` と `pgTest` は**まったく同じテスト**を走らせます。
違うのは読む設定ファイル（`application.dbtest.conf` / `application.pgtest.conf`）だけで、
後者に `product = "postgresql"` が入っています。
**テストのコードは1行も変えていません。**

繋ぎ先は環境変数で変えられます。

```bash
JIMBLE_TEST_DB_URL="jdbc:mariadb://127.0.0.1:3306/jimble_test" \
JIMBLE_TEST_DB_USER=jimble \
JIMBLE_TEST_DB_PASSWORD=jimble \
  ./gradlew :jimble-db:dbTest

JIMBLE_TEST_PG_URL="jdbc:postgresql://127.0.0.1:5432/jimble_test" \
JIMBLE_TEST_PG_USER=jimble \
JIMBLE_TEST_PG_PASSWORD=jimble \
  ./gradlew :jimble-db:pgTest
```

これは **jimble 本体のテスト用**です。
`examples/` のサンプルは **`JIMBLE_SAMPLE_DB_*` / `JIMBLE_SAMPLE_PG_*`** という別の名前を見ます。

> [!NOTE]
> **名前を分けているのは、混ぜると事故になるためです。**
> 以前はサンプルも `JIMBLE_TEST_*` を見ていたので、
> この環境変数を立てると<b>サンプルのマイグレーションが `jimble_test` に流れ込み</b>、
> 履歴が混ざって<b>jimble 本体のマイグレーションテストが落ちて</b>いました。
> 落ち方が「フレームワークの不具合」に見えるのが厄介なところでした。

> [!NOTE]
> `jimble new` が作る雛形には、まだテストの足場（JUnit の依存と `dbTest`）が入っていません。
> 上のとおり手で足してください。
