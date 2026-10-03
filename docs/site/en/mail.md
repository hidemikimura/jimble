---
title: Mail
summary: Send over SMTP. Send from MQ and a failure is retried
section: Runtime
order: 3
---

# Mail

**jimble sends mail over SMTP** (since 2.4.0). No dependency was added: SMTP and MIME are written with the JDK alone.

```java
import io.jimble.util.mail.MailMessage;
import io.jimble.util.mail.Mailer;

Mailer.send(new MailMessage()
	.to("hanako@example.co.jp", "Hanako Yamada")
	.subject("Your request was approved")
	.text("Dear Ms Yamada,\n\nYour expense request (No.123) was approved."));
```

| | |
| --- | --- |
| Recipients | `to` / `cc` / `bcc` (never written into the headers) / `replyTo`. Without a From, `mail.from` from the settings is used |
| Body | `text(...)`, `html(...)`. With both, it is sent as multipart/alternative so the mail client can choose |
| Attachments | `attach("invoice.pdf", bytes, "application/pdf")`. Non-ASCII file names work as they are |
| Headers | `header("List-Unsubscribe", "<mailto:...>")`. Headers jimble writes (From, Subject, ...) cannot be set |
| Charset | UTF-8. Non-ASCII subjects and names use RFC 2047; bodies use base64 |

**A subject, name or header containing a newline, or an address containing angle brackets or spaces, throws as soon as it is built** (to stop header injection).
Passing user input straight through cannot add a Bcc.

> [!TRAP]
> **HTML-only mail tends to land in spam.** Include `text(...)` when you send HTML.
> If user input goes into the HTML, escape it in the template (jte) first.

## Send from MQ

**Do not send directly while handling a request. Queue it in MQ and send from `execute()`.**

- SMTP is slow (hundreds of milliseconds to seconds) and sometimes fails; the request does not wait
- **It is queued in the same transaction.** Mail never goes out for a request that was not saved
- If it fails, MQ retries

```java
public MqStatus execute (DB db, Data row) {

	Data data = row.getDataOptional("data");

	try {
		Mailer.send(new MailMessage()
			.to(data.getString("to"))
			.subject("Your request was approved")
			.text(data.getString("body")));
		return MqStatus.completed;
	} catch (MailException ex) {
		if (ex.isTransient()) {
			throw ex;                  // transient (server busy, unreachable) -> retried up to maxRetry()
		}
		Log.error(ex, "Could not send the mail (retrying will not help)");
		return MqStatus.dead;          // no such recipient, authentication failed -> do not retry
	}

}
```

`MailException.isTransient()` is `true` for SMTP 4xx replies and for connection failures or drops.
It is `false` for 5xx replies (no such recipient, authentication failed) and for malformed messages.

> **The same mail can arrive twice.** MQ does not promise "exactly once" ([MQ](./mq)).
> If the process dies after sending but before writing `completed`, it sends again. For important notices, check a sent record before sending.

## Settings

```conf
mail {
	transport = "smtp"                   # smtp | log | memory
	from      = "noreply@example.com"    # the sender for mail without a From
	from_name = "Approval workflow"

	smtp {
		host     = "smtp.example.com"
		port     = 587                    # without it, from security (starttls 587 / tls 465 / none 25)
		security = "starttls"             # starttls | tls | none
		username = "apikey"
		password = ""
		password = ${?SMTP_PASSWORD}
	}
}
```

Amazon SES, SendGrid, Google Workspace and the like all accept SMTP (STARTTLS on 587 with a user name and password).

| Key | Default | |
| --- | --- | --- |
| `mail.smtp.connect_timeout` | `10s` | how long to wait to connect |
| `mail.smtp.timeout` | `30s` | how long to wait for each read or write |
| `mail.smtp.helo` | this machine's name | the name given in EHLO |
| `mail.smtp.envelope_from` | From | where bounces go |

**Refused for safety**

| | |
| --- | --- |
| STARTTLS chosen but not offered by the server | It does not carry on unencrypted; it throws |
| The TLS certificate's host name does not match | Nothing is sent |
| Authenticating over an unencrypted connection (`security = "none"`) | Refused when the settings are read (the password would travel in clear) |
| Any one recipient refused | Nothing is sent to anyone (a partial delivery means a retry sends again to those who got it) |

### Locally and in tests

**`transport` defaults to `smtp`.** Sending without `mail.smtp.host` throws (so mail never silently fails to go out).

```conf
# conf/application.local.conf
mail.transport = "log"      # do not send; log the recipients, subject and body (WARN)
```

In tests, collect what was sent in memory and check it.

```java
MemoryTransport memory = new MemoryTransport();
Mailer.use(memory);

// ... approve the request ...

assertEquals("Your request was approved", memory.sent().get(0).subject());

Mailer.reset();
```

## Sending through an HTTP API

To send through a service's HTTP API instead of SMTP, implement `MailTransport` and swap it in.

```java
Mailer.use(message -> {
	byte[] raw = message.toMime();     // the raw mail (an API that takes raw mail can use it as is)
	// ... call the API; throw MailException on failure (isTransient = true if worth retrying) ...
});
```

## Not included

| | |
| --- | --- |
| Receiving (IMAP / POP3) | Sending only |
| ISO-2022-JP | UTF-8 only (every current mail client and phone reads UTF-8) |
| Images embedded as attachments in HTML (cid) | Not yet. Reference images by URL |
| Templates | The body is a string. Pass something built with jte |
