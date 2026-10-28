---
name: jimble-batch-run
description: jimble のバッチを動かす側を書くときに使う。入口の main（起動の順番・終了コード）、環境（-Djimble.env・-Djimble.conf）、cron の常駐スケジューラ（DbScheduler・複数台・二重起動）、バッチのテーブル（BatchTables.install）、jooby_base から移した表、バッチ管理画面と、それぞれの落とし穴を含む。
---

# jimble のバッチを動かす

全体の決まりと、ほかの skill への案内は `jimble-batch`。バッチのクラスの書き方は `jimble-batch-write`。

```java
import io.jimble.batch.BatchExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchResult;
import io.jimble.batch.BatchTables;
import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.mq.MqRegistry;
```

## 入口は自分で書く

**起動の順番がそのファイルに全部出る**のが決まり（原則1）。

```java
public class BlogBatch {

	public static void main (String[] args) {

		Bootstrap.load();                                 // 1. DB とテーブル（アプリ側のクラス）

		MqRegistry.add(NoticeExecutor::new);              // 2. MQ の Executor

		BatchRegistry.add(PostCleanupBatch::new);         // 3. バッチの登録
		BatchRegistry.add(MqWorkerBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

		BatchResult result = BatchExecutor.start(args);   // 4. 実行

		DBUtil.stop();
		System.exit(result == BatchResult.completed ? 0 : 1);   // isExecuted() ではない

	}

}
```

> **終了コードを `result.isExecuted()` で決めない。**`isExecuted()` は「本体が走ったか」で、
> 例外で終わった `error` も中断された `canceled` も `true` になる。これで決めると**失敗しても 0 で終わり**、
> cron や監視が失敗に気づかない。成功を表すのは `BatchResult.completed` だけ。

```bash
java -Djimble.env=prod -cp app.jar blog.BlogBatch class=blog.batch.PostCleanupBatch days=30
```

- **環境は `-Djimble.env` で決める。引数の `env=` では変わらない**（設定はその前に読み終わっている）。
  `env=` を渡して、いま動いている環境と違えば `IllegalArgumentException` で止まる
- 環境は変えずに、読む設定ファイルだけ替えるなら `-Djimble.conf=application.batch`
  （クラスパスの `application.batch.conf` だけを読む。見つからなければ起動時に落ちる。2.5.3）

## バッチのテーブル

**バッチのテーブル（`batch_master` / `batch_history` / `batch_execute_info`）はマイグレーションでは作らない。**
`BatchTables.install(db)` が作る（無ければ作り、版が古ければ上げる）。**`DBUtil.load(...)` のあと、
`BatchRegistry.sync(...)` より前**に呼ぶ。サンプルは `Bootstrap.load()` の中で呼んでいる
（バッチ管理画面を載せる Web でも要るため）。`Bootstrap` に入れないなら、バッチとスケジューラの入口で
`Bootstrap.load()` のすぐあとに呼ぶ。

```java
Bootstrap.load();                                  // DBUtil.load まで
BatchTables.install(DBUtil.getMainDB());          // バッチのテーブル
BatchRegistry.add(PostCleanupBatch::new);
BatchRegistry.sync(DBUtil.getMainDB());
```

呼び忘れると **`sync()` が `IllegalStateException` を投げて起動が止まる**（メッセージに `BatchTables.install` が出る）。

`DbScheduler` の依頼を積む MQ の表（**`mq_scheduler`**）と、アプリの MQ の表は、codegen が自動では外さない。
`codegen.exclude_tables` に完全一致で1つずつ並べる（ワイルドカード不可。`jimble-db-migration` の skill）。

**jooby_base から移した `batch_master` や MQ の表を使うなら 2.5.9 以上にする。**2.5.8 では列が足されず、
起動は通るのに **cron のバッチがどれも走らない**（cron の回ごとに「この回を走らせてよいか確かめられませんでした」とエラーログ）。
MQ の表に `traceparent` が無いと**積むときに失敗する**。2.5.9 は起動時に足りない列を足す。

## cron で回す（常駐スケジューラ）

cron で回すなら、`DbScheduler` を入口にした**常駐プロセスを別に立てる**。

```java
new DbScheduler().start(noticeQueue);      // アプリの MQ も一緒に回る
```

Web・バッチ・スケジューラの**3つの入口が同じ `Bootstrap.load()` を呼ぶ**形にする。

**二重起動は `allowConcurrentExecutionCount()`（既定1本。0 以下は上限なし）。**
サーバーが複数台でも DB のテーブルで排他するので、**cron の1回で走るのは同時実行数まで**
（2.5.8。既定の 1 なら1台だけ、0 なら台数分）。2.5.7 までは、すぐ終わるバッチが同じ回に台数分走ることがあった。
`batch_master` に足す列（`last_fired_at` / `fire_count`）は `BatchTables.install` が起動時に足す。

## バッチ管理画面

- `jimble-batch-manager` を入れて `install(BatchManagerController::new)`、`batch_manager.enabled = true` と `batch_manager.username` / `password` を書く
  （既定は無効。ユーザー名かパスワードが空だと組み込まず、エラーログだけ出る。Basic 認証は組み込み）
- **書き換える API は `Content-Type: application/json` でしか受けない**（2.2.3。CSRF 対策。フォームは 415）。
  スクリプトから叩くなら JSON で送る。Basic 認証の失敗は `Lockout` で数え、超えると 429

詳しくは <https://jimble.io/ja/batch.md> / <https://jimble.io/ja/deploy.md> / <https://jimble.io/ja/config.md>（`batch.*`）。
