---
title: Using the DB
summary: Configuring a data source, choosing a DB product, generating table definitions, and the shape of a select result
section: Database
order: 1
---

# Using the DB

## Configure it

Write your data source in `application.conf`.

```conf
db {
	blog_example {
		main     = true
		driver   = "org.mariadb.jdbc.Driver"
		url      = "jdbc:mariadb://127.0.0.1:3306/blog_example?useUnicode=true&characterEncoding=UTF-8"
		url      = ${?DB_URL}
		username = "blog"
		username = ${?DB_USER}
		password = ""
		password = ${?DB_PASSWORD}

		maximum_pool_size      = 10
		connection_pool_type = "hikari"
	}
}

codegen {
	package = "db"
}
```

**Do not put secrets in the file.** Write them as `${?ENV_NAME}` and the
environment variable wins when it is set. When it is not, the value from the
line above stays.

`jimble-db` ships drivers for both MySQL / MariaDB and PostgreSQL, so you
normally do not have to add one.

## Choose a DB product

Set `product` and **the SQL builder emits SQL for that product.**

```conf
db {
	blog_example {
		main     = true
		product  = "postgresql"
		driver   = "org.postgresql.Driver"
		url      = "jdbc:postgresql://127.0.0.1:5432/blog_example"
		username = "blog"
		password = "blog"
		password = ${?DB_PASSWORD}
	}
}
```

> [!TRAP]
> **Never write the `${?ENV}` line on its own.** When the variable is not set, **the key
> disappears entirely** (the form above is a pair: a default, then an override). Connect
> to PostgreSQL with the password gone and what comes back is
> `The server requested SCRAM-based authentication, but no password was provided.` —
> **which does not look like a configuration problem at all.**

## When the database does not exist yet

Write `create_database_sql` and it is run **once, after a failed connection**.

```conf
db {
	blog_example {
		...
		create_database_sql = "CREATE DATABASE blog_example"
	}
}
```

Leave it out and nothing happens, so **you do not need it if you created the database by
hand.** In production you normally connect as a user without that privilege, so this is
for development and CI.

| Value you can write | Product |
|---|---|
| `mysql` (default) / `mariadb` | MySQL 8.x / MariaDB |
| `postgresql` / `postgres` / `pgsql` | PostgreSQL 16 and later |

Leave it out and you get `mysql`. Write a value it does not know and
**startup fails**.

**Not one line of your application code changes.**

```java
// This one line becomes
//   MySQL      SELECT `post`.`id` AS `post__id` ... LIMIT ? OFFSET ?
//   PostgreSQL SELECT "post"."id" AS "post__id" ... LIMIT ? OFFSET ?
// depending on product
SQL.select().from(Post.instance()).where(Post.id.eq(1L));
```

Each data source can name a different product (MySQL for the main one,
PostgreSQL for reporting, and so on).

> [!NOTE]
> **Only MySQL (MariaDB) and PostgreSQL are supported.**
> An unknown name **fails at startup** — falling back to MySQL silently would send
> backtick-quoted SQL to an application written for PostgreSQL.
>
> **There is no hook for adding your own dialect** (`Dialect` is `sealed`). A dialect
> has 40-odd methods, one per SQL function, so **every function jimble adds would
> break an outside implementation.** If you need another product, ask for it to be
> added to the framework.

### What you cannot write for a given product

**What you cannot write raises `DialectException` at the point where the SQL is
built.** You never get as far as running it and failing on the product's syntax
error.

| Builder | PostgreSQL |
|---|---|
| `Dsl.match(...)` (full-text search) | Not available. `to_tsvector` tokenizes differently and scores differently, so we do not silently substitute it |
| `Dsl.dateFormat(col, "%Y-%m-%d")` | Not available. `to_char` uses a different format language, so substituting it would return **a different string instead of raising**. Format it on the Java side |
| `Dsl.jsonExtract(col, "$.a[0]")` | Arrays, wildcards and quoted keys are not available. Only the `$.a.b` form |

The other way round, **things that differ only in name we do line up silently**
(`RAND` / `RANDOM`, `IFNULL` / `COALESCE`, `TRUNCATE` / `TRUNC`, and so on).
`Dsl.concat(...)` becomes `||` on PostgreSQL (PostgreSQL's `concat()` swallows
NULL as an empty string, so swapping the name alone would change the result
compared with MySQL).

When you really do want the product's own syntax, `Dsl.freeSql(...)` is the way out.

### Things to watch

- **Spatial functions take their coordinates in a different order.** SRID 4326 on
  MySQL 8 is latitude then longitude; PostGIS is longitude then latitude. jimble
  does not absorb this, so line it up yourself if you use both
- Migration SQL (`conf/migration/...`) **runs exactly as you wrote it.**
  To run on both products, split it **by filename suffix** —
  `001_xxx.mysql.sql` / `001_xxx.postgresql.sql` (no suffix is applied on both).
  See [Splitting SQL per product](./codegen)

## Connecting at startup

**Writing the configuration is not enough.** Call it explicitly from your `main`.

```java
public static void main (String[] args) {

	Migration.install();                                // startup migrations (if you want them; before DBUtil.load)

	DBUtil.load(Conf.conf().config(), App.class);       // throws if it cannot connect. Startup stops here

	JimbleServer.start(new App());

}
```

The second argument to `DBUtil.load` is the **classpath anchor** — migration SQL is
looked up from there. Pass a class of your own application.

**When it cannot connect it throws `SqlExecuteException` (`DB_007`), and startup stops there.**
There is no return value (`void`). Do not catch it; let it propagate out of `main`.

> [!NOTE]
> In 1.x, `DBUtil.load` only returned `false` when it could not connect, and throwing that away let the server start anyway.
> 2.0 made it an exception ([Moving to 2.0](./migrate-2)).

> [!TRAP]
> **If your app has more than one entry point, put this sequence in one place.**
> Write it separately in the web, batch and scheduler mains and <b>one of them goes stale</b>.
> Tests call the same thing ([Pitfalls](./pitfalls) / [Testing](./testing)).

To shut down, call `DBUtil.stop()` (the web server does it as part of
[graceful shutdown](./server); in batches and tests you call it yourself).

## When there is more than one data source

```conf
db {
	main_db { main = true, ... }
	log_db  { ... }
}
```

`DBUtil.getDB("log_db")` gets you a different data source.
The one with `main = true` is `DBUtil.getMainDB()` (there is no no-argument `getDB()`).

Everything listed at the top level is **treated as its own thing**:

- migrations under `conf/migration/<data source name>/` are applied to it
- codegen generates `db/<data source name>/` for it
- `migration` / `db_lock` / `db_value` / `db_cache` are created there too

If all you want is **one more handle without copying the settings**, use `subs`.

```conf
db {
	main_db {
		main = true
		...
		subs {
			archive_db { }
		}
	}
}
```

Get it with `db.newSubDB("archive_db")`.

> [!TRAP]
> **Neither the connection nor the transaction is shared.** `subs` is a separate pool
> from its parent. Even when it points at the same database it is a different
> connection, so writing through `newSubDB` inside the parent's transaction
> **does not roll back with it** — one side stays. Assume it inherits the settings
> from the parent and nothing more.

> [!TRAP]
> **Migrations and codegen do not apply to `subs`.** Per-data-source work only runs
> for the top-level `db { }` entries. So all you can put in a `subs` is a scratch
> table the application creates itself. Anything that needs a schema and generated
> types (an audit log in another database, say) belongs in **a second top-level entry**.
> Also, if a `subs` points at a different database, **that database has to exist
> first**: `subs` connects while the parent data source is still being built, so a
> `create_database_sql` written on a later top-level entry does not arrive in time
> (startup stops with `failed create subs datasource`).

`examples/approval-data` has both a second top-level entry and a `subs`.

## Generate table definitions

```bash
./gradlew codegen
```

This generates one class per table from the DB schema.

```java
Post.id       // Column
Post.title
Post.instance()   // Table
```

Add the `io.jimble.db` plugin and `migrate → codegen → compileJava` are wired
together. Write DDL, start up, and the code catches up on its own.

```kotlin
plugins {
	id("io.jimble.db")
}
```

**Commit the generated classes to the repository.**
If you do not commit them, you cannot build in an environment with no DB.

## Selecting

```java snippet=select-nesting
```

**`select` returns `Optional<Data>`.** It is empty when there are no rows
(the `.orElseThrow()` above is how you take it out when the row must be there).
When the row cannot be read you get an exception, not an empty result (see "Reading errors" below).

**A SELECT result nests under the table name.**
Join `post` and `comment` and the `id` on each side does not collide.

Query with `Column` and no string keys show up in your code.
`row.getData(Post.instance())` pulls out one table's worth, **flattened**.

> [!TRAP]
> **`extractTableData` does not flatten.** It returns `{post: {...}}` **still nested**, so
> serialising that to JSON **leaves one level of nesting in**. To flatten, use
> `getData(table)` or `flattenTable(table)`.

> [!TRAP]
> **Results of a hand-written SQL string (`db.select("SELECT ...")`) do not nest.** The nesting comes from the builder
> aliasing columns as `post__title`, so your own SQL gives a flat Data. In a join, **columns with the same name
> (`id` …) silently overwrite each other with the later value** — give them aliases.

### JSON columns

MySQL `JSON` and PostgreSQL `json` / `jsonb` columns **come back already decoded, as `Data` (objects) or `List` (arrays).**

```java
row.getStringList("tags");      // ["a", "b"]  an array column
row.getData("options");         // an object column
row.getString("tags");          // "a"  <- only the first element, not JSON text
```

**`getString` on an array column returns only the first element** (a WARN is logged the first time it happens).
To get the JSON text itself, convert it in SQL: `CAST(col AS CHAR)` on MySQL, `col::text` on PostgreSQL.

## Reading errors

```java snippet=db-error
```

**DB failures come back as exceptions.** When a statement fails or the connection cannot be made, you get `SqlExecuteException` (unchecked).
Leave it uncaught and it travels up to a 500; inside a transaction, the transaction rolls back.
The code is in `getCode()` (`DB_999` etc.), and the original JDBC exception is at `getCause().getCause()`.

**No rows is not a failure.** It comes back as an empty `Optional`, an empty list, or a count of 0.

| Method | Returns | With no rows |
| --- | --- | --- |
| `select` / `selectCached` | `Optional<Data>` | An empty `Optional` |
| `selectList` / `selectListCached` / `selectListPerformance` | `List<Data>` (never `null`) | An empty list |
| `insert` | Nothing (`void`) | —— |
| `insertKey` | The generated key. `SqlExecuteException` if no key was generated | —— |
| `update` / `delete` | The number of rows affected (`int`) | `0` |
| `execute` | The number of rows affected (`int`). `0` for DDL and statements that return a result set | `0` |
| `executeBatch` / `insertBatch` | A list of counts per statement / generated keys | An empty list for empty input |

**`executeBatch` / `insertBatch` require every statement to be the same SQL.** A mix throws with `DB_998`.

### "No rows" and "could not read"

**They come back as different things.** An empty `Optional` means "no rows" and nothing else; a failure to read throws.

```java
Optional<Data> user = db.select(sql, id);      // throws SqlExecuteException here if it cannot be read
if (user.isEmpty()) { return nobody; }         // empty means "no rows", nothing else

Data post = db.select(sql, id)
	.orElseThrow(() -> new HttpException(404, "no such post"));
```

The day the DB goes down, your app does not answer "no such user".

> [!NOTE]
> In 1.x, `select` returned `null` both for no rows and for a failure (you had to check `isError()` to tell them apart).
> 2.0 separates them. `selectOrThrow` / `selectListOrThrow` now mean the same as `select` / `selectList`, so they are
> deprecated (they go away during 2.x).

### Catching only unique-constraint violations

The only failure you usually want to branch on is a unique-constraint violation. **That one has its own type.**
Hitting a unique constraint throws `DuplicateKeyException` (a subclass of `SqlExecuteException`) — from `insert`, `insertKey`, `update` or any other statement.

```java
try {
	long id = db.insertKey(SQL.insert(User.instance()).value(User.email, email));
} catch (DuplicateKeyException e) {
	return "that email address is taken";
}
```

> [!TRAP]
> **Catch it inside a transaction and carry on, and that Tx can no longer commit.**
> `tx.commit()` refuses with `TransactionException` (`DB_004`) and rolls everything back ([Transactions](./transaction)).
> To use it as "update if it exists", catch it outside the transaction or write it in SQL
> (`INSERT ... ON DUPLICATE KEY UPDATE` on MySQL, `INSERT ... ON CONFLICT` on PostgreSQL).

### Where to catch

**DB exceptions are unchecked, so the compiler never asks you to catch them.** Decide where to catch with the table below. Anything not in it is left alone: the framework replies 500 and rolls back.

| When you | Catch | Reply with |
| --- | --- | --- |
| Insert or change user input in a column with a unique constraint (UNIQUE or primary key) — email address, login ID and the like | `DuplicateKeyException` | "Already taken" (409 or similar) |
| Take `RedisLock.lock(...)` for a user action | `RedisLockException` | "Already in progress" (409 or similar; or branch on an empty `tryLock(...)`) |
| Anything else (`SqlExecuteException` / `TransactionException`) | Nothing | The framework replies 500 and logs it |

- **Checking with `select` first does not let you skip the catch.** If two requests arrive together, both pass the check and one hits the constraint
- **Catch outside the transaction** (see the TRAP above)
- Write one test that inserts the same value twice; a forgotten catch shows up there as a 500

### Generated keys and counts

**`insert` returns nothing.** Use `insertKey` for the generated key, and `execute` when you need a count, as with `INSERT ... SELECT`.

```java
db.insert(SQL.insert(Tag.instance()).value(Tag.name, name));             // just insert
long id = db.insertKey(SQL.insert(Post.instance()).value(...));          // the generated key
int count = db.execute("INSERT INTO archive SELECT * FROM post WHERE ...");  // the count
```

> [!NOTE]
> In 1.x, `insert` returned the generated key when there was one and the number of rows inserted when there was not
> (adding one auto-increment column changed its meaning). 2.0 splits them.
> `insertNoReturnKey` is deprecated (it goes away during 2.x); use `insert` or `execute`.


## Migrations

Put them in `conf/migration/<schema name>/001_xxx.sql`.

```sql
# --- !Ups

CREATE TABLE `note` (
	`id`         BIGINT UNSIGNED AUTO_INCREMENT COMMENT 'ID' PRIMARY KEY,
	`title`      VARCHAR(200)  NOT NULL COMMENT 'Title',
	`created_at` DATETIME      NOT NULL COMMENT 'Created at'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Note';

# --- !Downs

DROP TABLE `note`;
```

```bash
./gradlew migrate
```

What has already been applied is recorded, so it never runs twice.
When several servers start at once, a lock is taken and only one of them runs.

When each thing runs, what happens when one fails, and the type mapping that
gets generated are all in
[Migrations and code generation](./codegen).
