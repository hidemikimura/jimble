---
title: ログ
summary: Log の使い方、アクセスログ、実行 ID、メトリクス、トレース、logback の設定
section: 開発
order: 7
---

# ログ

```java
Log.info("起動しました");
Log.warn("設定が足りません: %s".formatted(key));
Log.debug("見つかりませんでした");
Log.error(cause, "保存できませんでした: id=%d".formatted(id));
```

> [!WARN]
> **`{}` のプレースホルダは使えません。**SLF4J の書式ではないので、
> `Log.info("id={}", id)` と書いても `{}` のまま出ます。
> `String.formatted(...)` で組み立ててください。

> [!NOTE]
> `Log.error(cause, "説明")` は、**メッセージが例外のもの**になります。
> 書いた説明は構造化データのほうに入り、同梱のエンコーダが1行目に並べて出します。
>
> ```
> [2026-09-07 13:12:19]-[main] error 保存できませんでした / java.lang.IllegalStateException: 書けません
> ```

## ロガーの名前

| ロガー | 何が出るか |
| --- | --- |
| `access` | アクセスログ（1リクエストに1行） |
| `access.bot` | ボットのアクセスログ |
| `error` | `Log.error(...)` |
| `io.jimble.util.log.Log` | `Log.info` / `warn` / `debug` / `trace` |
| 任意の名前 | `Log.appInfo("my.audit", ...)` などで指定 |

> [!TRAP]
> `Log.info` などの**呼び出し元クラス名はロガー名になりません。**
> いつも `io.jimble.util.log.Log` です。
> 用途で分けたいときは `Log.appInfo("名前", ...)` を使ってください。

## どのログにも必ず入るもの

| キー | 中身 |
| --- | --- |
| `request_id` | 実行 ID（リクエストの外では `-`） |
| `sql_execute_count` | そのリクエストで投げた SQL の本数 |
| `sql_execute_time` | その合計時間（ミリ秒） |
| `local_info` | **サーバー自身**の IP とホスト名 |

**SQL の本数と時間がどのログにも入る**のが要点です。
N+1 は「ログを見れば分かる」ようにしてあります（要件 NF-O-02）。

## アクセスログ

**1リクエストに1行**、リクエストの終わりに出ます。**ルートに当たらなくても出ます**（404 も残る）。

| キー | 中身 |
| --- | --- |
| `method` / `path` / `query` | リクエスト |
| `status` | ステータスコード |
| `elapsed` | 実行時間（ミリ秒） |
| `matched` | ルートに当たったか |
| `bot` | ボットと判定したか |

```json
{"logger_name":"access","level":"INFO","message":"GET /users/42 200",
 "request_id":"m1abcd-xyz-1","sql_execute_count":2,"sql_execute_time":4.0,
 "method":"GET","path":"/users/42","query":"","status":200,"elapsed":12.34,
 "matched":true,"bot":false}
```

> [!TRAP]
> **クライアントの IP と User-Agent は入っていません。**`local_info` はサーバー自身のものです。
> 要るなら `Log.addFieldProvider(...)` で足してください。

### ボットを分ける

`server.bot_access_log`（既定 `true`）が有効なら、ボットの行は **`access.bot`** に出ます。
判定は User-Agent の一覧（`crawler-user-agents`）との照合です。

> [!TIP]
> クローラは全体の何割にもなります。分けておくと、
> **人のアクセスだけを数えられます。**同じところに出したいなら `false` にしてください。

### 切る

`server.access_log = false` で、1行も出さなくなります。

```conf
server {
	access_log = false
}
```

**1リクエストの中でいちばん大きいのがここです**（割り当ての約半分。約 1,965 byte）。
切ると、行を組み立てる仕事も、ボット判定（User-Agent の照合）も止まります。

秒あたりの本数がどれだけ変わるかは、**`logback.xml` の書き方でほとんど決まります。**
2コアの台・1接続で測ると（D-172）、アクセスログを切った jimble は**素の helidon の 94%**
——jimble の上乗せは、測ってもほとんど出てきません。
そこから、**1行を組み立てるぶんが -10%（p50 で +10µs）**、
**その1行を JSON にして書き出すぶんが -29%（p50 で +110µs）** です。
つまり**代金のほとんどは、jimble ではなく logback が同期で書き出すところ**にあります。
切る前に、まず**出し方**（`AsyncAppender` を挟む、出す先を変える、項目を減らす）を見てください。

**台によって違うので、自分の台で測ってください**
（`jimble-load/load.sh` が、出す版・組み立てるだけの版・出さない版・素の helidon を並べて測ります）。

**メトリクスとトレースは残ります。**切って消えるのはアクセスログだけです。
どちらも**それ自体はほとんど費用がかかりません**（下記）。

> [!TRAP]
> **既定は `true` のままにしてください。**切ると、
> **後から「あのとき何が起きたか」を調べる手段が無くなります。**
> 500 が出ていたことも、誰がどのパスを叩いたかも残りません。
> 切るのは、**前段（ロードバランサや nginx）が同じ内容を残していて、
> かつ実測して足りない**と分かったときだけです。

## 実行 ID

`36進の時刻-乱数-連番` で、リクエスト（や WebSocket のメッセージ、バッチ）ごとに1つ採番されます。
持ち回りは `ScopedValue` で、**MDC は使っていません。**

先頭が時刻なので、**ID を並べ替えると時系列になります。**

> [!NOTE]
> **レスポンスヘッダには出ません。**
> 「この画面のエラーのログを探したい」を成り立たせるなら、
> `after` で `context.response().setResponseHeader("X-Request-Id", context.executionId())` を自分で書いてください。

## SQL のログ

SQL の**本数と時間**は自動で数えます（上の表）。

> [!WARN]
> **SQL 文そのものとバインド値はログに出しません。**
> 遅いクエリの閾値もありません。
> 出したい場合は DB 側（`general_log` / `slow_query_log`）で見てください。

`log.db = true` にすると、アプリが `DBLog.save(db, data)` で書いたものが
`db_log` テーブルに入ります。**SQL を自動で記録する機能ではありません。**

## logback の設定

jimble は logback とエンコーダを持っていますが、**設定ファイルはアプリのものです。**
`jimble new` の雛形は `conf/logback.xml` を作ります（`conf/` は jar に入ります）。

```xml
<configuration>

	<!-- 人が読む形 -->
	<appender name="console" class="ch.qos.logback.core.ConsoleAppender">
		<encoder>
			<pattern>%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n</pattern>
			<charset>UTF-8</charset>
		</encoder>
	</appender>

	<!-- 例外。スタックトレースを字下げして出す（jimble 同梱） -->
	<appender name="error" class="ch.qos.logback.core.ConsoleAppender">
		<target>System.err</target>
		<encoder class="io.jimble.util.log.encoder.LogbackErrorEncoder"/>
	</appender>

	<!-- 機械が読む形。1行に1つの JSON（jimble 同梱） -->
	<appender name="json" class="ch.qos.logback.core.ConsoleAppender">
		<encoder class="io.jimble.util.log.encoder.LogbackJsonEncoder"/>
	</appender>

	<logger name="access" level="INFO" additivity="false">
		<appender-ref ref="json"/>
	</logger>

	<logger name="access.bot" level="INFO" additivity="false">
		<appender-ref ref="json"/>
	</logger>

	<logger name="error" level="ERROR" additivity="false">
		<appender-ref ref="error"/>
	</logger>

	<root level="INFO">
		<appender-ref ref="console"/>
	</root>

</configuration>
```

> [!TRAP]
> **`access.bot` は `access` の子です。**`additivity="false"` を外すと、
> ボットの行が**両方に出ます**（人のアクセスを数えているつもりで二重に数えます）。

> [!WARN]
> 設定ファイルを置かないと logback の既定になり、
> **アクセスログもアプリのログも同じところに混ざります。**

環境で分けたいときは、`logback.xml` の代わりに
`-Dlogback.configurationFile=conf/logback.prod.xml` を渡してください。

## テストで拾う

```java snippet=log-sink
```

`Log.sink(...)` は**全体を差し替えます**。`@AfterEach` で `Log.resetSink()` を忘れないでください。

## メトリクス

`Metrics.snapshot()` が、いまの値をまとめて返します。

```java
get("/metrics", context -> context.response().json(Metrics.snapshot()));
```

> [!WARN]
> **jimble は `/metrics` のルートを用意しません。**外に晒すかどうか、認証を付けるかどうかは
> アプリの都合なので、フレームワークが握ると閉じたいときに閉じられません
> （[ヘルスチェック](./server)と同じ考え方です）。
> 上の1行は**そのまま書くと誰でも見られます。**社内からだけ見せる、
> 認証を通す、といった手当てをしてください。

返る形はこうです。

```json
{
  "counter": { "http.request": 1234, "http.status.2xx": 1230, "http.status.5xx": 4 },
  "latency": {
    "http.GET /posts/{id}": {
      "count": 1200, "sum_ms": 4321.0, "max_ms": 812.3,
      "p50_ms": 10, "p95_ms": 100, "p99_ms": 500,
      "bucket": { "1": 300, "5": 700, "10": 150, "50": 40, "100": 8, "500": 1, "1000": 1, "5000": 0, "over": 0 }
    }
  },
  "gauge": { "db.pool.main.active": 3, "db.pool.main.idle": 5 }
}
```

### 何が勝手に入っているか

| 名前 | 何 |
| --- | --- |
| `http.request` | リクエスト数 |
| `http.status.2xx` … `5xx` | ステータスの百の位ごとの数 |
| `http.GET /posts/{id}` | ルートごとのレイテンシの分布 |
| `db.pool.<名前>.{active,idle,total,waiting}` | 接続プールの使用数 |
| `mq.<キュー>.received` / `.completed` / `.error` … | MQ の件数（終わり方ごと） |
| `mq.<キュー>` | MQ のレイテンシの分布 |

### 自分で入れる

```java
Metrics.count("posts.created");
Metrics.record("search.elapsed", elapsedNanos);
Metrics.gauge("cache.size", () -> cache.size());
```

> [!TRAP]
> **`Metrics.gauge(...)` に渡すものが I/O をしてはいけません。**
> `Metrics.snapshot()` のたびに呼ばれるので、DB を触ると
> **DB が詰まっているときに限ってメトリクスも取れなくなります**——いちばん見たいときに見えません。
> MQ の滞留数（`MqQueue#pendingCount()`）を jimble が自動で登録していないのはこのためです。
> 承知のうえで要るなら、自分で登録してください。
>
> ```java
> Metrics.gauge("mq.notice.pending", () -> noticeQueue.pendingCount());
> ```

> [!WARN]
> **利用者の入力を名前にしないでください。**`Metrics.count(request.path())` と書くと、
> `/aaa` `/aab` … と叩かれるだけでヒープが埋まります。名前は **1000 種類**が上限で、
> 超えると1回だけ警告を出して、それ以上は数えません（**そのあとは本物のルートも数えられません**）。
> jimble 自身がレイテンシに生のパスではなく `GET /posts/{id}` を使い、
> どのルートにも当たらなかったものを `(unmatched)` 1つにまとめているのも同じ理由です。

> [!NOTE]
> **分布は固定のバケット**（1 / 5 / 10 / 50 / 100 / 500 / 1000 / 5000ms ＋ あふれ）に数を入れるだけで、
> ひとつひとつの値は覚えません。何件入れてもメモリは増えませんが、
> **パーセンタイルは「入ったバケットの上限」まで**しか言えません（`p95_ms: 100` は「100ms 以下」）。
> あふれに入ったぶんは実測の最大を返します。

> [!NOTE]
> `Metrics.reset()` は**テストのためのもの**です。動いているアプリで呼ぶと、それまで数えたものが消えます。

### 費用と、切りたいとき

**数えること自体は 0 byte / 58ns です**（実測）。確保するメモリは**名前1つにつき**数十〜数百 byte で、
**数えた回数では増えません**。

> [!TRAP]
> **高くつくのは名前のほうです。**
> ```java
> Metrics.count("http.status.%dxx".formatted(code / 100));   // これで 1000 byte 超
> ```
> リクエストごとに `String.format` で組み立てると、**数える処理の全部より重くなります**。
> **名前は先に作って使い回してください**（jimble の中では、ルートの名前は登録時に1度だけ作っています）。

切りたいときは:

```conf
metrics {
	enabled = false
}
```

切ると `count` も `record` も `gauge` も何もしなくなり、`Metrics.snapshot()` は**空のまま**を返します。
**速くはなりません**（もともとほとんど費用がありません）。切る理由があるとすれば、
数えた値をどこにも出していないアプリで**持っている意味が無い**ときです。

## トレース

サービスをまたいだ1本の流れを見る（要件 NF-O-05）。**使うときだけ依存が増えます。**

```kotlin
// build.gradle.kts
implementation("io.jimble:jimble-otel:0.2.1")
```

```java
public static void main (String[] args) {

    JimbleOtel.install("my-app", "http://localhost:4318");

    new JimbleServer(...).start();

}
```

これだけで、次のものに自動で区間が付きます。

| 区間 | 名前 | 種類 |
| --- | --- | --- |
| HTTP リクエスト | `GET /posts/{id}` | `server` |
| SQL 1文 | `SELECT post` | `client` |
| MQ に積む | `mq.put notice` | `producer` |
| MQ を処理する | `mq notice` | `consumer` |
| バッチ1回 | `batch 日次集計` | `internal` |

アプリの中を細かく見たいところは自分で足せます。

```java
try (Span span = Tracing.start("画像の変換", SpanKind.internal)) {
    span.attribute("file", name);
    convert(file);
}
```

> [!NOTE]
> **足さないアプリは1 byte も増えません。**`jimble-core` が持っているのは口（`Tracer` / `Span`）だけで、
> OpenTelemetry の実体は `jimble-otel` にあります。登録しないあいだ、`Tracing.start(...)` の費用は
> **静的な変数を1つ読んで分岐するだけ**（実測で 1 回あたり 0 byte）です。

### サービスをまたぐ

入ってきた `traceparent`（W3C Trace Context）は**自動で引き継ぎます**。
jimble の HTTP クライアントから出るときも**自動で付きます**。

```java
// 相手側のトレースが、こちらの続きとして繋がる
new HttpGetExecutor("https://api.example.com/users").execute();
```

MQ も繋がります。**積んだリクエストと、何分もあとに別のプロセスで動いた処理が1本になります。**
そのために `traceparent` をキューの行に持っています（`data` には入れません）。

> [!WARN]
> **MQ を使っているアプリは、キューのテーブルに列が1つ増えます**（`traceparent varchar(64)`）。
> `MqTables.install(...)` が起動時に当てるので、手で流す必要はありません。

それ以外の経路で外へ渡すときは自分で入れてください。

```java
String traceparent = Tracing.traceparent();   // トレースが無効なら null
```

### ログと突き合わせる

トレースが有効なとき、**すべてのログに `trace_id` と `span_id` が入ります**。
実行 ID（`request_id`）はそのままなので、いままでの集計は壊れません。

```json
{"request_id":"mttvgm93-10676dj-1","trace_id":"4bf92f35...","span_id":"00f067aa...", ...}
```

### 全部は拾わない

流量が多いと送る先が持ちません。割合を渡してください。

```java
JimbleOtel.install("my-app", "http://localhost:4318", 0.1);   // 10 本に1本
```

> [!NOTE]
> **拾うかどうかはトレース単位で決まります。**1本のトレースの途中だけ欠けることはありません。

### 送り先

OTLP の HTTP（`/v1/traces`）へ protobuf で送ります。OpenTelemetry Collector でも、
Jaeger でも、Grafana Tempo でも、OTLP を受けるものなら何でも構いません。

> [!TRAP]
> **`/v1/traces` を付け忘れても動くようにしてあります。**付け忘れると 404 が返るだけで、
> **ログにも出ずに何も届きません**。

> [!NOTE]
> 送信は **JDK の `HttpClient`** です。OpenTelemetry の既定（okhttp）は
> okhttp 851KB ＋ okio 374KB ＋ kotlin-stdlib 1.7MB を連れてくるので、外してあります。
> `jimble-otel` を足したときに増えるのは **0.9MB** です。

### 自分で出す先を書く

`Tracer` は 2 メソッドの interface です。標準出力に出す、社内の仕組みへ送る、
テストで中身を見る、といったときは自分で書けます。テスト用の
`RecordingTracer` は `jimble-core` に入っています。

```java
RecordingTracer tracer = new RecordingTracer();
Tracing.use(tracer);

// ... テストしたい処理 ...

assertEquals("GET /posts/{id}", tracer.spans().get(0).name());
```

## 設定キー

| キー | 既定 | 何をするか |
| --- | --- | --- |
| `server.access_log` | `true` | アクセスログを出す。`false` で1行も出さない |
| `server.bot_access_log` | `true` | ボットのアクセスログを `access.bot` に分ける |
| `log.db` | `false` | `DBLog.save(...)` を有効にする |
| `metrics.enabled` | `true` | メトリクスを数える。`false` で `snapshot()` が空になる |

> [!NOTE]
> **ログのレベル・出力先・書式に設定キーはありません。**それは `logback.xml` の仕事です。

日本語が化けるときは `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` を付けてください
（[本番で動かす](./deploy)）。
