---
title: Transactions
summary: Wrap it in try-with-resources. A missed close is caught at the end of the execution
section: Database
order: 3
---

# Transactions

There are no annotations. **What you wrapped is the transaction.**

```java snippet=transaction-mq
```

Wrap the `Tx` that `db.begin()` returns in try-with-resources, and call `tx.commit()` at the end.
**Leave without calling `commit()` and it rolls back** — whether by `return` or by an exception.
In the example above, when the article does not go in it leaves with `return -1`, so nothing is left behind.

## Three ways to write it

```java
db.transaction(tx -> {
	db.insert(...);
	db.update(...);
});                                   // commits if the body returns normally; rolls back and rethrows otherwise

long id = db.transactionResult(tx -> db.insertKey(...));   // returns a value

try (Tx tx = db.begin()) {            // when you want to commit yourself
	db.update(...);
	tx.commit();                      // commits and ends; leaving without it rolls back
}
```

**`db.transaction(...)` is usually the shortest.**
Use `db.begin()` when you want to leave partway (like the `return -1` above) or decide the commit yourself.

`transaction` and `transactionResult` have different names because an expression lambda such as
`tx -> db.update(...)` reads both as "returns a value" and "returns nothing", so with a single name
the call could not be told apart.

## `Tx` methods

| Method | What it does |
| --- | --- |
| `commit()` | Commits and **ends** |
| `checkpoint()` | Commits what has happened so far, and **carries on** |
| `rollback()` | Rolls back and ends |
| `close()` | Rolls back if not yet ended (try-with-resources calls it) |

Failures are `TransactionException` (unchecked; a subclass of `SqlExecuteException`). `getCode()` gives `DB_004` and so on.

**`commit()` and `rollback()` are one-shot.** Calling one again after the transaction has ended throws `IllegalStateException`.
To commit and carry on, use `checkpoint()`. Anything written after `checkpoint()` rolls back unless you `commit()`.

You may call `tx.commit()` / `tx.rollback()` yourself inside `db.transaction(...)`. It then does nothing more on the way out.

> [!NOTE]
> 1.x's `DBTransaction` and the DB's `beginTransaction()` / `commit()` / `commitEndTransaction()` and friends were removed in 2.0.
> `DBTransaction.commit()` committed without ending, so whatever you wrote after it was silently rolled back by `close()`.
> In 2.0, commit-and-end is `commit()` and commit-and-carry-on is `checkpoint()` ([Moving to 2.0](./migrate-2)).

## When SQL fails inside

**A SQL failure is an exception** ([Using the DB](./db)). Leave it uncaught and it leaves the Tx, and everything rolls back.
Nothing goes in halfway.

**Catch it and carry on, and that Tx can no longer commit.**

```java
db.transaction(tx -> {
	db.insert(...);                  // fine
	try {
		db.update(...);              // hit a unique constraint
	} catch (DuplicateKeyException e) {
		// caught; carry on
	}
	db.insert(...);                  // fine
});                                  // <- TransactionException (DB_004) here. Everything rolls back
```

If any SQL has failed since the transaction started, `commit()` rolls everything back and throws
`TransactionException` (`DB_004`). **One successful statement after a failure does not hide it.**
`checkpoint()` refuses the same way.

### Branching on a failure and writing the other path

**Roll that Tx back and write again in a new Tx.**

```java
try {
	db.transaction(tx -> {
		insert(...);
		update(...);                 // may hit a unique constraint
	});                              // left by an exception, so it has rolled back here
} catch (DuplicateKeyException e) {
	db.transaction(tx -> insertFallback(...));   // the other path, in a new Tx
}
```

If there is an outer transaction too, the inner Tx has joined it (see "Nesting" below).
In that case, redo the outer one as a whole.

> [!TRAP]
> **Whether statements after a failure go through depends on the product.**
>
> | | After a failed statement |
> | --- | --- |
> | PostgreSQL | **Refused** until `ROLLBACK` (`current transaction is aborted, ...`) |
> | MySQL | **They run.** One failed statement does not abort the transaction |
>
> **Lean on neither.** What jimble promises is only this: **the commit is refused and not one
> row survives.** Once you have caught a failure, end that Tx instead of writing more.

## When the body throws a checked exception

The body of `db.transaction(...)` / `transactionResult(...)` may throw checked exceptions (no `throws` needed).
**Unchecked exceptions are rethrown as they are; checked ones are wrapped in `TransactionException` (`DB_006`)**, after rolling back.
The original exception is at `getCause().getCause()`.

## Nesting (joining)

Calling `db.begin()` (or `db.transaction(...)`) on a DB that already has a transaction open
**does not start a new one; it joins the outer one.** `tx.isJoined()` tells you whether it joined.

| What the inner one does | What happens |
| --- | --- |
| `commit()` / `checkpoint()` | Nothing. The outer one commits |
| `rollback()` | **Marks the outer one rollback-only** |
| Closed without committing (left by an exception) | **Marks the outer one rollback-only** |

A rollback-only outer `commit()` rolls everything back and throws `TransactionException` (`DB_005`).
**Catching the inner exception in the outer one and carrying on does not let the outer one commit.**
Nor can you commit the outer one partway (the inner `checkpoint()` does nothing).

## Locking with a DB row (`DBLock`)

Where there is no Redis, `DBLock` lets you run "one at a time per key".

```java
DBLock.create(db, "daily");            // create the key's row (does nothing if it exists). Once, beforehand

db.transaction(tx -> {
	DBLock.lock(db, "daily");          // SELECT ... FOR UPDATE. Only one until the transaction ends
	...
});
```

**Call `DBLock.lock` inside a transaction.** Outside one it throws `IllegalStateException`
(a `FOR UPDATE` lock is released at the end of the statement, so taking it outside protects nothing).

- A missing key (no `create`) also throws `IllegalStateException`
- `create` / `lock` return nothing (`void`). A SQL failure throws `SqlExecuteException`
- The lock is released **at the end of the transaction**. There is no way to release it explicitly

> [!NOTE]
> In 1.x, `DBLock.lock` returned a `boolean`, and returned `true` even outside a transaction.
> It carried on protecting nothing, so 2.0 made it an exception.

Locks backed by Redis are in [Cache and locks](./cache).

## What leaves the process, and what goes into the DB

**The two go on opposite sides of the commit.** This one is easy to get backwards.

| | Where to call it | Why |
| --- | --- | --- |
| **Push onto the DB-backed queue** ([MQ](./mq)'s `put()`) | **Inside the transaction** | It is the same DB, so a rollback removes what you pushed |
| **Anything that leaves the process** (SSE, mail, an external API) | **After the commit** | It cannot be taken back, so publishing first is unrecoverable |

```java
db.transaction(tx -> {

	long id = db.insertKey(...);

	new NoticeExecutor().put(db, data);      // inside — it is a DB queue

});

PostFeedHandler.notifyNewPost(title);        // outside — only after the commit
```

Publish the outgoing one first and a rollback leaves you delivering **"a notice about an
article that is not there".**

Push the DB queue after the commit instead and **whatever was in flight when the process
died is silently gone** — the article is there, and nobody is told about it.

> [!TIP]
> **The most reliable place for outgoing work is inside the queue.** `put()` in the
> transaction and do the actual sending in [MQ](./mq)'s `execute()`. Then **you never have
> to think about which side of the commit you are on.**

## Forgetting to close

Write `Tx tx = db.begin()` without try-with-resources and `return` partway through without
`commit()` or `rollback()`, and nothing is rolled back and the connection never goes back to the pool.

jimble **catches it at the end of the execution (`Context`)**.

```
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックして閉じます: blog_example
```

Rolling back silently would leave you with "I thought I saved it and it is not there",
so it logs an ERROR first and then rolls back.
If you see this line, you forgot to wrap something.
(It is watched on the `DB` side, because the `DB` is what holds the connection.)

## An unclosed `DB`

**An ordinary statement does not leak when you forget to close the `DB`.** The connection
goes back to the pool as soon as the statement finishes, which is what makes the throwaway
`DBUtil.getMainDB()` style fine.

**There are only two ways to leave with the connection held**, and both are caught at the
end of the execution.

| What holds it | When it comes back |
| --- | --- |
| An open transaction | `tx.commit()` / `tx.rollback()` / `tx.close()` |
| A cursor (`selectListWithFetcher`) | `db.close()` |

```
ERROR 閉じられていない DB が残っていました。閉じます: blog_example（selectListWithFetcher はカーソルなので、close() までコネクションを返しません）
```

> [!TRAP]
> **This is a last line of defence.** By the time it fires an ERROR has been logged, so
> treat the line as something to fix rather than something to rely on. In a long-running
> batch the connection stays held until the execution ends.
