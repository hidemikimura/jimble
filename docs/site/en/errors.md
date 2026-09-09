---
title: Error handling
summary: Exceptions, misses, validation failures. Where each is caught and what comes back
section: Basics
order: 5
---

# Error handling

There are three paths by which an error reaches the surface. **They go through different places.**

| What happened | Who catches it | What comes back by default |
| --- | --- | --- |
| Something threw | The `error(...)` hook | The status depends on the exception. The app builds the body |
| No route matched | **The outermost** `error(...)` | 404 |
| Validation failed | `ValidationExecutor` (`error` is not reached) | 422 and a `validation` JSON body |

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
| Everything else | **500** |

```java snippet=error-http-status
```

Override it to assign codes to your own exceptions.

```java snippet=error-status-resolve
```

> [!WARN]
> `CodeException` (`io.jimble.util.exception.CodeException`) **has no effect on the HTTP status.**
> It is the checked exception used for DB errors (`db.getError()`) and inside validators; throw it and you get a 500.

## Who builds the body

**The framework has no default error page.** The order is this.

1. `context.response().code(statusCode)` goes in **first** (so a handler can override it)
2. `error(...)` handlers are called from the inside out. **It stops the moment one of them sends**
3. If nobody sends, `response().send()` is called

The point of step 3 is that it is `send()`, not `send(statusCode)`.
Whatever a handler built up with `json(...)` or the like is **sent, not thrown away.**

So if you set `code(...)` and never build a body, only the status comes back.

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

`acceptJson()` checks whether `Accept` carries `application/json` or `text/javascript`.

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

## Validation failures do not go through error

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
> **Put your validation-error formatting in `error(...)` and it is never called.**
> To change how a 422 looks, work on the `ValidationExecutor` side (`onCancel`).

## What you can replace application-wide

There are exactly three things you can override on `JimbleApp`.

| Method | When it is called |
| --- | --- |
| `protected void onRequest (WebContext context) throws Exception` | First thing on every request. **Called even on a miss.** Send from here and nothing after it runs |
| `protected void onComplete (WebContext context)` | Last thing on every request. Always called, exception or not |
| `protected int resolveStatusCode (Throwable cause)` | Exception to status code |

The dispatcher itself cannot be replaced (it is `final`).
There is meant to be exactly one path through.
