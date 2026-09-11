---
title: リクエストとレスポンス
summary: 入力の読み方、入れ子パラメータ、検証、返し方
section: 基本
order: 2
---

# リクエストとレスポンス

## 入力を読む

`context.request()` から取れます。どこから来た値かで分かれています。

| メソッド | 中身 |
| --- | --- |
| `bodyPath()` | パスパラメータ（`/users/{id}`） |
| `bodyQuery()` | クエリ文字列 |
| `bodyForm()` | `application/x-www-form-urlencoded` / `multipart` |
| `bodyJson()` | JSON の本文 |
| `bodyFile()` | アップロードされたファイル（[ファイルアップロード](./upload)） |
| `bodyAll()` | 上を全部重ねたもの |

`bodyAll()` の重ね順は **path → query → form → json** です。
あとのものが前を上書きします。

戻り値はどれも `Data`（`LinkedHashMap<String,Object>` の派生）です。
`getString` `getInt` `getLong` `getBoolean` `getData` `getDataList` などで取り出します。
**無いキーは `getString` なら `null`、`getInt` なら `0`、`getBoolean` なら `false`** です
（「無い」と「0」は区別できません。区別したいときは `getIntObject` などの Object 版か `isNull(key)`。
詳しくは [ユーティリティ](./util)）。

> [!TRAP]
> **`context.request()` から直接は読めません。**
> `Request` も `Data` なので `context.request().getString("title")` は<b>コンパイルが通り</b>ますが、
> 本文もクエリも入っていないので **`null` が返ります。**
> 上の表のどれか（ふつうは `bodyAll()`）を通してください。
>
> ```java
> Data input = context.request().bodyAll();
> String title = input.getString("title");
> ```
>
> **`getStringOptional` は「無ければ空文字」ですが、その空文字を `Data` に入れます。**
> 読んだだけでキーが増えるので、JSON にして返す直前やループの中では使わないでください
> （[ユーティリティ](./util)）。

## 入れ子のパラメータ

`.` と `[ ]` のどちらでも入れ子にできます。

```
user.name=taro
user[name]=taro          同じ
items[0].price=100
items[].price=100        末尾に足す
```

`[ ]` の中が数字なら添字、空なら追加、それ以外なら名前です。
JSON の本文と混ざったときは、フォームの値が JSON を上書きします。

## 検証する

```java snippet=validation
```

**エラーは最初の1件で止めません。** 全部集めてから返します。
入力し直す人にとって、1件ずつ言われるのがいちばん困るからです。

`ValidationMessages.toMessages(errors)` で、項目名 → メッセージの `Data` になります。
そのまま JSON で返すか、テンプレートに渡します。

ルールの一覧、ルート単位でかける `ValidationExecutor`、ページングは
[検証とページング](./validation) にまとめてあります。

## 返す

```java
context.response().send("text");                   // text/plain
context.response().json("posts", list);            // JSON
context.response().view("blog/posts.jte");         // テンプレート
context.response().redirect("/");                  // 302
context.response().download(file, "報告書.xlsx");  // ダウンロード
context.response().code(201).send();               // 本文なし
```

`json()` は組み立てるだけです。複数回呼ぶと、1つの JSON に足されていきます。

**`json()` のあとに `send()` を書く必要はありません。**
組み立てておけば、ディスパッチャが実行の終わりに送ります。
`send()` を明示するのは、**本文なしで終わらせたいとき**（`code(204).send()`）や、
テキストをそのまま返すときです。

```java snippet=json-route
```

## 大きいものを流す

全部をメモリに載せたくないときは `outputStream()` を直接使います。

```java
context.response().setResponseHeader("Content-Type", "text/csv; charset=UTF-8");

try (OutputStream out = context.response().outputStream()) {
	// 1行ずつ書く。書いたそばから流れていく
}
```

進捗を送りたいだけなら [SSE](./sse) のほうが簡単です。

## 二重送信

`send()` を2回呼ぶとエラーになります。
`isSent()` で送信済みかどうかを見られます。
`after` フィルタや `error` ハンドラの中では、これを見てから触ってください。
