---
name: jimble-mq-worker
description: jimble の MQ を取り出して回す側を書く・設定するときに使う。キューの表の用意と Executor の登録、ワーカーのバッチ（時間で止める・doCancel を呼ばない）と DbScheduler、取り出し役とスレッド数・待ち（mq.thread_count・poll）、running のまま止まった行の戻し（mq.stale・recover_interval）、キューを置くデータソースを絞る・mq.hint を含む。
---

# jimble の MQ を回す

全体の決まりと、ほかの skill への案内は `jimble-batch`。Executor の書き方と積み方は `jimble-mq`。

```java
import io.jimble.batch.AbstractBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
```

## 回すのはバッチ

```java
new MqQueue(NoticeExecutor.QUEUE_NAME).install();   // テーブルを用意する
MqRegistry.add(NoticeExecutor::new);                // 走査はしない
```

取り出して回すのは**バッチ**。`MqWorkerBatch` のようなバッチを1つ作って cron で叩くか、
`DbScheduler` に渡す（`new DbScheduler().start(queue)`。`jimble-batch-run`）。

```java
public class MqWorkerBatch extends AbstractBatch {

	@Override public String batchName () { return "MQ ワーカー"; }

	@Override
	public void execute (BatchArgs args) {

		long deadline = System.currentTimeMillis() + args.cliArgs().getLong("run_seconds", 50) * 1000;

		// 「時間が来た」か「バッチが中断された」で止まる。doCancel() は呼ばない（下の落とし穴）
		CancelOrderNotify stopper = new CancelOrderNotify() {
			@Override public boolean isCancelOrder () {
				return System.currentTimeMillis() >= deadline || MqWorkerBatch.this.isCancelOrder();
			}
			@Override public void doCancel () { MqWorkerBatch.this.doCancel(); }
		};

		new MqQueue(NoticeExecutor.QUEUE_NAME).start(stopper);   // 止まるまで戻らない

	}

}
```

> **時間で終わるワーカーで `doCancel()` を呼ばない。**呼ぶと履歴が `canceled` になり、
> 時間どおりに終わっただけなのに**異常終了に見える**。

**ワーカーのバッチに cron を入れるかは構成しだい。**
スケジューラを常駐させているならスケジューラが直接回すので、
ワーカーのバッチは「スケジューラを置かない構成のときに cron から叩くもの」になる。

キューの表（`mq_scheduler` も）は codegen が自動では外さない。`codegen.exclude_tables` に1つずつ書く（`jimble-db-migration`）。

## 取り出し役とワーカー

**DB から取るのはキューごとに1本の取り出し役で、手の空いたワーカーの数だけまとめて取る**（2.5.5。それまでは1回に1件で、
ワーカーを増やしても件数が伸びなかった）。先読みはしない。

- 並んで処理する数は `mq.thread_count.<種別>`（種別は `MqExecuteType` の名前）。増やすと件数が伸びる
- `mq.poll_min` / `mq.poll_max` は**キューが空のときの待ち**（最初と上限）だけ。全員が処理中のときは、手が空くまで待つ（2.5.5）

## running のまま止まった行

**落ちたプロセスが残した行（`running` のまま `mq.stale` を過ぎたもの）は `recoverStale` が `waiting` に戻す。**
**戻すのもリトライの1回と数え、`maxRetry()` を使い切った行は `dead` にする**（2.2.4。`log_info.reason` に「running のまま止まっていました」）。
処理するたびにプロセスを落とす行（大きすぎる `data` など）が、いつまでも回り続けないため。`dead` の行は管理画面か SQL で見る。
**戻すのは `mq.recover_interval`（既定 1m）ごと**（2.7.8。それまでは起動のときだけ。`0s` で起動のときだけ）。
**`mq.stale` は一番長い処理より長くする。**短いと動いている行が戻されて二重に動く。そのとき古い処理は結果を書かない（取ったときの状態の行だけ書き換える。2.7.8）。

## キューを置くデータソースを絞る（2.7.8）

データソースを並べると、キューの表は**全部のデータソース**に作られ、取り出し役が全部を見に行く。
キューを使わない DB は `db.<名前>.mq = false`、キューごとには `mq.queues.<キュー名>.data_sources = [...]` で外す。
外したデータソースには表を作らず、取り出し・`recoverStale`・`pendingCount` にも行かない。**外したデータソースの DB に `put` すると `MqException`**。
**スケジューラのキュー（`mq_scheduler`）は主だけ**（`MqQueue.mainOnly`）。主の外に積んだ行は取り出されない。
取り出し役は、直前に行を取ったデータソースの次から見る（1つの DB が溜まっても、ほかを待たせない）。
データソースを何十も並べたら **`mq.hint = redis`**（積んだときに印を付け、印のある DB だけ見に行く）。全部を回るのは `mq.sweep_interval`（既定 10s）ごと。
印の無い行（別プロセスが時刻付きで積んだ・Redis が落ちていた）は最悪 `sweep_interval` 遅れる。既定は `off`（毎周すべて）。
**データソースが 1 つ落ちても MQ は止まらない**（2.7.9）。落ちた DB は 1 秒から倍々（最大 30 秒）あけて見直し、ほかの DB を取り出し続ける。
`recoverStale` は落ちた DB を飛ばす（起動もできる）。`pendingCount()` は落ちた DB を数えずに返す（2.7.8 までは例外）。

詳しくは <https://jimble.io/ja/mq.md> / <https://jimble.io/ja/execution.md>。
