---
title: Hot reload
summary: ./gradlew jimbleRun. Make a change and the next request picks it up
section: Development
order: 3
---

# Hot reload

```bash
./gradlew jimbleRun
```

## What is going on

```
browser ──▶ proxy (9000) ──▶ application (9100, same JVM as Gradle)
```

1. `jimbleRun` puts a proxy on port 9000
2. Behind it, it starts the application on port 9100 (`port + 100`)
3. When it sees a source file change, it marks it
4. **On the next request**, it builds and swaps the application
5. Once the swap is done, the request goes through

**It does not build the moment you save.** It waits for a request.
That is so a run of quick saves does not kick off build after build.

## It runs in the same JVM as Gradle

**The application runs inside the same process as Gradle.**
Debug `jimbleRun` from your IDE and **you stop on breakpoints inside the
application itself.** There is no separate remote debugger to attach.
Change only a method body and IntelliJ's HotSwap swaps it in as it is.

To swap the application, **it builds a new class loader and calls `main` again.**
The old one is stopped first, then dropped.

### Gradle's JVM has to be Java 25

Because everything runs in one JVM, **the Gradle daemon's JVM runs your
application — not the toolchain.** An old one cannot start it.
It checks at startup and, if the JVM is too old, stops and tells you what to fix.

- IntelliJ: Settings &gt; Build, Execution, Deployment &gt; Build Tools &gt; Gradle &gt; **Gradle JVM**
- `gradle.properties`: `org.gradle.java.home=<path to Java 25>`

### Hand over how to stop things

The process is never killed, so **whatever the application started stays running
unless you stop it.** A leftover scheduler or MQ worker then **runs twice over**
alongside the new one after the swap (the same job fires twice).

What jimble starts — the server, the scheduler, the DB pool — is already handed
over. Anything you start yourself, you write down.

```java
Shutdown.add("my worker", worker::stop);
```

If a thread did not stop, it names it (once per name).

```
[jimbleRun] 止まらなかったスレッドがあります: my-worker
  同じ JVM で動かしているので、止めないものは残ります。
  ...
```

> In English: "there are threads that did not stop: my-worker — it runs in the
> same JVM, so anything you do not stop stays running."

### Do not call `System.exit()`

If your application's `main` calls `System.exit()`, **Gradle goes down with it.**
Same process, nothing to stop it (there is no `SecurityManager` from Java 24 on).
A class that uses `System.exit()` — a batch entry point, say — must not be the
`mainClass` for `jimbleRun`.

### This is not production

The swap happens inside one JVM, so `static` state sometimes survives and
sometimes does not.
**You can get "it only fails after a reload" and "it only passes after a reload".**
When something looks wrong, stop `jimbleRun` and start it again.

### The serialization filter is switched off

On startup, helidon installs a **JVM-wide Java serialization filter**
(a guard that refuses to read back any class not on its allow-list).
We run in the same JVM, so that filter lands **on the Gradle daemon.**
It can only be set once and cannot be removed, so **it stays in the daemon
after you stop the app.**

Once it is there, the next build dies on it.

```
Couldn't populate class org.gradle.api.services.BuildServiceParameters$None
> filter status: REJECTED
```

It shows up as **"the first run works; stop it, run again, and it will not
start."** Recreating the daemon (Gradle sync in the IDE, `gradle --stop`)
fixes it, which makes the cause hard to spot.

To avoid this, `jimbleRun` sets
`helidon.serialFilter.missing.action` to `IGNORE` before it starts the app.
**It is only switched off while `jimbleRun` is running.** In production the
app has its own JVM, so helidon installs the filter as usual.

If you set `-Dhelidon.serialFilter.missing.action=...` yourself, that wins
and we leave it alone.

## When the build fails

The error comes out in the browser as it is. You do not have to go look at the
terminal. Fix it, reload, and the build runs again.

**The proxy stays up even while the application is down.** The port does not change.

## Settings

```kotlin
jimbleRun {
	// this is the only one you need. everything else has a default
	mainClass = "my_blog.App"

	port    = 9000              // the proxy (the one the browser talks to)
	appPort = 9100              // the application. defaults to port + 100
	env     = "local"

	buildTasks      = listOf(":my-blog:classes")
	watchExtensions = listOf(".java", ".jte", ".conf")
	excludeDirs     = listOf("build")

	restartMode         = "on_request"   // on_request | immediate
	quietMillis         = 300
	startTimeoutSeconds = 60

	args = listOf()
}
```

Set `restartMode = "immediate"` and the build and the swap start the moment you
save. You wait less, but it runs on every save.

To watch more directories, use `watchDirs` (paths relative to the root).
The full property list is in [the Gradle plugins](./gradle).

## Things to watch out for

- **`jimbleRun` is for development.** In production you use `./gradlew run`, or
  run the jar you built directly
- **It runs in the Gradle daemon's JVM.** To change the heap, use
  `org.gradle.jvmargs` in `gradle.properties` (there is no `jimbleRun { jvmArgs }`)
- Every swap leaves behind the memory of each thread that did not stop.
  Leave it up all day and it gets heavy — restart it
- If `gradlew` is there but `gradle/wrapper/gradle-wrapper.properties` is not,
  the wrapper is judged broken and the system `gradle` is used
