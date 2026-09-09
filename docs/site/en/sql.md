---
title: SQL DSL
summary: SQL.select / insert / update / delete
section: Database
order: 2
---

# SQL DSL

You can write SQL as a string, but the DSL gives you types.

## Selecting

```java
List<Data> posts = db.selectList(
	SQL.select()
		.from(Post.instance())
		.where(Post.published.eq(true))
		.orderBy(Post.created_at.desc())
		.limit(20)
);
```

There are `limit` and `offset`. Pass nothing to `select()` and you get every column.
Pass columns to pick them.

```java
SQL.select(Post.id, Post.title).from(Post.instance())
```

## Conditions

```java
.where(Post.id.eq(1L))
.where(Post.title.like("%jimble%"))
.where(Post.created_at.ge(from).and(Post.created_at.lt(to)))
.where(Post.id.in(List.of(1L, 2L, 3L)))
```

Call `where` more than once and the conditions are joined with AND.

## Joins

```java
SQL.select()
	.from(Post.instance())
	.left(Comment.instance()).on(Comment.post_id.eq(Post.id))
	.where(Post.id.eq(id))
```

Inner joins are `inner(...)`. An `on()` attaches to the join right before it.

The result nests under the table name, so you get it with `row.getData("comment")`.

## Inserting, updating, deleting

```java
long id = db.insert(
	SQL.insert(Post.instance())
		.value(Post.title, title)
		.value(Post.created_at, new Date())
);

int updated = db.update(
	SQL.update(Post.instance())
		.set(Post.published, true)
		.where(Post.id.eq(id))
);

int deleted = db.delete(
	SQL.delete(Post.instance()).where(Post.id.eq(id))
);
```

`insert` returns the generated key. When you do not need the ID,
`insertNoReturnKey` is faster.

Updating only the fields that were sent looks like this.

```java snippet=rest-patch
```

## Using functions

The functions live on `Dsl`. **Every one of them works on both MySQL and
PostgreSQL.**

```java
SQL.select(Dsl.count(), Dsl.max(Post.created_at)).from(Post.instance())
```

Things like `CASE WHEN` that **can be either a value or a column** cannot be
passed to `select` directly. Wrap them in a `SelectQuery`.

```java
SQL.select(new SelectQuery().dsl(Dsl.caseWhen()...)).from(...)
```

Pass one unwrapped and it is treated as **a bound value**, not a column.

### The list

| Family | Functions |
| --- | --- |
| String | `lower` / `upper` / `trim` / `ltrim` / `rtrim` / `length` / `byteLength` / `substring` / `replace` / `left` / `right` / `lpad` / `rpad` / `reverse` / `repeat` / `concat` / `concatWs` / `md5` / `locate` |
| Numeric | `abs` / `mod` / `power` / `sqrt` / `sign` / `exp` / `ln` / `log10` / `ceiling` / `floor` / `round` / `truncate` / `greatest` / `least` |
| Date | `now` / `curDate` / `curTime` / `date` / `year` / `month` / `day` / `hour` / `minute` / `second` / `quarter` / `dayOfWeek` / `dayOfYear` / `weekOfYear` / `dateAdd` / `dateSub` / `dateDiff` / `secondsBetween` / `unixTimestamp` / `fromUnixTime` / `secondsAgo` … `yearsAfter` |
| Conditional and casting | `caseWhen` / `ifThenElse` / `ifnull` / `coalesce` / `nullif` / `cast` / `castDecimal` / `regexp` / `regexpIgnoreCase` |
| Aggregate | `count` / `countDistinct` / `sum` / `sumDistinct` / `min` / `max` / `avg` / `stddev` / `variance` / `groupConcat` / `groupConcatDistinct` |
| Window | `rowNumber` / `rank` / `denseRank` / `nTile` / `lag` / `lead` / `firstValue` / `lastValue` / `over` |
| JSON and geospatial | `jsonExtract` / `jsonUnquote` / `stGeomFromText` / `stDistanceSphere` / `stWithin` / `match` |

```java
// Tidy up the name, and fall back to "(no name)" when it is empty
Dsl.coalesce(Dsl.trim(Post.title), "(no name)").as("title")

// Count per month
SQL.select(Dsl.year(Post.created_at).as("y"), Dsl.month(Post.created_at).as("m"), Dsl.count())
    .from(Post.instance())
    .groupBy(Dsl.year(Post.created_at), Dsl.month(Post.created_at))

// 30 days from now
Dsl.dateAdd(Post.created_at, 30, DateUnit.DAY)

// Roll the tags up into one string
Dsl.groupConcat(Tag.name, "/").as("tags")
```

Units (`DateUnit`) and types (`CastType`) are enums. **You cannot pass strings.**
They go straight into the SQL, so the API is built so that a string from outside
cannot reach them.

## Window functions

**They attach an aggregate to each row without collapsing rows.** Unlike
`GROUP BY`, you do not lose rows.

```java
SQL.select(
        Sale.shop_id
        , Sale.amount
        // Sales rank within each shop
        , Dsl.rank().partitionBy(Sale.shop_id).orderBy(Sale.amount.desc()).as("rank")
        // Running total
        , Dsl.over(Dsl.sum(Sale.amount))
            .orderBy(Sale.sold_at.asc())
            .rowsBetween(WindowFrame.unboundedPreceding(), WindowFrame.currentRow())
            .as("total"))
    .from(Sale.instance());
```

What you pass to `Dsl.over(...)` is an aggregate (`sum` / `count` / `avg` and so
on). Put an alias or a calculation inside it and you get an exception (attach
those after the `OVER`).

> [!WARNING]
> **A window function cannot go in `where` or in `having`.**
> SQL forbids it, because a window function is evaluated after both of them.
> Write `Dsl.rowNumber().over(...).eq(1)` and you get an exception **the moment
> the SQL is built.** To filter on a rank, select it in a subquery first and
> filter on the outside.

## `in` will not take an empty list

```java
where(Site.id.in(List.of()))   // ← throws the moment the SQL is built
```

`IN ()` is a syntax error. **Send it to the database quietly and the error
message tells you nothing about where the empty list came from** (F-D-07).

**We do not "drop the condition when the list is empty."**
`in(empty)` means "matches nothing"; dropping the condition means **everything**.
Only the caller knows which one was meant, so the caller decides.

```java
if (ids.isEmpty()) {
	return List.of();          // "matches nothing"
}
where(Site.id.in(ids));
```

## Product differences to watch

**Where the name is the same but the meaning is not**, the DSL lines them up.

| | How it is lined up |
| --- | --- |
| `length` | **Characters** (MySQL's `LENGTH` counts bytes). For bytes, use `byteLength` |
| `dayOfWeek` | **Sunday is 1** (PostgreSQL's `DOW` is 0, so we add 1) |
| `weekOfYear` | **ISO week** (MySQL's `WEEK` does not default to ISO, so we use `WEEKOFYEAR`) |
| `second` | Fractional seconds are **truncated** (raw PostgreSQL rounds, and can hand you 60) |
| `unixTimestamp` | Read in **the connection's time zone** (a raw PostgreSQL `timestamp` is treated as UTC) |
| `stddev` / `variance` | **Sample** (`STDDEV_SAMP` / `VAR_SAMP`) |
| `concat` | NULL if any argument is NULL (on PostgreSQL it becomes `\|\|`) |

Some things we **do not** line up.

| | The difference |
| --- | --- |
| `greatest` / `least` | **MySQL returns NULL if any argument is NULL; PostgreSQL ignores NULLs.** If NULL is possible, fill it with `ifnull` before passing it in |
| `regexp` | The regex dialects differ (MySQL 8 is ICU, PostgreSQL is POSIX). `^` `$` `[]` `+` are the same, but **`\d` only works on one of them.** Write `[0-9]` |
| Division by zero, negative lengths | MySQL returns NULL, **PostgreSQL blows up** (`mod(x, 0)`, `left(x, -1)`, and so on) |
| Passing a number to a string function | MySQL converts implicitly, **PostgreSQL blows up with "no such function".** Put a `cast` in between |
| `groupConcat` | MySQL **silently truncates** past `group_concat_max_len` (1024 bytes by default) |
| `cast` on a string that is not a number | MySQL returns **0**, PostgreSQL blows up |

**What the product does not have raises an exception (`DialectException`) at the
point where the SQL is built.**
The list is in [Using the DB](./db).

## When the DB product differs

**The same code emits SQL that follows `db.xxx.product`.**
Identifier quoting (`` ` `` and `"`), `ON DUPLICATE KEY UPDATE` versus
`ON CONFLICT`, `INSERT IGNORE` versus `ON CONFLICT DO NOTHING`, `RAND()` versus
`RANDOM()` — the builder absorbs all of it.

**What you cannot write for that product raises an exception
(`DialectException`) at the point where the SQL is built.**
Which ones those are is listed in [Using the DB](./db).

```java
// DialectException on PostgreSQL (to_char uses a different format language)
SQL.select(Dsl.dateFormat(Post.created_at, "%Y-%m-%d")).from(Post.instance());
```

## Running a batch

```java
db.insertBatch(builderList);
db.executeBatch(builderList);
```

The return value is a list of row counts. `DB.isBatchSuccess(list)` tells you
whether all of them went through.

## Large results

When you do not want the whole thing in a list, take it one row at a time.

```java
db.selectListWithFetcher(row -> {
	// arrives one row at a time
}, SQL.select().from(Post.instance()));
```
