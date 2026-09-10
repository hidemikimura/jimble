---
title: Static files and SPA
summary: assets / SPA / MPA. Serving from inside the jar, ETag, rewriting
section: Basics
order: 7
---

# Static files and SPA

There are three ways to serve. **Every one of them is a handler separate from routing proper** (requirement F-W-20).

| What you want | What to use | On a miss |
| --- | --- | --- |
| Serve CSS, JS and images as-is | `AssetHandler` | 404 |
| SPA (the same index.html on every path) | `SpaHandler` | The nearest `index.html` |
| MPA (an index.html per directory) | `MpaHandler` | `<path>/index.html`, when there is no extension |

**Content is read from the classpath.** You can serve it while it stays inside the jar, and
during development it is read from `build/resources/main`. **The behaviour is the same either way** (requirement F-W-18).

## Static files

```java snippet=assets-mount
```

The second argument is a **directory on the classpath**, not a filesystem path.
With `AssetHandler.mount("/assets", "assets")`,
`src/main/resources/assets/app.css` comes out at `/assets/app.css`.

Two routes are registered: `GET` and `HEAD` on `/assets/*`.

> [!WARNING]
> **`/assets` itself (no trailing path) is not served.** Only `/assets/*` is registered.
> SPA and MPA also register the bare prefix, so they differ here.

`routes()` hands those routes back, so **you can attach attributes to them.**

```java
AssetController assets = AssetHandler.mount("/assets", "assets");
for (Route route : assets.routes()) {
	route.attribute(NO_AUTH, true);
}
install(() -> assets);
```

`routes()` works the same way for `SpaHandler.mount(...)` and `MpaHandler.mount(...)`.
The **count differs**, though — SPA and MPA also register the bare prefix, so there are four
(`GET` and `HEAD` on both `/app` and `/app/*`).

### Caching

| Extension | `Cache-Control` |
| --- | --- |
| `.js` `.css` `.woff` `.woff2` | `public,max-age=31536000,immutable` |
| Everything else | `public,max-age=<assets.max_age>,must-revalidate` |

The default for dynamic responses is `no-store` (requirement F-X-07). **Static serving is the only thing that overrides it.**

```conf
assets {
	max_age            = 0        # ordinary files
	immutable_max_age  = 31536000 # for files with a content hash in the name (one year)
	etag               = true
	if_modified_since  = true
}
```

> [!WARN]
> Whether `immutable` gets attached is decided **by the extension alone.**
> Replace `app.js` with new content and **the browser will not come back for a year.**
> Put a hash in the file name (`app.9f3a1c.js`), or lower `assets.immutable_max_age`.

### Conditional GET

- `ETag` is a weak ETag (md5 of path, modification time and size)
- `Last-Modified` is `URLConnection`'s modification time
- **`If-None-Match` is checked first**; `If-Modified-Since` only when it is absent
- On a match, **a 304 without reading the body**

### What is not there

| | |
| --- | --- |
| Range (partial requests) | **Not supported.** No `206` is returned |
| Per-file compression | **Not supported.** We leave it to the server-wide `server.compression` (helidon) |
| Directory listings | Not served |

## SPA

```java snippet=spa-mount
```

When a request hits `/app/...`, this is how it looks.

1. If a real file exists, **return that** (same headers as `AssetHandler`)
2. Otherwise walk up from that path looking for `index.html`
3. Finally, the `index.html` directly under the base
4. If that is missing too, 404

**It coexists with ordinary routes.** Declare `/api/items` and the SPA will not swallow it.
The tree's precedence (fixed segment &gt; path parameter &gt; wildcard) applies unchanged.

### Rewriting index.html per path

Use this when you want to swap out `<title>` or `<meta>` for crawlers or OGP.

```java snippet=spa-rewrite
```

Paths are written the same way as real routes (`{id}`, a trailing `/*`).
**Matching is done by the same route tree the real routes use** (requirement D-75).

| | |
| --- | --- |
| Precedence | Fixed &gt; variable &gt; wildcard. **It does not depend on the order you wrote them** |
| Path parameters | `context.request().bodyPath().getString("id")` |
| A duplicate path | Fails at registration |
| Your real route list | **Stays separate** (a different instance — requirement F-W-20) |

> [!NOTE]
> When a rewrite happens — and only then — `ETag` and `Last-Modified` are not attached (the content changes per request).
> `Cache-Control` becomes `public,max-age=<assets.max_age>,must-revalidate`.

## MPA

```java snippet=mpa-mount
```

With an extension it returns the file; without one, `<path>/index.html`.
`/docs/guide` → `docs/guide/index.html`.

## Defaults on the safe side

Every path is inspected before anything is served. **Anything dangerous gets a 404** (so we do not reveal what exists).

Rejected: NUL characters, backslashes, empty segments (`//`), and `.` and `..` segments.

> [!TIP]
> Percent-encoded forms like `%2e%2e` are rejected too.
> Each segment is decoded before it is inspected, so a `..` is caught the moment it is reconstructed.

## When you edit during development

`jimbleRun` watches `.html` / `.js` / `.css`.
Save and the resources are rebuilt, and the next request serves them ([Hot reload](./hot-reload)).

> [!TRAP]
> **Swap files in without stopping the app and you can be left looking at the old content.**
> Whether a file exists, and the contents of `index.html`, are remembered in memory,
> and the only way to clear that is `Resources.clearCache()`. With `jimbleRun` it is rebuilt on every swap.
