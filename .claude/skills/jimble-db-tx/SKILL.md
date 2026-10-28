---
name: jimble-db-tx
description: jimble の DB でトランザクションかロックを書くときに使う。3つの書き方（db.transaction・transactionResult・try (Tx tx = db.begin())）、commit / checkpoint / rollback、中で SQL の失敗を catch すると DB_004 で確定しないこと、入れ子と DB_005、検査例外の DB_006、コミットの中で積むもの・外へ出すもの、DBLock と RedisLock を含む。
---

# jimble のトランザクションとロック

全体の決まりと、ほかの skill への案内は `jimble-db`。

## トランザクション

**書き方は3つだけ。**`db.transaction(...)` / `db.transactionResult(...)` / `try (Tx tx = db.begin())`。
検査例外は投げない。`DBTransaction` や `db.beginTransaction()` は無い。`@Transactional` も無い。

```java
db.transaction(tx -> {
	db.update(...);
	db.insert(...);
});                                    // 例外なく終われば確定。例外なら巻き戻して投げ直す

long id = db.transactionResult(tx -> db.insertKey(...));   // 値を返す版（名前が違う）

try (Tx tx = db.begin()) {
	db.update(...);
	if (...) throw new HttpException(409, "...");   // 例外で抜けたら巻き戻る。rollback は書かない
	tx.commit();                       // 確定して終わる。続けたいなら tx.checkpoint()
}                                      // commit せずに抜けたら巻き戻す
```

| | |
| --- | --- |
| `tx.commit()` | 確定して**終わる**（1度だけ） |
| `tx.checkpoint()` | ここまで確定して、**続ける** |
| `tx.rollback()` | 巻き戻して終わる |
| `close()` | 終わっていなければ巻き戻す（try-with-resources が呼ぶ） |

`db.begin()` の戻り値は捨てない（`@CheckReturnValue`）。

- **中で SQL の失敗を `catch` して続けても、確定しない。**`commit()` が全部巻き戻して
  `TransactionException`（`getCode()` が `DB_004`）を投げる。失敗のあとに成功する文があっても同じ。
  **受けるなら Tx の外で**
- **別の道で書き直したいなら、その Tx は例外で抜けさせて、外で受けて新しい Tx で書く。**

  ```java
  try {
  	db.transaction(tx -> insert(...));
  } catch (DuplicateKeyException e) {
  	db.transaction(tx -> updateInstead(...));    // 新しい Tx
  }
  ```

  「あれば更新」なら `onDuplicateKeyUpdate(列, 値)` で1文にするほうが素直
- 中身の検査例外は `TransactionException`（`DB_006`）に包まれる。非検査例外はそのまま
- `TransactionException` は `SqlExecuteException` の子。Web では捕まえなければ 500

**入れ子は外に合流する。**中の `commit` / `checkpoint` は何もしない（確定させるのは外）。
**中で `rollback` したり、例外で抜けたりすると、外の `commit()` が `DB_005` で断り、全部巻き戻る。**
中の例外を外で受け止めて続けても、外は確定できない。

**トランザクションとカーソル（`selectListWithFetcher`）の間は、コネクションを握ったまま**になる。

## 外へ出すものと DB に積むものは、コミットの逆側

| | どこで |
| --- | --- |
| DB のキューへ積む（MQ の `put()`） | **トランザクションの中**（ロールバックすれば消える） |
| 外へ出す（メール・外部 API・SSE） | **コミットしたあと**（戻せないので） |

いちばん確実なのは、**中で `put()` して、送信は MQ の `execute()` でやる**こと
（`jimble-mq` の skill）。

## ロック

```java
DBLock.create(db, "daily");            // キーの行を作る（あれば何もしない）。先に1回。void

db.transaction(tx -> {
	DBLock.lock(db, "daily");          // SELECT ... FOR UPDATE。トランザクションが終わるまで1つだけ
	...
});

try (RedisLockResult lock = RedisLock.lock("order:" + id)) {   // 取れなければ RedisLockException
	...
}

Optional<RedisLockResult> got = RedisLock.tryLock("report", 1000, 60000);   // 待っても取れなければ空
```

- **`DBLock.lock` はトランザクションの中で呼ぶ。**外で呼ぶと `IllegalStateException`（`FOR UPDATE` の鍵は文の終わりで外れ、何も守らない）。
  `create` していないキーも例外
- `RedisLock.tryLock` は **`Optional`**。取れたときだけ `try (RedisLockResult lock = got.get())` で囲んで外す
- 利用者の操作で `RedisLock.lock(...)` を取るなら `RedisLockException` を受けて 409 などにする（`jimble-db-write` の catch の表）
- `DBLock` は `io.jimble.db.lock`、`RedisLock` / `RedisLockResult` は `io.jimble.db.redis.lock`
- `framework_tables` で `redis_lock` を外したデータソースで `RedisLock.lock(db, ...)` を呼ぶと `IllegalStateException`（`jimble-db-config`）

詳しくは <https://jimble.io/ja/transaction.md>。
