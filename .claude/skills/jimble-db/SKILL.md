---
name: jimble-db
description: jimble で DB を扱うときに使う。SQL ビルダー、結果の Data がテーブル名でネストすること（文字列の SQL は平ら）、エラーが戻り値で返ること、トランザクションと検査例外、マイグレーションとコード生成の決まりを含む。
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

### 文字列の SQL の結果はネストしない

ネストするのは**ビルダーが列に `post__title` の別名を付けるから**。
`db.select("SELECT ...", ...)` / `selectList(String, ...)` に自分で書いた SQL を渡すと、**平らな Data が返る。**

```java
Data row = db.select("SELECT * FROM post WHERE id = ?", id);
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
Data row = db.select("SELECT * FROM setting WHERE id = ?", id);
// tags 列の中身が ["a","b"] のとき

row.getString("tags");                     // "a"  ← 配列の先頭の要素だけ。JSON の文字ではない
row.getStringList("tags");                 // ["a", "b"]  ← 配列はこちらで取る
row.getData("options");                    // オブジェクトの列は Data で取る
```

- **`getString` で JSON の文字を取ろうとしない。**オブジェクトの列は JSON の文字になるが
  （キーの間の空白などは元のとおりではない）、**配列の列は先頭の要素しか返らない**。
  それを `Dson.decodes(..., List.class)` に渡すと、JSON ではないので **`null`**
- **元の文字のまま欲しいなら、SQL で文字に変えて読む**：MySQL は `CAST(列 AS CHAR)`、
  PostgreSQL は `列::text`（または `CAST(列 AS text)`）
- `Dson.decodes(文字列)` / `Dson.decodes(文字列, Data.class)` に**一番外が配列の JSON** を渡すと、
  `{"0": ..., "1": ...}` の Data になる（添字がキー）。配列は **`Dson.decodes(文字列, List.class)`** で読む
  （これは `null` にならない。`null` になるのは、渡した文字が JSON でないとき）

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

**`DBTransaction` は検査例外を投げる。**`beginTransaction()` / `commit()` / `commitEndTransaction()` /
`rollback()` / `rollbackEndTransaction()` は `CodeException`（`Exception` の子）、`close()` は `IOException`、
`DBTransaction.transaction(...)` は `Exception`。書いたメソッドに **`throws Exception`** が無いとコンパイルが通らない。

- ハンドラ（`Handler.handle`）は `throws Exception` なので、**そこから呼ぶメソッドにも `throws Exception` を付ける**
  （サンプルの `SaveUseCase.save(...) throws Exception` の形）
- `Runnable` / `forEach` / `Supplier` などのラムダの中では投げられない。**トランザクションはラムダの外で張る**
- **`catch (Exception e) {}` で黙らせない。**`commitEndTransaction()` の `CodeException`（`DB_004`）は
  「中でエラーが出たのでロールバックした」という知らせで、捨てると**保存できていないのに成功を返す**

**中で1度でもエラーが出ていたら、コミットしない。**
ロールバックして `CodeException`（`DB_004`）を投げる——
エラーが戻り値で返る作りなので、<b>そのままだと部分的にコミットされていた</b>。

**失敗のあとの文が通るかは製品による**（PostgreSQL は断る／MySQL は通す）。
どちらにも寄りかからず、**失敗したら続ける前に `rollback()` する**。
枠組みが約束するのは「コミットは拒まれ、1行も残らない」ところまで。

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

起動時に流すなら `Migration.install()` を **`DBUtil.load(...)` より前**に呼ぶ。

```java
Migration.install();                                  // 登録するだけ。ここでは流れない
if (!DBUtil.load(Conf.conf().config(), App.class)) { // 流れるのはこの中
	throw new IllegalStateException("DB を読み込めませんでした");
}
```

- **`install()` の戻り値は「登録したか」。流れたかではない。**`false` は `migration.on_startup = false` で
  **わざと切ってある**ときだけ。`if (!Migration.install()) throw ...` と書くと、切ってある環境で起動しなくなる。
  **`true` でもまだ1行も流れていない**
- **`DBUtil.load(...)` のあとに呼ぶと何も起きない。**登録した処理はもう走り終わっているので、黙って流れない
- 流すのに失敗したら **`DBUtil.load(...)` が例外で落ちる**（戻り値の `false` ではない）。捕まえずに起動を止める

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
