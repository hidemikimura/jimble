---
title: Testing
summary: Start a real server and hit it. Keep the DB tests separate
section: Development
order: 4
---

# Testing

jimble has **no test-only module and no test annotations.**
You write tests with JUnit 5 and `java.net.http.HttpClient`, and nothing else.

That is the whole toolbox.

| What to do | What to use |
| --- | --- |
| Start a server | `JimbleServer.start(app, 0)` (**port 0 takes whatever is free**) |
| Stop it | `server.stop()` |
| Send HTTP | the JDK's `HttpClient` |
| Swap in configuration | `Conf.replace(config)` / `Conf.reload()` |
| Use the DB | `DBUtil.load(...)` / `Migration.install()` / `DBUtil.stop()` |

## Start it, hit it

**This is the basic shape.** Helidon really starts, so you check routing,
filters, error handling and `Content-Type` end to end.

```java snippet=test-server
```

Passing `0` for the port is the point. Fix the port and
**it fails only when another test happens to run at the same time.**

```java
@Test
void hello () throws Exception {
	assertEquals("hello", request("GET", "/hello").body());
}
```

> [!TIP]
> Give the `HttpClient` a `CookieManager` and session, CSRF and flash all work.
> To assert on a redirect, set `followRedirects(HttpClient.Redirect.NEVER)`
> (the default follows it, and you never see the 302).

## Hitting the router without HTTP

You can call `Dispatcher` directly, without starting a server. It is faster, and
in exchange you check nothing on the Helidon side (streamed sends, compression).

```java snippet=test-dispatcher
```

> [!WARN]
> `Fakes` — `FakeRequestSource` / `FakeResponseSink` in the code above —
> **lives inside jimble's own tests and is not published.**
> To do the same from your application, implement `RequestSource` / `ResponseSink`
> yourself.
> That rarely pays for the effort, so **start with "Start it, hit it" above.**

## Tests that use the DB

### Keep them separate

Tag the tests that need a DB and **leave them out of the everyday build.**
That keeps the build from going red where there is nothing to connect to — CI,
or a fresh clone.

```java
@Tag("db")
class PostIntegrationTest { ... }
```

Add this to your application's `build.gradle.kts`.

```kotlin
tasks.withType<Test>().configureEach {
	useJUnitPlatform { excludeTags("db") }
}

tasks.register<Test>("dbTest") {
	group = "verification"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform { includeTags("db") }

	// switch the configuration over to dbtest
	systemProperty("jimble.env", "dbtest")

	// run every time, so it never sees what an earlier test TRUNCATEd
	outputs.upToDateWhen { false }
}
```

Put `conf/application.dbtest.conf` in place and write the connection there.
**The `include` on the first line is what reads the shared configuration**
([Configuration](./config)).

```conf
include "application.conf"

db {
	main {
		url      = "jdbc:mariadb://127.0.0.1:3306/app_test"
		url      = ${?TEST_DB_URL}
	}
}
```

### Bringing it up

```java snippet=test-db-app
```

The order is fixed.

1. **`Conf.reload()`** — the environment (`jimble.env`) is settled the first time
   the configuration is read, so read it again
2. `Migration.install()` — create the tables
3. `DBUtil.load(...)` — connect
4. `JimbleServer.start(app, 0)`

Cleanup is `DBUtil.stop()` in `@AfterAll`, and `TRUNCATE TABLE ...` before each
test.

> [!TRAP]
> **There is no per-test automatic rollback.**
> What you write stays written. Delete it in `@BeforeEach`.

### Do not run them in parallel

> [!WARN]
> Run DB tests in parallel and **one test's `TRUNCATE` deletes another test's rows.**
> It only ever surfaces as "it fails now and then", which costs you a long time
> to track down.
> A Gradle shared service (`maxParallelUsages = 1`) serializes them for certain.

## Swapping in configuration

```java
// layer over the existing configuration (this is the usual one)
Conf.replace(ConfigFactory
	.parseString("upload.max_file_size = 10")
	.withFallback(Conf.conf().config()));
```

> [!TRAP]
> `Conf.replace` is **global, and there is no way back.**
> A test that swaps configuration in has to call `Conf.reload()` in `@AfterEach`.
> Forget it and **only the tests that run after it fail** — and which ones those
> are changes with the execution order.

## Running jimble's own tests

```bash
./gradlew build                 # the ones that need no DB
./gradlew :jimble-db:dbTest     # the ones that use a real MySQL / MariaDB
./gradlew :jimble-db:pgTest     # the same tests against a real PostgreSQL
```

`dbTest` and `pgTest` run **exactly the same tests.**
The only difference is which configuration file they read
(`application.dbtest.conf` / `application.pgtest.conf`), and the latter carries
`product = "postgresql"`.
**Not one line of the test code changes.**

You can point them somewhere else with environment variables.

```bash
JIMBLE_TEST_DB_URL="jdbc:mariadb://127.0.0.1:3306/jimble_test" \
JIMBLE_TEST_DB_USER=jimble \
JIMBLE_TEST_DB_PASSWORD=jimble \
  ./gradlew :jimble-db:dbTest

JIMBLE_TEST_PG_URL="jdbc:postgresql://127.0.0.1:5432/jimble_test" \
JIMBLE_TEST_PG_USER=jimble \
JIMBLE_TEST_PG_PASSWORD=jimble \
  ./gradlew :jimble-db:pgTest
```

These are for **jimble's own tests**.
The samples under `examples/` read different names: **`JIMBLE_SAMPLE_DB_*` / `JIMBLE_SAMPLE_PG_*`**.

> [!NOTE]
> **The names are separate because mixing them causes real damage.**
> The samples used to read `JIMBLE_TEST_*` too, so setting these variables sent
> <b>the samples' migrations into `jimble_test`</b>, mixing up the history and
> <b>failing jimble's own migration tests</b> — in a way that looks like a framework bug.

> [!NOTE]
> The skeleton `jimble new` creates does not carry the test scaffolding yet
> (the JUnit dependency and `dbTest`).
> Add it by hand, as above.
