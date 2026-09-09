---
title: Your first endpoint in 5 minutes
summary: From jimble new to a running server with hot reload
section: Getting started
order: 2
---

# Your first endpoint in 5 minutes

## What you need

- **JDK 25** (LTS). jimble uses virtual threads and `ScopedValue`
- **Gradle 9 or later**. Gradle 8 does not run on Java 25
  (8 can target Java 25 as a toolchain, but Gradle itself will not run there)

You do not need a DB yet. You can add one later.

Installing the `jimble` command is covered in [The jimble command](./cli)
(run `./gradlew :jimble-cli:installDist` in the [repository](https://github.com/hidemikimura/jimble)
and put it on your PATH).

> [!TIP]
> **You can start without the command.** The build files `jimble new` writes are spelled out in
> "Writing it from scratch" on the [Gradle plugins](./gradle) page.
> Paste those two files and everything from here on is the same.

## 1. Create the skeleton

```bash
jimble new my-blog
cd my-blog
gradle wrapper --gradle-version 9.7.1
```

The version matters: **if the Gradle you have is 8, a bare `gradle wrapper` pins the wrapper to 8.**

`jimble new` creates the build files, `application.conf`, `App.java`,
`index.jte`, and `.gitignore` — nothing else. It does not create empty directories.

## 2. Start it

```bash
./gradlew run
```

```
jimble 構成: env=local / session=none / cache=db / redis=なし / db=[]
route: GET     /
jimble を起動しました: http://localhost:9000
```

The startup log prints **every route**.
That is so you catch "the route I thought I registered isn't there" at startup.

```bash
curl http://localhost:9000/
```

## 3. Add an endpoint

Add one line to the initializer block in `App.java`.

```java snippet=hello-route
```

The `{ }` is an initializer block; it runs before the constructor.
The initializer block of a class extending `JimbleApp` *is* your route definition.

To return JSON:

```java snippet=json-route
```

The snippets above come from the sample application (`examples/blog`), so you will
see methods that belong to *that* app, such as `listPosts()`. In your own app, put
your own code there.

`json()` builds up a `Data` (a subclass of `LinkedHashMap<String,Object>`) and
turns it into JSON at `send()` time.

## 4. Make your edits show up

```bash
./gradlew jimbleRun
```

`jimbleRun` puts a proxy on port 9000 and starts your app behind it, **inside the same JVM**.
When you edit a source file, it builds and swaps it in on the next request.
If the build fails, you see that error in the browser.

See [Hot reload](./hot-reload) for the details.

## 5. Decide how errors come out

```java snippet=error-handler
```

Whatever you pass to `error()` handles both thrown exceptions and the 404 for a
request that matched no route.
Register nothing and you get jimble's default shape.

## Where the classes live

The snippets leave out the `import` lines. The ones you need most often are here.

| Class | Package |
| --- | --- |
| `JimbleApp` / `JimbleServer` | `io.jimble.web.server` |
| `Context` / `WebContext` | `io.jimble.core.context` / `io.jimble.web.context` |
| `Data` | `io.jimble.util.data` |
| `Conf` | `io.jimble.util.conf` |
| `Log` | `io.jimble.util.log` |
| `DB` / `DBUtil` | `io.jimble.db` |
| `SQL` / `Dsl` | `io.jimble.db.sql` / `io.jimble.db.sql.query.dsl` |
| `Migration` | `io.jimble.db.migration` |
| Generated table definitions | `<codegen.package>.<schema>.table.<table>` |

## What to read next

- [Routing](./routing) — path parameters, filters, how to group routes
- [Using the DB](./db) — adding a database
- [Traps](./pitfalls) — read it now and save yourself a day
