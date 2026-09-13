---
title: Batch
summary: Jobs that run on cron. Cancel requests, double starts, the admin screen
section: Runtime
order: 1
---

# Batch

## Writing one

```java snippet=batch-class
```

- `batchName()` is the name that shows up on the admin screen
- Only the ones whose `isScheduler()` returns `true` are picked up by the scheduler
- `cron()` is a five-field cron expression
- `settings()` is the configuration held in the DB (`defaultBatchSettings()` supplies the initial values)

## Registering it

Nothing is scanned, so you register it yourself.

```java
BatchRegistry.add(PostCleanupBatch::new);

// Bring the list in the DB in line with what is registered right now
BatchRegistry.sync(DBUtil.getMainDB());
```

`sync` sets `status = nothing` on the rows for **batches that are gone from the
code** (the row stays, so the history is still reachable).

> [!WARNING]
> **Register everything before you call `sync`.**
> "Nothing is registered" is treated as "everything is gone," so calling it
> before you register will set **every row to `nothing`** (and the scheduler
> then runs nothing at all). An app with no batches should not call it.
>
> **Do not share `batch_master` between apps.**
> Whether a batch is gone is decided by class name, so two apps looking at the
> same table will **set each other's rows to `nothing`.**

## Making it cancellable

```java
while (!isCancelOrder()) {
	// handle one at a time
}
```

Press cancel on the admin screen and `isCancelOrder()` turns `true`.
**In anything long-running, always check it.** A batch that never looks cannot be stopped.
**Always check it in anything long-running.** A batch that never looks cannot be stopped.

From code that does not extend `AbstractBatch` (a domain-layer call, say), the same signal
is reachable through the context.

```java
BatchContext context = Context.current(BatchContext.class);

for (Data row : rows) {
	if (context.isCancelOrdered()) { break; }
	// ...
}
```

**Both read the same order.** The DB is consulted only once per
`batch.cancel_check`, so calling it every iteration is fine.

> [!TRAP]
> **Up to 0.6.0 `BatchContext.isCancelOrdered()` was always false.** The cancel decision
> lived on `AbstractBatch` and nothing was wired to the context. No exception, no warning —
> **you find out when you press cancel in production** (wired up in 1.0).

## Reading a batch of rows at a time

When you don't want to hold a million rows in one transaction, extend `AbstractChunkBatch`.
It repeats **read → process → write in bulk**, committing every `chunkSize()` items.

```java
public class RequestArchiveBatch extends AbstractChunkBatch<Data> {

	@Override public String batchName ()   { return "Archive old requests"; }
	@Override public boolean isScheduler () { return true; }
	@Override public String cron ()        { return "0 4 * * *"; }
	@Override public int chunkSize ()      { return 500; }

	@Override
	protected Iterator<Data> reader (BatchArgs args, DB db) {

		// read a little at a time, in key order
		return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
				SELECT id, amount FROM request
				WHERE status = ? AND id > ?
				ORDER BY id ASC
				LIMIT ?
			""", "approved", lastKey, limit));

	}

	@Override
	protected Data process (Data item) {

		// return null to leave an item out (the default just passes it through)
		return item.getLong("amount") == 0 ? null : item;

	}

	@Override
	protected void write (List<Data> items, DB db) {

		for (Data item : items) {
			db.update("UPDATE request SET status = 'archived' WHERE id = ?", item.getLong("id"));
		}

	}

}
```

You register it, and see it in the admin screen, exactly like any other batch.

- **One call to `write()` is one transaction.** It commits when the method returns
- **Cancellation is checked at each chunk boundary.** You don't call `isCancelOrder()` yourself
- `execute()` is `final`. The only places you write code are `reader()`, `process()` and `write()`

### When it fails partway

**That chunk is rolled back and the whole batch fails.** It never quietly moves on.

How far it got is recorded in `batch_history.execute_info`.

| Key | What it holds |
|---|---|
| `chunk_read` | rows read |
| `chunk_filtered` | rows left out because `process()` returned `null` |
| `chunk_written` | rows committed |
| `chunk_committed` | chunks committed |
| `chunk_failed_at` | which chunk it failed on |
| `chunk_canceled` | whether it stopped on a cancel |

`chunk_written` is rewritten **every `batch.progress` (5 by default)**,
so the admin screen shows how far along a running batch is.

### The reading DB and the writing DB are different

The `db` handed to `reader()` and the `db` handed to `write()` are **different instances**.
**Use the one you are given.**

Closing a `DBTransaction` returns the connection to the pool, so if you read through the same
`DB`, **your open read dies the moment the first chunk commits.**

> [!TIP]
> **Prefer `KeyPagingReader` over a cursor (`selectListWithFetcher`).**
> A cursor holds one connection for as long as it stays open.
> On a batch that runs for hours, that matters.
> Paging holds no connection between pages.

The key you give `KeyPagingReader` must be **unique, in step with the `ORDER BY`, and not
rewritten while the batch runs.** If a full page comes back without the key advancing, it throws —
better that than spinning forever in silence.

> [!WARNING]
> **`db.insert()` and friends do not throw when they fail.**
> `db.isError()` only holds the result of **the statement just before it**.
> If `write()` runs several statements, check after each one or throw yourself.

## Double starts

How many copies of the same batch may run at once is set by `allowConcurrentExecutionCount()`.
The default is one. If the previous execution has not finished, the next one is skipped.

Even across several servers, a table in the DB does the locking, so only one runs.

## Running one on its own

**You write the batch entry point yourself.** The whole startup order is right there in that one file.

```java
public class BlogBatch {

	public static void main (String[] args) {

		DBUtil.load(Conf.conf().config(), BlogBatch.class);   // 1. DB
		BatchTables.install(DBUtil.getMainDB());              // 2. the batch tables

		BatchRegistry.add(PostCleanupBatch::new);             // 3. register
		BatchRegistry.sync(DBUtil.getMainDB());

		BatchResult result = BatchExecutor.start(args);       // 4. run

		DBUtil.stop();
		System.exit(result.isExecuted() ? 0 : 1);

	}

}
```

```bash
java -cp app.jar blog.BlogBatch env=local class=blog.batch.PostCleanupBatch days=30
```

Anything you pass as `key=value` lands in `args.cliArgs`.

To run on cron, stand up a separate resident process with `DbScheduler` as its entry point.

## The admin screen

Add `jimble-batch-manager` and you get a web view that lists your batches and lets you run them, cancel them, and read their history.

```kotlin
implementation("io.jimble:jimble-batch-manager:<version>")
```

```java
install(BatchManagerController::new);
```

**You have to add authentication yourself.** Put it in a `before`; that is all it takes.
With nothing there, anyone can start your batches.
