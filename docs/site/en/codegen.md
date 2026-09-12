---
title: Migrations and code generation
summary: How SQL is applied, what classes are generated, and when each thing runs
section: Database
order: 6
---

# Migrations and code generation

**The schema comes first, the code second.** You write SQL and apply it, and the
table definition classes are built from that.

| When | Locally | Everywhere else (CI, staging, production) |
| --- | --- | --- |
| At build time | `migrate` → `codegen` → `compileJava` | **`compileJava` only** |
| At startup | nothing | **apply any migration that has not been applied** |

> [!NOTE]
> **Commit the generated code to the repository.** Otherwise you cannot build in
> an environment with no DB.
> `codegenCheck` in CI watches for drift.

## Migrations

Put your SQL in `conf/migration/<schema name>/`.

```sql
# --- !Ups

CREATE TABLE `note` (
	`id`         BIGINT UNSIGNED AUTO_INCREMENT COMMENT 'ID' PRIMARY KEY,
	`title`      VARCHAR(200) NOT NULL COMMENT 'Title',
	`created_at` DATETIME     NOT NULL COMMENT 'Created at'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Note';

# --- !Downs

DROP TABLE `note`;
```

```bash
./gradlew migrate
```

> [!NOTE]
> **That is MySQL DDL.** Migration SQL is **handed to the database as it is**, so write
> PostgreSQL syntax if that is what you are on (`BIGINT UNSIGNED AUTO_INCREMENT` becomes
> `bigserial`, `DATETIME` becomes `timestamp`, no backticks). Unlike the SQL builder,
> **nothing here absorbs the dialect for you.** To ship both, you can
> [split them per product](#splitting-sql-per-product).

- File names are up to you. They are applied in **the natural order of the names**
  (a `001_` prefix makes it obvious)
- `<schema name>` is `scheme` from the configuration (or the data source name if
  that is missing)
- **They are looked up from the classpath.** Add `conf/` to your resources
  ([Configuration](./config))

### Splitting into single statements

One file can hold several statements. They are split on `;`, but **never on a
`;` inside a string, an identifier or a comment.**

Where those start and end **differs by product**, so they are read with the
rules of the product you are connected to.

| Written | MySQL | PostgreSQL |
|---|---|---|
| the `\` in `'a\'b'` | an escape | **an ordinary character** (an escape only in `E'a\'b'`) |
| `# ...` | a line comment | not a comment |
| `1--2` | not a comment (`--` needs a space after it) | a line comment |
| `` `a` `` | quotes an identifier | not a quote |
| `$$ ... $$` | ordinary characters | a string (function bodies, `DO` blocks) |
| `/* /* */ */` | closes at the first `*/` | can nest |

> [!TRAP]
> **Get the product's rules wrong and the split changes silently.**
> Read `insert into t values ('c:\');` with MySQL's rules and the `\'` looks
> like an escape, so **the string never closes** — every statement after it is
> glued onto this one.

A fragment that is nothing but a comment is dropped (MySQL fails it with "Query
was empty"). MySQL's and MariaDB's `/*!40101 ... */` and `/*M!100301 ... */` are
executable comments, so they are kept.

> [!WARN]
> **If not one statement can be taken out of it, that is a failure.** Letting
> "it was all comments" pass as success means a down that leaves **the tables in
> place and deletes only the history**.

### The record, and failures

| Table | What is in it |
| --- | --- |
| `migration` | The SQL applied, its hash, and its state (`complete` / `up_error` / `down_error`) |
| `migration_history` | **A run record per statement** (kind, SQL, success or failure, timestamp) |

A failure **stops there, and the application does not start.** That is so it
never takes requests in a half-migrated state.
The failure stays in `migration` as `up_error`, and the next run redoes
down → up.

> [!WARN]
> **DDL is not rolled back.** MariaDB / MySQL commit implicitly on
> `CREATE TABLE` and friends.
> Put several DDL statements in one file and it stops **with part of it applied.**

### Do not rewrite SQL that has already been applied

The hash is checked, so **rewriting it after it has been applied fails.**

```
適用済みのマイグレーションが書き換えられています: 003_xxx.sql
（down が無効なため巻き戻せません。新しい SQL ファイルを追加してください）
```

> In English: "a migration that has already been applied was rewritten: 003_xxx.sql
> (down is off, so it cannot be rolled back — add a new SQL file instead)."

Set `migration.down = true` (the default is `false`) and it redoes down → the
new up.
**There is no command to roll back by hand.** down runs in exactly two cases:
the file is gone, or the hash changed.

### Splitting SQL per product

The same DDL will not do for both MySQL and PostgreSQL (`AUTO_INCREMENT` versus
`bigserial`, `ENGINE=InnoDB`, how column comments are written).
Split them **by filename suffix.**

```
conf/migration/blog_example/
	001_create_post.mysql.sql        <- mysql / mariadb only
	001_create_post.postgresql.sql   <- postgresql only
	002_add_status.sql               <- no suffix = both
```

- The suffix is the same name you can write in `db.<name>.product`
  (`mariadb` counts as `mysql`)
- **A file with no suffix is applied on every product.** SQL that works on both
  does not need splitting
- Files that were skipped are named in the startup log

```
マイグレーション: 他の製品向けを飛ばしました（いまは postgresql）: 001_create_post.mysql.sql
```

> In English: "migration: skipped files for other products (currently postgresql): ...".

What has been applied is remembered **by filename**, so
`001_create_post.mysql.sql` and `001_create_post.postgresql.sql` are separate
records. **A file that is present is never treated as "gone"**, not even a file
for the other product, so it is never downed.

Two files for the same version that both run on the current product is an error
(`001_x.mariadb.sql` and `001_x.mysql.sql` both run on MySQL).

> [!TRAP]
> **Renaming a file that has already been applied makes it a different file.**
> Rename `001_create_post.sql` to `001_create_post.mysql.sql` and the same SQL
> runs a second time, giving you `Table 'post' already exists`.
>
> That is detected by name and stopped, with the fix printed. The comparison is
> on the **name with the product suffix removed**, so it is still found when the
> split halves have different contents.
>
> ```
> マイグレーション SQL の名前が変わったようです（いまは mysql）:
>   001_create_post.sql → 001_create_post.mysql.sql
> 履歴の名前も変えてください。
>   UPDATE migration SET name = '001_create_post.mysql.sql' WHERE name = '001_create_post.sql';
>   UPDATE migration_history SET name = '001_create_post.mysql.sql' WHERE name = '001_create_post.sql';
> ```
>
> > In English: "the migration SQL looks renamed — rename it in the history too."
>
> **When the contents changed as well, `hash` is included in the `UPDATE`** (that
> version is already applied, so the record is corrected rather than re-applied).
> The printed SQL belongs to **that database's product**; on the other product's
> database, read it as that product's filename.

> [!WARN]
> **It stops when no file is left for the current product.**
> Rename `001_create_post.sql` to `001_create_post.postgresql.sql` only, and on a
> MySQL database that file will never be read again. Rather than downing it
> silently, jimble makes you choose: add the `mysql` file, or delete the history row.

> [!WARN]
> **Where an `ALTER TABLE` puts a new column differs by product.**
> MySQL takes `AFTER body`; PostgreSQL always appends.
> The column order in the generated classes is the **physical order**, so the
> same schema can produce a different order on a different product (the types,
> nullability and comments are the same).
> **Generate on the product you lead with**, and run `codegenCheck` there too.

### When several servers start at once

The row in `db_lock` is taken with `FOR UPDATE` before anything is applied, so
**only one machine runs.** The one that could not wait long enough fails to start
(`migration.lock_timeout`, 60 seconds by default).

> [!NOTE]
> The lock wait timeout is set **for that session only** (`SET SESSION`).
> The server-wide setting is left alone.

### Migrations written in Java

A migration you cannot express in SQL (repacking existing data, for instance)
goes in a class.

```java
CodeMigration.add(new V20260901FillUserKana());
Migration.install();
```

They run once each, in `versionYyyyMmDd()` order, and the result is kept in
`migration_code`.
**The classpath is not scanned** (principle 2). Write one, then `add` it.

> [!TRAP]
> **The CLI's `migrate` does not run code migrations.**
> The registration lives in your application code, so they only run **when the
> application starts.**

## Code generation

```bash
./gradlew codegen
```

From the DB schema, three kinds of file are generated per data source.

| Generated | Where it goes | What it is for |
| --- | --- | --- |
| Schema class | `db/<data source>/BlogExample.java` | `BlogExample.db()`, the table list |
| Table class | `db/<data source>/table/post/Post.java` | The `Column`s, such as `Post.id` |
| Typed accessors | `db/<data source>/table_data/post/AbstractPostData.java` | `data.title("...").published(true)` |

```java
public class Post extends Table {

	/* Post ID */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	private static final List<Column> COLUMNS = List.of(id, title, body, published, created_at);
}
```

The column list is **fixed at generation time** (nothing is reflected on at
runtime).

> [!WARN]
> **The output directory is rebuilt from scratch every time.**
> Put a hand-written file under `db/<data source>/` and **the next `codegen`
> deletes it.**
> Generated files carry a marker at the top, too.

```java
/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */
```

> In English: "jimble wrote this file (codegen). Do not edit it by hand — an edit
> is gone at the next codegen."

### Type mapping

| DB type | Java |
| --- | --- |
| `bigint` | `long` |
| `tinyint` / `bool` | **`boolean`** |
| `int` / `smallint` / `mediumint` | `int` |
| `decimal` / `float` / `double` | `double` |
| `datetime` / `date` / `timestamp` | `java.util.Date` |
| `varchar` / `text` and so on | `String` |
| `json` | `Data` |
| Everything else (`blob` / `enum` and so on) | `String` |

On PostgreSQL these are treated the same way.

| DB type | Java |
| --- | --- |
| `bigserial` | `long` |
| `serial` / `smallserial` | `int` |
| `numeric` / `real` / `double precision` | `double` |
| `boolean` | `boolean` |
| `jsonb` | `Data` |

> [!TRAP]
> **`tinyint` is `boolean` no matter what width you gave it.** If you are storing
> something other than 0/1 in a `tinyint(4)`, make it a `smallint`.
> **`decimal` becomes `double`**, so it is a poor fit for money.

### On PostgreSQL

Write `db.xxx.product = postgresql` and **the same `./gradlew codegen` works**
([Using the DB](./db)). Only the reading side changes; the shape of the
generated classes is the same.

There are a few places where it does not line up with MySQL.

| What it is | What happens |
| --- | --- |
| `bigserial` / `serial` | Read as auto-increment columns (the `nextval(...)` default is not emitted) |
| **Expression indexes** | Something like `create unique index ... (lower(code))` <b>does not count as a unique key</b>. The column cannot be recovered, so this avoids mistaking the remaining columns for a unique set |
| **Partial indexes** | Ones with a `where` <b>do not count as a unique key</b> either. They are only unique among the rows that match the condition |
| The generated `SchemaSQL` | Written in that product's DDL (comments come out separately, as `comment on ...`) |

> [!NOTE]
> **If the catalog cannot be queried, we fail.** Silently treating it as "zero
> tables" and deleting the generated code would hide the cause.

### Tables that are not generated

The tables jimble creates for itself (`migration` / `db_cache` / `session` /
`batch_*` and so on) are excluded automatically.
**The application names its own MQ queue tables**, so list those yourself.

```conf
codegen {
	package        = "db"
	exclude_tables = ["mq_blog", "mq_scheduler"]
}
```

> [!NOTE]
> Wildcards do not work (you cannot write `mq_*`). List them one by one.

## Configuration

```conf
migration {
	on_startup            = "auto"   # auto | true | false. auto applies them everywhere but locally
	down                  = false    # whether to roll back
	lock_timeout  = 1m
	resource_dir          = "migration"
}

codegen {
	package        = "db"
	exclude_tables = []
}
```

## From the CLI

Where Gradle is not available (CI, production), the CLI does the same things.

```bash
java -cp app.jar io.jimble.db.cli.JimbleDbCli migrate
java -cp app.jar io.jimble.db.cli.JimbleDbCli codegen src/main/java
```

Both the configuration and the SQL are looked up **from the classpath**.
The Gradle tasks are listed in [the Gradle plugins](./gradle).
