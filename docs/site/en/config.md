---
title: Configuration
summary: application.conf. Per-environment overrides, and how to pass secrets
section: Development
order: 5
---

# Configuration

You write it in `conf/application.conf`. The format is HOCON.

## Where it lives

**Configuration goes in `conf/`.**

```
conf/application.conf          configuration
conf/application.local.conf    per environment
conf/migration/<schema name>/  migrations
```

Add `conf` to your resources in `build.gradle.kts`.

```kotlin
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}
```

Now **everything under `conf/` goes into the jar as it is.**
`codegen`, `migrate`, `jimbleRun` and your tests all see the same files, from the
classpath.

**jimble reads the classpath and nothing else.** It does not look outside the jar.
Change the configuration and you rebuild. **The running jar and its configuration
are one to one.**

**It reads exactly one file.**

1. `application.<env>.conf` if it exists — **that one**
2. Otherwise `application.conf`

**The startup log says which file it read.**

```
設定: jar:file:/opt/app/app.jar!/application.prod.conf
```

## Switching by environment

What pulls in the shared file is **the `include` in the per-environment file.**

```conf
# application.prod.conf
include "application.conf"

db {
	main {
		url = ${?DB_URL}
	}
}
```

```
application.conf          shared
application.prod.conf     production. line 1 reads the shared file, the rest overrides it
```

The environment comes from `-Djimble.env=prod`. The default is `local`.

**jimble does not add the shared file behind your back.** If it did, **you could
not tell from the file** whether the `include` is doing the work or the framework
is. One file is read, and what follows is what is written there.

### If you forget the include

The entire shared configuration drops out. When it does, startup names what is
missing.

```
設定: application.conf にしかないキーが読まれていません: cipher, session, server
 / application.prod.conf の先頭に include "application.conf" を書いてください
```

> In English: keys that exist only in application.conf are not being read — cipher,
> session, server. Put `include "application.conf"` at the top of application.prod.conf.

## Do not write secrets in the file

```conf
db {
	main_db {
		url      = "jdbc:mariadb://127.0.0.1:3306/app"
		url      = ${?DB_URL}
		password = ""
		password = ${?DB_PASSWORD}
	}
}
```

`${?ENV_NAME}` means "override if the environment variable is there, otherwise
leave the line above alone". Writing the same key twice is the correct way to do
this.

## Comments are # or //

**HOCON has no `/* */`.** Write one and you get this.

```
Key '/' may not be followed by token: '*'
```

## Every key

**Everything jimble reads is here.** A key that is not in this list is a key jimble does
not read — a typo is silently ignored. The values shown are the **defaults**, so leave out
anything you are not changing.

> [!NOTE]
> **Durations and sizes carry their unit in the value** (`30m`, `200ms`, `10MiB`).
> **A bare number fails at startup** — that is what stops `assets.max_age = 3600000` from
> going through as forty-one days. The units are `ns`, `us`, `ms`, `s`, `m`, `h`, `d`
> and `B`, `KiB`, `MiB`, `GiB`.

> [!TRAP]
> **`MB` is a power of 1000 and `MiB` a power of 1024** (HOCON's rule).
> `10MB` is 10,000,000 bytes; `10MiB` is 10,485,760.
> **What you write is what you get** — either is fine, but they are not the same number
> when you compare against a default.

```conf
server {
	host                 = ""       # the address to listen on. empty means all of them
	port                 = 9000
	max_request_size     = 10MiB    # cap on the request body
	max_header_size      = 16KiB    # cap on all headers together
	idle_timeout         = 60s      # how long a connection doing nothing is kept
	compression          = true     # gzip responses
	trust_proxy          = false    # false until it sits behind a load balancer (see below)
	access_log           = true     # turning it off is faster, and leaves no trace
	bot_access_log       = true     # keep bot access logs separate
	strict_routes        = false    # make unreachable routes an error (true in CI)
	shutdown_grace       = 0s       # from "start stopping" to refusing new requests
	shutdown_timeout     = 15s      # how long in-flight requests are waited for
	backlog              = 1024     # connections the OS holds while accept catches up
	write_queue_length   = 0        # length of the response write queue; 0/1 means "no queue"
	smart_async_writes   = false    # with a queue, write inline while it is not busy
}

metrics {
	enabled = true                  # whether to record metrics
}

router {
	ignore_case           = false   # whether to match paths case-insensitively
	redirect_to_canonical = false   # whether to 301 to the canonical URL
}

cookie {
	secure           = true                      # HTTPS only. false locally (see below)
	http_only        = true                      # not readable from JavaScript
	same_site        = "lax"                     # none | strict | lax
	domain           = ""                        # empty means the issuing domain
	max_age          = 365d                      # 0 or less for a session cookie
	secret           = ${?COOKIE_SECRET}         # signing key. empty means no signing
	previous_secrets = [${?COOKIE_SECRET_OLD}]   # only while a key is being rotated
	accept_unsigned  = false                     # true only while signing is being turned on
}

csrf {
	max_age = 1d    # how long a token lives. separate from cookie.max_age
}

session {
	store            = "none"                     # none | db | redis | cookie
	timeout          = 30m
	cookie_name      = "sid"
	table            = "session"                  # when store = db
	secret           = ${?SESSION_SECRET}         # required when store = cookie
	previous_secrets = [${?SESSION_SECRET_OLD}]   # only while a key is being rotated
}

upload {
	max_file_size  = 10MiB   # per file
	max_total_size = 10MiB   # per request, in total (keep it level with server.max_request_size)
	max_files      = 20      # files per request
	temp_dir       = ""      # empty means java.io.tmpdir
}

assets {
	max_age           = 0s      # Cache-Control max-age
	immutable_max_age = 365d    # files treated as immutable (js / css)
	if_modified_since = true    # honour If-Modified-Since
	etag              = true    # use ETag / If-None-Match
}

template {
	package      = "gg.jte.generated.precompiled"   # output of precompilation
	content_type = "text/html; charset=utf-8"
}

paging {
	name_page = "page"   # request parameter NAME for the page number (not a count)
	name_per  = "per"    # request parameter NAME for the row count
	max_per   = 200      # cap per page. applies to per=all too (0 for no cap)
}

auth {
	lockout {
		enabled       = true    # does nothing without a database
		free_attempts = 3       # nobody waits up to here (mistyping)
		base          = 1s      # from the fourth: 1 → 2 → 4 …
		max           = 5m      # cap on the wait
		forget        = 24h     # this long without a failure resets the count
	}

	remember {
		enabled     = true         # does nothing without a database
		cookie_name = "remember"
		sliding     = 30d          # measured from last use
		absolute    = 90d          # past this it expires even if still in use
		grace       = 60s          # right after rotation, the old one still passes
	}

	mfa {
		enabled        = true      # needs a database and secret_key (see below)
		issuer         = ""        # the name shown in the authenticator app
		digits         = 6         # leave it at 6 (most apps only show six)
		period         = 30        # seconds. an RFC 6238 parameter, so a plain number here
		window         = 1         # steps either side. wider accepts more guesses too
		recovery_codes = 10        # how many are shown at enrolment
		pending        = 5m        # from password accepted to code entered

		# Encrypts the TOTP secret. Mfa.enroll refuses without it.
		# Do not reuse cipher.key (see below)
		secret_key     = ${?MFA_SECRET_KEY}
	}

	# One block per provider. The name (google) is what you pass to Oidc.callback
	oidc {
		google {
			issuer        = "https://accounts.google.com"
			client_id     = ${?GOOGLE_CLIENT_ID}      # from the environment
			client_secret = ${?GOOGLE_CLIENT_SECRET}  # never in the file
			redirect_uri  = "https://example.com/auth/google/callback"

			# Optional — discovery reads them from issuer
			# authorization_endpoint = "..."
			# token_endpoint         = "..."
			# jwks_uri               = "..."

			scopes        = "openid email profile"
			clock_skew    = 60      # seconds of clock drift accepted
			discovery_ttl = 3600    # seconds discovery and JWKS are held
		}
	}
}

cipher {
	# Reads the already-encrypted password hashes of a migrated app.
	# Not for two-factor auth (see below)
	key = ${?CIPHER_KEY}   # 16 / 24 / 32 bytes
	iv  = ${?CIPHER_IV}    # 16 bytes
}

hash {
	password {
		# If you set cipher.*, you must set this too (startup fails otherwise)
		encrypt = false
		pepper  = ${?PASSWORD_PEPPER}   # mixed into the hash. cannot be rotated
	}
}

rate_limit {
	enabled = true
	store   = "db"     # db | memory | redis
}

cache {
	type          = "db"    # db | memory | redis
	temp_dir      = ""      # where the files go
	memory.expire = 0s      # memory only. 0 never expires
}

sql_cache {
	enabled = false
	store   = "memory"   # memory | redis | db
	ttl     = 5m
	max     = 10000      # cap on entries (memory only)
}

redis {
	host = ""        # empty means no Redis
	port = 6379
	ssl  = false

	settings {
		connection_timeout      = 10s
		timeout                 = 3s      # how long to wait for a command to answer
		connection_minimum_idle = 24
		connection_pool_size    = 64
		retry_attempts          = 3
		retry_minimum_interval  = 500ms
		retry_maximum_interval  = 2s
		idle_connection_timeout = 10s

		subscription_connection_minimum_idle_size = 1
		subscription_connection_pool_size         = 50
	}
}

db {
	# Drop all-null tables from the result.
	# Not a data source name, so it goes at this level
	remove_all_null_table_data = false

	# One block per data source. The name (blog) becomes the name of the DB class
	blog {
		main     = true                    # use as the main data source
		driver   = "com.mysql.cj.jdbc.Driver"
		url      = "jdbc:mysql://127.0.0.1:3306/blog"
		username = ${?DB_USER}
		password = ${?DB_PASSWORD}
		product  = "mysql"                 # mysql | mariadb | postgresql | postgres | pgsql
		schema   = ""                      # empty means the block's name. spelled scheme before

		maximum_pool_size     = 10         # largest the pool gets
		minimum_idle          = 1          # connections always kept open
		fetch_size            = 100        # rows fetched at a time
		idle_timeout          = 10m        # how long an unused connection is kept
		max_lifetime          = 30m        # how long one connection lives
		connection_timeout    = 30s        # cap on waiting for a connection
		keepalive_time        = 30s        # liveness check interval
		connection_init_sql   = ""         # SQL run right after connecting
		connection_test_query = ""         # SQL used for the liveness check
		connection_pool_type  = "hikari"   # hikari | agroal
		transaction_isolation = ""         # isolation level
		create_database_sql   = ""         # SQL used to create the database when missing
		long_connection_log   = false      # log connections held a long time
		long_connection_time  = 0s         # what counts as "a long time"

		# Read-only connection. Left out, reads go to the same place as writes
		read { url = "jdbc:mysql://127.0.0.1:3307/blog" }

		# Shards. Same keys as above
		subs {
			shard1 { url = "jdbc:mysql://127.0.0.1:3308/blog" }
		}
	}
}

db_sticky {
	use = false   # after a write, send reads in the same request to the write side
}

log {
	db = false    # log SQL
}

async {
	prefetch {
		on_response = false   # prefetch automatically before the response is sent
		max_depth   = 5       # cap on prefetch passes
	}
}

migration {
	on_startup   = "auto"        # auto | true | false. auto applies outside local
	down         = false         # run down migrations
	lock_timeout = 60s           # how long to wait for the lock
	resource_dir = "migration"   # resource directory holding the SQL files
}

codegen {
	package        = "db"   # package the generated code goes in
	exclude_tables = []     # tables left out of code generation
}

mq {
	poll_min          = 10ms   # wait when the queue is not empty
	poll_max          = 1s     # wait when the queue is empty (grows)
	retry_backoff     = 10s    # interval between retries (doubles each time)
	retry_backoff_max = 10m
	stale             = 10m    # this long as running counts as dead

	# Threads per execution type. Left out, each type's own default is used
	thread_count {
		short_time = 2
		long_time  = 8
	}
}

scheduler {
	reload_interval = 10s             # how often batch_master is re-read
	tick_interval   = 1s              # how often cron is checked
	exit_check      = 3s              # how often a stop order is checked for
	execute_threads = 10              # threads that run batches (0 for unlimited)
	queue_name      = "mq_scheduler"
}

batch {
	scheduler_id = ""     # identifier for this instance. empty means the host name
	heartbeat    = 3s     # how often it says it is still running
	alive        = 10s    # this long without an update stops counting as alive
	cancel_check = 3s     # how often a cancel order is checked for
	progress     = 5s     # how often a chunk batch writes progress to the history
	all_stop     = 1h     # how long the stop-everything flag stays in force
}

batch_manager {
	enabled  = false                    # an empty user or password means no screen at all
	path     = "/batch-manager"
	realm    = "jimble batch manager"
	username = ${?BATCH_MANAGER_USER}
	password = ${?BATCH_MANAGER_PASSWORD}
}

proxy {
	connect_timeout = 5s     # cap on connecting to the target
	request_timeout = 30s    # cap on waiting for the answer
}

sse {
	max_duration = 5m    # how long one stream may stay open (0 or less: unlimited)
	max_events   = 0     # cap on events sent (0 or less: unlimited)
	retry        = 3s    # how long before a disconnected client reconnects
}

mcp {
	path            = "/mcp"
	name            = "jimble"    # server name
	version         = "0.1.0"     # server version (not the MCP spec revision)
	instructions    = ""          # instructions for the model (returned by server/discover)
	page_size       = 100         # rows per page in listings
	allowed_origins = []          # allowed origins
}

jimble {
	io.buffer_size       = 256KiB   # read/write unit when sending files
	read_only_container  = false    # running in a container you cannot write to

	# server.port also takes -Djimble.server.port=8080 (the system property wins)
}
```

## `cipher.key` and `auth.mfa.secret_key` are different keys

**Do not reuse `cipher.key` for two-factor auth.**

**If you set `cipher.*`, you must also set `hash.password.encrypt`.** Leave it out and
startup fails.

The default used to be "true if `cipher.key` *and* `cipher.iv` are both set". So an app
that added only `cipher.key` stayed at false and **flipped the moment `cipher.iv` was
added** — stored BCrypt hashes were then read as encrypted and **everyone was locked
out**. All it says is "wrong id or password", so nothing points back at the setting that
was added.

**No default is decided by a distant key.** If you want encryption, say so.

| | |
| --- | --- |
| `cipher.key` / `cipher.iv` | Reads the **already-encrypted password hashes of a migrated app**. Not for anything new — fixed IV, no tamper detection |
| `auth.mfa.secret_key` | Encrypts **TOTP secrets**. AES-256-GCM |

An app with no `cipher.*` at all needs no setting, exactly as before (BCrypt only, no
encryption).

## trust_proxy is false by default

Set it to `true` and `X-Forwarded-For` is trusted.
**Leave it `false` until the application sits behind a load balancer.**
Turn it on while the application can still be reached directly and anyone can
forge their source IP.

## cookie.secure is true by default

The default is the safe side, but local development is http, so the browser never
sends the cookie back. Session, CSRF and flash all stop working, and nothing
raises an error.

When `env=local` and `secure = true`, startup logs a WARN.
Put `cookie { secure = false }` in your local `application.conf`, and
**take it out when you ship to production**.

## Check it in the startup log

```
jimble 構成: env=local / session=none / cache=db / redis=なし / db=[blog_example]
```

The configuration that was loaded appears on the first line at startup.
"I thought I configured that" gets caught right here.
