---
name: jimble-batch
description: jimble でバッチか MQ を書くときに最初に使う。全体の決まり（注釈なし・走査なし・自分で登録する・失敗は例外）と、バッチを書く（jimble-batch-write）・入口とスケジューラ（jimble-batch-run）・MQ を書いて積む（jimble-mq）・MQ を回す（jimble-mq-worker）のどの skill を読むかの案内。
---

# jimble のバッチと MQ

**注釈は無い。**`@Scheduled` も `@EnableBatchProcessing` も `@KafkaListener` も存在しない。
Spring Batch の `Job` / `Step` / `ItemReader` / `ItemWriter` でも、Quartz の `JobDetail` / `Trigger` でもない。

## 全体の決まり

- **走査はしない。自分で登録する。**バッチは `BatchRegistry.add(X::new)`、MQ の Executor は `MqRegistry.add(X::new)`
- **起動の順番が入口のファイルに全部出る**（原則1）。Web・バッチ・スケジューラの**3つの入口が同じ `Bootstrap.load()` を呼ぶ**
- **失敗は例外で抜ける**（SQL の失敗も `SqlExecuteException`）。戻り値で失敗を返さない
- **MQ は DB のテーブルがキュー。**Kafka も RabbitMQ も要らない。**トランザクションを共有できる**のがこの作りの理由
- **MQ を回すのもバッチ**（ワーカーのバッチか `DbScheduler`）
- **1.x のコードを直すなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す
  （`isError()` や `DBTransaction`、`if (!DBUtil.load(...))` はコンパイルエラーになる）
- 自前の排他（`DBLock`）は `jimble-db-tx` の skill を見る（トランザクションの中で呼ぶ決まりがある）。DB の書き方そのものは `jimble-db` の skill

## どの skill を読むか

**書く前に、該当する skill を読む。**落とし穴（実際に踏んだもの）は、それぞれの skill に分けて書いてある。

| やること | skill |
| --- | --- |
| バッチを書く（`AbstractBatch`・設定・引数）・登録の順番（`sync()`）・中断・チャンク処理（`AbstractChunkBatch`・`KeyPagingReader`） | `jimble-batch-write` |
| 入口の `main`・終了コード・環境（`-Djimble.env`）・cron の常駐スケジューラ（`DbScheduler`）・バッチのテーブル（`BatchTables`）・二重起動・バッチ管理画面 | `jimble-batch-run` |
| MQ の Executor を書く・返し方・トランザクションの中で積む・時刻指定・メール送信・同じメッセージが2回来る | `jimble-mq` |
| MQ を回す（ワーカーのバッチ・`DbScheduler`）・取り出し役とスレッド数・`running` の戻し・キューを置くデータソースを絞る・`mq.hint` | `jimble-mq-worker` |

## 詳しいことは引く

**推測で書かない。**

| | |
| --- | --- |
| バッチ・チャンク・管理画面 | <https://jimble.io/ja/batch.md> |
| MQ | <https://jimble.io/ja/mq.md> |
| メール | <https://jimble.io/ja/mail.md> |
| トランザクション | <https://jimble.io/ja/transaction.md> |
| SQL ビルダー | <https://jimble.io/ja/sql.md> |
| 実行の流れとスレッド | <https://jimble.io/ja/execution.md> |
| 設定（`batch.*`） | <https://jimble.io/ja/config.md> |
| デプロイ（常駐プロセスの立て方） | <https://jimble.io/ja/deploy.md> |
