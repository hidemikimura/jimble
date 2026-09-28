---
title: 2.0 への移行
summary: 1.5 のうちに書き換えておけば、2.0 に上げてもほぼ直すところが無い
section: そのほか
order: 6
---

# 2.0 への移行

2.0 では、**戻り値を捨てると効かない・黙って何もしない**API を作り直します。
壊れ方は**コンパイルエラーか例外だけ**で、黙って意味が変わる変更はしません。

**2.0 の書き方は、1.5 に先に入っています。**1.5 のうちに書き換えておけば、2.0 に上げたときに直すところはほとんど残りません。

## 1. まず jimbleCheck を流す

```bash
./gradlew jimbleCheck                  # 1.5 で置き換え先があるもの（J8xx）
./gradlew jimbleCheck --target=2.0     # 2.0 で型や意味が変わるもの（J9xx）も
```

書き換える行が**行番号と新しい書き方つきで**出ます。上から直してください。
誤検知は、その行か前の行に `// jimble-check:ignore J803` と書くと出なくなります。

1.5 は、2.0 で消すものに `@Deprecated(forRemoval = true)` を付けています。
コンパイラの警告（`[removal]`）も同じ一覧です。

## 2. 1.5 のうちに書き換えるもの

置き換え先がもう 1.5 にあります。

| 1.x | 1.5 からの書き方 | jimbleCheck |
| --- | --- | --- |
| `new DBTransaction(db)` / `DBTransaction.transaction(...)` | `db.transaction(tx -> { ... })` か `try (Tx tx = db.begin()) { ...; tx.commit(); }` | J801 |
| `transaction.commitEndTransaction()` | `tx.commit()`（確定して**終わる**） | J801 |
| `transaction.commit()`（終わらない） | `tx.checkpoint()` | J801 |
| `db.beginTransaction()` / `commitEndTransaction()` / `rollbackEndTransaction()` | `db.begin()` / `tx.commit()` / `tx.rollback()` | J802 |
| `Router admin = router.path("/admin")` | `router.path("/admin", admin -> { ... })` | J803 |
| `列.subtract(v)`（割り算を出す） | `列.divide(v)`（引き算なら `minus`） | J804 |
| `Dsl.or(w)` / `Dsl.and(w)` | `Dsl.anyOf(a, b, ...)` / `Dsl.allOf(a, b, ...)` | J805 |
| `cookies().put(cookie)`（署名しない） | `cookies().putSigned(cookie)` / `putUnsigned(cookie)` | J806 |
| `列.eq(null)` / `not(null)` | `列.is_null()` / `is_not_null()` | J807 |
| 文字列 `"now()"` を `set(Data)` / `value(Data)` に入れる | `Dsl.now()` を値に入れる。平らな行なら `setRow(Data)` / `valueRow(Data)` | J808 |
| `rules.validate(db, data);`（戻り値を捨てる） | `Data errors = rules.errors(db, data);` | J809 |
| `long id = db.insert(...)`（採番値か件数か分からない） | `db.insertKey(...)`（件数なら `insertNoReturnKey`） | J810 |
| `context.request().getString("x")` | `context.request().bodyAll().getString("x")` | J101 |
| `data.getInt("x")`（無くても 0） | `data.getInt("x", 既定値)`（読めない値は例外） | —— |

> [!NOTE]
> **`Tx` は例外で抜けたら巻き戻ります。**途中で `HttpException` を投げるのに、自分で `rollback` を書く必要はありません。
> 中で1度でも SQL が失敗していたら、`tx.commit()` は確定せずに `TransactionException`（`DB_004`）を投げます。

## 3. 2.0 で型や意味が変わるもの

1.5 ではまだ正しい書き方ですが、2.0 でコンパイルが通らなくなるか、例外になります。
`--target=2.0` のときだけ jimbleCheck が出します。

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `Data row = db.select(...)`（0件も失敗も `null`） | `Optional<Data>`。失敗は `SqlExecuteException` | J901 |
| `List<Data> rows = db.selectList(...)`（失敗は `null`） | `null` を返さない。失敗は例外 | —— |
| `long db.insert(...)` | `void`。採番値は `insertKey` | J810 |
| `int db.update(...)` / `delete(...)`（失敗は -1） | 件数。失敗は例外 | —— |
| `boolean db.execute(...)` | 件数（`int`）。失敗は例外 | J902 |
| `db.isError()` | 無くなる（失敗は例外） | J903 |
| `boolean DBUtil.load(...)` / `DBLock.lock(...)` / `create(...)` | `void`。失敗は例外。`lock` はトランザクションの外で呼ぶと例外 | J904 |
| `RedisLock.tryLock(...)` | `Optional<RedisLockResult>`。`lock` は取れなければ例外 | J905 |
| `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey` / `selectCached` | 非推奨（`select` / `selectList` / `insert` が同じ意味になる） | J906 |
| `eq(null)` | 例外 | J807 |
| `data.getInt("x")` で無いキー | 例外（`getInt("x", 既定値)` は変わらない） | —— |
| `validate(...)` が一覧を返す | 失敗したら 422 の例外。一覧は `errors(...)` | J809 |
| `Request` が `Data` を継ぐ | 継がない（`bodyAll()` などから読む） | J101 |
| `required()` はキーが無いと検査しない | キーが無ければ失敗 | —— |
| `json(...)` と `redirect(...)` を両方積む | 2つ目を積んだ時点で例外 | —— |
| 検査例外（`throws Exception` / `CodeException`） | 非検査例外 | —— |

## 4. 1.5 で出るようになった警告

1.5 は、2.0 で例外になる書き方を**プロセスで1度だけ**ログに出します（呼び出し元の行つき）。
黙って効いていなかったものなので、出たら直してください。

| 警告 | 何が起きていたか |
| --- | --- |
| `eq(null) は「= NULL」を組み…` | どの行にも当たっていなかった（`where(Data)` の空の値も） |
| `set(Data) は {"set": …} の形を読みます` | 包まない行を渡して、何も入っていなかった |
| `set(Data) は文字列 "now()" を…` | 利用者の入力 `"now()"` が現在時刻になっていた |
| `返し方が2つ以上積まれています` | 最初の1つだけが返り、残りは捨てられていた |
| `送ったあとにヘッダ…` / `応答を送ったあとに Cookie…` | 届いていなかった |
| `本文の JSON を読めませんでした` | 空の Data として扱われていた |
| `paging(50) の件数は効きません` | 先に `paging()` を呼んでいて、50 が無視されていた |
| `request().getString("x") は送られてきた値を読みません` | いつも `null` だった |
| `required() / empty() はキーが無いと検査しません` | キーごと送られないと素通りしていた |
| `destroy() のあとにセッションを変えても保存されません` | ログアウトのあとの値が捨てられていた |

> [!NOTE]
> **`save()` のあとにセッションを変えると、もう一度 `save()` すれば保存されるようになりました**（1.4 までは黙って捨てていました）。
> `Auth.login(...)` は中で `save()` するので、ログインの直後に入れた値が消えていたのはこのためです。
