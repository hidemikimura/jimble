---
name: jimble-db-write
description: jimble の SQL ビルダーで入れる・直す・消すときに使う。insert（void）と insertKey・update・delete・execute の戻り値、valueRow / setRow と許す列（リクエストを渡すとき）、where / set / value に Data を渡す形、executeBatch / insertBatch、どちらの時計（Dsl.now()）、DuplicateKeyException をどこで catch するかの表を含む。
---

# jimble の DB で書く

全体の決まりと、ほかの skill への案内は `jimble-db`。トランザクションは `jimble-db-tx`。

## 入れる・直す・消す

```java
db.insert(SQL.insert(Post.instance())            // void。採番値は要らない
	.value(Post.title, title)
	.value(Post.created_at, Dsl.now()));         // DB 側の時計

long id = db.insertKey(SQL.insert(Post.instance())   // 採番値が要るとき（採番されなければ例外）
	.value(Post.title, title));

int updated = db.update(SQL.update(Post.instance())
	.set(Post.published, true)
	.where(Post.id.eq(id)));

int deleted = db.delete(SQL.delete(Post.instance()).where(Post.id.eq(id)));

int copied = db.execute("INSERT INTO post_archive SELECT * FROM post WHERE created_at < ?", from);   // 件数
```

`SQL.insert(テーブル).value(列, 値)` の形。`insert().into(...)` ではない。
**`insert` は `void`、採番値は `insertKey`、件数が要る `INSERT ... SELECT` は `execute`（`int`）。**
非推奨の `insertNoReturnKey` は使わない（1件の INSERT なので件数は 1）。

**平らな行（`{"title": ..., "body": ...}`）を丸ごと入れるなら `valueRow(row)` / `setRow(row)`。**
**`where(Data)` / `set(Data)` / `value(Data)` は `{"where": ...}` / `{"set": {...}}` / `{"value": {...}}` に包んだ形しか読まず、包まない行は例外。**

### リクエストを渡すなら、許す列を並べる（2.2.3）

並べないと `role` や `is_admin` を足されても入る。

```java
db.update(SQL.update(User.instance())
	.setRow(form, User.nickname, User.bio)        // ほかの列が来たら SqlBuildException
	.where(User.id.eq(me)));

db.selectList(SQL.select(User.id, User.name).from(User.instance())
	.apply(context.request().bodyAll(), User.name, User.created_at));   // where / order に使ってよい列
```

`apply(Data)`（列を並べない形）にリクエストを渡すと、`?where[users][password_hash|starts_with]=$2a$...` で
**画面に出していない列を1文字ずつ当てられる**。`apply` / `setRow` / `valueRow` のどれも同じ。

### まとめて書く

- **`executeBatch` / `insertBatch` に積むビルダーは、SQL が全部同じでなければならない。**
  `value()` を積む順がループの中で変わると SQL も変わる。揃っていなければ `DB_998` の例外
  （直すまでは**値が横にずれて入っていた**）。空の入力は空のリスト
- **戻り値が違う。**`executeBatch` は件数（`List<Integer>`）、`insertBatch` は採番値（`List<Long>`）

### 時刻と計算

**時刻はどちらの時計か決める。**`new Date()` はアプリ側、`Dsl.now()` は DB 側
（**文字列の `"now()"` はただの文字列として入る**）。
**複数台で動かすなら DB 側**——台ごとに時計がずれると、
<b>あとから入れた行のほうが古い</b>ことが起きて、作成日時の並びが入れ替わる。

計算は `plus` / `minus` / `multiply` / `divide`（`subtract` は無い）。

## catch するところ（非検査なので、コンパイラは教えない。ここで決める）

**書く前に、この表で決める。表に無いものは catch しない。**

| こう書くとき | 受ける例外 | どうする |
| --- | --- | --- |
| **利用者の入力を、一意制約（UNIQUE・主キー）のある列に `insert` / `insertKey` / `update` する**（メールアドレス・ログイン ID・コード・スラッグなど） | `DuplicateKeyException` | 409 などの「もう使われています」を返す |
| 利用者の操作で `RedisLock.lock(...)` を取る | `RedisLockException` | 409 などの「処理中です」を返す（`tryLock(...)` の `isEmpty()` で分けてもよい） |
| それ以外の DB の失敗（`SqlExecuteException` / `TransactionException`） | 受けない | 500 と巻き戻しに任せる |
| `ValidationException` / `HttpException` | 受けない | 枠組みが 422 / 指定の状態で返す |

- **一意制約の列かどうかは、マイグレーションの DDL（`UNIQUE` / `PRIMARY KEY` / `CREATE UNIQUE INDEX`）で確かめる。**推測しない
- **先に `select` で「まだ無い」を確かめても、catch は省けない。**同時に2人が来れば、両方が確かめを通って片方が一意制約に当たる
- **受けるのはトランザクションの外**（`db.transaction(...)` を囲む）。中で受けて続けると `commit()` が `DB_004` で断る（`jimble-db-tx`）
- テストでは、同じ値を2回入れて 409 になることを1本書く（catch を忘れると 500 になるので、ここで見つかる）

```java
try {
	long id = db.transactionResult(tx -> {          // トランザクションを使うなら、try はその外
		long memberId = db.insertKey(SQL.insert(Member.instance())
			.value(Member.email, email)
			.value(Member.created_at, Dsl.now()));
		db.insert(SQL.insert(MemberProfile.instance()).value(MemberProfile.member_id, memberId));
		return memberId;
	});
	...
} catch (DuplicateKeyException e) {
	throw new HttpException(409, "そのメールアドレスは使われています");
}
```

詳しくは <https://jimble.io/ja/sql.md> / <https://jimble.io/ja/db.md>（エラー）。
