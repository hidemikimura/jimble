---
title: Execution model
summary: One virtual thread per request. What runs on which thread
section: Basics
order: 10
---

# Execution model

**One request, one virtual thread.** On top of it, everything runs **synchronously**
from start to finish.

- There is no thread pool to configure (Helidon handles it)
- No API returns a `CompletableFuture`
- **Blocking is fine.** You call JDBC directly

## The path of one request

```
onRequest
  ↓
before (outermost → innermost)
  ↓
the route's work / the Executors, in order
  ↓
send
  ↓
(on exception) error (innermost → outermost)
  ↓
after (innermost → outermost) — always runs
  ↓
onComplete — always runs
```

`onRequest` is called **even when no route matched**.
`after` and `onComplete` sit in a `finally`, so they run even when an exception is thrown.

### "Once it is sent, it stops" is decided in one place

Return the login page from a `before` and **the remaining `before` hooks, the
route and the Executors do not run.** That decision is closed up in one place (`Stage`).

**`after` and `onComplete` still run.** Those two sit in a `finally`, so they are
reached whether or not the response has been sent and whether or not something
threw — exactly as the diagram above says. "Once it is sent, it stops" applies
only to the stages up to sending.

> [!NOTE]
> The code this was ported from (jooby_base) had the same check copied into
> seven places. Collecting it into one is what stops **the check being forgotten
> in exactly the spot someone adds next** (requirement F-C-13).

### Executor

Instead of writing the work inline, a route can **push Executors onto a queue**.

```java
post("/save", () -> new SaveExecutor());
```

- You can add to the queue while it is running (`context.addExecutor(...)`)
- Calling `cancel()` **does not stop anything on the spot**. Once `execute` returns, the rest is thrown away and `onCancel` is called
- `OPTIONS` is for preflight only, so **the queue is not run** (requirement F-W-19)

## What runs on which thread

| Mechanism | Thread |
| --- | --- |
| HTTP request | Helidon's **virtual thread** (one per request) |
| `AsyncData` / `AsyncList` loading and prefetch | **The calling thread itself.** Despite the name, no separate thread is used |
| SSE | **Holds on to that request's virtual thread** |
| WebSocket | A Helidon listener thread (one per connection) |
| MQ workers | Virtual threads. `mq.thread_count.<type>` of them per type |
| The scheduler's clock | One platform thread (`jimble-scheduler`) |
| Batches the scheduler starts | Virtual threads. `scheduler.execute_threads` of them (`0` for unlimited) |
| Batch heartbeat | One virtual thread (`jimble-batch-heartbeat`) |

Where you want to cap the number, use `VirtualThreadManager`.

> [!NOTE]
> The `n` in `VirtualThreadManager(n)` is how many **run at once**.
> The semaphore is acquired after the virtual thread has been started, so the
> waiting threads do exist. Virtual threads are cheap, so this does no real harm —
> but it is not "only n threads are created".

## What runs out is not threads, it is the connection pool

Virtual threads grow without limit. **What runs out first is DB connections.**

> [!TRAP]
> Inside a transaction, or partway through `selectListWithFetcher`, the connection
> has not gone back to the pool.
> Hold something open in that state for a long time — SSE, say — and **you eat one
> pool slot per person.**
> With `maximum_pool_size = 10`, ten people exhaust it, and the eleventh gets neither
> "slow" nor "an error" but **a wait for a connection that just sits there.**

This is why you do not write an infinite loop in an SSE handler.
Send a finite amount, close, and let the client reconnect ([SSE](./sse)).

## Things to watch with virtual threads

| | |
| --- | --- |
| **Do not use `synchronized`** | It pins the virtual thread to its carrier. Use `ReentrantLock` (jimble itself does) |
| **Guard shared mutable state** | A `static` `HashMap` breaks under concurrent access. Virtual threads leave far less room for it to happen to work, so it **actually breaks** |
| **Do not lean on `ThreadLocal`** | jimble passes the request around with `ScopedValue` (`Context.current()`) |

## Shutting down

You hand your shutdown to one place (`Shutdown`).

```java
Shutdown.add("my worker", worker::stop);
```

| | |
| --- | --- |
| Order | **Last registered, first stopped** (the reverse of start-up order). Close the DB first and whatever is still shutting down cannot use it |
| On failure | **The rest are still stopped.** You get back the names of the ones that could not be |
| What jimble registers | The DB pool / the server / the scheduler |

> [!NOTE]
> **On SIGTERM, everything registered is stopped in reverse order**
> ([Server configuration](./server)).
> A container sends SIGTERM and waits, so a process that dies without catching it
> cuts off requests mid-flight.
>
> For an ordinary run you do not need any of this. **When the process ends, everything stops.**
> Where you do need it is <b>when the app is swapped out without the process ending</b> —
> that is, `jimbleRun`. Anything you forgot to stop keeps running alongside its replacement,
> and shows up as "the same job ran twice" ([Hot reload](./hot-reload)).

Cleanup for a single request is not `Shutdown` but `context.onClose(...)`.
It is called in reverse registration order, and one failure still folds up the rest.
