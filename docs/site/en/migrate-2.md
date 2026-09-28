---
title: Moving to 2.0
summary: From 1.x to 2.0 — what was removed, what changed type, what now throws
section: More
order: 6
---

# Moving to 2.0

2.0 rebuilds the APIs that **do nothing when you throw the return value away, or fail silently**.
Every break is either **a compile error or an exception**. Nothing changes meaning silently.

**Most of the 2.0 way of writing is already in 1.5.** Rewrite while you are on 1.5 and there is less to fix when you move to 2.0.

## 1. The order to upgrade in

1. **Move to 1.5 and run `./gradlew jimbleCheck --target=2.0`.** It lists every line to rewrite, with the line number and the new way of writing it. Fix what 1.5 already lets you fix (J8xx).
2. **Move to 2.0 and compile.** Everything that was removed or changed type is a compile error here (section 3).
3. **Run `./gradlew jimbleCheck` again.** The 2.0 jimbleCheck always reports J8xx and J9xx (`--target=2.0` makes no difference).
4. **Run your tests.** What now throws (section 4) is not caught by the compiler.

For a false positive, write `// jimble-check:ignore J901` on that line or the line before.

> [!NOTE]
> **`select` returns `Optional<Data>` in 2.0.** `Data row = db.select(...)` is a compile error, so you cannot miss it.
> If in doubt, the 1.x "null when missing" is `db.select(...).orElse(null)`, and "404 when missing" is `.orElseThrow(() -> new HttpException(404, "..."))`.

## 2. What you can rewrite on 1.5

The replacement is already in 1.5. In 2.0 the left-hand side is **gone**.

| 1.x | From 1.5 | jimbleCheck |
| --- | --- | --- |
| `new DBTransaction(db)` / `DBTransaction.transaction(...)` | `db.transaction(tx -> { ... })` or `try (Tx tx = db.begin()) { ...; tx.commit(); }` | J801 |
| `transaction.commitEndTransaction()` | `tx.commit()` (commits **and ends**) | J801 |
| `transaction.commit()` (does not end) | `tx.checkpoint()` | J801 |
| `db.beginTransaction()` / `commitEndTransaction()` / `rollbackEndTransaction()` | `db.begin()` / `tx.commit()` / `tx.rollback()` | J802 |
| `Router admin = router.path("/admin")` | `router.path("/admin", admin -> { ... })` | J803 |
| `column.subtract(v)` (emits a division) | `column.divide(v)` (`minus` for subtraction) | J804 |
| `Dsl.or(w)` / `Dsl.and(w)` | `Dsl.anyOf(a, b, ...)` / `Dsl.allOf(a, b, ...)` | J805 |
| `cookies().put(cookie)` (not signed) | `cookies().putSigned(cookie)` / `putUnsigned(cookie)` | J806 |
| `column.eq(null)` / `not(null)` | `column.is_null()` / `is_not_null()` | J807 |
| The string `"now()"` in `set(Data)` / `value(Data)` | Put `Dsl.now()` in the value. For a flat row, `setRow(Data)` / `valueRow(Data)` | J808 |
| `long id = db.insert(...)` | `db.insertKey(...)` | J810 |
| `context.request().getString("x")` | `context.request().bodyAll().getString("x")` | J101 |
| `Data errors = rules.validate(db, data)` | `rules.errors(db, data)` (in 2.0, `validate` means "422 if it fails") | —— |

## 3. What no longer compiles

### DB

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `Data row = db.select(...)` (`null` for no row and for failure) | `Optional<Data>`. Empty for no row; `SqlExecuteException` on failure. Same for `selectCached` | J901 |
| `long db.insert(...)` | `void`. For the generated key, `insertKey(...)`. For a count (`INSERT ... SELECT`), `execute(...)` | J810 |
| `boolean db.execute(...)` | `int` (rows affected; 0 for DDL). Throws on failure | J902 |
| `db.isError()` / `getError()` / `isDuplicateKeyError()` | Gone. Failures throw; catch `DuplicateKeyException` for unique violations | J903 |
| `boolean DBUtil.load(...)` | `void`. Throws `SqlExecuteException` (`DB_007`) if it cannot connect, so startup stops | J904 |
| `boolean DBLock.create(...)` / `lock(...)` | `void`. Throws on failure | J904 |
| `RedisLockResult RedisLock.tryLock(...)` | `Optional<RedisLockResult>` (empty if not acquired within the wait) | J905 |
| `DBTransaction`, DB's `beginTransaction` and friends | Gone (section 2) | J801 / J802 |
| `db.close()` throws `IOException` | It does not (remove the `catch (IOException e)`, which is now a compile error) | —— |

**Deprecated in 2.0** (`@Deprecated(forRemoval = true)`; removed during 2.x):

| Deprecated in 2.0 | Use instead | jimbleCheck |
| --- | --- | --- |
| `selectOrThrow` / `selectListOrThrow` | `select` / `selectList` (they mean the same now) | J906 |
| `insertNoReturnKey` | `insert` (`execute` if you need the count) | J906 |
| `DB.isBatchSuccess(...)` | Not needed (failures throw) | —— |
| `RedisLockStatus.Failed` | Not needed (`lock` throws, `tryLock` is empty) | —— |

### Building SQL

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `column.subtract(v)` | Gone. `divide` for division, `minus` for subtraction | J804 |
| `Dsl.and(w)` / `Dsl.or(w)` | Gone. `Dsl.allOf(...)` / `Dsl.anyOf(...)` | J805 |

### Web

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `Router.path(String)` | Gone. `path(path, admin -> { ... })` | J803 |
| `Cookies.put(Cookie)` | Gone. `putSigned` / `putUnsigned` | J806 |
| `Request` extends `Data` (`request().getString(...)`) | It does not. Read from `bodyAll()` / `body()` / `bodyQuery()` | J101 |
| `Data errors = rules.validate(db, data)` | `validate` is `void` (throws `ValidationException` if it fails). For the list, `errors(...)` | —— |

### Checked exceptions that are gone

A `catch (IOException e)` or `throws CodeException` may now be a compile error because **nothing in the block throws it**. Remove it.

| 1.x | 2.0 |
| --- | --- |
| `CodeException` (checked) | Extends `RuntimeException`. `catch (CodeException e)` still compiles |
| `LoadingCache.get()` / `LoadingCacheMulti.get(k)` | Unchecked (the loader's exception is the cause) |
| `Convertor.convert`, `CsvReader` / `CsvWriter`, `XmlBuilder.build`, `IOUtil.copy` / `readLines` | Unchecked (`IOException` becomes `UncheckedIOException`; others are wrapped in `CodeException`) |
| `DB.close()` / `RedisLockResult.close()` / `ResultSetFetcher.close()` | No checked exceptions |
| `throws CodeException` on `ValidationRule` / `IValidator` | Unchecked |

## 4. What now throws (and still compiles)

Every one of these **silently did something else in 1.x**. Find them with your tests.

### DB

| Code | 1.x | 2.0 |
| --- | --- | --- |
| An SQL failure | Returned `null` / `-1` / `false`; you checked `isError()` | `SqlExecuteException` (`DuplicateKeyException` for unique violations) |
| `selectList(...)` fails | `null` | Throws. No rows is an empty list (never `null`) |
| `update` / `delete` fails | `-1` | Throws. The return value is the count |
| Empty input to `executeBatch` / `insertBatch` | `null` | An empty list |
| Catching an SQL failure inside a transaction and carrying on | Committed anyway | `tx.commit()` refuses with `TransactionException` (`DB_004`) and rolls everything back |
| `DBLock.lock(...)` outside a transaction | The lock was released at the end of the statement and protected nothing | `IllegalStateException` |
| `RedisLock.lock(...)` cannot acquire | `status()` was `Failed` | `RedisLockException` |
| Failures inside the framework (DB cache, cache invalidation, batch history, migrations) | Dropped without a log | Throw |

> [!NOTE]
> **A `Tx` rolls back when you leave it with an exception.** You do not need your own `rollback` before throwing an `HttpException`.
> To use a unique violation as "update if it exists", catch `DuplicateKeyException` outside the transaction, or use `INSERT ... ON CONFLICT` (a Tx that caught a failure inside cannot commit).

### Building SQL

| Code | 1.x | 2.0 |
| --- | --- | --- |
| `column.eq(null)` / `not(null)` | Built `= NULL`, which matches no row | `SqlBuildException`. Use `is_null()` / `is_not_null()` |
| An empty value (`null`, empty array) in `where(Data)` | Built `= NULL` and the like | Throws (except `is_null` / `is_not_null` / `between` / `in` / `not_in`) |
| `where(Data)` with no `"where"` key | No condition — **every row** | Throws (an empty Data does nothing). Select, Update and Delete |
| `set(Data)` / `value(Data)` with an unwrapped row, or only another table's part | Silently set nothing | Throws. For a flat row, `setRow` / `valueRow` |
| The string `"now()"` | Became the current time | A plain string. Use `Dsl.now()` for the current time |

`apply(Data)` reads all clauses together, so a missing clause just stays missing (no exception).

### Data

| Code | 1.x | 2.0 |
| --- | --- | --- |
| `getInt` and friends on **an unreadable value** (`"abc"`, `"1.5"` as int, overflow, `"yes"` as boolean) | Silently `0` / `false` / `null` | `DataConversionException` |
| `getEnum` with no match | `null` | Throws. If it may be absent, `getEnumOptional(key, type)` |
| Broken JSON to `Data.fromJsonString` / `Dson.decodes` | `null` or a partial result | `JsonParseException` (an empty string and `"null"` give `null`) |
| Reading as another type (`getStringList` on a list of numbers, `getData` on a JSON string) | **Wrote the converted value back** (just reading changed the JSON output) | Does not write back |

> [!IMPORTANT]
> **A missing key (no key, `null` or an empty string) still gives `0` / `false` / `null`.** Only "present but unreadable" throws.
> When you read user input with `getInt` and friends, validate it first. An exception from an unvalidated read is a 500.
> `paging()` reads `?page=abc` leniently on the framework side and gives page 1.

The Optional variants such as `getDataOptional` / `getStringListOptional` still "create an empty one and put it in if missing", as the name says (a value written with `data.getDataOptional("x").put(...)` stays). An existing value is not rewritten.

### Web

| Code | 1.x | 2.0 |
| --- | --- | --- |
| `rules.validate(db, data);` fails | Only returned the list (dropping it let everything through) | `ValidationException`. The framework replies **422** with `{"validation": {field: [messages]}}` |
| `required()` / `empty()` when **the key is not sent at all** | Passed | Fails (rules with `insertRequired()` stay "required only on insert") |
| Queuing two kinds of reply (`json(...)` and `redirect(...)`, say) | Only the first was sent; the rest were dropped | `IllegalStateException` as soon as the second is queued (repeating the same kind is fine) |
| Headers or cookies after the response is sent | Never arrived | Throws |
| An unreadable `application/json` body | An empty Data | `body()` / `bodyJson()` / `bodyAll()` throw a 400 `HttpException` (MCP replies `PARSE_ERROR`) |
| `put` / `remove` / `clear` after `session().destroy()` | Not saved | `IllegalStateException` |
| `session().data().put(...)` | Not marked as changed, so not saved | `UnsupportedOperationException` (a read-only copy). Use `session().put(...)` |
| `paging(50)` after `paging()` | The 50 had no effect | `IllegalStateException` |
| `AbstractExecutor.cancel()` | Only set a flag; the following lines still ran | Leaves right there and goes to `onCancel` |

To build the 422 body yourself, get the list with `errors(...)` (the same `errors` as in 1.5).
For a list of rows, use `errors(db, List)` / `validate(db, List)` (the body is `{"rows": [...]}`).

## 5. What changed in jimbleCheck

- It **always** reports J8xx and J9xx (`--target=2.0` is accepted and does nothing).
- J901 / J905 do not report code that takes the result as an `Optional` (`.orElse(...)`, `Optional<...> x =` and so on).
- `selectCached` / `selectListCached` are no longer in J906 (the names stay; only their return types now match `select` / `selectList`).
- New J907: `session().data().put(...)` and the like (throws in 2.0).
- J809 (dropping the result of `validate`) is gone. Calling `validate(...)` as a statement is the right way in 2.0.

## 6. The warnings 1.5 started logging

1.5 logged each 2.0 exception case **once per process**, with the calling line.
**If 1.5 logged none of these, you do not hit most of section 4.**

| 1.5 warning | 2.0 |
| --- | --- |
| `eq(null) は「= NULL」を組み…` (eq(null) builds "= NULL") | `SqlBuildException` |
| `set(Data) は {"set": …} の形を読みます` (set(Data) reads the wrapped form) | Throws |
| `set(Data) は文字列 "now()" を…` (the string "now()") | A plain string |
| `返し方が2つ以上積まれています` (more than one way of responding) | `IllegalStateException` |
| `送ったあとにヘッダ…` / `応答を送ったあとに Cookie…` (header / cookie after sending) | Throws |
| `本文の JSON を読めませんでした` (could not read the JSON body) | 400 |
| `paging(50) の件数は効きません` (paging(50) has no effect) | `IllegalStateException` |
| `request().getString("x") は送られてきた値を読みません` (does not read submitted values) | Compile error |
| `required() / empty() はキーが無いと検査しません` (skips a missing key) | Fails |
| `destroy() のあとにセッションを変えても保存されません` (changes after destroy()) | `IllegalStateException` |
