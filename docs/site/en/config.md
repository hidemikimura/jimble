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

## The main keys

```conf
server {
	host        = ""         # the address to listen on. empty means all of them
	port        = 9000
	trust_proxy = false      # false until it sits behind a load balancer
	max_request_size     = 10485760
	max_header_size      = 16384
	idle_timeout_seconds = 60
	compression          = true
	access_log           = true     # turning it off is faster, but nothing is left behind
	bot_access_log       = true
	strict_routes        = false    # throw on routes nothing can reach (turn this on in CI)

	shutdown_grace_seconds   = 0    # from starting the stop until new requests are refused
	shutdown_timeout_seconds = 15   # how long to wait for work in flight
}

cookie {
	secure           = true            # false locally (see below)
	secret           = ${?COOKIE_SECRET}
	previous_secrets = [${?COOKIE_SECRET_OLD}]   # only while rotating
}

session {
	store            = "none"     # none | db | redis | cookie
	cookie_name      = "sid"
	secret           = ${?SESSION_SECRET}        # required when store = cookie
	previous_secrets = [${?SESSION_SECRET_OLD}]  # only while rotating
}

upload {
	max_file_size  = 10485760   # per file (10MB)
	max_total_size = 52428800   # per request in total (50MB)
	max_files      = 20
	temp_dir       = ""         # empty means java.io.tmpdir
}

auth {
	lockout {
		enabled       = true    # does nothing without a DB
		free_attempts = 3       # no wait up to here (typos)
		base_seconds  = 1       # from the 4th: 1 -> 2 -> 4 ...
		max_seconds   = 300     # the longest wait
		forget_hours  = 24      # start counting again after this gap
	}

	remember {
		enabled       = true    # does nothing without a DB
		cookie_name   = "remember"
		sliding_days  = 30      # from when it was last used
		absolute_days = 90      # past this it expires even if still in use
		grace_seconds = 60      # how long the old one still works after a rotation
	}

	oidc {
		google {
			issuer        = "https://accounts.google.com"
			client_id     = ${?GOOGLE_CLIENT_ID}      # from the environment
			client_secret = ${?GOOGLE_CLIENT_SECRET}  # never in the file
			redirect_uri  = "https://example.com/auth/google/callback"

			# optional - discovered from the issuer
			# authorization_endpoint = "..."
			# token_endpoint         = "..."
			# jwks_uri               = "..."

			scopes        = "openid email profile"
			clock_skew    = 60      # seconds of clock drift to tolerate
			discovery_ttl = 3600    # seconds to keep discovery and JWKS
		}
	}
}

rate_limit {
	store   = "memory"       # memory | redis | db
	enabled = true
}

cache {
	type          = "db"     # db | memory | redis
	temp_dir      = ""
	memory.expire = 0        # memory only. seconds
}

redis {
	host = ""                # empty means no Redis
	port = 6379
	ssl  = false
}

sse {
	max_duration_seconds = 300
	max_events           = 0
	retry_millis         = 3000
}

mcp {
	path            = "/mcp"
	allowed_origins = []
}

migration {
	# auto | true | false. auto applies them everywhere but locally
	on_startup   = "auto"
	resource_dir = "migration"
}

codegen {
	package = "db"
}
```

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
