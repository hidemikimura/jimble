---
name: jimble-db-select
description: jimble の SQL ビルダーで行を引くときに使う。select（Optional）と selectList、where の重ね方と NULL の比較、OR と括弧（Dsl.anyOf・a.or(b).and(c) の読み方）、in と空のリスト、1つの値の場所にリストを渡さない、結合と on、インデックスのヒント（MySQL）、ページング、カーソル（selectListWithFetcher）の閉じ方、形の決まった SELECT を値だけ差し替えて使い回す（SQL.template・Dsl.param・bind）を含む。
---

# jimble の DB で引く

全体の決まりと、ほかの skill への案内は `jimble-db`。結果の `Data` の読み方は `jimble-db-data`。

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

**`select` は `Optional<Data>`**（`selectCached` も同じ）。0件は空、読めなければ例外。
「無ければ 404」は `.orElseThrow(() -> new HttpException(404, "..."))`、
「無ければ null で続ける」は `.orElse(null)`、有無だけなら `.isPresent()` / `.isEmpty()`。
**`select(...).get()` を書かない**（0件で `NoSuchElementException` → 500）。

## 条件

条件は重ねると AND。

```java
.where(Post.title.like("%jimble%"))
.where(Post.created_at.ge(from).and(Post.created_at.lt(to)))
.where(Post.id.in(List.of(1L, 2L, 3L)))
.where(Post.deleted_at.is_null())          // eq(null) は SqlBuildException。is_null() / is_not_null()
```

- **`列.eq(null)` / `not(null)` は `SqlBuildException`。**NULL の比較は `is_null()` / `is_not_null()`。
  値が null かもしれない変数を `eq(x)` に渡すところは、分けて書く

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

**`in()` に空のリストを渡すと組み立てた時点で例外。**「空なら条件を外す」はしない——
外すと**全件**になる。呼び出し側で `if (ids.isEmpty()) return List.of();` と分ける。

**1つの条件に比較は1つ。**`Post.id.ge(1).le(9)` は組み立てで落ちる。
範囲は `between(a, b)`、別の条件は `.and(Post.id.le(9))`。列に直接 `.and(...)` も落ちる。

**リクエストを `apply` に渡すなら、許す列を並べる**（`jimble-db-write` の「許す列」）。

## 結合とヒント

結合は `left(...)` / `inner(...)` と `on(...)`。`on()` は**直前の結合に付く**。
結合の無いところの `on()` は落ちる。2度呼ぶと AND。

インデックスのヒント（MySQL。2.5.0）はテーブルに付けて渡す：`from(Orders.instance().forceIndex("orders__created_at"))`、`left(Customer.instance().useIndex("customer__code"))`、`ignoreIndex("PRIMARY")`。
**PostgreSQL では組み立てたところで `DialectException`**（黙って外さない）。最後の手段にし、`EXPLAIN` で効いていることを確かめる。

## 形の決まった SELECT（`SQL.template`。3.0.0）

要求のたびに同じ形（値だけ違う）で引く SELECT は、形を一度だけ作って値を差し替える。SQL の字面は方言ごとに 1 回だけ作る。

```java
static final SelectTemplate POST_BY_ID = SQL.template(SQL.select()
	.from(Post.instance())
	.where(Post.id.eq(Dsl.param("id"))));                  // 値の場所
static final SelectTemplate POSTS_IN = SQL.template(SQL.select()
	.from(Post.instance())
	.where(Post.id.in(Dsl.params("ids"))));                // in / not_in の値の並び

Optional<Data> post = db.select(POST_BY_ID.bind("id", id));
List<Data> posts    = db.selectListCached(POSTS_IN.bind("ids", ids));
```

- 出る SQL・パラメータ・結果の形（テーブル名でネスト）・キャッシュのキーは、同じ形のビルダーと同じ。`select` / `selectList` / `selectCached` / `selectListCached` に渡せる
- **`bind` に `null` と空の一覧は渡せない**（`SqlBuildException`。`eq(null)`・`in(空)` と同じ理由）。名前の過不足と、列などの SQL の部品も例外
- **`starts_with` / `contains` / `ends_with` に印は置けない**。`like(Dsl.param("q"))` にして `%` を付けた値を bind する
- **`Dsl.param` / `Dsl.params` は template の中でだけ。**印を置いたビルダーをそのまま投げると `SqlBuildException`
- **`SQL.template(...)` に渡したビルダーはそのあと使わない**（FROM や条件の部品を分け合う）。`SQL.template(SQL.select()...)` とその場で組む
- 条件で WHERE を足したり足さなかったりする文には使わない（形が毎回変わる）。ビルダーで書く

## ページングとカーソル

**ページングのページ番号は、開始位置があふれない数までに抑える**（2.2.4。`?page=9223372036854775807` でも `start()` は負にならない）。

**`selectListWithFetcher`（カーソル）は、読み終わったら `db.close()` も呼ぶ。**
ふつうの SQL は1文ごとにコネクションを返すので閉じ忘れても漏れないが、
カーソルとトランザクション中は**握ったまま**。長いバッチでは `KeyPagingReader` を使う（`jimble-batch-write`）。

詳しくは <https://jimble.io/ja/sql.md>。
