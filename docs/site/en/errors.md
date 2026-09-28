---
title: Error handling
summary: Exceptions, misses, validation failures. Where each is caught and what comes back
section: Basics
order: 5
---

# Error handling

There are four paths by which an error reaches the surface. **They go through different places.**

| What happened | Who catches it | What comes back by default |
| --- | --- | --- |
| Something threw | The `error(...)` hook | The status depends on the exception. The app builds the body |
| No route matched | **The outermost** `error(...)` | 404 |
| `validate(...)` did not pass (`ValidationException`) | The `error(...)` hook | 422 and a `validation` JSON body |
| `ValidationExecutor` validation failed | `ValidationExecutor` (`error` is not reached) | 422 and a `validation` JSON body |

## Writing an error handler

```java
error((context, cause, statusCode) -> {
	context.response().code(statusCode).send("error: " + statusCode);
});
```

It takes three arguments. **You get the status code, not only the exception.**
That way you do not re-derive the code from `cause` every single time.

```java
void handle (WebContext context, Throwable cause, int statusCode) throws Exception;
```

## How far it reaches

Same as `before` / `after`: **it attaches to the block you wrote it in** (not to a node in the path).
Execution runs <b>from the inside outward</b>. See [Routing](./routing) for the details.

```java snippet=error-fallthrough
```

A request to `/admin/x` calls them inner first, then outer.
**Even when the inner one throws, execution moves on to the outer one** (see "When the handler itself fails" below).

> [!NOTE]
> A miss (404) is the one case that works differently. **Only an `error` written directly in the outermost scope** is called.
> No route matched, so there is no "inner" to pick.
> An `error` written inside `path("/admin", ...)` is not called, not even for `/admin/nope`.

## How the status code is decided

`JimbleApp#resolveStatusCode(Throwable)` decides it. The default is only this.

| Exception | Code |
| --- | --- |
| `HttpException` | Whatever `statusCode()` says |
| `NotFoundException` (a subclass of `HttpException`) | 404 |
| `ValidationException` (a subclass of `HttpException`) | 422 |
| Everything else | **500** |

```java snippet=error-http-status
```

Override it to assign codes to your own exceptions.

```java snippet=error-status-resolve
```

> [!WARN]
> `CodeException` (`io.jimble.util.exception.CodeException`) **has no effect on the HTTP status.**
> It is an unchecked exception (a subclass of `RuntimeException`) that carries a code; throw it and you get a 500.
> DB failures (`SqlExecuteException`) and the like are also a 500 unless you do something (see "Exceptions the framework throws" below).

## Who builds the body

The order is this.

1. `context.response().code(statusCode)` goes in **first** (so a handler can override it)
2. `error(...)` handlers are called from the inside out. **It stops the moment one of them sends**
3. If nobody has built a body, the **default error response** goes in
4. `response().send()` is called

The point of step 4 is that it is `send()`, not `send(statusCode)`.
Whatever a handler built up with `json(...)` or the like is **sent, not thrown away.**

```java
error((context, cause, statusCode) -> {

	// JSON for an API, HTML for a screen
	if (context.request().acceptJson()) {
		context.response().json("error", cause.getMessage());
		return;
	}

	context.response().send("error: %d %s".formatted(statusCode, cause.getMessage()));

});
```

> [!WARN]
> Putting `cause.getMessage()` in the body, as above, means **whatever is in it goes out.**
> On a 500 that is the DB's error text and your internal paths.
> Hand a `Throwable` straight to `json(...)` and **the whole stack trace goes out.**
> The default response below carries neither, but **what you write is yours to keep clean.**

## When you write nothing

With no `error(...)` at all — or one that never builds a body — jimble returns a **fixed shape.**

If `Accept` names JSON, it is JSON.

```json
{"error": {"status": 404, "message": "Not Found"}}
```

Otherwise it is a short line of text (`Content-Type: text/plain; charset=UTF-8`).

```
404 Not Found
```

- **`message` is the short phrase from RFC 9110** (`Not Found`, `Internal Server Error`) —
  the same wording you see in the status line and in client libraries
- **The cause is not in there.** No exception message, no SQL, no stack trace.
  The cause is in the log (5xx goes to `Log.error`)
- **`*/*` gets text.** "Anything will do" is not "JSON, please"
- **An `error(...)` handler wins**, even one that built a body without sending it

`acceptJson()` asks whether `Accept` **names** `application/json` or `text/javascript`.
Parameters are fine — `application/json;q=0.9` counts. `q=0` (don't want it) and `*/*`
(anything will do) do not.

## 405 and 404 are different answers

When the path matches and only the method is wrong, you get a 405 with an `Allow` header.

```
$ curl -i -X POST http://localhost:9000/hello
HTTP/1.1 405 Method Not Allowed
Allow: GET
```

404 means "there is no such thing"; 405 means "there is, but not by that name".
Merge them and writing `get` where you meant `post` looks like a wrong path.

## When the handler itself fails

**We swallow it and move on to the next one, outward.** Being left unable to return anything
because error handling failed is the worse outcome.
The failure is logged as `エラーハンドラで例外が発生しました`.

## How it is logged

| Status | Log |
| --- | --- |
| 5xx | `Log.error` (with a stack trace) |
| Everything else (404 included) | `Log.debug` |

> [!TIP]
> Keeping 404 out of the error log is deliberate.
> Mix in the thousands a day that crawlers and stale links produce, and
> **the real 500s are buried.**

An exception thrown inside `after` or `onComplete` is swallowed and logged the same way (the response still goes out).

## Validation failures

### validate(...) answers with an exception

`ValidationRules.validate(db, data)` / `Validator.validate(...)` throw a
**`ValidationException`** (422, a subclass of `HttpException`) when the input does not pass.
Like any other exception it goes through `error(...)`, and **if nobody builds a body** the framework
answers in the shape below (the same as `ValidationExecutor`).

```java
rules.validate(db, data);   // leaves here with a 422 if it does not pass
```

When you want to handle the list yourself, take it with `errors(db, data)` (no exception).
Checking a list of rows is `validate(db, List)` / `errors(db, List)`; the body is `{"rows": [...]}`.

### ValidationExecutor does not go through error

`ValidationExecutor` **does not throw.**
It cancels itself, discards the executors behind it, and returns as-is.

```java
context.response().putForm(context.request().bodyAll());
context.response().json("validation", errors);
context.response().code(422);
```

This is the shape you get back. The input comes back with it, so you can rebuild the form.

```json
{
  "validation": { "title": ["入力してください"] },
  "title": "",
  "body": "..."
}
```

> [!TRAP]
> **For a `ValidationExecutor` failure, formatting you put in `error(...)` is never called.**
> To change how a 422 looks, work on the `ValidationExecutor` side (`onCancel`).
> A `ValidationException` from `validate(...)`, on the other hand, can be formatted in `error(...)`.

## Exceptions the framework throws

All of them are **unchecked**. Apart from the `HttpException` subclasses, each becomes a **500** unless you do something.

| Exception | When | Parent |
| --- | --- | --- |
| `SqlExecuteException` | SQL failed. `getCode()` gives the code (table below) | `RuntimeException` |
| `DuplicateKeyException` | A unique constraint was hit | `SqlExecuteException` |
| `TransactionException` | A transaction could not be committed, begun or rolled back | `SqlExecuteException` |
| `SqlBuildException` | The SQL cannot be built (`eq(null)` / `not(null)` and so on) | `RuntimeException` |
| `RedisLockException` | `RedisLock.lock(...)` could not take the lock | `RuntimeException` |
| `DataConversionException` | A Data value is there but cannot be read (`"abc"` through `getInt`, say) | `IllegalArgumentException` |
| `JsonParseException` | Broken JSON handed to `Data.fromJsonString` / `Dson.decodes` | `IllegalArgumentException` |
| `ValidationException` | `validate(...)` did not pass (**422**) | `HttpException` |
| `CodeException` | A failure with a code (often found in another exception's `getCause()`) | `RuntimeException` |

> [!NOTE]
> When the body is `application/json` but cannot be read, `body()` / `bodyJson()` / `bodyAll()` throw a **400** `HttpException`.
> It does not become a `JsonParseException` (500).

These are the DB codes. The original JDBC exception is in `getCause().getCause()`.

| Code | Exception | Meaning |
| --- | --- | --- |
| `DB_001` | `TransactionException` | The transaction could not be begun |
| `DB_002` | `TransactionException` | It could not be rolled back |
| `DB_003` | `TransactionException` | It could not be committed |
| `DB_004` | `TransactionException` | SQL had failed inside the transaction, so it was not committed (everything was rolled back) |
| `DB_005` | `TransactionException` | An inner, joined `Tx` asked for a rollback, so the outer one was not committed (everything was rolled back) |
| `DB_006` | `TransactionException` | The body of `transaction(tx -> ...)` threw a checked exception (wrapped and rethrown) |
| `DB_007` | `SqlExecuteException` | `DBUtil.load(...)` could not create the data source (startup stops) |
| `DB_998` | `SqlExecuteException` | Different SQL was mixed into `executeBatch` / `insertBatch` |
| `DB_999` | `SqlExecuteException` / `DuplicateKeyException` | Executing the SQL failed |

For more, see [Using the DB](./db) and [Transactions](./transaction).

## What you can replace application-wide

There are exactly three things you can override on `JimbleApp`.

| Method | When it is called |
| --- | --- |
| `protected void onRequest (WebContext context) throws Exception` | First thing on every request. **Called even on a miss.** Send from here and nothing after it runs |
| `protected void onComplete (WebContext context)` | Last thing on every request. Always called, exception or not |
| `protected int resolveStatusCode (Throwable cause)` | Exception to status code |

The dispatcher itself cannot be replaced (it is `final`).
There is meant to be exactly one path through.
