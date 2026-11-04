---
title: Bot protection (ALTCHA)
summary: Check that a form's sender is a person, without signing up for any outside service
section: Basics
order: 11
---

# Bot protection (ALTCHA)

Before a form is sent, **the browser solves a moderately heavy computation**, and the server checks the answer
(proof-of-work). Unlike CAPTCHAs that make people read distorted text, the user ticks "I'm not a robot" and waits a few seconds.

- **No outside service to sign up for.** Unlike reCAPTCHA or Turnstile, neither you nor your tenants register anywhere
- **No separate server.** Creating and checking challenges both happen inside the app
- **No domains to register.** Tenants on their own domains work as they are, however many there are
- Your users' data does not leave your app

The browser side is the [ALTCHA](https://altcha.org) widget (`altcha` 3.x, MIT).
jimble provides the two server pieces: a route that hands out challenges, and a filter that checks answers.

## What you write

```java snippet=altcha-routes
```

`Altcha::verify` works like `before`: **it applies only to the block you write it in.**
Methods that do not change state (GET and so on) pass straight through, so it can share a block with the route that shows the form.

Put the key in the configuration. **At least 32 characters, and always read it from the environment.**

```conf
altcha {
	secret = ${?ALTCHA_SECRET}
}
```

With no key, or a short one, both handing out a challenge (`/altcha/challenge`) and checking an answer stop with an error.

## Placing the widget

```html
<script type="module" src="https://cdn.jsdelivr.net/npm/altcha@3.5.0/dist/main/altcha.js"></script>

<form method="post" action="/contact">
	...
	<altcha-widget challenge="/altcha/challenge"></altcha-widget>
	<button>Send</button>
</form>
```

- **Pin the version** (not a range like `altcha@3`). Installing it from npm and serving it yourself is fine too
- The widget puts the solved answer in the form's `altcha` field
- If you send with `fetch` instead of a form, put the answer in the JSON body's `altcha` or in the `X-Altcha` header
- If your app sets a CSP, the widget uses Web Workers; check ALTCHA's documentation and add what it needs (jimble sends no CSP by default)

## When it refuses

No answer, a malformed one, an expired one, a tampered one, or one already used: all of these are **403**.
The messages come out of jimble in Japanese; the English in parentheses is a gloss.

| Result (`AltchaResult`) | Message |
| --- | --- |
| `MISSING` | 「ロボットでないことの確認（ALTCHA）が送られていません」（"no ALTCHA answer was sent"） |
| `MALFORMED` | 「ロボットでないことの確認（ALTCHA）の形が正しくありません」（"the ALTCHA answer is malformed"） |
| `EXPIRED` | 「ロボットでないことの確認（ALTCHA）の期限が切れています。もう一度確かめてください」（"expired; try again"） |
| `INVALID` | 「ロボットでないことを確かめられませんでした」（"could not confirm you are not a robot"） |
| `REPLAYED` | 「ロボットでないことの確認（ALTCHA）はもう使われています。もう一度確かめてください」（"already used; try again"） |

To show the form again instead of a 403, take the result from `Altcha.check`.

```java
post("/contact", context -> {
	AltchaResult result = Altcha.check(context.request().bodyForm().getStringOptional(Altcha.FIELD_NAME));
	if (!result.verified()) {
		// show result.message() and return the form
	}
	...
});
```

> [!WARN]
> **`Altcha.check` records a passing answer as used.** Call it twice with the same answer and the second call returns `REPLAYED`.
> Do not call `check` again inside a route already covered by `before(Altcha::verify)`.

## Each answer passes once

Once an answer passes, **a "used" mark is kept until the challenge expires**, and the same answer is refused after that.
The mark lives in the same store as rate limiting (`rate_limit.store`).

> [!WARN]
> **If you run more than one instance, set `rate_limit.store` to `redis` or `db`.**
> With `memory`, the mark exists only on one instance, so the same answer sent to another instance passes.

If the store is unavailable (Redis is down, say), the answer **passes and the failure is logged**. That matches rate limiting:
a store outage should not stop every form in the app. The signature and the answer have already been checked.

## Weight and expiry

```conf
altcha {
	cost        = 5000    # weight of one attempt (PBKDF2 iterations)
	counter_max = 10000   # upper bound of the answer, picked from counter_max / 2 up to counter_max
	expires     = 10m     # how long a challenge stays valid
}
```

The browser tries answers from 0 upward, so the work is about `cost × answer` computations.
**With the defaults, Chrome on a 10-core Mac took 3.1 seconds for an answer near the upper bound.**
Older phones take several times that. Heavier settings bother bots more, and people too.

The widget fetches a challenge **when the user touches it**, not when the page opens, so the expiry does not need to be long.

## This alone does not stop everything

Proof-of-work **makes bots spend effort**. A bot willing to compute gets through.

- Limit how often forms are sent with [rate limiting](./ratelimit) (putting it on `/altcha/challenge` as well limits floods of challenge requests)
- Limit login attempts with `Lockout.attempt` ([authentication](./auth))
