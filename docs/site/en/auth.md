---
title: Login and authorization
summary: One line of before, plus route attributes. Closed by default
section: Basics
order: 9
---

# Login and authorization

**No annotations.** One `before`, plus attributes on the routes.

```java
public class App extends JimbleApp {

	{
		before(Auth::guard);                                    // that is all

		path("/public", () -> {
			attribute(Auth.PUBLIC, true);                       // the whole block is public
			attribute(Auth.NO_SESSION, true);
			get("/guide", Guide::show);
		});

		get("/login",  Login::show).attribute(Auth.PUBLIC, true);
		post("/login", Login::submit).attribute(Auth.PUBLIC, true);

		get("/requests",  RequestController::list);             // needs a login by default
		get("/approvals", ApprovalController::list).attribute(Auth.ROLE, "approver");
	}

}
```

Register `before(Auth::guard)` **first**. It decides whether the request uses a session,
so **anything that touches `session()` before it wins the race.**

## Closed by default

`Auth.PUBLIC` defaults to `false` — a login is required.

**A route someone adds without writing anything is closed.** The other way round, a route
they forgot about is silently open. Both are the same mistake; **what differs is which way
it falls.**

## Route attributes

| Attribute | Default | What it decides |
| --- | --- | --- |
| `Auth.PUBLIC` | `false` | Whether a login is not required |
| `Auth.ROLE` | `""` | The role required. Empty means any |
| `Auth.NO_SESSION` | `false` | Whether to skip sessions entirely |
| `Auth.FULL_AUTH` | `false` | Whether **only someone who just typed their password** may pass (below) |

Write it on a block and it applies to every route in it ([Routing](./routing)).
Override it on the one route that differs.

> [!TRAP]
> **`PUBLIC` and `NO_SESSION` are separate decisions.** The login endpoints need **no
> login but do need a session** — that is where the CSRF token and the post-login session
> live.
>
> Tie them together and **the login page cannot hold a session, so nobody can log in**
> (the 302 comes back fine and the next request is a 401 — we walked into this one).
>
> `NO_SESSION` belongs only on **genuinely public pages**.

## Logging someone in

```java
Data staff = findStaff(loginId);

if (!Auth.checkPassword(password, staff.isEmpty() ? null : staff.getString("password_hash"))) {
	context.flash().put("message", "Wrong login id or password");
	context.response().redirect("/login");
	return;
}

Auth.login(context, Principal.of(
	staff.getLong("id"), staff.getString("name"), staff.getString("role")));

context.response().redirect("/me");
```

`Auth.attemptLogin` does the whole thing: **make them wait, check, and clear the count on
success.** `Auth.login` **regenerates the session id** before storing anything, and
**saves** ([Sessions and safe defaults](./session-security)).

> [!TRAP]
> **Do not call `PasswordUtil.check` directly here.** With a `null` hash it returns
> `false` **immediately**, so **a user that does not exist answers measurably faster**
> (being slow is BCrypt's whole job). That timing **lets someone enumerate which ids
> exist.**
>
> `Auth.attemptLogin` runs one round anyway before returning `false`.
> **Do not split the message either** — that undoes the point of matching the timing.

## When someone keeps getting it wrong

`Auth.attemptLogin` **counts the failures and makes the next attempt wait.** There is
nothing to write — the code above already does it.

```
failures 1-3 ... no wait (typos)
4th ... 1 second
5th ... 2 seconds
6th ... 4 seconds     ... up to the maximum (300 seconds by default)
```

While the wait is still running it answers **429 with `Retry-After`** (like 401, whether
that becomes a redirect is your `error()` handler's decision). After a 24 hour gap the
count starts again.

**It is not "N failures, locked for M minutes".** Anything that stops an account **is a
harassment tool as it stands** — getting it wrong on purpose locks that person out.
Doubling the wait instead makes the attacker's rate effectively zero while **a real user
waits a few seconds.**

> [!TRAP]
> **Count by the login id that was typed in.** Pass the found user's database id and
> **an id that does not exist is never counted** — brute force starts with ids that do
> not exist, and **whether you are made to wait then tells someone which ids are real.**

| | |
| --- | --- |
| Where | The `auth_attempt` table. **Does nothing without a DB** (it says so in the log, once) |
| Keyed by | The login id (**case and surrounding spaces are normalised**, then SHA-256. It is not stored in the clear) |
| Settings | `auth.lockout.*` ([Configuration](./config)) |
| Cleanup | Automatic (once an hour, while counting a failure). `Lockout.cleanup()` if you want it by hand |
| Releasing one | `Lockout.clear(loginId)` |

**This does not replace [rate limiting](./ratelimit).** Rate limiting counts **per IP**, so
one attempt each from a thousand IPs against one account never fires. This counts **per
account**, whoever it comes from. **Use both.**

## Who is logged in

```java
Principal me = Auth.principal(context);

me.id();                  // 0 means nobody
me.name();
me.hasRole("approver");
```

**It never returns `null`.** Nobody logged in gives you `Principal.ANONYMOUS`.

A `Principal` carries **three things: id, display name, role**. Put the whole user object
in the session instead and:

- fixing a name in the DB **leaves the old one until they log in again**
- revoking a role **does nothing until the session expires**
- with cookie sessions, **all of it travels to the browser**

Load the rest from the DB when you need it. If that is expensive, that is what the
[cache](./cache) is for.

## Logging out

```java
Auth.logout(context);
```

**The whole session goes.** Clear only the login keys and the shopping cart or the draft
is **still there for the next person** (which matters on a shared machine).

## Returning 401 and 403

`Auth.guard` only throws an `HttpException`. **Whether that becomes a redirect or JSON is
your `error()` handler's decision.**

```java
error((context, cause, statusCode) -> {

	if (statusCode == 401 && !context.request().acceptJson()) {
		context.response().redirect("/login");
		return;
	}

	context.response().code(statusCode).json("error", cause.getMessage());

});
```

**A missing role is a 403, not a 401.** The status code is how you say that logging in
again will not change the answer. Return 401 and people **keep trying, believing another
attempt will get them in.**

## Staying logged in (remember-me)

```java
{
	before(Remember.restore(App::findPrincipal));   // remember first
	before(Auth::guard);                            // then guard

	post("/password", Password::change).attribute(Auth.FULL_AUTH, true);
}

// Look the user up again by id. The role comes from here, so revoking one takes effect at once
private static Principal findPrincipal (long id) {
	Data staff = findStaff(id);
	return staff.isEmpty() ? null
		: Principal.of(staff.getLong("id"), staff.getString("name"), staff.getString("role"));
}
```

At login, remember them **only when the box was ticked**.

```java
Auth.login(context, principal);

if ("1".equals(request.getString("remember"))) {
	Remember.issue(context, principal);
}
```

> [!TRAP]
> **Register `before(Remember.restore(...))` before `before(Auth::guard)`.** The other way
> round, **guard decides nobody is logged in and only then do you remember them.**
>
> **Do not call `issue` unconditionally** — on a shared machine **the next person gets in.**

### A stolen cookie still cannot change the password

**This is what makes remember-me safe enough to offer.** Someone who came back through
the cookie is not "someone who just typed their password", so a route with
`attribute(Auth.FULL_AUTH, true)` answers **401**.

Put it on password changes, account deletion, payments, and contact details. In code it
is `Auth.fullyAuthenticated(context)`, but **the route attribute is the one you cannot
forget to write.**

### Theft shows up

The cookie holds **`selector:validator`**, and **the validator is replaced on every use.**
A stolen cookie and the real one cannot both stay current, so **a value from before the
last rotation is the signal that one of them was copied.**

On that signal **every remembered login for that user is deleted** (and it goes in the
log). Which of the two used it first **cannot be told apart**, so deleting only one can
leave **the real user locked out and the thief still in.**

| | |
| --- | --- |
| Where | The `auth_remember` table. **Does nothing without a DB** |
| Cookie | `selector:validator`. **The validator is stored as SHA-256** (the selector is just the lookup key, so it is stored as is) |
| Expiry | 30 days since last use, **and** 90 days since it was issued (it always expires eventually, however much you use it) |
| Grace | For 60 seconds after a rotation the old one still works, so **parallel requests do not log people out** |
| Logout | `Auth.logout` deletes it |
| Password change | **Call `Remember.forgetAll(userId)`** (below) |
| Settings | `auth.remember.*` ([Configuration](./config)) |

> [!TRAP]
> **Call `Remember.forgetAll(userId)` when the password changes.** Without it **a stolen
> cookie still works** — which is the whole point of changing the password. It is also
> what "log out everywhere" is.

## "Sign in with Google" (OpenID Connect)

```java
{
	before(Auth::guard);

	// both are "no login needed, but a session is"
	get("/auth/google", Oidc.start("google"))
		.attribute(Auth.PUBLIC, true);

	// third argument: the code screen. Pass it if you use two-factor auth
	get("/auth/google/callback", Oidc.callback("google", App::findOrCreate, "/login/code"))
		.attribute(Auth.PUBLIC, true);
}

// Map whoever turned up to one of your users. Return null to refuse them
private static Principal findOrCreate (OidcUser user) {

	Data staff = findByOidcKey(user.key());        // "google:1234567890"

	return staff.isEmpty() ? null
		: Principal.of(staff.getLong("id"), staff.getString("name"), staff.getString("role"));

}
```

Settings live under `auth.oidc.<name>.*` ([Configuration](./config)). **Put `client_secret`
in the environment**, never in the file. Leave the endpoints out and they are **discovered**
(`issuer` + `/.well-known/openid-configuration`). **Not at startup** — that would stop the
app from booting while the provider is down.

> [!TRAP]
> **Do not add `Auth.NO_SESSION`.** The `state`, the `nonce` and the PKCE verifier all live
> in the session, so with it **nothing is there when they come back**.
>
> `Auth.PUBLIC` you do need — this is the path people take before they are logged in.

### Authorization code + PKCE only

There is no implicit flow. `start` puts a **state, a nonce and a PKCE verifier** in the
session; `callback` checks them.

| | What it stops |
| --- | --- |
| `state` | **Login CSRF** — making you follow the attacker's code so **you end up logged into their account** |
| `nonce` | **Replay of an ID token** that was captured once |
| **PKCE** (S256) | **A stolen authorization code** — without the verifier it cannot be exchanged |

The `state` is **discarded as it is read**: being single-use is the whole point.

### Verifying the ID token

**No external library.** The `n`/`e` (RSA) and `x`/`y` (EC) a JWKS returns go back to a
public key through the JDK's `KeyFactory`, and `java.security.Signature` does the
verification.

Here is what is checked. **Every one of them passes silently if you simply do not look.**

| | |
| --- | --- |
| `alg` | **The header is not trusted.** Only the RS/ES entries in the table are accepted. `none` means "an empty signature counts as verified"; `HS256` means **handing the public key over as a shared secret, so anyone can sign** |
| `kid` | Picks the key. If the JWKS entry declares an `alg`, it has to match |
| `iss` | **Exact match.** A prefix match lets `https://accounts.google.com.evil.jp` through |
| `aud` | Must contain us. **If there is more than one, `azp` too** — otherwise a token minted for another client can be walked in |
| `exp` / `iat` / `nbf` | With a clock-skew allowance. **Tokens issued in the future are refused** |
| `nonce` | Must match the one we sent |

**The reason is never returned.** Answering "nonce mismatch" **lets someone measure how far
they got**. It goes in the log; the response is 401.

> [!TRAP]
> **A matching email does not link to an existing user.** The only lookup key is
> `provider + sub` (`user.key()`).
>
> Linking on email automatically means **one provider that does not verify email addresses
> is enough to take over an account**. To attach a provider to an existing account, make the
> user do it **explicitly while already logged in**.
>
> `user.emailVerified()` is there to read, but **whether to believe it is your call**.

### Using it with two-factor auth

**Pass the path of the code screen as the third argument.**

With it, someone who has two-factor auth enabled and arrives through "Sign in with Google"
is parked with `Mfa.pending` and sent to that screen — exactly as on the password path.

> [!TRAP]
> **Without it, that person cannot get in** (they get a 401).
> In 0.6.0 they were **silently logged in**, so **choosing "Sign in with Google" skipped
> the second factor entirely.** Refusing beats waving them through (fixed in 0.6.1).

### What this does not do

| | |
| --- | --- |
| Store access tokens | No. This ends at "who logged in". To call an external API, keep the token yourself (storage, encryption, revocation and incremental consent all come with it) |
| Make jimble an authorization server | Not offered |
| Register the routes for you | No. You write `get("/auth/google", ...)` yourself ([Principles](./principles)) |

## Two-factor authentication (TOTP)

Once the password is right, **hold the login back** and ask for the code.

```java
// after the password checks out
if (Mfa.isActive(staff.getLong("id"))) {
	Mfa.pending(context, principal);        // not logged in; just parked in the session
	context.response().redirect("/login/code");
	return;
}

Auth.login(context, principal);
```

```java
// POST /login/code
if (!Mfa.complete(context, request.getString("code"))) {
	context.flash().put("message", "That code is not right");
}

context.response().redirect(Mfa.isPending(context) ? "/login/code" : "/");
```

When `complete` returns true it has **already called `Auth.login`** for you.

> [!TRAP]
> **Until the code is in, they are not logged in.** `Auth.principal(context)` returns
> `Principal.ANONYMOUS` and every route other than `/login/code` refuses them.
>
> Which means **`/login/code` needs `Auth.PUBLIC`** — it is a path people take before they
> are logged in. Do not add `Auth.NO_SESSION`: the half-way user lives in the session.

### Enrolling

```java
Mfa.Enrollment enrollment = Mfa.enroll(staffId, "member1@example.com");

// show enrollment.uri() as a QR code (otpauth://totp/...)
// show enrollment.recoveryCodes() this once and never again
```

**`enroll` alone does not turn it on.** After the code is in their authenticator, take the
digits it shows and call `Mfa.activate(staffId, code)`.

```java
if (!Mfa.activate(staffId, request.getString("code"))) {
	context.flash().put("message", "That code does not match. Try again");
}
```

> [!TRAP]
> **The two steps exist so that nobody gets locked out.** Turning it on at `enroll` time
> means **anyone whose QR scan silently failed can never get back in.**

### The secret is stored encrypted

**`enroll` throws if `auth.mfa.secret_key` is not configured.**

A TOTP secret is not like a password hash. A hash costs work to break; **a leaked secret
produces valid codes immediately.** Stored in the clear it turns into "we have 2FA, and one
database leak walks through all of it".

> [!TRAP]
> **Do not reuse `cipher.key` for this.** Setting `cipher.*` means
> **`hash.password.encrypt` must be set too, or startup fails.**
>
> An app storing plain BCrypt today that sets `cipher.key` for 2FA and writes
> `encrypt = true` will find **every stored password read as ciphertext, and nobody can
> log in.** All the user sees is "wrong id or password", so there is nothing to trace it
> back from. **That is why the key is separate** — use `auth.mfa.secret_key` for 2FA.

### Recovery codes

Phones get lost. This is the way back.

| | |
| --- | --- |
| Where they appear | Only in the return value of `enroll`. **The DB keeps SHA-256 only** |
| How many | 10 (`auth.mfa.recovery_codes`) |
| Using one | Type it into the same code box. `verify` tries the authenticator first, then these |
| After use | **It is gone.** Each one works once |
| How many are left | `Mfa.remainingRecoveryCodes(userId)` |

**Running out is not announced.** Whether to show the count is your call.

### Against brute force

| | |
| --- | --- |
| Slowing down | The same `Lockout` machinery (keyed `mfa:<user id>`). **Six digits is a million guesses — unthrottled, a day is enough** |
| The shape | Three free attempts, then 1 → 2 → 4 … seconds, capped at 300. Over the cap the answer is **429** |
| Window | One step either way (`auth.mfa.window`). **Widening it widens the target** — at 10 there are 21 winning codes |
| Reuse | **A code that worked will not work again in its window.** That stops someone reusing one they watched being typed |
| Grace | 300 seconds between the password and the code (`auth.mfa.pending`). After that, **start over** |

### Turning it off

```java
post("/mfa/disable", Mfa2::disable).attribute(Auth.FULL_AUTH, true);
```

> [!TRAP]
> **Prove who they are before calling `Mfa.disable`.** A loose path here lets **whoever
> stole the cookie remove the second factor** — which is the whole of it. Put
> `attribute(Auth.FULL_AUTH, true)` on the route so only someone who **just typed the
> password** can get there.

### What it is, and what it is not

| | |
| --- | --- |
| Scheme | TOTP (RFC 6238 over RFC 4226). HMAC-SHA1, 6 digits, 30 seconds — **the one shape every authenticator app reads** |
| Storage | `auth_mfa` and `auth_mfa_recovery`. **A DB is required** (without one, `enroll` throws) |
| QR images | Not drawn. You get `enrollment.uri()` and render it yourself (no new dependency) |
| SMS / email | No. SIM swaps take SMS codes |
| WebAuthn / passkeys | Not yet |
| "Trusted devices" | No. remember-me is the nearby thing, and **it is a different thing** — that one replaces the password |


A working one is in `examples/approval-auth` — login, code, enrollment and turning it off.

## Basic auth

Operational endpoints can use Basic auth
([Requests and responses](./request-response)).

```java
path("/ops", () -> {
	before(BasicAuth.of("ops", System.getenv("OPS_PASSWORD")));
	attribute(Auth.PUBLIC, true);      // a different mechanism from the session login
	get("/whoami", Ops::whoami);
});
```

## What this does not do

| | |
| --- | --- |
| Annotations (`@PreAuthorize` and friends) | Not used, per the [principles](./principles). Routes declare it as an attribute |
| Showing the user how many days are left | Not offered. Read the row yourself |
| JWT | **Deliberately not offered.** You cannot revoke one, and it adds key management. If you need API auth, use an opaque token held in the DB |
| SAML | Not yet |
| A permission table | Roles are plain strings |

A working one is in `examples/approval-auth`.
