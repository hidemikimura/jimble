---
title: 検証とページング
summary: ValidationRule / ValidationExecutor と、page・per の読み方
section: 基本
order: 4
---

# 検証とページング

## 検証は3段ある

| 段 | クラス | 何をするか |
| --- | --- | --- |
| 1項目 | `ValidationRule` | 「空でない」「1〜120 の整数」を積む |
| 1リクエスト | `ValidationRules` | 列ごとにルールを結びつけ、まとめて回す |
| 1ルート | `ValidationExecutor` | 失敗したら**後続の処理を止めて 422 を返す** |

下だけ、上だけ、どちらでも使えます。

## ルールを組み立てる

```java
ValidationRule rule = new ValidationRule()
	.empty()
	.textLengthMax(100);
```

積んだ順に走り、**最初に落ちたところで止まります**（1項目につきエラーは1件）。

| 分類 | メソッド |
| --- | --- |
| 必須 | `empty()` / `required()`（同じもの） |
| 文字数 | `textLength(min, max)` / `textLengthMin(min)` / `textLengthMax(max)` |
| バイト数 | `textByteLength(min, max[, charset])` / `textByteLengthMin` / `textByteLengthMax`（既定 UTF-8） |
| 数値 | `integer()` / `integer(min, max)` / `integerMin` / `integerMax` / `number()` / `number(min, max)` / `numberMin` / `numberMax` |
| 形式 | `bool()` / `email()` / `url()` / `domain()` / `date()` / `date(format)` / `regex(regex)` / `enumType(Class)` |
| 文字種 | `characterType(CharacterType[])` / `characterType(Character[])` / 両方 |
| 自作 | `custom(IValidator)` |
| 分岐 | `insertRequired()` |

> [!NOTE]
> **`empty()` 以外は、空を通します。**
> `textLengthMax(100)` は「入っているなら 100 文字以内」という意味で、
> 空文字や `null` はエラーにしません。**必須は必ず `empty()` で書いてください。**

> [!NOTE]
> **形の検証は、値の全体と突き合わせます。**
> `email()` `url()` `domain()` `regex(...)` `characterType(...)` は、
> **中にそれらしい文字列が入っているだけでは通りません**——
> `こんにちは a@example.com です` は `email()` で落ちます。
> `regex("[0-9]{4}")` は「全体が4桁の数字」の意味なので、
> **部分一致させたいときは `.*` で挟んでください。**

> [!NOTE]
> **`bool()` が通すのは `true` / `false` / `1` / `0` だけです**（大小は問いません）。
> `yes` や `はい` は落ちます——**読み出し側が同じ規則で読む**ので、
> ここを広げると「はい」と答えた人が `false` として保存されます。

> [!WARN]
> **`date()` と `date(format)` は「読めるかどうか」しか見ていません。**
> `2026-13-01`（13月）も `2026-02-31`（2月31日）も、
> `2026-09-12x`（後ろにゴミ）も **通ります**。
> `date()` は DB の読み出しやリクエスト変換と同じ共通変換を通っているためです。
> **暦として正しいことまで見たいときは、`regex(...)` を重ねるか `custom(...)` を書いてください。**

## 列にまとめる

```java snippet=validation
```

- **項目をまたいだエラーは全部集めます**（1件ずつ言われるのが、入力し直す人にはいちばん困るため）
- **送られてこなかった項目は検証しません**（`insertRequired()` を除く）
- 1つの列に配列が来たら、要素ごとに回します
- `put(rule)`（列なし）で、項目に紐づかない相関チェックも書けます

### 生のエラーの形

`validate` が返すのは**文言ではありません。**「どの種別で落ちたか」と「そのときの設定」です。

```java
{ "validation_type": Empty, "validation_setting": {}, "input": "" }
```

これを人が読む形に変えるのが `ValidationMessages.toMessages(errors)` で、
**項目名 → メッセージの一覧**になります。

```json
{ "title": ["入力してください"], "age": ["1 以上 120 以下の整数で入力してください"] }
```

> [!TIP]
> 文言を持たずに種別で返しているので、**同じ検証結果を日本語にも英語にも API のコードにも変えられます。**

### 文言を差し替える

```java snippet=validation-message
```

> [!TRAP]
> `ValidationMessages` は **static でグローバル**です。テストで差し替えたら
> `finally` で `ValidationMessages.reset()` してください。
> 忘れると、**あとから走ったテストだけが落ちます。**

## 登録のときだけ必須

```java snippet=validation-insert-required
```

「登録リクエストかどうか」の判定は `insertRequestChecker` に渡します。
同じ判定が各バリデータの `isInsertRequest` にも届きます。

## 複数行を検証する

```java snippet=validation-list
```

**エラーのある行だけ**返り、各行に `index` が入ります（**1 始まり**）。

## ルートにかける

`ValidationExecutor` を継承して `validate` だけ書きます。

```java snippet=validation-executor
```

ルートでは**いちばん先に積みます。**

```java snippet=validation-executor-route
```

失敗すると、こうなります。

| | |
| --- | --- |
| ステータス | **422** |
| 本文 | `{"validation": {"項目名": ["メッセージ"]}}` **＋ 送られてきた入力値** |
| 後続の Executor | **実行されない**（捨てられる） |
| `error(...)` フック | **通らない**（例外を投げていないため。[エラー処理](./errors)） |

`ValidationRules` の結果をそのまま積むこともできます。

```java
addErrors(rules.validate(db, context.request().bodyAll()));
```

> [!NOTE]
> `ValidationExecutor` は **`WebContext` をフィールドに持ちません。**
> `validate(WebContext)` の引数で受け取ります。
> インスタンスを使い回したときに、**前のリクエストのコンテキストに書き込む**事故を避けるためです。

> [!WARN]
> **列定義からルールは自動生成されません。**
> 生成されたテーブルクラス（`Post.title` など）が持っているのは
> 型・NULL 可否・主キーだけで、**varchar の桁数を持っていません。**
> `textLengthMax` を自動で導く材料が無いので、ルールは手で書きます。

## ページング

### リクエストから読む

```java
Paging paging = context.request().paging();
```

| | |
| --- | --- |
| 読むキー | `page` / `per` |
| 既定 | `page = 1`、`per = 10` |
| 上限 | `paging.max_per`（既定 200）。**超える指定は上限に丸めます** |
| 全件 | `per=all`。**上限が効くので、既定では 200 件までです**（`paging.max_per = 0` で本当に全件） |
| キー名の変更 | `paging.name_page` / `paging.name_per`（設定） |
| 数値でない値 | 無視して既定を使います |

`context.request().paging(20)` と書けば、`per` が来ていないときの既定を変えられます。

### SELECT にかける

```java snippet=paging-select
```

`selectListWithRowCount` が**総件数の COUNT も投げます。**
COUNT 文は FROM / WHERE / GROUP BY / HAVING だけを写すので、
SELECT 句や ORDER BY、LIMIT には影響されません。

> [!TRAP]
> **総件数を数えるのは `selectListWithRowCount` だけ**です。
> ふつうの `selectList(builder)` では `paging.totalCount()` が 0 のままで、
> **ページャが「1 / 1 ページ」になります。**

> [!WARN]
> 生 SQL 版（`selectListWithRowCount(sql, params...)`）は `Paging` を触りません。
> 自分で `paging.set(response.list.size(), response.rowCount)` を呼んでください。

### 取れるもの

| メソッド | 中身 |
| --- | --- |
| `page()` | 現在ページ |
| `per()` | 1ページの件数 |
| `perAll()` | 全件指定だったか |
| `totalCount()` | 総件数 |
| `maxPage()` | 総ページ数（**最低 1**） |
| `start()` | このページの先頭が何件目か（**1 始まり**） |
| `count()` | このページで実際に取れた件数 |

> [!NOTE]
> **「次がある / 前がある」のメソッドはありません。**
> `page() > 1` と `page() < maxPage()` で判定してください。

### レスポンスに載る

`context.request().paging()` を**呼んだ時点で**レスポンスに載ります。自分で詰め直す必要はありません。

```json
{
  "rows": [ ... ],
  "paging": { "page": 2, "per": 10, "perAll": false,
              "maxPage": 3, "totalCount": 25, "start": 11, "count": 10 }
}
```

テンプレートからも同じものが見えます（`${paging.page()}`）。

### 気をつけること

> [!TRAP]
> **`per` に上限がありません。**`?per=100000` を送られると
> `LIMIT 100000` がそのまま出ます。外に公開する一覧では、
> `paging(...)` の前後で自分で上限を決めてください。

範囲外のページ（`?page=999`）はエラーにならず、**0 件が返ります。**
