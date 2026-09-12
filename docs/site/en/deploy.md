---
title: Running in production
summary: How to build the jar, and the command that starts it
section: Development
order: 8
---

# Running in production

## Build the jar

A fat jar (everything, dependencies included) is the easiest way.

```kotlin
plugins {
	application
	id("com.gradleup.shadow") version "<version>"
}

application {
	mainClass = "my_blog.App"
}

tasks.shadowJar {
	archiveFileName = "app.jar"
	mergeServiceFiles()   // so the META-INF/services entries (JDBC drivers and the like) survive
}
```

```bash
./gradlew shadowJar     # build/libs/app.jar
```

## Start the web app

```bash
java \
  -Djimble.env=prod \
  -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -jar app.jar
```

| What you pass | Why |
| --- | --- |
| `-Djimble.env=prod` | Reads `application.prod.conf` and makes `isProduction()` true |
| `-Dstdout.encoding` / `-Dstderr.encoding` | Leave them off and **Japanese log output is garbled** in a container with no `LANG` |

**`-Djimble.env` is a JVM argument.** As a program argument (`java -jar app.jar env=prod`)
it does nothing.

### Environment names

**The short forms work for the checks too.** What you wrote is used **only for the file name**.

| What you may write | Check | File read |
| --- | --- | --- |
| `local` / `dev` / `development` | `isLocal()` | `application.<what you wrote>.conf` |
| `staging` / `stg` / `stage` | `isStaging()` | same |
| `production` / `prod` / `prd` | `isProduction()` | same |

Case does not matter.

> [!TRAP]
> **A name that is not in the table matches none of the checks.**
> Misspell it as `producton` and both `isLocal()` and `isProduction()` are false.
> **A warning is logged once at startup** — silently falling back to `local` would let
> **a production machine call itself local** and keep running.
>
> For the record, **`-Djimble.env=prod` used to leave `isProduction()` false** (fixed in 0.6.1):
> the check only matched the literal `production`.

## Where the configuration is read from

**From inside the jar, and nowhere else.** (`conf/` is added to the resources, so it is in the jar.
See [Configuration](./config))

```
1. application.<env>.conf     ← if it exists, this one only
2. application.conf           ← if it does not
```

**Exactly one file is read.** What pulls the shared settings in is the
`include "application.conf"` on the first line of the per-environment file.
Forget to write it and the shared settings are gone, so **jimble names it at startup.**

**Configuration outside the jar is not read.** Change the configuration and you rebuild.
In exchange, the running jar and its configuration are one to one,
and "I fixed it but nothing changed" does not happen.
Values that differ per environment (the DB password, say) are passed as environment
variables, as in the next section.

**Which one it read is printed in the startup log** ([Configuration](./config)).

## Secrets go in environment variables

Write only `${?ENV_NAME}` in the configuration file, and pass the value in the environment.

```bash
export DB_PASSWORD='...'
export COOKIE_SECRET='...'
export CIPHER_KEY='...'
java -Djimble.env=prod -jar app.jar
```

## Run a batch job

Call the fat jar with the entry class named.

```bash
java \
  -Djimble.env=prod \
  -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -cp app.jar my_blog.batch.Batch \
  class=my_blog.batch.PostCleanupBatch
```

`class=` is the batch's class name. Any other `key=value` becomes an argument to the batch.

**Passing `env=prod` as an argument does not change the environment.**
The configuration has already been read before control reaches the batch entry point.
If the two disagree, it stops right there and says so.

```
env=prod と指定されていますが、いま動いているのは env=local です。
  env= では環境は変わりません（設定はここに来る前に読み終わっています）。
  JVM の引数で渡してください。
    java -Djimble.env=prod -cp app.jar <entry class> ...
```

> In English: "You passed env=prod, but the running environment is env=local.
> env= does not change the environment — configuration is read before this point.
> Pass it as a JVM argument instead."

## Run the scheduler

To drive batch jobs on cron, keep the scheduler resident.

```bash
java -Djimble.env=prod -cp app.jar my_blog.batch.Batch \
  class=my_blog.batch.SchedulerBatch
```

It runs until you stop it. Leave that to `systemd` or the like.
**You can also run it in the same process as the web app**, but if you are going to
put several machines side by side, settle how `batch.scheduler_id` is handled first.

## Shutting down

`SIGTERM` runs the shutdown path (server, scheduler, DB pool).
Avoid `kill -9` — it leaves transactions half finished.

## Check this once it is up

The configuration is printed around the first line of the startup log.
Look at it and check **that it is what you expected.**

```
jimble 構成: env=prod / session=db / cache=db / redis=あり / db=[main] / パスワード暗号化=あり
設定: jar:file:/opt/app/app.jar!/application.prod.conf, jar:file:/opt/app/app.jar!/application.conf
```

If it says `env=local`, then `-Djimble.env` did not reach it.
