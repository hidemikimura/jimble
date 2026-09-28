---
title: Request and response
summary: Reading input, nested parameters, validation, and how to send a response
section: Basics
order: 2
---

# Request and response

## Reading input

You get it from `context.request()`. It is split by where the value came from.

| Method | What is in it |
| --- | --- |
| `bodyPath()` | Path parameters (`/users/{id}`) |
| `bodyQuery()` | The query string |
| `bodyForm()` | `application/x-www-form-urlencoded` / `multipart` |
| `bodyJson()` | The JSON body |
| `bodyFile()` | Uploaded files ([File upload](./upload)) |
| `bodyAll()` | All of the above, layered together |

`bodyAll()` layers in this order: **path → query → form → json**.
Later ones overwrite earlier ones.

Every one of them returns a `Data` (a subclass of `LinkedHashMap<String,Object>`).
You pull values out with `getString` `getInt` `getLong` `getBoolean` `getData` `getDataList` and friends.
**A missing key gives you `null` from `getString`, `0` from `getInt`, and `false` from
`getBoolean`** — you cannot tell "missing" from "0". When you need to, use the Object
versions (`getIntObject` and friends) or `isNull(key)`. See [Utilities](./util).

**A value that is there but cannot be read** (`"abc"` read with `getInt`, for example) is a `DataConversionException`
instead of quietly becoming `0`. **Run user input through [validation](./validation) first.** Read it unchecked and the
exception becomes a 500. The form that takes a default (`getInt("page", 1)`) returns the default only when the key is missing or blank.

**`Request` is not a `Data`.** You cannot read it like `context.request().getString("title")` (that is a compile error).
Go through one of the methods in the table above (usually `bodyAll()`).

```java
Data input = context.request().bodyAll();
String title = input.getString("title");
```

**When the `Content-Type` is `application/json` but the body cannot be read as JSON, `body()` / `bodyJson()` / `bodyAll()`
throw a 400 `HttpException`** (the same on every read). Nothing carries on with a half-read body or an empty `Data`.

> [!TRAP]
> **`getStringOptional` does give you an empty string when the key is missing — and it
> puts that empty string into the `Data`.** Reading alone adds keys, so do not call it
> just before serialising to JSON or inside a loop ([Utilities](./util)).

## Headers and cookies (when the same name arrives twice)

Read them from `context.request().source()`.

| Method | What it holds |
| --- | --- |
| `headers()` | Headers, keys lower-cased. **Two lines with the same name are joined with `", "`** (`Cookie` uses `"; "`) |
| `cookies()` | Cookies. **If the same name arrives twice, only the first** |
| `headerValues()` | Headers, **before joining** (`Map<String, List<String>>`) |
| `cookieValues()` | Cookies, **before discarding** (`Map<String, List<String>>`) |

`headers()` / `cookies()` is normally enough.
Reach for `...Values()` only when **the fact that it arrived twice** is what you need.

> [!TRAP]
> **The same cookie name can arrive twice.**
> It happens when the same name is set on a different path or domain.
> **Which one comes first is not decided by the cookie spec.**
>
> When this happens to a session ID, **logins drop out at random**——
> no exception is raised, and the next request may look fine.
> jimble **warns when two or more arrive** (it does not log the values).
> If you see that warning, line up `Path` / `Domain` and set the cookie again.

## Nested parameters

You can nest with either `.` or `[ ]`.

```
user.name=taro
user[name]=taro          the same
items[0].price=100
items[].price=100        appends to the end
```

A number inside `[ ]` is an index, empty means append, anything else is a name.
When form values and a JSON body collide, the form values overwrite the JSON.

## Validating

```java snippet=validation
```

**Validation does not stop at the first error.** It collects them all, then returns.
For the person retyping the form, being told one problem at a time is the worst outcome.

`ValidationMessages.toMessages(errors)` turns them into a `Data` of field name → message.
Return that as JSON or hand it to a template.

When stopping is fine, write `rules.validate(db, input);` as a statement. If it fails, it throws `ValidationException` and the framework replies 422.

The list of rules, the `ValidationExecutor` you can apply per route, and paging
are all in [Validation and paging](./validation).
Pass the page size **on the first call** (`paging(50)` with a different count after `paging()` throws `IllegalStateException`).

## Sending a response

```java
context.response().send("text");                   // text/plain
context.response().json("posts", list);            // JSON
context.response().view("blog/posts.jte");         // template
context.response().redirect("/");                  // 302
context.response().download(file, "report.xlsx");  // download
context.response().code(201).send();               // no body
```

`json()` only builds. Call it several times and it keeps adding to the same JSON document.

**There is only one way to reply per response.** Stack two different kinds out of `json` / `jsonl` / `text` / `cache` / `view` / `redirect` / `download`
(`redirect(...)` after `json(...)`, say) and the second one throws `IllegalStateException`.
Stacking the same kind again is fine.

**You do not need a `send()` after `json()`.** Once it is built, the dispatcher sends it at
the end of the execution. Write `send()` explicitly only when you want to finish **with no
body** (`code(204).send()`), or when you are returning text directly.

```java snippet=json-route
```

**204 / 205 / 304 cannot carry a body** (that is how HTTP defines them).
If you `code(204).send()` with something already built by `json(...)` or similar, **the body is
dropped and only the status and headers go out** (Set-Cookie still arrives, so answering a logout
with 204 works). A WARN is logged when something is dropped — a body built for nobody is usually a
mistake. The body itself is not written to the log.

**If the target of `redirect()` is the same URL as the request being handled, a
`RedirectLoopException` (500) is thrown.** Sent as-is, the browser would fetch the same URL
again and the same handler would answer with the same target — it never stops. The exception
goes to `error(...)` like any other, so whether to show a page or return JSON is decided there.
Only GET / HEAD are checked; sending `POST /login` back to `/login` (PRG) is not stopped.
Only a one-hop loop back to itself is detected — `/a → /b → /a` across separate requests cannot
be seen from inside one request.

## Streaming something large

When you do not want the whole thing in memory, use `outputStream()` directly.

```java
context.response().setResponseHeader("Content-Type", "text/csv; charset=UTF-8");

try (OutputStream out = context.response().outputStream()) {
	// write it row by row
}
```

**Calling `outputStream()` fixes the status and headers.**
`code(...)`, cookies put in `cookies()` and the default `Cache-Control: no-store` (not overwritten if you set your own)
are applied at that moment, so settle them first. **Writing a header or a cookie after the response has been sent throws `IllegalStateException`** (it would never arrive).
For 204 / 205 / 304, whatever you write is dropped and a WARN is logged.

**What you write stays buffered until you `flush()`.** For something written to the end, like a CSV, that is fine as is;
when the other side should see each piece as soon as it is ready (progress, relaying another server's response), `flush()` after each write.

To hand over an `InputStream`, use `send(in, "type")` (or `stream(...)`).
**Whenever nothing more is available yet (`available()` is 0), what has been read so far is sent out.**
Things already at hand, like files, are written in large chunks; things that trickle in, like another server's streaming response, flow out as they arrive.

```java
HttpResponse<InputStream> upstream = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
context.response().code(upstream.statusCode()).send(upstream.body(), "text/event-stream");
```

If all you want is to report progress, [SSE](./sse) is easier.

## Sending twice

Calling `send()` twice is an error.
`isSent()` tells you whether the response has already gone out.
Inside an `after` filter or an `error` handler, check that before you touch anything.
