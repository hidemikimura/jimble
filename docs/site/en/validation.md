---
title: Validation and paging
summary: ValidationRule / ValidationExecutor, and how page and per are read
section: Basics
order: 4
---

# Validation and paging

## Validation comes in three levels

| Level | Class | What it does |
| --- | --- | --- |
| One field | `ValidationRule` | Stack up "not empty", "an integer from 1 to 120" |
| One request | `ValidationRules` | Bind rules to columns and run them in one pass |
| One route | `ValidationExecutor` | On failure, **stop everything downstream and return 422** |

Use only the lower levels, or only the top one. Either works.

## Building a rule

```java
ValidationRule rule = new ValidationRule()
	.empty()
	.textLengthMax(100);
```

They run in the order you stacked them and **stop at the first failure** (one error per field).

| Kind | Methods |
| --- | --- |
| Required | `empty()` / `required()` (the same thing) |
| Character count | `textLength(min, max)` / `textLengthMin(min)` / `textLengthMax(max)` |
| Byte length | `textByteLength(min, max[, charset])` / `textByteLengthMin` / `textByteLengthMax` (UTF-8 by default) |
| Numbers | `integer()` / `integer(min, max)` / `integerMin` / `integerMax` / `number()` / `number(min, max)` / `numberMin` / `numberMax` |
| Format | `bool()` / `email()` / `url()` / `domain()` / `date()` / `date(format)` / `regex(regex)` / `enumType(Class)` |
| Character classes | `characterType(CharacterType[])` / `characterType(Character[])` / both |
| Your own | `custom(IValidator)` |
| Conditional | `insertRequired()` |

> [!NOTE]
> **Everything other than `empty()` lets empty through.**
> `textLengthMax(100)` means "at most 100 characters, if there is a value";
> an empty string or `null` is not an error. **Always write required as `empty()`.**

> [!NOTE]
> **Format checks are matched against the whole value.**
> `email()`, `url()`, `domain()`, `regex(...)` and `characterType(...)`
> do **not** pass just because something plausible appears somewhere inside —
> `hello a@example.com there` fails `email()`.
> `regex("[0-9]{4}")` means "the whole value is four digits",
> so **wrap it in `.*` when you want a partial match.**

> [!NOTE]
> **`bool()` accepts only `true` / `false` / `1` / `0`** (case does not matter).
> `yes` fails — **the reading side uses the same spellings**, so widening this
> means someone who answered "yes" gets stored as `false`.

> [!WARN]
> **`date()` and `date(format)` only check that the value can be read.**
> `2026-13-01` (month 13), `2026-02-31` (Feb 31st) and
> `2026-09-12x` (trailing junk) all **pass**.
> `date()` goes through the same shared conversion as database reads and request binding.
> **To check that the date is real, stack a `regex(...)` on it or write a `custom(...)`.**

## Binding rules to columns

```java snippet=validation
```

- **Errors across fields are all collected** (being told about them one at a time is the worst possible experience for the person retyping the form)
- **Fields that were not sent are not validated** (except through `insertRequired()`)
- When an array arrives for one column, every element is run through
- `put(rule)` (with no column) lets you write cross-field checks that belong to no single field

### The raw shape of an error

What `validate` returns is **not wording.** It is which kind of check failed, and the settings it failed against.

```java
{ "validation_type": Empty, "validation_setting": {}, "input": "" }
```

`ValidationMessages.toMessages(errors)` turns that into something a person can read:
**a map of field name to messages**.

```json
{ "title": ["入力してください"], "age": ["1 以上 120 以下の整数で入力してください"] }
```

> [!TIP]
> Because the result carries a kind instead of wording, **the same validation result can become Japanese, English, or an API code.**

### Swapping the wording

```java snippet=validation-message
```

> [!TRAP]
> `ValidationMessages` is **static and global**. If you swap it in a test,
> call `ValidationMessages.reset()` in a `finally`.
> Forget, and **the tests that run afterwards are the ones that fail.**

## Required only on insert

```java snippet=validation-insert-required
```

You hand the "is this an insert request?" decision to `insertRequestChecker`.
The same decision reaches each validator as `isInsertRequest`.

## Validating multiple rows

```java snippet=validation-list
```

**Only the rows with errors** come back, and each one carries an `index` (**1-based**).

## Applying it to a route

Extend `ValidationExecutor` and write nothing but `validate`.

```java snippet=validation-executor
```

On the route, **stack it first.**

```java snippet=validation-executor-route
```

When it fails, this is what you get.

| | |
| --- | --- |
| Status | **422** |
| Body | `{"validation": {"field": ["message"]}}` **plus the input that was sent** |
| Executors after it | **Not run** (discarded) |
| The `error(...)` hook | **Not reached** (nothing was thrown — see [Error handling](./errors)) |

You can also stack the result of `ValidationRules` directly.

```java
addErrors(rules.validate(db, context.request().bodyAll()));
```

> [!NOTE]
> `ValidationExecutor` **does not hold `WebContext` in a field.**
> It receives it as the argument to `validate(WebContext)`.
> That avoids the accident where a reused instance **writes into the previous request's context.**

> [!WARN]
> **Rules are not generated from your column definitions.**
> The generated table classes (`Post.title` and the rest) carry
> the type, nullability and primary key only — **they do not carry the varchar length.**
> There is nothing to derive `textLengthMax` from, so you write the rules by hand.

## Paging

### Reading it from the request

```java
Paging paging = context.request().paging();
```

| | |
| --- | --- |
| Keys it reads | `page` / `per` |
| Defaults | `page = 1`, `per = 10` |
| Cap | `paging.max_per` (200 by default). **A larger request is rounded down to it** |
| Everything | `per=all`. **The cap still applies, so 200 rows by default** (`paging.max_per = 0` for a real "everything") |
| Renaming the keys | `paging.name_page` / `paging.name_per` (configuration) |
| A non-numeric value | Ignored; the default is used |

Write `context.request().paging(20)` to change the default used when no `per` arrives.

### Applying it to a SELECT

```java snippet=paging-select
```

`selectListWithRowCount` **also issues a COUNT for the total.**
The COUNT statement copies only FROM / WHERE / GROUP BY / HAVING,
so the SELECT list, ORDER BY and LIMIT have no effect on it.

> [!TRAP]
> **`selectListWithRowCount` is the only thing that counts the total.**
> With an ordinary `selectList(builder)`, `paging.totalCount()` stays 0
> and **your pager reads "page 1 of 1".**

> [!WARN]
> The raw-SQL version (`selectListWithRowCount(sql, params...)`) does not touch `Paging`.
> Call `paging.set(response.list.size(), response.rowCount)` yourself.

### What you can read

| Method | What it holds |
| --- | --- |
| `page()` | The current page |
| `per()` | Rows per page |
| `perAll()` | Whether "everything" was requested |
| `totalCount()` | The total number of rows |
| `maxPage()` | The total number of pages (**at least 1**) |
| `start()` | The index of this page's first row (**1-based**) |
| `count()` | How many rows this page actually returned |

> [!NOTE]
> **There are no "has next" / "has previous" methods.**
> Decide with `page() > 1` and `page() < maxPage()`.

### It rides on the response

**The moment you call** `context.request().paging()`, it is on the response. You do not repack it yourself.

```json
{
  "rows": [ ... ],
  "paging": { "page": 2, "per": 10, "perAll": false,
              "maxPage": 3, "totalCount": 25, "start": 11, "count": 10 }
}
```

Templates see the same thing (`${paging.page()}`).

### What to watch for

> [!TRAP]
> **There is no upper bound on `per`.** Send `?per=100000` and
> `LIMIT 100000` goes out exactly as written. On a listing you expose publicly,
> cap it yourself around the `paging(...)` call.

An out-of-range page (`?page=999`) is not an error; **it returns zero rows.**
