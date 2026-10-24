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
> **`getDataOptional` / `getStringListOptional` などの Optional 版は Data を書き換えます。**
> 名前どおり「無ければ空を作って **`put` してから**返す」ので、`data.getDataOptional("x").put(...)` と書いた値が残ります。
> そのかわり、読んだだけでキーが増えます。JSON にして返す直前やループの中で呼ぶと、出力が変わります。
> あった値は書き換えません。`getStringOptional` は空文字を返すだけで、書き込みません。

> [!TRAP]
> **「無い」と「0」が区別できません。**キーが無い・`null`・空文字のとき、`getString` は `null`、
> `getInt` は `0`、`getBoolean` は `false` を返します。
> 区別したいときは `getIntObject` など **Object 版**か `isNull(key)` を使ってください。
> 既定値を渡す形もあります（`getInt(key, 既定値)` / `getLong` / `getDouble` / `getBoolean` / `getString`）。

**あるのに読めない値は `DataConversionException` です。**`"abc"`、int に `"1.5"`、桁あふれ、真偽に `"yes"`（読めるのは `true` / `false` / `1` / `0` だけ）などです。
`getEnum` も一致しなければ例外で、無くてよいなら `getEnumOptional(key, 型)` を使います。
利用者の入力を `getInt` などで読むなら、**先にバリデーションを通してください。**通さずに読んで例外になると 500 です。

- 型を変えて読んでも（数の一覧を `getStringList` で読むなど）、**元の Data には書き戻しません**
- 書き換えられない写しが要るときは `readOnlyCopy()`（書き込むと `UnsupportedOperationException`）

> [!NOTE]
> 1.x は読めない値でも黙って `0` / `false` / `null` を返していました（[2.0 への移行](./migrate-2)）。

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

**壊れた JSON は `JsonParseException`（非検査）です。**`Data.fromJsonString` も `Dson.decodes` も、Reader や InputStream から読んでも同じです。
例外には、どこがおかしいか（`3 行 15 文字目`）が載ります。空文字と `"null"` は `null` を返します（例外にしません）。

**意味がはっきりしているゆるさは受け付け、データを黙って壊すものは断ります**（2.6.0 から。読み手は `jimble-json`）。

| | 書き方の例 |
| --- | --- |
| 受け付ける | クォートの無いキーと値（`{name: jimble}`）、シングルクォート、末尾のカンマ、コメント（`//` と `/* */`）、`NaN` / `Infinity`、`+1` / `01` / `.5`、文字列の中の生の改行 |
| 断る | 区切りの抜け（`{"a":1 "b":2}` / `[1,,2]`）、値の無いキー（`{"a":}`）、壊れたエスケープ（`\q` / `\u12`）、閉じていない括弧、後ろの余分なもの、一番外がオブジェクトでも配列でもないもの |

> [!TRAP]
> **2.5 までは、区切りの抜けや壊れたエスケープを黙って通していました。**
> `{"a":1 "b":2}` は両方読めてしまい、`"\q..."` は後ろの4文字を飲み込んで文字列の終わりがずれていました。
> 2.6.0 からは例外になります。

**上限があります。**外から来た JSON を読むので、超えたら例外にします。

| | 上限 |
| --- | --- |
| 入れ子の深さ | 512 段 |
| 1つの数値の長さ | 1000 文字 |
| 1つの文字列の長さ | 2000 万文字 |

> [!NOTE]
> 文字列の `"true"` / `"false"` は、型を決めずに読む（`Data` / `Map` / `List`）と Boolean になります（2.5 までと同じ）。
> 文字列で真偽を返す API があるためです。`Data` から `getString` で取れば `"true"` に戻ります。
>
> インスタンスの `new Dson().decode(...)` は例外を投げず、これまでどおり `isError()` / `getErrorException()` で見ます。

### 型に読む

Bean・record・それらの List は、**木（Data）を作らずに読みながら入れます**（2.7.0 から）。

```java
Order order = Dson.decodes(json, Order.class);                     // これまでどおり
List<Item> items = Dson.decodes(json, List.class, Item.class);

Order order = Json.read(json, Order.class);                        // jimble-json
List<Item> items = Json.read(json, new TypeRef<List<Item>>() {});  // 型の引数を持つ型
```

- record は正準のコンストラクタで作ります。JSON に無いコンポーネントは `null`（プリミティブは `0` / `false`）です
- Bean は引数の無いコンストラクタで作り、**setter があれば setter で、無ければフィールドに入れます**（親クラスの private フィールドにも入れます）
- **知らないキーは読み飛ばします**（例外にしません）
- 入れ子・`List<Item>`・`Map<String, Item>`・`Optional`・配列・型変数（`TypeRef<Box<Item>>`）も、型どおりに読みます

値は項目の型にします。**値が変わってしまうものは断ります**。

| | 例 |
| --- | --- |
| 受け付ける | `"12"` → int、全角の `"１２"`、`"1,234"`、`1.0` → int、`"yes"` / `"on"` / `1` → true、enum の名前（大文字小文字は問わない）、`"2026/10/24 10:00:00"`・ISO 8601（時差つきも）・epoch ミリ秒 → 日時、空文字 → `null`（プリミティブは `0`） |
| 断る | `1.5` → int、桁あふれ、`"abc"` → int、`"maybe"` → boolean、無い enum の名前、`2024-02-30`（ありえない日付）、後ろに余分のある日付 |

断るときは `JsonParseException`（`Json.read` は `JsonException`）で、**どの項目か（`$.items[1].qty`）と行・文字の位置**が載ります。

> [!TRAP]
> **2.6 までは、読めない値を黙って `0` や `null` にしていました**（`"abc"` → int が `0`、`1.5` → int が `1`）。
> setter しかない項目には値が入らず、親クラスの private フィールドにも入りませんでした。

`user_name` を `userName` の項目に入れたいときは、`JsonReadOptions.builder().snakeCaseKeys(true)` を渡します
（`Dson` では `Configration#setDefaultFieldDelimiterChar()`）。

### 書き出し

日時などの型は、次の形で書きます（2.7.0 から。それまでは `Date` と `LocalDateTime` 以外を書くと、JSON が `{}` になったり途中で切れたりしていました）。

| 型 | 形 |
| --- | --- |
| `Date`・`Timestamp`・`LocalDateTime` | `yyyy/MM/dd HH:mm:ss`（秒より細かいところは書かない） |
| `Instant`・`OffsetDateTime`・`ZonedDateTime`・`Calendar` | サーバーの時間帯にそろえて `yyyy/MM/dd HH:mm:ss` |
| `LocalDate`・`java.sql.Date` | `yyyy/MM/dd` |
| `LocalTime`・`java.sql.Time` | `HH:mm:ss` |
| `Duration`・`Period` | ISO 8601（`PT1H30M` / `P1Y2M`） |
| `UUID`・`URI`・`File` など JDK のクラス | 文字列（`toString`） |
| `Optional` | 中身（空なら `null`） |
| enum | 名前 |
| record | コンポーネントを宣言した順に、オブジェクト |
| Bean | フィールドを宣言した順（親クラスから）に、オブジェクト |

- **Bean のキーは宣言した順です**（2.7.0 から。それまでは JVM を起動するたびに順番が変わることがありました）
- **循環参照は、2回目を `{}` / `[]` と書いて続けます**（2.6 までと同じ）
- **入れ子は 512 段まで書きます。**超えたら書かず、`Dson.encodes` は空文字を返してエラーをログに出します（2.6 までは 50 段を超えたところを、黙って `{}` / `[]` にしていました）

時差を残したいとき・循環で例外にしたいとき・型ごとに書き方を変えたいときは、`jimble-json` の `Json.write(value, options)` を使います。

```java
JsonWriteOptions options = JsonWriteOptions.builder()
	.keepOffset(true)                 // Date / Instant / OffsetDateTime などを 2026-10-24T19:00:00+09:00 の形で
	.cycle(JsonCycle.THROW)           // 循環参照は例外（どこで循環したかが載る。Json.write の既定）
	.typeWriter(Money.class, (value, out) -> out.string(((Money) value).format()))
	.build();

String json = Json.write(value, options);
```

## 型変換

`jimble-converter` の `Converter.convert(値, 変換先.class)` が入口です（2.7.0 から。jimble のほかのモジュールに依存しません）。
Bean ↔ Map ↔ List ↔ record ↔ プリミティブ・日時のあいだを変換します。
`Data` からは `data.convertClass(MyBean.class)` や `getIntList` などの getter で、中身は同じものです。
変換できなければ**非検査例外**です（`throws` も `catch` も要りません）。

```java
Order order = Converter.convert(map, Order.class);
List<Item> items = Converter.convert(rows, new TypeRef<List<Item>>() {});

ConverterOptions options = ConverterOptions.builder()
	.rejectUnknownKeys(true)          // 知らないキーで断る
	.zone(ZoneId.of("Asia/Tokyo"))    // 時差の無い日時を補う時間帯
	.typeConverter(Money.class, value -> Money.parse(value.toString()))  // 型ごとの変換を足す
	.build();
Order strict = Converter.convert(map, Order.class, options);

int qty = Converter.convert("abc", int.class, ConverterOptions.LENIENT);  // 読めない値を 0 / null に（CSV など）
```

決まりは [JSON を型に読む](#型に読む) ときと同じです。

- **値が変わってしまうものは例外**です（`ConversionException`。どの項目か `$.items[1].qty` が載ります）。`1.5` → int、桁あふれ、`"abc"` → int、無い enum の名前、ありえない日付、後ろに余分のある日付
- 意味がはっきりしているゆるさは受け付けます。前後の空白、全角の数字、`"1,234"`、`1.0` → int、`true` → 1、`"yes"` / `"on"` → true（`"no"` / `"off"` は false）、`"2024年01月15日"` → 日付
- Bean には **setter があれば setter で**入れます（親クラスの private フィールドにも入れます。`static` / `transient` には入れません）。record は正準のコンストラクタで作ります
- **知らないキーは飛ばします**
- 日時は `Date`・`LocalDate`・`LocalDateTime`・`Instant`・`OffsetDateTime` などのあいだで変換します。`Z` や `+09:00` の時差は読みます（時差の無いものはサーバーの時間帯）
- 型が合っている値は、写さずにそのまま返します
- 循環参照は例外です（`cycleToNull(true)` で、そこを `null` に）
- 文字から `Class` / `InetAddress` / `Pattern` / `File` / `Path` は作りません（外から来た文字で、クラスの読み込み・DNS・ReDoS・ファイルへの到達を起こせるため）。要るなら `typeConverter` で足します

> [!TRAP]
> **2.6 までは、読めない値を黙って `null` / `0` にしていました**（`"abc"` → Integer が `null`、`1.9` → Integer が `1`、`3000000000` → Integer が桁あふれ、`"no"` → Boolean が true、`"...Z"` の時差を無視）。
> setter しかない項目には値が入りませんでした。

### `Convertor` から移る

`io.jimble.util.convertor` の `Convertor` / `IConvertor` / `ConvertorConfigKeys` / `PropertyUtil` は **2.7.0 で deprecated** です（動きは変わりません。中身は `Converter` です）。

| これまで | これから |
| --- | --- |
| `Convertor.convert(null, map, Order.class)` | `Converter.convert(map, Order.class)` |
| `Convertor.convert(null, rows, List.class, Item.class)` | `Converter.convert(rows, new TypeRef<List<Item>>() {})` |
| `Convertor.convert(null, data, Order.class)` | `Converter.convert(data, Order.class)`（`Data` は Map なのでそのまま渡せる。`data.convertClass` は失敗を `null` にするので、例外が要るならこちら） |
| `Convertor.addConvertor(Money.class, 変換器)` | `ConverterOptions.builder().typeConverter(Money.class, 変換のしかた)` |
| `Configration#setDefaultFieldDelimiterChar()`（`user_name` → `userName`） | `ConverterOptions.builder().snakeCaseKeys(true)` |

`Convertor` には、`Converter` に無い、これまでのままのゆるさが残っています。`Converter` に移すときは、次を確かめてください。

- **型が合っている List / Map も写しを返す**（`Converter` はそのまま返す）
- **JSON の文字を読む**（`"[1,2]"` → `List<Integer>`、`"{...}"` → `Data`。`Converter` は JSON を読まないので、`Dson.decodes` で読んでから渡す）
- **Map → 一覧は値の一覧、一覧・Map → 文字は先頭の値**（`Converter` は例外）
- **循環参照はそこを `null` にする**（`Converter` の既定は例外）

`Convertor.addConvertor` で足した変換器・`Configration` に渡した日付の書式（`CONVERT_STRING_TO_DATE` / `CONVERT_DATE_TO_STRING`）・XML の `Document` は、`Convertor` / `Data` からは、これまでの変換器で変換します。

> [!TRAP]
> **`Configration` は使い回さないでください。**これまでの変換器を通るとき、変換の途中で階層カウンタと
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
- **例外を投げません。**`isError` を見てください

> [!WARN]
> **タイムアウトの既定は 30 秒**で、接続と応答の**両方に同じ値**が入ります。個別には指定できません。

> [!TRAP]
> **`setIgnoreSslError(true)` は、その通信の証明書もホスト名も確かめません。**本番の通信には使わないでください。
> 2.2.2 までは**JVM 全体のホスト名の確認を止める**システムプロパティを立てていたので、同じプロセスのほかの通信
> （OIDC のトークンや JWKS の取得を含む）もホスト名を確かめなくなりました。2.2.3 からは、その1本だけに効きます。

- **リダイレクトは、https から http へは追いません**（2.2.3 から。それまでは追っていました）。追う先のホストは確かめないので、
  URL を許可リストで確かめてから取りにいくなら `setEnableRedirect(false)` にしてください
- **メモリに読む本文は、解いたあとの大きさで 50MiB まで**です（`setMaxResponseSize(バイト)`）。超えたら本文を読みません

## CSV

```java
try (CsvReader reader = new CsvReader(new File("in.csv"))) {
	while (reader.next()) {
		String name = reader.getString("name");
	}
}
```

**1行ずつ読みます**（全部メモリに載せません）。書くほうは `CsvWriter#writeLine(Object...)`。
`CsvReader` / `CsvWriter` は**検査例外を投げません**。読み書きの失敗は `UncheckedIOException` です。

> [!WARN]
> **文字コードを省略すると、判定に失敗したときは Shift_JIS になります。**
> UTF-8 と分かっているなら `new CsvReader(file, "UTF-8")` と書いてください。

**BOM**（Excel が書き出す UTF-8 の CSV に付くもの）は、文字コードが UTF 系なら読むときに落とします。
BOM があれば、その BOM が示す文字コードで読みます（UTF-8 と指定しても、UTF-16LE の BOM なら UTF-16LE）。
`Reader` を渡したときは、先頭の BOM を1文字だけ落とします。

書くときに BOM を付けるなら `setWithBom(true)` です（Excel に UTF-8 の CSV を開かせるとき）。
**文字コードが UTF 系のときだけ効き**、Shift_JIS などでは何もしません。

**`= + - @`・タブ・CR（全角の `＝＋－＠` も）で始まる値は、先頭に `'` を付けて書きます**（2.2.3 から）。
Excel はこれらで始まるセルを数式として動かすので、利用者が入れた `=HYPERLINK(...)` などが、CSV を開いた人の手元で動きました。
数値として読める値（`-5` など）には付けません。付けたくなければ `setEscapeFormula(false)` です。

```java
try (CsvWriter writer = new CsvWriter(new File("out.csv"), "UTF-8").setWithBom(true)) {
	writer.writeLine("名前", "数");
}
```

## XML

`XmlParser.parse(file)` で `XmlData` の木にします（**全部メモリに載ります**）。
**DOCTYPE を含む XML は解析しません**（null を返します。外部の実体でファイルを読ませないため）。
組み立ては `XmlBuilder.build(xmlData)`（検査例外は投げません）。

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
| ファイル | `FileUtil` / `IOUtil`（`copy` / `readLines` の失敗は `UncheckedIOException`）/ `FileCharDetecter`（文字コード判定） |
| スレッド | `VirtualThreadManager` / `ThreadManager`（[実行モデル](./execution)） |
| 計測 | `StopWatch` |

> [!NOTE]
> `Patterns` の TLD 一覧は**書き切りの文字列**です。新しい TLD は通りません。
