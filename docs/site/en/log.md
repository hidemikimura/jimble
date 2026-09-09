---
title: Logging
summary: How to use Log, access logs, execution IDs, configuring logback
section: Development
order: 7
---

# Logging

```java
Log.info("started");
Log.warn("configuration is missing: %s".formatted(key));
Log.debug("not found");
Log.error(cause, "could not save: id=%d".formatted(id));
```

> [!WARN]
> **`{}` placeholders do not work.** This is not SLF4J's format, so write
> `Log.info("id={}", id)` and you get a literal `{}` in the output.
> Build the string with `String.formatted(...)`.

> [!NOTE]
> With `Log.error(cause, "a description")`, **the message is the exception's.**
> The description you wrote goes into the structured data, and the bundled encoder
> lays them out together on the first line.
>
> ```
> [2026-09-07 13:12:19]-[main] error could not save / java.lang.IllegalStateException: cannot write
> ```

## Logger names

| Logger | What comes out of it |
| --- | --- |
| `access` | The access log (one line per request) |
| `access.bot` | The bot access log |
| `error` | `Log.error(...)` |
| `io.jimble.util.log.Log` | `Log.info` / `warn` / `debug` / `trace` |
| Any name you like | Chosen with `Log.appInfo("my.audit", ...)` and friends |

> [!TRAP]
> **The class that called `Log.info` and the rest does not become the logger name.**
> It is always `io.jimble.util.log.Log`.
> To split by purpose, use `Log.appInfo("a name", ...)`.

## What is in every log line, always

| Key | What it holds |
| --- | --- |
| `request_id` | The execution ID (`-` outside a request) |
| `sql_execute_count` | How many SQL statements this request issued |
| `sql_execute_time` | Their total time (milliseconds) |
| `local_info` | **The server's own** IP and host name |

The point is that **the SQL count and time are in every log line.**
N+1 is built to be something "you can see in the log" (requirement NF-O-02).

## The access log

**One line per request**, written at the end of it. **It is written even when no route matched** (404s are kept too).

| Key | What it holds |
| --- | --- |
| `method` / `path` / `query` | The request |
| `status` | The status code |
| `elapsed` | How long it took (milliseconds) |
| `matched` | Whether a route matched |
| `bot` | Whether it was judged a bot |

```json
{"logger_name":"access","level":"INFO","message":"GET /users/42 200",
 "request_id":"m1abcd-xyz-1","sql_execute_count":2,"sql_execute_time":4.0,
 "method":"GET","path":"/users/42","query":"","status":200,"elapsed":12.34,
 "matched":true,"bot":false}
```

> [!TRAP]
> **The client's IP and User-Agent are not in there.** `local_info` is the server's own.
> If you need them, add them with `Log.addFieldProvider(...)`.

### Splitting out the bots

With `server.bot_access_log` (default `true`) on, bot lines go to **`access.bot`**.
The judgement is a match against a User-Agent list (`crawler-user-agents`).

> [!TIP]
> Crawlers can be a sizeable share of the total. Split them out and
> **you can count human traffic on its own.** Set it to `false` to keep them together.

## The execution ID

It is `base-36 time-random-sequence`, and one is issued per request (and per WebSocket message, and per batch).
It is carried around in a `ScopedValue` — **MDC is not used.**

The time comes first, so **sorting the IDs sorts them by time.**

> [!NOTE]
> **It is not put in the response headers.**
> To make "find me the log for the error on this screen" work,
> write `context.response().setResponseHeader("X-Request-Id", context.executionId())` yourself in an `after`.

## SQL logging

The **count and time** of SQL statements are tallied automatically (the table above).

> [!WARN]
> **The SQL text itself and the bind values are not logged.**
> There is no slow-query threshold either.
> When you want them, look on the DB side (`general_log` / `slow_query_log`).

Set `log.db = true` and whatever the app writes with `DBLog.save(db, data)` goes into
the `db_log` table. **It is not a feature that records SQL automatically.**

## Configuring logback

jimble carries logback and the encoders, but **the configuration file is the app's.**
The `jimble new` skeleton creates `conf/logback.xml` (`conf/` goes into the jar).

```xml
<configuration>

	<!-- The form people read -->
	<appender name="console" class="ch.qos.logback.core.ConsoleAppender">
		<encoder>
			<pattern>%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n</pattern>
			<charset>UTF-8</charset>
		</encoder>
	</appender>

	<!-- Exceptions. Prints the stack trace indented (bundled with jimble) -->
	<appender name="error" class="ch.qos.logback.core.ConsoleAppender">
		<target>System.err</target>
		<encoder class="io.jimble.util.log.encoder.LogbackErrorEncoder"/>
	</appender>

	<!-- The form machines read. One JSON object per line (bundled with jimble) -->
	<appender name="json" class="ch.qos.logback.core.ConsoleAppender">
		<encoder class="io.jimble.util.log.encoder.LogbackJsonEncoder"/>
	</appender>

	<logger name="access" level="INFO" additivity="false">
		<appender-ref ref="json"/>
	</logger>

	<logger name="access.bot" level="INFO" additivity="false">
		<appender-ref ref="json"/>
	</logger>

	<logger name="error" level="ERROR" additivity="false">
		<appender-ref ref="error"/>
	</logger>

	<root level="INFO">
		<appender-ref ref="console"/>
	</root>

</configuration>
```

> [!TRAP]
> **`access.bot` is a child of `access`.** Drop the `additivity="false"` and
> the bot lines go to **both** (you double-count while thinking you are counting humans).

> [!WARN]
> Leave the configuration file out and you get logback's defaults, where
> **the access log and the app's own logs land in the same place, mixed together.**

To split by environment, pass `-Dlogback.configurationFile=conf/logback.prod.xml`
instead of using `logback.xml`.

## Catching it in tests

```java snippet=log-sink
```

`Log.sink(...)` **replaces the whole thing**. Do not forget `Log.resetSink()` in `@AfterEach`.

## Configuration keys

| Key | Default | What it does |
| --- | --- | --- |
| `server.bot_access_log` | `true` | Splits the bot access log out into `access.bot` |
| `log.db` | `false` | Enables `DBLog.save(...)` |

> [!NOTE]
> **There are no configuration keys for log level, destination, or format.** That is `logback.xml`'s job.

When Japanese comes out garbled, add `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`
([Running in production](./deploy)).
