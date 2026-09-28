---
title: Principles
summary: No annotations, no DI, no startup scanning — and why
section: Getting started
order: 3
---

# Principles

jimble holds to a handful of rules.
Whether a feature gets added is usually decided right here.

## 1. You can follow it top to bottom

The highest priority is that a reader can **trace with a finger** from `main`
to the code they are looking for.

What we gave up to get that:

- **Routes declared with annotations** (`@GET @Path("/x")`).
  Which class gets read never appears in the code
- **A DI container.**
  Which implementation `@Inject` lands on is unknowable until you run it
- **Transactions declared with annotations** (`@Transactional`).
  Where one begins and ends lives outside the method

Instead you write `new`. You write `install(AdminController::new)`.
You write `db.transaction(tx -> { ... })`. A few more lines.

## 2. No classpath scanning at startup

Controllers, batch jobs, MCP tools — **you register them yourself**.

```java snippet=mcp-controller
```

Because nothing is scanned, startup is fast, the startup log tells the whole story
of what got registered, and nothing fights with native images or jlink.
Forget to register something and the route list in the startup log shows you.

## 3. Never fail silently

The most expensive bug is the one that does not crash.
jimble spends its effort on killing the "quietly stopped working" states.

For example:

- If `env=local` but `cookie.secure = true`, jimble **logs a WARN** at startup.
  Left alone, the browser never sends the cookie back, and session, CSRF, and flash
  all stop working with no error
- If an execution ends with a transaction still open, jimble **logs an ERROR and rolls back**.
  Silently returning the connection to the pool leaves you with
  "I thought I inserted that"
- The code snippets in these docs are extracted from the real code.
  Delete a marked region and **the site build fails**

## 4. Failures are exceptions

**A failure is an exception (unchecked).** jimble never reports a failure through a return value.
Return values get thrown away, and a failure that was thrown away goes through in silence.

- Zero rows is not a failure. `select` returns an empty `Optional`, `selectList` an empty list, `update` 0
- A DB failure is a `SqlExecuteException`. About the only one you want to branch on is a unique violation, so catch just that as `DuplicateKeyException`
- If you write nothing, it flies to the top and becomes a 500. Inside a transaction, it rolls back

```java snippet=db-error
```

The same goes for using an API the wrong way. **Things break only as a compile error or an exception**; nothing
quietly does something else (what 2.0 rebuilt is listed in [Moving to 2.0](./migrate-2)).

## 5. One execution = one Context

A web request, a batch execution, one MQ message, one WebSocket message —
each gets exactly one `Context`.
The DB connection and the session hang off it, and it is folded up at the end.

`Context` is passed through a `ScopedValue`, not a `ThreadLocal`.
On virtual threads `ScopedValue` is cheaper, and because it
**cannot be rewritten**, nothing can swap it out from under you mid-execution.
