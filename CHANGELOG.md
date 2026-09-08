# 変更履歴

版の付け方は [Semantic Versioning](https://semver.org/lang/ja/) に従う。

---

## 未リリース

### 変わったこと（挙動）

| | |
|---|---|
| **`in()` / `not_in()` に空の一覧を渡すと例外になる**（F-D-07 / D-102） | いままでは `IN ()` という構文エラーの SQL を組み立てて DB に投げていた。**SQL を組み立てたところで `SqlBuildException`** にする。「空なら条件ごと外す」ことはしない（`in(空)` は「どれにも当たらない」、条件を外すと**全件**。取り違えると静かに全件消したり全件見せたりする）。空になりうるところは `if (ids.isEmpty())` で分けること |
| **ウィンドウ関数を `where` / `having` に書くと例外になる**（D-103） | `Dsl.rowNumber().over(...).eq(1)` は SQL の決まりで書けない。いままでは書けてしまい、**DB に投げるまで気づけなかった** |
| **バッチの登録が0件のまま `BatchRegistry.sync` を呼ぶと、`batch_master` の行が全部 `nothing` になる**（D-104） | いままでは0件のとき何もしなかったため、**最後の1つを消したときだけ行が `enable` のまま残って**いた。「1つも登録されていない = 全部消えた」に揃えた。**`sync` は登録を済ませてから呼ぶこと。**バッチを持たないアプリは呼ばないこと |
| **`codegen` が `sql_cache` / `sql_cache_tag` / `rate_limit` を生成しなくなった**（D-68） | jimble が作るテーブルなのに除外一覧から漏れていて、**アプリのテーブル定義クラスとして生成されていた**（`rate_limit` は `io.jimble.web.ratelimit.RateLimit` と単純名がぶつかる）。これらのテーブルがある環境では `codegen` の出力が変わるので、**生成物をコミットしているなら流し直すこと** |

### 直した

| | |
|---|---|
| **`jimbleRun` を止めて動かし直すと、次のビルドが起動しない**（D-101） | helidon が起動のときに JVM 全体の直列化フィルタを張る。`jimbleRun` はアプリを Gradle デーモンの中で動かす（D-77）ので、**フィルタがデーモンに残り**、次のビルドが `Couldn't populate class org.gradle.api.services.BuildServiceParameters$None > filter status: REJECTED` で落ちていた。`jimbleRun` のあいだだけ `helidon.serialFilter.missing.action = IGNORE` にする。**本番の挙動は変わらない** |
| **SPA を `/` に置くとトップページだけ 404** | `"/*"` はセグメントが0個の `/` に当たらないのに、`/` 自身を登録していなかった。`/any` は 200 で返るので気づきにくかった |
| **`in` の空一覧が JSON の `where` からだと素通りしていた** | `{"where": {"site": {"id|in": []}}}` が `IN (NULL)` になり、**例外もエラーも出ずに 0 件**（`not_in` なら本来の全件が 0 件）。`in(null)` も同じく落とすようにした |
| **エラーハンドラが送信してから落ちると、後続のエラーハンドラが走っていた** | 「送信済みなら以降は実行しない」の判定を `catch` で飛ばしていた |
| **ドキュメントの食い違い 3 件** | `execution.md`「送ったら止まる」で `after` も止まると書いていた（`after` と `onComplete` は `finally` にあるので必ず通る）／`config.md` の `migration.on_startup` の既定を `false` と書いていた（実際は `"auto"`）／`deploy.md` の起動ログ例が jar の外の conf を「クラスパスより優先」と書いていた（jimble はクラスパスしか見ない） |

### 足した

| | |
|---|---|
| **CI を入れた**（D-108） | GitHub Actions。`build`（DB なし）／`db`（MariaDB + Redis）／`pg`（PostgreSQL + Redis）の3ジョブ。`dbTest` / `pgTest` / `codegenCheck`（F-G-14）/ `migrate → codegen → compileJava` の連鎖（NF-T-07）/ サンプルアプリの疎通（NF-T-06）/ ドキュメントサイトの生成（NF-D-03）が毎回回る。**標準ランナーだけ**で、larger runner は使わない |
| **サンプルアプリが、まっさらな DB では動かなかった**（D-109） | `examples/blog` は MQ のテーブルをバッチの入口でしか作っていないのに、**キューに積むのは Web** だった。まっさらな DB で記事を登録すると「トランザクションのコミットに失敗しました」で 500。**いちどでもバッチを動かしたマシンでは動く**ので手元では気づけず、**CI を入れた初回に出た**。起動時に用意するものを `Bootstrap` 1か所にまとめ、3つの入口とテストがそれを呼ぶようにした。落とし穴のページにも足した |
| **依存の脆弱性を見る口を作った**（NF-S-07） | Gradle の依存グラフを GitHub に登録して Dependabot alerts に当てる。あわせて Dependabot で週1の更新 PR |
| **`UrlUtil.normalizeUrl`**（D-107） | **未エンコードの URL でも、エンコード済みの URL でも、同じ答えになる**（2回通しても変わらない）。ホストは punycode にする。移送してきた `fullUrlEncode` は<b>エンコード済みを二重にし、ホストをパーセントエンコードしていた</b>（DNS が引けない）。`urlToEncodeUrl` は<b>パスの空白を `+` にしていた</b>（`+` が空白なのはフォームの書式だけ）。どちらも呼ばれていないので消さず、javadoc から新しいほうへ案内している |
| **静的配信のルートを外から触れる**（D-70） | `AssetController` / `SpaController` / `MpaController` に `routes()`。`install` した子のルートに `AttributeKey`（認証の除外・流量制限）を付けられる |
| **`NOT IN` の条件を読めるようにした**（D-105） | `NotIn` に `operator()` / `conditionValue()`。`In` と対称になった。**`IN` とは別の演算子（`WhereTerm.NOT_IN`）**にしてある（`NOT IN (1,3)` は「それ以外の全部」なので、1 と 3 の行タグだけ消すと**古い値が返り続ける**）。SQL 結果キャッシュは従来どおりテーブルごと消す安全側のまま |
| **`codegen` が外したテーブルの名前を出す**（D-68） | `session` のように、jimble の管理テーブルとアプリの業務テーブルで名前がぶつかりうる。黙って外すと**自分のテーブルのクラスが生成されないことに気づけない** |

### 中の整理

| | |
|---|---|
| **移送してきたコードの警告を全部潰し、`-Werror` にした**（D-15。**完了**） | `jimble-util` は移送時点の警告を種別ごと（`unchecked` / `rawtypes` / `fallthrough` / `deprecation` / `dangling-doc-comments` / `this-escape` / `cast` / `overloads`）落としていて、**javadoc の doclint も切っていた**。**コンパイル警告 81 件と doclint 45 件を潰して、抑止をやめた。**以降は警告が出たらビルドが落ちる。消せないものは、消せない理由を書いた `@SuppressWarnings` をその場所に付けてある |
| **ヘルスチェックの要件を実態に合わせた**（NF-O-03 / D-106） | 要件は「標準で提供する」と書いてあったが、実装は無く、ドキュメントは「アプリが書いてください」だった。**グレースフルシャットダウンで「先にヘルスチェックだけ落とす」順番をアプリが決めるため**という理由を要件側に書いた |
| **jimble が作るテーブルの名前を1か所に**（D-68） | `io.jimble.db.FrameworkTables`。作る側も codegen の除外側も同じ定数を使う。`GeneratorConf.FRAMEWORK_TABLES` は廃止 |
| **「送信済みなら打ち切る」判定をエラー経路にも通した**（F-C-13） | `Dispatcher` から `isSent()` の直書きが無くなり、判定は `Stage` だけになった |
| 使われていない `Generator.isGenerationRequired` を削除 | スキーマが変わっても版は変わらないので、生成を飛ばす判定には使えない。鮮度は `codegenCheck` が生成物そのものを突き合わせて見る |
| 同名の `FileCharDetecter` が 2 パッケージにあったのを解消 | `io.jimble.util.io.FileCharDetecter` を残し、`io.jimble.util.charset.FileCharDetecter` を削除（後者はどこからも使われていなかった） |

---

## 0.2.0（2026-09-08）

**PostgreSQL に対応した版。**アプリのコードは1行も変えずに
`db.xxx.product = postgresql` だけで動く。

### 追加

| | |
|---|---|
| **PostgreSQL 対応**（F-D-30） | SQL ビルダーとフレームワークの内部テーブルが `db.xxx.product`（`mysql` / `mariadb` / `postgresql`）で切り替わる。**SQL ビルダーを使うアプリ側のコードは変更なし。**識別子の囲み・関数名・upsert・`INSERT IGNORE`・`INTERVAL`・JSON の取り出し・真偽値と JSON のバインド・採番値の取り出しを方言に寄せた。その製品に書けないものは<b>SQL を組み立てたところで `DialectException`</b> になる |
| **テーブル定義クラスの生成が PostgreSQL でも動く**（D-98） | `./gradlew codegen` が `pg_catalog` を読む。連番・既定値・コメント・一意キーを MySQL と同じ形に揃える |
| **SQL DSL の充実**（F-D-31） | 文字列（`lower` / `substring` / `replace` / `lpad` / `locate` …）、数値（`abs` / `mod` / `power` / `greatest` …）、日付（`year` … `weekOfYear` / `dateAdd` / `dateDiff` / `unixTimestamp` …）、条件と型変換（`coalesce` / `nullif` / `ifThenElse` / `cast` / `regexp`）、集約（`countDistinct` / `stddev` / `groupConcat`）、**ウィンドウ関数**（`rowNumber` / `rank` / `lag` / `over` ＋ `PARTITION BY` / `ORDER BY` / `ROWS BETWEEN`）。約 60 個。**両製品で同じ答えを返す** |
| **SQL 結果のキャッシュ**（F-D-28） | `selectCached` / `selectListCached` で結果をキャッシュし、**更新系 SQL が走ったら関係するキャッシュだけを消す**。判定に主キーと一意キーを使う。置き場は `sql_cache.store`（memory / redis / db）。**既定は無効**（`sql_cache.enabled = false`。切っているときは更新側の処理が1行も走らない） |
| **同じ `AsyncData` を面ごとに違う形で返す**（F-A-11） | `TableNest.ON` / `OFF` / `AS_IS`。管理画面はテーブルネスト、ショップはフラット、を<b>同じクラスのまま</b>出し分けられる。既定は `AS_IS` で既存の出力は変わらない |
| **実装済みの API を MCP からも出す**（F-W-27 / F-MCP-15） | `RouteTool` でルートをそのまま MCP のツールにする。`before` / `after` / エラーハンドラ / 流量制限が<b>同じように効く</b> |
| **レートリミット**（F-R-15） | ルートに宣言し、ディスパッチャが `before` より前に見る。トークンバケット。置き場は `rate_limit.store`（memory / redis / db） |
| **グレースフルシャットダウン**（F-X-05） | SIGTERM で「ヘルスチェックだけ落とす → 待つ → 新規を断つ → 処理中を待つ → 止める」 |

### 変更

- `db.xxx.product` が設定に増えた。**書かなければ `mysql`**（既存の設定はそのまま動く）
- `jimble-db` が PostgreSQL の JDBC ドライバを `runtimeOnly` で持つようになった。
  要らなければ `exclude(group = "org.postgresql", module = "postgresql")` で外せる
- `SelectBuilder` / `InsertBuilder` などの `sql()` に `sql(Dialect)` が増えた。
  引数なしの `sql()` は<b>主データソースの製品</b>に落ちる（既存の呼び出しはそのまま）
- `application.local.conf` の `include "application.conf"` が効くようになった

### 直したもの

- **テーブル定義クラスの型の取り違え。**MySQL の `SHOW FULL COLUMNS` は enum / set の
  <b>値そのもの</b>を型名に含めるため、`enum('serial','parallel')` が `int`、
  `enum('unreal','real')` が `double` になっていた。enum / set は `String` に倒す。
  **enum 列を持つアプリは、`codegen` をかけたあとの差分を一度見ること**
- 数の列に関数の既定値（`nextval(...)`、`NaN`）が付いていると、
  生成される Java が<b>コンパイルできない</b>ことがあった
- `selectListWithRowCount` が `ORDER` も `LIMIT` も無い SQL で例外になっていた
- データソースを開くときの例外を「データベースが無い」に潰していたため、
  <b>パスワード違いでもデータベースを作りにいっていた</b>

### 気をつけること

- **テーブル定義クラスの生成（`codegen`）は再実行を推奨。**上の型の取り違えの修正が入る
- PostgreSQL で使うときの制限（空間関数の座標の順、`greatest` / `least` の NULL、
  正規表現の方言など）は [DB を使う](https://jimble.io/ja/db) と
  [SQL DSL](https://jimble.io/ja/sql) にまとめてある

---

## 0.1.0（2026-09-07）

最初の公開。

- Web（Router / Request / Response / SSE / WebSocket / セッション / アセット / SPA / MPA）
- DB（SQL ビルダー / トランザクション / マイグレーション / コード生成 / キャッシュ / 分散ロック）
- バッチ / MQ / バッチ管理画面
- MCP サーバー
- `jimble new` / `migrate` / `codegen` / `jimbleRun`（Gradle プラグイン）
- `AsyncData` の先読み

**アノテーションと DI を使わない。**コードを上から辿れば処理が分かることを最優先にする。
