---
title: Lazy loading and prefetch
summary: Fetched at the moment it is referenced. When you walk the whole tree, roll it into one IN clause
section: Database
order: 4
---

# Lazy loading and prefetch

A list often has something you want to be able to return in detail, but frequently do not.
`AsyncData` / `AsyncList` **issue the SQL only at the moment the value is referenced**.

```java snippet=async-load
```

```java
Data post = ...;
post.put("comments", new CommentList(post.getLong(Post.id)));

// no SQL up to here
post.getJsonString();   // ← here it fires
```

## What you implement

| Method | What it does |
| --- | --- |
| `load()` | Fetches one record's worth |
| `setData(Data)` | Applies what was fetched to yourself |
| `setRelationData(...)` | Once, after every `setData` has finished. Leave it out if you do not need it |
| `hashKey()` | The key that decides whether two of these are the same thing |

`AsyncData` behaves as a `Data` and `AsyncList` as a `List`, so once loaded they are
ordinary data.

**Touching one loads it — reading or writing.** `get` and `size` of course, but also
`put` / `remove` / `add` / `clear` / `computeIfAbsent` / `putFirst`.

> [!NOTE]
> **Up to 0.6.x the write side did not trigger a load.** Calling `remove("id")` on an
> unloaded node removed a key from an *empty* map, and the value came back the moment
> someone read it. An `add` before the load was **overwritten and lost**. Nothing
> crashed and nothing was logged, so the first sign was someone reporting a wrong result.

## What you can look at without triggering a load

```java
list.isLoaded()       // already loaded?
list.isLoadFailed()   // did it fail?
list.loadedValues()   // only the values already held
list.loadedSize()     // only the count already held
```

`toString()` does not trigger a load either. **One log line must not fire SQL down
every branch of the tree**, and it does not.

`hashCode()` / `equals()` look at nothing but `hashKey()`. **Comparing two of them
fires no SQL.**

## When a load fails

No exception is thrown. It logs and moves on.

**A failure still counts as "loaded".** Otherwise every reference would keep re-issuing
a query that is going to fail. You can tell it failed from `isLoadFailed()`.

## When it becomes N+1

Hang one off every row of a list, turn the whole thing into JSON, and **1 + N** queries fly.

```
fetch 20 posts             1 query
  comments for post 1      1 query
  comments for post 2      1 query
  ...                      ×20
```

**Prefetch** is what turns this into two.

## Prefetch

Write what `load()` does, with an `IN` clause.

```java snippet=async-load-batch
```

Along with it, declare the unit you group by.

```java
@Override
public String batchKey () { return "CommentList"; }

@Override
public Object batchId () { return postId; }
```

**Unloaded branches that share a `batchKey` are rolled into one `loadBatch` call.**
`ids` arrives holding the `batchId` of the other branches too.

**Keep `load()` and `loadBatch()` side by side in the same file.**
Fix one and not the other and the results diverge. That is the reason they are not
kept apart.

## Running it

```java snippet=prefetch-route
```

`AsyncPrefetch.run(...)` walks the tree, collects the unloaded branches that have a
`batchKey`, and fills them. **The walk itself fires no SQL** (it does not look inside
an unloaded branch).

You can also have it run automatically, right before the response is sent.

```conf
async {
	prefetch {
		on_response = true
		max_depth   = 5
	}
}
```

**The default is `false`**, so that no hidden I/O is created and so that turning
it on does not change behaviour.

## Prefetch or no prefetch, the result is the same

The only thing that changes is the number of queries.

- A `batchId` that `loadBatch` did not return is treated **exactly as if fetching it
  individually had returned no rows**
- If `loadBatch` throws, **not one member of that group is marked loaded**.
  Each is then loaded individually with `load()`, so the result is unchanged
  (only the speed goes back)
- A class with no `loadBatch` is loaded individually, as before

## How it terminates

Filling branches grows new ones, so it repeats until no more grow.
Three things stop it from spinning forever.

| | |
| --- | --- |
| A `(batchKey, batchId)` is expanded once | A holding B and B holding A stops on the second pass |
| A cap on passes | `async.prefetch.max_depth` (default 5) |
| The same object is never walked twice | Decided by reference identity |

## Choosing table nesting on or off, per JSON

SELECT results are **nested under the table name**.

```json
{"post": {"id": 1, "title": "hello"}}
```

Whether `setData` flattens that (`flattenTable`) or keeps it as-is (`extractTableData`)
used to be **fixed the moment you wrote the class**.
One `AsyncData` could emit one shape, and only one.

Want the admin API nested and the shop API flat, and you end up **writing two classes
with the same content**. Fix one and you will not notice the other.

**The shape is chosen when you produce the JSON.**

```java
data.getJsonString(TableNest.ON);    // {"post": {"id": 1}, "comments": [...]}
data.getJsonString(TableNest.OFF);   // {"id": 1, "comments": [...]}
```

You can set it per response, too.

```java
path("/admin", () -> {
	before(context -> context.response().tableNest(TableNest.ON));
	get("/posts/{id}", context -> context.response().json("post", new PostData(id)));
});

path("/shop", () -> {
	before(context -> context.response().tableNest(TableNest.OFF));
	get("/posts/{id}", context -> context.response().json("post", new PostData(id)));
});
```

**Both are returning the same class.**

### How does it know the shape?

`AsyncData` / `AsyncList` **hold on to the raw data** that `load()` returned.
That raw data knows which key belongs to which column of which table.

```
{"post": {"id": 1, "title": "..."}}
  ↑ table name   ↑ column name
```

So whether you wrote `setData` with `flattenTable` or with `extractTableData`,
**it can be reassembled into either shape**. There is nothing to add on the
implementation side.

### Anything that is not a column does not move

Children added in `setRelationData` (`AsyncData` / `AsyncList`) and computed values
are not columns of the raw data, so **they always stay at the top level**.

```json
{"post": {"id": 1, "title": "..."}, "comments": [...]}
```

> [!note]
> **The default is `TableNest.AS_IS`.** Say nothing and the shape `setData` built
> comes out unchanged. The output of your existing app does not move by a single byte.

> [!trap]
> **Some things cannot be reassembled.**
> A result that is not table-nested — free-form SQL, an aggregate such as `{"total": 12}` —
> carries no table name, so **it comes out as it is.**
> The same goes for a node handed `putData(null)`.
>
> When you JOIN and **the same column name exists in two tables** (`post.id` and
> `comment.id`), one has already overwritten the other by the time it is held flat,
> and there is no telling which value it is.
> On the way back to nested, it lands in **whichever table came first**.
