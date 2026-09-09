---
title: ユーティリティ
summary: Data・JSON・HTTP クライアント・CSV・暗号。どこに何があるか
section: そのほか
order: 2
---

# ユーティリティ

`jimble-util` には移送元から持ってきた道具が **約 28,000 行**入っています。
ここは**地図**です。網羅ではなく「よく使うもの」と「引っかかるところ」を並べます。

> [!NOTE]
> **公開 API としての整理は Phase 2 です。**
> パッケージの切り方も名前も、移送元のままのところが残っています。

## Data

`io.jimble.util.data.Data` は **`LinkedHashMap<String, Object>` の派生**です。
リクエストも SELECT の結果も JSON もこれで扱います（[リクエストとレスポンス](./request-response)）。

```java snippet=data-nest
```

DB の値は**列（`Column`）で読み書き**します。列版は**テーブル名のネストを辿ります。**

| | |
| --- | --- |
| `putData(Column, 値)` | **必ずネストを作る** |
| `putDataTakeCare(Column, 値)` | 既存の形に合わせる（ネストが無ければ平ら） |
| `flattenTable(Table)` / `extractTableData(Table)` | 平らにする / 取り出す。**無ければ `null`** |

> [!TRAP]
> **`getStringOptional` などの Optional 版は Data を書き換えます。**
> 無ければ空文字を **`put` してから**返すので、読んだだけでキーが増えます。
> JSON にして返す直前やループの中で呼ぶと、出力が変わります。

> [!TRAP]
> **「無い」と「0」が区別できません。**`getString` は `null`、
> `getInt` は `0`、`getBoolean` は `false` を返します。
> 区別したいときは `getIntObject` など **Object 版**か `isNull(key)` を使ってください。

> [!NOTE]
> **`toString()` は要約です**（キーと型だけ）。
> ログに1行出しただけで中身が全部流れる／`Async` が読み込まれる、を防ぐためです。
> JSON が要るときは `getJsonString()` を呼んでください。

## JSON

自前実装です（Jackson も Gson も使っていません）。

```java snippet=data-json
```

| やること | 書き方 |
| --- | --- |
| Data → 文字列 | `data.getJsonString()` / `getJsonString(true)`（整形） |
| 文字列 → Data | `Data.fromJsonString(json)` |
| 任意のオブジェクト | `Dson.encodes(obj)` / `Dson.decodes(json, Xxx.class)` |
| ストリームに直接 | `data.outputJsonString(outputStream)` |
| 組み立てずに逐次書く | `JsonHashWriter` / `JsonArrayWriter` |

> [!WARN]
> **`Dson.encodes` / `decodes`（static 版）は失敗しても `null` を返すだけ**です。
> 理由が要るなら `new Dson()` を作り、`decode(...)` のあとに `isError()` /
> `getErrorException()` を見てください。

## 型変換

`Convertor.convert(conf, src, 変換先.class)` が入口です。
Bean ↔ `Data` ↔ Map ↔ List ↔ プリミティブが同じ1本を通ります。
`data.convert(new MyBean())` も中身は同じものです。

> [!TRAP]
> **`Configration` は使い回さないでください。**変換の途中で階層カウンタと
> 循環参照の記録が書き換わります。**1回の変換に1つ**作ります。

## HTTP クライアント

JDK の `HttpClient` の薄いラッパで、メソッドごとにクラスがあります。

```java
HttpGetExecutor res = new HttpGetExecutor()
	.setUrl("https://example.com/api")
	.addHeader("Accept", "application/json")
	.setTimeout(5000)
	.execute();

if (res.isError) {
	Log.error(res.errorException, "取得に失敗しました");
	return;
}

Data json = res.getContentJson();
```

- POST は `HttpPostExecutor`。`addBodyForm(name, value)` / `setBodyJson(data)`
- **ファイルを1つでも足すと multipart になります**（`addBodyForm(name, file, contentType)`）
- プロキシは `setProxy(new HttpProxy(host, port, id, pass))`
- **例外を投げません。**`isError` を見てください（DB と同じ流儀）

> [!WARN]
> **タイムアウトの既定は 30 秒**で、接続と応答の**両方に同じ値**が入ります。個別には指定できません。

> [!TRAP]
> **`setIgnoreSslError(true)` は JVM 全体に効きます。**
> 証明書の検証を止めるシステムプロパティを立てるので、
> **同じプロセスの他の通信も検証しなくなり、元に戻りません。**

## CSV

```java
try (CsvReader reader = new CsvReader(new File("in.csv"))) {
	while (reader.next()) {
		String name = reader.getString("name");
	}
}
```

**1行ずつ読みます**（全部メモリに載せません）。書くほうは `CsvWriter#writeLine(Object...)`。

> [!WARN]
> **文字コードを省略すると、判定に失敗したときは Shift_JIS になります。**
> UTF-8 と分かっているなら `new CsvReader(file, "UTF-8")` と書いてください。

## XML

`XmlParser.parse(file)` で `XmlData` の木にします（**全部メモリに載ります**）。
組み立ては `XmlBuilder.build(xmlData)`。

## ハッシュと暗号

| やること | 使うもの |
| --- | --- |
| パスワード | `PasswordUtil.createHash` / `check`（[セッションと安全側の既定](./session-security)） |
| 署名（改ざん検知） | `Signer.sign` / `unsign`（HMAC-SHA256） |
| 暗号化 | **`Aead.encrypt` / `decrypt`**（AES-256-GCM） |
| 短い ID | `Hashids` |
| ハッシュ値 | `Hash.md5` / `sha256` / `sipHash` / `xxHash64` |

> [!WARN]
> **`CipherUtil`（AES/CBC）は IV が設定で固定**です。同じ平文が必ず同じ暗号文になり、
> 改ざんも検知できません。移送元との互換のために残しています。
> **新しく書くところは `Aead` を使ってください。**

> [!TRAP]
> **`Hashids` の既定インスタンスは salt が空です。**並びが推測できるので、
> **見せたくない ID の隠蔽には使えません。**必要なら salt を指定して自分で作ってください。

## DB に置く key-value

```java
DBValue.set(db, "last_imported_at", "2026-09-07");
String value = DBValue.getString(db, "last_imported_at", "");
```

> [!TRAP]
> **`get` は値が無いと既定値を書き込みます**（読むだけのつもりで INSERT が飛びます）。
> 値は **250 文字まで**、消す API はありません。
> キャッシュはプロセスごとなので、**他の台の `set` は伝わりません。**

## そのほか

| 分野 | クラス |
| --- | --- |
| 文字列 | `StringUtil`（置換・全半角・Base62・パスワード生成）/ `IcuUtil`（かな・全半角） |
| 日時 | `DateUtil`（`java.util.Date` ベース。`getFrom` / `getTo` は「その日の 00:00:00 / 23:59:59」） |
| URL | `UrlUtil` / `UrlBuilder`（ドメイン抽出・punycode・エンコード） |
| 数値・パース | `Parse.parseInt` など（**失敗しても例外を投げません**） |
| 正規表現 | `Patterns`（メール・URL・ドメイン・電話） |
| ファイル | `FileUtil` / `IOUtil` / `FileCharDetecter`（文字コード判定） |
| スレッド | `VirtualThreadManager` / `ThreadManager`（[実行モデル](./execution)） |
| 計測 | `StopWatch` |

> [!NOTE]
> `Patterns` の TLD 一覧は**書き切りの文字列**です。新しい TLD は通りません。
