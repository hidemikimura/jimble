---
title: Gradle plugins
summary: Tasks and settings for io.jimble.db / jte / run
section: Development
order: 2
---

# Gradle plugins

There are three. **Add only the ones you need.**

| ID | What it does | Main tasks |
| --- | --- | --- |
| `io.jimble.db` | Runs migrations and code generation in front of `compileJava` | `migrate` / `codegen` / `codegenCheck` |
| `io.jimble.jte` | Turns `src/main/jte` into Java in front of `compileJava` | `generateJte` |
| `io.jimble.run` | Hot reload for development | `jimbleRun` |

```kotlin
plugins {
	application
	id("io.jimble.jte") version "2.1.3"
	id("io.jimble.run") version "2.1.3"
	id("io.jimble.db")  version "2.1.3"
}
```

**They are not on the Plugin Portal.** They come from Maven Central, so write the
repository into `settings.gradle.kts` (the `jimble new` skeleton already has it).

```kotlin
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
```

> [!NOTE]
> The plugins run **inside the Gradle daemon**, so their bytecode targets Java 17.
> That is separate from your application, which is Java 25.
> **Gradle 9 or later** is required.

## Writing it from scratch

If you are not using `jimble new`, these two files are enough.
**What follows was actually run** (`gradle build` chains
`migrate` → `codegen` → `compileJava`, and `gradle run` starts the app).

```kotlin
// settings.gradle.kts
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = "memo"
```

```kotlin
// build.gradle.kts
plugins {
	application
	id("io.jimble.jte") version "2.1.3"   // if you use src/main/jte
	id("io.jimble.run") version "2.1.3"   // if you want hot reload
	id("io.jimble.db")  version "2.1.3"   // if you use a database
}

repositories {
	mavenCentral()
}

// jimble is built for Java 25. Without this the dependencies will not resolve
java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencies {
	implementation("io.jimble:jimble-web:2.1.3")

	testImplementation(platform("org.junit:junit-bom:5.11.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

// Add conf/ to the resources. Config and migrations are both read from there ([Configuration](./config))
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}

application {
	mainClass = "memo.App"
}

jimbleRun {
	mainClass = "memo.App"
}
```

> [!TRAP]
> **The toolchain block is required.** Without it you stop at dependency resolution.
>
> ```
> Dependency resolution is looking for a library compatible with JVM runtime version 21,
> but 'io.jimble:jimble-web:2.1.3' is only compatible with JVM runtime version 25 or newer
> ```

## Which artifact to depend on

**You do not write down what comes along for the ride** (add `jimble-web` and you get
`jimble-db` and `jimble-util` too).

| Add this | What it is | What comes with it |
| --- | --- | --- |
| `io.jimble:jimble-web` | Web (Router / Context / Session / templates) | `jimble-db` → `jimble-util` → `jimble-core` |
| `io.jimble:jimble-db` | The database layer alone (for a batch or a CLI) | `jimble-util` → `jimble-core` |
| `io.jimble:jimble-mq` | MQ | `jimble-db` |
| `io.jimble:jimble-batch` | Batches and the DB scheduler | `jimble-db` / `jimble-mq` |
| `io.jimble:jimble-batch-manager` | The batch admin screen | `jimble-web` / `jimble-batch` |
| `io.jimble:jimble-mcp` | The MCP server | `jimble-web` |
| `io.jimble:jimble-otel` | Sends traces to OpenTelemetry (**adds 0.9MB, but only if you add it**) | `jimble-core` |

**You do not need a JDBC driver** — `jimble-db` carries MySQL / MariaDB and PostgreSQL.

## io.jimble.db

```bash
./gradlew migrate       # apply the SQL that has not been applied
./gradlew codegen       # build the table definition classes (after migrate)
./gradlew codegenCheck  # check the committed generated code against the schema
```

What they do is in [Migrations and code generation](./codegen).

```kotlin
jimble {
	sourceRoot   = "src/main/java"   // where generated code goes
	env          = "local"           // passed to the CLI as -Denv=...
	autoGenerate = true              // wire it in front of compileJava?
}
```

| Property | Default |
| --- | --- |
| `sourceRoot` | `src/main/java` |
| `env` | `-Pjimble.env` → environment variable `ENV` → `local` |
| `autoGenerate` | `-Pjimble.autoGenerate` → **true when `env == "local"`** |

So `migrate → codegen → compileJava` is wired **only locally**.
It is not wired in CI, which compiles straight from the committed generated code.

```bash
./gradlew build -Pjimble.autoGenerate=true    # when you want it wired in CI too
```

> [!NOTE]
> `migrate` and `codegen` **run every time** (they never go up-to-date).
> Gradle cannot see the state of the DB.

> [!TIP]
> Both of them are nothing but a call into the CLI (`io.jimble.db.cli.JimbleDbCli`).
> **No logic lives on the Gradle side**, so in CI or production you can do the
> same thing with the CLI directly.

> [!WARN]
> **This project's own classes are not on that classpath** (resources and
> dependencies only). Put them there and you get a
> `compileJava → codegen → compileJava` cycle.
> The SQL and the configuration are both resources, so this is enough.

When `codegenCheck` finds drift, it fails and says this.

```
コミットされている生成物がスキーマと一致しません（2 件）。codegen を実行してコミットしてください。
  生成物が古いです: db/blog_example/table/post/Post.java
  生成物が足りません: db/blog_example/table/tag/Tag.java
```

> In English: "The committed generated code does not match the schema (2 files).
> Run codegen and commit the result." — then, per file, "generated code is stale"
> and "generated code is missing".

## io.jimble.jte

The `.jte` files under `src/main/jte` are **turned into Java**. `compileJava`
compiles them, so **a type error in a template fails the build**
([Templates](./view)).

| Task | Input | Output |
| --- | --- | --- |
| `generateJte` | `src/main/jte` | `build/generated/sources/jte/main/java` |
| `generateTestJte` | `src/test/jte` (**not configurable**) | `build/generated/sources/jte/test/java` |

```kotlin
jte {
	sourceDirectory       = "src/main/jte"
	packageName           = "gg.jte.generated.precompiled"
	contentType           = "Html"    // Html | Plain
	trimControlStructures = true
	htmlCommentsPreserved = false
}
```

> [!WARN]
> **Do not change `packageName`.** It is lined up with jte's own default.
> Move it and the templates are not found at runtime.

> [!NOTE]
> The output directory is **wiped completely before every run.**
> Leave the generated code for a deleted template behind and **something you
> thought you deleted compiles and ships in the jar.**

Only the plugin carries the compiler (`gg.jte:jte`).
The application's runtime classpath gets `jte-runtime` and nothing else
(requirement D-27).

## io.jimble.run

```bash
./gradlew jimbleRun
```

How it works and what to watch out for is in [Hot reload](./hot-reload).
This page is the list of settings.

```kotlin
jimbleRun {
	mainClass = "my_blog.App"        // required
}
```

| Property | Default | What it decides |
| --- | --- | --- |
| `mainClass` | **none (required)** | The class that has `main` |
| `port` | `9000` | The proxy port the browser talks to |
| `appPort` | `port + 100` | The port the application listens on |
| `env` | `"local"` | Passed through as `jimble.env` |
| `buildTasks` | `[":classes"]` | Tasks to run on a change |
| `watchDirs` | none | Extra directories to watch (relative to the root) |
| `excludeDirs` | none | Directories not to watch |
| `watchExtensions` | `.java .jte .html .js .css .conf .xml .properties .yml .sql` | Extensions to watch |
| `restartMode` | `"on_request"` | `on_request` (on the next request) / `immediate` (on save) |
| `quietMillis` | `300` | How long before the changes count as settled |
| `startTimeoutSeconds` | `60` | How long to wait for startup |
| `args` | none | Arguments passed to `main` |

What gets watched is `src`, `conf` (if it exists), and `watchDirs`.

> [!TRAP]
> **A value `restartMode` does not accept fails the build.**
> Before, it silently fell back to `on_request`, so a typo left you with nothing
> but "I configured it and it does nothing".

> [!NOTE]
> There is **no `debug` property and no `jvmArgs` property.**
> The application runs in **the same JVM as Gradle**, so your IDE's debugger
> works as it is.
> To change the heap, use `org.gradle.jvmargs` in `gradle.properties`.

### When the build fails

Nothing stops. **The failure comes out in the browser.**

| What happened | Status |
| --- | --- |
| The rebuild failed | **503** (Gradle's output, verbatim) |
| The application cannot be reached | **502** |
| An exception inside `jimbleRun` | **500** |

Fix it, save, reload, and you carry on.

> [!NOTE]
> The build calls `gradlew` **in a child process** (a running build cannot invoke
> a task of the same project from inside itself).
> If `gradlew` is broken, it says why and falls back to `gradle` from your PATH.

## Tasks for AI assistants

**Every jimble plugin (`io.jimble.jte` / `io.jimble.run` / `io.jimble.db`) adds them.**

| Task | What it does |
| --- | --- |
| `./gradlew jimbleSkills` | Brings `.claude/skills/` in line with **the skills of the jimble version you use**. Places `AGENTS.md` / `CLAUDE.md` if they are missing |
| `./gradlew jimbleCheck` | Finds jimble's **known pitfalls** in your source and configuration, each with a fix and a link to read |

### jimbleSkills

`jimble new` **copies** the skills in, so upgrading jimble leaves the copies behind and your
application's AI keeps reading the old version's explanations. **Run it after you upgrade.**

```bash
./gradlew jimbleSkills
./gradlew jimbleSkills --overwrite   # also replace hand-edited skills and ones with no record
```

- **Hand-edited skills are never overwritten.** The hash of what was placed is recorded in
  `.claude/jimble-skills.properties`; a file that still matches (untouched since jimble placed it) is replaced,
  anything else is left alone
- **Skills with no record** (placed by a `jimble new` from before the record existed) are left alone too, since there is
  no telling whether they were edited. If you did not edit them, use `--overwrite`
- **Put your application's own rules in `AGENTS.md`, not in the skills.** An edited skill can no longer be brought up to date
- `AGENTS.md` / `CLAUDE.md` are **placed only if missing, never touched otherwise** (`CLAUDE.md` just says `@AGENTS.md`)

### jimbleCheck

It only looks for things that **go wrong silently at run time** (anything that fails at startup and tells you
how to fix it is left out). Any `ERROR` fails the task; `WARN` alone does not.

| Rule | Level | What it looks for |
| --- | --- | --- |
| J101 | ERROR | `context.request().getString(...)` and friends (`Request` is not a `Data`; go through `bodyAll()`) |
| J201 | WARN | `${ENV}` without `?` in configuration (fails at startup wherever the variable is not set) |
| J202 | ERROR | The same key written again after its `${?ENV}` line (the environment variable never wins) |
| J301 | ERROR | `Migration.install()` after `DBUtil.load(...)` (migrations never run) |
| J302 | ERROR | `BatchRegistry.sync` is used but `BatchTables.install` is called nowhere |
| J303 | ERROR | `BatchRegistry.sync` before `BatchRegistry.add` (every batch becomes `nothing`) |
| J304 | WARN | codegen is in use but MQ tables (`mq_scheduler` …) are missing from `codegen.exclude_tables` |
| J401 | ERROR | An outer `before(Auth::guard)` with `Remember.restore` inside an `Auth.REALM` block (401 every time despite remember-me) |
| J402 | WARN | `Remember.forgetAll(...)` with no `Auth.revoke` / `revokeOthers` in the same file (sessions on other devices stay logged in) |
| J501 | WARN | The skills are not from the jimble version you use (run `jimbleSkills`) |
| J701 | WARN | An empty `catch` in a file that uses transactions (`db.begin()` / `db.transaction(...)` / `TransactionException`) (reports success when nothing was committed) |

It also lists **1.x code that 2.0 removed, or whose type or meaning 2.0 changed** (all WARN; the rewrites are in [Moving to 2.0](./migrate-2)).

| Rule | What it looks for |
| --- | --- |
| J801 | `new DBTransaction(...)` / `DBTransaction.transaction(...)` (removed in 2.0) |
| J802 | The DB's `beginTransaction()` / `commitEndTransaction()` / `rollbackEndTransaction()` / `endTransaction()` (removed in 2.0) |
| J803 | `Router x = router.path("/x")` (removed in 2.0; use the block form `path("/x", r -> { ... })`) |
| J804 | A column's `subtract(...)` (removed in 2.0; it produced a division) |
| J805 | `Dsl.or(...)` / `Dsl.and(...)` (removed in 2.0; use `Dsl.anyOf` / `Dsl.allOf`) |
| J806 | `cookies().put(cookie)` (removed in 2.0; it did not sign) |
| J807 | `eq(null)` / `not(null)` (throws in 2.0; use `is_null()` / `is_not_null()`) |
| J808 | The string `"now()"` (just a string in 2.0; use `Dsl.now()`) |
| J810 | `long id = db.insert(...)` (2.0's `insert` returns nothing; use `insertKey`) |
| J901 | `Data row = db.select(...)` / `selectCached(...)` (2.0 returns `Optional<Data>`; code that takes it with `.orElse(...)` and the like is not listed) |
| J902 | `if (!db.execute(...))` / `boolean x = db.execute(...)` (2.0 returns a count, and a failure throws) |
| J903 | `isError()` (gone in 2.0) |
| J904 | Checking the return value of `DBUtil.load` / `DBLock.lock` / `DBLock.create` with `if (!...)` and the like (2.0 returns nothing, and a failure throws) |
| J905 | `RedisLock.tryLock(...)` (2.0 returns `Optional<RedisLockResult>`; code that takes it as an `Optional` is not listed) |
| J906 | `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey` (deprecated in 2.0) |
| J907 | `session().data().put(...)` (and `putData` / `remove` / `clear` / `putAll`; throws in 2.0) |

J8xx and J9xx are **always listed**. `--target=2.0` is still accepted so scripts from the 1.5 days keep working, but it does nothing.

> [!NOTE]
> Java is not parsed into a syntax tree; comments and strings are blanked out first. **Silence a false positive
> with `// jimble-check:ignore J101` on that line or the one above** (`# jimble-check:ignore J201` in configuration).
