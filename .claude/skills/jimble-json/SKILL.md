---
name: jimble-json
description: jimble で JSON を読み書きする・型を変換するときに使う。壊れた JSON は JsonParseException（読める書き方と上限）、JSON の小数は Double、書くときの日付と時差（keepOffset）と Bean の順、Bean / record / List への読み込み（Dson.decodes・Json.read・TypeRef）、Converter.convert と ConverterOptions（LENIENT・typeConverter）、deprecated の Convertor / IConvertor / PropertyUtil、Data.put が Data を返さないことを含む。
---

# jimble の JSON と型変換

全体の決まりと、ほかの skill への案内は `jimble`。DB の JSON の列は `jimble-db-data`、リクエストの JSON は `jimble-web-input`。

## JSON を読む

- **壊れた JSON は `JsonParseException`**（2.6.0〜。区切りの抜け `{"a":1 "b":2}`・値の無いキー・壊れたエスケープも。例外に行と文字の位置が載る）。
  クォートの無いキー・シングルクォート・末尾のカンマ・コメントは読める。数値は 1000 文字・入れ子は 512 段まで
- **JSON の小数は `Double` で返る**（2.5.6 まで `Float`）。`(Float)` でキャストせず `getDouble` / `getBigDecimal` で読む
- **JSON を Bean / record に読むと、読めない値は `JsonParseException`**（2.7.0〜。`"abc"` → int・`1.5` → int・桁あふれ。場所 `$.items[1].qty` が載る）。
  setter があれば setter に入る。知らないキーは飛ばす
- `List<Item>` は `Dson.decodes(json, List.class, Item.class)` か `Json.read(json, new TypeRef<List<Item>>() {})`
- 一番外が配列の JSON を `Dson.decodes(文字列)` に渡すと添字がキーの Data になる。配列は `Dson.decodes(文字列, List.class)`（`jimble-db-data`）

## JSON に書く

**`LocalDate` は `yyyy/MM/dd`、`Instant` などはサーバーの時間帯で `yyyy/MM/dd HH:mm:ss`、Bean は宣言した順**（2.7.0〜）。
時差を残すなら `Json.write(value, JsonWriteOptions.builder().keepOffset(true).build())`（`jimble-json` モジュール）。

## 型を変換する

- **新しく書く型変換は `Converter.convert(map, Order.class)` / `Converter.convert(rows, new TypeRef<List<Item>>() {})`**（`jimble-converter`）
- **`Convertor` / `IConvertor` / `PropertyUtil` は 2.7.0 で deprecated。**`Convertor.addConvertor` の代わりは `ConverterOptions.builder().typeConverter(型, v -> ...)`
- `Converter` は JSON の文字を読まない・型が合えば写さない・循環参照は例外（`Convertor` と違う）
- **`Convertor.convert` / `convertClass` / `getIntList` / `getDate` も、値が変わる変換は例外**（2.7.0〜。`"abc"` → int・桁あふれ・ありえない日付・`2` → boolean。
  `"no"` は false、`"...Z"` の時差を読む、文字から `Class` / `Pattern` / `InetAddress` / `File` は作らない）。
  読めない値を 0 / null にしたいなら `Converter.convert(v, int.class, ConverterOptions.LENIENT)`

## Data

- **`Data.put` は `Data` を返さない**（`Map.put` なので前の値）。続けて書くなら `putData("a", 1).putData("b", 2)`
- getter（`getInt` など）は**あるのに読めない値で `DataConversionException`**、無いキーは 0 / false / null（`jimble-db-data`）。
  利用者の入力は先に検査を通す（`jimble-web-input`）

詳しくは <https://jimble.io/ja/util.md>。
