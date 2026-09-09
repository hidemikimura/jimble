---
title: Utilities
summary: Data, JSON, HTTP client, CSV, crypto. Where to find what
section: More
order: 2
---

# Utilities

`jimble-util` holds **around 28,000 lines** of tools brought over from the code this was ported from.
This page is **a map**. Not an exhaustive list — what you use often, and where you get caught.

> [!NOTE]
> **Tidying this up into a public API is Phase 2.**
> Both the package layout and the names are still as the code this was ported from left them, in places.

## Data

`io.jimble.util.data.Data` is **a subclass of `LinkedHashMap<String, Object>`**.
Requests, SELECT results, and JSON are all handled with it ([Request and response](./request-response)).

```java snippet=data-nest
```

DB values are **read and written by column (`Column`)**. The column versions **follow the table-name nesting.**

| | |
| --- | --- |
| `putData(Column, value)` | **Always builds the nesting** |
| `putDataTakeCare(Column, value)` | Matches the shape already there (flat if there is no nesting) |
| `flattenTable(Table)` / `extractTableData(Table)` | Flatten / pull out. **`null` if it is not there** |

> [!TRAP]
> **The Optional variants — `getStringOptional` and the rest — write to the Data.**
> When the key is missing they **`put` an empty string first** and then return it,
> so reading alone adds keys. Call one just before serialising to JSON, or inside a loop,
> and the output changes.

> [!TRAP]
> **You cannot tell "missing" from "0".** `getString` returns `null`,
> `getInt` returns `0`, `getBoolean` returns `false`.
> When you need to tell them apart, use the **Object versions** — `getIntObject` and friends — or `isNull(key)`.

> [!NOTE]
> **`toString()` is a summary** (keys and types only).
> That is to stop a single log line from spilling the whole contents, or from pulling an `Async` in.
> Call `getJsonString()` when you want the JSON.

## JSON

It is our own implementation (neither Jackson nor Gson).

```java snippet=data-json
```

| What you want | How to write it |
| --- | --- |
| Data → string | `data.getJsonString()` / `getJsonString(true)` (pretty) |
| String → Data | `Data.fromJsonString(json)` |
| Any object | `Dson.encodes(obj)` / `Dson.decodes(json, Xxx.class)` |
| Straight to a stream | `data.outputJsonString(outputStream)` |
| Write it out piece by piece, without building it | `JsonHashWriter` / `JsonArrayWriter` |

> [!WARN]
> **The static `Dson.encodes` / `decodes` do nothing on failure but return `null`.**
> If you need the reason, create a `new Dson()` and check `isError()` /
> `getErrorException()` after `decode(...)`.

## Type conversion

`Convertor.convert(conf, src, Target.class)` is the way in.
Bean ↔ `Data` ↔ Map ↔ List ↔ primitives all go down the same single path.
`data.convert(new MyBean())` is the same machinery underneath.

> [!TRAP]
> **Do not reuse a `Configration`.** Its depth counter and its record of circular
> references are rewritten as the conversion runs. Make **one per conversion.**

## HTTP client

A thin wrapper over the JDK's `HttpClient`, with a class per method.

```java
HttpGetExecutor res = new HttpGetExecutor()
	.setUrl("https://example.com/api")
	.addHeader("Accept", "application/json")
	.setTimeout(5000)
	.execute();

if (res.isError) {
	Log.error(res.errorException, "could not fetch");
	return;
}

Data json = res.getContentJson();
```

- POST is `HttpPostExecutor`. `addBodyForm(name, value)` / `setBodyJson(data)`
- **Add even one file and it becomes multipart** (`addBodyForm(name, file, contentType)`)
- The proxy is `setProxy(new HttpProxy(host, port, id, pass))`
- **It does not throw.** Check `isError` (the same style as the DB)

> [!WARN]
> **The default timeout is 30 seconds**, and **the same value goes to both** connect and response. You cannot set them separately.

> [!TRAP]
> **`setIgnoreSslError(true)` takes effect across the whole JVM.**
> It raises a system property that turns certificate validation off, so
> **every other connection in the same process stops validating too, and it never goes back.**

## CSV

```java
try (CsvReader reader = new CsvReader(new File("in.csv"))) {
	while (reader.next()) {
		String name = reader.getString("name");
	}
}
```

**It reads a line at a time** (it does not put the whole file in memory). For writing, `CsvWriter#writeLine(Object...)`.

> [!WARN]
> **Leave the character encoding out and, when detection fails, you get Shift_JIS.**
> If you know it is UTF-8, write `new CsvReader(file, "UTF-8")`.

## XML

`XmlParser.parse(file)` turns it into a tree of `XmlData` (**all of it goes in memory**).
To build one, `XmlBuilder.build(xmlData)`.

## Hashing and encryption

| What you want | What to use |
| --- | --- |
| Passwords | `PasswordUtil.createHash` / `check` ([Sessions and safe defaults](./session-security)) |
| Signatures (tamper detection) | `Signer.sign` / `unsign` (HMAC-SHA256) |
| Encryption | **`Aead.encrypt` / `decrypt`** (AES-256-GCM) |
| Short IDs | `Hashids` |
| Hash values | `Hash.md5` / `sha256` / `sipHash` / `xxHash64` |

> [!WARN]
> **`CipherUtil` (AES/CBC) takes its IV from the configuration, so it is fixed.** The same plaintext
> always gives the same ciphertext, and tampering cannot be detected. It is kept for compatibility
> with the code this was ported from.
> **Use `Aead` for anything you write new.**

> [!TRAP]
> **The default `Hashids` instance has an empty salt.** The sequence can be guessed, so
> **it cannot hide an ID you do not want seen.** Build your own with a salt if you need that.

## Key-value stored in the DB

```java
DBValue.set(db, "last_imported_at", "2026-09-07");
String value = DBValue.getString(db, "last_imported_at", "");
```

> [!TRAP]
> **When there is no value, `get` writes the default in** (you meant to read, and an INSERT goes out).
> Values are **up to 250 characters**, and there is no API to delete one.
> The cache is per process, so **a `set` on another machine never reaches you.**

## The rest

| Area | Classes |
| --- | --- |
| Strings | `StringUtil` (replace, full-width/half-width, Base62, password generation) / `IcuUtil` (kana, full-width/half-width) |
| Date and time | `DateUtil` (built on `java.util.Date`. `getFrom` / `getTo` mean "00:00:00 / 23:59:59 on that day") |
| URLs | `UrlUtil` / `UrlBuilder` (domain extraction, punycode, encoding) |
| Numbers and parsing | `Parse.parseInt` and friends (**they do not throw on failure**) |
| Regular expressions | `Patterns` (email, URL, domain, phone) |
| Files | `FileUtil` / `IOUtil` / `FileCharDetecter` (character encoding detection) |
| Threads | `VirtualThreadManager` / `ThreadManager` ([Execution model](./execution)) |
| Measurement | `StopWatch` |

> [!NOTE]
> `Patterns`' TLD list is **a hard-coded string**. New TLDs do not match.
