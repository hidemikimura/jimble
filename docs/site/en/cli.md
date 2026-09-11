---
title: The jimble command
summary: Installing it, and new / migrate / codegen
section: Development
order: 1
---

# The jimble command

```
使い方: jimble <コマンド>

  new <name>              プロジェクトの雛形を作る
  migrate                 未適用のマイグレーションを適用する
  codegen <source root>   テーブル定義のコードを生成する（例: src/main/java）
  version                 版を表示する
```

The help text prints in Japanese. What each command does:

| Command | What it does |
|---|---|
| `new <name>` | Write a project skeleton |
| `migrate` | Apply the migrations that have not been applied yet |
| `codegen <source root>` | Generate the table definition code (`src/main/java`, say) |
| `version` | Print the version |

## Installing it

**Build it from the jimble repository and put it on your PATH.**

```bash
git clone https://github.com/hidemikimura/jimble.git
cd jimble
./gradlew :jimble-cli:installDist
export PATH="$PWD/jimble-cli/build/install/jimble/bin:$PATH"
```

> [!NOTE]
> **The CLI is on Maven Central, but it will not run as it is** — the
> `io.jimble:jimble-cli` jar has no `Main-Class`, so `java -jar` does nothing.
> Build it from the repository.
>
> **You can start without the CLI.** Everything `jimble new` writes is spelled out in
> "Writing it from scratch" on the [Gradle plugins](./gradle) page, and `migrate` and
> `codegen` also run as Gradle tasks (`./gradlew migrate` / `./gradlew codegen`).

```bash
$ jimble version
jimble 0.4.1-SNAPSHOT
```

If you would rather not type that every time, add the `export` above to your
`~/.zshrc`, or make a link.

```bash
ln -sf "$PWD/jimble-cli/build/install/jimble/bin/jimble" /usr/local/bin/jimble
```

> [!WARN]
> **`java` has to resolve to Java 25.**
> The launch script uses `JAVA_HOME`, or `java` from your PATH, so an older Java
> gives you `UnsupportedClassVersionError`.
> Building the project it generates also needs **Gradle 9 or later**.

> [!TRAP]
> **Change jimble and you have to run `installDist` again.**
> What runs is the copy under `build/install/`, so
> **editing the source alone changes nothing.**

### Using a jimble you built yourself

The `build.gradle.kts` that `jimble new` writes points at **the version the CLI
was built from**. If that version is not published (`0.4.1-SNAPSHOT`, say),
**publish it locally first**.

```bash
./gradlew publishToMavenLocal
./gradlew -p gradle-plugin publishToMavenLocal
```

The skeleton's `settings.gradle.kts` already looks at `mavenLocal()` first.
If you only ever use published versions, you do not need any of this.

## jimble new

```bash
jimble new my-blog
```

What you get:

```
my-blog/
	settings.gradle.kts
	build.gradle.kts
	gradle.properties
	.gitignore
	README.md
	src/main/java/myblog/App.java
	src/main/jte/myblog/index.jte
	conf/application.conf
	conf/logback.xml
	conf/migration/my_blog/001_create_note.sql.example
```

**It does not create empty directories.** Create them when you need them.
Anything ending in `.example` becomes live once you rename it.

Rules for the name:

- Lowercase letters, digits, `-` and `_`
- It starts with a letter
- **The package name is the name with only letters and digits kept** (`my-blog` → `myblog`)
- **The DB name is the name with everything else turned into `_`** (`my-blog` → `my_blog`)
- Java reserved words, and names of directories that already exist, are refused

The error message tells you why.

> [!NOTE]
> **What comes out needs neither a DB nor Redis.** Build it and start it without
> touching anything.
> When you do want them, you uncomment the lines already sitting in
> `build.gradle.kts` and `application.conf`.

## What to do next

```bash
cd my-blog
gradle wrapper --gradle-version 9.7.1   # set up the Gradle wrapper (once)
./gradlew run           # start it
./gradlew jimbleRun     # start it with hot reload
```

```
$ curl http://localhost:9000/hello
{"message":"hello, jimble"}
```

## migrate / codegen

**Inside a project, you call these through Gradle.**

```bash
./gradlew migrate
./gradlew codegen
```

Add the `io.jimble.db` plugin and `migrate → codegen` is wired in front of
`compileJava` for you.

What they do is in [Migrations and code generation](./codegen); the tasks and
their settings are in [the Gradle plugins](./gradle).

> [!TRAP]
> **Running `jimble migrate` / `jimble codegen` bare does not work.**
> Both of them **look up the configuration (`application.conf`) and the migration
> SQL on the classpath**, so your application's resources have to be visible.
> Where Gradle is not available — CI, production — add the jar to the classpath
> and call it there.
>
> ```bash
> java -cp app.jar io.jimble.db.cli.JimbleDbCli migrate
> ```

## Checking the version

```bash
jimble version
```

It reads `Implementation-Version` out of the jar.
**The jimble version the skeleton points at is this same one** — nothing is
written by hand.
