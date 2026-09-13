---
title: Versions and compatibility
summary: What counts as public API, when it can break, and what we do before breaking it
section: More
order: 5
---

# Versions and compatibility

## We are on 1.0

**Breaking a public API is preceded by a deprecation period of at least one minor.**
jimble follows [Semantic Versioning](https://semver.org/) at its word.

| | 0.x (up to 0.6) | 1.0 onwards (now) |
| --- | --- | --- |
| Breaking a public API | Could happen in a minor | **Deprecated for at least one minor, then removed in the next major** |
| Changing internals | Any time | Any time |
| Writing it in the CHANGELOG | Always | Always |

> [!NOTE]
> **Nothing breaks silently.**
> Anything that can break you goes in the **"上げる前に見るところ" (read before upgrading)**
> and **"変わったこと（挙動）" (behaviour changes)** sections of the
> [CHANGELOG](https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md).
> Reading those alone is enough before you upgrade.

> [!TRAP]
> **A promise starting also means some things can no longer be fixed.**
> Return types, record components, public fields, abstract methods on an `interface`, `final` —
> **a deprecation period saves none of them** (you cannot keep the old one and add the new one
> under the same name).
> Before 1.0 we read through all 103 public packages and 4,514 signatures and dealt with them
> ([docs/design-1.0.md](https://github.com/hidemikimura/jimble/blob/main/docs/design-1.0.md)).

## What counts as public API

**The line is drawn per package.**

| | |
| --- | --- |
| **Public API** | What you touch when writing an application: `io.jimble.db`, `io.jimble.web.server`, `io.jimble.util.data` and so on |
| **Internal** | What only exists inside an entry-point class: `io.jimble.db.internal.sql.query.*` (inside `SQL`), `io.jimble.util.internal.json.encoder` (inside `Dson`) and so on |
| **Preview** | **Tracks an outside specification, so the promise below does not cover it.** Right now that is only `io.jimble.mcp.*` |

The full list is in
[`docs/api-packages.txt`](https://github.com/hidemikimura/jimble/blob/main/docs/api-packages.txt).

**The name says it.**

> **Everything under `io.jimble.<module>.internal` is internal.**

If `internal` appears in an `import`, that is something you must not touch.

> [!TRAP]
> **`public` does not mean public API.** Java has no modifier for "visible outside the
> module but not meant for you", so **internals are `public` too**. The `internal` in the
> name, and the list above, are the line — not the keyword.

> [!NOTE]
> **Names you write in a config file, or type on a command line, are public API.**
> `io.jimble.util.log.encoder.LogbackJsonEncoder` in your `logback.xml`, and
> `io.jimble.db.cli.JimbleDbCli` in a CI job that does not use Gradle, **break your
> setup if we move them** even though nothing imports them. That is why they are not
> under `internal`.

**The list does not go stale.** Add a package and forget to classify it and
`ApiSurfaceTest` fails. The same test checks that the samples under `examples/` do not
import anything internal.

### Preview — deliberately outside the promise

**`io.jimble.mcp.*` is not covered by "one minor of deprecation before anything breaks",
even at 1.0.**

[MCP](./mcp) has published **five revisions in two years, every one of them breaking**
(2026-07-28 dropped sessions and the GET stream). jimble's policy is to **name the
revision it implements and implement exactly one**, so every time the spec moves, the
choice is **break the public API or stop tracking the spec**. Putting it inside the
promise means it can never track the spec again.

The revision in force is readable as **`McpProtocol.version()`**. It is a method rather
than a constant because **`public static final String` is inlined into your application's
bytecode** — as a constant, upgrading jimble would leave your build holding the old
revision string, with no warning.

If you use `io.jimble.mcp.*`, **read the
[CHANGELOG](https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md) before
upgrading**. Breaks are always written there.

## When the signature is the same but the result changes

**This is the part that matters most.**

Almost every way jimble has actually broken people so far **left the signatures alone** —
a 404 became a 405, `insertBatch` started returning `null`, a cancelled batch started
being recorded as `canceled` instead of `completed`. All of it still compiles, so
**upgrading tells you nothing.**

So we split these in two:

| | What we do |
| --- | --- |
| **It was broken and we fixed it** | **Fixed straight away.** No deprecation period |
| **We changed what it does** | From 1.0, **the old behaviour stays for one minor** |

**A fix gets no deprecation period because it cannot have one.** `insertBatch`, for
instance, **silently shifted values sideways** when the stacked builders did not produce
the same SQL. "For one more minor, we will keep writing corrupted rows" is not something
we can offer.

Either way it goes in the **behaviour changes** section of the CHANGELOG. It never gets
buried under "fixed".

## What breaking looks like

1. **Deprecate it** (1.0 onwards). Add `@Deprecated(since = "1.2", forRemoval = true)`
   and say **what to use instead** in the javadoc.
2. **Write it in the CHANGELOG**, including when it goes away.
3. **Wait at least one minor, then remove it** — in the next major.

> [!TRAP]
> **A `@Deprecated` thing still works until it is removed.** All you get is a build
> warning. **Do not switch those warnings off** — otherwise you find out it is gone
> after you upgrade.

## Choosing a version

**Releases are on [Maven Central](https://repo.maven.apache.org/maven2/io/jimble/).**
The dependency coordinates and a build file that works as it stands are on the
[Gradle plugins](./gradle) page.

**This site describes the latest release** (see [About versions](./index)).
