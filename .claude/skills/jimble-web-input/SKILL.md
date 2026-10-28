---
name: jimble-web-input
description: jimble の Web で利用者の入力を読む・検査するときに使う。bodyAll / bodyPath / bodyQuery / bodyForm / bodyJson の重なり方、Request は Data ではないこと、入れ子と添字の上限、JSON の小数、getter の DataConversionException（検査を先に通す）、アップロード（bodyFile・ファイル名）、ValidationRules（validate は void・errors・required・insertRequired・422 の形）、paging() を含む。
---

# jimble の Web で入力を読む

全体の決まりと、ほかの skill への案内は `jimble-web`。

## 入力を読む

| | 中身 |
| --- | --- |
| `bodyPath()` | パスパラメータ（`/users/{id}`） |
| `bodyQuery()` | クエリ文字列 |
| `bodyForm()` | フォーム / multipart |
| `bodyJson()` | JSON の本文 |
| `bodyAll()` | 全部重ねたもの（**path → query → form → json** の順で上書き） |

```java
Data input = context.request().bodyAll();
String title = input.getString("title");
```

**`Request` は `Data` ではない。**`context.request().getString("x")` は書けない（コンパイルエラー）。
値は `bodyAll()` / `body()` / `bodyQuery()` などから読む（`query()` はクエリの生の文字列）。
本文が `application/json` なのに読めなければ、`bodyAll()` などが **400 の `HttpException`** を投げる。
**512 段より深く入れ子になった JSON も 400**（2.2.4。読まずに断る）。

入れ子は `user.name=taro` でも `user[name]=taro` でも同じ。
`items[0].price` は添字、`items[].price` は末尾に追加。
**添字は 10,000 まで**（1回のリクエストで伸ばせる配列は合わせて 100,000 要素まで）。超えると 400（2.2.2）。
**キーの入れ子（`a.b.c` / `a[b][c]`）は 32 段まで**。超えると 400（2.5.2）。

**JSON の小数は `Double` で返る**（2.5.6。それまでは `Float`。`Double` に収まらなければ `BigDecimal`）。
`(Float) input.get("price")` は落ちる。`getDouble` / `getBigDecimal` で読む。

無いキー（キーが無い・空欄）は `getString` なら `null`、`getInt` なら `0`、`getBoolean` なら `false`。
「無い」と「0」を分けたいなら `getIntObject` か `isNull(key)`、既定値は `getInt("page", 1)`。
**あるのに読めない値（`"abc"`、int に `"1.5"`、真偽に `"yes"`）は `DataConversionException` → 500。**
黙って 0 にはならない。**利用者の入力は、getter で読む前に検査を通す**（`?id=abc` でも、先に `rules.validate(db, input);` を通せば 422 で返る）。
キーが無い・空欄は 0 のままなので、必須は `required()` で落とす。

`paging()` は枠組みが寛容に読む（`?page=abc` は1ページ目）。
**`paging()` のあとに `paging(50)` を呼ぶと `IllegalStateException`。**件数を変えるなら最初から `paging(50)`。

**リクエストを `apply(Data)` / `setRow(Data)` / `valueRow(Data)` にそのまま渡さない**（隠れた列や `role` を当てられる。`jimble-db-write`）。
**JSON の配列や `a[]=` はそのままリストになる。**SQL の `eq` などに渡すと `SqlBuildException` なので、検査で弾く（`jimble-db-select`）。

### アップロード

ファイルは `bodyFile()`（項目名ごとに `UploadFile` のリスト。`bodyAll()` には載らない）。`UploadFile` は `io.jimble.util.convertor`。

- 日本語・`+`・`%`・`;` を含むファイル名も、送られたとおりに受け取る（2.5.1。それまでは日本語の名前が 400）
- **ファイルを選ばずに送った入力（`filename=""`）は捨てる**（`bodyFile()` に載らない。2.5.1）
- `fileName()` は最後の名前だけ、フォルダごとのアップロード（`webkitdirectory`）は `relativePath()`（`写真/2026/a.png`。2.5.1）
- `.` / `..` の段・空の段・制御文字・ドライブ指定（`C:evil.jsp`）・文字の向きを変える文字を含む名前は 400（2.5.2）。
  **それでも `fileName()` をそのまま保存先のパスにしない**（長すぎる名前や OS で使えない文字は入りうる）。
  `FileUtil.safeFileName(名前, 拡張子を含めるか, 置き換える文字)` を通す

## 検査（バリデーション）

```java
new ValidationRules()
	.put(Post.title, new ValidationRule().required().textLengthMax(100))
	.put(Post.category_id, new ValidationRule().required().integer(1, 9999))
	.validate(db, input);                  // 通らなければ ValidationException（422）。戻り値は無い

long categoryId = input.getLong(Post.category_id);   // 検査を通したあとなら、読めない値で 500 にならない
```

- **`validate(...)` は `void`。**`Data errors = rules.validate(...)` と書かない。通らなければ `ValidationException`（`HttpException` の子、422）を投げ、
  枠組みが `{"validation": {項目: [メッセージ]}}`（と入力値）で返す。自分で `if` を書かない
- **エラーの一覧が欲しいときだけ `errors(db, input)`**（`Data`。空なら通った）。本文を自分で作るならこちら
- 明細などの一覧は `validate(db, list)` / `errors(db, list)`（エラーのある行だけ、1始まりの `index` つき。`validate` の 422 は `{"rows": [...]}` を包む）
- **`required()`（= `empty()`）はキーが送られてこなくても失敗する。**ほかの規則は空を通すので、必須には `required()` を積む。
  **空の配列（`{"name": []}`）も「値が無い」として見る**（2.2.3。それまでは1度も検証されず、必須も素通りした）
- **部分更新（PATCH）の規則に `required()` を付けると、送られてこない項目で 422 になる。**
  「登録のときだけ必須、更新は送られた項目だけ見る」は `insertRequired()` と `insertRequestChecker(...)`（登録かどうかを決める）
- `ValidationExecutor`（ルートに積む検査）も同じ 422 の形。こちらは `addError(...)` で積み、`error` は通らず `onCancel` で返す
- **422 に載せる入力値から、名前に `password` / `secret` / `token` などを含む項目を落とす**（2.2.4。リストの中の `users[0][password]` も落とすのは 2.5.2 から。64 段より深いところは写さない）。
  入力し直してもらう画面で、パスワードの欄を 422 の本文から埋め戻すことはできない
- **`url()` は 1,024 文字、`email()` は 254 文字まで**（2.2.4。超えたら検証の誤り）
- `error(...)` が `ValidationException` にも本文を組むと 422 の `{"validation": ...}` が消える（`jimble-web-response` の「エラー」）

詳しくは <https://jimble.io/ja/request-response.md> / <https://jimble.io/ja/validation.md> / <https://jimble.io/ja/upload.md>。
