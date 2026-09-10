---
title: Server configuration
summary: Port, listen address, limits, proxies, compression
section: Development
order: 6
---

# Server configuration

```conf
server {
	host                 = ""         # Address to listen on. Empty means all of them
	port                 = 9000
	max_request_size     = 10485760   # Limit on the request body (10MB)
	max_header_size      = 16384      # Limit on the headers as a whole (16KB)
	idle_timeout_seconds = 60
	trust_proxy          = false      # Whether to believe X-Forwarded-*
	compression          = true       # Whether to gzip responses
	access_log           = true       # Whether to write the access log ([Logging](./log))
	bot_access_log       = true       # Whether to split out the bot access log
	strict_routes        = false      # Throw on routes nothing can reach (turn this on in CI)

	shutdown_grace_seconds   = 0      # From "start stopping" to refusing new requests (seconds)
	shutdown_timeout_seconds = 15     # How long to wait for in-flight work (seconds)
}
```

**Write nothing and you get the defaults above.** What is actually in effect is printed in the startup log.

```
jimble を起動しました: http://localhost:9000
サーバー設定: 待受=全部 / 本文上限=10485760byte / ヘッダ上限=16384byte / アイドル=60秒 / 圧縮=true / プロキシ信頼=false
```

## Port

**The port, and only the port, can also be given as a system property.** That is so a container can change the port without touching the configuration file.

```bash
java -Djimble.server.port=8080 -jar app.jar
```

The order of precedence is `-Djimble.server.port` &gt; `server.port` &gt; 9000.

> [!TIP]
> `JimbleServer.start(app, 0)` **takes a free port automatically.**
> Use this in tests (pin the port and they fail only when you run them side by side).
> The port it actually took comes back from `server.port()`.

## The listen address

**Empty means it listens on every address.** Narrow it down for anything that must not be visible from outside.

```conf
server { host = "127.0.0.1" }
```

> [!TIP]
> An MCP server you run on your own machine needs this ([MCP](./mcp)).
> **Lean on the firewall instead, and the day someone forgets to configure it you are silently public.**

## Limits

| | Limit | When it is exceeded |
| --- | --- | --- |
| Request body | `server.max_request_size` (10MB) | **413** |
| Headers as a whole | `server.max_header_size` (16KB) | the connection is cut |
| A connection doing nothing | `server.idle_timeout_seconds` (60 seconds) | closed |

Uploads have a separate limit of their own ([File upload](./upload)).

> [!WARN]
> **The body limit (10MB by default) is smaller than the total upload limit (50MB by default)**,
> so raise both if you are going to accept large things.

There are no read or write timeouts.

## Behind a proxy

**By default `X-Forwarded-*` is not believed.** You start believing it once you know for certain that every request comes through the proxy in front.

```conf
server { trust_proxy = true }
```

With `true`, `proxyAddress()` returns the source in this order.

1. `CF-Connecting-IP`
2. `X-Real-IP`
3. the **first** entry in `X-Forwarded-For`
4. the connection source, if none of those are there

> [!TRAP]
> **Setting `trust_proxy` to true does not change `address()`.**
> `address()` is always the **TCP connection source** (= the load balancer).
> Use `proxyAddress()` anywhere you decide something from the source address.

> [!TRAP]
> **Bot detection (`isBotAccess()`) looks at `address()`.**
> Behind a proxy, the IP it judges on is the load balancer's.

> [!TRAP]
> **`scheme()` does not look at `X-Forwarded-Proto` either.**
> Terminate TLS in front and the app sees `http`.
> Watch for that when you build a redirect target.

> [!WARN]
> **Do not set `true` while the server can still be reached directly.**
> Anyone can attach those headers, so **the source address can be forged at will.**
> There is no mechanism for restricting trusted upstreams by IP (it is a single boolean).

## Compression

`server.compression` (default `true`) turns it **on / off**.
What gets compressed is the server's (Helidon's) decision, from `Accept-Encoding`.

> [!NOTE]
> **There is no compression level and no per-type exclusion.** Do the fine-grained control in front of jimble.

## Blocking bots

If you only need to tell them apart, splitting the access log is enough ([Logging](./log)). To **block** them, declare it.

```java
before(BotBlocker.forbidden());                          // return 403
before(BotBlocker.of(context -> context.response().redirect("/")));   // return whatever you like
```

> [!NOTE]
> **There is no default "block" behaviour.** Blocking the search engines too would hurt,
> so what to return is left to the app.

## What is not here

| | |
| --- | --- |
| **TLS / HTTPS** | Terminate it in front (load balancer, nginx) |
| **HTTP/2** | Not in the dependencies |
| **A health check route** | jimble does not provide one (see "Stopping" below) |

## Stopping

**It does not stop dead.** `stop()` goes in this order.

1. **Mark itself as stopping** — `Shutdown.isStopping()` becomes true. **Ordinary requests are still served**
2. Wait `server.shutdown_grace_seconds` (default 0)
3. **Refuse new requests** (503)
4. Wait for the in-flight ones to finish, up to `server.shutdown_timeout_seconds` (default 15 seconds)
5. Stop the server

**On SIGTERM this runs by itself** (that is what a container sends, and then it waits).

### Take the health check down first

jimble does not provide a health check route. **Write it like this.**

```java
get("/health_check", context ->
	context.response().send(Shutdown.isStopping() ? 503 : 200));
```

> [!TIP]
> It takes the load balancer a while to pull this machine out of rotation.
> Answer 503 to the requests that arrive in the meantime and **from the outside that is an error.**
> That is why there is room to wait between step 1 and step 3.
> Put in the health check interval × the failure count (2 seconds × 3, say → `shutdown_grace_seconds = 10`).

> [!WARN]
> **If the wait runs out, it stops with work still in flight.**
> When that happens, `処理中のリクエストが N 件残ったまま停止します` goes out at warn.
> Not stopping at all is the worse outcome (the container kills it).

> [!NOTE]
> To have threads you started yourself stop along with it, hand them to `Shutdown.add(...)`
> ([Execution model](./execution)).

## Making jimble the proxy

The other way round: jimble can forward on to another server.

```java
install(() -> ReverseProxy.mount("/api", "http://backend:8080"));
```

`X-Forwarded-For` is **appended to the existing value** (not overwritten).
The timeouts are `proxy.connect_timeout_ms` (5 seconds) and `proxy.request_timeout_ms` (30 seconds),
and a forward that fails returns **502**.
