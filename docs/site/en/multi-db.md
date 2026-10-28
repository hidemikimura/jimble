---
title: Lining up databases with the same tables
summary: One set of web and batch servers, one database per client. Configuration, choosing the database per request, migrations, generated code, MQ, connection counts, and when one goes down
section: Database
order: 7
---

# Lining up databases with the same tables

Keep one set of web and batch servers, line up many **databases with the same tables** as `db.<name>`,
and choose the client's database for each request (2.7.8 completes the settings for this).

- **Good for**: a database per client (or per group of clients); a dedicated database for a large client
- **Not for**: **hundreds** of data sources or more. Pools, MQ claiming and the startup checks grow with
  the number of data sources. Aim for dozens at most (not one database per company)

The shared database (the main one, `main = true`) holds the client ledger, the scheduler, batch
management and so on; the shop databases hold the business tables and the MQ queue tables.

## Example configuration

```conf
# example of lined-up databases
db_templates {
	tenant {
		driver             = "org.mariadb.jdbc.Driver"
		username           = ${?SHOP_DB_USER}
		password           = ${?SHOP_DB_PASSWORD}
		maximum_pool_size  = 6
		minimum_idle       = 0              # do not keep connections open to idle databases
		connection_timeout = 3s             # do not let an unreachable database delay startup too much
		migration_dir      = "shop"         # the same SQL for every shop database
		migrate_on_startup = false          # apply from the CLI during deployment, not at startup
		codegen_name       = "shop"         # one set of generated code
		required           = false          # one going down does not stop startup or the health check
		framework_tables   = ["db_lock"]    # no framework tables the shop databases do not need
	}
}

db {
	startup_parallelism = 8                 # run the startup checks in parallel

	main {
		main     = true
		driver   = "org.mariadb.jdbc.Driver"
		url      = "jdbc:mariadb://db0/common"
		username = ${?DB_USER}
		password = ${?DB_PASSWORD}
		mq       = false                    # no queues on the shared database
	}

	shop_a = ${db_templates.tenant} { url = "jdbc:mariadb://db1/shop_a" }
	shop_b = ${db_templates.tenant} { url = "jdbc:mariadb://db1/shop_b" }
}

mq {
	hint           = redis                  # visit only the databases that have work
	sweep_interval = 10s
}
```

Each setting is described in [DB](./db), [Migrations and code generation](./codegen) and [MQ](./mq).
The template (`db_templates`) lives outside `db { }` and is copied and overridden with `${}`.

## Choosing the database per request

In an entry filter, choose the database **from the Host or the like** and keep it on the request.

```java
before(context -> {
	String dataSource = tenants.dataSourceOf(context.request().host());   // look up the ledger (cached)
	if (dataSource == null) {
		throw new HttpException(404, "unknown host");
	}
	context.attribute(SHOP_DB, dataSource);
});

get("/orders", context -> {
	try (DB db = DBUtil.getDB(context.<String>attribute(SHOP_DB))) {
		context.response().json("orders", db.selectList(SQL.select().from(Order.instance())));
	}
});
```

> [!WARN]
> **Do not take the value that chooses the database from request parameters (query, form, JSON).**
> Changing that value would hit another client's database. Derive it from something fixed by the route,
> such as the Host, and keep filtering by `shop_id` inside the shop database too.

Keep the ledger (Host → data source name) in the shared database and cache the lookups (every request
goes through it).

## What stays on the main data source

These live only on the main data source (the shop databases do not need them):

- the scheduler (`batch_master` and the rest) and its MQ queue (`mq_scheduler`)
- batch run records (`batch_history` and the rest); even a chunk batch running on a shop database records
  on the main one ([Batch](./batch))
- session and authentication tables (`session`, `auth_*`) and rate limits
- code migrations (`CodeMigration`)

## Applying migrations

Set `migrate_on_startup = false` on the shop databases and **apply them from the CLI during deployment**.

```bash
java -cp app.jar io.jimble.db.cli.JimbleDbCli migrate --migration-dir shop --parallel 4 --keep-going
java -cp app.jar io.jimble.db.cli.JimbleDbCli migrateCheck --migration-dir shop
# start the application once they line up
```

A failure does not stop the others, and a per-data-source result is printed at the end. `migrateCheck`
writes nothing and reports files not applied, extra records and hash mismatches per data source
([Applying to databases lined up side by side](./codegen#applying-to-databases-lined-up-side-by-side)).

## Generated code

With the same `codegen_name`, there is one set of generated code (the representative is the main data
source, else the first by name). Table classes put only the table name into SQL, so **run them with the
database chosen for the request**. `Shop.db()` returns the representative database, so do not use it for
shop work ([Sharing generated code](./codegen#sharing-generated-code-across-databases-with-the-same-tables)).

## MQ

- **Keep the queue table in the same database as the business tables**, so enqueueing and the business
  update roll back together
- Leave out databases that use no queues (such as the shared one) with `mq = false`. Putting to one that is
  left out throws
- The poller orders the data sources by name and starts right after the one it last claimed from (one
  database with a backlog does not hold back the others)
- With `mq.hint = redis`, only databases with work are visited. Every database is visited once per
  `sweep_interval`, which is the longest a row without a hint waits
- Query estimate: with `hint = off`, even an idle process issues "queues × types × databases × 2 ÷
  `poll_max`" SELECTs per second

See "Choosing the data sources a queue lives on" and "Visiting only the databases that have work" in
[MQ](./mq).

## Connection counts

There is one pool per data source × write/read × parent and each sub.

- Connections a process always keeps open = the sum of `minimum_idle` over those pools
- Connections at peak = the sum of `maximum_pool_size` over those pools

With 30 shop databases in the example above, the shop databases keep 0 open and reach 30 × 6 = 180 at
peak (plus the main one). Set the database server's `max_connections` above the number of processes × the
peak.

## SQL result cache

The SQL result cache is **kept apart by data source name**, so shop databases never share entries.
**For caches your application keeps itself** (the ledger, settings, templates and so on), **put the data
source name into the key**. Otherwise another client's value comes back.

## When one database goes down

Set `required = false` on the shop databases.

- If one cannot be reached at startup, startup goes on, and once the database is back it just works
- `DBUtil.healthCheck()` (the health check) looks only at `required = true` data sources, so one going
  down does not take the whole machine out of rotation
- Per data source, use `DBUtil.healthCheckAll()`
- A database that could not be reached at startup did not get its framework tables or migrations. Apply
  them with the CLI's `migrate --data-source` once it is back

See [DB](./db#data-sources-that-may-be-down).
