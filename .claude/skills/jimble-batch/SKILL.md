---
name: jimble-batch
description: jimble でバッチと MQ を書くときに使う。バッチの定義と登録の順序、中断、チャンク処理、二重起動、入口の main、DB のテーブルをキューにする MQ の書き方と積み方を含む。
---

# jimble のバッチと MQ

**注釈は無い。**`@Scheduled` も `@EnableBatchProcessing` も `@KafkaListener` も存在しない。
Spring Batch の `Job` / `Step` / `ItemReader` / `ItemWriter` でも、Quartz の `JobDetail` / `Trigger` でもない。

```java
import io.jimble.batch.AbstractBatch;
import io.jimble.batch.AbstractChunkBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchExecutor;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.BatchResult;
import io.jimble.batch.BatchTables;
import io.jimble.batch.scheduler.DbScheduler;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
import io.jimble.mq.MqStatus;
```

## バッチを書く

```java
public class PostCleanupBatch extends AbstractBatch {

	@Override public String batchName ()    { return "記事の掃除"; }
	@Override public boolean isScheduler () { return true; }
	@Override public String cron ()         { return "15 3 * * *"; }   // 5フィールド

	@Override
	public void execute (BatchArgs args) {

		int days = settings().getInt("days");        // DB に入れた設定

		while (!isCancelOrder()) {                   // 長い処理では必ず見る
			// 1件ずつ処理する
		}

	}

}
```

- `isScheduler()` が `true` のものだけスケジューラが拾う
- `settings()` は DB の設定（初期値は `defaultBatchSettings()`）
- `args.cliArgs` に `key=value` で渡したものが入る

## 登録する。順番が効く

走査はしない。自分で登録する。

```java
BatchRegistry.add(PostCleanupBatch::new);
BatchRegistry.add(MqWorkerBatch::new);
BatchRegistry.sync(DBUtil.getMainDB());        // ← 登録が全部済んでから
```

> **`sync()` を登録より先に呼ぶと、全部の行が `status = nothing` になる。**
> 「1つも登録されていない」を「全部消えた」として扱うので、
> **スケジューラが何も回さなくなる。**例外も警告も出ない。
> **バッチを持たないアプリは `sync()` を呼ばない。**

> **`batch_master` を別のアプリと共有しない。**
> 消えた判定はクラス名でやるので、同じテーブルを見るアプリが2つあると
> **お互いの行を `nothing` にし合う。**

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
		System.exit(result.isExecuted() ? 0 : 1);

	}

}
```

```bash
java -cp app.jar blog.BlogBatch env=local class=blog.batch.PostCleanupBatch days=30
```

cron で回すなら、`DbScheduler` を入口にした**常駐プロセスを別に立てる**。

```java
new DbScheduler().start(noticeQueue);      // アプリの MQ も一緒に回る
```

Web・バッチ・スケジューラの**3つの入口が同じ `Bootstrap.load()` を呼ぶ**形にする。

## 中断できるようにする

```java
while (!isCancelOrder()) { ... }
```

管理画面から中断を押すと `true` になる。
**見ていないバッチは止められない。**

## 大量に処理する（チャンク）

100万件を1トランザクションで抱えたくないときは `AbstractChunkBatch<T>`。
**読む → 加工する → まとめて書く**を `chunkSize()` 件ごとに確定する。

```java
public class RequestArchiveBatch extends AbstractChunkBatch<Data> {

	@Override public String batchName () { return "古い申請の書庫入れ"; }
	@Override public int chunkSize ()    { return 500; }

	@Override
	protected Iterator<Data> reader (BatchArgs args, DB db) {

		return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
			SELECT id, amount FROM request WHERE status = ? AND id > ? ORDER BY id ASC LIMIT ?
			""", "approved", lastKey, limit));

	}

	@Override
	protected Data process (Data item) {
		return item.getLong("amount") == 0 ? null : item;   // null なら書かない
	}

	@Override
	protected void write (List<Data> items, DB db) {
		for (Data item : items) {
			db.update("UPDATE request SET status = 'archived' WHERE id = ?", item.getLong("id"));
		}
	}

}
```

- **1回の `write()` が1トランザクション**
- **中断はかたまりの切れ目で見る。**`isCancelOrder()` を自分で呼ぶ必要はない
- `execute()` は `final`。触るのは `reader()` / `process()` / `write()` の3つだけ
- 途中で落ちたら**そのかたまりだけ戻して、バッチ全体を失敗にする**（黙って次へ進まない）

> **`reader()` の `db` と `write()` の `db` は別のインスタンス。渡されたものを使う。**
> 同じ `DB` で読んでいると、**最初のかたまりを確定した時点で読みかけが死ぬ**
> （トランザクションを閉じるとコネクションがプールへ返るため）。

> **カーソル（`selectListWithFetcher`）ではなく `KeyPagingReader` を使う。**
> カーソルは開いているあいだコネクションを1本押さえ続ける。数時間のバッチでは効いてくる。
> キーは**一意で、`ORDER BY` と揃っていて、走っているあいだ書き換わらない**列にする。

## MQ — DB のテーブルがキュー

**Kafka も RabbitMQ も要らない。**別のミドルウェアを立てない。
**トランザクションを共有できる**のがこの作りの理由。

```java
public class NoticeExecutor extends MqExecutor {

	@Override public String queueName ()          { return "mq_blog"; }      // テーブル名
	@Override public String key ()                { return "notice"; }       // 種別
	@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

	@Override
	public MqStatus execute (DB db, Data row) {

		Data data = row.getDataOptional("data");

		// ... 処理する

		return MqStatus.completed;

	}

}
```

`completed` 以外を返すと `maxRetry()` まで再実行される。

### 積むのはトランザクションの中

```java
try (DBTransaction transaction = new DBTransaction(db)) {

	transaction.beginTransaction();

	long id = db.insert(SQL.insert(Post.instance()) ... );

	new NoticeExecutor().put(db, data);       // ← 中で積む

	transaction.commitEndTransaction();

}
```

**ここが要点。**同じ DB なので、ロールバックすれば積んだものも消える——
**「メールだけ飛んで注文が入っていない」が起きない。**

**外へ出すもの（メール・外部 API・SSE）はコミットしたあと。**
DB に積むのは中、外へ出すのは外——**コミットの逆側**である。

いちばん確実なのは、**中で `put()` だけして、送信は `execute()` の中でやる**こと。
そうすれば「どちら側か」を考えなくてよくなる（上のコード例がその形）。

時間を指定するなら `put(db, data, scheduledAt)`。

### 同じメッセージは2回来る

**「ちょうど1回」は作れない。jimble はそのふりをしない。**
処理の途中でプロセスが落ちれば、同じものがもう一度実行される。

**何度やっても同じになるように書く。**

- メールなら、送信済みの記録を見てから送る
- 集計なら、**足すのではなく入れ直す**
- 外部 API なら、冪等キーを付ける

### 回すのはバッチ

```java
new MqQueue(NoticeExecutor.QUEUE_NAME).install();   // テーブルを用意する
MqRegistry.add(NoticeExecutor::new);                // 走査はしない
```

取り出して回すのは**バッチ**。`MqWorkerBatch` のようなバッチを1つ作って cron で叩くか、
`DbScheduler` に渡す（`new DbScheduler().start(queue)`）。

**ワーカーのバッチに cron を入れるかは構成しだい。**
スケジューラを常駐させているならスケジューラが直接回すので、
ワーカーのバッチは「スケジューラを置かない構成のときに cron から叩くもの」になる。

## 落とし穴（実際に踏んだもの）

- **`BatchRegistry.sync()` は登録が全部済んでから。**先に呼ぶと全部 `nothing` になる
- **`db.insert()` などは失敗しても例外を投げない。**`db.isError()` に入るのは
  **その直前の1文**の結果だけ。`write()` で複数文を流すなら1文ごとに見るか、自分で投げる
- **`isCancelOrder()` を見ていないバッチは止められない**（チャンクは自動）
- **`reader()` と `write()` の `DB` を混ぜない**
- **時間で終わるワーカーで `doCancel()` を呼ばない。**呼ぶと履歴が `canceled` になり、
  時間どおりに終わっただけなのに**異常終了に見える**
- **二重起動は `allowConcurrentExecutionCount()`（既定1本）。**
  サーバーが複数台でも DB のテーブルで排他するので1本しか走らない

## 詳しいことは引く

**推測で書かない。**

| | |
| --- | --- |
| バッチ・チャンク・管理画面 | <https://jimble.io/ja/batch.md> |
| MQ | <https://jimble.io/ja/mq.md> |
| トランザクション | <https://jimble.io/ja/transaction.md> |
| SQL ビルダー | <https://jimble.io/ja/sql.md> |
| 実行の流れとスレッド | <https://jimble.io/ja/execution.md> |
| 設定（`batch.*`） | <https://jimble.io/ja/config.md> |
| デプロイ（常駐プロセスの立て方） | <https://jimble.io/ja/deploy.md> |

DB の書き方そのものは `jimble-db` の skill に詳しい。
