# jimble の JSON とほかのライブラリの比較

jimble の JSON（`Dson`）を、fastjson2・wast・Apache Fory JSON（基準）と Jackson（参考）と同じ入力で比べます。

```bash
./benchmarks/json-compare/run.sh
```

結果は `results/summary.md`（jimble を 1 とした倍率の表）と `results/summary.csv`、測った環境は `results/environment.md` に出ます。
ライブラリごと・回ごとの生の結果は `results/raw/` に残ります（標準出力は `results/logs/`。こちらは git に入れません）。

jimble 本体のビルドには入っていません（このディレクトリは独自の `settings.gradle.kts` を持つ別のビルドです）。
jimble の依存も増えません。

## 比べるもの

| | 版 | 呼び方 |
|---|---|---|
| jimble | 2.5.12（Maven Central に公開したもの） | `Dson.decodes` / `Dson.encodes`。木は `Data` と `List` |
| fastjson2 | 2.0.66 | `JSON.parse` / `parseObject` / `parseArray` / `toJSONString` / `toJSONBytes` |
| wast | 0.0.29.1 | `JSON.parse` / `parseObject` / `parseArray` / `toJsonString` / `toJsonBytes` |
| Apache Fory JSON | 1.7.7 | `ForyJson.builder().build()` の `fromJson` / `toJson` / `toJsonBytes` |
| Jackson（参考） | 3.2.4（`tools.jackson.core:jackson-databind`） | `JsonMapper.builder().build()` の `readValue` / `writeValueAsString` / `writeValueAsBytes` |

版は `build.gradle.kts` で固定しています。変えたらこの表も直してください。

どのライブラリも**いちばん普通の呼び方**で呼びます。速くなるオプションや、型を先に登録する手間は入れていません（利用者が最初に書く形で比べるため）。

## 入力と項目

入力は jimble-util のベンチマーク（`jimble-util/src/test/java/io/jimble/util/bench/JsonSamples.java`）と同じです（`src/main/java/bench/Samples.java` がその写しで、大きい配列だけこちらにあります）。

| 入力 | 中身 |
|---|---|
| 小 | 10 キーのオブジェクト 1 つ（数値・文字列・真偽・配列・小数。約 230 文字） |
| 中 | 小と同じ形を 100 件並べた配列（約 23KB） |
| 大 | 同じく 4,800 件（約 1.1MB） |
| 数値 | 数値だけの 10 キー × 1,000 件（整数・負数・小数・指数表記が混ざる） |
| 日本語とエスケープ | 日本語と `\n` `\"` `\\` `\t` と、バックスラッシュ u の文字参照の多い 10 キー × 100 件 |

| 項目 | 中身 |
|---|---|
| 読む 文字列 → 木 | 小・中・大・数値・日本語とエスケープ |
| 読む byte[] → 木 | 中・大・日本語とエスケープ（UTF-8 の byte[] から。jimble は InputStream で読む） |
| 書く 木 → 文字列 | 小・中・大・数値・日本語とエスケープ |
| 書く 木 → byte[] | 中・大・日本語とエスケープ（jimble は OutputStream に書いて byte[] にする） |
| 読む 文字列 → Bean / record | 小（1 件）、中・大（List で） |
| 書く Bean / record → 文字列 | 小（1 件）、中・大（List で）、大の List&lt;Bean&gt; を byte[] へ |

**書く側の入力はどのライブラリも同じもの**（Jackson で読んだ `LinkedHashMap` / `ArrayList`、Bean、record）です。
jimble の `Data` を書く費用は jimble-util のベンチマーク（`./gradlew :jimble-util:bench`）で見ています。

測る前に 1 度だけ、**結果が入力と同じ中身か**を確かめます（結果を Jackson の木にして比べる。数値は値で比べ、Integer と Long、Double と BigDecimal の違いは問わない）。
違ったものは表に ※ が付き、`summary.md` の最後に一覧が出ます。

## 測り方

- **1 回の起動で 1 つのライブラリだけ**測ります。同じ JVM で続けて測ると、前のライブラリの JIT や GC の状態があとのライブラリに乗るためです
- 項目ごとに 1 秒空回しし、そのときの回数から 1 ラウンドの回数（約 200ms ぶん）を決めて、5 ラウンドの**中央値**を 1 回あたりの時間にします
- 割り当ては `ThreadMXBean#getThreadAllocatedBytes` の実測（1 回あたりの byte 数。呼んだスレッドのぶんだけ）です
- これを 3 回くり返し（回ごとに起動し直し、順番をずらす）、**回の中央値**を使います
- どのライブラリにも同じ JVM の引数（`-Xms2g -Xmx2g`・`--add-opens=java.base/java.lang.invoke=ALL-UNNAMED`）を渡します

全部で 15〜20 分ほどかかります。

## 作り直しの前と後を比べる

既定では、spring-compare と同じく **Maven Central に公開した jimble** を使います。
作業中のソース（このリポジトリの `jimble-util`）で測るときは `JIMBLE=local` を付けます。

```bash
JIMBLE=local ./benchmarks/json-compare/run.sh
```

中では `-Pjimble.local=true` を渡していて、`settings.gradle.kts` がリポジトリの根を `includeBuild` します
（依存の `io.jimble:jimble-util` が、ソースから作ったものに置き換わる）。
`results/` は上書きされるので、前の結果を残したいときは先に別の場所へ写してください。

## 読むときの注意

- **倍率は「そのライブラリ ÷ jimble」**です。時間も割り当ても、1 より小さいほどそのライブラリのほうが速い・少ない、ということです
- ノート PC は温度で速さが変わります。回ごとに順番を入れ替えて中央値を取ることで、特定のライブラリに寄らないようにしています。回ごとのばらつきは `summary.md` の最後に出ます
- 木の形はライブラリごとに違います（jimble は `Data`、fastjson2 は `JSONObject`、Fory は `JsonObject`、wast と Jackson は `LinkedHashMap`）。小数を `Double` で持つか `BigDecimal` で持つかも違います（fastjson2 は `BigDecimal`）
- ここで測っているのは 1 スレッドでの速さです。たくさんのスレッドから同時に呼んだときの差は見ていません

## 2026-10-24 に測ったときに分かったこと

`results/` に入っている結果（Apple M5 の Mac 1 台、jimble 2.5.12、3 回の中央値）で見えたことです。Dson を作り直す前の姿です。

- **木を読む**のは、fastjson2・wast が jimble の約 5 倍（0.15〜0.28）、Fory が約 4 倍の速さでした。割り当ては jimble の 4 分の 1 前後です
- **Bean・record を読む**のがいちばん差が大きく、fastjson2・wast・Fory は jimble の 10〜50 倍の速さでした（record は 0.02〜0.04）。jimble は木を作ってから移しているので、割り当ても 7〜10 倍あります
- **書く**のは差が小さく、木を書くのはおおむね 0.5〜0.8、Bean・record を書くのは 0.2〜0.5 でした
- Jackson（参考）は、読むのは jimble の 1.2〜8 倍の速さ、木を書くのはほぼ同じでした
- 回ごとのばらつきは中央値 22%（大きいものは 2 倍）ありました。0.8 と 1.0 のような差は、ばらつきの中に入ります

## 変えられるもの

```bash
ROUNDS=5 LIBS="jimble fastjson2" WARMUP_MS=2000 ./benchmarks/json-compare/run.sh
```

| 環境変数 | 既定 | 意味 |
|---|---|---|
| `ROUNDS` | `3` | くり返す回数 |
| `LIBS` | `jimble fastjson2 wast fory jackson` | 比べるライブラリ（倍率の基準なので `jimble` は外さない） |
| `WARMUP_MS` | `1000` | 項目ごとの空回し（ミリ秒）。測る時間はこの 5 分の 1 × 5 ラウンド |
| `JVM_OPTS` | `-Xms2g -Xmx2g` | どのライブラリにも渡す JVM の引数 |
| `JIMBLE` | `release` | `release` なら Maven Central の公開版、`local` ならこのリポジトリのソース |
| `SKIP_BUILD` | （なし） | `1` ならビルドを飛ばす |

必要なもの：Java 25、python3。ライブラリは Maven Central から取ってきます。
