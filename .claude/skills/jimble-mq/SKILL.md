---
name: jimble-mq
description: jimble の MQ（DB のテーブルがキュー）で、Executor を書く・メッセージを積むときに使う。返し方（completed・error・dead）、トランザクションの中で積むこと、時刻を指定して積む、MQ からのメール送信（Mailer）、同じメッセージが2回来ること（冪等に書く）を含む。
---

# jimble の MQ を書いて積む

全体の決まりと、ほかの skill への案内は `jimble-batch`。取り出して回す側は `jimble-mq-worker`。

**Kafka も RabbitMQ も要らない。**別のミドルウェアを立てない。
**トランザクションを共有できる**のがこの作りの理由。

```java
import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;   // status パッケージ（io.jimble.mq ではない）
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;

import java.time.Duration;
import java.time.Instant;
```

## Executor を書く

```java
public class NoticeExecutor extends MqExecutor {

	public static final String QUEUE_NAME = "mq_blog";

	@Override public String queueName ()          { return QUEUE_NAME; }     // テーブル名
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

| 返し方 | どうなるか |
| --- | --- |
| `completed` | 行を**消す** |
| `error`・例外で抜ける（`null` / `running` を返したときも `error` 扱い） | `maxRetry()`（既定 3）まで間をあけてやり直し、使い切ったら `dead` |
| `dead` | **やり直さずに**その場であきらめる |

## 積むのはトランザクションの中

```java
db.transaction(tx -> {

	long id = db.insertKey(SQL.insert(Post.instance()) ... );

	new NoticeExecutor().put(db, data);       // ← 中で積む（積めなければ MqException で巻き戻る）

});                                          // 例外なく終われば、記事とキューを一緒に確定
```

**ここが要点。**同じ DB なので、ロールバックすれば積んだものも消える——
**「メールだけ飛んで注文が入っていない」が起きない。**

**外へ出すもの（メール・外部 API・SSE）はコミットしたあと。**
DB に積むのは中、外へ出すのは外——**コミットの逆側**である。

いちばん確実なのは、**中で `put()` だけして、送信は `execute()` の中でやる**こと。
そうすれば「どちら側か」を考えなくてよくなる（上のコード例がその形）。

時間を指定するなら `put(db, data, Instant.now().plus(Duration.ofMinutes(10)))`（`Instant` 版を使う。`Date` 版は残してあるだけ）。
積む時刻も拾う時刻も**この JVM の時刻**で比べる（2.5.2。それまでは拾うときに DB の `NOW()` と比べていて、
JVM と DB の時間帯が違うと時差のぶん遅れて拾われていた）。`scheduled_at` を SQL で直接書くなら JVM の時刻で入れる。

**キューを置かないデータソースの DB に `put` すると `MqException`**（`jimble-mq-worker` の「データソースを絞る」）。

## メールは MQ の execute() から送る（2.4.0）

```java
try {
	Mailer.send(new MailMessage().to(data.getString("to")).subject("...").text("..."));   // io.jimble.util.mail
	return MqStatus.completed;
} catch (MailException ex) {
	if (ex.isTransient()) throw ex;   // SMTP の 4xx・繋がらない → maxRetry() までやり直す
	return MqStatus.dead;             // 5xx（宛先が無い・認証が通らない）→ やり直しても同じ
}
```

- 設定は `mail.smtp.*`（`host` が要る。書かずに送ると例外）。手元は `application.local.conf` に `mail.transport = "log"`、テストは `Mailer.use(new MemoryTransport())`
- 件名・名前・ヘッダの改行、山括弧のあるアドレスは作った時点で例外（ヘッダの差し込みを止める）。HTML を送るときも `text(...)` を入れる

## 同じメッセージは2回来る

**「ちょうど1回」は作れない。jimble はそのふりをしない。**
処理の途中でプロセスが落ちれば、同じものがもう一度実行される
（落ちたプロセスが残した `running` の行を戻す仕組みは `jimble-mq-worker`）。

**何度やっても同じになるように書く。**

- メールなら、送信済みの記録を見てから送る
- 集計なら、**足すのではなく入れ直す**
- 外部 API なら、冪等キーを付ける

詳しくは <https://jimble.io/ja/mq.md> / <https://jimble.io/ja/mail.md> / <https://jimble.io/ja/transaction.md>。
