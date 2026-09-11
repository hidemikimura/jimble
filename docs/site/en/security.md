---
title: Security
summary: Where to report a vulnerability, and what happens after we get it
section: More
order: 4
---

# Security

## Reporting a vulnerability

**Please do not open a public issue.**

Tell us through GitHub's private vulnerability reporting:

<https://github.com/hidemikimura/jimble/security/advisories/new>

The discussion, the fix and the publication all happen in that thread.
**There is no email address** — one intake means nothing gets lost in a second inbox.

Helpful things to include:

- the version it happens on (`0.3.0`, say)
- the module (`jimble-web`, say)
- how to reproduce it, ideally as a minimal piece of code
- what it lets someone do

## What happens next

| | |
| --- | --- |
| Acknowledgement | **Within a few days.** If you hear nothing, it may not have arrived |
| Deadline for a fix | **Not promised** — this is a one-person project. We work worst impact first |
| Publication | A GitHub Security Advisory, once the fixed version is out |
| Credit | **We credit reporters by name**, unless you would rather we did not |

## Which versions get fixed

**Fixes go into the latest release.** We do not backport to older versions.
This is still 0.x, so upgrading is the fastest path.

## Scope

**In scope** — the `io.jimble` libraries and Gradle plugins published to Maven Central.

**Out of scope:**

- **The sample applications under `examples/`.** They exist to explain things, not to be
  deployed. Their credentials and keys are left at defaults. **Do not run them as they are.**
- The content and appearance of this documentation site.
- **Vulnerabilities in dependencies themselves.** Those are tracked through the Dependency
  graph and Dependabot. If the way jimble uses one is what makes it reachable, that is in scope.
- **Behaviour the configuration asks for.** Set `server.trust_proxy = true` and
  `X-Forwarded-For` is trusted (the default is `false`). Documented behaviour is not a
  vulnerability.

> [!TRAP]
> **A mismatch between the documentation and the implementation is treated as a
> vulnerability.** "I thought it was safe because that is what the page said" is the most
> dangerous way to be wrong.

## Hardening your own application

What jimble gives you lives on the pages for each topic.

| For | See |
| --- | --- |
| Sessions and CSRF, signed cookies, key rotation | [Sessions and safe defaults](./session-security) |
| Rate limiting and bot handling | [Rate limiting](./ratelimit) |
| Whether to trust proxy headers | [Server configuration](./server) |
| Keeping internals out of error responses | [Error handling](./errors) |
| Where keys and connection details live | [Configuration](./config) |
