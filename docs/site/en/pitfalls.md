---
title: Traps
summary: The places that go quiet instead of failing. Read this first and save yourself a day
section: More
order: 1
---

# Traps

Bugs that crash find themselves. What is collected here are **the bugs that do not crash.**

## Cookies do not work locally

**Symptom**: session, login, CSRF, flash — none of them work, and there is no error.

**Cause**: `cookie.secure` defaults to `true`. Local is http, so the browser
never sends the cookie back.

**Fix**: put this in your local `application.conf`. Take it out in production.

```conf
cookie {
	secure = false
}
```

A WARN is printed at startup, so read the log.

## The route is not registered

**Symptom**: you get a 404. The code is right there.

**Cause**: you forgot to write `install(SomeController::new)`.
jimble does not scan the classpath, so if you do not write it, nothing is registered.

**Fix**: look at the route list in the startup log. If it is not there, it is not registered.

## before is not applying (and it is the same path)

`before` applies **only inside the block you wrote it in**. It does not attach to a path.

```java
path("/admin", () -> {
	before(requireAuth);
	get("/users", ...);
});

install(() -> new SpaController("/admin", ...));   // ← not authenticated
```

Same `/admin` or not, a route registered somewhere else is in a different block.
If you want them covered together, put them in the same block.

The opposite symptom — **it applies where you did not want it to** — does not happen.
That is what used to happen in the code this was ported from, where even the SPA login
screen under `/admin` was caught by the auth filter.

Also, **adding a `before` after the routes are settled throws.**
That is so "I added it and it does nothing" never gets through in silence.
Keep route definitions entirely inside the controller's initializer block.

## Reading straight off request() silently gives you nothing

**Symptom**: you are posting a form or JSON, but no value comes back. No exception.

**Cause**: `Request` is a `Data` too, so this is writable:

```java
context.request().getString("title")   // compiles. returns null
```

The `Request` object itself holds neither the body nor the query string.

**What to do**: go through `bodyAll()` (or `bodyJson()` and friends when you want to
pin down where the value came from).

```java
Data input = context.request().bodyAll();
String title = input.getString("title");
```

`getString` on a missing key is `null`. **Put that into a NOT NULL column and you get
`Column 'title' cannot be null`.**

## The session was not saved

**Symptom**: you called `put()`, and on the next request it is gone.

**Cause**: you did not call `save()`. Nothing is saved automatically.

```java snippet=session-save
```

## You cannot get at the select result

**Symptom**: `row.getString("title")` comes back empty.

**Cause**: the result is nested under the table name.

```java snippet=select-nesting
```

Look it up with `Column` and you cannot get it wrong.

## You do not notice the DB error

**Symptom**: zero rows when zero is impossible. Or an NPE.

**Cause**: DB errors are return values, not exceptions. `select` gives `null`, the update
family gives `-1`.

```java snippet=db-error
```

## The transaction did not carry on

**Symptom**: an update made after `commit()` does not go away when you roll back.

**Cause**: this one is about the code this was ported from. Its `commit()` ended the
transaction, so everything past that point ran in autocommit.
In jimble, `commit()` does not end it. `commitEndTransaction()` is what ends it.

**When you read code you brought over, check what comes after each `commit()`.**

## "A transaction was left neither committed nor rolled back"

```
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックします
```

**Cause**: you called `beginTransaction()` without wrapping it in try-with-resources,
and then returned partway through.

**Fix**: wrap it. jimble picks it up at the end of the execution and rolls it back,
but that is cleaning up after an accident, not the right way to write it.

## Which tables exist depends on which entry point you started

```
{"error":"トランザクションのコミットに失敗しました。"}
```

**Cause**: `MqQueue#install()` or `BatchTables.install()` is only called from **some** of
your entry points.

When the web app, the batch and the scheduler each have their own `main`, it is easy for
each one to set up only what it thinks it needs. You end up creating the MQ table in the
batch entry point while it is **the web app that pushes onto the queue**.

The nasty part: **on a machine where the batch has been run even once, it works.** You
will not see it locally. It shows up the first time you point at an empty database — CI,
or a new environment.

**Fix**: **Put the startup setup in one place and have every entry point call it.**

```java
public final class Bootstrap {

	public static void load () {

		Migration.install();                                  // before DBUtil.load

		// false when it could not connect. Ignore it and you fall over later
		// with an exception that says nothing about the DB
		if (!DBUtil.load(Conf.conf().config(), Bootstrap.class)) {
			throw new IllegalStateException("could not load the DB");
		}

		BatchTables.install(DBUtil.getMainDB());
		new MqQueue(NoticeExecutor.QUEUE_NAME).install();

	}

}
```

**Call the same thing from your tests.** Set the tests up separately and you get the state
where **only the tests pass** once an entry point changes.

> [!TIP]
> **Run it once against an empty database** — that is the quickest way to check.
> It is how we found `examples/blog` was broken.

## The generated column order changes when you change products

**Symptom**: `codegen` produces a diff although nothing was changed. Only the
column order differs.

**Cause**: the generated column order is the **physical order**. Where
`ALTER TABLE ... ADD COLUMN` puts the column differs between MySQL (which takes
`AFTER body`) and PostgreSQL (which always appends), so the same schema can come
out in a different order.

**What to do**: **generate on the product you lead with**, and run
`codegenCheck` there too. Types, nullability and comments all match, so nothing
but the order changes.

## SSE does not arrive, or arrives all at once

**Cause**: an nginx in between is buffering it up.

**Fix**: jimble attaches `X-Accel-Buffering: no`. If that is not enough,
set `proxy_buffering off;` on the nginx side.

## The SSE loop never ends

**Cause**: you are not checking `sse.isOpen()`.

`isOpen()` tells you **whether the lifetime is still running**. It cannot detect a
disconnect (helidon does not tell it, and the write can block forever).
Check it inside every loop. [SSE](./sse) has the details.

## You cannot read cookies in a WebSocket

**Cause**: you are trying to read them after the upgrade.
The HTTP headers are visible at `onUpgrade` and nowhere else.

**Fix**: authenticate in `onUpgrade`. Return `false` and you get a 403.

## An MQ message ran twice

**Cause**: that is how it is. Exactly-once cannot be built.

**Fix**: write it so that running it again changes nothing. [MQ](./mq) has the details.

## A HOCON comment breaks the config

```
Key '/' may not be followed by token: '*'
```

**Cause**: you used `/* */`. HOCON comments are `#` or `//`.

## jimbleRun says "no main manifest attribute"

`gradle-wrapper.jar` has no `Main-Class`. **Your `gradlew` and your
`gradle-wrapper.jar` do not match.**

Today's `gradlew` starts like this.

```sh
exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ...
```

That needs a JAR with a `Main-Class`. Leave an old JAR (one written for `-classpath`)
lying around and `gradlew` and the JAR look like a matched pair while nothing works.

```sh
unzip -p gradle/wrapper/gradle-wrapper.jar META-INF/MANIFEST.MF
# Main-Class: org.gradle.wrapper.GradleWrapperMain  ← if this is missing, that is your problem
```

To fix it, run this once in that project.

```sh
gradle wrapper --gradle-version 9.7.1
```

`jimbleRun` shells out to `./gradlew` on every rebuild, so **you see this on every save.**
Once it works out that the wrapper is broken, it prints the reason and falls back to
the `gradle` on your PATH, so hot reload itself keeps going.

## Gradle will not start

```
* What went wrong: 25.0.4
```

**Cause**: Gradle 8 does not run on Java 25.
(Using Java 25 as a toolchain works on 8 — it is Gradle itself that will not run.)

**Fix**: use Gradle 9 or later.

## Kotlin comments nest

If a `/*` turns up inside a block comment in `build.gradle.kts`,
**a nested comment starts right there** (Kotlin block comments nest).
The outer one never closes, and every line of code below it disappears. **There is no error.**

```kotlin
/*
 * Reads docs/site/<lang>/*.md      ← this /* opens a nested comment
 */
tasks.register("site") { ... }      ← never registered, and never complains
```

When you write a path, avoid `*`, or use `//`.

## `docs/` in .gitignore applies at every level

**Symptom**: the templates are missing from the repository you published.

**Cause**: write `docs/` in `.gitignore` and git applies it to **a `docs/` at any level.**
It drags `jimble-docs/src/main/jte/docs/` in with it.

**Fix**: put a `/` in front and write `/docs/`.

## Build scripts that break on Gradle 9

Some things pass on Gradle 8 and break on 9.

| What you wrote | What happens |
|---|---|
| `val x by tasks.registering { }` | Deprecated in 9.6. In the Kotlin DSL it is a **compile error**. Use `tasks.register("x") { }` |
| `"...".formatted(...)` | A Java 15 method. From a 9.7 Kotlin script it **cannot be resolved**. Use a string template |

**Run your build script at least once on the version you are actually going to use.**
