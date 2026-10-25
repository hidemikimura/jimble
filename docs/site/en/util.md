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

**Broken JSON throws `JsonParseException` (unchecked).** `Data.fromJsonString` and `Dson.decodes` behave the same, and so does reading from a Reader or an InputStream.
The exception says where the problem is (`line 3, column 15` — written as `3 行 15 文字目`). An empty string and `"null"` return `null` (no exception).

**Leniency with a clear meaning is accepted; anything that would silently damage the data is refused** (since 2.6.0; the reader is `jimble-json`).

| | Examples |
| --- | --- |
| Accepted | Unquoted keys and values (`{name: jimble}`), single quotes, trailing commas, comments (`//` and `/* */`), `NaN` / `Infinity`, `+1` / `01` / `.5`, raw newlines inside strings |
| Refused | A missing separator (`{"a":1 "b":2}` / `[1,,2]`), a key with no value (`{"a":}`), broken escapes (`\q` / `\u12`), unclosed brackets, something extra at the end, a top level that is neither an object nor an array |

> [!TRAP]
> **Up to 2.5, missing separators and broken escapes went through silently.**
> `{"a":1 "b":2}` read both values, and `"\q..."` swallowed the next four characters and moved the end of the string.
> Since 2.6.0 they throw.

**There are limits.** The JSON comes from outside, so going over one throws.

| | Limit |
| --- | --- |
| Nesting depth | 512 |
| Length of one number | 1,000 characters |
| Length of one string | 20 million characters |

> [!NOTE]
> The strings `"true"` / `"false"` become Boolean when read without a target type (`Data` / `Map` / `List`), as in 2.5,
> because some APIs return booleans as strings. `getString` on the `Data` gives back `"true"`.
>
> The instance method `new Dson().decode(...)` does not throw; check `isError()` / `getErrorException()` as before.

### Reading into a type

Beans, records, and Lists of them are **filled while reading, without building a tree (Data)** (since 2.7.0).

```java
Order order = Dson.decodes(json, Order.class);                     // as before
List<Item> items = Dson.decodes(json, List.class, Item.class);

Order order = Json.read(json, Order.class);                        // jimble-json
List<Item> items = Json.read(json, new TypeRef<List<Item>>() {});  // a type with type arguments
```

- A record is built with its canonical constructor. Components missing from the JSON are `null` (`0` / `false` for primitives)
- A Bean is built with its no-argument constructor, and **each value goes through the setter if there is one, otherwise into the field** (private fields of superclasses included)
- **Unknown keys are skipped** (no exception)
- Nested objects, `List<Item>`, `Map<String, Item>`, `Optional`, arrays, and type variables (`TypeRef<Box<Item>>`) are read as their types

Values are converted to the property's type. **Anything that would change the value is refused.**

| | Examples |
| --- | --- |
| Accepted | `"12"` → int, full-width `"１２"`, `"1,234"`, `1.0` → int, `"yes"` / `"on"` / `1` → true, enum names (any case), `"2026/10/24 10:00:00"` · ISO 8601 (with offsets) · epoch millis → date-times, empty string → `null` (`0` for primitives) |
| Refused | `1.5` → int, overflow, `"abc"` → int, `"maybe"` → boolean, an enum name that does not exist, `2024-02-30` (an impossible date), a date with something extra at the end |

A refusal throws `JsonParseException` (`JsonException` from `Json.read`) carrying **which property (`$.items[1].qty`) and the line and column**.

> [!TRAP]
> **Up to 2.6, values that could not be read silently became `0` or `null`** (`"abc"` → int gave `0`, `1.5` → int gave `1`).
> Properties with only a setter got no value, and private fields of superclasses were not filled.

To put `user_name` into a `userName` property, pass `JsonReadOptions.builder().snakeCaseKeys(true)`
(`Configration#setDefaultFieldDelimiterChar()` for `Dson`).

### Writing

Date-time and other types are written like this (since 2.7.0; before that, anything other than `Date` and `LocalDateTime` turned the JSON into `{}` or cut it off midway).

| Type | Form |
| --- | --- |
| `Date` · `Timestamp` · `LocalDateTime` | `yyyy/MM/dd HH:mm:ss` (nothing below seconds) |
| `Instant` · `OffsetDateTime` · `ZonedDateTime` · `Calendar` | `yyyy/MM/dd HH:mm:ss` in the server's time zone |
| `LocalDate` · `java.sql.Date` | `yyyy/MM/dd` |
| `LocalTime` · `java.sql.Time` | `HH:mm:ss` |
| `Duration` · `Period` | ISO 8601 (`PT1H30M` / `P1Y2M`) |
| `UUID` · `URI` · `File` and other JDK classes | A string (`toString`) |
| `Optional` | Its content (`null` when empty) |
| enum | Its name |
| record | An object, components in declaration order |
| Bean | An object, fields in declaration order (superclass first) |

- **Bean keys come out in declaration order** (since 2.7.0; before that, the order could change each time the JVM started)
- **A cyclic reference writes `{}` / `[]` the second time and carries on** (as in 2.6)
- **Nesting is written up to 512 levels.** Beyond that nothing is written; `Dson.encodes` returns an empty string and logs an error (up to 2.6, anything past 50 levels silently became `{}` / `[]`)

To keep offsets, to throw on a cycle, or to change how a type is written, use `Json.write(value, options)` from `jimble-json`:

```java
JsonWriteOptions options = JsonWriteOptions.builder()
	.keepOffset(true)                 // Date / Instant / OffsetDateTime and so on as 2026-10-24T19:00:00+09:00
	.cycle(JsonCycle.THROW)           // throw on a cycle, with where it happened (the default for Json.write)
	.typeWriter(Money.class, (value, out) -> out.string(((Money) value).format()))
	.build();

String json = Json.write(value, options);
```

## Type conversion

`Converter.convert(value, Target.class)` from `jimble-converter` is the way in (since 2.7.0; it depends on no other jimble module).
It converts between Beans, Maps, Lists, records, primitives, and date-times.
From a `Data`, use `data.convertClass(MyBean.class)` or getters such as `getIntList`; they are the same machinery underneath.
When it cannot convert, it throws an **unchecked exception** (no `throws` or `catch` needed).

```java
Order order = Converter.convert(map, Order.class);
List<Item> items = Converter.convert(rows, new TypeRef<List<Item>>() {});

ConverterOptions options = ConverterOptions.builder()
	.rejectUnknownKeys(true)          // refuse unknown keys
	.zone(ZoneId.of("Asia/Tokyo"))    // time zone for date-times without an offset
	.typeConverter(Money.class, value -> Money.parse(value.toString()))  // add a conversion for a type
	.build();
Order strict = Converter.convert(map, Order.class, options);

int qty = Converter.convert("abc", int.class, ConverterOptions.LENIENT);  // unreadable values become 0 / null (for CSV and the like)
```

The rules are the same as for [reading JSON into a type](#reading-into-a-type).

- **Anything that would change the value throws** (`ConversionException`, carrying which property: `$.items[1].qty`): `1.5` → int, overflow, `"abc"` → int, an enum name that does not exist, an impossible date, a date with something extra at the end
- Leniency with a clear meaning is accepted: surrounding blanks, full-width digits, `"1,234"`, `1.0` → int, `true` → 1, `"yes"` / `"on"` → true (`"no"` / `"off"` → false), `"2024年01月15日"` and `"2024/1/5 9:00"` (one-digit month, day or hour) → a date
- A Bean is filled **through its setter when there is one** (private fields of superclasses included; never `static` / `transient` fields). A record is built with its canonical constructor
- **Unknown keys are skipped**
- Date-times convert between `Date`, `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime`, and so on. Offsets such as `Z` and `+09:00` are honoured (values without one use the server's time zone)
- A value that already has the target type is returned as is, without copying
- A circular reference throws (`cycleToNull(true)` turns it into `null` at that point)
- `Class` / `InetAddress` / `Pattern` / `File` / `Path` are never built from a string (outside text could trigger class loading, DNS lookups, ReDoS, or file access). Add a `typeConverter` if you need one

> [!TRAP]
> **Up to 2.6, values that could not be read silently became `null` / `0`** (`"abc"` → Integer gave `null`, `1.9` → Integer gave `1`, `3000000000` → Integer overflowed, `"no"` → Boolean gave true, and the offset in `"...Z"` was ignored).
> Properties with only a setter received no value.

### Moving off `Convertor`

`Convertor` / `IConvertor` / `ConvertorConfigKeys` / `PropertyUtil` in `io.jimble.util.convertor` are **deprecated in 2.7.0** (their behaviour is unchanged; underneath they use `Converter`).

| Before | After |
| --- | --- |
| `Convertor.convert(null, map, Order.class)` | `Converter.convert(map, Order.class)` |
| `Convertor.convert(null, rows, List.class, Item.class)` | `Converter.convert(rows, new TypeRef<List<Item>>() {})` |
| `Convertor.convert(null, data, Order.class)` | `Converter.convert(data, Order.class)` (a `Data` is a Map, so pass it as is; `data.convertClass` turns failures into `null`, so use this when you want the exception) |
| `Convertor.addConvertor(Money.class, converter)` | `ConverterOptions.builder().typeConverter(Money.class, conversion)` |
| `Configration#setDefaultFieldDelimiterChar()` (`user_name` → `userName`) | `ConverterOptions.builder().snakeCaseKeys(true)` |

`Convertor` keeps some of the old leniency that `Converter` does not have. Check these when you move to `Converter`:

- **A List / Map that already has the target type is still copied** (`Converter` returns it as is)
- **JSON text is parsed** (`"[1,2]"` → `List<Integer>`, `"{...}"` → `Data`; `Converter` does not parse JSON, so read it with `Dson.decodes` first)
- **A Map becomes the list of its values, and a list or Map becomes its first value as a string** (`Converter` throws)
- **A circular reference becomes `null` at that point** (`Converter` throws by default)

Converters added with `Convertor.addConvertor`, date formats passed in a `Configration` (`CONVERT_STRING_TO_DATE` / `CONVERT_DATE_TO_STRING`), and XML `Document`s still go through the previous converters when called from `Convertor` / `Data`.

> [!TRAP]
> **Do not reuse a `Configration`.** When the previous converters run, its depth counter and its record of
> circular references are rewritten as the conversion runs. Make **one per conversion.**

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
> **`setIgnoreSslError(true)` checks neither the certificate nor the host name of that connection.** Do not use it in production.
> Up to 2.2.2 it raised a system property that **turned host-name checking off for the whole JVM**, so every other connection
> in the process (including fetching OIDC tokens and JWKS) stopped checking host names too. From 2.2.3 it affects that one client only.

- **Redirects are not followed from https to http** (since 2.2.3). The target host is not checked, so if you check URLs
  against an allow-list before fetching, use `setEnableRedirect(false)`
- **A body read into memory is capped at 50MiB after decompression** (`setMaxResponseSize(bytes)`); above that the body is not read

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

**Values starting with `= + - @`, a tab or CR (and the full-width `＝＋－＠`) are written with a leading `'`** (since 2.2.3).
Excel runs such cells as formulas, so a user-supplied `=HYPERLINK(...)` would run on the machine of whoever opened the CSV.
Values that read as numbers (`-5` and so on) are left alone. Turn it off with `setEscapeFormula(false)`.

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
