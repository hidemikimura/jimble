---
title: 遅延読み込みと先読み
summary: 参照された時点で引く。全部たどるなら IN 句1本にまとめる
section: データベース
order: 4
---

# 遅延読み込みと先読み

一覧に「詳細も返せるようにしておきたいが、返さないことも多い」ものがあります。
`AsyncData` / `AsyncList` は、**参照された時点ではじめて SQL を投げます**。

```java snippet=async-load
```

```java
Data post = ...;
post.put("comments", new CommentList(post.getLong(Post.id)));

// ここまでは SQL が飛ばない
post.getJsonString();   // ← ここで飛ぶ
```

## 実装するもの

| メソッド | 何をするか |
| --- | --- |
| `load()` | 1件ぶん引く |
| `setData(Data)` | 引いたものを自分に反映する |
| `setRelationData(...)` | 全件の `setData` が終わってから1回だけ。要らなければ書かない |
| `hashKey()` | 同じものかどうかを決めるキー |

`AsyncData` は `Data`、`AsyncList` は `List` として振る舞うので、
読み込んだあとは普通のデータです。

## 読み込みを起こさずに見られるもの

```java
list.isLoaded()       // 読み込み済みか
list.isLoadFailed()   // 失敗したか
list.loadedValues()   // すでに持っている値だけ
list.loadedSize()     // すでに持っている件数
```

`toString()` も読み込みを起こしません。**ログに1行出しただけで枝という枝に
SQL が飛ぶ**、ということが起きないようにしてあります。

`hashCode()` / `equals()` も `hashKey()` しか見ません。**比較しただけで
SQL が飛びません。**

## 読み込みに失敗したとき

例外は投げません。ログを出して先へ進みます。

**失敗しても「読み込み済み」になります。** そうしないと、参照されるたびに
落ちるクエリを投げ続けます。失敗したことは `isLoadFailed()` で分かります。

## N+1 になるとき

一覧の全行にぶら下げて、全部を JSON にすると **1 + N 本**飛びます。

```
記事を20件引く          1本
  記事1のコメント        1本
  記事2のコメント        1本
  ...                   ×20
```

これを2本にするのが**先読み**です。

## 先読み

`load()` と同じことを、`IN` 句で書きます。

```java snippet=async-load-batch
```

あわせて、まとめる単位を宣言します。

```java
@Override
public String batchKey () { return "CommentList"; }

@Override
public Object batchId () { return postId; }
```

**同じ `batchKey` を持つ未読み込みの枝が、`loadBatch` 1回にまとまります。**
`ids` には、ほかの枝の `batchId` も入って渡ってきます。

`load()` と `loadBatch()` は**同じファイルに並べて置いてください**。
片方だけ直すと結果がずれます。置き場を分けない理由がこれです。

## 走らせる

```java snippet=prefetch-route
```

`AsyncPrefetch.run(...)` がツリーを走査し、未読み込みで `batchKey` を持つ枝を
集めて埋めます。**走査そのものは SQL を起こしません**（未読み込みの枝の中は見ません）。

レスポンスを送る直前に自動で走らせることもできます。

```conf
async {
	prefetch {
		on_response = true
		max_depth   = 5
	}
}
```

**既定は `false`** です。隠れた I/O を作らないためで、
入れたとたんに挙動が変わることはありません。

## 先読みしてもしなくても、結果は同じ

変わるのはクエリの本数だけです。

- `loadBatch` が返さなかった `batchId` は、**個別に引いて0件だったのと同じ**扱いになります
- `loadBatch` が例外を投げたら、**そのまとまりは1つも読み込み済みにしません**。
  それぞれが個別に `load()` されるので、結果は変わりません（速さだけが戻ります）
- `loadBatch` を書いていないクラスは、そのまま個別に読まれます

## 止まること

埋めると新しい枝が生えるので、生えなくなるまで繰り返します。
無限に回らないよう、3つで止めています。

| | |
| --- | --- |
| `(batchKey, batchId)` は1回だけ展開する | A が B を、B が A を持っていても2周目で止まる |
| 周回の上限 | `async.prefetch.max_depth`（既定 5） |
| 同じオブジェクトを二度たどらない | 参照の同一性で判定する |

## テーブルネストあり / なしを選んで JSON にする

SELECT の結果は**テーブル名でネスト**しています。

```json
{"post": {"id": 1, "title": "こんにちは"}}
```

これを `setData` で平らにするか（`flattenTable`）、そのまま持つか（`extractTableData`）は、
これまで**クラスを書いた時点で決まって**いました。
つまり 1 つの `AsyncData` は 1 つの形しか出せません。

管理画面 API はネスト、ショップ API はフラットで返したい、となると
**同じ内容のクラスを 2 つ書く**ことになります。片方だけ直したときに気づけません。

**形は JSON にするときに選べます。**

```java
data.getJsonString(TableNest.ON);    // {"post": {"id": 1}, "comments": [...]}
data.getJsonString(TableNest.OFF);   // {"id": 1, "comments": [...]}
```

レスポンス単位でも指定できます。

```java
path("/admin", () -> {
	before(context -> context.response().tableNest(TableNest.ON));
	get("/posts/{id}", context -> context.response().json("post", new PostData(id)));
});

path("/shop", () -> {
	before(context -> context.response().tableNest(TableNest.OFF));
	get("/posts/{id}", context -> context.response().json("post", new PostData(id)));
});
```

**返しているのは同じクラスです。**

### どうやって形が分かるのか

`AsyncData` / `AsyncList` は `load()` が返した**生のデータを持ったまま**です。
その生データが「どのキーがどのテーブルの、どの列か」を知っています。

```
{"post": {"id": 1, "title": "..."}}
  ↑ テーブル名   ↑ 列名
```

だから、`setData` を `flattenTable` で書いても `extractTableData` で書いても、
**どちらの形にも組み直せます**。実装側に書き足すものはありません。

### 列でないものは動きません

`setRelationData` で足した子（`AsyncData` / `AsyncList`）や計算値は、
生データの列に無いので**常に最上位に残ります**。

```json
{"post": {"id": 1, "title": "..."}, "comments": [...]}
```

> [!note]
> **既定は `TableNest.AS_IS`** です。指定しなければ `setData` が作った形がそのまま出ます。
> いまあるアプリの出力は 1 バイトも変わりません。

> [!trap]
> **組み直せないものがあります。**
> 自由 SQL や集約でテーブルネストしていない結果（`{"total": 12}` など）は、
> テーブル名が分からないので**そのまま出ます**。
> `putData(null)` を受けたノードも同じです。
>
> JOIN していて**同じ列名が 2 つのテーブルにある**とき（`post.id` と `comment.id`）は、
> 平らに持っている時点ですでに片方で上書きされていて、どちらの値かは分かりません。
> ネストに戻すときは**先に出てきたテーブル**に入ります。
