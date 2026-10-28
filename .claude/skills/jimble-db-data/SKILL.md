---
name: jimble-db-data
description: jimble の DB の結果（Data）を読むときに使う。ビルダーの結果がテーブル名でネストすること（getData・flattenTable・extractTableData は平らにならない）、文字列の SQL の結果は平らなこと（結合で列が上書きされる・別名 テーブル__列）、JSON の列が読んだ時点で Data / List になること、getter の DataConversionException、Data.put が Data を返さないことを含む。
---

# jimble の DB の結果（Data）

全体の決まりと、ほかの skill への案内は `jimble-db`。

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
DB から読んだ値ならまず起きないが、**利用者の入力を同じ getter で読むと 500 になる**——
先に `rules.validate(db, input);` を通せば 422 で返る（`jimble-web-input` の skill）。
無くてよい enum は `getEnumOptional(key, 型)`。

**`Data.put` は `Data` を返さない。**`Map.put` なので戻り値は**前の値**（`Object`）。
`new Data().put("a", 1).put("b", 2)` はコンパイルが通らず、`return data.put("x", v);` は前の値（初めてなら `null`）を返す。
続けて書くなら **`putData("a", 1).putData("b", 2)`**（自身を返す）。

## 文字列の SQL の結果はネストしない

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

## JSON の列は、読んだ時点で Data / List になっている

MySQL の `JSON`、PostgreSQL の `json` / `jsonb` の列は、**枠組みが読んだところで解いてしまう**
（オブジェクトは `Data`、配列は `List`）。ビルダーでも文字列の SQL でも同じ。

```java
Data row = db.select("SELECT * FROM setting WHERE id = ?", id).orElseThrow(() -> new HttpException(404, "ありません"));
// tags 列の中身が ["a","b"] のとき

row.getString("tags");                     // "a"  ← 配列の先頭の要素だけ。JSON の文字ではない
row.getStringList("tags");                 // ["a", "b"]  ← 配列はこちらで取る（中がオブジェクトなら getDataList）
row.getData("options");                    // オブジェクトの列は Data で取る
```

- **中の小数は `Double`**（2.5.6。それまでは `Float` で、`1234567.89` が `1234567.9` になっていた）。
  `get(...)` を `Float` にキャストしない。`getDouble` / `getBigDecimal` で取る
- 配列の列を `getString` すると、**1度だけ WARN**（「JSON の配列の列 〜 を getString しています」）が出る。見たらここを直す
- **`getString` で JSON の文字を取ろうとしない。**オブジェクトの列は JSON の文字になるが
  （キーの間の空白などは元のとおりではない）、**配列の列は先頭の要素しか返らない**。
  それを `Dson.decodes(..., List.class)` に渡すと、JSON ではないので **`JsonParseException`**
- **元の文字のまま欲しいなら、SQL で文字に変えて読む**：MySQL は `CAST(列 AS CHAR)`、
  PostgreSQL は `列::text`（または `CAST(列 AS text)`）
- `Dson.decodes(文字列)` / `Dson.decodes(文字列, Data.class)` に**一番外が配列の JSON** を渡すと、
  `{"0": ..., "1": ...}` の Data になる（添字がキー）。配列は **`Dson.decodes(文字列, List.class)`** で読む。
  壊れた JSON は `JsonParseException`（空文字と `"null"` は `null`）。JSON 全般は `jimble-json`

## 型の対応

**`tinyint` は桁数に関係なく `boolean`。**`decimal` は `double` なので金額計算に向かない。

詳しくは <https://jimble.io/ja/sql.md> / <https://jimble.io/ja/codegen.md>（型の対応）。
