---
title: 2.0 への移行
summary: 1.x から 2.0 へ。消したもの・型が変わったもの・例外になったもの
section: そのほか
order: 6
---

# 2.0 への移行

2.0 では、**戻り値を捨てると効かない・黙って何もしない**API を作り直しました。
壊れ方は**コンパイルエラーか例外だけ**です。黙って意味が変わる変更はしていません。

**2.0 の書き方の多くは 1.5 に先に入っています。**1.5 のうちに書き換えておくと、2.0 に上げたときに直すところが少なくなります。

## 1. 上げる順番

1. **1.5 に上げて、`./gradlew jimbleCheck --target=2.0` を流す。**書き換える行が行番号と新しい書き方つきで出ます。1.5 のうちに直せるもの（J8xx）は直しておきます。
2. **2.0 に上げてコンパイルする。**消したもの・型が変わったものは、ここで全部エラーになります（下の 3.）。
3. **`./gradlew jimbleCheck` をもう一度流す。**2.0 の jimbleCheck は J8xx と J9xx をいつも出します（`--target=2.0` は付けても付けなくても同じ）。
4. **テストを流す。**例外になった書き方（下の 4.）は、コンパイルでは見つかりません。

誤検知は、その行か前の行に `// jimble-check:ignore J901` と書くと出なくなります。

> [!NOTE]
> **2.0 の `select` は `Optional<Data>` です。**`Data row = db.select(...)` はコンパイルエラーになるので、見落としません。
> 迷ったら、1.x と同じ「無ければ null」は `db.select(...).orElse(null)`、「無ければ 404」は `.orElseThrow(() -> new HttpException(404, "..."))` です。

## 2. 1.5 のうちに書き換えられるもの

置き換え先が 1.5 にあります。2.0 では左の書き方が**消えています**。

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
| `long id = db.insert(...)` | `db.insertKey(...)` | J810 |
| `context.request().getString("x")` | `context.request().bodyAll().getString("x")` | J101 |
| `Data errors = rules.validate(db, data)` | `rules.errors(db, data)`（2.0 の `validate` は「通らなければ 422」） | —— |

## 3. コンパイルが通らなくなるもの

### DB

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `Data row = db.select(...)`（0件も失敗も `null`） | `Optional<Data>`。0件は空、失敗は `SqlExecuteException`。`selectCached` も同じ | J901 |
| `long db.insert(...)` | `void`。採番値は `insertKey(...)`。件数が要る `INSERT ... SELECT` は `execute(...)` | J810 |
| `boolean db.execute(...)` | `int`（当たった件数。DDL は 0）。失敗は例外 | J902 |
| `db.isError()` / `getError()` / `isDuplicateKeyError()` | 無い。失敗は例外で、一意制約は `DuplicateKeyException` で受ける | J903 |
| `boolean DBUtil.load(...)` | `void`。繋がらなければ `SqlExecuteException`（`DB_007`）で起動が止まる | J904 |
| `boolean DBLock.create(...)` / `lock(...)` | `void`。失敗は例外 | J904 |
| `RedisLockResult RedisLock.tryLock(...)` | `Optional<RedisLockResult>`（待っても取れなければ空） | J905 |
| `DBTransaction`・DB の `beginTransaction` など | 無い（上の 2.） | J801 / J802 |
| `db.close()` が `IOException` を投げる | 投げない（`try` の `catch (IOException e)` がエラーになるので消す） | —— |

**非推奨になったもの**（2.0 で `@Deprecated(forRemoval = true)`。2.x のうちに消します）：

| 2.0 で非推奨 | 置き換え | jimbleCheck |
| --- | --- | --- |
| `selectOrThrow` / `selectListOrThrow` | `select` / `selectList`（同じ意味になった） | J906 |
| `insertNoReturnKey` | `insert`（件数が要るなら `execute`） | J906 |
| `DB.isBatchSuccess(...)` | 要らない（失敗は例外） | —— |
| `RedisLockStatus.Failed` | 要らない（`lock` は例外、`tryLock` は空） | —— |

### SQL を組む側

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `列.subtract(v)` | 無い。割り算は `divide`、引き算は `minus` | J804 |
| `Dsl.and(w)` / `Dsl.or(w)` | 無い。`Dsl.allOf(...)` / `Dsl.anyOf(...)` | J805 |

### Web

| 1.x | 2.0 | jimbleCheck |
| --- | --- | --- |
| `Router.path(String)` | 無い。`path(パス, admin -> { ... })` | J803 |
| `Cookies.put(Cookie)` | 無い。`putSigned` / `putUnsigned` | J806 |
| `Request` が `Data` を継ぐ（`request().getString(...)`） | 継がない。`bodyAll()` / `body()` / `bodyQuery()` から読む | J101 |
| `Data errors = rules.validate(db, data)` | `validate` は `void`（通らなければ `ValidationException`）。一覧は `errors(...)` | —— |

### 検査例外が無くなったもの

`catch (IOException e)` や `throws CodeException` は、**投げないものを捕まえている**としてコンパイルエラーになることがあります。消してください。

| 1.x | 2.0 |
| --- | --- |
| `CodeException`（検査例外） | `RuntimeException` の子。`catch (CodeException e)` はそのまま書ける |
| `LoadingCache.get()` / `LoadingCacheMulti.get(k)` | 非検査（読み込みの例外は cause） |
| `Convertor.convert`、`CsvReader` / `CsvWriter`、`XmlBuilder.build`、`IOUtil.copy` / `readLines` | 非検査（`IOException` は `UncheckedIOException`、ほかは `CodeException` で包む） |
| `DB.close()` / `RedisLockResult.close()` / `ResultSetFetcher.close()` | 検査例外を投げない |
| `ValidationRule` / `IValidator` の `throws CodeException` | 非検査 |

## 4. 例外になったもの（コンパイルは通る）

どれも **1.x では黙って違うことをしていた**書き方です。テストで見つけてください。

### DB

| 書き方 | 1.x | 2.0 |
| --- | --- | --- |
| SQL の失敗 | `null` / `-1` / `false` を返し、`isError()` で見る | `SqlExecuteException`（一意制約は `DuplicateKeyException`） |
| `selectList(...)` の失敗 | `null` | 例外。0件は空リスト（`null` を返さない） |
| `update` / `delete` の失敗 | `-1` | 例外。戻り値は件数 |
| `executeBatch` / `insertBatch` の空の入力 | `null` | 空のリスト |
| トランザクションの中で SQL の失敗を catch して続ける | そのままコミットされた | `tx.commit()` が `TransactionException`（`DB_004`）で断り、全部巻き戻す |
| `DBLock.lock(...)` をトランザクションの外で呼ぶ | 鍵が文の終わりで外れ、何も守らなかった | `IllegalStateException` |
| `RedisLock.lock(...)` で取れない | `status()` が `Failed` | `RedisLockException` |
| 枠組みの中の失敗（DB のキャッシュ・キャッシュの無効化・バッチの履歴・マイグレーション） | ログも出ずに捨てていた | 例外 |

> [!NOTE]
> **`Tx` は例外で抜けたら巻き戻ります。**途中で `HttpException` を投げるのに、自分で `rollback` を書く必要はありません。
> 一意制約を「あれば更新」に使うなら、トランザクションの外で `catch (DuplicateKeyException e)` するか、`INSERT ... ON CONFLICT` を使ってください（中で受け止めると、その Tx は確定できません）。

### SQL を組む側

| 書き方 | 1.x | 2.0 |
| --- | --- | --- |
| `列.eq(null)` / `not(null)` | `= NULL` を組み、どの行にも当たらない | `SqlBuildException`。`is_null()` / `is_not_null()` |
| `where(Data)` の空の値（`null`・空の配列） | `= NULL` などを組んでいた | 例外（`is_null` / `is_not_null` / `between` / `in` / `not_in` は別） |
| `where(Data)` に `"where"` キーの無い Data | 条件が付かずに**全件** | 例外（空の Data は何もしない）。Select / Update / Delete すべて |
| `set(Data)` / `value(Data)` に包まない行・別のテーブルの分だけの Data | 黙って何も入れない | 例外。平らな行は `setRow` / `valueRow` |
| 文字列 `"now()"` | 現在時刻になった | ただの文字列。現在時刻は `Dsl.now()` |

`apply(Data)` は句をまとめて読むので、無い句は無いままです（例外にしません）。

### Data

| 書き方 | 1.x | 2.0 |
| --- | --- | --- |
| `getInt` などで**読めない値**（`"abc"`、int に `"1.5"`、桁あふれ、真偽に `"yes"`） | 黙って `0` / `false` / `null` | `DataConversionException` |
| `getEnum` で一致しない | `null` | 例外。無くてよいなら `getEnumOptional(key, 型)` |
| `Data.fromJsonString` / `Dson.decodes` に壊れた JSON | `null` か途中まで | `JsonParseException`（空文字と `"null"` は `null`） |
| 型を変えて読む（`getStringList` で数の一覧を読む、JSON の文字を `getData` で読む） | 変換した値を**書き戻していた**（読んだだけで JSON の出力が変わった） | 書き戻さない |

> [!IMPORTANT]
> **無いキー（キーが無い・`null`・空文字）は、これまでどおり `0` / `false` / `null` です。**例外になるのは「あるのに読めない」ときだけです。
> 利用者の入力を `getInt` などで読むなら、先にバリデーションを通してください。通さずに読んで例外になると 500 です。
> `?page=abc` の `paging()` は枠組みの側で寛容に読み、1 ページ目にします。

`getDataOptional` / `getStringListOptional` などの Optional 版は、名前どおり「無ければ空を作って入れる」を残しています（`data.getDataOptional("x").put(...)` と書いた値は残ります）。あった値は書き換えません。

### Web

| 書き方 | 1.x | 2.0 |
| --- | --- | --- |
| `rules.validate(db, data);` で通らない | 一覧を返すだけ（捨てると素通り） | `ValidationException`。枠組みが **422** で `{"validation": {項目: [メッセージ]}}` を返す |
| `required()` / `empty()` で**キーごと送られない** | 素通り | 失敗（`insertRequired()` の規則は「登録のときだけ必須」のまま） |
| 返し方を2つ積む（`json(...)` と `redirect(...)` など） | 最初の1つだけ返り、残りは捨てた | 2つ目を積んだ時点で `IllegalStateException`（同じ種類を重ねるのはよい） |
| 送ったあとのヘッダ・Cookie | 届いていなかった | 例外 |
| `application/json` の本文が読めない | 空の Data | `body()` / `bodyJson()` / `bodyAll()` が 400 の `HttpException`（MCP は `PARSE_ERROR`） |
| `session().destroy()` のあとの `put` / `remove` / `clear` | 保存されなかった | `IllegalStateException` |
| `session().data().put(...)` | 「変えた」印が付かず保存されなかった | `UnsupportedOperationException`（読み取り専用の写し）。`session().put(...)` を使う |
| `paging()` のあとの `paging(50)` | 50 が効かなかった | `IllegalStateException` |
| `AbstractExecutor.cancel()` | 印を立てるだけで、あとの行も走った | そこで抜けて `onCancel` へ |

422 の本文を自分で作りたいときは、`errors(...)` で一覧を受け取ってください（1.5 の `errors` と同じです）。
一覧の検査は `errors(db, List)` / `validate(db, List)` です（本文は `{"rows": [...]}`）。

## 5. jimbleCheck の変わったところ

- J8xx と J9xx を**いつも**出します（`--target=2.0` は受け付けて何もしません）。
- J901 / J905 は、`Optional` として受け取っている書き方（`.orElse(...)`、`Optional<...> x =` など）は出しません。
- J906 から `selectCached` / `selectListCached` を外しました（名前はそのまま。戻り値の形が `select` / `selectList` と揃っただけです）。
- J907 を足しました：`session().data().put(...)` など（2.0 で例外）。
- J809（`validate` の戻り値を捨てている）を外しました。文として書く `validate(...)` が 2.0 の正しい書き方です。

## 6. 1.5 で出るようになった警告

1.5 は、2.0 で例外になる書き方を**プロセスで1度だけ**ログに出していました（呼び出し元の行つき）。
**1.5 で警告が出ていなければ、上の 4. の多くには当たっていません。**

| 1.5 の警告 | 2.0 |
| --- | --- |
| `eq(null) は「= NULL」を組み…` | `SqlBuildException` |
| `set(Data) は {"set": …} の形を読みます` | 例外 |
| `set(Data) は文字列 "now()" を…` | ただの文字列 |
| `返し方が2つ以上積まれています` | `IllegalStateException` |
| `送ったあとにヘッダ…` / `応答を送ったあとに Cookie…` | 例外 |
| `本文の JSON を読めませんでした` | 400 |
| `paging(50) の件数は効きません` | `IllegalStateException` |
| `request().getString("x") は送られてきた値を読みません` | コンパイルエラー |
| `required() / empty() はキーが無いと検査しません` | 失敗 |
| `destroy() のあとにセッションを変えても保存されません` | `IllegalStateException` |
