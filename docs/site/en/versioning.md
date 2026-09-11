---
title: Versions and compatibility
summary: What counts as public API, when it can break, and what we do before breaking it
section: More
order: 5
---

# Versions and compatibility

## We are on 0.x

**While we are on 0.x, a minor release can break things.**
[Semantic Versioning](https://semver.org/) says so itself — below 1.0 nothing is promised —
and jimble takes it at its word.

**From 1.0, breaking a public API is preceded by a deprecation period of at least one minor.**

| | 0.x (now) | 1.0 onwards |
| --- | --- | --- |
| Breaking a public API | **Can happen in a minor** | Deprecated for at least one minor, then removed in the next major |
| Changing internals | Any time | Any time |
| Writing it in the CHANGELOG | **Always** | Always |

> [!NOTE]
> **Even on 0.x, nothing breaks silently.**
> What is not promised is that things will not break — not that you will not be told.
> Anything that can break you goes in the **"変わったこと（挙動）" (behaviour changes)**
> section of the [CHANGELOG](https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md).
> Reading that section alone is enough before you upgrade.

## What counts as public API

**The line is drawn per package.**

| | |
| --- | --- |
| **Public API** | What you touch when writing an application: `io.jimble.db`, `io.jimble.web.server`, `io.jimble.util.data` and so on |
| **Internal** | What only exists inside an entry-point class: `io.jimble.db.sql.query.*` (inside `SQL`), `io.jimble.util.json.encoder` (inside `Dson`) and so on |

The full list is in
[`docs/api-packages.txt`](https://github.com/hidemikimura/jimble/blob/main/docs/api-packages.txt).

> [!TRAP]
> **`public` does not mean public API.** Java has no modifier for "visible outside the
> module but not meant for you", so **internals are `public` too**. Whether you may
> touch something is decided by the list above, not by the keyword.

**The list does not go stale.** Add a package and forget to classify it and
`ApiSurfaceTest` fails. The same test checks that the samples under `examples/` do not
import anything internal.

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
