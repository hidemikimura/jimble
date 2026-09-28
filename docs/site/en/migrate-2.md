---
title: Moving to 2.0
summary: Rewrite on 1.5 and there is almost nothing left to fix when you move to 2.0
section: More
order: 6
---

# Moving to 2.0

2.0 rebuilds the APIs that **do nothing when you throw the return value away, or fail silently**.
Every break is either **a compile error or an exception**; nothing changes meaning silently.

**The 2.0 way of writing is already in 1.5.** Rewrite while you are on 1.5 and there is almost nothing left to fix when you move to 2.0.

## 1. Run jimbleCheck first

```bash
./gradlew jimbleCheck                  # things with a replacement in 1.5 (J8xx)
./gradlew jimbleCheck --target=2.0     # plus things whose type or meaning changes in 2.0 (J9xx)
```

It lists every line to rewrite, **with the line number and the new way of writing it**. Fix them from the top.
For a false positive, write `// jimble-check:ignore J803` on that line or the line before.

1.5 marks what 2.0 removes with `@Deprecated(forRemoval = true)`, so the compiler's `[removal]` warnings give the same list.

## 2. What to rewrite on 1.5

The replacement is already in 1.5.

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
| The string `"now()"` in `set(Data)` / `value(Data)` | Put `Dsl.now()` in as the value. For a flat row, `setRow(Data)` / `valueRow(Data)` | J808 |
| `rules.validate(db, data);` (return value thrown away) | `Data errors = rules.errors(db, data);` | J809 |
| `long id = db.insert(...)` (key or count, you cannot tell) | `db.insertKey(...)` (`insertNoReturnKey` for the count) | J810 |
| `context.request().getString("x")` | `context.request().bodyAll().getString("x")` | J101 |
| `data.getInt("x")` (0 when missing) | `data.getInt("x", default)` (unreadable values throw) | — |

> [!NOTE]
> **A `Tx` rolls back when you leave it by an exception.** You can throw an `HttpException` halfway without writing a `rollback` yourself.
> If any SQL failed inside, `tx.commit()` does not commit and throws a `TransactionException` (`DB_004`).

## 3. What changes type or meaning in 2.0

These are still correct on 1.5, but stop compiling or throw in 2.0.
jimbleCheck lists them only with `--target=2.0`.

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `Data row = db.select(...)` (`null` for both no row and failure) | `Optional<Data>`; failure throws `SqlExecuteException` | J901 |
| `List<Data> rows = db.selectList(...)` (`null` on failure) | Never `null`; failure throws | — |
| `long db.insert(...)` | `void`; use `insertKey` for the key | J810 |
| `int db.update(...)` / `delete(...)` (-1 on failure) | The count; failure throws | — |
| `boolean db.execute(...)` | The count (`int`); failure throws | J902 |
| `db.isError()` | Gone (failures throw) | J903 |
| `boolean DBUtil.load(...)` / `DBLock.lock(...)` / `create(...)` | `void`; failure throws. `lock` outside a transaction throws | J904 |
| `RedisLock.tryLock(...)` | `Optional<RedisLockResult>`; `lock` throws when it cannot take the lock | J905 |
| `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey` / `selectCached` | Deprecated (`select` / `selectList` / `insert` mean the same) | J906 |
| `eq(null)` | Throws | J807 |
| `data.getInt("x")` on a missing key | Throws (`getInt("x", default)` is unchanged) | — |
| `validate(...)` returns the list | Throws a 422 on failure; the list comes from `errors(...)` | J809 |
| `Request` extends `Data` | It does not (read from `bodyAll()` and friends) | J101 |
| `required()` skips a missing key | A missing key fails | — |
| Both `json(...)` and `redirect(...)` | Throws when the second one is set | — |
| Checked exceptions (`throws Exception` / `CodeException`) | Unchecked | — |

## 4. New warnings in 1.5

1.5 logs the ways of writing that throw in 2.0, **once per process**, with the calling line.
They were silently not working, so fix them when you see them.

| Warning | What was happening |
| --- | --- |
| `eq(null) は「= NULL」を組み…` (eq(null) builds "= NULL") | It matched no rows (so did an empty value in `where(Data)`) |
| `set(Data) は {"set": …} の形を読みます` (set(Data) reads the wrapped form) | An unwrapped row inserted nothing |
| `set(Data) は文字列 "now()" を…` (the string "now()") | User input `"now()"` became the current time |
| `返し方が2つ以上積まれています` (more than one way of responding) | Only the first one was sent; the rest were dropped |
| `送ったあとにヘッダ…` / `応答を送ったあとに Cookie…` (header / cookie after sending) | They never arrived |
| `本文の JSON を読めませんでした` (could not read the JSON body) | It was treated as an empty Data |
| `paging(50) の件数は効きません` (paging(50) has no effect) | An earlier `paging()` had already been built; 50 was ignored |
| `request().getString("x") は送られてきた値を読みません` (does not read submitted values) | It was always `null` |
| `required() / empty() はキーが無いと検査しません` (skips a missing key) | A key that was not sent at all passed |
| `destroy() のあとにセッションを変えても保存されません` (changes after destroy()) | Values set after logout were dropped |

> [!NOTE]
> **Changing the session after `save()` is now saved if you call `save()` again** (up to 1.4 it was silently dropped).
> `Auth.login(...)` calls `save()` internally, which is why values set right after logging in used to disappear.
