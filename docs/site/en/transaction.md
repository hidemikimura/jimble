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
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックします
```

Rolling back silently would leave you with "I thought I saved it and it is not there",
so it logs an ERROR first and then rolls back.
If you see this line, you forgot to wrap something.

## The short form

```java
DBTransaction.transaction(db, transaction -> {
	db.insert(...);
	db.update(...);
});
```

It starts one, runs the work you passed in, and sees it through `commitEndTransaction()`.
If an exception is thrown, `close()` rolls back.

## Mixing in MQ

You push onto the queue **after the commit**.

```java
transaction.commitEndTransaction();

PostFeedHandler.notifyNewPost(title);
```

Publish first and a rollback leaves you delivering "a notice about an article that is not there".
The other way round: pushing onto a DB-backed queue ([MQ](./mq)) belongs **inside the transaction**.
It is the same DB, so a rollback removes what you pushed along with everything else.
