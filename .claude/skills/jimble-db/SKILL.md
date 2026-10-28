---
name: jimble-db
description: jimble で DB を扱うときに最初に使う。全体の決まり（JPA ではない・組み立てと実行が分かれている・select は Optional・失敗は非検査の例外で 0 件は失敗ではない・非推奨の API）と、引く（jimble-db-select）・結果の Data（jimble-db-data）・書く（jimble-db-write）・トランザクションとロック（jimble-db-tx）・マイグレーションとコード生成（jimble-db-migration）・設定と Redis（jimble-db-config）のどの skill を読むかの案内。
---

# jimble の DB

**JPA でも MyBatis でもない。**エンティティも `@Entity` も `JpaRepository` も無い。
テーブルクラスは**コード生成が作る**。

```java
import io.jimble.db.DB;
import io.jimble.db.Tx;
import io.jimble.db.DBUtil;
import io.jimble.db.SqlExecuteException;      // SQL の失敗（非検査）
import io.jimble.db.DuplicateKeyException;    // 一意制約（SqlExecuteException の子）
import io.jimble.db.TransactionException;     // 確定できなかった（DB_004 / DB_005 / DB_006）
import io.jimble.db.sql.SqlBuildException;    // 組み立ての誤り（DB に投げる前に落ちる）
import io.jimble.db.dialect.DialectException; // その製品に無いもの（PostgreSQL でインデックスのヒントなど）
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.data.Data;
```

## 全体の決まり

**組み立てと実行が分かれている。**ビルダーは自分では走らない——
`db.select(...)` / `db.selectList(...)` などに渡して初めて走る。入口は生成されたスキーマクラス（`BlogExample.db()`）。

**失敗は例外。0件は失敗ではない。**

```java
Optional<Data> row = db.select(...);   // 0件は Optional.empty()
List<Data> rows = db.selectList(...);  // 0件は空のリスト
int updated = db.update(...);          // 0件は 0
```

- **SQL の失敗は `SqlExecuteException`（非検査）。**一意制約の違反だけ子の `DuplicateKeyException`。
  `isError()` / `getError()` は無い。コードは `e.getCode()`、元の JDBC の例外は `getCause().getCause()`
- **書かなければ上まで飛んで 500、トランザクションの中なら巻き戻る。**
  `catch (Exception e)` で握りつぶさない。組み立ての誤り（`eq(null)` など）は `SqlBuildException` で、DB に投げる前に落ちる
- **`DuplicateKeyException` の import を間違えない。**`org.springframework.dao` ではなく `io.jimble.db`。
  **どこで catch するかは `jimble-db-write` の表で決める。表に無いものは catch しない**
- **`select` は `Optional<Data>`。`select(...).get()` を書かない**（0件で `NoSuchElementException` → 500）
- **列は静的フィールドで、名前は DB のまま**（`Request.decided_by`。camelCase ではない）
- **ビルダーの結果はテーブル名でネストする。文字列の SQL の結果は平ら**（`jimble-db-data`）
- **1.x のコードを直すなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す。
  `isError()` / `DBTransaction` / `long id = db.insert(...)` / `Data row = db.select(...)` はコンパイルエラーになる
- **非推奨の `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey` / `DB.isBatchSuccess` を書かない。**
  `select` / `selectList` が同じ意味になり、失敗はもともと例外

## どの skill を読むか

**書く前に、該当する skill を読む。**落とし穴（実際に踏んだもの）は、それぞれの skill に分けて書いてある。

| やること | skill |
| --- | --- |
| 引く（`select` / `selectList`・条件・OR と括弧・`in`・結合・インデックスのヒント・ページング・カーソル） | `jimble-db-select` |
| 結果の `Data` を読む（テーブル名でネスト・文字列の SQL は平ら・JSON の列・getter の例外・`Data.put`） | `jimble-db-data` |
| 入れる・直す・消す（`insert` / `insertKey` / `update` / `delete` / `execute`・`setRow` と許す列・バッチ・時刻）と、どこで catch するか | `jimble-db-write` |
| トランザクション（`Tx`・`DB_004` / `DB_005`・入れ子）・コミットと外へ出すもの・ロック（`DBLock` / `RedisLock`） | `jimble-db-tx` |
| マイグレーション（起動時・CLI・`migrateCheck`）・コード生成（クラス名・外す表・生成物） | `jimble-db-migration` |
| `db.<名前>` の設定（プール・Agroal・`required`・`framework_tables`）・SQL 結果キャッシュ・Redis・製品の違い | `jimble-db-config` |

## 詳しいことは引く

**推測で書かない。**

| | |
| --- | --- |
| SQL ビルダー全般・関数・ウィンドウ関数 | <https://jimble.io/ja/sql.md> |
| 設定・複数データソース・エラー・製品選び | <https://jimble.io/ja/db.md> |
| トランザクション | <https://jimble.io/ja/transaction.md> |
| マイグレーションとコード生成・型の対応 | <https://jimble.io/ja/codegen.md> |
| キャッシュ | <https://jimble.io/ja/cache.md> |
| テスト（`@Tag("db")` と実 DB） | <https://jimble.io/ja/testing.md> |
