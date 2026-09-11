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
	id("io.jimble.jte") version "0.3.0"
	id("io.jimble.run") version "0.3.0"
	id("io.jimble.db")  version "0.3.0"
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
	id("io.jimble.jte") version "0.3.0"   // if you use src/main/jte
	id("io.jimble.run") version "0.3.0"   // if you want hot reload
	id("io.jimble.db")  version "0.3.0"   // if you use a database
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
	implementation("io.jimble:jimble-web:0.3.0")

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
> but 'io.jimble:jimble-web:0.3.0' is only compatible with JVM runtime version 25 or newer
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
