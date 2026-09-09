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

> [!TRAP]
> **You cannot read straight off `context.request()`.**
> `Request` is a `Data` too, so `context.request().getString("title")` **compiles** — and
> returns **`null`**, because neither the body nor the query string is in there.
> Go through one of the methods in the table above (usually `bodyAll()`).
>
> ```java
> Data input = context.request().bodyAll();
> String title = input.getString("title");
> ```
>
> **`getStringOptional` does give you an empty string when the key is missing — and it
> puts that empty string into the `Data`.** Reading alone adds keys, so do not call it
> just before serialising to JSON or inside a loop ([Utilities](./util)).

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

The list of rules, the `ValidationExecutor` you can apply per route, and paging
are all in [Validation and paging](./validation).

## Sending a response

```java
context.response().send("text");                   // text/plain
context.response().json("posts", list);            // JSON
context.response().view("blog/posts.jte");         // template
context.response().redirect("/");                  // 302
context.response().download(file, "report.xlsx");  // download
context.response().code(201).send();               // no body
```

`json()` only builds; `send()` is what sends.
Call `json()` several times and it keeps adding to the same JSON document.

```java snippet=json-route
```

## Streaming something large

When you do not want the whole thing in memory, use `outputStream()` directly.

```java
context.response().setResponseHeader("Content-Type", "text/csv; charset=UTF-8");

try (OutputStream out = context.response().outputStream()) {
	// write it row by row; it flows out as you write
}
```

If all you want is to report progress, [SSE](./sse) is easier.

## Sending twice

Calling `send()` twice is an error.
`isSent()` tells you whether the response has already gone out.
Inside an `after` filter or an `error` handler, check that before you touch anything.
