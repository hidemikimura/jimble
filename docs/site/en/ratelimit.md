---
title: Rate limiting
summary: Declared on the route. Token bucket, memory / Redis / DB
section: Basics
order: 9
---

# Rate limiting

**You declare it on the route.** It applies where you wrote it, and nowhere else.

```java
// a single route
post("/api/login", handler)
	.attribute(RateLimit.KEY, RateLimit.perIp(5, Duration.ofMinutes(1)));

// the whole block
path("/api", () -> {
	rateLimit(RateLimit.perIp(60, Duration.ofMinutes(1)));

	get("/items", handler);
	get("/items/{id}", handler);
});
```

Like `before`, **it attaches to the block you wrote it in** (not to a node in the path).
Write another one further in and the inner one wins; if the route carries one as an
attribute, that wins.

## How it counts

It is a **token bucket**. It refills up to `limit` over `duration`.
**A short burst gets through; hammer it and you are stopped.**

> [!NOTE]
> We did not use a fixed window ("N per minute") because at the window boundary
> **2N requests get through in an instant**.

## What it counts by

```java
RateLimit.perIp(60, Duration.ofMinutes(1));                       // per source address
RateLimit.of(context -> context.session().id(), 10, Duration.ofMinutes(1));   // per login
```

> [!TRAP]
> **`perIp` looks at `server.trust_proxy`.** Sit behind a proxy with it left at
> `false` and **everyone is counted as one client — the load balancer's IP**
> ([Server configuration](./server)).

Things you do not want counted, such as access from inside the office, can be excluded.

```java
RateLimit.perIp(60, Duration.ofMinutes(1))
	.exclude(context -> context.request().proxyAddress().startsWith("10."));
```

## What comes back when it stops you

| | |
| --- | --- |
| Status | **429** |
| `Retry-After` | Seconds to wait (rounded up, minimum 1) |
| `X-RateLimit-Limit` / `X-RateLimit-Remaining` | The limit and what is left |

**It stops the request before `before` runs.** So it never reaches your authentication
or your DB.

## Where the counters live

```conf
rate_limit {
	store   = "memory"   # memory | redis | db
	enabled = true
}
```

| | Shared across machines | Fits |
| --- | --- | --- |
| `memory` (the default) | **No** | A single machine |
| `redis` | Yes | Several machines side by side |
| `db` | Yes | No Redis available, and **the counts are low** |

> [!WARN]
> **Leave it on `memory` and run several machines, and the total that gets through
> is multiplied by the machine count.** Three machines, three times the limit.
> If you are running more than one, use `redis`.

> [!NOTE]
> `redis` counts in Lua, because "what is left" and "when it was last topped up"
> **have to be read and written together or the count slips**.
> `db` uses `SELECT ... FOR UPDATE`, so **it is not for high frequency**
> (it is for things like counting login attempts). The table is created the first
> time it is used.

> [!TRAP]
> **When it cannot count, it lets the request through.** Redis being down is not
> a good reason for the whole site to return 429.
> It does not **let it through silently**, though — `流量制限を数えられませんでした（通します）`
> goes to the log.
