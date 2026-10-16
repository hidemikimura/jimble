---
title: MQ
summary: The DB is the queue. Write for a message that will run twice
section: Runtime
order: 2
---

# MQ

jimble's MQ **makes a DB table the queue**.
No separate middleware. The big thing you get is that it shares your transaction.

## Writing one

```java
public class NoticeExecutor extends MqExecutor {

	@Override
	public String queueName () { return "mq_blog"; }

	@Override
	public String key () { return "notice"; }

	@Override
	public MqExecuteType executeType () { return MqExecuteType.short_time; }

	@Override
	public MqStatus execute (DB db, Data row) {

		Data data = row.getDataOptional("data");

		// ... do the work

		return MqStatus.completed;

	}

}
```

- `queueName()` is the table name. One table per queue
- `key()` is the kind. You can mix several kinds in one table
- `executeType()` is `short_time` or `long_time`. It changes how messages are pulled off
- Return anything other than `MqStatus.completed` and it runs again, up to `maxRetry()`

## Enqueueing

```java snippet=transaction-mq
```

Call `put()` **inside the transaction**.
It is the same DB, so rolling back throws away what you queued along with everything else.
"The mail went out but the order was never created" cannot happen.

You can also queue for a later time.

```java
new NoticeExecutor().put(db, data, scheduledAt);
```

## It will run twice

**The same message can be executed twice.**
The process dying partway through the work is the usual reason.

So **write it so that running it again lands in the same place**.

- For mail, look at the sent record before you send
- For a total, write the value in rather than adding to it
- For an external API, attach an idempotency key

"Exactly once" is not something a distributed system can give you. jimble does not pretend it can.

## Registering it and running it

```java
// create the table
new MqQueue(NoticeExecutor.QUEUE_NAME).install();

// register the Executor (no classpath scanning)
MqRegistry.add(NoticeExecutor::new);
```

Pulling messages off and running them is a batch's job. Write one batch — something like
`MqWorkerBatch` — register it the same way as any other [batch](./batch), and run it on cron.

**Only one poller per queue reads the DB.** When workers are free, it takes **as many messages as there are free workers** with `SELECT ... FOR UPDATE SKIP LOCKED`
and hands them straight to those workers (nothing is read ahead; up to 2.5.4 it took one message at a time).
However many servers run the queue, each message is taken by exactly one worker (it can still be processed twice in the cases described above).
`mq.thread_count.<type>` is **how many messages are processed in parallel**; raising it does not add connections
borrowed while the queue is idle (up to 2.1.2 every worker polled the DB itself, so a slow DB filled one
connection per worker at once).
