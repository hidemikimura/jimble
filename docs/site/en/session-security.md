---
title: Sessions and safe defaults
summary: Sessions, CSRF, flash, and signed cookies
section: Basics
order: 8
---

# Sessions and safe defaults

## Sessions

```java snippet=session-save
```

**Nothing is saved automatically.** Nothing is written unless you call `save()`.
That is to avoid writing on every request that only ever read.

**Change it with `session().put(...)` / `remove(...)` / `clear()`.**
`session().data()` (and `request().session()`) is **a read-only copy**; writing to it throws `UnsupportedOperationException`.
That keeps you from a change that is never marked as changed and so never saved.

A `put` / `remove` / `clear` after `session().destroy()` throws `IllegalStateException` (there is nowhere left to save it).
If you want a value in after logout, put it in before `destroy()`, or on the next request.

Choose where it is stored in `application.conf`.

```conf
session {
	store       = "db"     # none | db | redis | cookie
	cookie_name = "sid"
}
```

| store | Where it fits |
| --- | --- |
| `none` | You do not use sessions (the default) |
| `db` | Several servers. You have a DB |
| `redis` | Several servers. You need the speed |
| `cookie` | You want to keep nothing on the server. The contents are signed |

### Cleaning up expired sessions

Only the `db` store **accumulates rows** (Redis has TTLs, and cookies are not stored server-side at all).
Call the cleanup from a batch.

```java
SessionStores.defaultStore().cleanupExpired();
```

> [!TRAP]
> **Do not write `SessionStores.db().cleanupExpired()`.**
> It keeps working the day you switch the setting to Redis——
> it simply goes on cleaning the DB session table, with no exception and no warning.
> Redis expires its own keys, so **nobody notices that what you meant to clean has changed.**
>
> Call it through `defaultStore()` and it is correct whatever the store is
> (a store with nothing to clean returns `0`).

### Writing your own store

Implement `SessionStore` and swap it in once at startup.

```java
SessionStores.replace(new MemcachedSessionStore());
```

`context.sessionStore(...)` applies to **that request only**.
Use `replace` when you want it to be the application-wide default——
specifying it route by route means **the one route you forget reads from somewhere else.**

### Regenerate the session id on login

**Call `regenerateId()` as soon as the login succeeds.**

```java
context.session().regenerateId();          // new id, same contents
context.session().put("staff_id", staffId);
context.session().save();                  // the new id is issued here
```

If the id does not change across the login, **an id an attacker planted beforehand keeps
working and now carries the privileges** (session fixation). There is no shortage of ways
to plant one — a link on a public page, another subdomain, a browser extension.

**The contents are carried over.** Whatever you put in just before the login — the URL to
return to, a half-filled form, the CSRF token — **would otherwise vanish exactly when the
user logs in**, and they lose their place.

> [!TRAP]
> **Call `save()` after regenerating.** `regenerateId()` does not save (nothing here
> saves by itself). Regenerate without saving and **the old side is gone and the new side
> was never written** — the user is simply not logged in. It **fails closed**, so it is
> not dangerous, but "I logged in and got a 401" starts here.

What it does depends on the store:

| store | What happens |
| --- | --- |
| `db` / `redis` | The old row is deleted and rewritten under the new id |
| `cookie` | **Nothing is keyed by the id**, so the contents cookie is simply rewritten. **The old value does not die at once**: the server drops it `session.timeout` after it was last used, or `session.absolute_timeout` (1 day by default) after it was issued |
| `none` | Nothing |

### The limit from issue (`session.absolute_timeout`)

`session.timeout` (30 minutes by default) counts from the last use, so **a session in constant use never expires**.
The limit from issue (that is, from login, since login regenerates the id) is `session.absolute_timeout`.

| store | Default |
| --- | --- |
| `cookie` | 1 day |
| `db` / `redis` | **only when you write it** (since 2.2.4; without it there is no limit) |

```conf
session {
	store            = "db"
	absolute_timeout = 12h   # log in again 12 hours after login, even if in constant use
}
```

Writing it **caps how long a stolen session id stays usable**.
It is not the default for `db` / `redis` so that upgrading does not log out, on the same day, everyone who has been using the app for more than a day.
An expired session cannot be read and its id is regenerated (with remember-me, the user is logged back in from there).

## CSRF

```java snippet=csrf-form
```

Put `Csrf::verify` in a `before` and the routes below it are protected.
`GET` `HEAD` `OPTIONS` `TRACE` pass through untouched (`Csrf.SAFE_METHODS`).

Take the token with `Csrf.token(context)` and put it in a hidden form field (named `csrf_token`).
It issues one (in a cookie) if there is none yet, so **call it on the page that renders the form**.
(`context.request().csrfToken()` only reads a `csrf-token` header that was sent; it does not issue anything.)

The token sent back is read from **the `X-CSRF-Token` header, then the form body, then the JSON body**.
**The query string (`?csrf_token=...`) is not accepted** (since 2.2.4): a token in the URL ends up in access logs and `Referer`.

**The token lives for `csrf.max_age` (one day by default).** Up to 0.6.x it rode on
`cookie.max_age` (one year by default), so shortening `cookie.max_age` for your own reasons
**shortened the CSRF token with it** — and all you got back was a 403 saying the token was
missing.

### Binding the token to the session (`csrf.bind_session`)

The default is a **double submit cookie**: the request passes if the token sent matches the one in the cookie.
The cookie is signed, so an attacker cannot make up a value — but the token is **not tied to the user**.
An attacker who can write cookies under the same parent domain (another subdomain, say) can plant
**a valid token they obtained themselves** in the victim's browser and have it sent.

```conf
csrf {
	bind_session = true   # since 2.2.4. default false
}
```

With `true`:

| | |
| --- | --- |
| Where it lives | In the **session**, not a cookie. `session.store` must be `db` / `redis` / `cookie`; otherwise using it throws |
| Login | When the session id is regenerated (`Auth.login`, completing two-factor auth, ...), **the token is regenerated too** |
| The new token | Returned in the **`X-CSRF-Token` header** of that response |

**A SPA should replace its token whenever a response carries an `X-CSRF-Token` header.**
A SPA that logs in without reloading keeps the token it got before login, so without replacing it the first POST after login gets 403.

```javascript
const response = await fetch("/login", { method: "POST", headers: { "X-CSRF-Token": csrfToken }, body });
csrfToken = response.headers.get("X-CSRF-Token") ?? csrfToken;
```

> Called from another origin, the header is only readable if CORS sends `Access-Control-Expose-Headers: X-CSRF-Token`.
>
> **After logout there is no token** (the session is thrown away). Get a new one by rendering a form or calling a route that returns it.
>
> Forms already open when you switch to `true` get **one 403** (the token moved).

## Flash

Something you hand to the redirect target exactly once.

```java
context.flash().put("message", "Saved");
context.response().redirect("/");
```

It disappears the moment you read it. It travels signed, inside a cookie.

## Cookies default to secure = true

jimble's cookies carry `Secure` by default. They are only sent over HTTPS.

**Local runs on http, so with that default the browser never sends the cookie back.**
Sessions, CSRF, and flash all stop working — without an error.

This is the textbook silent failure, so jimble **logs a WARN at startup** when
`env=local` and `cookie.secure = true`.

```
cookie.secure = true のままです（env=local）。
ローカルは http なので、ブラウザは Cookie を送り返しません。
application.conf に次を足してください。
  cookie { secure = false }
```

> In English: "cookie.secure is still true (env=local). Local runs on http, so the
> browser will not send the cookie back. Add this to application.conf."

The `jimble new` skeleton ships with that stanza already in it.
**Delete it before you go to production.**

## Cookies

```java
// 30 days
context.cookies().put("last_post", String.valueOf(id), 30L * 24 * 60 * 60);

String lastPost = context.cookies().get("last_post");
```

**A value you write is readable again inside the same request.** If only received cookies
were visible, every re-read of a CSRF token you had issued a moment ago would hand you
a different one.

When you write a `Cookie` whose attributes (`Path`, `Max-Age`) you set yourself, **pick signing by name** (since 1.5.0).

```java
context.cookies().putSigned(new Cookie("user", id).path("/app").maxAge(3600));    // signed (readable with get)
// The signature is bound to the cookie name (2.2.3+): a signature moved to another cookie name does not verify.
// Signatures from 2.2.2 and earlier are read during the move (cookie.accept_legacy_signature, default true).
// A signature carries no expiry: do not trust a signed cookie alone to identify a user — keep that in the session.
context.cookies().putUnsigned(new Cookie("theme", "dark").httpOnly(false));      // not signed (for JavaScript)
```

The 1.x `put(Cookie)` did not sign, so 2.0 removed it ([Moving to 2.0](./migrate-2)).

**Writing a cookie after the response has been sent throws `IllegalStateException`** (it would never arrive). Write it before `send()`.

**Read it back with `context.cookies().get("a name")` or `context.request().cookie("a name")`.**
A value whose signature did not check out never lands there — that is the point, so a tampered
value cannot reach your code.

> [!WARN]
> **`unsignCookie("a name")` does not verify the signature.**
> Despite the name, it hands back the **raw received value**, and an empty string — not `null` —
> when there is nothing. Use `cookie("a name")` when you want the verified value.

The key is `cookie.secret` in `application.conf` (`session.secret` for cookie sessions);
in production, pass it in from an environment variable.

```conf
cookie {
	secret = ${?COOKIE_SECRET}
}
```

**With no key set, signing is off entirely.** Nothing throws, cookies read and write as usual,
and there is no sign that it is not working — so we warn about it at startup.

### Turning signing on later

> [!TRAP]
> **The moment `cookie.secret` is set for the first time, every cookie already out there is
> thrown away at once** — `sid`, `csrf_token`, `remember`, flash. **Everyone is logged out
> and every form returns 403.** Nothing throws and nothing is logged, because discarding a
> cookie whose signature does not check out is the correct behaviour.

**Give yourself a migration window.**

```conf
cookie {
	secret          = ${?COOKIE_SECRET}
	accept_unsigned = true    # only until the changeover is done
}
```

While `accept_unsigned` is true, **unsigned cookies are read too**. Writing signs from the
start, so **leaving it alone replaces them**.

**Turn it back off once they are replaced.** Leave it on and **a cookie with its signature
stripped keeps working**, which is the whole thing you turned signing on to stop. The count
of unsigned cookies let through is `cookie.unsigned` — **wait until it reaches 0**.

## Rotating a key

**Keys can be rotated. Nobody gets logged out.**

Put the new key in `secret` and the outgoing one in `previous_secrets`.
**Writing always uses `secret`; `previous_secrets` is only tried when reading.**

```conf
cookie {
	secret           = ${?COOKIE_SECRET}       # the new key
	previous_secrets = [${?COOKIE_SECRET_OLD}] # the one being retired
}

session {
	secret           = ${?SESSION_SECRET}
	previous_secrets = [${?SESSION_SECRET_OLD}]
}
```

Three steps.

1. **Put the new key first and keep the old one in `previous_secrets`.** Deploy
2. **Wait.** As people come back, their old-key cookies get rewritten with the new key
3. **Drop `previous_secrets`.** Deploy again and you are done

### Knowing when step 3 is safe

**Do not guess.** How often an old key was needed shows up in the metrics (`Metrics.snapshot()`).

| Name | Meaning |
|---|---|
| `cookie.stale_secret` | Cookies read that were signed with an old key |
| `session.stale_secret` | Sessions read that were encrypted with an old key |

**Once these stop climbing, the old key can go.** The startup log carries the count too
(`鍵=cookie=2, session=2`): still 2 means the rotation is in flight — or that you forgot to
drop `previous_secrets`.

If it never quite reaches zero, that is people who come back rarely. Cookies expire at
`cookie.max_age` (a year by default), so that is the longest you would ever wait.

### Cookies you wrote are yours to rewrite

`sid` and `csrf_token` are re-signed for you. **Cookies your app wrote with `cookies().put(...)`
are not** — only the code that wrote one knows what its lifetime should be, and the browser
does not send it back.

```java
if (context.cookies().isStale("last_post")) {
	context.cookies().put("last_post", context.cookies().get("last_post"), 30 * 24 * 60 * 60);
}
```

They still read fine if you skip this; you just cannot drop the old key until they expire.

### `hash.password.pepper` gives no protection

**Despite the name, it is not mixed into the input before hashing.** It is **appended to** the bcrypt hash when stored and stripped off again when checking (a shape inherited from 1.x; changing it would make every stored hash fail to verify).
It does **nothing** to make brute force harder if the DB leaks. If you need protection against a DB leak, store the hash encrypted with `hash.password.encrypt = true` and `cipher.key`.

### Password keys cannot be rotated this way

**`cipher.key` and `hash.password.pepper` are outside this mechanism.**
What they produce lives in a **password column in your database**, not in a cookie, and the only
moment it can be rewritten is **a successful login** — that is the only time the plaintext exists.
So as long as one person has not logged in lately, the old key has to stay.

To change them, do this in your application:

1. Switch `createHash` over to the new key
2. **On a successful login, if the stored hash is in the old form, rebuild and save it**
   (`check(input, hash, encrypted)` lets you name the form to check against)
3. Retire the old key once everyone has moved over

jimble cannot tell you when step 3 is safe. **Decide it from something your application knows —
counting accounts whose last login is old, for instance.**

## Passwords

`PasswordUtil` hashes with BCrypt. **Whether it then encrypts is yours to choose.**

```java
String hash = PasswordUtil.createHash(password);

if (PasswordUtil.check(input, user.getString("password"))) {
	// it matched
}
```

```conf
hash {
	password {
		# If you set cipher.*, you must set this too (startup fails otherwise)
		encrypt = true
		pepper  = ${?PASSWORD_PEPPER}
	}
}

cipher {
	key = ${?CIPHER_KEY}   # 16 / 24 / 32 bytes
	iv  = ${?CIPHER_IV}    # 16 bytes
}
```

**The default is "encrypt if a key is there."** Pin it to a hard `false` and an existing app
that stores encrypted hashes will compare them as plaintext even though the key is
configured — and **nobody can log in any more.** Which mode you are running in shows up
in the startup log (`パスワード暗号化=あり`). Write `encrypt` when you want it stated.

Turning encryption on or off later means rebuilding the hashes you already stored.
`createHash(password, encrypt)` and `check(input, hash, encrypted)` let you pin each side
on its own.

`CipherUtil` is **AES/CBC with a fixed IV.** It is kept so that ciphertext already in your
database can still be read. **Do not use it for anything you encrypt from now on.** The same
plaintext always produces the same ciphertext, and there is no tamper detection.
For anything new, use `Aead` (AES-256-GCM).
