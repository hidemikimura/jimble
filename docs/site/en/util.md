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
> **The Optional variants — `getDataOptional`, `getStringListOptional` and the rest — write to the Data.**
> As the name says, when the key is missing they **create an empty value, `put` it** and then return it,
> so a value written with `data.getDataOptional("x").put(...)` stays.
> The price is that reading alone adds keys. Call one just before serialising to JSON, or inside a loop,
> and the output changes. A value that is already there is not rewritten.
> `getStringOptional` just returns an empty string and does not write.

> [!TRAP]
> **You cannot tell "missing" from "0".** When the key is missing, `null` or blank, `getString` returns `null`,
> `getInt` returns `0`, `getBoolean` returns `false`.
> When you need to tell them apart, use the **Object versions** — `getIntObject` and friends — or `isNull(key)`.
> There is also a form that takes a default (`getInt(key, default)` / `getLong` / `getDouble` / `getBoolean` / `getString`).

**A value that is there but cannot be read throws `DataConversionException`.** That means `"abc"`, `"1.5"` as an int,
an overflow, `"yes"` as a boolean (only `true` / `false` / `1` / `0` are read), and so on.
`getEnum` also throws when nothing matches; if the value may be absent, use `getEnumOptional(key, type)`.
When you read user input with `getInt` and friends, **run it through validation first.** Read it unvalidated,
hit the exception, and the answer is a 500.

- Reading as a different type (a list of numbers through `getStringList`, say) **does not write back into the Data**
- When you want a copy that cannot be changed, use `readOnlyCopy()` (writing to it throws `UnsupportedOperationException`)

> [!NOTE]
> In 1.x an unreadable value silently came back as `0` / `false` / `null` ([Migrating to 2.0](./migrate-2)).

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

**Broken JSON throws `JsonParseException` (unchecked).** `Data.fromJsonString` and `Dson.decodes` behave the same.
Unclosed brackets, something extra at the end, or something that is not JSON at all — each of these throws.
An empty string and `"null"` return `null` (no exception).

> [!NOTE]
> **Only the shape is checked.** The reader is lenient, so as long as the brackets close, a grammar mistake such as
> `"a":` with no value gets through.
> The instance method `new Dson().decode(...)` does not throw; check `isError()` / `getErrorException()` as before.

## Type conversion

`Convertor.convert(conf, src, Target.class)` is the way in.
Bean ↔ `Data` ↔ Map ↔ List ↔ primitives all go down the same single path.
`data.convert(new MyBean())` is the same machinery underneath.
When it cannot convert, it throws an **unchecked exception** (no `throws` or `catch` needed).

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
- **It does not throw.** Check `isError`

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
`CsvReader` / `CsvWriter` **throw no checked exceptions**. A read or write failure is an `UncheckedIOException`.

> [!WARN]
> **Leave the character encoding out and, when detection fails, you get Shift_JIS.**
> If you know it is UTF-8, write `new CsvReader(file, "UTF-8")`.

A **BOM** (the one Excel puts on UTF-8 CSV files) is dropped when reading, as long as the encoding is a UTF one.
If there is a BOM, the file is read in the encoding the BOM says (ask for UTF-8 and get a UTF-16LE BOM, and it is read as UTF-16LE).
Pass a `Reader` and a single leading BOM character is dropped.

To write a BOM, use `setWithBom(true)` (for Excel to open a UTF-8 CSV correctly).
**It only has an effect for UTF encodings**; with Shift_JIS and the like it does nothing.

```java
try (CsvWriter writer = new CsvWriter(new File("out.csv"), "UTF-8").setWithBom(true)) {
	writer.writeLine("name", "count");
}
```

## XML

`XmlParser.parse(file)` turns it into a tree of `XmlData` (**all of it goes in memory**).
**XML with a DOCTYPE is not parsed** (it returns null, so external entities cannot read files).
To build one, `XmlBuilder.build(xmlData)` (it throws no checked exception).

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
| Files | `FileUtil` / `IOUtil` (a failure in `copy` / `readLines` is an `UncheckedIOException`) / `FileCharDetecter` (character encoding detection) |
| Threads | `VirtualThreadManager` / `ThreadManager` ([Execution model](./execution)) |
| Measurement | `StopWatch` |

> [!NOTE]
> `Patterns`' TLD list is **a hard-coded string**. New TLDs do not match.
