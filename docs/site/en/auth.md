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

`Auth.login` **regenerates the session id** before storing anything, and **saves**
([Sessions and safe defaults](./session-security)).

> [!TRAP]
> **Do not call `PasswordUtil.check` directly here.** With a `null` hash it returns
> `false` **immediately**, so **a user that does not exist answers measurably faster**
> (being slow is BCrypt's whole job). That timing **lets someone enumerate which ids
> exist.**
>
> `Auth.checkPassword` runs one round anyway before returning `false`.
> **Do not split the message either** — that undoes the point of matching the timing.

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
| Lockout, remember-me | Not yet |
| JWT | **Deliberately not offered.** You cannot revoke one, and it adds key management. If you need API auth, use an opaque token held in the DB |
| OAuth / OIDC / SAML | Not yet |
| A permission table | Roles are plain strings |

A working one is in `examples/approval-auth`.
