---
title: Migrating from jooby
summary: What works as it is, and what you have to rewrite
section: More
order: 3
---

# Migrating from jooby

jimble's routing DSL is modelled on jooby's.
**Most route definitions work as they are.** What needs rewriting is everything around them.

## What works as it is

```java
get("/posts", ctx -> ...);
post("/posts", ctx -> ...);
path("/admin", () -> { ... });
before(...);
after(...);
```

The method names are lowercase, the same as jooby's. Nesting `path()` works the same way.

**How far `before` / `after` / `error` reach is different, though.**
jooby attaches them to a path; jimble applies them **only inside the block you wrote them in**.
Same `/admin` or not, a route registered in a different block is not covered
([How far a filter reaches](./routing)).
If you are relying on "everything under `/admin` requires auth", check that the
`/admin` routes really are all in one place.

## What you rewrite

### Context

| jooby | jimble |
| --- | --- |
| `ctx.path("id").value()` | `context.route().variables().get("id")` |
| `ctx.query("q").value()` | `context.request().bodyQuery().getString("q")` |
| `ctx.form("name").value()` | `context.request().bodyForm().getString("name")` |
| `ctx.body(Foo.class)` | `context.request().bodyJson()` → `Data` |
| `ctx.send("text")` | `context.response().send("text")` |
| `ctx.render(obj)` | `context.response().json(...)` / `.view(...)` |
| `ctx.setResponseCode(201)` | `context.response().code(201)` |
| `ctx.sessionOrNull()` | `context.session()` |

jimble **does not repack the input into a typed class**. It stays a `Data`.
That is because binding by reflection is gone. Validate the values with
[ValidationRules](./validation).

### Drop the DI

```java
// jooby
@Inject
public PostController (PostService service) { ... }
```

```java
// jimble
install(PostController::new);
```

You `new` your dependencies, or call a `static` method.
When you want to swap one out, pass it through the constructor.

```java
install(() -> new PostController(new PostService(db)));
```

### Drop the annotations

| jooby | jimble |
| --- | --- |
| `@GET @Path("/x")` | `get("/x", ...)` |
| `@Transactional` | `try (DBTransaction transaction = ...)` |
| `@Inject` | pass it through the constructor |

### Startup

```java
// jooby
public class App extends Jooby {
	{ get("/", ctx -> "hello"); }
	public static void main (String[] args) { runApp(args, App::new); }
}
```

```java
// jimble
public class App extends JimbleApp {
	{ get("/", context -> context.response().send("hello")); }
	public static void main (String[] args) { JimbleServer.start(new App()); }
}
```

**Handlers do not return a value.** You build the response on `context.response()` and send it.
Branch the rendering on a return value and what actually comes back stops being readable
from the code.

### Configuration

The `application.conf` format is the same HOCON. **The key names change.**

| jooby | jimble |
| --- | --- |
| `server.port` | `server.port` (the same) |
| `application.env` | `-Djimble.env=<env>` |
| `jooby.*` | Gone |

`jooby.*` keys are **not read. And there is no error, either.**
When you bring a configuration file over, always check the configuration line in the startup log.

### Templates

If you were on jooby's jte module, the templates themselves carry over.
Replace it with the `io.jimble.jte` plugin.

The model you pass becomes a `Data`. Receive it with `@param Data data`.

## The order to migrate in

1. Swap in the `build.gradle.kts` (`io.jimble.jte` / `io.jimble.run`)
2. Make `App.java` extend `JimbleApp`. The route definitions are a straight paste
3. Rewrite the insides of the handlers to `context.request()` / `context.response()`
4. Replace `@Inject` with `install()` and a constructor
5. Replace `@Transactional` with `try (DBTransaction ...)`
6. Start it up and **check the route list and the configuration line**

## What you do not have to migrate

The DB layer carries over. How you use `SQL` / `Column` / `Table` / `Data` / `DB` does not change.
The generated table definition classes work as they are, too.
