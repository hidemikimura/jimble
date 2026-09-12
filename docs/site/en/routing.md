---
title: Routing
summary: get / post / path / before / after / error, and how to split up controllers
section: Basics
order: 1
---

# Routing

## Where routes are defined

The initializer block of a class extending `JimbleApp` is your route definition.

```java
public class App extends JimbleApp {

	{
		get("/hello", context -> context.response().send("hello"));
	}

	public static void main (String[] args) {
		JimbleServer.start(new App());
	}

}
```

The methods are `get` `post` `put` `patch` `delete` `head` `options` `trace`,
plus `any`, which registers for all of them. All lowercase.

## Path parameters

```java
get("/users/{id}", context ->
	context.response().send(context.route().variables().get("id")));
```

- `{id}` covers one path segment. It comes back as a single value even when it contains `%2F`
- `*` is a wildcard. `context.route().variables().wildcard()` gives you everything that is left

When several routes match the same path, **the more specific one wins**
(`/users/me` comes before `/users/{id}`).
The order is **fixed path > path parameter > wildcard**, and when a level has several
path parameters they are tried **in registration order.**

**Registering the same path and method twice throws right there** — better than finding out
after it is running.

### Routes that are never called

Even without a duplicate, a route can be one that **no request ever reaches.**

```java
get("/users/{id}", ...);
get("/users/{userId}", ...);   // ← never called
```

Only the name differs, so it registers fine, but `/users/5` always hits the first one.
**We find it at startup and warn.**

```
WARN  ルート: GET     /users/{userId} は一生呼ばれません（GET     /users/{id} が先に当たります）
```

A warning gets lost among the dozens of startup log lines, so **make it throw in CI.**

```conf
server {
	strict_routes = true
}
```

`/users/me` winning over `/users/{id}` is correct behaviour, so it is not reported —
`{id}` still serves everything that is not `me`. What this looks for is routes that get
**no request at all.**

### When only the method is wrong

If the path matches and only the method differs, you get **405**, not 404, with an `Allow` header.

```
$ curl -i -X POST http://localhost:9000/hello
HTTP/1.1 405 Method Not Allowed
Allow: GET
```

## Grouping

```java snippet=csrf-form
```

Everything registered inside `path()` gets the same `before`.
`path()` can be nested.

## Filters

| Registration | When it runs |
| --- | --- |
| `before(handler)` | Before the handler |
| `after(handler)` | After the handler (before the response is sent) |
| `error(handler)` | When an exception is thrown, or when no route matched |

Throw from inside a `before` and execution stops there and goes to `error`.
Authentication belongs here.

```java
static void requireAuth (WebContext context) {

	if (!"secret".equals(context.request().header().getString("x-token"))) {
		throw new HttpException(401, "authentication required");
	}

}
```

## How far a filter reaches

**A filter attaches to the place you wrote it. It does not attach to a path.**
It applies to the routes registered in the same block, and to anything nested
below it through `path()` or `install()`.

```java
path("/admin", () -> {
	before(AdminController::requireAuth);
	get("/users", ...);          // ← applies
	install(GroupController::new);   // ← applies (and to every route inside it)
});

path("/admin", () -> {
	get("/login", ...);          // ← does not apply (different block)
});
```

Same path or not, **a route registered in a different block is not covered.**
So if you want "everything under `/admin` requires auth" to hold,
keep the `/admin` routes in one place.
Read the other way round: **if someone adds `/admin/...` somewhere else,
they will not get caught by your filter without knowing it.**

Within a block, the order of `before` and the routes does not matter.
It applies to the whole block.

Filters are assembled per route **once, at startup**.
So **adding a filter after that is settled throws** (rather than creating
an "I added it but it does nothing" situation).
Keep route definitions entirely inside the controller's initializer block.

**On a miss (404), only the outermost `error` is called.**
No route matched, so there is no inner block to pick.

## Marking a single route

To take one filter off a single route, use an **attribute**, not an annotation.

```java
static final AttributeKey<Boolean> NO_AUTH = new AttributeKey<>("no_auth", false);

get("/health_check", context -> context.response().send()).attribute(NO_AUTH, true);
```

```java
if (context.route().route().attribute(NO_AUTH)) {
	return;
}
```

An `AttributeKey` carries a default. Routes without the attribute get that default,
so you never write a `null` check.

> [!TIP]
> **A key is the instance you created.**
> Hold one in a `static final` field and reuse it.
> Another key created with the same name is a *different* key, so writing through
> one and reading through the other gives you the default.
>
> **Creating a key with the same name as a framework key (`Auth.PUBLIC`, say) does not
> take over its slot.** It does make the two indistinguishable in logs, though, so
> two keys sharing a name **fail at startup** — rename one.

### Setting it for a whole block

**You do not have to write it on every route.** Call `attribute()` in a block and it
applies to every route registered in that block.

```java
path("/docs", () -> {

	attribute(PUBLIC, true);          // the whole block is public

	get("/guide", Guide::show);       // public
	get("/faq",   Faq::show);         // public
	get("/me",    Me::show).attribute(PUBLIC, false);   // this one needs a login

});
```

Strongest first: **the route > the inner block > the outer block > the key's default**.

**Where you write it in the block does not matter.** Put it at the end and it still
applies to routes registered above it — like `before`, it is handed out when the tree is
sealed at startup.

> [!TRAP]
> **Like filters, it does not attach to the path.** It applies to **routes registered in
> the same block**, and to anything nested from it via `path()` / `install()`. Routes
> registered in a different block do not get it, even at the same path.
>
> ```java
> path("/docs", () -> {
> 	attribute(PUBLIC, true);
> 	get("/guide", ...);        // gets it
> });
>
> path("/docs", () -> {
> 	get("/internal", ...);     // does not (a different block)
> });
> ```
>
> This is so that **reading the code tells you whether it is there**
> (the same rule as [where filters apply](#how-far-a-filter-reaches)).

`rateLimit()` rides on this same mechanism ([Rate limiting](./ratelimit)).

## Splitting into controllers

When one file gets long, pull it out into a `Controller`.

```java
public class PostController extends Controller {

	{
		get("/posts", context -> context.response().json("posts", listPosts()));
	}

}
```

```java
install(PostController::new);
```

What you pass to `install()` is **a constructor reference, not an instance**.
Nothing is scanned, so if you do not write `install()`, nothing is registered.
Check the route list in the startup log.

## Handling errors

```java snippet=error-handler
```

Throw `HttpException(404, "...")` and you land in `error` with that status.
Register several `error` handlers and they are called in registration order.
As soon as one of them sends a response, it stops there.

How the status code is decided, how a miss (404) is handled, and how this differs
from a validation failure are all in [Error handling](./errors).

## Calling your own routes from the inside

A registered route can be called without going through HTTP.

```java
CallResponse response = context.dispatcher().call(context
	, CallRequest.of("GET", "/api/posts").query("page", "2"));

Data json = response.json();
```

**It takes exactly the same path as a normal request.**
`before` / `after` / the error handlers / [rate limiting](./ratelimit) all apply.
The only difference is where it goes (you receive a `CallResponse` instead of putting
bytes on the network).

**The inner call runs as a continuation of the outer request.**
Headers, cookies, session, and flash are the outer ones as-is, and the execution ID
carries over, so the logs stay a single thread.
Override individual headers with `.header("X-Token", "...")`.

Cookies issued by the inner call ride out on the outer response and reach the client.

> [!trap]
> **The inner call is a separate transaction from the outer one.**
> Each context holds its own DB connection, so it does not join the transaction
> you opened outside.
> If work has to commit together, share it at the domain layer.

The main use for this is exposing your API as-is through [MCP](./mcp).
With `RouteTool`, one route becomes one tool.
