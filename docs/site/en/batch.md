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
