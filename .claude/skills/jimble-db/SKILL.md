---
name: jimble-db
description: jimble で DB を扱うときに使う。SQL ビルダー、結果の Data がテーブル名でネストすること（文字列の SQL は平ら）、select が Optional を返し失敗は例外になること、トランザクション（Tx）、マイグレーションとコード生成の決まりを含む。
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
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.data.Data;
```

## 引く

```java
DB db = BlogExample.db();          // 生成されたスキーマクラスが入口

List<Data> posts = db.selectList(  // 0件は空のリスト。null は返らない
	SQL.select()
		.from(Post.instance())
		.where(Post.published.eq(true))
		.orderBy(Post.created_at.desc())
		.limit(20));

Data post = db.select(SQL.select().from(Post.instance()).where(Post.id.eq(id)))
	.orElseThrow(() -> new HttpException(404, "記事がありません: " + id));   // select は Optional<Data>
```

**組み立てと実行が分かれている。**ビルダーは自分では走らない——
`db.select(...)` / `db.selectList(...)` に渡して初めて走る。

**`select` は `Optional<Data>`**（`selectCached` も同じ）。0件は空、読めなければ例外。
「無ければ 404」は `.orElseThrow(() -> new HttpException(404, "..."))`、
「無ければ null で続ける」は `.orElse(null)`、有無だけなら `.isPresent()` / `.isEmpty()`。

条件は重ねると AND。

```java
.where(Post.title.like("%jimble%"))
.where(Post.created_at.ge(from).and(Post.created_at.lt(to)))
.where(Post.id.in(List.of(1L, 2L, 3L)))
.where(Post.deleted_at.is_null())          // eq(null) は SqlBuildException。is_null() / is_not_null()
```

OR や括弧は `Dsl.anyOf(...)` / `Dsl.allOf(...)` でまとめる。

```java
.where(Post.shop_id.eq(shopId), Dsl.anyOf(Post.status.eq("draft"), Post.status.eq("review")))
```

**`a.or(b).and(c)` とつなぐと `(a OR b) AND c`**（左から読んだとおり。2.2.3）。
2.2.2 までは括弧なしの `a OR b AND c`（＝ `a OR (b AND c)`）で、テナントの条件を足したつもりが効いていなかった。
`a OR (b AND c)` にしたいなら `a.or(b.and(c))`。迷ったら `Dsl.anyOf` / `Dsl.allOf` で書く。

**1つの値を書く場所（`eq` / `gt` / `like` / `set` / `value` など）にリストや配列を渡すと `SqlBuildException`**（2.2.2）。
かつては値だけが増えて後ろのプレースホルダーとずれ、MySQL では**別の行を書き換えていた**。
リクエストの JSON の配列や `a[]=` はそのままリストになるので、検査で弾く。一覧で比べるなら `in(...)`（中の入れ子も断る）。
`byte[]` は1つの値として渡る。

**1つの条件に比較は1つ。**`Post.id.ge(1).le(9)` は組み立てで落ちる。
範囲は `between(a, b)`、別の条件は `.and(Post.id.le(9))`。列に直接 `.and(...)` も落ちる。

結合は `left(...)` / `inner(...)` と `on(...)`。`on()` は**直前の結合に付く**。
結合の無いところの `on()` は落ちる。2度呼ぶと AND。

## 結果は Data。テーブル名でネストしている

```java
Data row = db.select(SQL.select()
	.from(Post.instance())
	.left(Comment.instance()).on(Comment.post_id.eq(Post.id))
	.where(Post.id.eq(id)))
	.orElseThrow(() -> new HttpException(404, "記事がありません"));

row.getString(Post.title);                    // 列オブジェクトで引く（これが素直）
row.getString("title");                       // null。ネストの外を見ている
row.getData(Post.instance());                 // 1テーブルぶんを平らにして取り出す
row.getData("comment");                       // 結合した側
```

**`post` と `comment` の両方に `id` があっても衝突しない**のがネストの理由。

> **`extractTableData` で平らにはならない。**
> あれは<b>ネストしたまま</b> `{post: {...}}` を返すもので、JSON にして返すと
> <b>入れ子が1段残る</b>。平らにしたいなら `getData(テーブル)` か `flattenTable(テーブル)`
> （これは実際に踏んだ）。

**取り出しは「あるのに読めない値」で `DataConversionException`**（`"abc"` を `getInt`、int に `"1.5"`、桁あふれ、
真偽に `"yes"`、`getEnum` で一致しない）。**無いキー（キーが無い・null・空文字）は 0 / false / null のまま。**
DB から読んだ値ならまず起きないが、**利用者の入力を同じ getter で読むと 500 になる**——先に検査する（`jimble-web` の skill）。
無くてよい enum は `getEnumOptional(key, 型)`。

### 文字列の SQL の結果はネストしない

ネストするのは**ビルダーが列に `post__title` の別名を付けるから**。
`db.select("SELECT ...", ...)` / `selectList(String, ...)` に自分で書いた SQL を渡すと、**平らな Data が返る。**

```java
Data row = db.select("SELECT * FROM post WHERE id = ?", id).orElseThrow(() -> new HttpException(404, "ありません"));
// {"id":1,"title":"..."}                  ← post の下に入っていない

row.getString("title");                   // 取れる
row.getString(Post.title);                // 取れる（テーブルのキーが無ければ平らなほうを見る）
row.getData(Post.instance());             // null。ビルダーの結果と同じつもりで .getString() を続けると NPE
```

- **結合すると同じ名前の列が黙って上書きされる。**`SELECT * FROM post p JOIN comment c ...` は
  `id` が1つだけ残る（**あとの列の値**。PostgreSQL で確かめた）。例外もログも出ない。
  列を並べて別名を付ける（`c.id AS comment_id`）
- **ビルダーと同じ形にしたいなら、別名を `テーブル__列` にする**（`p.id AS "post__id"`）。区切りは `__`
- **ビルダーの結果を扱うコードに、文字列の SQL の結果を渡さない。**`getData(テーブル)` が `null`、
  JSON にすると入れ子が1段少ない、`putData(列, 値)` は**ネストを作る**ので平らな行の中に `post: {...}` が生える
  （平らな行には `putData("title", 値)` か `putDataTakeCare(列, 値)`）

### JSON の列は、読んだ時点で Data / List になっている

MySQL の `JSON`、PostgreSQL の `json` / `jsonb` の列は、**枠組みが読んだところで解いてしまう**
（オブジェクトは `Data`、配列は `List`）。ビルダーでも文字列の SQL でも同じ。

```java
Data row = db.select("SELECT * FROM setting WHERE id = ?", id).orElseThrow(() -> new HttpException(404, "ありません"));
// tags 列の中身が ["a","b"] のとき

row.getString("tags");                     // "a"  ← 配列の先頭の要素だけ。JSON の文字ではない
row.getStringList("tags");                 // ["a", "b"]  ← 配列はこちらで取る
row.getData("options");                    // オブジェクトの列は Data で取る
```

- 配列の列を `getString` すると、**1度だけ WARN**（「JSON の配列の列 〜 を getString しています」）が出る。見たらここを直す
- **`getString` で JSON の文字を取ろうとしない。**オブジェクトの列は JSON の文字になるが
  （キーの間の空白などは元のとおりではない）、**配列の列は先頭の要素しか返らない**。
  それを `Dson.decodes(..., List.class)` に渡すと、JSON ではないので **`JsonParseException`**
- **元の文字のまま欲しいなら、SQL で文字に変えて読む**：MySQL は `CAST(列 AS CHAR)`、
  PostgreSQL は `列::text`（または `CAST(列 AS text)`）
- `Dson.decodes(文字列)` / `Dson.decodes(文字列, Data.class)` に**一番外が配列の JSON** を渡すと、
  `{"0": ..., "1": ...}` の Data になる（添字がキー）。配列は **`Dson.decodes(文字列, List.class)`** で読む。
  壊れた JSON は `JsonParseException`（空文字と `"null"` は `null`）

## 失敗は例外。0件は失敗ではない

```java
Optional<Data> row = db.select(...);   // 0件は Optional.empty()
List<Data> rows = db.selectList(...);  // 0件は空のリスト
int updated = db.update(...);          // 0件は 0

try {
	db.insert(...);
} catch (DuplicateKeyException e) {    // 分けたいのは一意制約くらい。それだけ受ける
	throw new HttpException(409, "もう登録されています");
}
```

**SQL の失敗は `SqlExecuteException`（非検査）。**一意制約の違反だけ子の `DuplicateKeyException`。
`isError()` / `getError()` は無い。コードは `e.getCode()`、元の JDBC の例外は `getCause().getCause()`。

**書かなければ上まで飛んで 500、トランザクションの中なら巻き戻る。**
`catch (Exception e)` で握りつぶさない。組み立ての誤り（`eq(null)` など）は `SqlBuildException` で、DB に投げる前に落ちる。

### catch するところ（非検査なので、コンパイラは教えない。ここで決める）

**書く前に、この表で決める。表に無いものは catch しない。**

| こう書くとき | 受ける例外 | どうする |
| --- | --- | --- |
| **利用者の入力を、一意制約（UNIQUE・主キー）のある列に `insert` / `insertKey` / `update` する**（メールアドレス・ログイン ID・コード・スラッグなど） | `DuplicateKeyException` | 409 などの「もう使われています」を返す |
| 利用者の操作で `RedisLock.lock(...)` を取る | `RedisLockException` | 409 などの「処理中です」を返す（`tryLock(...)` の `isEmpty()` で分けてもよい） |
| それ以外の DB の失敗（`SqlExecuteException` / `TransactionException`） | 受けない | 500 と巻き戻しに任せる |
| `ValidationException` / `HttpException` | 受けない | 枠組みが 422 / 指定の状態で返す |

- **一意制約の列かどうかは、マイグレーションの DDL（`UNIQUE` / `PRIMARY KEY` / `CREATE UNIQUE INDEX`）で確かめる。**推測しない
- **先に `select` で「まだ無い」を確かめても、catch は省けない。**同時に2人が来れば、両方が確かめを通って片方が一意制約に当たる
- **受けるのはトランザクションの外**（`db.transaction(...)` を囲む）。中で受けて続けると `commit()` が `DB_004` で断る
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
非推奨の `insertNoReturnKey` は使わない。

**平らな行（`{"title": ..., "body": ...}`）を丸ごと入れるなら `valueRow(row)` / `setRow(row)`。**
`value(Data)` / `set(Data)` は `{"value": {...}}` / `{"set": {...}}` に包んだ形しか読まず、包まない行は例外。

**リクエストを渡すなら、入れてよい列を並べる**（2.2.3）。並べないと `role` や `is_admin` を足されても入る。

```java
db.update(SQL.update(User.instance())
	.setRow(form, User.nickname, User.bio)        // ほかの列が来たら SqlBuildException
	.where(User.id.eq(me)));

db.selectList(SQL.select(User.id, User.name).from(User.instance())
	.apply(context.request().bodyAll(), User.name, User.created_at));   // where / order に使ってよい列
```

`apply(Data)`（列を並べない形）にリクエストを渡すと、`?where[users][password_hash|starts_with]=$2a$...` で
**画面に出していない列を1文字ずつ当てられる**。

**時刻はどちらの時計か決める。**`new Date()` はアプリ側、`Dsl.now()` は DB 側
（**文字列の `"now()"` はただの文字列として入る**）。
**複数台で動かすなら DB 側**——台ごとに時計がずれると、
<b>あとから入れた行のほうが古い</b>ことが起きて、作成日時の並びが入れ替わる。

計算は `plus` / `minus` / `multiply` / `divide`（`subtract` は無い）。

## トランザクション

**書き方は3つだけ。**`db.transaction(...)` / `db.transactionResult(...)` / `try (Tx tx = db.begin())`。
検査例外は投げない。`DBTransaction` や `db.beginTransaction()` は無い。

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

- **中で SQL の失敗を `catch` して続けても、確定しない。**`commit()` が全部巻き戻して
  `TransactionException`（`getCode()` が `DB_004`）を投げる。失敗のあとに成功する文があっても同じ
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

**外へ出すものと DB に積むものは、コミットの逆側。**

| | どこで |
| --- | --- |
| DB のキューへ積む（MQ の `put()`） | **トランザクションの中**（ロールバックすれば消える） |
| 外へ出す（メール・外部 API・SSE） | **コミットしたあと**（戻せないので） |

いちばん確実なのは、**中で `put()` して、送信は MQ の `execute()` でやる**こと
（`jimble-batch` の skill）。

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
- `DBLock` は `io.jimble.db.lock`、`RedisLock` / `RedisLockResult` は `io.jimble.db.redis.lock`

## 落とし穴（実際に踏んだもの）

- **1.x のコードを直すなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す。
  `isError()` / `DBTransaction` / `long id = db.insert(...)` / `Data row = db.select(...)` はコンパイルエラーになる
- **`select(...).get()` を書かない。**0件で `NoSuchElementException`（500）。`orElseThrow(() -> new HttpException(404, ...))` か `orElse(null)`
- **Tx の中で SQL の失敗を受け止めて続けない**（上の `DB_004`）。受けるなら Tx の外で
- **非推奨の `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey` / `DB.isBatchSuccess` を書かない。**
  `select` / `selectList` が同じ意味になり、失敗はもともと例外
- **`列.eq(null)` / `not(null)` は `SqlBuildException`。**NULL の比較は `is_null()` / `is_not_null()`。
  値が null かもしれない変数を `eq(x)` に渡すところは、分けて書く
- **`where(Data)` / `set(Data)` / `value(Data)` は包んだ形（`{"where": ...}` など）しか読まない。**
  平らな行を渡すと例外。平らな行は `setRow` / `valueRow`
- **リクエストを `apply` / `setRow` / `valueRow` に渡すときは、許す列を並べる**（上の「入れる・直す・消す」）
- **`eq` などにリストを渡さない**（`SqlBuildException`）。`a.or(b).and(c)` は `(a OR b) AND c`
- **`in()` に空のリストを渡すと組み立てた時点で例外。**「空なら条件を外す」はしない——
  外すと**全件**になる。呼び出し側で `if (ids.isEmpty()) return List.of();` と分ける
- **`executeBatch` / `insertBatch` に積むビルダーは、SQL が全部同じでなければならない。**
  `value()` を積む順がループの中で変わると SQL も変わる。揃っていなければ `DB_998` の例外
  （直すまでは**値が横にずれて入っていた**）。空の入力は空のリスト
- **戻り値が違う。**`executeBatch` は件数（`List<Integer>`）、`insertBatch` は採番値（`List<Long>`）
- **`selectListWithFetcher`（カーソル）は `DB` も畳む。**
  ふつうの SQL は1文ごとにコネクションを返すので閉じ忘れても漏れないが、
  カーソルとトランザクション中は**握ったまま**
- **`tinyint` は桁数に関係なく `boolean`。**`decimal` は `double` なので金額計算に向かない
- **`db/<データソース>/` は毎回まるごと作り直される。**手で書いたファイルを置くと次の `codegen` で消える

## マイグレーションとコード生成

```
conf/migration/<スキーマ名>/001_xxx.sql      # --- !Ups / # --- !Downs
./gradlew migrate
./gradlew codegen
```

生成物は**リポジトリに入れる**（入れないと DB の無い環境でビルドできない）。

起動時に流すなら `Migration.install()` を **`DBUtil.load(...)` より前**に呼ぶ。

```java
Migration.install();                                  // 登録するだけ。ここでは流れない
DBUtil.load(Conf.conf().config(), App.class);         // 流れるのはこの中。繋がらなければ例外で止まる
```

- **`install()` の戻り値は「登録したか」。流れたかではない。**`false` は `migration.on_startup = false` で
  **わざと切ってある**ときだけ。`if (!Migration.install()) throw ...` と書くと、切ってある環境で起動しなくなる。
  **`true` でもまだ1行も流れていない**
- **`DBUtil.load(...)` のあとに呼ぶと何も起きない。**登録した処理はもう走り終わっているので、黙って流れない
- **`DBUtil.load(...)` は `void`。**繋がらなければ `SqlExecuteException`（`DB_007`）、流すのに失敗しても例外で落ちる。
  `if` で囲まず、捕まえずに起動を止める

**jimble が自分で作るテーブル**（`migration` / `session` / `db_cache` / `batch_*` / `auth_*` など）は
codegen が自動で外す。**MQ のキュー表は外れない**——名前をアプリが決めるので、jimble の管理テーブルに入っていない。
`DbScheduler` が使う **`mq_scheduler`**（`scheduler.queue_name` の既定）も同じ。外すなら並べる。

```conf
codegen {
	exclude_tables = ["mq_notice", "mq_scheduler"]   # ワイルドカード（mq_*）は使えない。完全一致で1つずつ
}
```

外したテーブルは codegen のログに名前が出る。**アプリのテーブルが jimble の管理テーブルと同じ名前**
（`session` など）だと黙って生成されないので、ログを見る。

**クラス名はテーブル名をそのまま UpperCamel にしたもの。**`orders` → `Orders`、
`audit_log` → `AuditLog`。<b>単数形にはしない。</b>
列は静的フィールドで、名前は DB のまま（`Orders.staff_id`）。

パッケージは `db.<データソース名>` の下に来る。

```java
import db.shop_example.ShopExample;              // スキーマクラス（db() の入口）
import db.shop_example.table.orders.Orders;      // テーブルクラス
```
**マイグレーションの SQL は方言を吸収しない**——PostgreSQL なら PostgreSQL の DDL を書く。

## 製品の違い

`db.xxx.product` を見て SQL が変わる。識別子の囲み、`ON DUPLICATE KEY UPDATE` と
`ON CONFLICT`、`RAND()` と `RANDOM()` はビルダーが吸収する。
**その製品に無いものは、組み立てたところで `DialectException`。**

揃っていないものもある（`greatest`/`least` の NULL の扱い、正規表現の方言、
0 除算で MySQL は NULL・PostgreSQL は落ちる、`cast` できない文字列）。
一覧は `sql.md` の「製品の違いで気をつけること」。

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

## db.<名前> の設定（要件 D-159）

**snake_case で書く**（`maximum_pool_size` / `minimum_idle` / `idle_timeout` /
`max_lifetime` / `connection_timeout` / `keepalive_time` / `fetch_size` / `schema`）。
**camelCase の古い綴りも読めるが、起動時に1度だけ警告が出る**（`scheme` は綴り違いだった）。

**表に無いキーを書くと起動時に落ちる。**以前は黙って無視されたので、
`maximum_pool_size` を打ち間違えたアプリは**プールが既定のまま**だった。

時間は**単位を値に書く**（`idle_timeout = 5m`）。素の数値は落ちる。

**プールの大きさは、書かなければ上限 10・最小 1**（2.2.1。それまでは書かないと HikariCP は最小も 10、Agroal は起動時に落ちた）。
`maximum_pool_size` が 1 未満、`minimum_idle` が負なら起動時に落ちる。

**`connection_pool_type = "agroal"` でも、DB が再起動すれば切れた接続は捨てる**（`product` に合った見分け方を渡す。2.2.1）。
`validate_on_borrow = true` は取り出すたびに確かめるので**速さが半分ほど**になる。再起動に備えるだけなら要らない。

## Redis とキャッシュの設定

- **Redis にパスワードを掛ける**：`redis.password = ${?REDIS_PASSWORD}`（Redis 6 の ACL なら `redis.username` も。2.2.3）
- Redis には**文字列として**読み書きする（2.2.3。それまでの Kryo は、読むときに何のクラスでも作った）。アプリは何もしなくてよい
- **SQL 結果のキャッシュはデータソースごとに分かれる**（2.2.3）。1つの Redis を複数のアプリや環境で分け合うなら、
  それぞれ `sql_cache.namespace` を別の値にする
