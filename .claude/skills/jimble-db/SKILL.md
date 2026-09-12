---
name: jimble-db
description: jimble で DB を扱うときに使う。SQL ビルダー、結果の Data がテーブル名でネストすること、エラーが戻り値で返ること、トランザクション、マイグレーションとコード生成の決まりを含む。
---

# jimble の DB

**JPA でも MyBatis でもない。**エンティティも `@Entity` も `JpaRepository` も無い。
テーブルクラスは**コード生成が作る**。

```java
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.data.Data;
```

## 引く

```java
DB db = BlogExample.db();          // 生成されたスキーマクラスが入口

List<Data> posts = db.selectList(
	SQL.select()
		.from(Post.instance())
		.where(Post.published.eq(true))
		.orderBy(Post.created_at.desc())
		.limit(20));

Data post = db.select(SQL.select().from(Post.instance()).where(Post.id.eq(id)));
```

**組み立てと実行が分かれている。**ビルダーは自分では走らない——
`db.select(...)` / `db.selectList(...)` に渡して初めて走る。

条件は重ねると AND。

```java
.where(Post.title.like("%jimble%"))
.where(Post.created_at.ge(from).and(Post.created_at.lt(to)))
.where(Post.id.in(List.of(1L, 2L, 3L)))
```

結合は `left(...)` / `inner(...)` と `on(...)`。`on()` は**直前の結合に付く**。

## 結果は Data。テーブル名でネストしている

```java
Data row = db.select(SQL.select()
	.from(Post.instance())
	.left(Comment.instance()).on(Comment.post_id.eq(Post.id))
	.where(Post.id.eq(id)));

row.getString(Post.title);                    // 列オブジェクトで引く（これが素直）
row.getString("title");                       // 空が返る。ネストの外を見ている
row.getData(Post.instance());                 // 1テーブルぶんを平らにして取り出す
row.getData("comment");                       // 結合した側
```

**`post` と `comment` の両方に `id` があっても衝突しない**のがネストの理由。

> **`extractTableData` で平らにはならない。**
> あれは<b>ネストしたまま</b> `{post: {...}}` を返すもので、JSON にして返すと
> <b>入れ子が1段残る</b>。平らにしたいなら `getData(テーブル)` か `flattenTable(テーブル)`
> （これは実際に踏んだ）。

`selectList` は**エラーなら `null`、行が無ければ空のリスト**。ここは分かれている。

## エラーは戻り値。例外ではない

```java
Data row = db.select(...);

if (row == null) { ... }               // エラーも「無い」も null

db.insert(...);

if (db.isError()) {                    // 書き込みは isError() で見る
	Log.error(db.getError());
}
```

`select` 系は `null`、`insert` / `update` / `delete` は `-1`。
理由は `db.getError()`。

**`try` で囲まない。**囲むと、囲み忘れたときに上まで飛ぶ。

## 入れる・直す・消す

```java
long id = db.insert(SQL.insert(Post.instance())
	.value(Post.title, title)
	.value(Post.created_at, Dsl.now()));       // DB 側の時計

int updated = db.update(SQL.update(Post.instance())
	.set(Post.published, true)
	.where(Post.id.eq(id)));

int deleted = db.delete(SQL.delete(Post.instance()).where(Post.id.eq(id)));
```

`SQL.insert(テーブル).value(列, 値)` の形。`insert().into(...)` ではない。
ID が要らないなら `insertNoReturnKey` のほうが速い。

**時刻はどちらの時計か決める。**`new Date()` はアプリ側、`Dsl.now()` は DB 側。
**複数台で動かすなら DB 側**——台ごとに時計がずれると、
<b>あとから入れた行のほうが古い</b>ことが起きて、作成日時の並びが入れ替わる。

## トランザクション

```java
try (DBTransaction transaction = new DBTransaction(db)) {

	transaction.beginTransaction();

	db.update(...);

	if (db.isError()) {
		transaction.rollbackEndTransaction();
		throw new HttpException(500, "更新できませんでした");
	}

	transaction.commitEndTransaction();

}
```

短く書くならこう。例外が出れば `close()` がロールバックする。

```java
DBTransaction.transaction(db, transaction -> {
	db.insert(...);
	db.update(...);
});
```

**中で1度でもエラーが出ていたら、コミットしない。**
ロールバックして `CodeException`（`DB_004`）を投げる——
エラーが戻り値で返る作りなので、<b>そのままだと部分的にコミットされていた</b>。

**失敗したら、`rollback()` するまでその先は1文も通らない**（PostgreSQL がそう決めている）。
エラーを見て分岐して続けたいときは、いったん `rollback()` してから書き直す。

```java
db.beginTransaction();
insert(...);  db.commit();          // ここまで確定。トランザクションは続く
update(...);                        // 失敗
if (db.isError()) {
	db.rollback();                  // 決着を付ける（持ち越しも畳まれる）
	insertFallback(...);
}
db.commitEndTransaction();
```

| | |
| --- | --- |
| `commit()` | 確定する。**トランザクションは続く** |
| `commitEndTransaction()` | 確定して**終わる**。ふつうはこちら |
| `rollbackEndTransaction()` | 戻して終わる |

**`commit()` で終わったつもりにしない。**続いているので、そこから先も同じ中にいる。

**外へ出すものと DB に積むものは、コミットの逆側。**

| | どこで |
| --- | --- |
| DB のキューへ積む（MQ の `put()`） | **トランザクションの中**（ロールバックすれば消える） |
| 外へ出す（メール・外部 API・SSE） | **コミットしたあと**（戻せないので） |

いちばん確実なのは、**中で `put()` して、送信は MQ の `execute()` でやる**こと
（`jimble-batch` の skill）。

## 落とし穴（実際に踏んだもの）

- **`in()` に空のリストを渡すと組み立てた時点で例外。**「空なら条件を外す」はしない——
  外すと**全件**になる。呼び出し側で `if (ids.isEmpty()) return List.of();` と分ける
- **`executeBatch` / `insertBatch` に積むビルダーは、SQL が全部同じでなければならない。**
  `value()` を積む順がループの中で変わると SQL も変わる。
  揃っていなければ `DB_998` を立てて `null`（直すまでは**値が横にずれて入っていた**）
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
