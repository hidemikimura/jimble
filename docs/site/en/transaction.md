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

## Which one to call

| Method | What it does |
| --- | --- |
| `beginTransaction()` | Starts one. Does nothing if one is already open |
| `commit()` | Commits. **The transaction continues** |
| `commitEndTransaction()` | Commits and ends |
| `rollback()` | Rolls back. The transaction continues |
| `rollbackEndTransaction()` | Rolls back and ends |
| `close()` | Rolls back if still open |

Watch the difference between `commit()` and `commitEndTransaction()`.
`commit()` means "settle what has happened so far, and carry on".
In the code this was ported from, `commit()` ended the transaction internally, which
left a hole: **everything after it silently became auto-commit.** jimble fixes that.

## Forgetting to close

Call `beginTransaction()` without try-with-resources and `return` partway through, and
nothing is rolled back and the connection never goes back to the pool.

jimble **catches it at the end of the execution (`Context`)**.

```
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックして閉じます: blog_example
```

Rolling back silently would leave you with "I thought I saved it and it is not there",
so it logs an ERROR first and then rolls back.
If you see this line, you forgot to wrap something.

**This is not limited to `DBTransaction`.** Calling `db.beginTransaction()` directly is
caught the same way — the `DB` is what holds the connection, so that is where it is watched.

## An unclosed `DB`

**An ordinary statement does not leak when you forget to close the `DB`.** The connection
goes back to the pool as soon as the statement finishes, which is what makes the throwaway
`DBUtil.getMainDB()` style fine.

**There are only two ways to leave with the connection held**, and both are caught at the
end of the execution.

| What holds it | When it comes back |
| --- | --- |
| An open transaction | `commitEndTransaction()` / `rollbackEndTransaction()` / `close()` |
| A cursor (`selectListWithFetcher`) | `db.close()` |

```
ERROR 閉じられていない DB が残っていました。閉じます: blog_example（selectListWithFetcher はカーソルなので、close() までコネクションを返しません）
```

> [!TRAP]
> **This is a last line of defence.** By the time it fires an ERROR has been logged, so
> treat the line as something to fix rather than something to rely on. In a long-running
> batch the connection stays held until the execution ends.

## The short form

```java
DBTransaction.transaction(db, transaction -> {
	db.insert(...);
	db.update(...);
});
```

It starts one, runs the work you passed in, and sees it through `commitEndTransaction()`.
If an exception is thrown, `close()` rolls back.

## What leaves the process, and what goes into the DB

**The two go on opposite sides of the commit.** This one is easy to get backwards.

| | Where to call it | Why |
| --- | --- | --- |
| **Push onto the DB-backed queue** ([MQ](./mq)'s `put()`) | **Inside the transaction** | It is the same DB, so a rollback removes what you pushed |
| **Anything that leaves the process** (SSE, mail, an external API) | **After the commit** | It cannot be taken back, so publishing first is unrecoverable |

```java
try (DBTransaction transaction = new DBTransaction(db)) {

	transaction.beginTransaction();

	long id = db.insert(...);

	new NoticeExecutor().put(db, data);      // inside — it is a DB queue

	transaction.commitEndTransaction();

}

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
