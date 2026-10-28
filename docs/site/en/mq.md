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

### Rows left running

If a process dies partway through, the row stays `running`.
**`running` rows older than `mq.stale` (10 minutes by default) are put back to `waiting` every
`mq.recover_interval` (1 minute by default)** (since 2.7.8; before that only when the queue started).
Putting a row back counts as one retry, and a row that has used up `maxRetry()` becomes `dead`. Several
servers doing this at once do not count twice.

> [!WARN]
> **Make `mq.stale` longer than your longest job.**
> A job that runs longer than `mq.stale` is put back while it is still running, and another worker runs it
> again (it runs twice). When that happens, the old job that finishes later **does not write its result**
> (only a row still in the state it was claimed in is updated; since 2.7.8). If the log says the row "was
> claimed again while it was being processed", raise `mq.stale`.

```conf
mq {
	stale            = 10m   # running rows older than this are treated as left behind by a dead process
	recover_interval = 1m    # how often to put them back; 0s means only when the queue starts
}
```

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

## Choosing the data sources a queue lives on

With several data sources, a queue's table is created on **every data source**, and the poller visits them
all in turn. A database that does not use queues (a shared one, say) can be left out (since 2.7.8).

```conf
db {
	main   { main = true, ..., mq = false }   # no queues on this data source
	shop_a { url = "..." }
	shop_b { url = "..." }
}

mq.queues {
	mq_notice { data_sources = ["shop_a", "shop_b"] }   # where this queue lives (unset means every data source with mq = true)
}
```

- A data source that is left out gets **no table, and is not visited for claiming, for recovering stale
  rows (`recoverStale`), or for counting (`pendingCount`)**
- The poller orders the data sources by name and **starts right after the one it last claimed from**
  (since 2.7.8). One data source with a long backlog does not hold back the others (they are visited
  within "number of data sources" claims)
- With both settings, the queue lives only on data sources that satisfy both. An unknown name in
  `data_sources` stops startup
- **`put` on a database of a data source that is left out throws `MqException`** (nobody would process it)
- Keep the queue table in the same database as the business tables, so enqueueing and the business update
  roll back together

> [!NOTE]
> **The scheduler's queue (`scheduler.queue_name`, `mq_scheduler` by default) lives on the main data source
> only** (since 2.7.8). The scheduler's own tables (`batch_master` and the rest) and the admin screen's
> enqueueing are on the main data source, so the queue is created there and claimed from there regardless of
> `db.<name>.mq`. Rows put on other data sources are no longer claimed. To keep a queue of your own on the
> main data source only, use `MqQueue.mainOnly("name")` and return true from `mainOnly()` in the
> `MqExecutor` that puts to it.

## Visiting only the databases that have work

With dozens of data sources, the poller visits **even the empty ones on every round**. Whether the
system is idle or only one database has work, SELECTs grow with the number of data sources.
Set `mq.hint` and **putting a message leaves a "this data source has work" hint**; the poller then visits
only the data sources with a hint (since 2.7.8).

```conf
mq {
	hint           = redis   # off (default: visit everything every round) | memory | redis
	sweep_interval = 10s     # how often every data source is visited (rows without a hint are picked up within this)
}
```

| `mq.hint` | Where hints live | When it fits |
|---|---|---|
| `off` (default) | Nowhere; every round visits everything (as before) | Few data sources |
| `memory` | Inside the same process only | Putting and claiming happen in the same process |
| `redis` | Inside the process and in a Redis set | Put from the web, claim in a batch process (different processes) |

With hints, the poller visits only:

- data sources with a hint (kept for 5 seconds after the hint is seen, so a putting transaction that has
  not committed yet is not missed)
- data sources that last returned as many rows as were wanted (there may be more)
- every data source, once per `sweep_interval`

**A hint only makes the poller look sooner; nothing is lost without it.** If Redis is down, neither putting
nor claiming fails (a log line is written) and the rows are picked up by the full round.

> [!WARN]
> **A row without a hint can wait up to `sweep_interval`.** Rows get no hint when another process put them
> with a time (`scheduled_at`), when another process put them under `memory`, or while Redis was down.
> Retries are not delayed: the poller's own process keeps a hint for the retry time.

Claiming SELECTs measured on a local MySQL with one queue, two workers, `poll_max = 1s` and
`sweep_interval = 10s`:

| Data sources | Idle (`off` → `redis`) | To the other databases while one gets 10 messages a second (`off` → `redis`) |
|---|---|---|
| 10 | 20/s → 2/s | 837/s → 1.8/s |
| 30 | 54/s → 6/s | 1,701/s → 5.8/s |
