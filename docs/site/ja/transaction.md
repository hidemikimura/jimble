---
title: トランザクション
summary: try-with-resources で囲む。畳み忘れは実行の終わりに拾う
section: データベース
order: 3
---

# トランザクション

注釈はありません。**囲んだところがトランザクションです。**

```java snippet=transaction-mq
```

`db.begin()` が返す `Tx` を try-with-resources で囲み、最後に `tx.commit()` を呼びます。
**`commit()` を呼ばずに抜けたら巻き戻ります**（`return` でも例外でも）。
上の例では、記事が入らなかったときに `return -1` で抜けるので、何も残りません。

## 書き方は3つ

```java
db.transaction(tx -> {
	db.insert(...);
	db.update(...);
});                                   // 例外なく終われば確定、例外が出れば巻き戻して投げ直す

long id = db.transactionResult(tx -> db.insertKey(...));   // 値を返す版

try (Tx tx = db.begin()) {            // 自分で確定したいとき
	db.update(...);
	tx.commit();                      // 確定して終わる。呼ばずに抜けたら巻き戻す
}
```

**ふつうは `db.transaction(...)` がいちばん短く書けます。**
途中で抜けたいとき（上の `return -1` のような）や、確定を自分で決めたいときは `db.begin()` です。

`transaction` と `transactionResult` で名前が分かれているのは、
`tx -> db.update(...)` のような式のラムダが「値を返す」とも「返さない」とも読めて、
同じ名前だと呼び分けられないためです。

## `Tx` のメソッド

| メソッド | 何をするか |
| --- | --- |
| `commit()` | 確定して**終わる** |
| `checkpoint()` | ここまでを確定して、**続ける** |
| `rollback()` | 巻き戻して終わる |
| `close()` | 終わっていなければ巻き戻す（try-with-resources が呼びます） |

失敗は `TransactionException`（非検査。`SqlExecuteException` の子）です。`getCode()` で `DB_004` などが取れます。

**`commit()` と `rollback()` は1度だけです。**終わったあとにもう一度呼ぶと `IllegalStateException` になります。
確定して続けたいなら `checkpoint()` を使います。`checkpoint()` のあとに書いた分は、`commit()` しなければ巻き戻ります。

`db.transaction(...)` の中で `tx.commit()` / `tx.rollback()` を自分で呼んでもかまいません。そのときは、抜けたあとに何もしません。

> [!NOTE]
> 1.x の `DBTransaction` と、DB の `beginTransaction()` / `commit()` / `commitEndTransaction()` などは 2.0 で消しました。
> `DBTransaction.commit()` は終わらせない確定だったので、そのあとに書いた分が `close()` で黙って巻き戻っていました。
> 2.0 では、終わらせる確定が `commit()`、続ける確定が `checkpoint()` です（[2.0 への移行](./migrate-2)）。

## 中で SQL が失敗したら

**SQL の失敗は例外です**（[DB を使う](./db)）。受け止めなければ Tx から抜けて、全部巻き戻ります。
途中まで入ることはありません。

**受け止めて続けても、その Tx は確定できません。**

```java
db.transaction(tx -> {
	db.insert(...);                  // 通った
	try {
		db.update(...);              // 一意制約に当たった
	} catch (DuplicateKeyException e) {
		// 受け止めて続ける
	}
	db.insert(...);                  // 通った
});                                  // ← ここで TransactionException（DB_004）。全部巻き戻る
```

トランザクションを始めてから1度でも SQL が失敗していたら、`commit()` は全部を巻き戻して
`TransactionException`（`DB_004`）を投げます。**失敗のあとに成功する文があっても素通りしません。**
`checkpoint()` も同じように断ります。

### 失敗を見て、別の道で書き直したいとき

**その Tx は巻き戻して、新しい Tx で書き直します。**

```java
try {
	db.transaction(tx -> {
		insert(...);
		update(...);                 // 一意制約に当たるかもしれない
	});                              // 例外で抜けたので、ここで巻き戻っている
} catch (DuplicateKeyException e) {
	db.transaction(tx -> insertFallback(...));   // 新しい Tx で、別の道
}
```

外にもトランザクションがあるときは、中の Tx は外に合流しています（下の「入れ子」）。
その場合は、外ごと書き直してください。

> [!TRAP]
> **失敗のあとの文が通るかどうかは、製品によって違います。**
>
> | | 失敗のあとの文 |
> | --- | --- |
> | PostgreSQL | **通りません。**`ROLLBACK` するまで全部断ります（`current transaction is aborted, ...`） |
> | MySQL | **通ります。**1文の失敗ではトランザクションを中断しません |
>
> **どちらにも寄りかからないでください。**jimble が約束するのは
> 「**コミットは拒まれ、1行も残らない**」ところまでです。
> 失敗を受け止めたら、続けて書かずに、その Tx を終わらせてください。

## 中身が検査例外を投げたら

`db.transaction(...)` / `transactionResult(...)` の中身は、検査例外を投げてもかまいません（`throws` は要りません）。
**非検査例外はそのまま、検査例外は `TransactionException`（`DB_006`）に包んで**、巻き戻してから投げ直します。
元の例外は `getCause().getCause()` にあります。

## 入れ子（合流）

すでにトランザクションが始まっている DB で `db.begin()`（や `db.transaction(...)`）を呼ぶと、
**新しくは始めず、外に合流します。**合流しているかは `tx.isJoined()` で分かります。

| 中でしたこと | どうなるか |
| --- | --- |
| `commit()` / `checkpoint()` | 何もしない。確定させるのは外 |
| `rollback()` | **外を巻き戻し専用にする** |
| commit せずに閉じた（例外で抜けた） | **外を巻き戻し専用にする** |

巻き戻し専用になった外の `commit()` は、全部を巻き戻して `TransactionException`（`DB_005`）を投げます。
**中の例外を外で受け止めて続けても、外は確定できません。**
外を途中まで確定させることもできません（中の `checkpoint()` は何もしません）。

## DB の行でロックする（`DBLock`）

Redis が無い場所では、`DBLock` で「同じキーの処理を1つずつ」にできます。

```java
DBLock.create(db, "daily");            // キーの行を作る（あれば何もしない）。先に1回

db.transaction(tx -> {
	DBLock.lock(db, "daily");          // SELECT ... FOR UPDATE。トランザクションが終わるまで1つだけ
	...
});
```

**`DBLock.lock` はトランザクションの中で呼びます。**外で呼ぶと `IllegalStateException` です
（`FOR UPDATE` の鍵は文の終わりで外れるので、外で取っても何も守りません）。

- キーが無い（`create` していない）ときも `IllegalStateException` です
- `create` / `lock` は戻り値がありません（`void`）。SQL の失敗は `SqlExecuteException` です
- 解放は**トランザクションの終わり**です。明示的に外す口はありません

> [!NOTE]
> 1.x の `DBLock.lock` は `boolean` を返し、トランザクションの外で呼んでも `true` を返していました。
> 何も守らないまま先へ進んでいたので、2.0 で例外にしました。

Redis を使うロックは [キャッシュとロック](./cache) にあります。

## 外へ出すものと、DB に積むもの

**どちらの中で呼ぶかが逆になります。**ここは間違えやすいところです。

| | どこで呼ぶか | なぜ |
| --- | --- | --- |
| **DB のキューへ積む**（[MQ](./mq) の `put()`） | **トランザクションの中** | 同じ DB なので、ロールバックすれば積んだものも消えます |
| **外へ出す**（SSE・メール送信・外部 API） | **コミットしたあと** | 戻せないので、先に流すと取り返しがつきません |

```java
db.transaction(tx -> {

	long id = db.insertKey(...);

	new NoticeExecutor().put(db, data);      // ← 中で積む（DB のキュー）

});

PostFeedHandler.notifyNewPost(title);        // ← 外へ出すのはコミットしてから
```

外へ出すものを先に流すと、ロールバックしたときに
**「入っていない記事のお知らせ」だけが届きます。**

逆に、DB のキューをコミット後に積むと、**積む直前に落ちたぶんが黙って消えます**——
記事は入っているのに、お知らせは誰も知らない状態になります。

> [!TIP]
> **外へ出す処理は、キューの中でやるのがいちばん確実です。**
> トランザクションの中で `put()` して、実際の送信は
> [MQ](./mq) の `execute()` に書きます。こうすると、
> <b>「どちらの中で呼ぶか」を考えなくてよくなります。</b>

## 畳み忘れ

try-with-resources を使わずに `Tx tx = db.begin()` として、`commit()` も `rollback()` もせずに途中で `return` すると、
ロールバックもされず接続もプールへ戻りません。

jimble は**実行（`Context`）の終わりに拾います**。

```
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックして閉じます: blog_example
```

黙って戻すと「入れたつもりが入っていない」が残るので、ERROR を出してからロールバックします。
このログが出たら、囲み忘れです。
（見ているのは `DB` の側です。コネクションを握っているのは `DB` なので。）

## 閉じ忘れた `DB`

**ふつうの SQL は、`DB` を閉じ忘れても漏れません。**
1文ごとにコネクションをプールへ返しているので、`DBUtil.getMainDB()` を
使い捨てにする書き方でかまいません。

**握ったまま抜ける道は2つだけ**で、どちらも実行の終わりに拾います。

| 握るもの | いつ返るか |
| --- | --- |
| トランザクション中 | `tx.commit()` / `tx.rollback()` / `tx.close()` |
| カーソル（`selectListWithFetcher`） | `db.close()` |

```
ERROR 閉じられていない DB が残っていました。閉じます: blog_example（selectListWithFetcher はカーソルなので、close() までコネクションを返しません）
```

> [!TRAP]
> **拾うのは最後の砦です。**拾われた時点で ERROR が出ているので、
> <b>ログが出たら直す</b>ものだと思ってください。
> 実行が長いバッチでは、実行の終わりまで1本が握られたままになります。
