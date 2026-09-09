---
title: What jimble is
summary: A web framework on Java 25 and Helidon, with no annotations, no DI, and no reflection
section: Getting started
order: 1
---

# What jimble is

jimble is a web framework for Java. It sits on Helidon's Níma (virtual threads) and
gives you routing, DB, templates, batch, MQ, SSE, WebSocket, and MCP as one piece.

```java snippet=hello-route
```

## What makes it different

Most frameworks aim to reduce how much you write.
jimble aims to let **the person reading the code follow it top to bottom**.

- **No annotations.** There is no `@Controller`, no `@Inject`, no `@Transactional`.
  A route exists only where `get("/hello", ...)` is written.
- **No DI container.** You `new` your objects. Who creates whom is right there in the code.
- **No classpath scanning at startup.** Startup is fast, and nothing fights with native images.
  In exchange, you `install()` what you use yourself.

Hold to these three and the "why does this even work?" hours disappear.
The price is a few extra lines. jimble thinks that is the cheaper side of the trade.

Read [Principles](./principles) for the details.

## What it can do today

| What you can do | Where to look |
| --- | --- |
| HTTP routing, path parameters, filters | [Routing](./routing) |
| Reading and validating requests, building responses | [Request and response](./request-response) |
| File upload and download | [File upload](./upload) |
| Input validation, paging | [Validation and paging](./validation) |
| Handling exceptions, unmatched routes, validation failures | [Error handling](./errors) |
| jte templates (precompiled) | [Templates](./view) |
| Serving static files, SPAs, MPAs | [Static files and SPAs](./assets) |
| Session, CSRF, flash, signed cookies | [Session and safe defaults](./session-security) |
| Rate limiting (per route, per IP) | [Rate limiting](./ratelimit) |
| Virtual threads, what runs where, how to stop it | [Execution model](./execution) |
| SQL DSL, generating table definitions | [Using the DB](./db) |
| The migration and code generation flow | [Migrations and code generation](./codegen) |
| Transactions | [Transactions](./transaction) |
| Cache (DB / memory / Redis) and distributed locks | [Cache and locks](./cache) |
| Batch jobs on cron, and the admin screen | [Batch](./batch) |
| A queue backed by the DB | [MQ](./mq) |
| Server-Sent Events | [SSE](./sse) |
| WebSocket | [WebSocket](./websocket) |
| A Model Context Protocol server | [MCP](./mcp) |
| Installing the CLI, generating a skeleton, hot reload | [The jimble command](./cli) / [Hot reload](./hot-reload) |
| Gradle tasks and configuration | [the Gradle plugins](./gradle) |
| How to write tests (including how to split off the ones that use the DB) | [Testing](./testing) |
| Data, JSON, HTTP client, CSV, crypto | [Utilities](./util) |
| Ports, limits, proxies, compression | [Server configuration](./server) |
| Access logs, execution IDs, logback configuration | [Logging](./log) |

## About versions

**This site describes the latest release.**
It is published only when a release is cut, so what you read here
**works on the version you can download**.

The releases are on
[Maven Central](https://repo.maven.apache.org/maven2/io/jimble/).
The dependency coordinates and a build file that works as it stands are on the
[Gradle plugins](./gradle) page; the source is on
[GitHub](https://github.com/hidemikimura/jimble).

## Get it running

Go to [Your first endpoint in 5 minutes](./quickstart).
