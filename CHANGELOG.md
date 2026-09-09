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
| **`FileUtil.FileMetaData` の `delimiter` と `charset` の中身が変わった**（D-116） | `delimiter` は tika の名前（`comma`）ではなく**区切り文字そのもの**（`,` / `\t` / `;` / `|`）を返します。`charset` は `Charset` の正式な名前（`UTF-8`。以前は小文字）です。画像・その他のときは `null` ではなく空文字を返します。`contentType` は**パラメータを落とした小文字**で返します |
| **`IcuUtil.convertToHiragana(...)` が「ー」を残すようになった**（D-120） | いままでは `コーヒー` → **`こおひい`**、`サーバー` → `さあばあ` と、直前のかなの母音に開いていました（ICU の決まり）。これからは `こーひー` `さーばー` と<b>そのまま残ります</b>。`convertToKatakana` で往復すると元に戻ります。`ヶ` → `け`、`ヵ` → `か` は変わりません。あわせて、かなの変換のついでに起きていた**無関係な文字の Unicode 正規化**（チベット文字の合成、結合文字の並べ替え）が無くなりました |
| **`FileCharDetecter.detector(...)` の返す名前が変わった**（D-119） | `Charset.name()` の正式な名前を返します（`SHIFT_JIS` → `Shift_JIS`、`WINDOWS-1252` → `windows-1252`）。`Charset.forName(...)` に通す使い方なら影響しません。あわせて**言い切れないときは既定の文字コードを返す**ようになりました（純 ASCII や空のファイルに当てずっぽうを返しません） |
| **`Dialect` に `sqlSyntax()` が増えた**（D-114） | SQL の字面の決まり（文字列・識別子・コメントの見分け方）を返す。`jimble-db` の `MySqlDialect` / `PostgreSqlDialect` は対応済み。**自分で `Dialect` を実装している場合は追加が要る**（`SqlSyntax.MYSQL` / `SqlSyntax.POSTGRESQL` をそのまま返せます）。既定値を持たせなかったのは、黙って MySQL の読み方になるほうが危ないためです |
| **マイグレーションで1文も取り出せなかったら失敗にする**（D-114） | up や down が全部コメントだったとき、いままでは成功として通していた。down でこれが起きると<b>テーブルは残ったまま履歴だけ消える</b> |
| **適用済みのマイグレーション SQL の名前を変えると、起動時に止まる**（F-G-20 / D-113） | 適用済みはファイル名で覚えているので、`001_create_post.sql` を `001_create_post.mysql.sql` に変えると**別のファイルとして同じ SQL がもう一度流れる**（`Table 'post' already exists`）。**製品の接尾辞を落とした名前**でそれを見分けて止め、**履歴を付け替える `UPDATE` 文を出す**（中身も変わっていれば `hash` も）。`examples/blog` を使っている環境では、その `UPDATE` を流すか `blog_example` を作り直すこと |
| **`codegen` が `sql_cache` / `sql_cache_tag` / `rate_limit` を生成しなくなった**（D-68） | jimble が作るテーブルなのに除外一覧から漏れていて、**アプリのテーブル定義クラスとして生成されていた**（`rate_limit` は `io.jimble.web.ratelimit.RateLimit` と単純名がぶつかる）。これらのテーブルがある環境では `codegen` の出力が変わるので、**生成物をコミットしているなら流し直すこと** |

### 直した

| | |
|---|---|
| **`jimbleRun` を止めて動かし直すと、次のビルドが起動しない**（D-101） | helidon が起動のときに JVM 全体の直列化フィルタを張る。`jimbleRun` はアプリを Gradle デーモンの中で動かす（D-77）ので、**フィルタがデーモンに残り**、次のビルドが `Couldn't populate class org.gradle.api.services.BuildServiceParameters$None > filter status: REJECTED` で落ちていた。`jimbleRun` のあいだだけ `helidon.serialFilter.missing.action = IGNORE` にする。**本番の挙動は変わらない** |
| **マイグレーション SQL の切り分けが、製品にかかわらず MySQL の読み方だった**（F-G-05 / D-114） | バックスラッシュを常にエスケープとして読むので、PostgreSQL の `insert into t values ('c:\');` で**文字列が閉じず、後ろの SQL が全部1文にくっついて**いた。文字列・識別子・コメントの見分け方を方言に聞くようにした（`Dialect.sqlSyntax()`）。`#` の行コメント（MySQL だけ）、`--` のあとの空白（MySQL は要る）、ドル引用符 `$tag$ ... $tag$`（PostgreSQL の関数の本体・`DO` ブロック）、入れ子のブロックコメント（PostgreSQL）も読み分ける |
| **行コメントを書くと、そこから先の SQL が全部コメントになっていた**（D-114） | 読んだ直後に改行を空白へ潰していたので、`--` や `#` が**行末で終わらなかった**。潰すのは**ハッシュを取るときだけ**にした（潰し方は変えていないので、適用済みのハッシュは変わりません）。コメントだけになった断片は捨てます（MySQL の `/*! ... */` は実行されるコメントなので捨てません） |
| **SPA を `/` に置くとトップページだけ 404** | `"/*"` はセグメントが0個の `/` に当たらないのに、`/` 自身を登録していなかった。`/any` は 200 で返るので気づきにくかった |
| **`in` の空一覧が JSON の `where` からだと素通りしていた** | `{"where": {"site": {"id|in": []}}}` が `IN (NULL)` になり、**例外もエラーも出ずに 0 件**（`not_in` なら本来の全件が 0 件）。`in(null)` も同じく落とすようにした |
| **エラーハンドラが送信してから落ちると、後続のエラーハンドラが走っていた** | 「送信済みなら以降は実行しない」の判定を `catch` で飛ばしていた |
| **ドキュメントだけでアプリを1本作れなかった**（`docs/docs-only-trial.md`） | ドキュメントと Maven Central の 0.2.0 だけでアプリを作る実地確認をしたところ、**そもそも始められませんでした**（依存の座標も動くビルドファイルもサイトに無い）。日英 35 ページずつに次を入れました：**そのまま動く `settings.gradle.kts` / `build.gradle.kts` の全文**とモジュールの表、リポジトリ URL、**「サイトは開発中の版に追従している」の明示と 0.2.0 に無い節の印**、`getString` / `getStringOptional` の説明の訂正（逆でした）、**`context.request()` から直接は読めない**、`DBUtil.load(...)` を DB のページに、クラスの置き場所の表、`gradle wrapper --gradle-version`。**フレームワークのコードは変えていません** |
| **依存の一覧が GitHub に一度も登録できていなかった**（NF-S-07 / D-118） | `dependency-submission` が9回とも失敗していた。原因はワークフローではなく<b>リポジトリの設定で Dependency graph が無効</b>だったことで、`The Dependency graph is disabled for this repository` は**ログ本文ではなく Annotations にしか出ない**ため、最後まで読んでも成功したようにしか見えなかった。設定を有効にして通した。ワークフローの先頭にこの前提を書いた |
| **GitHub Actions の action を上げた**（D-118） | Node 20 の非推奨警告を消すため、`actions/checkout` を v7、`actions/upload-artifact` を v7、`actions/setup-java` を v5 に。**`gradle/actions` は v5 で止めた**（v6 はキャッシュが MIT ではない非公開コンポーネントになり、使うと Gradle の Terms of Use への同意になる）。Dependabot が毎週 v6 を出してこないよう `ignore` を入れ、上限を 3 から 5 に上げた |
| **`icu4j` を外した**（NF-L-01 / D-120） | `IcuUtil` の4つの変換（ひらがな⇄カタカナ、全角⇄半角）を自前の表にしました。**14.5MB の依存が 12KB のクラス1つになります**（実行時クラスパスの単独最大でした）。ICU が返していたものと **129 万通りを突き合わせて**あり、上の「ー」の件以外は 1 文字も変わりません（`IcuUtilTest`）。半角カナの取り込み、濁点の合成、`㋐` → `ア`、`㌀` → `アパート` もそのままです |
| **`juniversalchardet` を外した**（NF-L-01 / D-119） | 文字コードの判定を tika に寄せました。tika の判定器は CSV のために入れている `tika-parser-text-module` が連れてくるので、**依存は増えません**（Apache-2.0）。**245KB と MPL-1.1 / GPL-3.0 / LGPL-3.0 のトリプルライセンスが1本減ります。**94 ファイルで突き合わせて、**実寸のファイルでは答えが変わらない**ことを確かめてあります（`FileCharDetecterTest`）。ICU4J も測りましたが、短い日本語に弱いので採っていません |
| **ドキュメントの食い違い 3 件** | `execution.md`「送ったら止まる」で `after` も止まると書いていた（`after` と `onComplete` は `finally` にあるので必ず通る）／`config.md` の `migration.on_startup` の既定を `false` と書いていた（実際は `"auto"`）／`deploy.md` の起動ログ例が jar の外の conf を「クラスパスより優先」と書いていた（jimble はクラスパスしか見ない） |

### 足した

| | |
|---|---|
| **マイグレーション SQL を製品ごとに分けられる**（F-G-20 / D-113） | ファイル名の接尾辞（`001_create_post.mysql.sql` / `001_create_post.postgresql.sql`）。**接尾辞の無いファイルはどちらでも流れる**ので、両製品で通る SQL は分けなくてよい。接尾辞は `db.<名前>.product` に書ける名前と同じ表で見る（`mariadb` は `mysql`）。飛ばしたファイルは起動ログに出る。`examples/blog` の DDL を両製品で書き、**`pgTest` に載せた**（D-112 の残り） |
| **ドキュメントサイトの反映を自動化した**（NF-D-01 / D-117） | **タグを打つと** GitHub Actions がサイトを作り、配る先（`jimble-document`）の `dist/` を置き換えて push します。Cloudflare はそこを見ているので、そのままデプロイされます。**push のたびには反映しません**（サイトだけが公開版より先に進むのを止めるため）。ドキュメントだけ直したときは Actions の画面から手で流せます。あわせて**サイトの原稿をリポジトリに入れました**（`.gitignore`）。それまで原稿が入っていなかったので、CI の「ドキュメントサイトの生成」は**0 ページのサイトを作って成功していました**（`site` タスクは 0 ページでも成功します）。ページ数の確認を CI と反映の両方に入れてあります |
| **CI を入れた**（D-108） | GitHub Actions。`build`（DB なし）／`db`（MariaDB + Redis）／`pg`（PostgreSQL + Redis）の3ジョブ。`dbTest` / `pgTest` / `codegenCheck`（F-G-14）/ `migrate → codegen → compileJava` の連鎖（NF-T-07）/ サンプルアプリの疎通（NF-T-06）/ ドキュメントサイトの生成（NF-D-03）が毎回回る。**標準ランナーだけ**で、larger runner は使わない |
| **待ち受けを始めてから止め方を預けていた**（D-110） | `JimbleServer` は `WebServer` を start したあとに `Shutdown.add(...)` していたので、**そのあいだに `jimbleRun` が止めに来ると何も止まらなかった**（古いアプリがポートを握ったまま残る）。start の前に預けるようにした。あわせて `stop()` は待ち受け前なら即座に抜ける（猶予のぶん黙って寝ないように） |
| **`pgTest` が PostgreSQL のつもりで MySQL に繋ぎに行っていた**（D-112） | `application.pgtest.conf` が無いモジュール（`examples/blog`）では `application.conf` に落ちるため。**手元は MySQL も立っているので通ってしまい**、CI の PostgreSQL だけのジョブで初めて落ちた。conf が無いモジュールは `pgTest` を飛ばす（`SKIPPED` としてログに出る） |
| **Gradle 9 でプラグインの検証が落ちていた**（D-111） | キャッシュの可否と入力の正規化の注釈が無かった。Gradle 8 は警告で流すが 9 はエラー。`enableStricterValidation` を入れて**手元でも 9 と同じ厳しさ**で見るようにし、あわせて javadoc の警告 14 件も潰した |
| **サンプルアプリが、まっさらな DB では動かなかった**（D-109） | `examples/blog` は MQ のテーブルをバッチの入口でしか作っていないのに、**キューに積むのは Web** だった。まっさらな DB で記事を登録すると「トランザクションのコミットに失敗しました」で 500。**いちどでもバッチを動かしたマシンでは動く**ので手元では気づけず、**CI を入れた初回に出た**。起動時に用意するものを `Bootstrap` 1か所にまとめ、3つの入口とテストがそれを呼ぶようにした。落とし穴のページにも足した |
| **Apache Tika を 3.3.2 → 4.0.0 に上げた**（D-115） | `Parser.parse` / `Detector.detect` が `TikaInputStream` を取るようになり、`Detector.detect` に `ParseContext` が増え、`Metadata` が `HttpHeaders` / `TIFF` を実装しなくなった（`Metadata.CONTENT_TYPE` → `HttpHeaders.CONTENT_TYPE`、`Metadata.IMAGE_WIDTH` → `TIFF.IMAGE_WIDTH`）。**種別判定の答えは1つも変わっていません**（同じテストを両方の版で流して確認。そのために `FileUtil` に初めてテストを付けました）。**依存が増えます**：`juniversalchardet`（MPL-1.1）が消え、`tika-encoding-detector-mojibuster` / `tika-ml-core` / `tika-parser-datauri-commons` と **commonmark**（tika-core 4 の既定出力が Markdown になったため）が入ります。実行時クラスパスで約 +0.5MB |
| **画像の幅と高さが取れるようになった**（D-116） | いままで**常に 0** でした（`AutoDetectParser` に画像を読む parser が無いため）。JDK の `ImageIO` でヘッダだけを読みます（PNG / JPEG / GIF / BMP / TIFF）。**WebP は `ImageIO` に無いのでコンテナを自前で読みます**（`VP8 ` / `VP8L` / `VP8X`）。画素は展開しないので大きな画像でもヒープを食いません。**HEIC / AVIF / SVG と動画は 0 のまま**です。`tika-parser-image-module` は +9 jar / 約 5.4MB（pdfbox 一式）になるので入れていません |
| **CSV の文字コードと区切り文字が取れるようになった**（D-116） | 種別を名前から決め直すときに**文字コードごと差し替えていました**。あわせて、`text/csv; charset=UTF-8` のようなパラメータ付きの種別が CSV として扱われなかったのと、拡張子の無い一時ファイル（アップロードの置き場所）が CSV として扱われなかったのも直しました |
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
