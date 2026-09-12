---
title: Cache and locks
summary: Swapping between DB / memory / Redis, automatic invalidation of SQL results, and distributed locks
section: Database
order: 5
---

# Cache and locks

## Pick one of three implementations

```conf
cache {
	type = "db"        # db | memory | redis
}
```

| | Where it lives | Crosses processes | Expiry |
| --- | --- | --- | --- |
| `db` (**default**) | The `db_cache` table | **yes** | **none** |
| `memory` | JVM memory | no | `cache.memory.expire` (seconds) |
| `redis` | Redis | **yes** | **none** |

Write a value it does not know and it does not fail.
**It warns and falls back to `db`.**
What you are actually running on shows up in the startup log (`cache=db`).

> [!WARN]
> **`db` and `redis` have no expiry.** There is no mechanism to sweep old entries
> either.
> Keep putting things in and `db_cache` keeps growing.
> **Removing them is the job of whoever put them there** (`remove` / `removeGroup`).

## Reading and writing

```java
ICache cache = Cache.instance(db);

cache.set("top:posts", json, "application/json");
String json = cache.getString("top:posts");

cache.remove("top:posts");
```

| Method | What comes back |
| --- | --- |
| `getString(key)` | A string, or `null` when there is nothing (`""` on the memory implementation only) |
| `get(key)` | A `CacheData` (with creation time and kind). **Check `isError()`** |
| `set(key, value, contentType[, group])` | `true` when it went in |
| `has(key, group)` / `remove(key)` / `removeGroup(group)` | |

> [!NOTE]
> **Values over 256KiB are spilled to a file** (gzipped, `cache.temp_dir`).
> `CacheData#hasContentFile()` becomes true, and you can hand it straight to
> `context.response().cache(data)` to return it as `Content-Encoding: gzip`.
> There is no mechanism to delete older generations of those files, so keep an
> eye on that directory.

## Dropping a whole group at once

Use this when you want "edit one article and throw away every cache entry that
touches it".

```java snippet=cache-group
```

jimble uses this itself. The scheduler puts each machine's liveness information
in the same group and **reads every machine's worth back at once** with
`getStringGroup(group)`.

## Caching SQL results

`ICache` is the kind where whoever put it in takes it out.
**The SQL result cache watches your updates and evicts itself.**

**It is off by default.** Write one line in the applications that use it.

```conf
sql_cache.enabled = true
```

```java
Data customer = db.selectCached(
	SQL.select()
		.from(Customer.instance())
		.inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
		.where(Customer.id.eq(1)));
```

```java
// Write the update exactly as before. Only the related cache entries are evicted
db.update(SQL.update(Customer.instance()).set(Customer.name, "name1").where(Customer.id.eq(1)));
```

| The update | The cache entry above |
| --- | --- |
| `UPDATE customer SET name = 'name1' WHERE id = 1` | **evicted** |
| `UPDATE customer SET name = 'name2' WHERE id = 2` | not evicted |
| `UPDATE shop SET name = 'shop1' WHERE id = 1` | **evicted** (the joined table) |
| `UPDATE shop SET name = 'shop2' WHERE id = 2` | not evicted |

**Reading is explicit, evicting is automatic.**
The other way round, a forgotten eviction quietly turns into stale data.

### How it decides

Each cache entry carries **dependency tags**.

| Tag | What it means | What evicts it |
| --- | --- | --- |
| `customer#id#1` | It depends on that row | Writing that row |
| `customer#*` | It depends on a set of rows (a listing) | INSERT / DELETE / an UPDATE that cannot be narrowed |
| `customer#@` | It reads that table | An update where **which rows it hits cannot be read** |

The tags are built from two sources.

- **The rows the SELECT returned**, from which the **primary key and unique key**
  values are picked up per table. The result nests under the table name
  ([Data nesting](./db)), so **the keys of joined tables come along too**
- **The WHERE of the UPDATE / DELETE**, from which equality and `IN` on primary
  and unique keys are read. If it can be read, only that row tag is evicted; if
  not, the whole table goes

Unique keys are picked up from `SHOW INDEX` by codegen and generated into the
code. Run `codegen` again.

### Caching a listing

A SELECT that has not pinned down a single row also gets `customer#*`.

```java
// A listing narrowed by shop_id. Add a customer and the result changes
db.selectListCached(SQL.select().from(Customer.instance()).where(Customer.shop_id.eq(1)));
```

These are the cases that count as "pinned down".

- The WHERE constrains every column of a primary or unique key **by value**
  (`WHERE id = 1`, `WHERE id IN (1, 2)`)
- The join condition constrains every column of a key against **columns of a
  table that is already pinned down**
  (with `ON (customer.shop_id = shop.id)`, if customer is pinned down then shop
  resolves to one row as well)

> [!trap]
> **A SELECT with a `LIMIT` never counts as "pinned down".**
> With `ORDER BY name LIMIT 1`, changing the name of some other row swaps out the
> row you get back.
> `IN (subquery)` and `= a column of another table` are treated the same way.

> [!note]
> **If you do not SELECT the key columns, it falls back to the table tag.**
> Narrow the columns like `SELECT id, name` and it would miss an update narrowed
> by the unique key `code`, so it errs on the safe side (any update to that table
> evicts everything). With `SELECT *` this does not happen.

### Transactions

**Inside a transaction it neither reads nor writes.**
That is so nothing uncommitted ends up in the cache.
Evictions are held and applied **together at commit**. On rollback nothing is
evicted.

### Where it does not work

| | What happens |
| --- | --- |
| Raw SQL updates (`db.execute("UPDATE ...")`) | Where they land cannot be read, so **everything is evicted** |
| `ON DUPLICATE KEY UPDATE` / `INSERT ... SELECT` | Existing rows may change, so everything touching that table is evicted |
| The DB written directly by another process | Cannot be followed. `sql_cache.ttl` (300 seconds by default) is your safety net |
| `sql_cache.store = "memory"` | **That machine only.** With more than one machine, use `redis` or `db` |

### Configuration

```conf
sql_cache {
	enabled = true       # false by default
	store   = "memory"   # memory | redis | db
	ttl     = 300        # seconds. 0 means no expiry
	max     = 10000      # memory only. The cap on how many entries to hold
}
```

**The default is `false` so that applications that do not use it do not pay for
it.** When it is on, then even if you never call `selectCached` once, every
`insert` / `update` / `delete` builds up "which rows does this hit" (reading the
WHERE and looking up the table's keys).
While it is `false`, **not one line of that runs**.

It is built so you notice when you forgot to turn it on.

- The configuration log at startup says `sql_cache=off`
- Call `selectCached` while it is off and you get a warning **the first time
  only** (not every time)

```
jimble 構成: env=local / session=none / cache=db / sql_cache=off / redis=なし / ...
```

> [!note]
> With it off, `selectCached` **behaves as a plain `selectList`**.
> It does not raise. Turning the cache off to investigate something in production
> and having the whole application stop would be going too far.

## Loading cache

The form where "make it if it is not there" is written in one place.

```java
LoadingCache<List<Data>> cache = Cache.loadingCache(
	"top:posts", db, Duration.ofMinutes(5), loaderDb -> loaderDb.selectList(...));

List<Data> posts = cache.get();
```

| | |
| --- | --- |
| Where the value lives | **In that JVM's memory** (only a "made it" marker goes in the shared cache) |
| Expiry | **None** unless you pass a `Duration` |
| When another machine calls `clear()` | It checks the shared cache **once a minute** and throws its copy away |
| The `DB` handed to the loader | A new connection (separate from the caller's transaction) |

> [!TRAP]
> **A loader that returns `null` still counts as loaded.**
> It keeps returning `null` until the expiry comes round, so if you do not want
> "it happened to be missing" memorized, **express it with a value** — an empty
> list, for instance.

## Distributed locks

**These use Redis.** They are for "I want exactly one of several machines to do
this".

```java snippet=redis-lock
```

```java
// Wait 100ms, give up if it is not free. Hold it for 30 seconds once taken
try (RedisLockResult lock = RedisLock.tryLock("batch:daily", 100, 30000)) {

	if (lock.status() != RedisLockStatus.Success) {
		return;   // someone else is running it
	}

	// only one machine gets in here

} catch (IOException ignore) {
}
```

> [!WARN]
> **Failing to take the lock does not throw.** You must check `status()`.
> If you do not, you get "the work proceeds without holding the lock".

> [!TRAP]
> **The same thread can take the same key again** (the lock is reentrant).
> Check "surely someone else already holds this" from the same thread and you
> pass straight through.

> [!TRAP]
> **`tryLock`'s hold time is not extended.** Write 30 seconds and let the work
> take 40, and **it is released partway through and a second machine walks in.**
> The no-argument `RedisLock.lock(key)` keeps extending for as long as you hold it
> (at the cost of waiting indefinitely).

When Redis is not configured, it **raises rather than silently succeeding**
(requirement F-U-10).
That is so you never create "it went ahead even though there was no lock".

### Locking with the DB alone

Where there is no Redis you can use `DBLock`. **Inside a transaction**, create
the row first, then take it.

```java
DBLock.create(db, "daily");           // once only
...
if (DBLock.lock(db, "daily")) {       // SELECT ... FOR UPDATE
	// one at a time until the transaction ends
}
```

If the row is not there you get `false`. It is released **at the end of the
transaction**; there is no way to release it explicitly.

## Configuration keys

```conf
cache {
	type          = "db"     # db | memory | redis
	temp_dir      = ""       # where large values are spilled. Empty means the temp directory
	memory.expire = 0        # memory only. Seconds. 0 means no expiry
}

redis {
	host = ""                # empty means no Redis
	port = 6379
	ssl  = false
}
```

> [!NOTE]
> If `redis.host` is empty, **there is no Redis**. The application still starts.
> With `cache.type = redis` set you get a warning at startup, and it fails the
> moment you use it.
