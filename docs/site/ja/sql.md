---
title: SQL DSL
summary: SQL.select / insert / update / delete
section: データベース
order: 2
---

# SQL DSL

文字列で SQL を書くこともできますが、DSL のほうが型が効きます。

## 引く

```java
List<Data> posts = db.selectList(
	SQL.select()
		.from(Post.instance())
		.where(Post.published.eq(true))
		.orderBy(Post.created_at.desc())
		.limit(20)
);
```

`limit` と `offset` があります。`select()` に何も渡さなければ全列です。列を選ぶなら渡します。

```java
SQL.select(Post.id, Post.title).from(Post.instance())
```

## 条件

```java
.where(Post.id.eq(1L))
.where(Post.title.like("%jimble%"))
.where(Post.created_at.ge(from).and(Post.created_at.lt(to)))
.where(Post.id.in(List.of(1L, 2L, 3L)))
```

`where` を複数回呼ぶと AND で繋がります。

## 結合

```java
SQL.select()
	.from(Post.instance())
	.left(Comment.instance()).on(Comment.post_id.eq(Post.id))
	.where(Post.id.eq(id))
```

内部結合は `inner(...)` です。`on()` は直前の結合に付きます。

結果はテーブル名でネストするので、`row.getData("comment")` で取れます。

## 入れる・直す・消す

```java
long id = db.insert(
	SQL.insert(Post.instance())
		.value(Post.title, title)
		.value(Post.created_at, new Date())
);

int updated = db.update(
	SQL.update(Post.instance())
		.set(Post.published, true)
		.where(Post.id.eq(id))
);

int deleted = db.delete(
	SQL.delete(Post.instance()).where(Post.id.eq(id))
);
```

`insert` は採番された ID を返します。ID が要らないときは
`insertNoReturnKey` のほうが速いです。

送られた項目だけ直す、という書き方はこうなります。

```java snippet=rest-patch
```

## 関数を使う

`Dsl` に関数が並んでいます。**どれも MySQL と PostgreSQL の両方で動きます。**

```java
SQL.select(Dsl.count(), Dsl.max(Post.created_at)).from(Post.instance())
```

`CASE WHEN` のように**値としても列としても使えるもの**は、
select に直接は渡せません。`SelectQuery` で包んでください。

```java
SQL.select(new SelectQuery().dsl(Dsl.caseWhen()...)).from(...)
```

包まずに渡すと、列ではなく**バインドされる値**として扱われます。

### 一覧

| 系統 | ある関数 |
| --- | --- |
| 文字列 | `lower` / `upper` / `trim` / `ltrim` / `rtrim` / `length` / `byteLength` / `substring` / `replace` / `left` / `right` / `lpad` / `rpad` / `reverse` / `repeat` / `concat` / `concatWs` / `md5` / `locate` |
| 数値 | `abs` / `mod` / `power` / `sqrt` / `sign` / `exp` / `ln` / `log10` / `ceiling` / `floor` / `round` / `truncate` / `greatest` / `least` |
| 日付 | `now` / `curDate` / `curTime` / `date` / `year` / `month` / `day` / `hour` / `minute` / `second` / `quarter` / `dayOfWeek` / `dayOfYear` / `weekOfYear` / `dateAdd` / `dateSub` / `dateDiff` / `secondsBetween` / `unixTimestamp` / `fromUnixTime` / `secondsAgo` … `yearsAfter` |
| 条件・型変換 | `caseWhen` / `ifThenElse` / `ifnull` / `coalesce` / `nullif` / `cast` / `castDecimal` / `regexp` / `regexpIgnoreCase` |
| 集約 | `count` / `countDistinct` / `sum` / `sumDistinct` / `min` / `max` / `avg` / `stddev` / `variance` / `groupConcat` / `groupConcatDistinct` |
| ウィンドウ | `rowNumber` / `rank` / `denseRank` / `nTile` / `lag` / `lead` / `firstValue` / `lastValue` / `over` |
| JSON・地理空間 | `jsonExtract` / `jsonUnquote` / `stGeomFromText` / `stDistanceSphere` / `stWithin` / `match` |

```java
// 名前を整えて、空なら「（無名）」
Dsl.coalesce(Dsl.trim(Post.title), "（無名）").as("title")

// 月ごとの件数
SQL.select(Dsl.year(Post.created_at).as("y"), Dsl.month(Post.created_at).as("m"), Dsl.count())
    .from(Post.instance())
    .groupBy(Dsl.year(Post.created_at), Dsl.month(Post.created_at))

// 30 日後
Dsl.dateAdd(Post.created_at, 30, DateUnit.DAY)

// タグをまとめて1つの文字列に
Dsl.groupConcat(Tag.name, "/").as("tags")
```

単位（`DateUnit`）と型（`CastType`）は enum です。**文字列では渡せません。**
SQL にそのまま入るところなので、外から来た文字列を通せないようにしてあります。

## まとめた値で絞る（`having`）

**集計した値は、そのまま条件にできます。**列と同じ書き方です。

```java
SQL.select(
        Department.id
        , Dsl.sum(Request.amount).as("total"))
    .from(Request.instance())
    .groupBy(Department.id)
    // 合計が 50 万を超える部署だけ
    .having(Dsl.sum(Request.amount).ge(500_000L));
```

`CASE` の条件にも置けます。

```java
new SelectQuery().dsl(Dsl.caseWhen()
        .when(Dsl.sum(Request.amount).ge(500_000L)).then("大")
        .elseCase("小")).as("size")
```

書けるのは**比べるもの**だけです
（`eq` / `not` / `gt` / `lt` / `ge` / `le` / `between` / `is_null` / `is_not_null`）。
`like` や `contains` は足していません。集計に対しては、書けても意味がないためです。

> [!TRAP]
> **`where` には書けません。**SQL の決まりで、`WHERE` は集計より前に評価されます。
> まとめたあとの値で絞るのは `having` の仕事です。

## ウィンドウ関数

**行をまとめずに、まとめた結果を各行に付けます。**`GROUP BY` と違って行が減りません。

```java
SQL.select(
        Sale.shop_id
        , Sale.amount
        // 店ごとの売上順位
        , Dsl.rank().partitionBy(Sale.shop_id).orderBy(Sale.amount.desc()).as("rank")
        // 累計
        , Dsl.over(Dsl.sum(Sale.amount))
            .orderBy(Sale.sold_at.asc())
            .rowsBetween(WindowFrame.unboundedPreceding(), WindowFrame.currentRow())
            .as("total"))
    .from(Sale.instance());
```

`Dsl.over(...)` には集約（`sum` / `count` / `avg` など）を渡します。
別名や計算を中に入れると例外になります（`OVER` のあとに付けてください）。

> [!WARNING]
> **ウィンドウ関数は `where` にも `having` にも書けません。**
> SQL の決まりで、評価されるのがどちらより後だからです。
> `Dsl.rowNumber().over(...).eq(1)` と書くと**組み立てた時点で例外**になります。
> 順位で絞るなら、いったん副問い合わせで出してから外側で絞ってください。

## `in` に空の一覧は渡せません

```java
where(Site.id.in(List.of()))   // ← 組み立てた時点で例外
```

`IN ()` は構文エラーです。**黙って DB に投げると、
エラーメッセージからどこで空を渡したのか辿れません**（要件 F-D-07）。

**「空なら条件を外す」ことはしません。**
`in(空)` は「どれにも当たらない」で、条件を外すと**全件**です。
どちらの意味かは呼び出し側にしか分からないので、呼び出し側で分けてください。

```java
if (ids.isEmpty()) {
	return List.of();          // 「どれにも当たらない」
}
where(Site.id.in(ids));
```

## 製品の違いで気をつけること

**名前が同じでも意味が違う**ものは DSL が揃えます。

| | どう揃えているか |
| --- | --- |
| `length` | **文字数**です（MySQL の `LENGTH` はバイト数）。バイト数は `byteLength` |
| `dayOfWeek` | **日曜が 1**（PostgreSQL の `DOW` は 0 なので +1 しています） |
| `weekOfYear` | **ISO 週**（MySQL の `WEEK` は既定が ISO ではないので `WEEKOFYEAR`） |
| `second` | 小数秒は**切り捨て**（PostgreSQL は素だと四捨五入して 60 を返すことがあります） |
| `unixTimestamp` | **接続のタイムゾーン**で読みます（PostgreSQL の `timestamp` は素だと UTC 扱い） |
| `stddev` / `variance` | **標本**（`STDDEV_SAMP` / `VAR_SAMP`） |
| `concat` | 1つでも NULL なら NULL（PostgreSQL では `\|\|` になります） |

**揃えていない**ものもあります。

| | 違い |
| --- | --- |
| `greatest` / `least` | **MySQL は1つでも NULL なら NULL、PostgreSQL は NULL を無視します。**NULL が入りうるなら `ifnull` で埋めてから渡してください |
| `regexp` | 正規表現の方言が違います（MySQL 8 は ICU、PostgreSQL は POSIX）。`^` `$` `[]` `+` は同じですが、**`\d` は片方でしか効きません。**`[0-9]` と書いてください |
| 0 除算・負の長さ | MySQL は NULL を返し、**PostgreSQL は落ちます**（`mod(x, 0)`、`left(x, -1)` など） |
| 数値を文字列関数に渡す | MySQL は暗黙に変換し、**PostgreSQL は「関数が無い」で落ちます。**`cast` を挟んでください |
| `groupConcat` | MySQL は `group_concat_max_len`（既定 1024 バイト）を超えると**黙って切ります** |
| `cast` で数にできない文字列 | MySQL は **0** を返し、PostgreSQL は落ちます |

**その製品に無いものは、SQL を組み立てたところで例外**（`DialectException`）になります。
[DB を使う](./db) にまとめてあります。

## DB 製品が違うとき

**同じコードのまま、`db.xxx.product` に応じた SQL が出ます。**
識別子の囲み（`` ` `` と `"`）、`ON DUPLICATE KEY UPDATE` と `ON CONFLICT`、
`INSERT IGNORE` と `ON CONFLICT DO NOTHING`、`RAND()` と `RANDOM()` などは
ビルダーが吸収します。

**その製品で書けないものは、SQL を組み立てたところで例外**（`DialectException`）になります。
どれが書けないかは [DB を使う](./db) にまとめてあります。

```java
// PostgreSQL では DialectException（to_char は書式の言語が違う）
SQL.select(Dsl.dateFormat(Post.created_at, "%Y-%m-%d")).from(Post.instance());
```

## まとめて流す

```java
List<Integer> counts = db.executeBatch(builderList);   // 件数のリスト
List<Long>    ids    = db.insertBatch(builderList);    // 採番値のリスト
```

**2つで戻り値が違います。**`executeBatch` は件数（`List<Integer>`）、
`insertBatch` は採番値（`List<Long>`）です。
件数のほうは `DB.isBatchSuccess(list)` で全部通ったか見られます。

> [!TRAP]
> **積んだビルダーの SQL は、全部同じでなければなりません。**
> 1本の文にパラメータだけを積み替えて流すためです。
> `value()` を積む順が途中で変わると SQL も変わるので、
> **ループの中で順を揃えてください**。
> 揃っていなければ `DB_998` を立てて `null` が返ります
> （直すまでは、例外も警告も無しに**値が横にずれて入っていました**）。

## 大きい結果

全部をリストに載せたくないときは、1行ずつ受け取ります。

```java
db.selectListWithFetcher(row -> {
	// 1行ずつ来る
}, SQL.select().from(Post.instance()));
```
