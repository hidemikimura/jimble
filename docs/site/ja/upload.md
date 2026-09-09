---
title: ファイルアップロード
summary: multipart の受け取り、一時ファイル、上限、保存とダウンロード
section: 基本
order: 3
---

# ファイルアップロード

`multipart/form-data` で来たファイルは `bodyFile()` から取ります。

```java
Data files = context.request().bodyFile();
```

> [!TRAP]
> **ファイルは `bodyAll()` にも `bodyForm()` にも載りません。**`bodyFile()` だけです。
> 同じ本文に入っているテキスト項目のほうは、いつもどおり `bodyAll()` から取れます。

## 受け取る

```java snippet=upload-read
```

取り出すのは `UploadFile` です。**メソッドを持たない入れ物**で、中身はこれだけです。

| フィールド | 中身 |
| --- | --- |
| `name` | フォームの `name` 属性 |
| `fileName` | **クライアントが名乗った**ファイル名（無ければ `""`） |
| `contentType` | **クライアントが名乗った** Content-Type（無ければ `""`） |
| `fileSize` | 実際に書き出したバイト数 |
| `file` | 一時ファイル（`java.io.File`） |

> [!WARN]
> **ファイルが1件でも `List` にくるまれます。**
> `bodyFile()` の値を `UploadFile` に直接キャストすると落ちます。
> 同じ `name` で複数送れる以上、常にリストとして扱うのが正しい形です。

## 一時ファイル

| | |
| --- | --- |
| 置き場所 | `upload.temp_dir`（既定は `java.io.tmpdir`） |
| 名前 | `jimble-upload-<連番>.tmp`。**元のファイル名は使いません** |
| 消えるとき | **リクエストが終わるとき**（コンテキストのクローズ） |

> [!TRAP]
> **リクエストが終わったあとに `uploadFile.file` は触れません。**
> 非同期に処理を投げたり、パスだけ DB に入れて後で読んだりすると、
> **もう無いファイル**を掴みます。残したいものはハンドラの中でコピーしてください。

一時ファイルには `deleteOnExit()` を付けていません。
プロセスが落ちれば残るので、そこは OS の一時ディレクトリの掃除に任せています。

## 上限

**設定を書かなくても上限は掛かります**（要件 NF-S-05）。

```conf
upload {
	max_file_size  = 10485760   # 1ファイル。既定 10MB
	max_total_size = 52428800   # 1リクエスト合計。既定 50MB
	max_files      = 20         # 件数
	temp_dir       = ""         # 空なら java.io.tmpdir
}
```

超えたときはその場で例外になり、**書きかけの一時ファイルも消します。**

| 条件 | 返り |
| --- | --- |
| 件数超過 | **413**「アップロードできるファイル数は N 件までです」 |
| 1ファイル／合計の超過 | **413**「アップロードのサイズが上限（N バイト）を超えています」 |
| multipart として読めない | **400**「multipart を読めませんでした」 |

サイズは**読みながら**見ています。全部受け取ってから測ると、
上限を超えた分までディスクに書いてしまうからです。

> [!WARN]
> **`server.max_request_size`（既定 10MB）のほうが先に効きます。**
> `upload.max_total_size` の既定は 50MB なので、**そのままでは 50MB は届きません。**
> 大きいものを許すなら、両方を上げてください。

## 保存する

**保存のヘルパはありません。**`uploadFile.file` を JDK の `Files` で写します。

```java snippet=upload-save
```

要点は3つです。

| | |
| --- | --- |
| **名前はこちらで付ける** | `UUID.randomUUID() + extension` |
| **拡張子は許可リストで見る** | 落ちたら 400 で断る |
| `Files.copy` | 元は消えるので、コピーでも移動でもよい |

> [!TRAP]
> **`uploadFile.fileName` をそのまま保存先に使わないでください。**
> jimble はこの名前を**一切検査しません**（クライアントが名乗ったままです）。
> `../../etc/passwd` のような名前が来ると、置き場所の外に書けてしまいます。

> [!WARN]
> **`contentType` も検査していません。**クライアントの自己申告です。
> `image/png` と名乗った実行ファイルが来ます。
> 拡張子の許可リストと、必要なら中身の検査を自分で書いてください。

## テキスト項目との混在

同じ本文に入っているので、**1回で解析して持っておきます**（本文は1回しか読めません）。
ファイル名の無いパートはテキスト項目として扱われ、`bodyAll()` に載ります。

> [!NOTE]
> テキスト項目は **UTF-8 固定**で読み、**全量をメモリに載せます**（サイズ上限はありません）。
> ここを守っているのは `server.max_request_size` だけです。

## 返す（ダウンロード）

```java
context.response().download(file);                  // 実ファイル名で添付
context.response().download(file, "報告書.xlsx");   // 名前を指定して添付
context.response().file(file);                      // インライン（種類は中身から判定）
context.response().file(file, "image/png");         // インライン（種類を指定）
context.response().stream(in, "text/csv");          // ストリーム
context.response().stream(in, "text/csv", length);  // 長さが分かっているストリーム
```

`Content-Disposition` は RFC 5987 の形（`filename*=UTF-8''...`）で付くので、
**日本語のファイル名がそのまま通ります。**

> [!NOTE]
> 互換用の `filename="..."`（ASCII 版）は併記していません。
> また、名前に**半角スペースが入ると `+` になります**（`%20` ではありません）。
> 気になる場合はスペースを `_` に置き換えてから渡してください。
