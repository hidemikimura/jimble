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

## CSRF

```java snippet=csrf-form
```

Put `Csrf::verify` in a `before` and the routes below it are protected.
`GET` `HEAD` `OPTIONS` `TRACE` pass through untouched (`Csrf.SAFE_METHODS`).

Take the token with `context.request().csrfToken()` and put it in a hidden form field.

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

To sign a value, sign it with `Cookies.sign(value)` and put it in with `put(Cookie, plaintext)`.
Read it back with `context.request().unsignCookie("a name")`. If it has been tampered with, you get `null`.
The key is `cookie.secret` in `application.conf` (`session.secret` for cookie sessions);
in production, pass it in from an environment variable.

```conf
cookie {
	secret = ${?COOKIE_SECRET}
}
```

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
		# Default: true when cipher.key is set, false when it is not
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
