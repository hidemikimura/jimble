# jimble 要件定義書

| 項目 | 内容 |
|---|---|
| プロダクト名 | jimble（Java 製 Web アプリケーションフレームワーク） |
| ドメイン | jimble.io |
| 版 | v4.8 |
| 作成日 | 2026-09-06 |
| 最終更新 | 2026-09-09（**分散トレーシング（NF-O-05）。jimble-otel を追加**） |
| ステータス | **M1〜M8 完了 = Phase 1 完了。M9〜M12 完了**（M9 = SSE / MCP / WebSocket / ドキュメントサイト / Maven Central 公開、M10 = 先読み、M11 = 実アプリの移送と PostgreSQL 対応、M12 = CI / 依存の縮小 / 可観測性）**。0.2.0 公開済み（`io.jimble`）。ドキュメントは <https://jimble.io>**（テスト 994 件 + Gradle プラグイン 27 件 + 実 MySQL / Redis 結合テスト 226 件 + 実 PostgreSQL 結合テスト 226 件 + ベンチマーク 5 件。ドキュメント 日英 35 ページずつ）。関連：`docs/extends-jooby-analysis.md` / `docs/jooby-base-inventory.md` / `docs/sample-app-analysis.md` / `CHANGELOG.md` |

---

## 1. 背景と目的

### 1.1 背景

現在、自社プロダクト（aqSell 等）は [jooby](https://jooby.io/) をラップした内製フレームワーク `jooby_base` 上で稼働している。
`jooby_base` は数年の運用を通じて、DB アクセス層（SQL ビルダー・`Data`）、遅延読み込み（`AsyncData`）、
設定・ログ・キャッシュ・バッチといった実用的な部品が揃っている一方で、
Web 層が jooby の API・ライフサイクル・DI に強く依存している。

### 1.2 目的

jimble は、`jooby_base` の資産のうち **Web フレームワーク非依存な部分をそのまま引き継ぎ**、
Web 層のみを **helidon-webserver + 自作 Router** に置き換えた新しいフレームワークである。
これにより次を実現する。

1. **依存の縮小** — jooby という大きなフレームワークへの依存をなくし、HTTP サーバー（helidon-webserver）だけに依存する
2. **実行モデルの統一** — Web / バッチ / MQ を同一の `Context` 概念で扱い、同じコードを3経路から呼べるようにする
3. **可読性の最大化** — アノテーション・DI を排し、「コードを上から辿れば処理が分かる」状態を維持する
4. **移行コストの最小化** — 既存プロダクトが現実的な工数で乗り換えられること

### 1.2.1 プロジェクトの実像（棚卸しで判明した前提）

`jooby_base` 全 61,177 行のうち、**`io.jooby` に依存しているのは 18 ファイル・約 4,700 行（7.7%）だけ**である。
さらに **jooby に依存しない自作 Router が既に存在する**（`lib/base/web/router/`、1,619 行、
doc.txt が指定する小文字 DSL を実装済み。ただしハンドラが `Runnable` で Context と未結線）。

**したがって jimble は「フレームワークを一から作る」プロジェクトではなく、
「`jooby_base` の 7.7% を helidon 向けに置き換え、既存 Router を結線する」プロジェクトである。**
作業の重心は新規実装ではなく、既存コードの切り出し・結線・検証にある。

さらに、その 4,700 行を精読した結果（`docs/extends-jooby-analysis.md`）：

- **607 行（`EmptyJoobyContext`）は jimble では丸ごと不要になる。**
  バッチ・MQ から Context を作るために jooby の `Context` を全実装した偽物であり、
  Context が自前の型になれば存在理由が消える
- **380 行のルート登録 DSL は既存 Router に寄せればほぼ消える**（同型コードの繰り返し）
- **934 行（46%）は静的ファイル配信**で、ルーティングの本質ではない。流用して独立クラスに切り出す

**真に新規で書くのは 1,000 行前後**（リクエストライフサイクル約 250 行 / Context 約 500 行 /
起動シーケンス約 200 行）。詳細は `docs/jooby-base-inventory.md` と `docs/extends-jooby-analysis.md`。

### 1.3 位置づけとゴール

**第1フェーズはフレームワーク単体の完成、第2フェーズで OSS 公開**という段階を取る。

| フェーズ | ゴール | 判断基準 |
|---|---|---|
| Phase 1 | `jooby_base` の機能を可能な限り網羅したフレームワークが単体で完成している | 機能カバレッジ表（10.1）が埋まり、サンプルアプリで一通り動作する。**2026-09-06 完了** |
| Phase 2 | jimble.io でドキュメントを公開し、社外の Java 開発者が使える | 外部の人間がドキュメントだけでアプリを1本作れる。**2026-09-09 に実地確認した**（`docs/docs-only-trial.md`）：<b>コードを書き始めてからはドキュメントで足りた</b>が、**動く `build.gradle.kts` が載っていないので、そもそも始められない**。<b>指摘 8 件はすべてドキュメント側で直した</b>（同日。サイトへの反映は未実施） |

**既存アプリケーションの移行は Phase 1 のスコープに含めない。**
移行を先に置くと「そのアプリが使っている機能」に引きずられ、フレームワークとしての機能が欠けたまま固まるため、
まず `jooby_base` 相当の機能を揃えることを優先する。移行は Phase 1 完了後に別途計画する。

Phase 2 を後付けにしないため、**Phase 1 の時点から次を満たす**（詳細は「9. 非機能要件」）。

- 公開しても問題のない命名・パッケージ構成にする（社名・プロダクト名をフレームワークに混ぜない）
- 自社固有の業務知識（マルチテナント、`shop_id` 等）をフレームワーク側に持ち込まない
- 公開ライセンスに適合する依存関係のみ使う

---

## 2. スコープ

### 2.1 対象（初期スコープ）

モノリポに以下すべてを含める。

| # | モジュール | 概要 |
|---|---|---|
| A | コア | Router / Context / HTTP 入出力。helidon-webserver 統合 |
| B | DB 層 | SQL ビルダー、`DB`、`Data`、テーブル定義コード生成 |
| C | 周辺ランタイム | Conf / Log / Cache / Session / Cookie / Flash / Validation / Paging / 分散ロック |
| D | 実行基盤 | バッチ、MQ、AsyncData（遅延読み込み・先読み） |

### 2.2 対象外（初期スコープ外・将来検討）

- ORM（エンティティマッピング）— jimble は `Data` ベースを維持し、ORM は導入しない
- 管理 UI・スキャフォールディング GUI
- **MySQL 以外の RDBMS — PostgreSQL に対応した**（F-D-30。M11 で実装。`docs/design-m11.md` 19 章）。SQL ビルダーとフレームワークの内部テーブルが `db.xxx.product` で切り替わり、アプリ側のコードは1行も変えなくてよい。**テーブル定義クラスの生成（codegen）も PostgreSQL に対応した**（D-98）。Oracle / SQL Server は引き続き対象外
- **gRPC — 対象外。**依存が +28 jar / +10.4MB になり（実行時全体が 2.7MB → 13.1MB）、目的1「依存の縮小」と正面からぶつかる。使いたいアプリが自分で `helidon-webserver-grpc` を足して `GrpcRouting` を登録できる**逃げ道だけ**用意する（D-52。`docs/design-protocols.md` 4章）
- HTTP/3（Phase 3 以降の検討事項）
- **WebSocket は対象になった**（F-W-22。M9 で実装。`docs/design-m9.md` 6章）
- 分散トレーシングの独自実装（OpenTelemetry への出力口だけ用意する方針、「9.2」参照）
- 既存アプリケーションの移行作業（「1.3」参照）

### 2.3 前提条件

- 既存 `jooby_base` のソースコードを参照・流用できる
- 想定するアプリケーションは MySQL 構成（**PostgreSQL でも動く**。F-D-30）。Redis は任意（**Redis 無しでも起動できること**）
- 実行環境は Linux コンテナ
- Phase 1 では既存アプリの移行を行わない（機能の網羅性を優先する）

---

## 3. 用語定義

| 用語 | 意味 |
|---|---|
| Context | 1つの実行単位（Web リクエスト / バッチ1回 / MQ メッセージ1件）に対応するスコープの入口。DB 接続・ログ・属性を保持する |
| Router | URL パスと HTTP メソッドから実行対象を決定する自作のルートツリー |
| Executor | Context を受け取って処理を行う実行単位の抽象。UseCase / Validation の基底 |
| UseCase | 1エンドポイント（または1処理）につき1クラスの処理本体 |
| Data | `LinkedHashMap<String, Object>` を継承した汎用データ構造。DB 行・リクエスト・レスポンスすべてに使う |
| Column | 自動生成されたテーブル定義の列参照（`Product.id` など） |
| AsyncData / AsyncList | 参照された時点で初めて SQL を発行する遅延読み込みデータ |
| 面 | admin / shop / system のようなアプリ側の区分。jimble 自体は面の概念を持たない |

---

## 4. 設計原則

doc.txt に記された方針を、判断基準として使える形に落としたもの。
**迷ったときはこの順で優先する。**

### 原則1: 人間がコードを辿れること（最優先）

- ある URL を叩いたときに何が動くかを、**IDE の「定義へジャンプ」だけで最後まで追える**こと
- 実行順序がソースの記述順と一致すること
- 「どこかで登録されている」「実行時に解決される」構造を作らない

### 原則2: アノテーションを使わない

- ルーティング、バリデーション、トランザクション、DI いずれもアノテーションで表現しない
- ルート定義はメソッド呼び出しの DSL、バリデーションはコードで組む
- リフレクションによるスキャンを行わない（起動時間・GraalVM native-image 対応の面でも有利）

### 原則3: DI コンテナを使わない

- 依存はコンストラクタ引数か、明示的な `static` メソッド呼び出しで渡す
- ルートへの登録は `UseCase::new` のような **メソッド参照（`Supplier`）** で行い、実行のたびに生成する
- シングルトンが必要な場合は `Xxx.instance()` の形で明示する

### 原則4: 過度な抽象化をしない

- インターフェースは差し替えが実在する箇所だけに置く（`SessionStore`、`ICache` など）
- 「将来のため」の抽象化層を作らない

### 原則5: 隠れた I/O を作らない

- getter がクエリを投げる `AsyncData` は例外だが、**その事実がクラス名で分かる**ようにする
- 暗黙のトランザクション開始・自動コミットを行わない

### 原則6: 既存 API との互換性

- `jooby_base` から流用する部分は **原則としてシグネチャを変えない**
- 変える場合は「12. 決定事項と残る未決事項」または移行ガイドに明記する

---

## 5. 技術前提

| 項目 | 決定 | 補足 |
|---|---|---|
| 言語 | Java 25（LTS） | `ScopedValue`・仮想スレッドを正式機能として使用 |
| ビルド | Gradle（Kotlin DSL） | マルチプロジェクト構成 |
| HTTP サーバー | helidon-webserver 4.5.4 | `routing.any()` で全リクエストを受ける |
| スレッドモデル | 仮想スレッド（リクエストごと） | 「9.1」に注意点 |
| DB | MySQL 8.x / MariaDB / PostgreSQL 16+ | JDBC 直叩き。ORM なし。`db.xxx.product` で方言を切り替える（F-D-30） |
| キャッシュ | DB / メモリ / Redis | 設定で切替。`jooby_base` に3種とも実装済み |
| セッション | DB / Redis / Cookie / なし | 設定で切替（F-S-01）。Cookie のみ新規実装 |
| 分散ロック | Redis（Redisson） | Redis 未設定時は利用不可（F-U-10） |
| テンプレート（標準） | jte | コンパイル済みテンプレート。フレームワークが標準で対応する |
| テンプレート（実行時） | Pebble 等を差し替え可 | アプリがエンドユーザー編集のテンプレート機能を提供する場合のみ |
| JSON | 自前（`Dson`） | `jooby_base` の `lib.base.util.common.json.Dson` を流用。外部 JSON ライブラリに依存しない |
| マイグレーション | `jooby_base` の機構を流用（生 SQL / `!Ups`・`!Downs`） | ローカルはビルド時、それ以外は起動時に適用（「7.10.1」） |
| パッケージ / 座標 | `io.jimble` | groupId も `io.jimble` |
| ライセンス | Apache-2.0 | |
| 最低起動環境 | Linux / コンテナ | |

---

## 6. リポジトリ構成（モノリポ）

```
jimble/
  docs/                      要件・設計・ドキュメントサイトの原稿
  jimble-core/               Context / Executor / AppLifecycle（実行の骨格だけ）
  jimble-util/               Data / Log / Conf / 型変換 / Dson / 文字列 / 日時 / URL / IO /
                             CSV / XML / ハッシュ・暗号 / スレッド / ネットワーク /
                             HTTP クライアント / Paging / AsyncData / IColumn・ITable・ISchema
  jimble-db/                 SQL ビルダー / Column / Table / DB / トランザクション / キャッシュ / ロック
  jimble-web/                Router / Request / Response / Session / Cookie / Flash / Validation / Paging
  jimble-batch/              バッチ実行基盤・DB スケジューラ
  jimble-mq/                 MQ 実行基盤
  jimble-batch-manager/      バッチ管理画面（Web + バッチ）
  jimble-migration/          マイグレーションの適用・履歴管理（自前実装）
  jimble-codegen/            テーブル定義・型付きアクセサのコード生成
  jimble-gradle-plugin/      migrate / codegen / jte 変換 / ホットリロードを Gradle タスクとして提供
  jimble-cli/                プロジェクト雛形生成（jimble new）・migrate / codegen の CLI 実行
  jimble-mcp/                MCP サーバー（Streamable HTTP / 仕様 2026-07-28）
  jimble-docs/               ドキュメントサイトの生成（jte + commonmark。実コードからコード片を抜く）
  examples/                  サンプルアプリ（ドキュメントの実行可能な裏付け）
  integration-tests/         実サーバーを起動する結合テスト
```

**モジュール間の依存は一方向とする。**

```
gradle-plugin → cli → codegen → migration → db → util → core
batch → mq → db → util → core
web → db → util → core
batch-manager → web / batch
mcp → web → db → util → core
```

- `core` は HTTP も `Data` も知らない（Context と実行の骨格だけ）。**ログも設定も持たない**
- `util` は SQL を知らない（列・テーブルは `IColumn` / `ITable` の定義インターフェースだけ見る）
- `db` は Web を知らない
- 循環依存は CI で機械的に検出して落とす

---

## 7. 機能要件

優先度：**MUST** = Phase 1 必須 / **SHOULD** = Phase 1 で入れたい / **LATER** = Phase 2 以降

### 7.1 コア：Router

| ID | 要件 | 優先度 |
|---|---|---|
| F-R-01 | helidon-webserver の `routing.any()` で全リクエストを受け、jimble の Router に委譲する。helidon 側のルーティング機能は使わない | MUST |
| F-R-02 | ルート定義 DSL は次の語彙を提供する：`path` / `before` / `get` / `post` / `put` / `patch` / `delete` / `options` / **`head`** / **`trace`** / `after` / `error` / `install`（すべて小文字。既存 `lib/base/web/router` の実装と一致） | MUST |
| F-R-03 | ルート定義はコントローラクラスの**インスタンス初期化ブロック**に記述する（アノテーション不使用） | MUST |
| F-R-04 | `path(prefix, () -> { ... })` でパスをネストでき、ネストは任意段数を許す | MUST |
| F-R-05 | `install(ChildController::new)` で子コントローラを取り込める | MUST |
| F-R-06 | パスパラメータ `"/{id}"`、ワイルドカード `"/*"` をサポートする | MUST |
| F-R-07 | ハンドラの登録形態を2つ持つ：(a) `Consumer<Context>` のラムダ直書き、(b) `Supplier<Executor>` を可変長で並べ、**登録順に**実行する | MUST |
| F-R-08 | `before` / `after` は**書いたブロック（レキシカルスコープ）で登録したルート**と、そこから `path` / `install` でネストしたものに適用され、外側→内側の順で `before`、内側→外側の順で `after` を実行する。**パスのノードには付かない**（D-69）。ブロックの中では書いた順（フックとルートの前後）を問わない | MUST |
| F-R-09 | `error` は同じスコープで登録したルートで発生した例外を捕捉する。内側のスコープが優先される | MUST |
| F-R-09b | **ルート未マッチ（404）も `error` の経路に流す。**アプリ全体の 404 ページをトップレベルの `error` で書けること。**未マッチのときに呼ぶのは一番外側のスコープの `error` だけ**（どのルートにも当たっていないので内側のスコープが決まらない） | MUST |
| F-R-10 | ルートツリーの構築は**起動時に1回**行い、リクエスト時はマッチングのみを行う（毎回の再構築・リフレクション探索をしない）。**ルートごとの `before` / `after` / `error` も起動時に確定する**（リクエストのたびに親を辿って集め直さない）。確定後にフックを足したら例外にする（D-69） | MUST |
| F-R-11 | マッチング結果はパスとメソッドをキーにキャッシュしてよいが、キャッシュ有無で挙動が変わらないこと | SHOULD |
| F-R-12 | 起動時に、登録された全ルートの一覧（メソッド・パス・実行クラス列）をログ出力できる | SHOULD |
| F-R-13 | **重複ルート（同一メソッド・同一パス）は登録時に例外**とする。**到達不能ルート**（どんなリクエストでも他が先に当たるもの）は起動時に検出して警告する。**既定は警告のみ**で、`server.strict_routes = true` にすると例外（D-10）。検出は<b>条件を書き起こさず、試しのパスを本物のマッチャに流して「返ってきたのが自分か」を見る</b> | MUST（**済**。D-10） |
| F-R-14 | 静的ファイル配信（`resource`）、SPA / MPA 配信、リバースプロキシをルート定義から宣言できる | SHOULD |
| F-R-15 | レートリミットをルート単位で宣言できる | LATER（**済**。D-90） |
| F-R-16 | **ルート単位の属性**を宣言でき（`.attribute(key, value)`）、`before` / `after` からそのルートの属性を参照できる | MUST |
| F-R-17 | ルート属性はキーが型付きで、未設定時の既定値を安全側に倒せること（認証スキップ等の制御に使われるため） | MUST |
| F-R-18 | パスパラメータを `Column` から生成できる（`Site.id.urlPathPlaceholder()` 相当）。パラメータ名とカラム名がずれないこと | SHOULD |
| F-R-19 | **既存の `lib/base/web/router`（`Router` / `RouteTree` / `PathRoute` / `RouterController` / `RouteComparator`）を流用する。**ハンドラを `Runnable` から Context つき Executor に置き換えて結線する | MUST |
| F-R-20 | ルートの優先順位は **固定パス > パスパラメータ > ワイルドカード**。同一階層に複数のパスパラメータがある場合は**登録順**に試す。優先順位はツリー探索順が決めるものであり、テストで固定する（既存 `RouteComparator` はルート一覧の表示順専用で、マッチには関与しない） | MUST |
| F-R-21 | **全 HTTP メソッドへの一括ルート登録**ができる（リバースプロキシ用。現状は 14 回手書きしている） | MUST |
| F-R-22 | ルートに宣言できるもの：**属性 / タグ / レートリミット（ルート単位・パス単位）/ Bot ブロック / 認証要否** | MUST |
| F-R-23 | **ルーティングは生のパス（`rawPath`）を `/` で分割し、セグメントごとに URL デコードする。**デコード済みパスを分割してはならない（`%2F` がパス区切りに化け、ルートを取り違える） | MUST |
| F-R-24 | 末尾スラッシュの有無は区別しない（`/a/b` と `/a/b/` は同じルート） | MUST |
| F-R-25 | **パスは合っていてメソッドだけ違うなら 405 を返し、`Allow` を付ける**（RFC 9110 の MUST）。404 と混ぜない——404 は「そんなものは無い」、405 は「あるが、その呼び方ではない」で、混ぜると<b>`post` と書くべきところを `get` と書いただけ</b>の間違いが「パスが違う」に見える。擬似メソッドの `WS` は `Allow` に出さない。**当たりうるメソッドは探索のついでに集める**——失敗してから木を舐め直すと<b>外れたときだけ高い</b>形になり、叩かれると効く（NF-P-06 / `RouterBench`） | MUST（**済**。D-11） |
| F-R-26 | **ルート属性をブロック単位で宣言できる**（`Router#attribute(key, value)`）。効く範囲は `before` / `after` と同じ（書いたブロックと、そこから `path` / `install` でネストしたもの。<b>パスのノードには付かない</b>。D-69）。強い順に <b>ルート > 内側のブロック > 外側のブロック > キーの既定値</b>。配るのは起動時の確定（F-R-10） | MUST |

**メソッド名は小文字**（`get` / `post` / ...）とする。
`jooby_base` が `GET` / `POST` と大文字にしていたのは jooby 本体の同名メソッドとの衝突を避けるためであり、
jooby に依存しない jimble ではその制約がない。Java の命名規約に従う。

### 7.2 コア：Context

helidon には jooby の `Context` に相当するものがないため、jimble が独自に用意する。

| ID | 要件 | 優先度 |
|---|---|---|
| F-C-01 | `Context` を **実行単位ごとに1つ**生成する。生成契機は (a) Web リクエスト、(b) バッチ実行、(c) MQ メッセージ処理、(d) **WebSocket のメッセージ1件**（D-56）の4つ | MUST |
| F-C-02 | 3種の Context は共通の基底を持ち、DB アクセス・属性・ログ・実行 ID など**実行経路に依存しない機能は共通基底が提供する** | MUST |
| F-C-03 | `WebContext` のみが `request()` / `response()` / `session()` / `cookie()` / `flash()` を持つ。バッチ・MQ から HTTP 由来の API に触れられないこと | MUST |
| F-C-04 | Context は引数として明示的に渡すことを基本とし、加えて `Context.get()` による取得手段を `ScopedValue` で提供する | MUST |
| F-C-05 | `Context.get()` は、Context の生存範囲外で呼ばれた場合に**明確な例外**を投げる（`null` を返さない） | MUST |
| F-C-06 | Context は明示的なライフサイクルを持つ：生成 → 実行 → クローズ。クローズ時に DB 接続の返却、未保存セッションの警告、アクセスログの出力を行う | MUST |
| F-C-07 | Context は次を保持する：実行 ID、開始時刻、環境、DB、属性マップ（`attribute(key)` / `attribute(key, value)`）、SQL 実行回数・実行時間の集計値 | MUST |
| F-C-08 | Context は**1つの実行単位に閉じる**。別スレッドへ引き継ぐ場合は明示的な API を通す（暗黙の継承をしない） | MUST |
| F-C-09 | 実行途中で追加の Executor を積める（`addExecutor` 系） | SHOULD |
| F-C-10 | テスト用に、HTTP サーバーなしで Context を組み立てられるファクトリを提供する | MUST |
| F-C-11 | `ScopedValue` で束ねるのは **Context / リクエストスコープキャッシュ / ログ / 操作情報 / sticky コネクション** の5種。アプリが独自の ScopedValue を追加できるフックを持つ | MUST |
| F-C-12 | **非 Web 実行のためにダミーの HTTP Context を作らない。**`BatchContext` / `MqContext` は共通基底を継承するだけで成立すること（`EmptyJoobyContext` 相当を作らない） | MUST |
| F-C-13 | ライフサイクルの各段で「レスポンス送信済みなら打ち切る」判定を行う。**判定は1箇所（`Stage`）に集約する**。通常の段（`onRequest` / 流量制限 / `before` / ルート / Executor / 送信）だけでなく<b>エラー経路（`error` フック）も同じ `Stage` を通す</b>。移送元は同じ判定が7箇所に散っていた。<b>`after` と `onComplete` はこの判定の外</b>で、`finally` にあるので送信済みでも例外が出ても必ず通る | MUST |
| F-C-14 | エラーハンドラは **`(Context, Throwable, ステータスコード)`** を受け取る。コードは例外から解決し、アプリが解決規則を上書きできる | MUST |
| F-C-15 | **エラーハンドラ自身が投げた例外は握って次のハンドラに進む。**最終的に必ずレスポンスが返ること | MUST |
| F-C-16 | エラーログに出すのは 500 番台のみ。404 などをエラーログに流さない | MUST |
| F-C-17 | **誰も本文を用意しなかったときの既定のエラー応答を持つ。**JSON を名指しされていれば `{"error":{"status":404,"message":"Not Found"}}`、そうでなければ `404 Not Found` の短いテキスト（`*/*` は「何でもいい」なのでテキスト）。**中身は決め打ちで、例外のメッセージも SQL もスタックトレースも載せない**（NF-S-06）。**`error` フックが組み立てていれば、送信前でもそちらが勝つ**（D-11） | MUST（**済**。D-11） |

### 7.3 コア：Executor / UseCase

| ID | 要件 | 優先度 |
|---|---|---|
| F-E-01 | `Executor` は Context を受け取り処理を行う抽象。`UseCase` と `Validation` がこれを継承する | MUST |
| F-E-02 | 実装側は `execution(Context)` のみを実装する。例外処理・前後処理は基底が担う | MUST |
| F-E-03 | キャンセル機構を持つ：`setCanceled()` / `isCanceled()` / キャンセル時コールバック。キャンセルされたら後続 Executor を実行しない | MUST |
| F-E-04 | エラー状態を保持し `hasError()` で判定できる | MUST |
| F-E-05 | 1エンドポイント1 UseCase を推奨とし、ドキュメントとサンプルでその形を示す | MUST |
| F-E-06 | Executor は**キュー**であり、実行中に追加できる。キャンセル時は**残りを破棄してからキャンセル処理を実行する** | MUST |

### 7.4 Web：Request / Response

| ID | 要件 | 優先度 |
|---|---|---|
| F-W-01 | `Request` は `Data` を継承し、クエリ文字列・フォーム・JSON ボディのパラメータを同一の取得 API で扱える | MUST |
| F-W-02 | `Request` は `method` / `path` / `url` / `host` / `port` / `scheme` / `header` / `address` / `proxyAddress` / `accept` 系を提供する | MUST |
| F-W-03 | ネストしたパラメータ（`a[b][c]=1` 形式）のパースを提供する | MUST |
| F-W-04 | `Response` は `Data` を継承し、`json(Data)` / `json(key, value)` / `jsonL(List)` / `text` / `redirect` / `code` / `header` を提供する | MUST |
| F-W-05 | レスポンス送信済みかどうかを判定できる（`isResponseStarted()`）。送信後の書き込みは明確に失敗させる | MUST |
| F-W-06 | ファイルアップロード（multipart）を受け取れる。**サイズ上限とテンポラリ保存先を設定で制御できる** | MUST |
| F-W-07 | ストリーミングレスポンス（大きな CSV / JSON Lines）をメモリに全量載せずに返せる | SHOULD |
| F-W-08 | テンプレート描画（`modelAndView`）に対応する。**標準エンジンは jte**（コンパイル済みテンプレート） | MUST |
| F-W-10 | jte テンプレートは **Gradle での事前コンパイルを前提**とする。実行時コンパイルには対応しない | MUST |
| F-W-11 | **エンドユーザーが編集するテンプレート**（アプリがテーマ機能等を提供する場合）向けに、実行時コンパイル型エンジン（Pebble 等）を差し込める口を用意する。jimble 標準としては同梱しない | SHOULD |
| F-W-12 | **CORS** を宣言的に設定できる（`jooby_base` の `Cors` を流用） | MUST |
| F-W-13 | **Basic 認証**をルート単位で掛けられる | MUST |
| F-W-14 | **Bot ブロック**：User-Agent による判定と、Bot 時のレスポンス処理を宣言できる | SHOULD |
| F-W-15 | **リクエストスコープキャッシュ**：1リクエスト中に何度も使うデータを再取得しない仕組みを提供する | MUST |
| F-W-16 | **UserAgent 解析**（デバイス・ブラウザ判定）を提供する | SHOULD |
| F-W-17 | SPA 配信は専用のルーティング（`SpaRouter` / `SpaRoute`）を持ち、通常のルートツリーと共存できる。**マッチは本体と同じ `Router`（ルートツリー）が行う**（別インスタンス。D-75） | MUST |
| F-W-18 | 静的ファイル配信は Etag / Last-Modified / 条件付き GET に対応し、**jar 実行時とディレクトリ実行時の両方で動く** | MUST |
| F-W-19 | OPTIONS は**プリフライト専用の経路**とし、Executor ループを回さない | MUST |
| F-W-20 | 静的ファイル配信 / SPA / MPA / assets は**独立したハンドラクラスに分離する。**ルーティング本体に混ぜない（`ExtendsJooby` が 2,021 行になった主因） | MUST |
| F-W-09 | 再表示用フォーム値の保持（`putForm` / `setForm`）を提供する。**入れた値は応答本体にも載る**（D-133。載せていなかったので、<b>検証に落ちた画面を組み直せなかった</b>）。`examples/approval-forms` で通る | SHOULD（**済**） |
| F-W-21 | **SSE**（Server-Sent Events）を提供する。`context.response().sse()` で開き、種別つきイベント・キープアライブ・`retry:` を送れる。**1本の寿命に上限を置く**（設定。既定5分）。<b>相手が切ったことは検知できない</b>ため、上限で必ず終わること（D-53） | MUST |
| F-W-22 | **WebSocket** を提供する。`ws(path, Supplier)` で登録し、**jimble のルート表に載せる**（起動時の一覧 F-R-12 と重複検出 F-R-13 が効くこと）。**メッセージごとに Context を作る**（接続ごとではない。D-56）。認証は `onUpgrade` で通し、断るときは 403（繋がってから切らない）。**接続の一覧はフレームワークが持たない**（D-57） | SHOULD |
| F-W-27 | **内部呼び出し**：実装済みのルートを **HTTP を通さずに**呼べる（`dispatcher().call(context, CallRequest)`）。**通常のリクエストと同じ道を通る**（`before` / `after` / エラーハンドラ / 流量制限が効く）。**外側のリクエストの続きとして走る**：ヘッダ・Cookie・セッション・Flash・書き込み直後の参照先（F-D-19）は外側と共有し、**実行IDも引き継ぐ**（アクセスログは二重に出さない）。入れ子には上限を置く（循環で `StackOverflowError` を出さない） | MUST（**済**。D-92） |
| F-W-28 | **ログインと認可を組み立て済みで提供する**（`io.jimble.web.auth.Auth` / `Principal`）。`before(Auth::guard)` 1行と、ルート属性（`PUBLIC` / `ROLE` / `NO_SESSION`）だけで書ける。<b>既定は「要ログイン」</b>（書き忘れたルートは閉じる）。役割が足りないときは <b>403</b>（401 ではない）。`Auth.login` は<b>セッション ID を振り直してから保存する</b>（F-S-13）。`Auth.checkPassword` は<b>利用者がいなくても同じだけ時間を使う</b>。注釈も DI も使わない（原則2 / 原則3） | MUST（**済**。D-148） |
| F-W-29 | **ログインの失敗を数えて、次の試行を待たせる**（`io.jimble.web.auth.Lockout`）。`Auth.attemptLogin` が「待たせる → 照合する → 成功なら消す」までやる（成功時に消し忘れられない形）。<b>「N 回で M 分ロック」にはしない</b>——アカウント単位で止める仕組みは<b>そのまま嫌がらせの道具になる</b>ので、<b>待ち時間を倍にしていく</b>（既定：3回までは待たせない → 1 → 2 → 4 …… 上限 300 秒、24 時間で数え直す）。数える単位は<b>入力されたログイン ID</b>（居ない ID も数える。数えないと<b>待たされるかどうかで ID の有無が漏れる</b>）。大小と前後の空白はそろえ、<b>SHA-256 にして保存する</b>（平文で溜めない／攻撃者が好きな行を作れない）。待ち時間が残っていれば <b>429 と `Retry-After`</b>（画面へ飛ばすかはアプリの `error()`）。<b>DB が無ければ何もしない</b>（1度だけ警告する）。掃除は失敗を数えたついでに1時間に1度。流量制限（IP ごと）の代わりにはならない——<b>両方掛ける</b> | MUST（**済**。D-149） |

### 7.5 Web：Session / Cookie / Flash

| ID | 要件 | 優先度 |
|---|---|---|
| F-S-01 | `Session` の保存先を**設定で切り替えられる**：**DB / Redis / Cookie / セッションなし** の4種（DB / Redis / なし は `jooby_base` に実装済み、Cookie が新規） | MUST |
| F-S-02 | 保存は**明示的な `save()`** で行い、自動保存しない | MUST |
| F-S-03 | Context クローズ時に、変更済みかつ未保存のセッションがあれば警告ログを出す | SHOULD |
| F-S-04 | 型付き getter（`getInt` / `getLong` / `getBoolean` 等）を提供する | MUST |
| F-S-05 | Cookie の読み書き、`Secure` / `HttpOnly` / `SameSite` の指定に対応する | MUST |
| F-S-06 | CSRF トークンの発行・検証を提供する | MUST |
| F-S-07 | Flash（次リクエストにだけ残る値）を提供する | SHOULD |
| F-S-08 | Cookie セッションは**署名（改ざん検知）と暗号化の両方**を行う。鍵は設定から与える（`jooby_base` の暗号ユーティリティを流用）。値はサイズ上限を持ち、超過時は明確に失敗させる | MUST |
| F-S-09 | DB セッションは有効期限切れレコードを掃除する仕組みを持つ（バッチまたは遅延削除） | MUST |
| F-S-10 | 保存先を切り替えてもアプリ側のコード（`session().get/put/save`）が変わらないこと | MUST |
| F-S-11 | **リクエスト単位でセッション保存先を選択できる。**（例：管理画面だけ DB セッション、公開側はセッションなし） | MUST |
| F-S-12 | **セッションを使わないリクエストでは Cookie（cookie id / session id）の発行と CSRF トークン生成をスキップできる。**（現状は無セッションでも常に発行しており、公開ページのキャッシュ効率を落としている） | MUST |
| F-S-13 | **ログイン時にセッション ID を振り直せる**（`Session#regenerateId()`）。中身は持ち越し、古い側（DB / Redis の行、ID の Cookie）は消す。<b>保存はしない</b>（F-S-02 のまま）ので、振り直したあと `save()` しなければ<b>ログインしていない状態に倒れる</b> | MUST |

### 7.6 Web：Validation / Paging

| ID | 要件 | 優先度 |
|---|---|---|
| F-V-01 | `ValidationRule` をコードで組み立てる。`empty` / `textLength` / `textByteLength` / `bool` / `integer` / `number` / `custom` を持つ | MUST |
| F-V-07 | 標準バリデータとして次を提供する：真偽値 / 文字種別 / 日時 / ドメイン / メールアドレス / 空文字 / enum / 整数 / 数値 / 正規表現 / 文字列長 / 文字列バイト長 / URL（`jooby_base` の 14 種を流用） | MUST |
| F-V-08 | ルールの組み合わせを再利用できる仕組み（`ValidationRules` 相当）を提供する | SHOULD |
| F-V-02 | INSERT 時のみ必須といった、リクエスト種別による分岐を表現できる | MUST |
| F-V-03 | 検証結果はエラーの一覧（項目名・メッセージ）として取得できる | MUST |
| F-V-04 | ルート単位でかける `ValidationExecutor` を提供し、失敗時に後続 UseCase をキャンセルする | MUST |
| F-V-05 | `Paging` はリクエストから `page` / `per` を読み、SELECT に適用でき、総件数と最大ページを返せる | MUST |
| F-V-06 | ページングのリクエストキー名は設定で変更できる | SHOULD |

### 7.7 DB：SQL ビルダー

`jooby_base` の実装をそのまま流用する（原則6）。要件として明記するのは、**仕様として固定すべき点**である。

| ID | 要件 | 優先度 |
|---|---|---|
| F-D-01 | `SQL.select()` / `insert()` / `update()` / `delete()` のビルダーを提供する | MUST |
| F-D-02 | SELECT の列には**常に** `` `テーブル名__列名` `` の別名を付け、結果 `Data` はテーブル名でネストする。単一テーブルでも例外を作らない | MUST |
| F-D-03 | 条件式は `Column` のメソッドで組む（`eq` / `not` / `gt` / `lt` / `ge` / `le` / `is_null` / `between` / `like` / `in` / `exists` 等、`and` / `or` で連結） | MUST |
| F-D-04 | SQL 関数リテラル（`Dsl`）を提供する（`now` / 集約関数 / 日時演算 / JSON 関数 / 地理関数 等） | MUST |
| F-D-05 | JOIN（`inner` / `left`）、`groupBy` / `having` / `orderBy` / `limit` / `offset` / `forUpdate` 系に対応する | MUST |
| F-D-06 | ビルダーは **DB 接続なしで** 生成される SQL 文字列とバインドパラメータを取り出せる（`sql()` / `params()`） | MUST |
| F-D-07 | `in()` / `not_in()` に空コレクション（または空配列）が渡された場合、**SQL を組み立てたところで `SqlBuildException`** にする。移送元は `IN ()` という構文エラーの SQL をそのまま DB に投げていたので、<b>DB のエラーメッセージから呼び出し箇所に辿り着けなかった</b>。**「空なら条件ごと外す」ことはしない**（`in(空)` は「どれにも当たらない」で、条件を外すと<b>全件</b>。取り違えると静かに全件消したり全件見せたりする） | SHOULD |
| F-D-08 | `executeBatch` は全ビルダーの SQL 一致を前提とし、不一致時はエラーコードを立てて `null` を返す | MUST |
| F-D-09 | 次も提供する：**CASE 式** / **仮テーブル・仮列**（サブクエリ結果への別名）/ **自由 SQL**（ビルダーで書けない SQL の逃げ道）/ **地理空間関数**（`ST_GeomFromText` / `ST_Distance_Sphere` / `ST_Within` / Point / Polygon）/ **全文検索の `MATCH` 構文** | MUST |

### 7.8 DB：実行と接続

| ID | 要件 | 優先度 |
|---|---|---|
| F-D-10 | `select` / `selectList` / `selectListWithRowCount` / `selectListWithFetcher` / `insert` / `update` / `delete` / `execute` / `executeBatch` / `insertBatch` を提供する | MUST |
| F-D-11 | **エラーは例外ではなく戻り値で返す**（select 系は `null`、更新系は `-1`）。エラー内容は `isError()` / `getError()` で取得する | MUST |
| F-D-12 | 大量件数はフェッチャ方式（`selectListWithFetcher`）で全件ロードを避けられる | MUST |
| F-D-13 | 参照用・書き込み用の接続を分けて取得でき、参照も書き込み接続に固定するモードを持つ | MUST |
| F-D-14 | 複数データソース（サブ DB）に接続できる | MUST |
| F-D-15 | トランザクションは明示的に `beginTransaction` / `commit` / `rollback` / `endTransaction` で操作する。暗黙のトランザクションを作らない | MUST |
| F-D-16 | Context クローズ時に、未コミットのトランザクションが残っていればロールバックし**エラーログを出す** | MUST |
| F-D-17 | 発行した SQL の実行回数と累計実行時間を Context に集計する | MUST |
| F-D-18 | スロークエリを閾値超過でログ出力できる（閾値は設定値） | SHOULD |
| F-D-19 | **sticky コネクション**（一度書き込み接続を使ったら以降の参照も同じ接続に固定する）を提供する。レプリケーション遅延対策として実運用で使われている | MUST |
| F-D-28 | **SQL 結果キャッシュ**：`selectCached` / `selectListCached` で結果をキャッシュし、**更新系 SQL が走ったら関係するキャッシュだけを消す**。判定には**主キーと一意キー**を使う。SELECT の依存は<b>結果行から</b>（結合先も含む）、更新の影響は<b>WHERE の内省から</b>求める。読めなければ安全側（そのテーブルに触るものを全部消す）に倒す。**キャッシュするのは明示的に頼んだときだけ、消すのは常に自動**。置き場は `sql_cache.store`（memory / redis / db）。トランザクション中は読み書きせず、コミットでまとめて消す。**アプリ全体の有効・無効を `sql_cache.enabled` で切り替えられ、既定は無効**（切っているときは更新側の処理が1行も走らないこと。D-95） | MUST（**済**。D-94 / D-95） |
| F-D-30 | **MySQL 以外の RDBMS への対応**：`db.xxx.product`（`mysql` / `mariadb` / `postgresql`）で SQL の方言を切り替える。**SQL ビルダーを使うアプリ側のコードは1行も変えない**（識別子の囲み・関数名・upsert・`INSERT IGNORE`・`INTERVAL`・JSON の取り出し・真偽値と JSON のバインド・採番値の取り出しを方言に寄せる）。**フレームワークの内部テーブルの DDL も製品ごとに持ち**、PostgreSQL でも起動して動くこと。**その製品に書けないものは、SQL を組み立てたところで例外にする**（実行してから製品の構文エラーで落ちない）。**MySQL 向けの出力は方言化の前と1バイトも変えない。****テーブル定義クラスの生成（codegen）も製品ごとに読む**（D-98） | MUST（**済**。D-96 / D-97 / D-98） |
| F-D-31 | **SQL DSL の充実**：文字列（`lower` / `substring` / `replace` / `lpad` / `locate` など）、数値（`abs` / `mod` / `power` / `greatest` など）、日付（`year` … `weekOfYear` / `dateAdd` / `dateDiff` / `unixTimestamp` など）、条件と型変換（`coalesce` / `nullif` / `ifThenElse` / `cast` / `regexp`）、集約（`countDistinct` / `stddev` / `groupConcat`）、ウィンドウ関数（`rowNumber` / `rank` / `lag` / `over` ＋ `PARTITION BY` / `ORDER BY` / `ROWS BETWEEN`）を提供する。**MySQL / PostgreSQL の両方で同じ答えを返すこと**（名前だけ違うものは吸収し、返る値まで揃える。片方に無いものは組み立て時に例外）。**同じ結合テストを `dbTest` と `pgTest` の両方で走らせて、同じ値が返ることを確かめる** | MUST（**済**。D-99） |
| F-D-19b | DB バージョン管理（`DBVersion` 相当）と DB ログ（`DBLog` 相当）を提供する | SHOULD |

### 7.9 DB：Data

| ID | 要件 | 優先度 |
|---|---|---|
| F-D-20 | `Data` は `LinkedHashMap<String, Object>` を継承し、挿入順を保持する | MUST |
| F-D-21 | 型付き getter を提供する（`getString` / `getLong` / `getInt` / `getBoolean` / `getBigDecimal` / `getDate` / `getData` / `getDataList` / `getEnum` 等） | MUST |
| F-D-22 | ほぼ全ての取得・設定 API に `String key` 版と `Column` 版を持つ。DB 由来の値は `Column` 版を使うことをドキュメントで徹底する | MUST |
| F-D-23 | `flattenTable(Table)` / `flattenTable()` / `extractTableData(Table)` を提供する | MUST |
| F-D-24 | `putData(Column, value)`（常にネスト）と `putDataTakeCare`（既存構造に従う）を区別して提供する | MUST |
| F-D-25 | JSON 入出力（`getJsonString` / `fromJsonString`）を提供する | MUST |
| F-D-26 | **`toString()` は要約表示**（キー数・型など）とし、全件シリアライズは行わない。JSON 化は `getJsonString()` に限定する | MUST |
| F-D-27 | `AsyncData` の `toString()` は未読み込みの枝を読み込まないこと（`isLoaded()` の結果を表示する） | MUST |

### 7.10 DB：マイグレーションとコード生成

| ID | 要件 | 優先度 |
|---|---|---|
| F-G-01 | DB スキーマからテーブル定義クラス（`static final Column` を持つ）を生成する | MUST |
| F-G-02 | 型付きアクセサ（`Abstract<Table>Data`）を生成する | MUST |
| F-G-03 | 生成物と手書きコードのディレクトリを分離し、生成物には「編集禁止」の明示をヘッダに入れる | MUST（**済**。ヘッダは D-88 で追加） |
| F-G-04 | 生成は CLI から実行でき、CI で「生成物が最新か」を検証できる | SHOULD |
| F-G-05 | マイグレーションは**自前で持つ**（Flyway 等の外部ツールに依存しない）。`jooby_base` の既存機構（`# --- !Ups` / `# --- !Downs` 形式、`migration` / `migration_history` テーブル）を流用する。**1文ずつの切り分けは、文字列・識別子・コメントの中の「;」では切らない。どこからどこまでがそれなのかは製品の決まりで読む**（D-114） | MUST |
| F-G-06 | マイグレーションの適用履歴を DB のテーブルで管理し、未適用分のみを順に適用する | MUST |
| F-G-07 | ローカルでは Gradle が **`migrate` → `codegen` → `compileJava`** の順に実行する。ローカル以外のビルドでは `compileJava` のみを行う（「7.10.1」） | MUST |
| F-G-08 | 同じ操作を CLI からも実行できる（CI・本番デプロイ用） | MUST |
| F-G-09 | 複数データソース（サブ DB）それぞれにマイグレーションを適用できる | SHOULD |
| F-G-10 | 適用済みマイグレーションの改変を検出して失敗させる（チェックサム） | SHOULD |
| F-G-11 | マイグレーションは**生 SQL** で記述し、**up と down の両方を持つ**。**down を実行するかどうかは設定で切り替えられる**（既定は実行しない） | MUST |
| F-G-12 | down の未記述を検出して警告する（適用は妨げない） | SHOULD |
| F-G-13 | **codegen の生成物はリポジトリにコミットする。**ローカル以外のビルドは DB に接続せずコンパイルできること | MUST |
| F-G-14 | CI で「スキーマから生成した結果」と「コミットされている生成物」が一致するかを検証する | MUST |
| F-G-15 | ローカル以外の環境では**アプリ起動時に未適用のマイグレーションを適用する**。排他制御は**ロックテーブル方式**（`db_lock` の行を `FOR UPDATE` で取る。要件の初版は「`migration` テーブルをロックする」と書いていたが、実装は共通の `db_lock` を使う）とし、複数インスタンスが同時に起動しても一度しか適用されないこと | MUST |
| F-G-16 | 起動時マイグレーションが失敗した場合はアプリを起動させない（中途半端な状態で受け付けない） | MUST |
| F-G-17 | 起動時マイグレーションを行うかどうかは設定で制御でき、環境判定に依存しない形でも指定できる | MUST |
| F-G-18 | SQL に加えて**コードマイグレーション**（Java クラスで書くマイグレーション）に対応する。`jooby_base` の `AbstractCodeMigration` 相当 | MUST |
| F-G-19 | ロック待ちのタイムアウトを設定でき、待ちきれなかったインスタンスの挙動（起動失敗）が定義されていること | MUST |
| F-G-20 | **マイグレーション SQL を製品ごとに分けられる**（`001_x.mysql.sql` / `001_x.postgresql.sql`）。接尾辞は `db.<名前>.product` に書ける名前と同じ表で判定し（`mariadb` は `mysql` に畳む）、**接尾辞の無いファイルはどの製品でも適用する**。適用済みはファイル名で覚えるので、製品ごとに別の記録になる。飛ばしたファイルはログに出す。**置いてあるファイルは、他の製品向けでも「消えた」とは見ない**（down しない）。**すでに適用済みのファイルを製品別の名前に変えたときは、同じ SQL が二度流れる前に止めて直し方（履歴の `UPDATE`。中身も変わっていれば `hash` も）を出す。**いまの製品向けのファイルが無くなったときも止める。同じ版がいまの製品で2つとも流れる場合も止める | MUST（**済**。D-113） |

#### 7.10.1 マイグレーション適用タイミング

| | ビルド時 | 実行時（起動時） |
|---|---|---|
| **ローカル** | `migrate` → `codegen` → `compileJava` | 何もしない |
| **ローカル以外**（CI / staging / production） | `compileJava` のみ | 未適用のマイグレーションを適用する |

この分担から導かれる制約：

- **codegen の生成物はリポジトリにコミットされている必要がある**（F-G-13）。
  そうしないとローカル以外でコンパイルできない。生成物とスキーマのずれは CI で検出する（F-G-14）
- **起動時マイグレーションには排他制御が必須**（F-G-15）。
  ローリングデプロイで複数インスタンスが同時に立ち上がるため。Redis 無しで動く必要があるので、
  ロックは DB 側の仕組みで取る
- ローカルでは起動時に何もしないので、**スキーマを変えたら Gradle を回す**という運用がドキュメントに要る

### 7.11 AsyncData（遅延読み込み）

| ID | 要件 | 優先度 |
|---|---|---|
| F-A-01 | `AsyncData`（1件）/ `AsyncList`（複数件）を提供し、**参照された時点で**初めて SQL を発行する | MUST |
| F-A-02 | 実装側の契約は `load()` / `setData()` / `setRelationData()` / `hashKey()` とする | MUST |
| F-A-03 | `AsyncList.setRelationData(List)` は全行の `setData` 完了後に**1回だけ**呼ばれる（一括処理の置き場） | MUST |
| F-A-04 | 読み込みを起こさない走査 API（`isLoaded()` / `loadedValues()` / `batchKey()` / `batchId()`）を提供する | MUST |
| F-A-05 | `hashCode()` / `equals()` は `hashKey()` に基づき、**比較しただけで SQL が飛ばない**こと | MUST |
| F-A-06 | 先読み（バッチローダー）を提供する：出力前にツリーを走査し、同種の未読み込みノードを `IN` 句1クエリで埋める。**まとめて引くコードはクラスに `loadBatch` として置く**（レジストリに登録しない。D-64） | **MUST（済）** |
| F-A-07 | 先読みは循環参照下でも停止すること。`(batchKey, batchId)` 単位の展開1回制限、周回の上限（`async.prefetch.max_depth`。既定 5）、**参照の同一性による走査済み判定**を併用する | **MUST（済）** |
| F-A-08 | 先読みの有無で**出力が完全に一致する**こと。CI で JSON 突き合わせによって検証する | **MUST（済）**（`AsyncPrefetchTest` / `AsyncIntegrationTest`） |
| F-A-09 | `load()` が失敗した場合、その事実を保持し `isLoadFailed()` で判定できる。**既定の挙動（例外を投げずログ出力して継続）は変えない** | MUST |
| F-A-11 | **JSON にするときにテーブルネストのあり／なしを選べる**（`getJsonString(TableNest)` / `response().tableNest(...)`）。どのキーがどのテーブルの列かは<b>生の読み込みデータから導く</b>ので、`setData` を `flattenTable` で書いても `extractTableData` で書いても<b>同じクラスから両方の形が出る</b>。`setRelationData` が足した子や計算値は常に最上位に残る。既定は `AS_IS`（現行の出力と1バイトも変わらない） | MUST（**済**。D-93） |
| F-A-10 | 先読みは Phase 2 に回すが、`batchKey()` / `batchId()` / `loadedValues()` など**先読みが必要とする API は Phase 1 の時点で用意しておく**（後から入れるときに既存実装を書き換えずに済むように） | MUST。**狙いどおりに効いた**：M10 で足したのは `loadBatch` と `AsyncPrefetch` だけで、既存の実装は1つも書き換えていない |

### 7.12 設定 / ログ / キャッシュ / ロック

| ID | 要件 | 優先度 |
|---|---|---|
| F-U-01 | 設定は環境別ファイル（`application.conf` + `application.<env>.conf`）で管理し、環境変数で上書きできる | MUST |
| F-U-01c | 環境は **`-Djimble.env`**（`-Denv` も可） &gt; 環境変数 `ENV` &gt; `local` の順で決まる（D-78） | MUST |
| F-U-01b | **設定はクラスパスから1ファイルだけ読む**（jar の外は見ない）。`application.<env>.conf` があればそれ、無ければ `application.conf`。**共通を足すのは環境別ファイルの `include "application.conf"`**（フレームワークは裏で重ねない）。**実際に読んだファイルを起動ログに出す**（D-80 / D-81。D-71 を撤回） | MUST |
| F-U-01d | 環境別ファイルに `include` を書き忘れて共通の設定が落ちていたら、**落ちたキーを名指しで起動ログに出す**（`Conf.missingFromEnvFile()`。葉まで比べ、トップレベル名にまとめる）（D-81） | MUST |
| F-U-02 | 設定取得は**既定値付きの形を推奨**とし、既定値なし版は起動時検証で不足を検出できるようにする | MUST |
| F-U-03 | 起動時に必須設定の欠落をまとめて報告して停止する（1つずつ落ちない） | SHOULD |
| F-U-04 | ログは `error` / `warn` / `info` / `debug` / `trace` と、用途別ロガー（app / error / access 等）を提供する | MUST |
| F-U-05 | アクセスログに実行時間・SQL 実行回数・SQL 実行時間・実行 ID を必ず含める | MUST |
| F-U-06 | ログ出力は構造化（JSON）形式を選べる（logback の JSON エンコーダを同梱）。**雛形が `conf/logback.xml` を作り、`access` / `access.bot` / `error` の振り分けが最初から効く**（D-84） | SHOULD |
| F-U-07 | キャッシュはインターフェース（**DB / メモリ / Redis**）で差し替えられ、ローディングキャッシュ（単一キー / 複数キー）を提供する | MUST |
| F-U-08 | キャッシュはグループ単位の一括無効化に対応する | SHOULD |
| F-U-09 | Redis による分散ロック（`lock` / `tryLock` と待機・保持時間の指定）を提供する | MUST |
| F-U-10 | **Redis 未設定でもアプリが起動できること。**Redis 前提の機能（分散ロック等）を Redis 無しで呼んだ場合は、黙って成功させず明確な例外を投げる | MUST |
| F-U-11 | 起動時に、有効になっている実装（セッション保存先・キャッシュ実装・Redis の有無）をログに出す | SHOULD |
| F-U-12 | **標準出力・標準エラーの文字コードを UTF-8 に固定する。**（Java 18+ でも `System.out` はコンソールの文字コードに従うため、`LANG` 未設定のコンテナで日本語ログが化ける） | MUST |

### 7.13 バッチ

| ID | 要件 | 優先度 |
|---|---|---|
| F-B-01 | バッチは基底クラスを継承し、`batchName()` と `execute(BatchArgs)` を実装する | MUST |
| F-B-02 | バッチ実行ごとに `BatchContext` を生成し、Web と同じ DB / ログ / 設定 API を使える | MUST |
| F-B-03 | CLI 引数を `Data` として受け取れる | MUST |
| F-B-04 | cron 式によるスケジューラ実行に対応する | MUST |
| F-B-05 | 同時実行数の上限を宣言でき、超過時の挙動が定義されていること | MUST |
| F-B-06 | 長時間バッチは中断指示を検知して安全に止められる（`isCancelOrder()` 相当） | MUST |
| F-B-07 | 全バッチの一斉停止フラグを持つ | SHOULD |
| F-B-08 | 実行履歴（開始・終了・結果・例外）を記録する | SHOULD |
| F-B-09 | **バッチの登録は明示的に行う。**パッケージ名を渡してクラスパスを走査する自動登録は行わない（原則1・原則2） | MUST |
| F-B-10 | DB ベースのスケジューラ（`DBScheduler` 相当）を提供する。Redis に依存しないこと | MUST |
| F-B-11 | バッチの一覧・実行状況を確認する管理画面を提供する（Basic 認証つき、設定で無効化できる） | SHOULD |
| F-B-12 | **一定件数ずつ読んでまとめて書くバッチ**（チャンクモデル）を提供する。1かたまりを1トランザクションで確定し、途中で落ちたときは**そのかたまりだけ戻して全体を失敗にする**。かたまりの切れ目で中断を見る。進み具合を実行履歴に定期的に書く | SHOULD |

### 7.14 MQ

| ID | 要件 | 優先度 |
|---|---|---|
| F-M-01 | メッセージ1件の処理ごとに `MqContext` を生成する | MUST |
| F-M-02 | Executor は `put()` で登録し、`execute(DB, Data)` でステータスを返す形とする | MUST |
| F-M-03 | **DB をキューとする方式**をサポートする（`put()` をトランザクション内で呼べば、ロールバックでキューも消える） | MUST |
| F-M-04 | リトライ回数・リトライ間隔・最大試行超過時の扱い（デッドレター）を定義する | MUST |
| F-M-05 | 同一メッセージが二重処理されうる前提を明示し、冪等性の担保方法をドキュメントに書く | MUST |
| F-M-06 | **DB キュー方式のみを実装する。**差し替え用の抽象インターフェースは作らない（原則4：実際に2つ目の実装が要るまで抽象化しない） | MUST |
| F-M-07 | **MQ Executor の登録は明示的に行う。**パッケージスキャンによる自動登録は行わない（F-B-09 と同じ理由） | MUST |
| F-M-08 | 処理時間の想定に応じた**実行種別**を持ち、種別ごとに同時実行スレッド数を設定できる（`MQExecuteType` 相当） | MUST |

### 7.15 開発者体験（DX）

| ID | 要件 | 優先度 |
|---|---|---|
| F-X-01 | CLI でプロジェクト雛形を生成できる（`jimble new`）。**生成したものは手を入れずにビルドして起動できること**（DB も Redis も要らない形で出す。D-49） | SHOULD |
| F-X-02 | ローカル開発でホットリロードができる。**`io.jimble.run` プラグイン（`jimbleRun` タスク）**がソースを見張り、**リクエストが来たときに** `classes`（＝ `codegen` と `generateJte` を含む）を流してから**アプリを入れ替える**（Gradle と<b>同じ JVM</b>で、クラスローダを作り直す。D-77）。ビルドに失敗したらブラウザにその内容を出す（D-47） | SHOULD |
| F-X-03 | 起動時間はローカルで 2 秒以内を目標とする | SHOULD |
| F-X-04 | 例外時のスタックトレースに、jimble 内部フレームが過剰に混ざらない（アプリのフレームが読み取れる） | MUST |
| F-X-05 | エラーメッセージは「何が起きたか」と「どう直すか」をセットで書く | MUST |
| F-X-06 | テスト用ユーティリティを提供する：HTTP サーバーなしで Context を作る、偽 DB を差す、ルートを直接叩く | MUST |
| F-X-07 | 動的レスポンスは既定で `Cache-Control: no-store`。静的配信は別扱いとする | MUST |

---

### 7.16 共通ユーティリティ

`jooby_base` の `lib.base.util.common.*` 相当。**約 18,000 行**あり、アプリが当たり前に使っているため、
無いと移植できない。**原則としてそのまま流用する**（jooby に依存していない）。

| ID | 領域 | 内容 | 規模の目安 |
|---|---|---|---|
| F-Y-01 | 型変換フレームワーク | `Convertor` + 約 60 の型別コンバータ + プロパティユーティリティ | 約 4,000 行 |
| F-Y-02 | JSON（`Dson`） | エンコーダ / デコーダ / フォーマッタ / ストリーム出力 | 約 3,000 行 |
| F-Y-03 | 文字列 | 文字列操作 / 正規化 / 正規表現パターン集 / 汎用パース | 約 1,500 行 |
| F-Y-04 | 日時 | 日時ユーティリティ | 約 900 行 |
| F-Y-05 | URL | URL ユーティリティ / URL ビルダー | 約 700 行 |
| F-Y-06 | HTTP クライアント | GET / POST / PUT / PATCH / DELETE / HEAD / OPTIONS / multipart / プロキシ | 約 2,500 行 |
| F-Y-07 | ファイル / IO | ファイル操作 / 文字コード判定 / テキスト出力 | 約 900 行 |
| F-Y-08 | CSV | 読み込み / 書き込み | 約 800 行 |
| F-Y-09 | XML | パース / データ構造 / ビルダー | 約 800 行 |
| F-Y-10 | ハッシュ・暗号 | 汎用ハッシュ / Hashids / XXHash / 共通鍵暗号 / パスワードハッシュ | 約 800 行 |
| F-Y-11 | スレッド | スレッド管理 / **仮想スレッド本数制御** / sleep 管理 | 約 300 行 |
| F-Y-12 | ネットワーク | IP 判定（v4 / v6）/ ホスト名 / ローカルアドレス | 約 500 行 |
| F-Y-13 | その他 | Map / List / 数値 / ストップウォッチ / システム情報 / 幾何 / 各種 Comparator | 約 900 行 |
| F-Y-14 | 例外 | **コード付き例外**（エラー処理規約の中核） | 66 行 |
| F-Y-15 | DB key-value | DB に置く key-value ストア | 543 行 |

**要件としての方針：**

- **すべて流用する。**この量を書き直す選択肢はない
- 公開 API として整理するのは Phase 2。Phase 1 は動くことを優先する
- ただし **`io.jooby` に触れている 2 ファイル**（`Convertor` / `FilePartConvertor`。参照は各 1〜2 行）は
  ファイルアップロードの型が jooby 依存なので、helidon の型に差し替える

### 7.17 HTTP サーバー設定

| ID | 要件 | 優先度 |
|---|---|---|
| F-H-01 | 待ち受けポート、リクエスト最大サイズ、アイドルタイムアウトを設定できる | MUST |
| F-H-02 | プロキシヘッダを信用するかを設定でき、信用する場合に `proxyAddress()` が正しい値を返す | MUST |
| F-H-03 | レスポンスの gzip 圧縮レベルを設定できる（無効化を含む） | SHOULD |
| F-H-04 | コネクションプール実装を設定で切り替えられる | SHOULD |
| F-H-05 | ボットからのアクセスを判定でき（`isBotAccess()` 相当）、アクセスログを通常とボットで分けて出力できる | SHOULD |
| F-H-06 | **待ち受けるアドレスを設定できる。**ローカルで動かす MCP サーバーは 127.0.0.1 だけに bind することが求められる（MCP 仕様 SHOULD） | SHOULD（**済**。`server.host`。D-86） |

### 7.18 MCP（Model Context Protocol）

対応する仕様は **2026-07-28 の1版のみ**（D-54）。トランスポートは **Streamable HTTP** と **stdio**（D-127）。

| ID | 要件 | 優先度 |
|---|---|---|
| F-MCP-01 | `McpController` を継承し、**初期化ブロックに明示的に**ツール・リソース・プロンプトを登録する。アノテーションもクラスパス走査も使わない（原則1・原則2 / NF-P-03） | MUST |
| F-MCP-02 | 公開する口は**1本の POST**（既定 `/mcp`）。**GET と DELETE は 405** で断る（2026-07-28 でセッションと GET ストリームが仕様から消えた） | MUST |
| F-MCP-03 | `tools/list` / `tools/call` / `resources/list` / `resources/read` / `prompts/list` / `prompts/get` に応える。一覧は**登録した順**で返す（クライアントのキャッシュのため） | MUST |
| F-MCP-04 | **`Origin` を検証する。**付いていて許可外なら 403（DNS リバインディング対策。仕様 MUST） | MUST |
| F-MCP-05 | **ヘッダと本文を突き合わせる。**`MCP-Protocol-Version` / `Mcp-Method` / `Mcp-Name` / `Mcp-Param-*` が本文と食い違えば 400 と `-32020`。Base64 センチネル（`=?base64?...?=`）を解いてから比べる | MUST |
| F-MCP-06 | 対応しない版には **400 と、対応している版の一覧**を返す | MUST |
| F-MCP-07 | 通知（`id` 無し）には **202 を本文なし**で返す。知らないメソッドには **404 と `-32601`** | MUST |
| F-MCP-08 | **2種類のエラーを混ぜない。**要求の形が悪いものは JSON-RPC の `error`、ツールの中の失敗は結果の `isError: true`。後者は<b>モデルが読んで直せる</b>内容にする | MUST |
| F-MCP-09 | ツールの `inputSchema` は JSON Schema。<b>引数が無くても null にしない</b>（`{"type":"object","additionalProperties":false}`）。ビルダーを自前で持ち、外部ライブラリを足さない | MUST |
| F-MCP-10 | ツールは**呼ばれるたびに1つ作る**（`Supplier`。原則3。MQ の D-36 と同じ） | MUST |
| F-MCP-11 | ツールが例外を投げても、**中身を外に出さない。**ログには残す | MUST |
| F-MCP-15 | **実装済みの API（Handler）をそのままツールにできる**（`RouteTool.of(method, path)`）。API を先に作り、あとから MCP でも出すときに<b>ハンドラを2度書かない</b>。ルートの `before` / `after`（認証・セッション）は効く。引数はパス変数 → クエリ（GET / DELETE / HEAD）または JSON の本文（それ以外）に割り当てる。2xx の JSON は構造化出力に、**4xx は本文ごと、5xx は本文を伏せて** `isError` にする（F-MCP-11） | MUST（**済**。D-92） |
| F-MCP-12 | **stdio トランスポート。**クライアントがプロセスを起こし、標準入出力で会話する。**1行に1つの JSON-RPC メッセージ**（改行を中に含めない）。**標準出力には MCP のメッセージ以外を書かない**（仕様 MUST）ので、`System.out` を stderr へ差し替える。出力は端末の文字コードに関係なく **UTF-8**。標準入力が閉じたら終わる。HTTP サーバーは立てないが**ルート表は組む**ので `RouteTool`（F-MCP-15）はそのまま効く | MUST（**済**。D-127） |
| F-MCP-13 | **`subscriptions/listen`。**開いたら**まず `notifications/subscriptions/acknowledged`**（仕様 MUST）。**頼まれていない種類は流さない**（仕様 MUST）。jimble が流せるのは `resources/updated` と `resources/list_changed` の2つだけ（ツールとプロンプトは起動時に決まるので変わりようが無い）。サーバー側から終わるときは**元の要求への応答を返してから閉じる**。`notifications/cancelled` で取り消されたときは**応答を返さない**（仕様 MUST NOT）。アプリからの入口は `McpNotify` | MUST（**済**。D-127） |
| F-MCP-14 | **一覧のページ分け。**`params.cursor` で受け、`result.nextCursor` で返す。カーソルは**不透明**。**読めないカーソルは `-32602` で断る**（黙って先頭に倒さない）。1ページの件数はサーバーが決める（既定 100・`mcp.page_size`）。`tools/list` / `resources/list` / `prompts/list` に効く | MUST（**済**。D-127） |
| F-MCP-16 | **`server/discover` に応える**（仕様 MUST）。`supportedVersions` / `capabilities` / `_meta` の `serverInfo` を返す。<b>版のヘッダを要求しない</b>——版を知るための呼び出しに版が要ると、初めて繋ぐクライアントは何もできない | MUST（**済**。D-127） |

---

## 8. 処理フロー（確認用）

### 8.1 Web リクエスト

`jooby_base` の `requestSupplier` を精読して確定した仕様（`docs/extends-jooby-analysis.md`）。

```
helidon-webserver
  └ routing.any()                        全リクエストを1点で受ける
      └ jimble Dispatcher
          1. Context 生成（実行ID発行 / DB 取得 / 開始時刻記録）
             ※ セッションなしのリクエストでは cookie id / session id / CSRF を発行しない
          2. Router マッチング（メソッド + パス → ルート）
             └ 未マッチ → 404 ハンドラ
          3. ScopedValue 5点セット（Context / リクエストスコープキャッシュ /
             ログ / 操作情報 / sticky コネクション）を束ねて以下を実行
             a. Bot ブロック適用
             b. CORS 適用
             c. before（App レベル → 外側スコープ → 内側スコープ）
             d. ルートのハンドラ実行（Executor をキューに積む）
             e. Executor ループ:
                  キューから取り出して実行
                  キャンセルされたら → 残りを破棄 → キャンセル処理 → 送信して終了
             f. レスポンス送信
             ※ a〜f の各段で「送信済みなら打ち切る」
             例外時: ログ出力 → エラーハンドラ（既定は 500）
             finally: after（内側スコープ → 外側スコープ → App レベル）
          4. Context クローズ
             ├ 未コミットトランザクションのロールバック + エラーログ
             ├ DB 接続返却
             └ アクセスログ出力（実行時間 / SQL 回数 / SQL 時間）
```

**OPTIONS だけは別経路**（プリフライト専用。Executor ループを回さない）。

**`before` / `after` は2系統ある。**
App レベル（アプリ全体に1つ、protected メソッドで上書き）と、
ルートレベル（`before()` / `after()` の DSL、スコープ配下に適用）。

### 8.2 バッチ / MQ

```
Batch main / MQ ワーカー
  └ BatchContext / MqContext 生成      ← Web と同じ生成・クローズ規約
      └ 処理本体（Web の UseCase と同じ Domain 層を呼べる）
          └ Context クローズ（Web と同一の後始末）
```

**Web / バッチ / MQ の3経路で、Context の生成・クローズ規約を共通化する**ことが設計上の要である。
これにより「Web からもバッチからも呼べる Domain 層」が自然に書ける。

---

## 9. 非機能要件

### 9.1 性能・並行性

| ID | 要件 |
|---|---|
| NF-P-01 | リクエストごとに仮想スレッドを使う。ブロッキング JDBC を前提に、**接続プールがボトルネックになる設計**であることを明記する |
| NF-P-02 | 仮想スレッド上では `synchronized` によるピン留めを避け、ロックは `ReentrantLock` を使う（`AsyncData` の読み込みロック含む） |
| NF-P-03 | 起動時のリフレクションスキャンを行わない。起動時間はアプリ規模に対してほぼ一定であること |
| NF-P-04 | ルーティングのマッチングコストはパス深さに対して線形以下であること。**済**（D-125）。`RouterBench` が深さ 2 / 4 / 8 で 448 / 544 / 736 byte を測り、`bench` タスクで見張っている |
| NF-P-05 | 1リクエストあたりのフレームワーク由来のオブジェクト生成を最小化する（Context / Request / Response 以外に大きな割り当てを作らない）。**済**（D-125）。`RequestBench` が 1リクエスト 約 8,100 byte を測り、10,240 byte を超えたら落とす。**内訳は段ごとに積んだ差で出す**ので、<b>名前の付いていない残りが出ない</b>（D-131）：足場 504／Context の生成 1,168／ルーティング 455／ハンドラの枠 304／後始末 5,724。**いちばん大きいのはアクセスログ**（約 3,400 byte ＝ 4割。要件 NF-O-02。**既定で出る**が `server.access_log = false` で切れる。D-130）。**ベンチは既定のまま測る**——切った状態を基準にすると、<b>既定のまま動いているアプリの費用が誰にも見えなくなる</b> |
| NF-P-06 | ベンチマークをリポジトリに含め、CI で性能退行を検出する。**済**（D-125）。`@Tag("bench")` ＋ `./gradlew bench`、CI の `build` ジョブで毎回流す。**当初の「ベースライン比 -10% で警告」は採らなかった**：共用ランナーの時間は 20〜30% ぶれるので<b>直していないのに赤くなる</b>。かわりに<b>1回あたりに割り当てた byte 数</b>で落とす（同じコードなら機械が変わっても同じ値になる）。時間は `build/bench/bench.txt` に記録するだけ。これで D-6 が決まった |
| NF-P-07 | **長く生きる接続（SSE / WebSocket）は必ず有限で終わること。**相手が切ったことは検知できず、書き込みが止まったまま帰ってこないことがある（Java のソケットに書き込みタイムアウトが無い）。寿命の上限で終わらせる。トランザクションやフェッチャを開いたまま張らない（接続プールを食う） |
| NF-P-08 | **負荷をかけて秒あたりの本数とレイテンシを測れること。**サーバーと負荷生成は<b>別のプロセス</b>で動かす（同じプロセスだと負荷側が相手の CPU を奪い、相手が遅いのか自分が邪魔しているのか分からなくなる）。**素の helidon と並べて測る**——差がそのまま jimble の上乗せ分になる。**アクセスログを切った版（`jimble-nolog`）も並べる**：素の helidon は1行も書かないので、これが無いと<b>「上乗せ分」と「helidon が持っていない機能の代金」が混ざる</b>（D-130）。**2xx 以外は成功に混ぜない**（500 は速いので、壊れているときほど良い数字が出る）。**CI では回さない**（D-125。共用ランナーの時間は 20〜30% ぶれる）。`jimble-load` は公開しない。**実測**（10 コアの Mac・8本）：helidon 59,075 / アクセスログ無し 57,030 / 既定 52,537 rps。<b>コア数を超える接続数の行は読まない</b>（負荷生成側の限界を測ってしまう） | SHOULD（**済**。D-129 / D-130） |

### 9.2 可観測性

| ID | 要件 |
|---|---|
| NF-O-01 | すべての Context に一意な実行 ID を持たせ、ログの全行に含める |
| NF-O-02 | アクセスログに SQL 実行回数・SQL 実行時間を必ず含める（N+1 の一次診断がログだけでできること）。**`server.access_log = false` で切れる**（既定 `true`。D-130）。1リクエストの割り当ての約4割（約 3,400 byte）がここで、切ると秒あたりの本数が1割ほど変わる。**切ってもメトリクス（NF-O-04）とトレース（NF-O-05）は残る**——速くするつもりで監視まで消さないため |
| NF-O-03 | ヘルスチェックのルートは<b>フレームワークでは用意しない</b>（D-106）。アプリが `get("/health_check", ...)` で書く。**グレースフルシャットダウン（D-91）で「先にヘルスチェックだけ落として、処理中のリクエストは返しきる」ができる**ためである。フレームワークが握ると、落とす順番をアプリから決められない。**書き方はドキュメントに載せる**（`server.md` の「ヘルスチェックを先に落とす」／`routing.md`） |
| NF-O-04 | メトリクス（リクエスト数・レイテンシ・接続プール使用率・キュー滞留数）を取得できる。**済**（D-124）。`Metrics.snapshot()` が値を返すだけで、**`/metrics` のルートは生やさない**（NF-O-03 と同じ理由）。リクエスト数とレイテンシは `WebContext` が、接続プールは `DBUtil` が、キューは `MqQueue` が入れる |
| NF-O-05 | OpenTelemetry へトレースを出力する口を用意する。**済**（D-126）。`jimble-core` が持つのは口（`Tracer` / `Span` / `Tracing`）だけで、OpenTelemetry の実体は**別モジュール `jimble-otel`**。登録しないあいだの費用は**1回あたり 0 byte**（測ってある）。HTTP・SQL・MQ・バッチに自動でスパンが付き、`traceparent` は受け取りも送出も自動 |

### 9.3 セキュリティ

| ID | 要件 |
|---|---|
| NF-S-01 | SQL は必ずバインドパラメータで組む。文字列連結で値を埋める API を公開しない |
| NF-S-02 | テンプレートは既定で HTML エスケープし、エスケープ解除は明示的な記述を要する |
| NF-S-03 | CSRF 対策を標準で提供する |
| NF-S-04 | Cookie の `Secure` / `HttpOnly` / `SameSite` の既定値を安全側に置く |
| NF-S-05 | リクエストボディ・アップロードのサイズ上限、ヘッダ数上限を既定で設ける |
| NF-S-06 | エラーレスポンスに内部情報（スタックトレース・SQL）を含めない。本番環境で自動的に抑止する。**既定のエラー応答は済**（D-11。本番かどうかに関係なく載せない）。<b>アプリが `error` に自分で `cause.getMessage()` を書く場合は未対応</b>（`Throwable` をそのまま `json(...)` に入れるとスタックトレースが全部出る） |
| NF-S-07 | 依存ライブラリの脆弱性スキャンを CI に組み込む。**済**（D-108）。**自分でスキャナを持たない。**Gradle が解決した依存の一覧を GitHub の Dependency graph に登録し（`.github/workflows/dependency-submission.yml`）、公開リポジトリでは無料の Dependabot alerts に当てる。版が上がったことは `.github/dependabot.yml`（週1）で拾う。**リポジトリの設定で Dependency graph を有効にしておくこと**（Settings → Advanced Security → Dependency graph → Enable）。無効のままだと、一覧は作れているのに登録だけが `The Dependency graph is disabled for this repository` で落ちる（<b>ワークフローの側は直しようがない</b>） |
| NF-S-08 | セキュリティ問題の報告窓口と対応方針を定める（Phase 2 の公開要件）。**済**（D-144）。GitHub の非公開報告（private vulnerability reporting）1本。`SECURITY.md` と jimble.io の[セキュリティ](https://jimble.io/ja/security)、README の1行 |
| NF-S-09 | **署名・暗号の鍵を、止めずに入れ替えられる。**書くのは常に `secret`、読むときだけ `previous_secrets` も順に試す。**古い鍵で読めたらその場で新しい鍵で書き直す**（書き直さないと入れ替えが終わらない）。**古い鍵で読めた回数をメトリクスに出す**（`cookie.stale_secret` / `session.stale_secret`。これが無いと古い鍵をいつ捨ててよいか分からない）。鍵が無いときは起動時に警告する。**対象は Cookie に載るものだけ**（`cookie.secret` / `session.secret`）で、DB に残る `cipher.key` / `hash.password.pepper` は対象外（D-128） |

### 9.4 互換性・移行性

| ID | 要件 |
|---|---|
| NF-C-01 | `jooby_base` から流用する API は原則シグネチャを変えない。変更点は移行ガイドに一覧化する |
| NF-C-02 | 将来 `jooby_base` からの移行を行う際に「Web 層だけ書き換えれば動く」状態になるよう、Domain 層・DB 層から見える API 名を維持する |
| NF-C-03 | Phase 2 以降、公開 API はセマンティックバージョニングに従う。破壊的変更は非推奨期間を1マイナーバージョン以上置く。**済**（D-145）。<b>0.x のあいだはマイナーで壊れうる</b>（SemVer の定義どおり）と明記し、<b>1.0 から非推奨期間を発効</b>させる。0.x でも「壊れうる変更は CHANGELOG の『変わったこと（挙動）』に必ず出す」は今から守る。<b>シグネチャを変えない挙動の変更</b>は「壊れていたのを直した（すぐ直す）」と「仕様を変えた（1.0 以降は1マイナー残す）」に分ける。`docs/site/{ja,en}/versioning.md` |
| NF-C-04 | 公開 API と内部実装をパッケージレベルで分離する（内部パッケージであることが名前で分かること）。**線引きは済**（D-145。`docs/api-packages.txt` に 154 パッケージを公開 101 / 内部 49 / 空 4 で分類し、`ApiSurfaceTest` が古くなるのを塞ぐ）。<b>`internal` へのクラス移動はしていない</b>——0.3.0 を出した直後にやると<b>いま使っている人の import が全部壊れる</b>。名前で分かるようにする部分は 1.0 のときに再検討する |

### 9.5 テスト

| ID | 要件 |
|---|---|
| NF-T-01 | SQL ビルダーは DB 接続なしで、生成 SQL とパラメータを突き合わせるテストを持つ |
| NF-T-02 | Router はルート表とマッチング結果の表駆動テストを持つ（`before` / `after` / `error` の実行順を含む） |
| NF-T-03 | Context のライフサイクル（クローズ漏れ・トランザクション残留）を検証するテストを持つ |
| NF-T-04 | `AsyncData` の先読みは「出力一致」「クエリ本数」「循環参照下での停止」の3点を検証する。**済**（`AsyncPrefetchTest` 15 件 + `AsyncIntegrationTest`） |
| NF-T-05 | 結合テストは**開発用の実 MySQL / Redis に接続して**実行する（D-16）。`@Tag("db")` を付け、`dbTest` タスクで動かす。**同じテストを PostgreSQL に対しても走らせる**（`pgTest`。設定ファイルだけが違い、テストのコードは1行も変えない。F-D-30）。**DB を持たない環境でも `build` が通ること** |
| NF-T-06 | サンプルアプリは CI でビルド・起動・疎通まで確認する（ドキュメントの陳腐化防止） |
| NF-T-07 | Gradle の `migrate` → `codegen` → `compileJava` の連鎖が CI で検証されていること |

### 9.6 ドキュメント（Phase 2 の中核）

| ID | 要件 | 状態 |
|---|---|---|
| NF-D-01 | jimble.io にドキュメントサイトを公開する。ソースはモノリポの `docs/` に置く | **済。**<https://jimble.io>（Cloudflare Workers 静的アセット / <https://github.com/hidemikimura/jimble-document>）。生成は本体の `./gradlew :jimble-docs:site`。**反映も自動化した**（D-117。タグを打つと GitHub Actions が作って向こうの `dist/` を置き換える）。`docs/design-m9.md` 10章 |
| NF-D-02 | 「5分で最初のエンドポイント」から始まるチュートリアルを持つ | **済**（`docs/site/ja/quickstart.md`） |
| NF-D-03 | ドキュメント中のコード片は `examples/` の実コードから引用し、コンパイル可能であること | **済**（`// docs:begin <名前>` の印で抜く。**印が消えたらサイトのビルドが落ちる。**サンプルとテストの両方から抜く） |
| NF-D-04 | 落とし穴（SELECT のテーブル名ネスト、DB エラーが戻り値で返ること、`in()` の空リスト、N+1 など）を専用ページにまとめる | **済**（`docs/site/ja/pitfalls.md`。21 件） |
| NF-D-05 | jooby からの移行ガイドを持つ | **済**（`docs/site/ja/migration-jooby.md`） |
| NF-D-07 | **機能ごとに1ページを持つ**（jooby.io と同じ粒度）。とくに<b>テスト / エラー処理 / 実行モデル / 静的ファイルと SPA</b> は、他ページから参照される土台なので独立させる | **済**（`testing.md` / `errors.md` / `execution.md` / `assets.md` / `validation.md` / `upload.md` / `log.md` / `cache.md` / `server.md` / `util.md` / `codegen.md` / `gradle.md`。**34 ページ**。jooby.io で見た穴はすべて埋めた） |
| NF-D-08 | ドキュメントの読み口を揃える：**注記の枠**（`> [!NOTE]` / `[!TIP]` / `[!WARN]` / `[!TRAP]`）、**前後のページのリンク**、**コードのコピー**。外部の JS には依存しない | **済**（D-82） |
| NF-D-06 | 日本語・英語の両方で公開する（Phase 2） | **済。日本語 35 ページ・英語 35 ページ。**ページの中身だけでなく、<b>枠の文言（まとまり名・注記の見出し・検索欄・前後のリンク・フッタ）も言語ごと</b>に持つ（`Texts`。D-100） |

**作りの要点**（`docs/design-m9.md` 8章）：

- **jimble 自身で作る。**jte でテンプレートを組み、`Data` をモデルに渡す。
  ドキュメントサイトそのものが jimble の実例になる
- **外部の CDN に繋がない。**CSS も JS もフォントも自前。検索は索引を配って素の JavaScript で絞る
- 追加の依存は commonmark-java（BSD-2-Clause）だけで、**ドキュメントを作るためだけに使う**。
  ~~アプリの実行時クラスパスには入らない~~ → **tika 4.0.0 から入るようになった**（D-115）。
  tika-core 4 が既定の出力を Markdown にしたため、commonmark が tika-core の依存になっている

### 9.7 ライセンス・公開

| ID | 要件 |
|---|---|
| NF-L-01 | 依存ライブラリは公開ライセンスに適合するもののみとする（helidon は Apache-2.0） |
| NF-L-02 | jimble のライセンスは **Apache-2.0** とする |
| NF-L-03 | フレームワークのコードに自社固有の業務ロジック・命名を含めない |
| NF-L-04 | Maven Central への公開手順を Phase 2 までに確立する。**済。0.1.0 を公開した**（2026-09-07）。**0.2.0 も公開した**（2026-09-08）。**0.3.0 も公開した**（2026-09-11）。**0.4.0 も公開した**（同日）。`centralBundle` → `centralUpload` → `centralStatus` → `centralRelease`。手順と落とし穴は `docs/publishing.md`、版ごとの中身は `CHANGELOG.md` |

**公開の作り**（`docs/design-m9.md` 9章 / D-60）：

- 公開先は **Central Portal**（`central.sonatype.com`）。旧 OSSRH は使わない
- Sonatype に公式の Gradle プラグインが無いので、**Portal の REST API を直に叩く**。
  外部プラグインを足さない。HTTP は JDK の `HttpClient`（D-24 と同じ）
- **鍵もトークンも環境変数だけ。**リポジトリにもファイルにも置かない。
  鍵が無い環境では署名を飛ばしてビルドが通る
- 版は `-Pjimble.version` で明示する。既定は `-SNAPSHOT`
  （Central は一度公開したものを消せないため、うっかり出さない側に倒す）
- 出すのは **13 アーティファクト**（ライブラリ 9 + CLI + Gradle プラグイン本体 + マーカー 3）。
  `jimble-docs` と `examples` は出さない

---

## 10. jooby_base からのコード流用と機能カバレッジ

Phase 1 では既存アプリの移行を行わない代わりに、**`jooby_base` の機能を可能な限り網羅すること**が完成条件になる。
本章はそのための対応表である。

### 10.1 移行規模（実測）

`jooby_base` 全 375 ファイル / 61,177 行に対する内訳。詳細は `docs/jooby-base-inventory.md`。

| 区分 | 規模 | 内容 |
|---|---|---|
| **新規実装** | 18 ファイル中 9 ファイル / 約 4,700 行 | `lib/jooby/*`（ルーティング DSL 本体 2,021 行 / Context 716 行 / 非 Web 用ダミー Context 607 行 / Jetty 起動 389 行 / Cookie 生成 / route ラッパー / エラーハンドラ）＋ バッチ管理画面 548 行 |
| **薄い改修** | 10 ファイル / jooby 参照は計 38 行 | `WebResponse` / `WebRequest` / `BatchExecutor` / `Conf` / `WebCookie` / `WebFlash` / `RateLimitExecutor` / `ReverseProxy` / `Convertor` / `FilePartConvertor` |
| **結線** | 1,619 行 | 既存 `lib/base/web/router` に Context と Executor をつなぐ |
| **そのまま流用** | 約 56,000 行 | 上記以外すべて |

### 10.1.1 モジュール別の方針

| モジュール | 方針 | 内容 |
|---|---|---|
| SQL ビルダー / `DB` / トランザクション / `Data` | **そのまま流用** | jooby 非依存。パッケージを `io.jimble.*` へ。`Data.toString()` のみ変更（F-D-26） |
| **Router（`lib/base/web/router`）** | **流用 + 結線** | **jooby 非依存の実装が既にある。**小文字 DSL・`trace` まで実装済み。ハンドラを `Runnable` から Context つき Executor に置き換える |
| バリデーション（14 バリデータ + `ValidationRules`） | **そのまま流用** | |
| セッション（DB / Redis / Cookie / なし） | **そのまま流用** | **Cookie セッションも既存だった**（`web/cookie/session/CookieSessionStore`）。Cookie 発行部分のみ jooby 依存 |
| キャッシュ（DB / メモリ / Redis）/ ロック / `Conf` / `Log` | **そのまま流用** | 構造化ログ（JSON エンコーダ）も既存。設定キーの接頭辞を `jimble.*` へ |
| CORS / Basic 認証 / CSRF / Flash / Paging / SPA ルーティング / リバースプロキシ / レートリミット / リクエストスコープキャッシュ | **そのまま流用〜薄い改修** | Flash・リバースプロキシ・レートリミットのみ jooby 参照が数行ある |
| 共通ユーティリティ（約 18,000 行） | **そのまま流用** | 7.16 |
| マイグレーション（SQL / コード） | **そのまま流用** | `!Ups` / `!Downs`、`migration` / `migration_history`、hash 検証、起動時適用。Gradle タスク化と down の設定切替のみ追加 |
| コード生成（`Generator` 697 行） | **そのまま流用** | 出力先パッケージの変更、Gradle タスク化 |
| `AsyncData` / `AsyncList` | **改修して流用** | `isLoadFailed()` を追加（F-A-09）。先読みは Phase 2 |
| バッチ / DB スケジューラ / MQ | **改修** | Context 生成を新方式に。**登録をパッケージスキャンから明示登録へ**（F-B-09 / F-M-07） |
| `WebRequest` / `WebResponse` / `WebCookie` | **薄い改修** | jooby 参照は各数行。helidon の型に差し替える |
| `AppContext`（716 行）/ 非 Web 用ダミー Context（607 行） | **再設計** | Web / Batch / MQ 共通基底 + Web 固有に分割 |
| ルーティング DSL 本体（`ExtendsJooby` 2,021 行） | **新規** | install 系・起動シーケンスがここに同居している。**最優先で精読する対象**（N-5） |
| 起動（`JoobyAppJetty` 389 行） | **新規** | helidon-webserver 4.5.4 の起動・`routing.any()` 登録 |
| バッチ管理画面（548 行） | **新規** | ルーティングが jooby 依存 |
| DI・アノテーション関連 | **廃止** | 原則2・原則3 |

### 10.2 機能カバレッジ表（Phase 1 の完成判定）

**この表がすべて「済」になることを Phase 1 の完了条件とする。**
着手時に `jooby_base` のソースを読み直し、抜けている行を追加すること。

| # | jooby_base の機能 | jimble での対応 | 対応 M | 状態 |
|---|---|---|---|---|
| 1 | ルート定義 DSL（path / before / get / post / put / patch / delete / options / after / error / install） | F-R-02〜09 | M1 | **済**（`RouterMatchingTest` / `RouterHookTest` / `ControllerInstallTest`） |
| 2 | パスパラメータ・ワイルドカード | F-R-06 | M1 | **済**（`RouterMatchingTest` / `PathSegmentsTest`） |
| 3 | 静的配信 / SPA / MPA / assets | F-R-14, F-W-17, 18, 20 | M5 | **済**（`docs/design-m5.md` 2〜3章） |
| 4 | リバースプロキシ | F-R-14 | M5 | **済**（`docs/design-m5.md` 4章） |
| 5 | レートリミット | F-R-15 | Phase 2 | **済**（M11。`memory` / `redis` / `db`。D-90） |
| 6 | AppContext 相当（request / response / session / cookie / flash / DB / attribute） | F-C-01〜10 | M2 | **済**（`ContextTest` / `ThreeWayDomainTest`） |
| 7 | UseCase / Executor / キャンセル | F-E-01〜05 | M2 | **済**（`AbstractExecutorTest`） |
| 8 | WebRequest（パラメータ / ヘッダ / ネストパラメータ） | F-W-01〜03 | M2 / M8 | **済**（`NestedParameterTest`。**`a[b][c]` が動いていなかったのでパーサを書き直した**。サンプルのフォームでも通る。`docs/design-m8.md` 4.2 / 7.2） |
| 9 | WebResponse（json / jsonL / text / redirect / modelAndView / form） | F-W-04〜09 | M2 / M5 | **済**（`docs/design-m5.md` 5章で modelAndView が埋まった） |
| 10 | ファイルアップロード | F-W-06 | M4 / M8 | **済**（`docs/design-m4.md` 7章。サンプルの `POST /form` で通る。`docs/design-m8.md` 7.2） |
| 11 | Validation（ルール / ValidationExecutor） | F-V-01〜04 | M4 | **済**（`docs/design-m4.md` 4章） |
| 12 | Session（DB / Redis / Cookie / 無効）+ 明示 save | F-S-01〜04 | M4 | **済**（`docs/design-m4.md` 3章） |
| 13 | Cookie / Flash / CSRF | F-S-05〜07 | M4 / M8 | **済**（`docs/design-m4.md` 2章。サンプルの `GET/POST /form` で通る。`docs/design-m8.md` 7.2） |
| 14 | Paging | F-V-05〜06 | M4 | **済**（`docs/design-m4.md` 6章） |
| 15 | SQL ビルダー（select / insert / update / delete / Dsl / JOIN / forUpdate） | F-D-01〜08 | M3 | **済**（`SqlBuilderPortingTest`） |
| 16 | DB 実行系（fetcher / batch / 接続使い分け / トランザクション） | F-D-10〜18 | M3 | **済**（`DbIntegrationTest`） |
| 17 | Data（型付き getter / Column 版 / flatten / JSON） | F-D-20〜27 | M3 / M6 | **済**（移送は `docs/design-m3.md`。`toString()` の要約表示は `docs/design-m6.md` 4章） |
| 18 | テーブル定義・型付きアクセサの自動生成 | F-G-01〜04, 08 | M3 | **済**（`docs/design-m3.md` 7章。CLI 込み） |
| 19 | マイグレーション | F-G-05〜12, 15〜17, 19 | M3 | **済**（`docs/design-m3.md` 6章） |
| 20 | AsyncData / AsyncList（遅延読み込み） | F-A-01〜05, 09 | M6 / M8 | **済**（`docs/design-m6.md`。サンプルの `GET /posts/{id}` で通る。`docs/design-m8.md` 7.2） |
| 21 | AsyncData 先読み（バッチローダー） | F-A-06〜08 | M10 | **済**（`docs/design-m10.md`。実 DB で 4本 → 2本） |
| 22 | Conf（環境別設定 / 既定値付き取得） | F-U-01〜03 | M4 / M8 / M11 | **済**（F-U-03 は M8。読むのはクラスパスだけ = D-80。`ConfTest` / `ConfLoadTest`） |
| 23 | Log（レベル別 / 用途別ロガー / SQL 集計） | F-U-04〜06 | M4 | **済**（`AccessLogTest`） |
| 24 | Cache（メモリ / Redis / LoadingCache / グループ） | F-U-07〜08 | M4 | **済**（`docs/design-m4.md` 8章） |
| 25 | RedisLock | F-U-09 | M4 | **済** |
| 26 | バッチ（引数 / cron / 同時実行制御 / 中断） | F-B-01〜08 | M7 | **済**（cron 実行そのものはステップ3。`docs/design-m7.md` 2章） |
| 27 | MQ（DB キュー / リトライ） | F-M-01〜06 | M7 | **済**（`docs/design-m7.md` 3章） |
| 28 | テンプレート描画 | F-W-08, 10, 11 | M5 | **済**（`docs/design-m5.md` 5章） |
| 29 | JSON（`Dson`） | F-D-25 | M3 / M8 | **済**（`DsonTest`。**壊れた JSON を黙って受けることを仕様として固定**。`docs/design-m8.md` 4.3） |
| 30 | セッション保存先の切替（DB / Cookie は新規） | F-S-01, 08〜10 | M4 / M8 | **済**（**Redis セッションは M8 で結合テストを足した**。`RedisSessionIntegrationTest`。`docs/design-m8.md` 7.3） |
| 31 | Redis 無しでの起動 | F-U-10, 11 | M4 | **済** |
| 32 | ルート属性（`.attribute()`）と before からの参照 | F-R-16, 17 | M1 | **済**（`RouteAttributeTest`） |
| 33 | `Column` からのパスパラメータ生成 | F-R-18 | M3 / M8 | **済**（`ColumnPathParameterTest` / `SqlDslTest`。`docs/design-m8.md` 4.4） |
| 34 | リクエスト単位のセッション保存先切替 | F-S-11 | M4 | **済** |
| 35 | コードマイグレーション | F-G-18 | M3 | **済**（明示登録。D-19） |
| 36 | DB スケジューラ | F-B-10 | M7 | **済**（`docs/design-m7.md` 4章） |
| 37 | バッチ管理画面 | F-B-11 | M7 | **済**（`docs/design-m7.md` 5章） |
| 38 | MQ 実行種別とスレッド数制御 | F-M-08 | M7 | **済**（設定に寄せた。`docs/design-m7.md` 3.10） |
| 39 | 共通ユーティリティ一式 | F-Y-01〜08 | M4 | **済**（`DataPortingTest` / `CryptoTest` / `PagingTest` / `BotUtilTest`） |
| 40 | HTTP サーバー設定（max_size / trust_proxy / 圧縮 / プール切替 / bot 判定） | F-H-01〜05 | M8 | **済**（M8 で実装。`ServerConfTest` / `BotUtilTest`。`docs/design-m8.md` 2章） |
| 41 | CORS | F-W-12 | M4 / M8 | **済**（`docs/design-m4.md` 5章。サンプルで有効にした。`docs/design-m8.md` 7.2） |
| 42 | Basic 認証 | F-W-13 | M4 | **済** |
| 43 | Bot ブロック / UserAgent 解析 | F-W-14, 16 | M4 | **済** |
| 44 | リクエストスコープキャッシュ | F-W-15 | M4 | **済**（M1 で実装済み。`ScopeCache`） |
| 45 | SPA 専用ルーティング | F-W-17 | M5 | **済** |
| 46 | 標準バリデータ 14 種 + ルール組み合わせ | F-V-07, 08 | M4 | **済** |
| 47 | CASE 式 / 仮テーブル / 自由 SQL / 地理空間 / MATCH | F-D-09 | M3 / M8 | **済**（`SqlDslTest`。`docs/design-m8.md` 4.5） |
| 48 | sticky コネクション / DB バージョン / DB ログ | F-D-19, 19b | M3 / M8 | **済**（`WebContextStickyTest` / `DbInfraIntegrationTest`。**sticky はスコープに束ねておらず丸ごと効いていなかった。**`docs/design-m8.md` 4.6） |
| 49 | 共通ユーティリティ（型変換 / JSON / 文字列 / 日時 / URL / HTTP / IO / CSV / XML / 暗号 / スレッド / ネットワーク / 例外） | F-Y-01〜15 | M4 | **済**（移送は M3。`DataPortingTest` ほか） |
| 50 | ルート優先順位の決定性 | F-R-20 | M1 | **済**（`RouterDeterminismTest`） |
| 51 | **複数データソース**（別データソース / ぶら下がるサブ） | F-D-14 | M3 / M8 | **済**（`MultiDataSourceIntegrationTest`。`docs/design-m8.md` 7.3） |
| 52 | **サンプルアプリの疎通** | NF-T-06 | M8 | **済**（`BlogAppIntegrationTest`。実サーバーを起動して 15 本叩く。`docs/design-m8.md` 7.4） |

### 10.3 サンプルアプリだけでは検証できない機能

RSS サイトの既存実装を調査した結果（`docs/sample-app-analysis.md`）、
**そのまま移植しただけでは以下が一度も動かない。**

AsyncData / トランザクション / PUT・PATCH・DELETE・OPTIONS / CSRF / Flash / Cookie 操作 /
ファイルアップロード / Redis 系（セッション・キャッシュ・分散ロック）/ 複数データソース /
リバースプロキシ / MPA / レートリミット / ストリーミングレスポンス

**対応（M8 ステップ5 で完了）：**

- **サンプルは `examples/blog`** にした（RSS サイトではなく、記事とコメントの題材）。
  機能を通すことが目的なので、題材そのものは軽いほうがよい
- サンプルに足したもの：**AsyncList**（記事→コメント）/ **PUT・PATCH・DELETE・OPTIONS** /
  **CSRF・Flash・Cookie 操作**（サーバーサイドフォーム）/ **ファイルアップロード** /
  **CSV のストリーミング** / **CORS**。トランザクションと MQ は M7 で入っている
- **サンプルでは埋まらない分**（Redis セッション / 複数データソース）は結合テストにした。
  Redis キャッシュ・分散ロック・リバースプロキシ・MPA はすでにテストがある
- レートリミット（F-R-15）は Phase 2 なので対象外
- サンプルが動き続けることは `BlogAppIntegrationTest`（実サーバーを起動して 15 本叩く）で
  機械的に確かめる（要件 NF-T-06）

---

## 11. マイルストーン

棚卸し（10.1）と `ExtendsJooby` 精読を踏まえ、**「作る」より「切り出して結線する」**を前提に組んだもの。

| # | 内容 | 完了条件 |
|---|---|---|
| ~~M0~~ | ~~要件定義・アーキテクチャ確定~~ | **完了** |
| ~~M1~~ | ~~モノリポ骨格 + helidon 起動 + Router 結線 + Dispatcher~~ | **完了。**`examples/hello` が動き、テスト 93 件が通る（`docs/design-m1.md` 13 章） |
| M2 | Context 3種 + 実行スコープキャッシュ | **完了。**Web / バッチ / MQ から同じ Domain 層を呼べる。ダミー HTTP Context を作らずにバッチが動く（`docs/design-m2.md`）。<br>**`Request` / `Response` の本実装は M3 に移した**（`Data` が前提になるため。同 3 章） |
| M3 | DB 層 + `Data` / `Dson` / 型変換の移送 + **`Request` / `Response` の本実装** + マイグレーション + コード生成 + Gradle プラグイン | 約 56,000 行の流用分がコンパイルを通る。`migrate` → `codegen` → `compileJava` が連鎖する。<br>**完了**（ステップ1〜7。`docs/design-m3.md`） |
| M4 | 周辺ランタイム（Session 4種 / Validation 14種 / Paging / Cache 3種 / Lock / CORS / Basic 認証 / Bot ブロック / リクエストスコープキャッシュ） | **Redis 無しで起動でき**、実用的なエンドポイントが一通り書ける。<br>**完了**（ステップ1〜7。`docs/design-m4.md`） |
| M5 | テンプレート（jte）+ **静的配信ハンドラ群**（assets / SPA / MPA / Etag）+ リバースプロキシ | HTML を返すアプリが書ける。jar 実行とディレクトリ実行の両方で静的ファイルが出る。<br>**完了**（ステップ1〜4。`docs/design-m5.md`） |
| M6 | AsyncData（遅延読み込み。先読みは含まない） | 遅延読み込みのツリーが JSON 出力できる。先読み用 API が揃っている。<br>**完了**（`docs/design-m6.md`） |
| M7 | バッチ / DB スケジューラ / MQ / バッチ管理画面 | cron 実行と DB キューが動く。登録が明示的である。<br>**完了**（ステップ1〜4。`docs/design-m7.md`） |
| M8 | CLI + サンプルアプリ + 結合テスト + カバレッジ表の充足（**Phase 1 完了**） | `jimble new` から疎通まで通る。10.2 の表がすべて「済」。<br>**完了（ステップ1〜5）。Phase 1 完了**（`docs/design-m8.md`） |
| M9 | **SSE / MCP / WebSocket / ドキュメントサイト** + Maven Central 公開 / 英語版 / 先読み実装（**Phase 2**） | 外部の人間がドキュメントだけでアプリを1本作れる。<br>**ステップ1〜6（SSE / MCP / WebSocket / ドキュメントサイト / Maven Central 公開 / jimble.io 公開）完了**（`docs/design-m9.md`）。**0.1.0 公開済み。ドキュメント公開済み** |
| M10 | **AsyncData の先読み**（F-A-06〜08） | 実 DB で 1+N 本が 1+1 本になる。先読みの有無で出力が一致する。<br>**完了**（`docs/design-m10.md`）。英語版も完了（NF-D-06）。反映の自動化も完了（D-117） |
| M11 | **実アプリ（RSS まとめサイト）を移送して穴を探す** ＋ そこから出た実装（レートリミット / グレースフルシャットダウン / MCP からの API 再利用 / テーブルネストの切り替え / SQL 結果キャッシュ / **PostgreSQL 対応** / SQL DSL の拡充） | サンプルでは踏まない道を通し、見つかった穴を塞ぐ。<br>**完了**（`docs/design-m11.md`）。**`examples/blog` は jimble に合わせて書いたものなので、jimble が想定した使われ方しかしない。**実アプリは想定していない順番で呼ぶ。<br>決定 **D-66〜D-105（40 件）**。ドキュメントは 21 → **35 ページ**にし、<b>書く過程で不具合・未実装が 7 件出た</b>（D-83〜D-89。「ドキュメントを書くのがいちばん強いレビューだった」）。**MySQL と PostgreSQL に同じテストを流す**ようにした（`dbTest` / `pgTest`。テスト 737 件・実 DB 各 199 件） |
| M12 | **運用に耐える形にする**（CI / 依存の縮小 / 可観測性 / ビルド規約） | 毎回の push を機械が見張り、使っていないものを抱えず、動いているアプリの中が外から見える。<br>**完了**（決定 D-106〜D-126。積み残していた D-6 / D-13 もここで決着）。<br>**CI**（D-108）は `build` / `plugin` / `db` / `pg` の4ジョブ ＋ 依存の一覧を GitHub に登録（NF-S-07）＋ ベンチマーク（NF-P-06）。<b>入れた初回に、まっさらな DB では動かないサンプル（D-109）が出た。</b><br>**依存を 39.5MB / 102 jar → 18.0MB / 86 jar に減らした**（D-119〜D-123）。<b>置き換えたものはすべて、置き換える前と突き合わせて答えが変わらないことを確かめてある</b>（IP 判定 54 万件・公開サフィックス 33 万件・ICU の変換 129 万通り・SipHash 9 万件）。<br>**可観測性**はメトリクス（NF-O-04 / D-124）と分散トレーシング（NF-O-05 / D-126。`jimble-otel` を足したときだけ 0.9MB 増える）。どちらも<b>値を返すだけで、ルートは生やさない</b>（D-106 と同じ理由）。<br>**ベンチマーク**（NF-P-06 / D-125）は<b>割り当てた byte 数で落とす</b>（時間は共用ランナーで 20〜30% ぶれるので使わない）。これで D-6（マッチ結果をキャッシュするか）が決まった。<br>**ビルド規約を build-logic へ**（D-13。root 709 → 369 行）。ドキュメントサイトの反映も自動化（D-117） |

**M3 が最大の山。**新規実装ではなく「約 56,000 行をパッケージを変えて移し、コンパイルを通し、
挙動が変わっていないことをテストで確認する」作業である。

**M1 と M2 が設計上の山。**真に新規で書く約 1,000 行のほぼ全部がここに集中する。

### 11.1 サンプルアプリ

**題材：`examples/blog`（記事とコメント）**

当初は自作の RSS まとめサイト（https://oreteki-matome.com/ ）を移す案だった（O-17）。
調査（`docs/sample-app-analysis.md`）の結果、**そのまま移してもフレームワークの機能の半分が
一度も動かない**ことが分かり（10.3）、さらに RSS の実装（フィード取得・パース・サイトマップ）が
コードの大半を占めて<b>どのルートが何を通しているのかが読めなくなる</b>ため、
**題材を軽くして機能を1つずつ通す形に変えた**（D-51）。

既存実装の調査結果は、カバレッジの洗い出しとして引き続き有効である
（`docs/sample-app-analysis.md`）。

**どのルートが何を通しているか**（`examples/blog`）：

| ルート | 通しているもの |
|---|---|
| `GET /` | jte / DB |
| `GET /posts` | JSON |
| `GET /posts/{id}` | **AsyncList**（記事→コメントの遅延読み込み） |
| `POST /posts` | トランザクション + MQ |
| `PUT /posts/{id}` | 全部置き換える |
| `PATCH /posts/{id}` | 送った項目だけ変える |
| `DELETE /posts/{id}` | 消す |
| `OPTIONS /posts` | CORS のプリフライト |
| `GET /posts.csv` | ストリーミング + CSV |
| `GET /form` / `POST /form` | CSRF / Flash / Cookie / ファイルアップロード |

バッチ・DB スケジューラ・MQ ワーカー・バッチ管理画面は
`BlogBatch` / `BlogScheduler` / `blog.mq` / `blog.batch` にある。

**サンプルでも埋まらない分**は**結合テスト**で担保する。

| 機能 | テスト |
|---|---|
| Redis セッション | `RedisSessionIntegrationTest` |
| 複数データソース | `MultiDataSourceIntegrationTest` |
| Redis キャッシュ / 分散ロック | `RedisIntegrationTest` |
| リバースプロキシ | `ReverseProxyIntegrationTest` |
| SPA / MPA | `SpaMpaTest` |
| レートリミット | Phase 2（対象外） |

**サンプルが動き続けること自体**は `BlogAppIntegrationTest`
（実サーバーを起動して 15 本叩く）で機械的に確かめる（要件 NF-T-06）。

---

## 12. 決定事項

### 12.1 第1回で確定（O-1〜O-10）

| # | 論点 | 決定 |
|---|---|---|
| O-1 | ルーティング DSL のメソッド名 | **小文字**（既存 `lib/base/web/router` の実装とも一致していた） |
| O-2 | テンプレートエンジン | **標準は jte。**Pebble は実行時エンジンとして差し込み可能にする |
| O-3 | `Data.toString()` | **要約表示にする。**JSON 化は `getJsonString()` に限定 |
| O-4 | `AsyncData.load()` の例外 | **`isLoadFailed()` で判定できる。**既定の挙動は変えない |
| O-5 | 先読み（バッチローダー） | **Phase 2。**前提 API は Phase 1 で用意（F-A-10） |
| O-6 | パッケージ名・座標 | **`io.jimble`** |
| O-7 | ライセンス | **Apache-2.0** |
| O-8 | MQ のキュー実装 | **DB キュー方式のみ** |
| O-9 | マイグレーション | **自前で持つ**（`jooby_base` の既存機構を流用） |
| O-10 | 対象アプリと移行順序 | **Phase 1 では移行しない。**機能網羅を優先 |

### 12.2 第2回で確定（O-11〜O-17）

| # | 論点 | 決定 |
|---|---|---|
| O-11 | migrate → codegen と開発用 DB | **ビルド時：**ローカルのみ `migrate` → `codegen` → `compileJava`。**実行時：**ローカル以外で起動時に適用 |
| O-12 | マイグレーションの形式 | **生 SQL。up と down の両方**（既存の `!Ups` / `!Downs` と一致） |
| O-13 | jte のコンパイル方式 | **Gradle での事前コンパイル前提** |
| O-14 | JSON ライブラリ | **自前（`Dson`）** |
| O-15 | Redis 無しで起動 | **できる。**セッションは DB / Redis / Cookie / なし を設定で切替 |
| O-16 | helidon のバージョン | **helidon-webserver 4.5.4 + Java 25** |
| O-17 | サンプルアプリの題材 | ~~**自作の RSS まとめサイト**~~ → **D-51 で変更**（`examples/blog`） |

### 12.3 第3回で確定（D-1〜D-5、N-4）

| # | 論点 | 決定 |
|---|---|---|
| D-1 | 起動時マイグレーションの排他制御 | **ロックテーブル方式**（`migration` テーブルをロック） |
| D-2 | down の運用範囲 | **設定で切り替えられるようにする**（既定は実行しない） |
| D-3 | ローカルでのテンプレート反映 | ~~**Gradle の継続ビルド**~~ → **D-47 で変更**（`jimbleRun` が自分で見張る） |
| D-4 | Cookie セッションの署名・暗号化 | **署名 + 暗号化の両方。**鍵のローテーションは<b>後回しにしていたが D-128 で入れた</b>（NF-S-09）。あわせて、<b>署名を通していなかったために `cookie.secret` を設定すると Cookie セッションが丸ごと効かなくなっていた</b>のを直した（D-128） |
| D-5 | RSS サイトのカバレッジ突き合わせ | **実施済み**（`docs/sample-app-analysis.md`） |
| N-4 | mroonga の扱い | **本体には持ち込まない。**ただし `MATCH` 構文は本体に持つ（F-D-09） |

### 12.3.1 第4回で確定（D-8 / D-9）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-16 | DB 結合テストの実行環境 | **開発用 DB に接続する**（Testcontainers は使わない）。実 DB が要るテストは `@Tag("db")` を付け、`./gradlew :jimble-db:dbTest` で実行する。**通常の `build` は DB を要求しない** | NF-T-05 / `docs/design-m3.md` 3.8 |
| D-14 | `Data` と共通ユーティリティの置き場所 | **`jimble-util` を新設。**`web → db → util → core` の一方向。`Data` の `Column` / `Table` 参照は `IColumn` / `ITable` に置き換えた（アプリ側の書き方は変わらない） | 6章 / `docs/design-m3.md` 1章 |
| D-8 | 変数ノードの探索順 | **登録順**（「先に書いたルートが勝つ」）。`LinkedHashMap` で保持 | F-R-20。既存実装の `HashMap` による非決定性を修正する |
| D-9 | `ErrorHandler` のシグネチャ | **`(Context, Throwable, ステータスコード)` の3引数。**コードは例外から解決する（`HttpException`）。**未マッチの 404 も同じ経路に流す** | F-R-09b / F-C-14〜16 |

### 12.3.2 M3 ステップ4 で確定（D-18）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-18 | 本文もステータスコードも設定しないまま送信したときのステータス | **204 No Content。**移送元（`WebResponse.send()`）の仕様をそのまま採る。`code(200)` / `send(200)` / `send("...")` を呼べば 200 | M1 の暫定実装（200）から意図的に変更した。`docs/design-m3.md` 5.4 |

### 12.3.3 M3 ステップ5 で確定（D-19 / D-20）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-19 | コードマイグレーションの収集方法 | **明示登録**（`CodeMigration.add(...)`）。移送元の Guava `ClassPath` によるパッケージ走査は使わない。実行順は登録順ではなく `versionYyyyMmDd()` 順 | 原則2（アノテーション・DI なし）と NF-P-03（起動時のリフレクションスキャンなし）。`docs/design-m3.md` 6.3 |
| D-20 | コード生成の実行場所 | **マイグレーションから切り離し、Gradle の `codegen` タスクに移す。**移送元はアプリクラスの物理パスから4階層上を辿って `src/main/java` を探しており、ビルド構成に強く依存していた | F-G-07 / ステップ6・7。`docs/design-m3.md` 6.3 |

### 12.3.4 M3 ステップ6 で確定（D-17 / D-21）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-17 | テーブル定義の列一覧をリフレクションで集めている | **解消。**生成コードが `List.of(...)` で列一覧を静的に出力し、`Table.declareColumns()` で返す。手書き定義向けのリフレクションは残すが `setAccessible(true)` を付け、空になったら警告する | 原則2 / NF-P-03。`docs/design-m3.md` 7.2 |
| D-21 | 生成対象から jimble の管理テーブルを外す | **外す。**一覧は `io.jimble.db.FrameworkTables.ALL` 1か所にある（D-68）。`migration` / `migration_history` / `migration_code` / `db_lock` / `db_log` / `db_value` / `db_cache` / `db_sticky` / `redis_lock` / `sql_cache` / `sql_cache_tag` / `rate_limit` / `session`（既定名）/ `batch_master` / `batch_history` / `batch_execute_info`。設定 `codegen.exclude_tables` で追加できる。**外した名前は codegen のログに出す**（`session` のようにアプリの業務テーブルとぶつかりうる名前があり、黙って外すと<b>自分のテーブルのクラスが生成されないことに気づけない</b>） | フレームワークの内部テーブルがアプリのコードに現れていた。`docs/design-m3.md` 7.3 |

### 12.3.5 M3 ステップ7 で確定（D-22）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-22 | Gradle プラグインの配布方法 | **Plugin Portal に publish せず、`pluginManagement { includeBuild("gradle-plugin") }` で取り込む。**プラグインは Gradle デーモンで動くのでバイトコードは Java 17 で出す（本体は Java 25） | Plugin Portal への依存を持ち込まない（D-13 と同じ方針）。`docs/design-m3.md` 8.1 |

### 12.3.6 M4 ステップ1 で確定（D-23）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-23 | Cookie の暗号方式 | **AES-256-GCM を新規に用意する**（`io.jimble.util.crypto.Aead`）。移送済みの `CipherUtil`（AES/CBC + 固定 IV + 改ざん検知なし + 失敗時に空文字）は Cookie セッションに使わない。改ざん検知だけでよい場面は HMAC-SHA256（`Signer`） | F-S-08。`docs/design-m4.md` 2.2 |

### 12.3.7 M5 ステップ3 で確定（D-24 / D-25）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-24 | リバースプロキシの HTTP クライアント | **JDK 標準の `java.net.http.HttpClient` を使う。**依存を増やさない。リダイレクトは追わず（`followRedirects(NEVER)`）そのままクライアントへ返す。接続 5 秒 / 応答 30 秒を既定とし、繋がらない場合は **502** を返す（移送元はタイムアウトなし・例外がそのまま出て 500） | F-R-14。`docs/design-m5.md` 4.1 / 4.6 |
| D-25 | 「レスポンス送信済み」の判定にストリーム送信を含める | **含める。**helidon の `isSent()` は `send(...)` 系でしか true にならず、`outputStream()` で書いた場合は false のままになる。`HelidonResponseSink` 側で「出力ストリームを取ったか」を持ち、`isSent()` がそれも見る | F-C-13 / F-W-07。`docs/design-m5.md` 4.7 |

### 12.3.8 M5 ステップ4 で確定（D-26〜D-28）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-26 | jte の事前コンパイルの持ち方 | **自前の Gradle プラグイン（`io.jimble.jte`）で `.java` を生成し、コンパイルは `compileJava` に任せる。**jte 公式プラグイン（Plugin Portal）は使わない（D-22 と同じ理由）。`.class` を直接吐く `precompileAll()` ではなく `generateAll()` を使うので、**テンプレートの型の間違いがアプリのコンパイルエラーとして出る** | F-W-10 / O-13。`docs/design-m5.md` 5.3 / 5.5 |
| D-27 | 「実行時コンパイルに対応しない」の担保 | **依存関係で保証する。**アプリの実行時クラスパスに入れるのは `gg.jte:jte-runtime` だけで、**コンパイラを含む `gg.jte:jte` は Gradle プラグインしか持たない。**設定での禁止にしない（書き換えれば有効にできてしまうため） | F-W-10。`docs/design-m5.md` 5.4 |
| D-28 | テンプレートの描画結果の送り方 | **いったん文字列に組み立ててから送る。**描画しながら流すと、途中でテンプレートが落ちたときに**もうヘッダが出ていて 500 を返せない。**大きなものを流したい場合は `Templates.render(name, model, writer)` を使う | F-W-07 / F-W-08。`docs/design-m5.md` 5.7 |

### 12.3.9 M6 で確定（D-29〜D-31）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-29 | `Data.toString()` を要約表示にしたときのログ | **ログに載せるデータだけ例外にする**（`LogData extends Data`。`toString()` は JSON のまま）。テキスト形式のログ（logback の pattern レイアウト）は引数を `toString()` で描くので、ここまで要約にすると**開発中のログから情報が消える。**ログのデータは「出すために作ったもの」であって、うっかり出るものではない | F-D-26 / F-U-06 / O-3。`docs/design-m6.md` 4章 |
| D-30 | 遅延読み込みを1つの型で拾う | **`Async` インターフェースを新設する。**`AsyncData` は `Map`、`AsyncList` は `List` で型の親が違うため、先読み（F-A-06。Phase 2）の走査と `Data` の要約表示が `instanceof` を2つ持つことになる。読み込みを起こさない口（`isLoaded` / `batchKey` / `batchId` / `isLoadFailed`）だけを持たせる | F-A-10 / F-D-27。`docs/design-m6.md` 5章 |
| D-31 | 読み込みに失敗したあとの扱い | **失敗しても「読み込み済み」にして、二度は読みにいかない。**参照されるたびに落ちるクエリを投げ続けないため。例外を投げずログを出して継続する既定の挙動は変えない（要件 F-A-09）。失敗したことは `isLoadFailed()` で分かる。あわせて `putData()` は**すでに読み込み済みなら何もしない**（先読みが後から二重に上書きしないため） | F-A-09 / F-A-10。`docs/design-m6.md` 3.6 / 5章 |

### 12.3.10 M7 ステップ1 で確定（D-32〜D-34）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-32 | バッチの起動でアプリケーションを立ち上げるか | **立ち上げない。**移送元は `BatchExecutor` の中で jooby / Jetty を起動しており、そのために**テンポラリディレクトリのファイルロックでプロセス間の起動を直列化する処理が 120 行**あった。jimble のバッチは Web サーバーを起動しないので不要。**起動の順番はアプリの `main` に書く**（原則1） | F-B-01。`docs/design-m7.md` 2.9 |
| D-33 | バッチの識別子 | **`getClass().getName()` を使う**（移送元は `getCanonicalName()`）。canonical name は**入れ子クラスで `Class.forName` が読めない形**になり、無名クラスでは null になる。あわせて CLI の `class=` は `Class.forName` に渡さず、**`BatchRegistry` に登録済みのものだけを引く** | F-B-09。`docs/design-m7.md` 2.2 / 2.8 |
| D-34 | バッチが実行されなかったときの伝え方 | **`BatchResult` を返し、理由をログに出す。**移送元はマスタに無い / 無効 / 同時実行数オーバーのいずれでも黙って `return` しており、**「起動したのに何も起きない」だけが残っていた** | F-B-05 / F-B-07 / F-X-05。`docs/design-m7.md` 2.3 |

### 12.3.11 M7 ステップ2 で確定（D-35〜D-37）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-35 | MQ の先読み | **やめて1件ずつ取る**（`SELECT ... FOR UPDATE SKIP LOCKED`）。移送元はスレッド数 × 10 件を先読みしてメモリに持ち、その行を `queuing` にしていたが、**停止時に戻す SQL のプレースホルダが置換されておらず**（`UPDATE \`%s\`` のまま）、止めるたびに `queuing` の行が取り残されていた。状態の置き場所を DB 1つに寄せる | F-M-01。`docs/design-m7.md` 3.8 / 3.9 |
| D-36 | MQ Executor のインスタンス | **メッセージごとに1つ作る**（登録の入れ物は `Supplier` を持つ）。移送元は1つを全ワーカーで使い回し、メッセージごとに `setCancelOrderNotify()` で共有インスタンスのフィールドを書き換えていた。**Executor にフィールドを持たせると別のメッセージと混ざる。**ルートの `UseCase::new` と同じ形にする（原則3） | F-M-07。`docs/design-m7.md` 3.3 |
| D-37 | リトライ回数の置き場所 | **コード側（`MqExecutor.maxRetry()`）に置く。**データ側に持つと「なぜこの行だけ5回なのか」がコードから読めなくなる（原則1）。間隔は設定（`mq.retry_backoff_seconds`）で、回を追うごとに倍・上限あり。超えたら `dead`（デッドレター）として残す | F-M-04。`docs/design-m7.md` 3.5 |

### 12.3.12 M7 ステップ3 で確定（D-38 / D-39）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-38 | スケジューラが「いま動かして」を受ける口 | **`jimble-mq` を使う**（`batch → mq → db → util → core`）。管理画面は Web のプロセスで動くので、別プロセスのスケジューラに直接お願いする手段がない。DB のキューに1行積めば、動いているスケジューラが拾う。**キューをもう1つ自前で作らない**（移送元も同じ形） | F-B-10 / F-B-11。6章の依存図を更新。`docs/design-m7.md` 4.2 |
| D-39 | cron の判定に使う「いまの時刻」 | **`tick(ZonedDateTime now)` と引数で受ける。**移送元は中で `ZonedDateTime.now()` を呼んでいたため、「日次バッチが翌日 03:00 に動くか」を確かめるには実際に待つしかなかった。引数にすれば 16 時間先まで1ミリ秒で進められる | F-B-04 / NF-T-02。`docs/design-m7.md` 4.9 |

### 12.3.13 M7 ステップ4 で確定（D-40〜D-42）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-40 | 管理画面の認証 | **認証情報が無ければルートそのものを登録しない**（`jimble-batch-manager` は独立モジュール）。移送元は `public static BasicAuthentication basicAuthentication = null;` という差し替え可能なフィールドで、**設定し忘れると管理 API が誰でも叩ける状態で公開されていた**（バッチの実行・停止・設定変更ができる API である）。あわせて**状態を変える操作はすべて POST**（移送元は GET だった） | F-B-11 / NF-S-04。`docs/design-m7.md` 5.2 / 5.3 |
| D-41 | 管理画面の作り | **依存のない HTML 1枚**（`resources/jimble/batch-manager/index.html`）。移送元は Vite でビルドした SPA を `resources/builds/...` に置いていたが、**フレームワークが npm のビルド成果物を抱えるのは重い** | F-B-11 / NF-L-01。`docs/design-m7.md` 5.1 |
| D-42 | `dbTest` の同時実行 | **Gradle の共有ビルドサービス（`maxParallelUsages = 1`）で直列化する。**開発用 DB は1つ（D-16）なのに `org.gradle.parallel=true` で、`dbTest` を持つモジュールが5つに増えた結果、**あるモジュールのテストが `TRUNCATE` した裏で別のモジュールのバッチが走る**ようになっていた | D-16 / NF-T-05。`docs/design-m7.md` 5.8 |

### 12.3.14 M8 ステップ1 で確定（D-43 / D-44）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-43 | プロキシヘッダを信じるか | **既定は信じない**（`server.trust_proxy = false`）。`X-Forwarded-For` はクライアントが名乗るだけの値なので、ロードバランサの後ろにいない構成で信じると**送信元をいくらでも偽れる**（IP でのアクセス制限やレートリミットが効かなくなる）。信じるときは `CF-Connecting-IP` → `X-Real-IP` → `X-Forwarded-For` の先頭 の順に見る | F-H-02 / NF-S-05。`docs/design-m8.md` 2.3 |
| D-44 | 同梱リソースが見つからないとき | **例外にする。**移送元の `BotUtil2` は「クラスパスに無ければ jar の場所から `../../../resources/main/lib/base/util/common/bot/` を辿る」という逃げ道を持っており、移送先にそのパスが無いため**起動のたびにエラーを1行出してパターン0件のまま動いていた**（ボット判定が実質死んでいた）。**「効いていないことに気づけない」形をやめる** | F-W-14 / F-H-05 / F-X-05。`docs/design-m8.md` 2.4 |

**あわせて記録：JSON に `public static final` の定数が出ていた。**
`PropertyUtil.getFieldNames()`（`Dson` のプロパティ一覧）が static フィールドまで拾っており、
`Paging` を返す API の応答に `KEY_NAME_PAGE` や `DEFAULT_PER` が並んでいた。
プロパティ一覧からは static を落とした（`getFields()` 自体は enum 定数の解決に使われるので触らない）。

**あわせて記録：`batch_master.cron` はコードで上書きされない。**
`BatchRegistry.sync()` は `default_cron` を毎回更新するが `cron` は最初の1回しか書かない
（運用が管理画面で変えた値をデプロイで踏み潰さないため。移送元と同じ）。
**コードの `cron()` を変えても、すでに動いている環境のスケジュールは変わらない。**
NF-D-04（落とし穴のページ）に載せる。

### 12.3.15 M8 ステップ2 で確定（D-45 / D-46）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-45 | ネストパラメータの区切り | **`.` と `[...]` を同じ区切りとして扱う。**`[...]` の中身は 数字 → 添字 / 空 → 末尾に足す / それ以外 → 名前。移送元はキーを `.` でしか分けず、`[...]` の中を数値としてしか読まなかったため、**`user[name]` が `user[0]` と解釈され、要件 F-W-03 の例（`a[b][c]=1`）がそのまま動かなかった。**あわせて JSON の重ね方を深いマージ（`MapUtil.mergeData`）から**上書き**に変えた（同じキーがあると値が配列に化けていた） | F-W-01〜03。`docs/design-m8.md` 4.2 |
| D-46 | sticky コネクションの範囲 | **(a) `WebContext` のスコープに束ねる**（`jimble-core` は `jimble-db` を見られないので共通基底には置かない）。**(b) 「使わない」設定は判定そのものを止める**（移送元の `isUse` は `db_sticky` テーブルへの読み書きしか止めておらず、`db_sticky.use = false` でも固定は起き続けた）。**(c) リクエストをまたぐ固定は `apply()` を `doClose()` で呼んで初めて成立する**（移送元は呼び出しが1つも無く、テーブルには誰も書き込んでいなかった）。**(d) 相手を見分ける値は既存のセッション Cookie を使い、この用途で Cookie を新しく発行しない**（移送元は `cid` を全リクエストで無条件に発行していた。要件 F-S-12 に反する）。レプリカが無ければ Cookie の解析も `db_sticky` の SELECT もしない | F-D-19 / F-S-12。`docs/design-m8.md` 4.6 |

**あわせて記録：設定キーに `jooby.` が残っていた。**
`DBLog` が `jooby.log.db`、`DBSticky` が `jooby.db_sticky.use` を読んでいた。
`application.conf` に `log.db = true` と書いても効かず、**効いていないことに気づく手がかりが無い**
（既定値で動くだけ）。それぞれ `log.db` / `db_sticky.use` にした。
`DBLog` の `stack_trace` も `Throwable` をそのまま入れていたので
（DB に入るのは例外のメッセージ1行だけで、どこで落ちたか分からない）、
フレームの文字列リスト（最大 20 行）にした。

**あわせて記録：`Dson` は壊れた JSON を黙って受ける。**
`{` や `{"a":}` は空の `Data`、`[1,2` は 2 件入った `Data`、
`これは JSON ではない` は `null` になる。**例外は飛ばない。**
「空のリクエスト」と「壊れた JSON」がアプリから区別できない。
既定の挙動は変えず、NF-D-04（落とし穴のページ）に載せる。

**あわせて記録：CASE 式は `SelectQuery` に包まないと `?` になる。**
`Dsl.caseWhen()` が返す `Case` は `IDsl` であって `ISelect` ではないので、
`SQL.select(Dsl.caseWhen()...)` と直接渡すと**ただの値としてバインドされる。**
コンパイルは通り実行もエラーにならず、返る値が違うだけである。
`SQL.select(new SelectQuery().dsl(...).as("..."))` と書く。NF-D-04 に載せる。

### 12.3.16 M8 ステップ3 で確定（D-47 / D-48）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-47 | ホットリロードの作り | **継続ビルド（`gradlew -t`）は使わない。**`jimbleRun` が自分でソースを見張り、`classes` を子プロセスで流し、**終了コードで**成否を判定する。継続ビルドがやるのは「ファイルを見て再ビルドする」ところまでで**アプリへの反映はしない**ため、結局そこは誰かが再起動する必要があり、しかも「ビルドが終わった」を出力の文字列で判定することになる。D-3 を上書きする | F-X-02 / D-3。`docs/design-m8.md` 5.5 |
| D-48 | アプリをどこで動かすか（**D-77 で撤回**） | **別プロセス**（`java -cp ... <mainClass>`）。移送元（`jooby_run`）は Gradle デーモンと同じ JVM に `URLClassLoader` でロードし、リフレクションで `startApp` / `stop` / `server` フィールドを叩いていた。速いが **(a)** `close()` していないのでクラスローダーが毎回漏れる、**(b)** 止まりきらないスレッド（スケジューラ・MQ ワーカー）が**次の起動と二重に動く**、**(c)** アプリが `System.exit()` を呼ぶとデーモンごと落ちる、**(d)** アプリ側が決まったシグネチャを強いられる（原則1・原則2）。別プロセスなら殺せば必ず全部止まる。**アプリはプロジェクトのツールチェーンで動かす**（デーモンの JVM ではない）。再起動は実測 1.4〜1.7 秒で、要件 F-X-03 に収まる | F-X-02 / F-X-03 / 原則1・2。`docs/design-m8.md` 5.2 |

**あわせて記録：ビルドの成否を標準エラーで判定していた。**
移送元は「標準エラーに1行でも出たら失敗」で見ており、
そのために「無視するエラー文字」を 20 個（`npm warn` / `Xlint` / `ノート: ` / `│` …）
並べる必要があった。**表に載っていない警告が1行出ただけで再起動しなくなり、
逆に `Xlint` を含む本物のエラーは見逃す。**どちらも
「なぜか反映されない」「なぜか古いまま」という形でしか表に出ない。
`jimbleRun` は**終了コードだけ**を見る。

**あわせて記録：Gradle の `Property` はタスクのスレッドでしか引けない。**
`jimbleRun` の入れ替えはプロキシのスレッドから走るので、
そこで `Property.get()` を呼ぶと落ちる。しかも `HttpServer` のハンドラから出た
非検査例外は**黙って接続を切る**ので、ブラウザには「応答なし」としか見えない。
起動に要るものは実行のはじめに素の値へ写し（`resolveOnce()`）、
入れ替えとハンドラの両方に `RuntimeException | Error` の受け口を置いた。

### 12.3.17 M8 ステップ4 で確定（D-49 / D-50）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-49 | 雛形に何を入れるか | **DB も Redis も要らない、そのまま動くものを出す。**DB・バッチ・MQ・セッションは「外すだけのコメント」にし、マイグレーションは `.sql.example` で置く（置いただけで流れると、DB を使わない人が驚く）。雛形が「手を入れないと動かない」状態だと、最初につまずいたときに**自分のコードが悪いのか雛形が悪いのかが分からない。**あわせて、聞くのは**名前1つだけ**にし、パッケージ名と DB 名はそこから決める（対話で複数聞くと、答えを間違えたときに作り直しになる）。**大文字の切れ目では区切らない**（`MyBlog` → `myblog`。区切ると `APIServer` が `a_p_i_server` になる） | F-X-01 / F-X-05 / NF-T-06。`docs/design-m8.md` 6.2 / 6.3 |
| D-50 | 雛形が参照する jimble の版 | **jar のマニフェスト（`Implementation-Version`）から読む。**手で書くとビルドした版とずれ、**生成したプロジェクトが解決できない依存を持つ。**しかもそれに気づくのは受け取った人である。あわせて、雛形が本当にビルドできることを確かめるために `maven-publish` を入れ、本体と Gradle プラグインの両方を `publishToMavenLocal` できるようにした（Maven Central は Phase 2。NF-L-04 の下ごしらえ） | F-X-01 / NF-L-04 / NF-T-06。`docs/design-m8.md` 6.3 / 6.4 |

**あわせて記録：エラーハンドラが組み立てた本文を捨てていた。**
`Dispatcher.handleError` の最後が `context.response().send(statusCode)` になっており、
エラーハンドラが `json(...)` で組み立てただけで `send()` を呼んでいない場合に
**中身を捨ててステータスだけを返していた。**通常の経路は「組み立てて最後に
`Stage.send()` が流す」形なので、**まったく同じ書き方なのにエラーの経路だけが違った。**
例外も出ず「404 は返るが本文が空」という形でしか表に出ない。
`send()` に変え、ステータスはハンドラを呼ぶ前に入れるようにした（ハンドラが上書きできる）。
**移送元の穴ではなく jimble で作った穴で、`jimble new` の雛形を書いていて見つかった。**

**あわせて記録：Java 25 と Gradle 9 が要る。**
Gradle 8 は Java 25 の上では動かない（ツールチェーンとして使うのは問題ない）。
CLI も Java 25 でコンパイルしてあるので Java 21 で起動すると
`UnsupportedClassVersionError` になる。雛形の README の先頭に書いた。
**Gradle ラッパーは生成しない**（jimble が Gradle のバイナリを配ることになる）。
`gradle wrapper` を1回流す案内を出す。

### 12.3.18 M8 ステップ5 で確定（D-51）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-51 | サンプルアプリの題材 | **RSS まとめサイトではなく `examples/blog`**（記事とコメント）にした。O-17 を上書きする。サンプルの目的は<b>「これが動く＝その機能が動く」</b>ことであって、題材の作り込みではない。RSS の実装をそのまま移すと、フレームワークの検証に関係のないコード（フィード取得・パース・サイトマップ）が大半を占め、<b>どのルートが何を通しているのかが読めなくなる。</b>10 本のルートで要件 10.3 の項目を1つずつ通す形にし、`BlogAppIntegrationTest` で実サーバーを起動して確かめる | O-17 / 10.3 / 11.1 / NF-T-06。`docs/design-m8.md` 7.2 |

**あわせて記録：ローカルでは Cookie が届かない。**
`cookie.secure` の既定は `true`（HTTPS のみ。要件 NF-S-04）だが、
**ローカルは http なのでブラウザが Secure な Cookie を送り返さない。**
セッションも CSRF も Flash も、例外を出さずに効かなくなる。
既定は変えず（本番で外れているほうが危ない）、
**`env=local` で `cookie.secure = true` のままなら起動時に警告する**ようにした。
`jimble new` の雛形にも `cookie { secure = false }` を入れてある。

**あわせて記録：バッチのテーブルがアプリのコードに生成されていた。**
D-21 の除外リストに `batch_master` / `batch_history` / `batch_execute_info` が
漏れていた（M7 で足したテーブルなので）。足した。
MQ のテーブルは名前をアプリが決めるのでリストには書けない。
`codegen.exclude_tables` で外す（サンプルでその形を見せている）。

**あわせて記録：「別のデータソース」と「ぶら下がるサブ」は別物。**
`db { 名前 { ... } }` は `DBUtil.getDB(名前)`、
`db { 親 { subs { 名前 { ... } } } }` は `DB.newSubDB(名前)` で引く。
**引き方が似ているのに別物**で、取り違えると `getSubDBSource` が `null` を返し、
その先の `new DB(null)` で `NullPointerException` になる。
「設定に無い」ことは何も言われない。NF-D-04 に載せる。

**あわせて記録：HOCON のコメントは `#` か `//`。**
`application.conf` に `/* ... */` を書くと
`Key '/' may not be followed by token: '*'` で起動しない。NF-D-04 に載せる。

### 12.3.19 M9 ステップ1〜2 で確定（D-52〜D-55）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-52 | gRPC を本体に入れるか | **入れない。**実測で **+28 jar / +10.4MB**（実行時全体が 34 jar / 2.7MB → 62 jar / 13.1MB。約5倍）。目的1「依存の縮小」と正面からぶつかる。加えて HTTP/2・protobuf のコード生成・別ルーティング・5種類目の Context が要り、<b>gRPC の側では「上から辿れば分かる」が成り立たない層がもう1つできる。</b>使いたいアプリが自分で `helidon-webserver-grpc` を足して `GrpcRouting` を登録できる**逃げ道だけ**用意する | 2.2。`docs/design-protocols.md` 4章 |
| D-53 | SSE の寿命 | **上限を置く**（`sse.max_duration_seconds`。既定 300 秒）。**相手が切ったことは検知できない**ためである。実測すると、クライアントが TCP を閉じたあとの書き込みは環境によって「例外が出ない（30 秒で 4.8MB 書けた）」か「<b>止まったまま帰ってこない</b>」のどちらかになる。Java のソケットには書き込みタイムアウトが無く（helidon の `SocketOptions` も connect と read だけ）、<b>外から止める手段が無い。</b>`isOpen()` が見ているのは相手の生死ではなく上限と明示的な close だけである。SSE はもともと繋ぎ直す前提の仕組みなので（`retry:` を開いたときに1回送る）、上限を置くほうが本来の使い方に近い | F-W-21 / NF-P-07。`docs/design-m9.md` 2.2 |
| D-54 | MCP の対応版 | **2026-07-28 の1版のみ。**複数版を同時に見ると、互換の分岐がコード中に散る。この版で<b>プロトコルレベルのセッションと GET ストリームが消えた</b>ため、「POST 1本 + 必要ならレスポンスを SSE にする」だけになり、jimble の Context モデル（実行単位ごとに1つ）とそのまま重なる。**サーバーからリクエストを投げることも無くなった**ので、MCP に WebSocket は要らない | F-MCP-01〜。`docs/design-m9.md` 3.1 |
| D-55 | ツールの JSON Schema | **小さなビルダーを自前で持つ**（`io.jimble.mcp.schema.JsonSchema`）。外部の JSON Schema ライブラリを足さない。ここで要るのは「引数を宣言して JSON にする」ことだけで、検証は jimble の Validation が持っている。ライブラリを足すと<b>書き方が2つになる</b> | F-MCP-09。`docs/design-m9.md` 3.5 |

**あわせて記録：要件 F-D-16 が実装されていなかった。**
「Context クローズ時に未コミットのトランザクションをロールバックしエラーログを出す」は
MUST だが、**それを行うコードがどこにも無かった**（`Context` の javadoc には書いてあった）。
try-with-resources を使っているかぎり `DBTransaction.close()` が拾うので、
**使わずに途中で return したときだけ、ロールバックもされず接続もプールへ戻らない。**
`Context` にインスタンスごとの後始末リスト（`onClose`）を足し、
`DBTransaction` が開始時に登録・終了時に解除するようにした。
`jimble-core` は DB を知らないので、後始末そのものは知っている側が持つ。

**あわせて記録：`DBTransaction.commit()` がトランザクションを終わらせていた。**
中で `db.commitEndTransaction()` を呼んでおり、`commitEndTransaction()` と同じ動きだった。
`rollback()` は終わらせない（`db.rollback()`）ので、**コミットとロールバックで揃っていない。**
「途中まで確定させて続ける」つもりで `commit()` を呼ぶと、
<b>そこから先は自動コミットになる</b>（例外は出ない）。`db.commit()` に直した。

**あわせて記録：`code(202).send()` が空の `{}` を返す。**
`Response.send()`（引数なし）は、何も設定されていなくても
**`Accept` に `application/json` があれば自分自身を JSON にして返す。**
MCP のクライアントは `Accept: application/json, text/event-stream` を必ず送るので常に当たり、
「本文を付けてはいけない」通知の 202 に `{}` が付いていた。
本文なしで送るのは `send(202)` である。NF-D-04 に載せる。

**あわせて記録：SSE の本文の改行。**
`data:` は行ごとに付ける必要がある。改行は「次のフィールド」の合図なので、
そのまま流すと<b>1件が2件3件に割れる。</b>

### 12.3.20 M9 ステップ3 で確定（D-56 / D-57）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-56 | WebSocket の Context の単位 | **メッセージごとに1つ**（接続ごとではない）。MQ とまったく同じ形になる。接続ごとにすると、<b>何時間も生きる Context が居座り、トランザクションやフェッチャを握れば接続プールをそのぶん食い続ける</b>（SSE と同じ踏み方で、しかも長い）。接続に紐づけたいもの（誰が繋いでいるか、どの部屋か）は `WsSession.attributes()` に置く | F-C-01 / F-W-22 / NF-P-07。`docs/design-m9.md` 6.2 |
| D-57 | 接続の一覧をフレームワークが持つか | **持たない。**持った瞬間に、複数インスタンス構成で「自分のインスタンスに繋いでいる人にしか届かない」ものになる。配信としては間違っているのに<b>1台で動かしているうちは正しく見えてしまい、台数を増やした日に壊れる。</b>1接続を扱う口だけを出し、配信が要るアプリは MQ（F-M-03）か Redis の Pub/Sub を挟む（原則4） | F-W-22。`docs/design-m9.md` 6.4 |

**あわせて記録：helidon では WebSocket が別のルーティングになる。**
アップグレードは `WsUpgradeProvider` が HTTP のルーティングより前で横取りし、
登録先も `WsRouting` なので、**`routing.any()` には来ない。**
そのまま helidon に直接登録すると<b>ルートの置き場所が2つに分かれ</b>、
起動時の一覧（F-R-12）にも重複の検出（F-R-13）にも乗らない。
jimble は**擬似メソッド `WS` としてルートツリーに載せ**、
起動時に `WsBridge` が helidon の `WsRouting` へ流し込む。
HTTP のリクエストに `WS` というメソッドは来ないので、マッチングとぶつからない。

**あわせて記録：WebSocket の認証はアップグレードで通す。**
`onUpgrade(WsSession)` でだけ Cookie が読める。断ると **403**。
**繋がってから切ってはいけない。**繋がると、クライアントは
「一度は通った」と思って繋ぎ直しにくる。

### 12.3.21 M9 ステップ4 で確定（D-58 / D-59）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-58 | ドキュメントサイトの作り方 | **jimble 自身で作る**（`jimble-docs` + jte + commonmark-java）。静的サイトジェネレータ（Hugo / Docusaurus / MkDocs）を使わない。<b>ドキュメントサイトそのものが jimble の実例になる</b>ものを、別の言語のツールで作る理由がない。**外部の CDN に繋がない**（CSS / JS / フォント / favicon すべて自前）。検索は索引を配って素の JavaScript で絞り、検索エンジンを足さない（20 ページ程度なら足りる） | NF-D-01。`docs/design-m9.md` 8.1 |
| D-59 | コード片の陳腐化を防ぐ方法 | **実コードに印を付けて抜く**（`// docs:begin <名前>` 〜 `// docs:end`）。ドキュメントには `` ```java snippet=名前 `` とだけ書く。**印が見つからなければサイトのビルドを落とす。**抜く先はサンプルと<b>テスト</b>（テストから抜けるのが大きい。「動く証拠」がそのまま載る）。置き場所が無いものだけ `docs/site/snippets/DocsOnly.java` に置き、<b>これはコンパイルされない</b>ことを同ファイルに明記する | NF-D-03。`docs/design-m9.md` 8.2 |

**あわせて記録：Kotlin のブロックコメントは入れ子になる。**
`build.gradle.kts` のコメントに `docs/site/<言語>/*.md` と書いたところ、
`/*` が**入れ子のコメントを開き**、外側が閉じないまま
その下の `tasks.register<JavaExec>("site")` が丸ごと消えていた。
**エラーは出ない。**「タスクが無い」とだけ言われ、
`gradle :jimble-docs:properties` は正しい `buildFile` と `description` を返す
（ファイルは読まれている）。NF-D-04 に載せた。

**あわせて記録：`JavaExec` も Gradle デーモンの JVM で動く。**
`site` タスクが Java 21 で走って `UnsupportedClassVersionError` になった。
`jimbleRun` と同じ踏み方である（D-48）。
`javaLauncher = javaToolchains.launcherFor(java.toolchain)` を付ける。

**あわせて記録：中身が無い言語へのリンクを出さない。**
言語の切り替えを固定リンクで書いていたので、英語版が無いあいだ
`/en/index.html` が 404 になっていた。**ページがある言語だけ**リンクを出すようにした。

### 12.3.22 M9 ステップ5 で確定（D-60 / D-61）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-60 | Maven Central への公開を何で組むか | **Portal の REST API を直に叩く。**コミュニティ製の Gradle プラグイン（vanniktech / nmcp / JReleaser）を入れない。Sonatype には公式の Gradle プラグインが無い（ドキュメントに「no official Gradle plugin」と明記）。やることは **(1) Maven のレイアウトで出す (2) 署名する (3) zip にする (4) multipart で POST する** の4つで、(1)(2) は Gradle 同梱の `maven-publish` / `signing` で済む。残る2つのために外部プラグインを増やすと、<b>公開のときに何が起きているかがビルドファイルから辿れなくなる</b>（原則1）。HTTP は JDK の `HttpClient`（D-24 と同じ）。**鍵とトークンは環境変数だけ**にし、無い環境では署名を飛ばしてビルドが通るようにする | NF-L-04。`docs/design-m9.md` 9.1 / `docs/publishing.md` |
| D-61 | 版の既定 | **次の版の `-SNAPSHOT`（いまは `0.4.1-SNAPSHOT`）。**リリースのときだけ `-Pjimble.version=0.4.1` のように渡す。<b>既定は `gradle/libs.versions.toml` の `[versions] jimble` 1か所にしかない</b>（本体も gradle-plugin も `JimbleBuild.version(project)` 経由で読む。0.2.0 のときは2つのビルドファイルに書き写していた）。**公開したら既定を次の版に上げる**（0.2.0 を出したら `0.2.1-SNAPSHOT`）。上げ忘れると `publishToMavenLocal` が公開済みより<b>古い版番号</b>を吐き、アプリ側は気づかないまま Central の版を使い続ける（実際 M11 で踏んだ）。Central は `-SNAPSHOT` を受け付けないので、渡し忘れれば `centralUpload` がその場で止まる。既定を素の `0.1.0` にすると、<b>うっかり publish したものが「リリース版」として残る</b>。Central は一度公開したものを消せない | NF-L-04。`docs/design-m9.md` 9.1 |

**あわせて記録：javadoc が通っていなかった。**
`withJavadocJar()`（Central の必須）を入れて初めて分かった。
`jimble-db` は `<` `<=` `<>` を javadoc に生で書いており（`malformed HTML`。6箇所）、
`jimble-batch-manager` は存在しない引数に `@param` が付いていた。どちらも直した。
`jimble-util`（移送した約 18,000 行）は 30 件以上あるので**そこだけ doclint を切った**。
全体で切ると、これから書くコードの間違いに気づけなくなる。
`DOCLINT_OFF` という表にモジュール名を並べ、潰したら外す（D-15 に足した）。

**あわせて記録：`tasks.withType<Javadoc>()` の中の `name` はタスク名。**
モジュール名だと思って `if (name in DOCLINT_OFF)` と書いたが、
ここでの `name` は `"javadoc"` である。表に何を書いても当たらない。

**あわせて記録：Gradle プラグインの POM は2箇所にある。**
`gradle-plugin` は別ビルド（`includeBuild`）なので、
本体の `jimblePom()` を共有できない。**同じ内容が2つある。**
片方だけ直すと、検証で落ちるのは片方だけになる。

### 12.3.23 0.1.0 の公開で確定（D-62）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-62 | ビルドスクリプトの検証環境 | **実際に使う Gradle のバージョンで一度は流す。**0.1.0 の公開で、Gradle 8.14.3 では通り 9.7.1 で落ちる書き方が2つ見つかった。**(a)** `val x by tasks.registering { }` は Gradle 9.6 で非推奨になり、Kotlin DSL では<b>コンパイルエラー</b>になる（`tasks.register("x") { }` に直す）。**(b)** `"...".formatted(...)` は Java 15 のメソッドで、8.14.3 の Kotlin スクリプトからは見えていたが 9.7.1 では解決できない（文字列テンプレートに直す）。どちらも「動くほうでしか試していない」ことが原因で、<b>公開の当日に開発機で初めて分かった</b> | NF-L-04。`docs/design-m9.md` 9.5 |

**あわせて記録：`central.sonatype.com` へ出られない環境がある。**
egress に許可リストがある環境では CONNECT が 403 で弾かれ、
`repo.maven.apache.org` は通るのに**公開だけができない**。
公開は開発機のターミナルから流す。

**あわせて記録：トークンの前後の空白で 401 になる。**
Portal は `Invalid token` としか返さない。
`export X=$(pbpaste)` やコピーの取りこぼしで末尾に改行が1つ入るだけで
Base64 の中身が変わる。前後を落とすようにし、401 のときは
**値を出さずに「何文字か」「空白が混ざっていないか」だけ**を出すようにした。

**あわせて記録：`.gitignore` の `docs/` は全階層に効く。**
GitHub へ公開するとき `docs/` を外そうとして書いたところ、
`jimble-docs/src/main/jte/docs/` まで巻き込んで**テンプレートが消えた**。
先頭に `/` が要る。NF-D-04（落とし穴のページ）に載せた。

**あわせて記録：同梱している一覧は MIT である。**
`crawler-user-agents.json`（ボット判定。約 178KB）は
[monperrus/crawler-user-agents](https://github.com/monperrus/crawler-user-agents) のもので、
MIT は著作権表示と許諾文を一緒に配ることを条件にしている。
Apache-2.0 のリポジトリに入れるので `NOTICE` を足した。jar にも入る。

### 12.3.24 jimble.io の公開で確定（D-63）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-63 | ドキュメントの置き場と配り方 | **Cloudflare Workers（静的アセット）に、本体とは別のリポジトリ（`jimble-document`）から配る。**生成は本体でしかできない（`Snippets` が `examples/` とテストの実コードから抜くため。NF-D-03）ので、本体で作った `docs/site/build/` を向こうの **`dist/` へ置き換えて push** する。<b>資産を `dist/` に1段下げるのが要点</b>で、直下に置くと `wrangler.jsonc` と `README.md` まで配られる（実際 `https://jimble.io/wrangler.jsonc` が 200 で返っていた）。下げたことで更新が `dist/` の置き換えだけになり、「手で置いた設定が生成し直すと消える」も構造的に起きなくなった | NF-D-01。`docs/design-m9.md` 10章 |

**あわせて記録：Pages と Workers 静的アセットは別物である。**
`*.workers.dev` が出てくるのは後者。**Workers は「見つからないときに 404.html を返す」を既定でしない。**
`wrangler.jsonc` に `not_found_handling: "404-page"` が要る。
それまでは、見つからないパスが**「ステータスは 404 だが本文が空」**という
気づきにくい形で壊れていた。`_redirects` と `_headers` はどちらでも効くので、
そこだけでは区別がつかない。

**あわせて記録：`.html` は 307 で正規化される。**
`/ja/routing.html` は `/ja/routing` へ送られる。生成していたリンクが全部 `.html` 付きで、
**クリックのたびに1往復よけいにかかり、sitemap の 20 件も全部転送**になっていた。
本文・テンプレート・`search.js`・sitemap・404 のリンクを拡張子なしに直した。
副作用として**ファイルを直接開くとリンクが辿れない**ので、
`:jimble-docs:site` の最後にサーバー越しに見る案内を出すようにした。

**あわせて記録：`_redirects` の相対パスはスキームを引き継ぐ。**
`/  /ja/  302` と書くと、`http://` で来た人は **`http://` のまま** `/ja/` へ送られる。
絶対 URL はプレビュー環境で壊れるので、Cloudflare の Always Use HTTPS で直す。

### 12.3.25 M10 で確定（D-64 / D-65）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-64 | 「まとめて引く」コードの置き場 | **クラスに `loadBatch` を足す。**`AsyncLoaders.add("...", ids -> ...)` のようなレジストリにしない。バッチ（F-B-09）や MQ（F-M-07）の明示登録と揃うが、<b>登録を忘れたときの現れ方が違う</b>。バッチや MQ は忘れれば「動かない」のですぐ分かる。先読みは忘れても<b>動く。遅いだけである。</b>気づく手がかりが無い。`load()` と `loadBatch()` を同じファイルに並べて置けば、片方だけ直したときに目に入る。ここでいちばん怖いのは「1件ずつ引くのと、まとめて引くのとで結果がずれている」ことである（原則1） | F-A-06。`docs/design-m10.md` 2.1 |
| D-65 | 先読みを走らせるところ | **明示的に呼ぶ（`AsyncPrefetch.run(data)`）。**あわせて「レスポンスを送る直前に自動」も設定で選べるようにするが、**既定は false**（`async.prefetch.on_response`）。レスポンスのたびに黙ってクエリが飛ぶ形は原則5（隠れた I/O を作らない）に反する。既定を false にしておけば、<b>入れたとたんに挙動が変わることがない</b> | F-A-06 / 原則5。`docs/design-m10.md` 2.2 |

**あわせて記録：走査の順番を間違えると全部読み込む。**
`AsyncData extends Data`、`AsyncList extends ArrayList` なので、
`Map` や `Collection` として先に判定すると `values()` / `iterator()` を呼んでしまい、
**走査しただけで枝という枝に SQL が飛ぶ。**必ず `Async` かどうかを先に見る。

**あわせて記録：走査済みの判定は参照の同一性で見る。**
`AsyncData.equals()` は `hashKey()` しか見ない（要件 F-A-05）ので、
`equals` で「もう見た」を判定すると<b>別物を同じとみなして取りこぼす。</b>
`IdentityHashMap` を使う。

**あわせて記録：返ってこなかった id も読み込み済みにする。**
しないと、そのぶんだけ個別のクエリが飛んで先読みの意味が無くなる。
しかも**減り方が入力次第で変わる**ので気づきにくい。
逆に `loadBatch` が例外を投げたときは<b>1つも読み込み済みにしない</b>。
個別に読み直されるので結果は変わらない（要件 F-A-08）。

**あわせて記録：遅延読み込みのページがドキュメントに無かった。**
`docs/site/ja/async.md` を足した（21 ページ目）。

### 12.3.26 M11 / M12 で確定（D-66〜D-136。積み残していた D-6 / D-10 / D-11 と残件 N-2 もここで決着）

| # | 論点 | 決定 | 影響 |
|---|---|---|---|
| D-66 | ハッシュの暗号化を必須にするか | **任意にする**（`hash.password.encrypt`）。あわせて `CipherUtil` の **static フィールドを消す**。static 初期化子で設定を読むと、鍵が無いとき<b>1回目は `ExceptionInInitializerError`（メッセージが `null`）、2回目以降は `Could not initialize class ...`</b> になる。同じ原因なのに違うメッセージが出て、どちらも何をすればよいか分からない。呼ばれたときに読む形に変え、**失敗したら例外を投げる**（従来は `printStackTrace()` して<b>空文字を返していた</b>ので、暗号化に失敗したまま空のハッシュが保存され得た）。鍵と IV の長さも見る | F-X-05 / F-Y-10。`docs/design-m11.md` 1.2 |
| D-77 | `jimbleRun` はアプリを<b>どこで</b>動かすか | **Gradle と同じ JVM の中で動かす**（D-76 をやめて入れ替え）。別プロセスは<b>本番と同じ状態で動く</b>のが利点だったが、IDE のデバッガが繋がらない。リモートデバッガを別に繋ぐ形（D-76）にしても、**作り直すたびに繋ぎ直しが要り、忘れる**。開発ツールとして手間のほうが勝った。入れ替えは<b>クラスローダを作り直す</b>（要件の「クラスローダを差し替えないのはなぜか」もここで撤回）。あわせて **`io.jimble.core.lifecycle.Shutdown` に「止め方」を1か所で預ける**形にした。プロセスを殺せないので、止め忘れたものは<b>入れ替え後と二重に動く</b>（同じジョブが2回走る）。止める側（プラグイン）が<b>クラス名を1つずつ知っている</b>形にすると、新しく何かを立てるたびに直し忘れて黙って止まらなくなる。**Gradle デーモンの JVM がツールチェーンより古ければ起動時に落とす**（`UnsupportedClassVersionError` からは IDE の Gradle JVM 設定に辿り着けない）。<b>D-48 が挙げていた4点の始末</b>：**(a)** クラスローダは<b>きれいに止まったときだけ</b>閉じる（残っているのに閉じると `NoClassDefFoundError` を撒く）、**(b)** 二重起動は `Shutdown` と残存スレッドの通知で対処、**(c)** アプリが `System.exit()` を呼ぶと<b>Gradle ごと落ちる。これは防げないので書いておく</b>（Java 24 以降 `SecurityManager` が無い）、**(d)** アプリに求めるのは `main(String[])` だけ | F-X-02 / F-X-05。`docs/design-m11.md` 12 章 |
| D-90 | レートリミット（F-R-15） | **ルートに宣言し、ディスパッチャが `before` より前に見る。**止めると決めたリクエストに認証や DB を触らせないためである。数え方は<b>トークンバケット</b>（固定の窓は境目で一瞬 2N 回通る）。置き場は `memory`（既定）/ `redis` / `db` を `rate_limit.store` で選ぶ。**1回ぶん数えるのは不可分でなければならない**ので、Redis は Lua、DB は `SELECT ... FOR UPDATE` で1往復に閉じる（読んで足して書く、を分けると同時に来たぶんが数え落ちる）。<b>数えられなかったときは通す</b>（Redis が落ちただけで全部 429 になるほうが困る）。ただし黙っては通さずログに出す。宣言は before と同じ<b>レキシカルスコープ</b>（D-69）で、内側とルートの属性が勝つ。bucket4j は入れず自前（依存を増やさない） | F-R-15 / F-R-22。`RateLimitTest` |
| D-91 | グレースフルシャットダウン | **いきなり止めない。**(1) 止め始めた印を立てる（`Shutdown.isStopping()`。<b>ヘルスチェックだけ</b>が落ち、普通のリクエストはまだ受ける）(2) `server.shutdown_grace_seconds` 待つ（ロードバランサがこの台を外すまで。ここで 503 を返すと<b>外から見たらエラー</b>になる）(3) 新規を 503 で断つ (4) 処理中が終わるのを `server.shutdown_timeout_seconds`（既定 15 秒）まで待つ (5) 止める。**SIGTERM でこれが走る**（`Shutdown.installJvmHook`）。<b>ホットリロードのときは JVM フックを付けない</b>（入れ替えのたびに Gradle デーモンへ溜まり、古いクラスローダを掴む）。断つ印は<b>サーバーごと</b>に持つ（static にすると1つ止めただけで同じ JVM の別のサーバーまで断つ）。待ちきれなければ<b>残ったまま止めて warn</b>（止まらないほうが困る） | F-X-05 / NF-O。`GracefulShutdownTest` |
| D-92 | 実装済みの API を MCP からも出す | **ディスパッチャに「送らない呼び出し」を1本足し、MCP のツールはそれを呼ぶ。**API を作ったあとで MCP も出すとき、<b>ハンドラを2度書くと必ずどちらかが古くなる。</b>ドメイン層を共有する形（design-m9 4章）が第一だが、**検証・整形・権限まで含めて「API として組み上がったもの」をそのまま出したい**ことがある。作りは<b>出力口を差し替えるだけ</b>で、`Dispatcher.handle` はそのまま通る。したがって `before` / `after` / エラーハンドラ / 流量制限が<b>必ず同じように効く</b>（MCP 用の別経路を作ると、認証を足したときに片方だけ忘れる）。内側は別のコンテキストだが**実行IDは外側から引き継ぐ**（ログが1本に繋がる）。<b>アクセスログは二重に出さない</b>（1リクエスト1行で数えている集計がずれる）。書き込み直後の参照先（F-D-19）は<b>外側と共有する</b>（別々に持つと、MCP で登録した直後に一覧を引いて「いま入れたものが無い」になる）。入れ子には上限（8）を置く（自分を呼ぶルートを1つ作ると `StackOverflowError` まで潜る）。結果は 2xx の JSON を構造化出力に、**4xx は本文ごと**（モデルが読んで直せる）、**5xx は本文を伏せて**（要件 F-MCP-11。500 の本文にはスタックトレースが入り得る）`isError` にする | F-W-27 / F-MCP-15。`InternalCallTest` / `RouteToolTest`。`docs/design-m11.md` 16 章 |
| D-95 | SQL 結果キャッシュの既定 | **既定は無効（`sql_cache.enabled = false`）。**有効だと、`selectCached` を1度も呼んでいないアプリでも<b>すべての更新で「どの行に当たるか」を組み立てる</b>（WHERE を読み、テーブルのキーを引く）。<b>使っていない機能の代金を全員が払う</b>形になる。切っているときは、削除の予定を組み立てるところまで含めて<b>1行も走らない</b>（判定は設定インスタンスの参照比較1つ。`Conf` を読み直したときだけ設定を引き直す）。既定を無効にすると<b>「書いたのに効かない」</b>を作りうるので、D-86 / D-89 と同じ手当てを入れる：**起動時の構成ログに `sql_cache=off` を出し**、切ったまま `selectCached` を呼んだら<b>初回だけ warn を出す</b>（毎回は出さない）。<b>例外にはしない</b>。本番で調べるためにキャッシュを切ったらアプリごと止まる、というのは行きすぎである | F-D-28。`SqlCacheConfTest` / `SqlCacheIntegrationTest` |
| D-94 | SQL 結果キャッシュの無効化の判断 | **依存を「タグ」で持ち、SELECT の結果行と UPDATE の WHERE から作る。**キャッシュ1件に複数のタグが付く（既存の `ICache` はキー1つにグループ1つなので、「customer の1行と shop の1行の両方に依存している」を表せない）。タグは3種類：**行タグ**（`customer#id#1`。主キー・一意キーの値）、**一覧タグ**（`customer#*`。行の増減で影響する）、**読み取りタグ**（`customer#@`。どの行に当たるか読めない更新のときだけ消す）。SELECT の依存は<b>結果行から</b>拾う（SELECT の結果はテーブル名でネストしている（F-D-02）ので、**結合先のキーもそのまま取れる**）。更新の影響は<b>WHERE の内省から</b>求める（`IWhere` / `ICondition` に読み取り用の口を新設）。<b>読めないものは必ず安全側（テーブルごと消す）に倒す</b>ので、機能を足しても古いデータは出ない。**キャッシュするのは明示的に頼んだ SELECT だけ**（原則5）で、<b>消すのは常に自動</b>。逆にすると消し忘れが黙って古いデータになる。トランザクション中は<b>読みも書きもせず</b>（未確定の値を残さない）、コミットでまとめて消す。値は<b>JSON にせず直列化する</b>（JSON にすると `BigDecimal` が `Float` に丸まり、`Long` が `Integer` になり、`byte[]` が数値の配列になり、`"true"` という文字列が `Boolean` になる。**キャッシュに当たったときだけ結果が変わる**という、いちばん気づけない形になる）。期限の既定は 300 秒（無期限にすると、取りこぼしが<b>直るきっかけを持たない</b>） | F-D-28。`SqlCacheTagsTest` / `SqlCacheIntegrationTest`。`docs/design-m11.md` 18 章 |
| D-93 | 同じ `AsyncData` を面ごとに違う形で返す | **形は出力するときに選ぶ**（`TableNest.ON` / `OFF` / `AS_IS`）。SELECT の結果はテーブル名でネストしている（F-D-02）が、それを平らにするかどうかは<b>`setData` の書き方で固定されていた</b>。管理画面はネスト、ショップはフラット、という要求に対して<b>同じ内容のクラスを2つ書く</b>ことになり、片方だけ直したときに気づけない。<b>どのキーがどのテーブルの列かは、生の読み込みデータが知っている</b>（`load()` の戻りはテーブル名でネストした SELECT の結果そのもので、`AsyncData` / `AsyncList` はそれを保持したままである）。そこから「テーブル名 → 列名」を導けば、どちらの形にも組み直せる。**実装側に書き足すものは無い。**`table()` を宣言させる案は<b>書き忘れると黙ってネストしなくなる</b>ので採らなかった（「書いたのに効かない」の裏返しで、書き忘れに気づく手がかりが無い）。列でないキー（`setRelationData` が足した子・計算値）は<b>常に最上位に残す</b>。テーブルとみなすのは<b>生データのトップレベルで値が `Data` のキーだけ</b>なので、自由 SQL や集約のようにネストしていない結果は組み直さずそのまま出す。既定は `AS_IS` で、<b>既存の出力は1バイトも変わらない</b> | F-A-11。`TableNestTest` / `ResponseTableNestTest` / `AsyncIntegrationTest`。`docs/design-m11.md` 17 章 |
| D-96 | MySQL 以外の RDBMS への広げ方 | **方言を1つのインタフェースに閉じ、SQL を組み立てるときに DB から渡す。**「SQL 文字列を作ってから置換する」案は採らなかった（文字列リテラルの中のバッククォートまで書き換えてしまい、<b>データが静かに変わる</b>）。「起動時に静的な方言を選ぶ」案も採らなかった（複数データソースで製品を混ぜられなくなる）。したがって `*Sql(StringBuilder)` を<b>`SqlWriter`（StringBuilder + 方言）</b>に置き換え、ビルダーは `sql(Dialect)` を受け取る。引数なしの `sql()` は主データソースの方言に落ちる。**その製品に語彙が無いものは組み立てた時点で `DialectException`**（要件 F-D-30）。とくに<b>名前を置き換えるだけでは合わないもの</b>は投げるか書き換える：`DATE_FORMAT` は `to_char` と書式の言語が違うので<b>投げる</b>、`CONCAT` は PostgreSQL では NULL を飲み込むので<b>`\|\|` に書き換える</b>（どちらも「動くけれど値が違う」になる）。全文検索（`MATCH ... AGAINST`）も投げる。テーブル定義クラスの生成は<b>読み方だけを製品ごとに分ける</b>（D-98）。**空間関数の軸の順（MySQL の SRID 4326 は緯度・経度、PostGIS は経度・緯度）は吸収しない**ので、両方で使うアプリは自分で合わせる | F-D-30。`DialectTest` / `SqlBuilderDialectTest` / `pgTest`。`docs/design-m11.md` 19 章 |
| D-97 | フレームワーク内部テーブルの製品差 | **DDL は `DBVersion` に製品別で持つ**（`.mysql(...)` / `.postgresql(...)` / `.any(...)`）。片方しか無い版を流そうとしたら<b>例外にする</b>（黙って飛ばすと、テーブルが無いまま起動して使ったところで落ちる）。版の記録は<b>テーブルコメント</b>のままでよい（両製品にコメントがある）。引き方・書き方だけを方言に寄せた。生 SQL の upsert と `INSERT IGNORE` は `Sqls` の小道具に寄せる（PostgreSQL は<b>「どのキーで重複を見るか」を書かせる</b>ので、テーブル定義から主キー・一意キーを渡す。**入れようとしている列で埋まっているキー**を選ぶ：採番の主キー ＋ 業務上の一意キー、という定番の形で主キーしか見ないと衝突を見つけられない）。採番値の取り出しも違う（MySQL は採番列だけ、**PostgreSQL は行を丸ごと返す**）ので、`id` が無ければ<b>当てにいかず</b>件数を返す（それらしい別の列の値を採番値として返すほうが危ない） | F-D-30。`DialectTest` / `pgTest` |
| D-98 | テーブル定義クラスの生成の製品差 | **読む口を1つに揃え、生成の本体は分岐させない**（`TableMetaReader`）。MySQL は `SHOW TABLE STATUS` / `SHOW FULL COLUMNS` / `SHOW INDEX`、PostgreSQL は `pg_catalog` を引く。返る列の名前も意味も揃っていないので、<b>読んだ直後にキー名と型を揃える</b>（`name` / `type` / `nullable` / `primary_key` / `comment` / `default_value` など）。**MySQL 側は移送元と同じ `SHOW ...` のまま**にした（`information_schema` に寄せると既定値や型名の表記がわずかに変わり、<b>生成物が静かに変わる</b>）。PostgreSQL 側で気をつけたこと：**連番は「型 ＋ nextval の既定値」でしかない**ので `bigserial` に戻して既定値を落とす、既定値の `'abc'::character varying` からキャストと引用符を落として MySQL と同じ形にする（ただし<b>キャスト1つの形のときだけ</b>。`'a'::text \|\| 'b'::text` で最初の `::` を切ると残り半分が黙って消える）、**式インデックスは丸ごと捨てる**（式の列は `indkey` に 0 が入り、落とすと<b>実際には一意でない列の組を一意キーとして宣言する</b>）、**部分インデックスは一意キーに数えない**（条件に合う行の中でしか一意でない）、`indisvalid = false` と `INCLUDE` の非キー列を除く、パーティションの子テーブルを出さない。**カタログを引けなかったら落とす**（`selectList` は失敗しても null を返すので、そのまま空として通すと<b>列も一意キーも無いクラス</b>が生成される）。生成する `SchemaSQL` はその製品の DDL で書く（PostgreSQL は列の定義にコメントを書けないので `COMMENT ON` を別に出し、`add unique index` は制約に、`add index` は `create index` にする） | F-D-30。`GeneratorIntegrationTest`（`dbTest` / `pgTest` の両方） |
| D-99 | DSL を増やすときの決まり | **書き出す順とバインドする順を、どの製品でも同じにする。**`Dialect` が引数を並べ替えると、`?` が2つ以上あるときに<b>値が入れ替わって黙って別の答え</b>になる（PostgreSQL の `STRPOS(対象, 探すもの)` は MySQL の `LOCATE(探すもの, 対象)` と逆なので `POSITION(x IN y)` を使う、MySQL の `TIMESTAMPDIFF(SECOND, to, from)` は引き算で書く）。**名前だけ違うものは `SqlFunction` に並べ、構造が違うものだけ `Dialect` のメソッドにする。**「同じ名前で意味が違う」ものは吸収する：`LENGTH`（MySQL はバイト数・PostgreSQL は文字数）は<b>文字数</b>に、曜日（MySQL は日曜が 1・PostgreSQL は 0）は<b>MySQL 側</b>に、週は<b>ISO</b>に、`EXTRACT(SECOND)`（PostgreSQL は小数秒を四捨五入する）は<b>切り捨て</b>に、`UNIX_TIMESTAMP`（PostgreSQL の timestamp は素で UTC 扱い）は<b>接続のタイムゾーン</b>に揃える。**バインドできないところがある**：MySQL の `LAG` / `LEAD` / `NTILE` の行数はリテラルでなければならない。**単位や型は文字列で受け取らない**（`DateUnit` / `CastType`。SQL にそのまま埋め込むところなので、外から来た文字列を通すと書き換えられる）。引数に一覧を渡されたら組み立て時に落とす（`?` は1つなのにバインドは複数になり、以降が全部ずれる） | F-D-31。`SqlDslFunctionTest` / `DslIntegrationTest`（`dbTest` / `pgTest` の両方） |
| D-100 | 英語版ドキュメントの作り方 | **翻訳しないものを先に決める。**フレームワークが<b>実際に日本語で出力するもの</b>（起動ログ、バリデーションの既定メッセージ、アップロードの 413 / 400、`codegen` の生成物のヘッダ）と、<b>コード中の日本語の文字列リテラル</b>は英語版でもそのまま置く。英訳すると<b>読者の手元に出ないものを見せる</b>ことになる。ただし読めないままにはせず、括弧で英語の意味を添える。逆に、<b>説明のための図のラベル</b>と<b>コードブロックの中のコメント</b>は散文なので英訳する。**枠にも文言がある**（まとまり名・注記の見出し「補足 / 注意 / 落とし穴」・検索欄・前後のリンク・フッタ）。ここを日本語のまま出すと<b>半分だけ英語のサイト</b>になるので、`Texts` に言語ごとで持ち、`Markdown` と `SiteNav` とテンプレートに渡す。**知らない言語は既定（日本語）に落とす**（文言が1つ足りないだけでビルドが落ちるより、出てから直せるほうがよい）。文体は<b>日本語版の調子を持ち込む</b>：落とし穴を太字で名指しし、なぜそうしたかを書き、移送元の不具合を隠さない。直訳ではなく英語として自然に読める強い文にする | NF-D-06。`docs/site/en/`（35 ページ） |
| D-101 | `jimbleRun` の中で helidon に JVM 全体の直列化フィルタを張らせない | **`helidon.serialFilter.missing.action = IGNORE` を立ててからアプリを起動する。**helidon は `WebServer` を立てるときに `ObjectInputFilter.Config.setSerialFilter` で<b>JVM 全体</b>の直列化フィルタ（許可リスト ＋ `!*`）を張る。アプリが自分のプロセスで動くなら正しい守りだが、`jimbleRun` は<b>アプリを Gradle デーモンの中で動かす</b>（D-77）ので、**フィルタはデーモンに張られる**。一度きりで外せないため、**アプリを止めてもデーモンに残る**。Gradle は自分のビルドサービスのパラメータを Java 直列化で読み戻すので、次のビルドが `Couldn't populate class org.gradle.api.services.BuildServiceParameters$None > filter status: REJECTED` で<b>タスクに入る前に</b>落ちる。**「1回目は動く。止めてもう1回動かすと起動しない」**という出かたをし、デーモンを作り直す（IDE の Gradle 同期・`gradle --stop`）と直るので原因に辿り着けない。切るのは `jimbleRun` のあいだだけで、**本番はアプリが自分の JVM で動くので helidon はいつもどおり張る**。利用者が自分で `-Dhelidon.serialFilter.missing.action=...` を指定していたら何もしない | F-X-02 / D-77。`AppRunnerTest` |
| D-102 | 「どの製品でも書けない SQL」の落とし方 | **`io.jimble.db.sql.SqlBuildException` を、SQL を組み立てたところで投げる。**製品ごとの「これは書けない」は `DialectException`、<b>どの製品でも書けないもの</b>はこちら、と分ける。最初の対象は空の `IN`（F-D-07）とウィンドウ関数を条件に書いたとき（D-103）。<b>実行時に DB から返る構文エラーは、呼び出し箇所を教えてくれない</b>（D-86 / D-89 と同じ考え方） | F-D-07 / F-D-31 |
| D-103 | ウィンドウ関数を `WHERE` / `HAVING` に書けてしまう | **`Over` が条件の組み立て先ごと塞ぐ。**`AbstractDsl` は条件のメソッド（`eq` / `gt` …）を全部の DSL に生やしているので、`Dsl.rowNumber().over(...).eq(1)` と書けてしまい、<b>DB に投げるまで気づけない</b>（SQL の決まりで、ウィンドウ関数はどちらより後に評価されるため書けない）。メソッドを1つずつ塞ぐと<b>条件のメソッドを増やしたときに塞ぎ忘れる</b>ので、`AbstractDsl` に `protected IWhere where()` を1つ置いて全メソッドをそこに通し、`Over` はそれを上書きして落とす。**選択の側（`as` / `partitionBy` …）は塞がない** | F-D-31。`SqlDslFunctionTest` |
| D-104 | `BatchRegistry.sync` に1つも登録が無かったら | **「全部消えた」として、残っている行を全部 `nothing` にする。**空のときだけ何もしないと、<b>最後の1つを消したときにだけ行が `enable` のまま残る</b>（スケジューラが消したはずのバッチを回し続ける）。空のときは `NOT IN ()` を書けないので、その節ごと落とす。**裏返しに、登録を済ませる前に `sync` を呼ぶと全部の行が `nothing` になる**ので、バッチを持たないアプリは呼ばないこと。あわせて、**`batch_master` を別のアプリと共有しないこと**（消えた判定はクラス名なので、同じテーブルを見る2つのアプリは<b>お互いの行を `nothing` にし合う</b>。移送元は `created_at` を世代マーカーにしていたので、jooby_base 版と jimble 版が同じ DB を見ると必ずこうなる） | F-B-09。`BatchIntegrationTest` |
| D-105 | `NOT IN` を条件として読めるようにする | **`WhereTerm.NOT_IN` という別の演算子にする。`IN` と同じ扱いにしてはいけない。**`IN (1, 3)` は「1 か 3 の行」だが `NOT IN (1, 3)` は<b>それ以外の全部</b>である。同じ扱いにすると、SQL 結果キャッシュ（F-D-28）が 1 と 3 の行タグだけを消し、<b>本当に書き換わった行のキャッシュが残って古い値を返し続ける</b>。`isIn()` は `NOT_IN` で true にならないので、`SqlCacheTags` は読めないもの扱い＝<b>テーブルごと消す安全側</b>に倒れる。読めるようにしたのは `In` と対称にするためで、絞り込みには使わない | F-D-28。`SqlCacheTagsTest` |
| D-68 | jimble が作るテーブルの名前をどこに置くか | **`io.jimble.db.FrameworkTables` 1か所に置き、作る側も外す側もそこの定数を使う。**以前は作る側（`Migration` / `DBLock` / `BatchTables` / `SessionConf` …）と外す側（`GeneratorConf`）に同じ名前が並んでいて、<b>テーブルを増やしたときに外す側を足し忘れる</b>と、フレームワークの内部テーブルがアプリのテーブル定義クラスとして生成された。生成された時点では何も壊れないので<b>アプリのコードに紛れてから気づく</b>。逆向き（外す側を1か所にして作る側が登録する）は、codegen が別プロセス（`JimbleDbCli`）で走るため<b>登録するクラスが読み込まれず</b>成立しない。**定数を足して `ALL` に足し忘れる**のが唯一の抜け道なので、そこは `FrameworkTablesTest` が塞ぐ。<b>MQ のテーブル（名前はアプリが決める）と、既定名から変えた DB セッションのテーブルはここに書けない</b>ので `codegen.exclude_tables` に足す | `FrameworkTablesTest` / `GeneratorIntegrationTest`。`docs/design-m11.md` 4-1 |
| D-70 | `SpaController` / `AssetController` / `MpaController` が登録したルートを外に出す | **`routes()` で返す**（登録順、変更不可）。以前は登録して終わりだったので、`install` した子のルートに `AttributeKey`（認証の除外や流量制限）を付ける手が無く、<b>パス文字列を突き合わせる回避策</b>が書かれていた。クラスは `final` のままにする（継承させるのではなく、ルートを渡す）。**あわせて、SPA を `/` に置いたときに `/` 自身を登録していなかったのを直した**（`"/*"` はセグメントが0個の `/` に当たらないので、<b>トップページだけ 404</b> になり、`/any` は 200 で返るので気づきにくい） | `AssetHandlerTest` / `SpaMpaTest`。`docs/design-m11.md` 4-3 |
| D-15 | 移送してきたコードの警告をどう畳むか（**完了**） | **種別ごとの抑止をやめ、`-Werror` にする。**`jimble-util` は移送時点の警告が多く、`unchecked` / `rawtypes` / `fallthrough` / `deprecation` / `dangling-doc-comments` / `this-escape` / `cast` / `overloads` を種別ごと落とし、**javadoc の doclint も切っていた**。それだと<b>切っている場所で壊れても気づけない</b>（`publishToMavenLocal` が javadoc で落ちて初めて分かる、という形でしか出てこなかった）。2026-09-08 に<b>コンパイル警告 81 件と doclint 45 件を潰し、抑止を全部やめた</b>。消せないものは<b>消せない理由を書いた `@SuppressWarnings` をその場所に</b>付ける（`(E) this` の自己型は `self()` 1か所に閉じる、`Data` の可変長総称型は `@SafeVarargs`、日付の丸めと JSON パーサの<b>意図した switch 落ち</b>は `@SuppressWarnings("fallthrough")`、`new URL(String)` は URI に置き換えると入力が弾かれるので据え置き）。**種別ごとに全体で切るのはやめる**が、`this-escape` だけは例外で落とす（D-106 の理由と同じく、jimble の「コンストラクタで登録する」形に由来し、<b>正しく書いたコントローラが必ず警告を出す</b>。登録の口は全部 `protected final` なので安全） | `build.gradle.kts` / `jimble-util/build.gradle.kts` / `gradle-plugin/build.gradle.kts` |
| D-106 | ヘルスチェックのルートを誰が持つか | **フレームワークでは持たない。アプリが書く**（要件 NF-O-03）。グレースフルシャットダウン（D-91）は「**先にヘルスチェックだけ落として、処理中のリクエストは返しきる**」という順番を要る。フレームワークが握ると<b>その順番をアプリから決められない</b>。標準で生やすと、認証の除外や流量制限の扱いもフレームワーク側の判断になる。**そのかわり書き方をドキュメントに載せる**（`server.md`「ヘルスチェックを先に落とす」／`routing.md` の `NO_AUTH`） | NF-O-03 / D-91。`docs/site/ja/server.md` |
| D-107 | 「未エンコードでもエンコード済みでも正しく整う」URL のエンコード | **`UrlUtil.normalizeUrl` を自分で書く（べき等）。**受け取る側は<b>どちらを渡されるか決められない</b>ので、2回通すと壊れる関数はどこかで必ず2回通る。**べき等の要は「`%` のあとに16進が2桁続いていたら触らない、続いていなければ `%25` にする」**（`%25` はその形なので2回目以降も動かない）。「`%20` という<b>文字列そのもの</b>を入れたい」場合だけは区別できない、と割り切る（javadoc に明記）。**解析は `URL` にも `URI` にもさせない**（`URL` は非推奨、`URI` は空白・`|`・生の日本語を入口で落とす）。RFC 3986 付録B の正規表現で5つに割り、部分ごとに許される文字を見て UTF-8 で `%XX` にする。**ホストは punycode**（`IDN.toASCII`。パーセントエンコードすると DNS が引けない）。**ライブラリは入れない**：WHATWG 準拠の `galimatias` は動きは正しいが 0.2.1（2015 年）で止まっており、`httpcore5` の `URIBuilder` は `URI` と同じく生の空白で落ちる。80 行で書けるものに、止まった依存を1つ増やす理由が無い。<b>移送してきた `fullUrlEncode`（二重エンコード・ホストをパーセントエンコード）と `urlToEncodeUrl`（パスの空白を `+` にする）はどちらも呼ばれていない</b>ので、javadoc から新しいほうへ案内するだけにした | F-Y-05。`UrlNormalizeTest` |
| D-108 | CI をどう組むか | **GitHub Actions の標準ランナー（`ubuntu-latest`）だけで組む。**公開リポジトリなので実行時間は無料で、larger runner は使わない。**3つのジョブに分ける**：`build`（DB を使わない。単体テスト・javadoc・`-Werror`・Gradle プラグイン・ドキュメントサイトの生成）／`db`（MariaDB + Redis で `dbTest` と `codegenCheck` と `migrate → codegen → compileJava` の連鎖）／`pg`（PostgreSQL + Redis で `pgTest`）。**`db` と `pg` を分けたのは、片方だけ落ちたときに「製品差の問題」だと分かる**ためである（1つにまとめるとログを追わないと分からない）。**DB の URL は上書きしない**：`examples/blog` の `application.conf` も `JIMBLE_TEST_DB_URL` を見ているので、ここで差し替えると<b>blog が `jimble_test` を向いてしまう</b>。サービスのポートを既定（3306 / 5432 / 6379）に合わせ、MariaDB だけ<b>スキーマを自分で作れる権限が要る</b>ので利用者名だけ root に上書きする（PostgreSQL の `POSTGRES_USER` はスーパーユーザーなので既定のままでよい）。脆弱性は自分でスキャンせず、依存の一覧を Dependency graph に渡して Dependabot alerts に当てる（NF-S-07。OWASP dependency-check は NVD の API キーが要るうえ、<b>キーが切れた日に黙って何も見なくなる</b>） | F-G-14 / NF-S-07 / NF-T-06 / NF-T-07。`.github/workflows/ci.yml` |
| D-109 | 入口が複数あるアプリで、起動時に用意するものをどこに置くか | **1か所にまとめ、どの入口もそれを呼ぶ**（`examples/blog` の `Bootstrap`）。`examples/blog` は Web・バッチ・スケジューラの3つの入口があり、**MQ のテーブルを作っていたのはバッチとスケジューラだけ**だった。ところが<b>キューに積むのは Web</b>（記事の登録）なので、まっさらな DB で Web だけ立てると<b>「トランザクションのコミットに失敗しました」で 500</b> になる。<b>いちどでもバッチを動かしたマシンでは動いてしまう</b>ので手元では気づけず、**CI（まっさらな DB）を入れた初回に初めて出た**（D-108）。原則1（起動の順番が入口に全部書いてある）とは折り合いを付ける：同じことを3か所に書くと<b>そのうち1か所だけ古くなる</b>ほうが害が大きい。順番は `Bootstrap` に上から書いてあるので、入口からは1行辿れば読める。**テストも同じものを呼ぶ**（テストだけ別に用意すると、入口が変わったときに<b>テストだけ通る</b>） | NF-T-06。`BlogAppIntegrationTest` / `docs/site/ja/pitfalls.md` |
| D-110 | 止め方をいつ預けるか | **待ち受けを始める前に預ける。**`JimbleServer` は `WebServer` を start してから `Shutdown.add(...)` を呼んでいたので、**あいだに `jimbleRun` が止めに来ると、預かっているものがまだ無く、何も止まらない**（古いアプリがポートを握ったまま残り、入れ替えたほうが立ち上がれない）。ログ2行を挟んでいたので<b>隙間は実測できる長さ</b>だった。`builder.build()` と `start()` を分け、あいだで登録する。**登録名に番号を入れない**（番号が決まるのは start のあとで、ポート 0 では 0 になる）。あわせて `stop()` は<b>まだ待ち受けていなければ即座に抜ける</b>（抜けないと猶予のぶん黙って寝る）。`AppRunner` の javadoc にも「止め方は待ち受けを始める前に預けること」と書いた。**これは `gradle-plugin` のテストが 7 回に 1 回落ちる形で出ていた**（テストはポートが開いた瞬間に止めるので、隙間をまともに踏む） | D-77 / D-91。`AppRunnerTest` |
| D-111 | Gradle プラグインの作法を手元でも Gradle 9 と同じ厳しさで見る | **`enableStricterValidation = true` を入れる。**Gradle 8 は「キャッシュの可否を書いていない」「入力の正規化を書いていない」を<b>警告で流す</b>が、Gradle 9 は<b>エラーで落とす</b>。手元が 8 のままだと<b>CI で初めて分かる</b>（実際そうなった）。直した内容：`GenerateJteTask` は **`@CacheableTask`**（生成物に絶対パスも時刻も入らないので、別のマシンで作ったものを使い回せる）、`JimbleRunTask` と `CodegenCheckTask` は **`@DisableCachingByDefault`**（前者は居座るタスクで作るものが無い、後者は出力を持たない）、`runtimeClasspath` は **`@Classpath`**（並び順は意味を持ち、置き場所は持たない）、`CodegenCheckTask` の入力ディレクトリは **`@PathSensitive(RELATIVE)`**。**javadoc の警告 14 件も潰した**（残しておくと、この手のエラーがログの山に埋もれて見えない。要件 D-15 と同じ話） | D-15。`gradle-plugin/build.gradle.kts` |
| D-112 | `pgTest` を持たないモジュールをどうするか | **`application.pgtest.conf` が無いモジュールでは飛ばす**（`onlyIf`）。`Conf` は環境別ファイルが無ければ `application.conf` に落ちるので、そのままだと<b>PostgreSQL のつもりで MySQL に繋ぎに行く</b>。手元は MySQL も立っているので通ってしまい、**CI の `pg` ジョブ（PostgreSQL しか無い）で初めて落ちた**（`examples/blog`。`NullPointerException: dbSource is null`）。`dbTest` のほうは落ちた先が MySQL なので<b>`application.conf` に落ちるのが意図どおり</b>（`examples/blog` はそれで動く）ため、同じ判定を付けない。**飛ばしたことは `SKIPPED` としてログに出る**ので、conf を足し忘れたモジュールは目で見える。`examples/blog` を PostgreSQL でも通すには<b>マイグレーションを両製品で書き直す</b>必要があり、それは D-113 でやった | F-D-30 / D-108。`build.gradle.kts` |
| D-113 | マイグレーション SQL の製品差の書き分け | **ファイル名の接尾辞で分ける**（`001_create_post.mysql.sql` / `001_create_post.postgresql.sql`。接尾辞なしは両方で流れる）。案は3つあった：**(a)** ディレクトリを分ける（`migration/mysql/...`）、**(b)** ファイルの中に `# --- !Ups[postgresql]` のような印を書く、**(c)** ファイル名の接尾辞。(a) は<b>両方で通る SQL を2か所に置く</b>ことになり、片方だけ直したときに気づけない。(b) は<b>1つの SQL に製品差が混ざる</b>ので、適用済みのハッシュが製品をまたいで同じになり「書き換えられた」の判定（F-G-10）と噛み合わない。(c) なら<b>`ls` で並べたときに、どの版がどの製品にあるかが目で見える</b>し、両方で通るものは1つだけ置ける。接尾辞の判定は `Dialects.productNameOrNull` に寄せ、**設定に書ける名前とまったく同じ表を通す**（表が2つあると、`mariadb` と書いた人のファイルが<b>置いたのに流れない</b>）。<b>適用済みはファイル名で覚えている</b>ので、製品ごとに別の記録になり、片方の製品で流したものをもう片方が「消えた」と見て down することはない。**いちばん危ないのは移行時で、すでに流した `001_create_post.sql` を `001_create_post.mysql.sql` に変えると、別のファイルとして同じ SQL がもう一度流れる**（`Table 'post' already exists`）。中身が同じなら名前が変わったと見なして<b>止め、履歴の `UPDATE` 文を出す</b>（履歴の付け替えを勝手にはやらない。中身が同じでも別の意図かもしれないし、履歴を黙って書き換えると後から追えない）。**見分けるのは中身のハッシュではなく、製品の接尾辞を落とした名前である**（ハッシュで見ると、分けた先の SQL は製品ごとに中身が違うので<b>片方の製品でしか気づけず</b>、もう片方は同じ DDL を二度流して落ちる。たまたま中身が同じだけの別のファイルも取り違える）。中身まで変わっているときは `hash` も一緒に入れ替える SQL を出す（その版は<b>すでに当たっている</b>ので、当て直すのではなく記録を合わせる）。`up_error` のまま名前が変わったときは<b>止めない</b>（そちらは履歴を消してやり直す道が元からある）。**消えた判定は「製品で絞る前の一覧」で行う**（絞ったほうを見ると、他の製品向けのファイルが「消えた」ことになり、<b>相手の製品で流したテーブルを down する</b>。論理ダンプの移し替えや `product` の書き換えで、他の製品の履歴が残っている DB では実際に起きる）。**いまの製品向けのファイルが1つも無くなったときも止める**（`001_x.sql` を `001_x.postgresql.sql` にだけ変えると、MySQL の DB では二度と読まれない）。**同じ版がいまの製品で2つとも流れる場合も止める**（`.mariadb.sql` と `.mysql.sql` はどちらも MySQL で流れ、ファイル名が違うので記録も別々になり、2つ目が「already exists」で落ちるまで気づけない）。**`ALTER TABLE` で足した列の位置は吸収しない**（MySQL は `AFTER body` が書けるが PostgreSQL は必ず末尾）。生成される列の並びは物理順なので、<b>同じスキーマでも製品によって並びが変わる</b>。型も NULL 可否もコメントも一致するので、生成は主にする製品で行い `codegenCheck` も同じ製品で回す、と決めた | F-G-20 / F-D-30 / D-112。`MigrationIntegrationTest`（`dbTest` / `pgTest` の両方）|
| D-114 | マイグレーション SQL を1文ずつに切り分けるときの製品差 | **どこからどこまでが文字列・識別子・コメントなのかを方言に聞く**（`Dialect.sqlSyntax()` → `SqlSyntax`）。移送元からの `MigrationSql.split` は<b>製品にかかわらず MySQL の読み方</b>をしていた。バックスラッシュを常にエスケープとして読むので、PostgreSQL の `insert into t values ('c:\');`（`standard_conforming_strings = on` では `\` はただの文字）で<b>文字列が閉じず、後ろの SQL が全部1文にくっつく</b>。PostgreSQL 向けの SQL を置けるようにした（D-113）ことで、これは現実に踏める道になった。<b>製品ごとに違うのはこれだけ</b>なので、真偽値を持つ小さなレコードにまとめた：バックスラッシュのエスケープ（MySQL のみ。PostgreSQL は `E'...'` のときだけ）、`#` の行コメント（MySQL のみ）、`--` のあとに空白が要るか（MySQL は要る。`1--2` は「1 引く マイナス2」）、バックティックの囲み（MySQL のみ）、実行されるコメント（MySQL / MariaDB のみ）、ドル引用符 `$tag$ ... $tag$`（PostgreSQL のみ。<b>関数の本体や `DO` ブロックの中の「;」で切ると壊れる</b>）、ブロックコメントの入れ子（PostgreSQL のみ）。**あわせて、実行する SQL では改行を潰さないようにした。**それまでは読んだ直後に改行を空白へ潰していたので、<b>`--` や `#` の行コメントが行末で終わらず、そこから先の SQL が全部コメントになる</b>（潰すのはハッシュを取るときだけにした。<b>潰し方を変えると適用済みが全部「書き換えられた」ことになる</b>ので、ハッシュ側は1バイトも変えていない）。コメントだけになった断片は捨てる（MySQL は「Query was empty」で落ちる）。ただし MySQL / MariaDB の `/*!40101 ... */` `/*M!100301 ... */` は<b>実行されるコメント</b>なので捨てない。**1文も取り出せなかったら失敗にする**（「全部コメントだった」を成功として通すと、down のときに<b>テーブルは残ったまま履歴だけ消える</b>）。ドル引用符は<b>トークンの先頭の `$` だけ</b>が始まりになる（PostgreSQL は識別子に `$` を書けるので、`a$b$c` を引用符と読むと後ろが全部くっつく）。バックティックの囲みは MySQL だけ（PostgreSQL で囲みとして読むと、書き間違えた1つで後ろが全部くっつく） | F-G-05 / F-D-30 / D-113。`MigrationSqlTest` / `MigrationIntegrationTest`（`dbTest` / `pgTest` の両方）|
| D-115 | Apache Tika を 4.0.0 に上げる | **上げる。**使っているのは `tika-core`（中身からの種別判定）と `tika-parser-text-module`（CSV）だけなので、移行で要ったのは4か所である：`Parser.parse` / `Detector.detect` が `TikaInputStream` を取るようになった（`TikaInputStream.get(file.toPath())` で包む）、`Detector.detect` に `ParseContext` が増えた、`Metadata` が `HttpHeaders` / `TIFF` を実装しなくなった（`Metadata.CONTENT_TYPE` → `HttpHeaders.CONTENT_TYPE`、`Metadata.IMAGE_WIDTH` → `TIFF.IMAGE_WIDTH`）。**版を上げても答えが1つも変わらないことを、同じテストを両方の版で流して確かめた**（そのために `FileUtil` に初めてテストを付けた）。<b>そのとき、版とは関係のない穴が2つ見つかった</b>（D-116）。移行ガイドが挙げる残りの破壊的変更（XML 設定の廃止、`TikaConfig` → `TikaLoader`、既定出力の Markdown 化、tika-app / tika-server の配布形式、gRPC のパッケージ移動、埋め込み文書の抽出）は<b>どれも使っていない</b>ので影響しない。**依存は増える**：tika の推移依存から `juniversalchardet`（MPL-1.1）が消え（<b>ただし `jimble-util` が自分で入れているぶんは残った。D-119 で外した</b>）、`tika-encoding-detector-mojibuster` / `tika-ml-core` / `tika-parser-datauri-commons` と<b>commonmark（tika-core 4 が既定の出力を Markdown にしたため）</b>が入る。実行時クラスパスで tika 関連が約 900KB → 約 1.2MB、加えて commonmark が約 260KB。commonmark を `exclude` すれば減らせるが、<b>使っていない経路で `NoClassDefFoundError` になっても気づけない</b>ので外していない | NF-L-01 / F-Y-07。`FileMetaDataTest` |
| D-116 | 画像の大きさと CSV の文字コード・区切りが取れていなかった | **画像は JDK の `ImageIO`（＋ WebP は自前）で読み、CSV は tika が読んだものを捨てないようにする。**tika 4 に上げるときにテストを付けて分かった穴で、<b>版とは関係なく前からそうだった</b>。**(1) 画像の幅と高さが常に 0。**`AutoDetectParser` に画像を読む parser が無いためで、`tika-parser-image-module` を足せば直るが<b>+9 jar / 約 5.4MB（pdfbox 一式を含む）</b>になる。D-52 で gRPC を +10.4MB として断ったのと同じ話なので入れない。かわりに<b>ヘッダだけを読む</b>（画素は展開しない。アップロードでヒープを食わないため）：`ImageIO` が持っている PNG / JPEG / GIF / BMP / TIFF / WBMP と、<b>WebP はコンテナを自前で読む</b>（`ImageIO` に無い。40 行で済むものに依存を増やさない）。WebP は `VP8 ` / `VP8L` / `VP8X` で大きさの置き場所が違い、<b>`VP8 ` は中間フレームだと大きさが入っていない</b>ので、キーフレームと同期コードを確かめてから読む（確かめないと、別のファイルから<b>それらしい数が出てしまう</b>。0 になるより悪い）。**HEIC / AVIF / SVG と動画は 0 のまま。****(2) CSV の文字コードと区切りが空。**種別を名前から決め直すときに<b>文字コードごと差し替えていた</b>。文字コードは tika のメタデータから直に取り、区切りは tika が持っている名前（`comma`）を<b>文字（`,`）に戻して</b>返す。<b>区切りは中身が短いと空になる</b>（tika は自信が無ければ言わない。当てずっぽうを返すほうが困る）。あわせて振り分けの穴も直した：**種別はパラメータを落としてから見る**（ブラウザは `text/csv; charset=UTF-8` を送るので、完全一致では<b>CSV が CSV として扱われなかった</b>）、**拡張子の無い一時ファイルでも、呼び出し側が CSV だと言っていればそれを使う**（アップロードの置き場所は `temp_...` で拡張子が無く、tika は名前でしか CSV を見分けられない）。**`Tika` は使い回す**（作るたびに `META-INF/services` を走査する。ファイルを返すたびに新しく作っていた）。**本文は読み捨てる**（`BodyContentHandler(-1)` は中身を全部ヒープに溜めていた。300MB の CSV で `OutOfMemoryError` になり、<b>`Error` なので `catch (Exception)` にも引っかからない</b>） | F-Y-07。`ImageSizeTest` / `FileMetaDataTest` |
| D-117 | ドキュメントサイトの反映をいつ・どう自動化するか | **タグを打ったときだけ、GitHub Actions が作って配る先の `dist/` を置き換える**（`.github/workflows/docs.yml`）。**push のたびに反映しない。**そうすると<b>サイトだけが公開されている版より先に進む</b>——実地確認（`docs/docs-only-trial.md` の B-2）で、サイトに載っている「製品ごとに SQL を分ける」が 0.2.0 には無く、<b>PostgreSQL 向けと書いたファイルが MariaDB に当たった</b>。ドキュメントだけ直したときは Actions の画面から手で流す（理由を必須入力にして記録に残す）。**Cloudflare は配る先のリポジトリを見ている**ので、CI がやるのは push までである（`wrangler` を CI に持ち込まない。デプロイの経路を2つにしない）。あわせて<b>原稿をリポジトリに入れた</b>（`.gitignore` の `/docs/` → `/docs/*` ＋ `!/docs/site/`）。**`/docs/` のままでは `!` で戻せない**（ディレクトリごと外すと中身は戻せないという git の決まり。`.gitignore` に書いてあった例がそのままでは動かなかった）。これで CI の「ドキュメントサイトの生成」が<b>初めて意味を持つ</b>——それまで原稿がクローンに無く、**0 ページのサイトができて成功していた**（`site` タスクは 0 ページでも成功する）。空振りを止めるため、CI と反映の両方に<b>ページ数と主要ファイルの確認</b>を入れた。反映がリリースに揃うので、サイトの「版について」は「いちばん新しいリリースの内容です」に変え、節ごとに付けていた「0.2.0 には入っていません」の印は外した | NF-D-01 / NF-D-03。`.github/workflows/docs.yml` / `.github/workflows/ci.yml` |
| D-118 | GitHub Actions の action をどこまで上げるか | **`gradle/actions` は v5 で止め、ほかは最新にする。**Node 20 が非推奨になり、`actions/checkout@v4` / `actions/setup-java@v4` / `gradle/actions@v4` が警告を出すようになった。checkout は v7、`actions/upload-artifact` は v7、`setup-java` は v5、**`gradle/actions` だけ v5** にした。**v6 を断ったのはライセンスである**：v6 でキャッシュ機能が `gradle-actions-caching` という<b>MIT ではない非公開の商用コンポーネント</b>に切り出され、リリースノートに「v6 に上げることで Gradle の Terms of Use に同意したことになる」と明記された。Apache-2.0 のプロジェクトで<b>依存の縮小を第一に置いている</b>以上、Dependabot が PR を出したから merge する、で通す話ではない（D-52 と同じ判断）。**v5 は「v4 ＋ Node 24」だけ**で、入力も挙動もライセンスも v4 と同じである。断りっぱなしにすると<b>毎週 v6 の PR が来る</b>ので、`.github/dependabot.yml` に `ignore` を入れて理由をそこに書いた。上げた側で確かめること：**checkout は v7 で `pull_request_target` / `workflow_run` から fork の PR を取れなくなった**が、CI の起点は素の `pull_request` なので当たらない。**v6 から認証情報を `.git/config` ではなく別ファイルに持つ**ので、`docs.yml` の「配る先を取ってきて push する」は手で1回流して確かめる。`upload-artifact` の v5〜v7 は Node 24 化・ESM 化と `archive` の追加だけで、使っている4つの入力は変わっていない。あわせて **Dependabot の `open-pull-requests-limit` を 3 から 5 に上げた**：3本開いたままだったので、<b>非推奨になった `setup-java` の PR がいつまでも出てこなかった</b>（上限に当たると黙って出ない） | NF-S-07 / D-108 / D-117。`.github/workflows/*.yml` / `.github/dependabot.yml` |
| D-119 | 文字コードの判定を何でやるか | **tika に寄せ、`juniversalchardet` を外す。**`FileCharDetecter` は `juniversalchardet`（**MPL-1.1 / GPL-3.0 / LGPL-3.0 のトリプルライセンス**、245KB）を使っていた。tika の判定器は<b>すでにクラスパスに乗っている</b>——CSV のために入れている `tika-parser-text-module` が `tika-encoding-detector-mojibuster` を compile 依存で連れてくるので、**使っても増えるものはゼロ**である（Apache-2.0）。**選ぶ前に測った**：日本語（Shift_JIS / EUC-JP / ISO-2022-JP / UTF-8）・BOM 付き UTF-16・欧文の 94 ファイルで3つを突き合わせた。**実寸のファイル（155〜9,383 byte）28 件では juniversalchardet 27 / tika 27 / ICU4J 26**、短い日本語（14〜44 byte）45 件では **41 / 41 / 30**、合計で **87 / 86 / 73**。**ICU4J は採らない**（`icu4j` も元から入っているので同じくタダだが、<b>短い日本語に弱い</b>：16 byte の Shift_JIS を windows-1252、32 byte の EUC-JP を <b>Big5 を confidence 100 で</b>と答える。CSV のヘッダ行だけ、短い HTTP レスポンスといった場面で崩れる。そのうえ `CharsetDetector` は<b>インスタンスに状態を持つので共有できない</b>）。**言い切れないときは既定に倒す**（confidence 0.9 未満）。実測で、当たっているときの confidence は最低 0.95、純 ASCII や空のファイルには 0.10 しか付かない。ただし<b>これで誤りは弾けない</b>（外すときにも 1.00 が付く）ので、弾けるのは「材料が無いときの当てずっぽう」だけである。**判定器は使い回す**（作るたびに `META-INF/services` を走査する。D-116 で `Tika` に対してやったのと同じ）。共有して差し支えないことは<b>8 スレッド × 1,880 回で答えが1つも食い違わない</b>ことで確かめた。**返す名前が変わる**（`SHIFT_JIS` → `Shift_JIS`、`WINDOWS-1252` → `windows-1252`）。`Charset.forName` に通す使い方なら影響しない。**直らないもの**：ASCII が大半で日本語がまばらな EUC-JP は GB18030 と答える（<b>3つとも同じように外した</b>ので、判定器を変えても直らない） | NF-L-01 / D-115 / D-116。`FileCharDetecterTest` |
| D-120 | `IcuUtil` の4つの変換のために ICU4J を抱えるか | **抱えない。表を持つ。**`icu4j` は<b>14.5MB あり、実行時クラスパスの単独最大</b>だった。使っていたのは `IcuUtil` の4メソッド（ひらがな⇄カタカナ、全角⇄半角）だけで、しかも<b>jimble の中からは1か所も呼んでいない</b>（利用者向けに `jooby_base` から持ってきたもの）。依存の縮小が目標1である以上、置いておく理由が無い（D-52 / D-115 と同じ物差し）。**やり方**：ICU の `Transliterator` が返すものを<b>BMP の全文字について書き出し</b>、そこから表を起こした（手で書いていない）。できたのは 470 行・約 12KB で、**14.5MB が 12KB になった**。**確かめ方**：ICU と<b>129 万通りを突き合わせた</b>（BMP の全 1 文字 63,487／全 1 文字 × 濁点6種 380,922／かな・記号だけを混ぜた文字列 20 万件、4メソッドぶん）。食い違いは<b>意図した2種類だけ</b>で、それ以外は 0 である。**(1) `convertToHiragana` は「ー」を残す。**ICU は「ひらがなに長音符は使わない」という決まりで直前のかなの母音に開いていた（`コーヒー` → <b>`こおひい`</b>、`サーバー` → `さあばあ`、`ッー` → `っう`）。ふりがなの正規化や検索キー作りに使うものなので、<b>往復して元に戻らない</b>ほうが困る。`ヵ` → `か`、`ヶ` → `け` は ICU のままにした。**(2) 無関係な文字の Unicode 正規化をしない。**ICU の `Hiragana-Katakana` は<b>チベット文字を合成し、結合文字を正規順序へ並べ替えていた</b>（判定に使う内部処理の副作用）。かなの変換を呼んで別の文字が書き換わるのは原則5に反する。**ICU がやっていて、こちらも残したもの**：半角カナの取り込み（`ｱ` → `ア`、`｡｢｣､･` も）、濁点・半濁点の合成（<b>結合文字 `U+3099 U+309A` と半角の `ﾞ ﾟ` だけ。離れた `゛ ゜` は合成しない</b>）、囲みカタカナ（`㋐` → `ア`、47 文字）と単位の合成文字（`㌀` → `アパート`、88 文字）と `ゟ` `ヿ` の展開、`ゕ ゖ` を動かさないこと、全角⇄半角のハングル字母・矢印・罫線。**`Transliterator` は消した**（この4つからしか呼ばれていなかった） | NF-L-01 / D-52。`IcuUtilTest` |
| D-121 | 依存をどこまで削るか（棚卸し） | **アプリ1本の実行時クラスパスを 39.5MB / 102 jar から 18.0MB / 86 jar にした。**全依存を洗い、使っている API を1つずつ数えたうえで削った。**(1) redisson の使わない枝を切る（−13.5MB）。**redisson 本体は 2.8MB だが依存が 17MB 付いてきて、<b>実行時クラスパスの半分を占めていた</b>。jar の中のクラスを1つずつ数えると、`byte-buddy`（8.79MB）は `liveobject` の 6 クラス、`jodd-util` は 2 クラス、`rxjava`（2.54MB）は `RedissonRx…` の 120 クラス、`reactor-core`（1.85MB）は `RedissonReactive…` の 113 クラスからしか参照されていない。jimble が使うのは `RLock` / `RMap` / `RSet` / `RBucket` / `RKeys` / `RBatch` / `RScript` の<b>同期 API だけ</b>で、入口の `Redisson` と `Config` はどれも参照していない。**`redissonClient.reactive()` / `rxJava()` / `getLiveObjectService()` は使えなくなる**ので、build ファイルにそう書いた（必要な人は自分で足す）。**(2) `aircompressor-v3` を外す（−2.9MB）。**`Content-Encoding: zstd` を展開する<b>1行のため</b>だけに 2.87MB あった。`Accept-Encoding` からも `zstd` を外す（名乗らなければ送られてこない）。gzip / deflate / br は残る。**(3) `guava` を外す（−3.0MB）。**使っていたのは4つだけ：`Utf8.encodedLength`（3か所）→ `StringUtil.utf8Length`、`CharMatcher`（1か所）→ 数行、`Hashing.sipHash24()`（1か所）→ 自前の `SipHash`、`InternetDomainName`（2か所）→ 自前の `PublicSuffix`。**SipHash は値が DB に入っている**（`sql_cache` のタグ、`DBLock` のキー、レート制限の行）ので、長さ 0〜300 の乱数 90,300 件で guava と<b>1 bit も違わない</b>ことを確かめた。公開サフィックスは<b>表（Public Suffix List の ICANN 部分、74KB）を同梱</b>した。**表は古くなると黙って間違える**が、これは guava も同じ（guava の版を上げるまで古いまま）で、抱え方が変わっただけである。取得日と取り直し方をファイルの先頭に書いた。**MPL-2.0 のデータを抱え直すことになる**のは承知のうえで、2メソッドを捨てるより表を持つほうを選んだ。**(4) `commons-validator` を外す（−1.25MB）。**使っていたのは `InetAddressValidator` 1か所で、`commons-beanutils` / `commons-digester` / `commons-collections` / `commons-logging` という<b>古い4本</b>を連れてきていた。**(5) `commons-text` を外す（−0.93MB）。**使っていたのは `escapeXml10` だけ。**(6) 使っていない宣言を消す。**`jimble-db` の `caffeine` と `jimble-docs` の `commonmark-anchor` は参照ゼロだった。**`jimble-web` の `helidon-encoding-gzip` は import が無いが消してはいけない**（`META-INF/services` の SPI。外すと<b>コンパイルは通ったまま gzip が黙って止まる</b>）ので、build ファイルにそう書いた。**確かめ方は全部「置き換える前と突き合わせる」**：IP 判定 540,066 件、公開サフィックス 334,784 件、XML エスケープは BMP 全 65,536 文字、SipHash 90,300 件、`utf8Length` と制御文字の除去は 20 万件——<b>すべて食い違い 0</b>。公開サフィックスだけ 126 件ずれたが、いずれも<b>guava の抱えている一覧が古い</b>ためで（`.web` のような新しい TLD、ルールが変わった `kh`）、こちらが新しい。**ついでに1つバグが見つかった**：`UrlUtil.isIPUrl` は `URI.getHost()` が返す `[::1]` の角かっこを外していなかったので、<b>IPv6 の URL で一度も true にならなかった</b>。**残したもの**：`jts-core`（1.18MB。WKB / WKT のパーサを自前で持つものではない）、`caffeine`（1.0MB。当たりの良いキャッシュは自分で書くと壊す）、`HikariCP` ＋ `Agroal`（合計 0.37MB。`connection_pool_type` で選べるようにしているぶん）、`tika`（2.4MB。D-115 / D-119 で判断済み） | NF-L-01 / D-52 / D-115 / D-119 / D-120。`SipHashTest` / `PublicSuffixTest` / `IpAddressTest` / `XmlEscapeTest` / `StringUtilTest` |
| D-122 | BOM は自分で読む | **`ServiceLoader` で集める判定器は、並び順が jar の並び順で決まる。**tika の `DefaultEncodingDetector` は BOM を見る判定器（`BOMDetector`）と統計で当てる判定器（`MojibusterEncodingDetector`）を`META-INF/services` から集めて<b>先頭から順に聞く</b>。どちらも確信度 1.00 を返すので、<b>どちらが先に来るかで答えが変わる</b>。手元で組んだクラスパスは tika-core が先だったので BOM が勝ち、**CI（Gradle が並べたクラスパス）は mojibuster が先だったので、UTF-16LE の BOM 付きファイルが GB18030 になった**。UTF-16 の日本語は統計で見ると GB18030 に見えるためである。<b>同じ入力・同じ版で、jar の並び順だけで答えが変わる</b>という質の悪い形で、手元では再現しなかった。**直し方は「BOM を自分で読む」**（`FileCharDetecter.bomCharset`）。5 種類（UTF-8 / UTF-16LE / UTF-16BE / UTF-32LE / UTF-32BE）を自分で見て、無ければ tika に聞く。<b>`UTF-32LE` は `UTF-16LE` と頭2 byte が同じ</b>なので先に見る。BOM が無いときは `BOMDetector` も `MetadataCharsetDetector`（`Metadata` を渡していない）も何も返さないので、結局 mojibuster しか答えず、<b>並び順に関係なく同じ答えになる</b>。94 ファイルを2つの並び順で流して、答えが1件も違わないことを確かめた。**教訓**：`ServiceLoader` で集めたものを「先頭から順に」使う API は、<b>クラスパスの並びという見えない入力</b>を持っている。手元とビルドサーバで並びが違えば、テストが通ったまま本番だけ壊れる | D-119。`FileCharDetecterTest` |
| D-123 | JUnit 6 と commonmark 0.30 に上げるか | **どちらも上げる。****JUnit 5.14.4 → 6.1.3。**メジャーだが、jimble が使っているのは `@Test` / `@DisplayName` / `@BeforeAll` / `@AfterAll` / `@BeforeEach` / `@AfterEach` / `@Tag` / `@TempDir` と `Assertions` だけで、<b>パッケージ名も型も変わっていない</b>。6.0 の要求は Java 17 以上（jimble は 25）。消えたのは `junit-platform-jfr` と `junit-jupiter-migrationsupport`、`junit.jupiter.tempdir.scope`、`@Parameterized` の CSV パーサの入れ替えで、<b>どれも使っていない</b>。座標も同じ（`org.junit:junit-bom` / `org.junit.jupiter:junit-jupiter` / `org.junit.platform:junit-platform-launcher`）なので、<b>版の数字を1つ変えるだけ</b>。確かめ方：6.1.3 で `jimble-util` の 236 件を `-Werror` 込みで通し、全モジュールのテスト 79 ファイルのコンパイルも通した。**Gradle 9.7.1 が JUnit Platform 6 を動かせるか**が唯一の不安だったが、Dependabot の PR で CI（build / plugin / db / pg の4ジョブ）が緑になっているので確認済みである。**`gradle-plugin` は別ビルドで版を直書きしている**（版カタログを見ていない）ので、<b>2か所を同時に上げる</b>こと。**commonmark 0.26.0 → 0.30.0。**0.27〜0.30 に<b>壊す変更は無い</b>（足したものと、病的な入力への防御）。既定値が3つ増えた：表のセル数の上限 100 万、`maxOpenBlockParsers` 100、`maxInlineNesting` 100。あわせて<b>スタックオーバーフローと二次関数的な遅さの修正</b>が入っている。tika 4 も commonmark を連れてくるので、上げると<b>アプリの実行時クラスパス側も 0.30 になる</b>。確かめ方：**ドキュメントサイトの全 70 ページを 0.26 と 0.30 で描き分けて、生成される HTML が 1 byte も違わない**ことを確認し、tika を使うテスト（`FileMetaDataTest` / `FileCharDetecterTest`）も 0.30 で通した。**Dependabot の PR は勝手に閉じた**：`commonmark-ext-heading-anchor` と1本にまとめられていて、D-121 でその1つを外したため「もう依存していない」と判断されたためである。<b>残り2つは上がっていない</b>ので手で上げた | NF-L-01 / D-115 / D-121。`libs.versions.toml` / `gradle-plugin/build.gradle.kts` |
| D-124 | メトリクス（要件 NF-O-04）をどう持つか | **値を返す API だけを持ち、`/metrics` のルートは生やさない。**外に晒すかどうかは認証も公開範囲もアプリの都合なので、フレームワークが握ると<b>閉じたいときに閉じられない</b>（ヘルスチェックを持たないと決めた D-106 / NF-O-03 と同じ）。アプリは `get("/metrics", c -> c.response().json(Metrics.snapshot()))` と1行書く。**分布は固定バケット**（1/5/10/50/100/500/1000/5000ms ＋ あふれ）で、<b>ひとつひとつの値は覚えない</b>ので何件入れてもメモリが増えない。そのかわりパーセンタイルは<b>入ったバケットの上限</b>までしか言えない（あふれたぶんだけ実測の最大を返す）。**名前は 1000 種類まで**で、超えたら捨てて1回だけ警告する：<b>利用者の入力を名前にすると、いくらでも増える</b>（`/aaa` `/aab` … と叩かれるだけでヒープが埋まる）ので、レイテンシの名前には生のパスではなく<b>マッチしたルートの型</b>（`GET /posts/{id}`）を使い、どのルートにも当たらなかったものは `(unmatched)` 1つにまとめる。**内部呼び出し（F-W-27）は数えない**（アクセスログを1行にしているのと同じ理由。1リクエストが2回になる）。**ゲージは I/O をしてはいけない**：`snapshot()` が DB を触ると<b>DB が詰まっているときに限ってメトリクスも取れなくなる</b>——いちばん見たいときに見えない。だから MQ の滞留数は `MqQueue#pendingCount()` として<b>公開はするが自動では登録しない</b>（アプリが `Metrics.gauge("mq.notice.pending", () -> q.pendingCount())` と書く）。**Agroal は `metricsEnabled` を立てないと常に 0 を返す**ので `true` にした（既定は false で、黙って 0 が並ぶ） | NF-O-04 / NF-O-03 / D-106 / 原則5。`Metrics` / `WebContext#recordMetrics` / `DBUtil` / `MqQueue` |
| D-125 | ベンチマーク（要件 NF-P-06）で何を見て落とすか | **落とすのは「1回あたりに割り当てた byte 数」だけ。時間では落とさない。**要件には「ベースライン比 -10% で警告」と書いてあったが、<b>GitHub の共用ランナーは走るたびに 20〜30% ぶれる</b>ので、そのまま実装すると<b>直していないのに赤くなる日</b>ができる。赤が信用されなくなると本物の退行も見過ごされるので、要件のほうを変えた。かわりに `ThreadMXBean#getThreadAllocatedBytes` の実測を使う：**同じコードなら機械が変わっても同じ値**で、3回まわして 1 byte も動かないことを確かめてある。時間は表に出すが落とす材料にしない。`@Tag("bench")` を付け、`./gradlew bench` で走らせる（通常の `test` からは外す。<b>数十万回まわすので、同じ JVM でほかのテストと混ぜると測った順で答えが変わる</b>）。**捕まえられないのは「割り当てを増やさない退行」**（舐める回数が増えただけ、ロックの取り合いが増えただけ）——時間には出るが、その時間を信じないと決めている | NF-P-04 / NF-P-05 / NF-P-06 / D-6 / D-108。`Bench` / `RouterBench` / `RequestBench` / `build.gradle.kts` / `.github/workflows/ci.yml` |
| D-6 | マッチ結果をキャッシュするか | **入れない。測った結果、キャッシュする理由が無かった。**`RouterBench` によると、**ルートが 1000 本あっても 10 本と 1 byte も変わらない**（深さ4で 544 byte / 約 170ns）。木を降りているので<b>ルート数に依らない</b>。1リクエスト全体が 7,969 byte・約 6μs（DB もテンプレートも無しで）なので、マッチはその 7% で、実際のアプリでは誤差に沈む。いっぽうキャッシュを持つと<b>鍵は利用者が送ってくる生のパス</b>になり、`/aaa` `/aab` … と叩かれるだけで<b>無限に増える表</b>を抱えることになる（メトリクスの名前で同じ問題を扱った。D-124）。得るものが誤差で、失うものがヒープなら、持たない | NF-P-04 / NF-P-06 / D-125。`RouterBench` |
| D-13 | ビルド規約の置き場所 | **`build-logic` を「含まれるビルド」にして、そこに Java の規約プラグインを置く。**root の `build.gradle.kts` が 709 行になり、そのうち 293 行が `subprojects {}` だった。`jimble.java-conventions`（ツールチェーン / -Werror / doclint）／`jimble.test-conventions`（test / dbTest / pgTest / bench と junit）／`jimble.publish-conventions`（POM / 置き場 / 署名）／`jimble.plugin-publish-conventions`（gradle-plugin 用）の4つに分け、**どのモジュールが何を使っているかは、そのモジュールの `plugins {}` を見れば分かる**ようにした（原則1）。**当初案の buildSrc は採らなかった**：`gradle-plugin` は別ビルドなので buildSrc からは見えず、POM の必須項目 25 行を2か所に持ち続けることになる。`build-logic` なら向こうの `settings.gradle.kts` からも取り込めるので、**「直すときは両方直すこと」という注意書きを消せた**。**Kotlin（`kotlin-dsl`）ではなく Java で書いた**：`kotlin-dsl` プラグインの実体（`org.gradle.kotlin:gradle-kotlin-dsl-plugins`）は<b>Gradle の配布物に入っておらず、Plugin Portal からしか取れない</b>（Maven Central には `org.gradle.kotlin` というグループ自体が無い）。D-22 が「Plugin Portal への依存を持ち込まない」と決めていて、しかもその理由としてここを名指ししているので、`java-gradle-plugin`（Gradle 同梱）で書いた——`gradle-plugin` と同じ形なので読み方も同じである。**Maven Central の一連のタスク（320 行）は root に残した**：年に数回しか動かさず、HTTP を直に叩いていて読む機会がまとまっているうえ、**書き直せば次のリリースまで未検証のまま**になる。root は 709 → 369 行、`gradle-plugin` は 199 → 117 行。**版の既定値は `gradle/libs.versions.toml` の `jimble` に1つだけ置いた**（本体・gradle-plugin・バンドルの3か所が同じ値を見る）。**規約プラグインのパッケージは `io.jimble.conventions`。`io.jimble.build` にしてはいけない**：`.gitignore` の `build/` は<b>どの階層でも「build という名前のディレクトリ」に当たる</b>ので、`io/jimble/build/` ごと無視される。<b>手元では動くのに commit されていない</b>ので CI だけが落ちる（`compileJava` が `NO-SOURCE` になり、実装クラスの入っていない jar ができて `Could not find implementation class`）。**実際にこれで1回赤くした。**`git status` は `?? build-logic/` としか出さないので、ディレクトリを1つ足したときは `git status --untracked-files=all` で中身まで見ること。移行の確かめ方：`build/central` に出るファイル 330 個の一覧と **POM 13 個が移行前と1 byte も変わらない**こと、使い捨ての GPG 鍵で署名まで通して `.asc` が 53 個（jar 30 / module 10 / pom 13）出ること、`./gradlew -p gradle-plugin build`（CI の plugin ジョブ）が単独で通ること | 原則1 / D-22 / NF-L-04。`build-logic/` / `build.gradle.kts` / `settings.gradle.kts` |
| D-126 | 分散トレーシング（要件 NF-O-05）をどう入れるか | **口だけを `jimble-core` に置き、OpenTelemetry の実体は別モジュール（`jimble-otel`）にする。**トレースを使わないアプリの実行時クラスパスを1 byte も増やさないためである（依存の縮小は目標1。D-52 / D-121）。**登録するまで何もしない**：`Tracing.start(...)` は静的な変数を1つ読んで `Span.NOOP` を返すだけで、<b>1回あたり 0 byte</b>（`TracingTest` が測っている）。登録はアプリが起動時に1行書く（`JimbleOtel.install("my-app", "http://localhost:4318")`）——起動時に走査しない（NF-P-03）、黙って外へ繋がない（原則5）。**スパンを刻む場所は4つ**：HTTP リクエスト（`server`。名前はマッチしたルートの型。生のパスにすると<b>トレースを見る道具の側で種類が無限に増える</b>。D-124 と同じ話）／SQL（`client`）／MQ（`producer`・`consumer`）／バッチ（`internal`）。**SQL は「終わってから」記録する**：実行しているところは `DB` の中に 10 か所あり、全部を `try` で囲むと<b>いちばん熱い経路に手を入れる</b>ことになる。時間はすでに測ってあるので、`Context.recordSqlExecution(nanos, sql)` からその分だけさかのぼった区間を1つ残す（口に「すでに経過した時間」を渡せるようにしてある）。**MQ の `traceparent` は専用の列に持つ**（`data` に混ぜると<b>アプリが入れた覚えの無い鍵が増える</b>）ので、キューのテーブルに版2の `ALTER` が1つ増える。**ログには `trace_id` / `span_id` を別項目で足す**：実行 ID（NF-O-01）の形を変えると<b>それを見て集計しているものが黙って壊れる</b>。`Log` から直に読んでいるのは、`FieldProvider` で登録する形だと Web・バッチ・MQ・CLI の4つで登録が要り、<b>1つ忘れるとそこのログだけ突き合わせられない</b>ためである。**依存は 4.2MB → 0.88MB に削った**：OpenTelemetry の既定の送信器（okhttp）は okhttp 851KB ＋ okio 374KB ＋ <b>kotlin-stdlib 1.7MB</b> を連れてくるので、`opentelemetry-exporter-sender-jdk`（20KB）に差し替え（D-24 と同じ判断）、トレースしか出さないので `sdk-metrics` と `sdk-logs` も外した。**足すだけでは駄目で、okhttp を exclude する必要がある**（送信器は `ServiceLoader` で選ばれるので、両方あると jar の並び順で決まる。D-122 と同じ罠）。<b>削りすぎていないことはコンパイルでは分からない</b>ので、OTLP を受ける口を立てて実際に送り、届いた中身を見るテストを置いてある | NF-O-05 / NF-O-01 / NF-P-03 / NF-L-01 / 原則5 / D-24 / D-122 / D-124。`jimble-core/trace/` / `jimble-otel/` |
| D-127 | MCP の残り（stdio / `subscriptions/listen` / ページング）をどう入れるか | **HTTP の層と JSON-RPC の中身を切り離し（`McpHandler` / `McpDispatch`）、stdio は同じ `McpDispatch` を通す。**分けないと<b>トランスポートを1つ足すたびに振る舞いが枝分かれし、片方だけ直し忘れる</b>。**stdio でもルート表は組む**ので `RouteTool`（F-MCP-15）と `before` の認証がそのまま効く。ポートは開かない。<b>いちばん壊れやすいのは標準出力である</b>：仕様は「サーバーは stdout に MCP のメッセージ以外を書いてはならない」と定めているのに、**logback の既定の出力先は stdout** なので、ログが1行混ざるだけでクライアントは「壊れた JSON が来た」として切る。そこで**本物の stdout は `McpStdio` だけが持ち、`System.out` は stderr に差し替える**——「書かないように気をつける」ではなく<b>書けなくする</b>。さらに**出力は自分で UTF-8 のバイトにしてから書く**：`PrintStream.print` に任せると端末の文字コードが使われ、`LANG` が決まっていないコンテナでは<b>日本語が全部 `?` になって出る。しかも例外が出ないので動いているように見える</b>（実プロセスを `LANG=C` で起こして見つけた）。**購読は読み取りの輪を止めない**：stdio は入出力が1本ずつしか無いので、待つと<b>取り消しの通知を読めなくなり二度と閉じられない</b>。HTTP 側は逆に、通知を待ち行列に入れて**ストリームを持っているスレッドだけが書く**（1本の口に書き手を2つ作らない）。行列には閉じた合図も入れるので、**取り消されたらその場で接続を手放せる**（合図が無いと空行を送る 15 秒まで居座る）。**閉じ方は2つある**：サーバー側から終わるときは元の要求への応答を返してから閉じる（返さないとクライアントは「落ちた」と思って繋ぎ直す）が、`notifications/cancelled` で取り消されたときは<b>応答を返してはならない</b>（仕様 MUST NOT）ので `cancel` と `complete` を分けた。**流せる通知は resources の2つだけ**にした：ツールとプロンプトは起動時に明示登録する（原則2）ので変わりようが無く、<b>「対応している」と答えて一生届かないほうが悪い</b>ため `acknowledged` から落とす。**ページ分けの既定は 100 件**なので、それ以下しか登録していないアプリの応答は<b>1 byte も変わらない</b>。読めないカーソルは `-32602` で断る——黙って先頭に倒すと<b>クライアントは同じページを永遠に読み続け、しかもエラーが1つも出ない</b>。あわせて **`server/discover`（仕様 MUST）が未実装だった**のを入れた。これは<b>版のヘッダを要求してはいけない</b>：版を知るための呼び出しに版が要ると、初めて繋ぐクライアントは何もできない（stdio ではこれが新旧の見分け方そのものである） | F-MCP-12 / F-MCP-13 / F-MCP-14 / F-MCP-16 / 原則2。`McpStdioTest` / `McpIntegrationTest` / `McpPagingTest` |
| D-10 | 到達不能ルートの検出範囲 | **変数どうしの衝突まで見る。判定は書き起こさず、本物のマッチャに聞く。****範囲**：要件の文面は「ワイルドカードに隠れるもの」だったが、<b>調べたらそれは起こらない</b>——優先順位が固定 &gt; 変数 &gt; ワイルドカードなので、ワイルドカードは何も隠さない（`/files/*` を先に書いても `/files/readme` は当たる）。実際に起きるのは<b>変数どうし</b>で、`/users/{id}` と `/users/{userId}` を両方 GET で登録すると<b>後者は一生呼ばれない</b>のに、いままで警告も例外も出なかった（名前を取り違えただけで起きる）。**やり方**：隠れる条件を並べるのをやめ、<b>パターンの変数のところに「どの固定セグメントとも重ならない語」を置いた試しのパスを作り、本物の `find` に流して、返ってきたのが自分かどうかだけを見る</b>。条件を書き起こすと探索の順番を<b>2か所に持つ</b>ことになり、片方を直したときにもう片方が黙って古くなる——そして<b>「警告が出ないから大丈夫」が嘘になる</b>。この形なら探索を変えても検出が勝手に追いつく。**ワイルドカードは長さを変えて何度か試す**：`/a/{x}` と `/a/{x}/*` と `/a/*` が並ぶと `/a/*` はどの長さでも他方に取られるが、<b>これは1本では隠せていない</b>（残り1つなら `{x}`、2つ以上なら `{x}/*`）ので、2つを見比べる形の判定では見つからない。**扱い**：既定は<b>起動時の警告だけ</b>（動いているアプリを、版を上げただけで起動しなくしない）。`server.strict_routes = true` で例外——<b>警告は起動ログの何十行にも紛れるので、出しただけでは誰も見ない</b>。CI ではこれを立てる。**見ないもの**：`/users/me` が `/users/{id}` より先に当たるのは正しい動きで、`{id}` は `me` 以外のすべてで呼ばれる。ここが見るのは<b>ただの1本も来ないもの</b>だけである | F-R-13 / F-R-20。`UnreachableRoutesTest` |
| D-11 | 既定のエラーレスポンス形式 | **JSON を名指しされたら決まった形の JSON、そうでなければ短いテキスト。中身は決め打ち。****いままで**：フレームワークは既定のエラー本文を持たず、`error` を書いていないアプリでは<b>ステータスだけで本文が空</b>だった。ただし `Accept: application/json` のときだけ `{}` が返っていて、これは<b>「中身の無い成功」と見分けが付かないぶん、無いより悪い</b>。**形**：`{"error":{"status":404,"message":"Not Found"}}`。RFC 9457（`application/problem+json`）は<b>採らなかった</b>——jimble には既に JSON-RPC（MCP）・`{"validation":{...}}`（422）・`{"error":"..."}`（batch-manager）があり、<b>5つ目の形を増やす</b>ことになる。**`message` は RFC 9110 の短い語（`Not Found`）で、英語のまま**：ステータス行やクライアントのライブラリに出てくる語と揃えるためで、ここだけ和訳すると<b>検索しても何も出てこない語</b>になる。**中身に原因を書かない**（NF-S-06）：例外のメッセージも SQL もスタックトレースも載せない。<b>本番かどうかの判断を1か所忘れただけで漏れる</b>形を作らないためで、原因はログに残っている。結果として<b>本番と非本番で出し分ける必要がなくなった</b>（NF-S-06 の「本番で自動的に抑止する」は、既定の本文については<b>いつでも抑止</b>という形で満たす。アプリが `error` に自分で `cause.getMessage()` を書く場合は別で、そこは未対応のまま）。**`*/*` はテキスト**：「何でもいい」であって「JSON がいい」ではない。**`error` フックが勝つ**：組み立てただけで送っていなくても、既定は踏まない（`json(...)` で用意して送信は枠組みに任せる書き方が、ドキュメントの勧める形である）。**405 は「外れたときだけ高い」を作りかけた**：最初の実装は、探索が失敗してから<b>木をもう一度舐めて</b>当たりうるメソッドを集めていた。パスそのものが無いリクエスト（いちばん多い）でも余分に歩いて 1 つ集合を作るので、<b>404 のほうが 200 より高い</b>形になった——攻撃者がでたらめな URL を叩くと効く。`RouterBench` の「どのルートにも当たらなくても費用は変わらない」がこれを捕まえた（552 → 592 byte）ので、<b>探索のついでに集める</b>形に直した（512 byte。もとより安い）。**`gradle build` と `pgTest` だけでは通ってしまう**——`bench` は別のタスクなので、これを流していなかったのが見落としの原因である。**あわせて `acceptJson()` を直した**：`,` で割った断片を丸ごと比べていたので、<b>`application/json;q=0.9` も、並びの2つ目以降で前に空白があるものも false</b> だった。パラメータを落としてから比べ、`q=0`（要らない）は false にする。<b>`*/*` は true にしない</b>——ここを true にすると<b>ビューを返すルートが JSON を返す</b>ようになる（`curl` の既定も、ブラウザが画像を取りにくるときも `*/*` である） | F-C-17 / F-R-25 / NF-S-06。`DefaultErrorResponseTest` / `ErrorHookWinsTest` / `AcceptJsonTest` |
| D-128 | 鍵のローテーション手順（残件 N-2） | **Cookie に載る鍵だけを対象にし、「新しい鍵で書き、古い鍵でも読み、読めたらその場で書き直す」形にする。****対象を分けた理由**：鍵は6種類あるが、<b>性質が2つに割れる</b>。`cookie.secret` / `session.secret` で作った値は<b>Cookie にしか残らない</b>ので、ブラウザが持ってきたときに書き直せるし、書き直せなくても Cookie の寿命が尽きれば機械的に終わる。いっぽう `cipher.key` / `hash.password.pepper` で作った値は<b>DB のパスワードカラムに永続</b>し、書き直せる瞬間が<b>ログイン成功時しかない</b>（平文がそのときしか無い）。<b>休眠ユーザーが1人でもいれば古い鍵を捨てられる日が来ない</b>ので、仕組みで面倒を見るふりをせず<b>「できない」と書いた</b>（手順はアプリ側の再ハッシュとしてドキュメントに載せた）。**設定の形**：`secret` はそのままにして `previous_secrets`（配列）を足す。既存の設定ファイルを1字も直さずに済み、<b>「どちらで書くのか」に迷いが無い</b>（`secret` で書き、`previous_secrets` は読むときだけ）。**書き直す**：読めただけでは終わらない。`Signer.unsignAny` / `Aead.decryptAny` が<b>どちらの鍵で通ったかを返す</b>ので、古ければその場で今の鍵で書き直す。<b>枠組みが出す `sid` と `csrf_token` は自分で書き直す</b>——ここを一律に書き直す作りにすると、<b>ブラウザは有効期限を送ってこない</b>ので既定（1年）で書くことになり、<b>30分で切れるはずのセッション Cookie が1年残る</b>。アプリが書いた Cookie は `isStale(name)` で見えるようにして、書き直しはアプリに任せた。**終わりが分かるようにする**：古い鍵で読めた回数を数える（`cookie.stale_secret` / `session.stale_secret`）。<b>これが無いと手順書に「しばらく待つ」としか書けない</b>。起動ログにも本数を出す（消し忘れに気づける）。**ついでに直した3件**：(1) <b>`cookie.secret` を設定すると Cookie セッションが丸ごと効かなくなっていた</b>——セッションの Cookie を<b>署名せずに</b>書いていたのに、受け取り側は全 Cookie の署名を検証して落ちたものを捨てるため。例外もログも出ず「保存したのに消えている」だけが残る形だった。(2) `touch()` が<b>中身を見ずに有効期限だけ延ばして</b>いたので、読めなくなった Cookie が永久に延命され、古い鍵の暗号文も入れ替わらなかった。(3) ドキュメントが <b>`unsignCookie()` を「検証して読む」と説明していた</b>——実際は未検証の生値を返し、無いときは null ではなく空文字。<b>署名検証をすり抜けるコードを教えていた</b> | NF-S-09 / F-S-08 / D-4 / N-2。`SecretRotationTest` / `SecretsRotationTest` / `MissingSecretWarningTest` |
| D-129 | 負荷試験をどう置くか | **公開しないモジュール（`jimble-load`）に、依存を1つも足さずに置く。CI では回さない。****CI で回さない**のは D-125 と同じ理由である——共用ランナーの時間は 20〜30% ぶれるので、<b>直していないのに赤くなる日</b>ができ、赤が信用されなくなる。手元で回して数字を見るためのものである。**負荷ツール（wrk / hey）を前提にしない**：入っていない台では回せず、<b>「動く人と動かない人」ができる</b>。Java で書いて `installDist` すれば、Mac でも Linux でも同じものが走る（依存は増えない。目標1）。**サーバーと負荷を別プロセスにする**：同じプロセスだと負荷をかける側が相手の CPU を奪い、<b>台のコアが少ないほど「相手が遅いのか自分が邪魔しているのか」が分からなくなる</b>。**素の helidon を並べる**：中身を揃えた（どちらも `GET /` に同じ文字列を返す）ので、<b>差がそのまま jimble の上乗せ分</b>になる。絶対値だけ見ても速いのか遅いのか判断できない。**閉ループであることを隠さない**：接続が返ってから次を投げるので、<b>rps は信じてよいが p99 は実際より良く出る</b>（1本が遅れると次の送信も遅れ、待たされたはずの要求が数から抜ける＝coordinated omission）。開ループは入れていないので、<b>入れていないと書いた</b>。**2xx 以外を成功に混ぜない**：混ぜると<b>アプリが落ちているときにいちばん良い数字が出る</b>（500 を返すのは速い）。失敗は行に ★ で出し、終了コードでも言う。**起動を決め打ちで待たない**：`READY` の1行を待つ——遅い台では温まる前に測り始めてしまう。**JAVA_HOME は書き換えない**：Java 25 の場所は別に持つ。書き換えると、古い JDK で動いている Gradle が起動しなくなる（実際に踏んだ） | NF-P-08 / NF-P-06 / D-125 / D-52。`jimble-load/load.sh` |
| D-130 | アクセスログを切れるようにするか／DB が読み込めていないときの落ち方 | **`server.access_log`（既定 `true`）で切れるようにし、DB は「取り出したその場で」落とす。****切れるようにした理由**：Mac で測ったら、素の helidon に対して 6〜18% 遅かった（<b>あとで分かるが、この数字は2つのものが混ざっていた</b>）。内訳を見ると<b>1リクエストの割り当ての約4割がアクセスログ</b>（約 8,100 byte のうち約 3,400 byte。測るたびに数十 byte 動く）で、<b>素の helidon は1行も書かない</b>。つまりあの差は「jimble の上乗せ分」と「helidon が持っていない機能の代金」が混ざったものだった。**切った版を並べて初めて分けられる**ので、`jimble-load` に `jimble-nolog` を足した（helidon と nolog の差が上乗せ分、nolog と jimble の差がログ1行の代金）。**既定は `true` のまま**：切ると<b>後から「あのとき何が起きたか」を調べる手段が無くなる</b>——500 が出ていたことも、誰がどのパスを叩いたかも残らない。速さのために既定で監視を落とす取引はしない。**切る `if` はアクセスログ1行だけを囲う**：メトリクス（NF-O-04）・トレース（NF-O-05）・セッションの保存し忘れの警告は外に出したままにした。`doClose()` の中で並んでいるので<b>まとめて囲ってしまうのがいちばんありそうな壊れ方</b>であり、そうなると<b>速くするつもりで監視を消す</b>ことになる（テストで固定した）。**ボットのログも同じ栓で止まる**：別のロガーへ出ているので<b>片方だけ残る</b>形になりやすく、「切ったのにログが増え続ける」は気づくまでが長い。**ベンチは既定のまま測る**：切った状態を基準にすると、<b>既定のまま動いているアプリの費用が誰にも見えなくなる</b>。**DB の落ち方**：`--blog` を回したら `NullPointerException: Cannot read field "conf" because "dbSource" is null` で落ちた。`DBUtil.load` は<b>繋がらなくても例外を投げず、原因をログに出して `false` を返す</b>設計で、サンプルアプリが<b>その戻り値を捨てていた</b>。結果、サーバーは起動してしまい、<b>DB のことを何も言わない例外</b>で最初のリクエストが落ちる（本当の原因は何十行も上にある）。**`load` を例外にする形は採らなかった**：戻り値は公開 API で、<b>`false` を投げるように変えると既存の呼び出しが黙って壊れる</b>（設定に `db` が無ければ `true` を返す、という約束も持っている）。かわりに<b>取り出したその場</b>で落とす：`DBUtil.getMainDB()` / `getDB(name)` が<b>どの DB の話か・何を直せばいいか</b>を書いた `IllegalStateException` を投げ、`new DB(null)` も受けない。**テストは前から正しかった**：`assertTrue(DBUtil.load(...))` と書いてある。<b>間違っていたのはドキュメントとサンプル</b>のほうで、そちらを直した（`db.md` / `pitfalls.md` / `Bootstrap`）。**分けて測った結果**（Darwin arm64・10 コア。温め3秒／測る10秒）：1本で helidon 22,507 / nolog 22,184 / jimble 21,315 rps、8本で 59,075 / 57,030 / 52,537 rps。つまり<b>jimble の素の上乗せは 1.4〜3.5%</b>で、<b>アクセスログ1行が 3.9〜7.9%</b>——<b>差の大半はログのほうだった</b>。**64本・256本は読まない**：3つとも同じ数字に寄り（64本では jimble のほうが速いことになる）、<b>コア数を超えたところで測っているのは相手ではなく負荷生成側の限界</b>である。実際、64本は前回 −17.7%・今回 +0.8% と走るたびに符号が変わる。**そこで、接続数がコア数を超えた行に `▲接続>コア<n>` を付けるようにした。**<b>接続数はコア数から決めない</b>——台ごとに行が変わると、<b>Mac と CI の結果を並べられなくなる</b>。固定のまま印だけ付けて、<b>読み手が読み飛ばせる</b>形にした。境界は「コア数と同数までは印なし」で、<b>コア数が分からない（0 以下）ときは付けない</b>——<b>全部の行に付いた印は、付いていないのと同じ</b>だからである | NF-O-02 / NF-P-05 / NF-P-08 / NF-O-04 / F-X-05 / D-129 / D-125。`ServerConf#accessLog` / `WebContext#doClose` / `AccessLogTest` / `DBUtil#require` / `DbSourceMissingTest` |
| D-131 | 1リクエストの内訳をどう出すか（helidon との差をこれ以上追うか） | **時間の差（1.4〜3.5%）は追わない。かわりに、名前の付いていない割り当てを無くす。****追わない理由**：素の helidon との差は 1本で 1.4%・8本で 3.5%（D-130）で、<b>測定器自身のブレより小さい</b>（同じ道具の 64 接続の列は、走るたびに符号が変わる）。しかも上乗せの中身は<b>helidon がやっていないこと</b>（Context・実行 ID・メトリクス・トレース・セッションの後始末）で、削るのは最適化ではなく<b>機能を減らすこと</b>である（原則1 / 原則5 とぶつかる）。目標1は速さではなく依存の縮小（D-52）。**かわりにやったこと**：`RequestBench` の内訳は<b>部品を1つずつ別に測って並べていた</b>ので、<b>合計と一致しない分が黙って残っていた</b>——1リクエスト 約 8,100 byte のうち<b>約 3,700 byte（45%）に名前が無かった</b>。上限（10,240）まで 2,000 byte ほどしか無いのに<b>いちばん大きい塊が「何か分からないもの」</b>で、これでは明日 800 byte 増えても<b>どこで増えたか誰も言えない</b>——byte で見張ると決めた D-125 が、そこだけ効いていなかった。**段ごとに積んで隣との差を名前にする**形に変えた（差を全部足すと最後の段になるので、<b>名前の無い残りは構造上できない</b>）。**分かったこと**：足場（テスト用の入出力）504／Context の生成 1,168／ルーティング 455／ハンドラを走らせる枠 304／**後始末 5,724**（うち<b>アクセスログ 3,387</b>、メトリクス・トレース・セッション 2,337）。**アクセスログは今まで 1,000 byte 安く見えていた**：`Log.access` を単体で呼んで測っていたので、<b>実際に載る項目（実行 ID・SQL 集計・ホスト情報）が揃っていなかった</b>。その場で切って引く形に変え、<b>約 2,300 byte → 約 3,400 byte（3割 → 4割）</b>と直した（D-130 に書いた数字も直してある）。**次に大きいのは「メトリクス・トレース・セッションの後始末」2,337 byte**だが、<b>名前を付けただけで手は入れていない</b>——削る判断はここではしない。**ついでに直した `bench` の不安定さ**：`RouterBench` が `assertEquals` で byte をぴったり比べていて、<b>552 が一度だけ 556 になっただけで赤くなった</b>（続けて3回流すと 552 に戻る）。D-125 は<b>時間のブレ</b>を byte に替えて避けたのに、<b>比べ方がぴったりだったので同じ問題が戻っていた</b>。64 byte のすき間を入れた——ここが守っているのは「ルートを1本ずつ試す実装に戻る」ことで、そうなれば 1000 本では<b>桁が変わる</b>（1本 8 byte でも 8KB）ので見逃さない | NF-P-05 / NF-P-06 / NF-O-02 / D-125 / D-130 / D-52 / 原則1 / 原則5。`RequestBench#breakdown` / `Bench#derived` / `RouterBench` |
| D-132 | サンプルアプリを機能ごとに分ける（残件 N-3）／Cookie の値を符号化する | **題材を「社内の申請・承認」に統一し、機能ごとに1本ずつ、単体で完結するサンプルを並べる。****1本にまとめない理由**：`examples/blog` に足りないものを全部足すと、<b>D-51 で「実装がコードの大半を占めて、どのルートが何を通しているのか読めなくなる」</b>として捨てた RSS サイト案と同じ状態に戻る。<b>D-51 は生きたまま</b>で、変えたのは「サンプルは1本」という前提のほうである。**共通モジュールを持たない**：各アプリが自分のマイグレーション・生成コード・設定・データベースを持つ。<b>1本読めば真似できる</b>ことを優先した（2つのモジュールを行き来させると、それだけで真似されなくなる）。代償として `request` テーブルが複数のアプリに違う列で出てくるが、<b>各アプリのスキーマが最小になる</b>という利点のほうが大きい（`approval-forms` は `staff` も `department` も要らない）。**PostgreSQL 単独**：方言の両対応は `examples/blog` と `jimble-db` のテストが担保している。サンプルを2製品ぶん書くと<b>マイグレーションが倍になり片方だけ古くなる</b>。ただし <b>`dbTest` は `application.pgtest.conf` の有無を見ない</b>ので、各サンプルで `tasks.named("dbTest") { enabled = false }` と書かないと<b>MySQL しか無い CI の db ジョブで PostgreSQL に繋ぎに行って落ちる</b>。**費用は測った**：1本あたり `pgTest` 7 秒＋`codegenCheck` 5 秒で、`build` と `pgTest` の全体時間には<b>計測誤差に埋もれて見えない</b>。見込みで「2〜4 分増える」と書いていたが、<b>7本ぶんでも 1〜2 分</b>である。**1本目（`approval-auth`）で3つ見つかった**：(1) <b>Cookie の値を符号化していなかった</b>——`Cookie#toSetCookie()` が値をそのまま連結していたので、<b>日本語の Flash が例外も警告も無しに {@code ????} になって届いていた</b>。HTTP のヘッダは ASCII なので当たり前なのだが、<b>誰も落ちないので気づけない</b>。`examples/blog` の結合テストが<b>「`id="flash"` があるか」しか見ていなかった</b>ので、壊れたまま緑だった（そちらも中身を見るように締めた）。<b>percent-encode で直した</b>——`URLEncoder` は使わない（`+` を空白にするので、署名の Base64 と区別が付かなくなる）。`|` も `=` も `+` もそのまま通すので、<b>いま出ている署名つき Cookie の見た目は1文字も変わらない</b>。<b>救えないのは、値に `%` が入っている既存の Cookie を一度だけ読み違えること</b>（区別できない）。(2) <b>`session.save()` は1リクエストに1回だけ効く</b>ので、共通の `before` で保存すると<b>そのリクエスト本来の保存を黙って食う</b>（ログインが 302 を返すのに次で 401 になる）。(3) <b>`after` で Cookie を足しても遅い</b>——応答はもう送られている。**「効いていない設定」を作らないこと**も1本目で学んだ：公開ルートに「セッションを使わない」と宣言しても、<b>誰も `session()` を触らなければ外から見て何も変わらない</b>。変異テストで生き残ったので、<b>セッションに触る共通処理を足して、効いていることが見える形にした</b> | N-3 / F-S-05〜12 / F-R-16 / F-R-17 / F-W-13 / F-Y-10 / F-G-14 / D-51 / NF-T-06。`docs/design-n3.md` / `examples/approval-auth` / `CookieValue` / `CookieValueTest` |
| D-133 | サンプル2本目（`approval-forms`）と、そこで出た2件 | **入力の確認を保存の処理から追い出し、同じルールを下書きと提出で使い回す形にした。**`SaveValidation`（`ValidationExecutor`）→ `SaveUseCase` の2段で、<b>保存の側には `if (金額が空なら…)` が1行も無い</b>。**ルートには `::new` で登録する**：エラーは Executor のインスタンスに溜まるので、<b>使い回すと前のリクエストのエラーが混ざる</b>。**「提出かどうか」は `insertRequestChecker` に載せた**：名前は移送元由来だが、意味は<b>「送られてこなくても検証する」</b>である。`if` を保存の処理に2本書くと、<b>下書きと提出は別の口なので、そのうち片方だけ直る</b>。**生成した型付きアクセサを初めて使った**（F-G-02 / F-D-22）：`AbstractRequestData` は再帰ジェネリクスなので `class RequestData extends AbstractRequestData<RequestData> {}` の1行で閉じる。<b>置き場所は `db/` の外</b>——あそこは codegen のたびに作り直されるので、手で書いたものは消える。**ここで見つかった2件。**(1) <b>空で送られた入力欄が、空文字ではなく「空のリスト」になっていた</b>。`name=` は Helidon から空の List で届き、`NestedParameterParser#single` が<b>要素1つのときしか中身を取り出していなかった</b>。`EmptyValidator` が見るのは「null か、空の文字列か」なので<b>空の List はどちらでもない</b>——つまり<b>必須の欄を空のまま送れば検証を抜けられた</b>。空の List は空文字にした。<b>「送っていない」とは区別が残る</b>（そもそもキーが届かない）ので、`examples/blog` の PATCH（送った項目だけ変える）は壊れない。(2) <b>`putForm` に入れた入力値がどこにも出てこなかった</b>。`ValidationExecutor#onCancel` が検証に落ちたときの入力を `putForm` に入れているのに、`formData` は<b>誰も読まないフィールド</b>で、応答にもテンプレートにも載らない（`getForm()` の呼び出しがリポジトリ全体で 0 件だった）。<b>ドキュメント（`errors.md`）は最初から「入力値も一緒に返る」と書いてある</b>ので、実装が仕様に届いていなかっただけである。`putForm` / `setForm` が応答本体にも載せるようにした。**費用**：2本目も `pgTest` 全体で 60 → 70 秒（1本 10 秒前後という 1本目の実測どおり） | N-3 / F-V-01〜04 / F-V-07 / F-V-08 / F-W-03 / F-W-06 / F-W-09 / F-G-02 / F-D-22 / D-132。`examples/approval-forms` / `NestedParameterParser#single` / `Response#putForm` |
| D-134 | サンプル3本目（`approval-list`）——一覧・集計・キャッシュ | **一覧は JOIN 1本で引き、ページングと絞り込みを載せる。集計は別の口にしてページングを重ねない。****`selectListWithRowCount` を使う**：`selectList` だと総件数が 0 のままで、<b>画面に「1/0 ページ」と出る</b>。**並び順に第2のキー（id）を足す**：`created_at` だけだと同着があり、<b>同じ行が2ページに出る</b>。変異テストで実際に落ちた。**空の `in` を作らない**（F-D-07）：`in(空)` は組み立て時に例外になる。枠組みが「空なら条件ごと外す」をやると<b>全件が返る</b>ので、<b>呼ぶ側が「空なら積まない」と書く</b>——リポジトリ初のその実例である。**ページングと集計を重ねない**：総件数を数える SQL が<b>集計を包んだ形</b>になり、<b>この組み合わせには jimble にテストが無い</b>。サンプルでは踏まないことにした（未検証を承知で使わない）。**集計した値を CASE の条件にはできない**：`Dsl.sum(...)` が返すのは「選択できるもの」で、`ge(...)` のような比較を持っていない。かわりに<b>条件付き集計</b>（`SUM(CASE WHEN status='approved' THEN amount ELSE 0 END)`）を書き、大／中／小の区分けは Java で付けた。CASE は<b>`SelectQuery` に包まないと</b>ただの値になる。**キャッシュに手を入れる場所は無い**（F-D-28）：読むときに `selectListCached` と書くだけで、<b>消すのは更新した側が勝手にやる</b>。タグを自分で付ける形にしないのは、<b>付け忘れたときに古いものが出続ける</b>からである。**ここで初めて使ったもの**：`Paging` / `groupBy` / `having` の系統 / `ScopeCache`（F-W-15）/ `Column#urlPathPlaceholder`（F-R-18）——<b>どれも `examples/` に実例が1つも無かった</b>。**テストで踏んだ落とし穴**：連番の id を決め打ちしていた。マイグレーションを流し直すと `bigserial` は進むので、<b>手元は 4,5,6 で CI は 1,2,3</b> になる。名前から引く形に直した。**残した穴**：`selectListCached` を `selectList` に変えても結合テストは全部通る（外から見て変わるのは速さだけ）。<b>見張っているのは「更新したのに古いものが出続ける」ほう</b>で、そちらが利用者に見える壊れ方である。テストにそう書いた | N-3 / F-V-05 / F-V-06 / F-D-02 / F-D-05 / F-D-07 / F-D-09 / F-D-12 / F-D-28 / F-D-31 / F-W-15 / F-R-18 / F-Y-08 / D-132。`examples/approval-list` |
| D-135 | 集計を条件にできるようにする（`HAVING SUM(...) >= ?`） | **`ISelect` に比較（9つ）を既定メソッドとして生やし、式を左辺に置ける where を作った。****見つかり方**：`approval-list` で「合計が 50 万以上なら大」を SQL で書こうとして、`Dsl.sum(...)` に `ge(...)` が無いことに気づいた。<b>調べたら、もっと悪かった</b>——道が無いのではなく、`WhereQuery(IDsl)` が式を<b>右辺</b>に置いていて、そのあとの比較が右辺を上書きするので、<b>{@code HAVING ( >= ?)} という左辺の消えた SQL が、例外も警告も無しに組み上がっていた</b>。つまり<b>`groupBy` も `having` もあるのに、集計に対する HAVING が一度も書けたことがなかった</b>。**直し方**：`WhereQueryInner` に<b>左辺の式</b>を持たせ、`WhereQuery(IDsl)` と `WhereQuery.ofExpression(ISelect)` がそこへ置くようにした。**書き口は `ISelect` の既定メソッド**：`Dsl.sum(x).ge(y)` と、<b>列のとき（`Column.ge`）と同じ形</b>で書ける。<b>戻り値の型は1つも変えていない</b>ので、公開済みの jar との互換性が壊れない（83 個の `Dsl` の関数を全部書き換える案は、そこが理由で採らなかった）。`ISelect` の実装は4つだけで、うち `Column` と `AbstractDsl` は同じメソッドを自分で持っているため衝突しない。**比較だけに絞った**：`like` や `contains` も足せるが、<b>集計に対しては「書けるけれど意味の無い組み合わせ」が増えるだけ</b>である。**字面を変えない**：左辺の式の前に空白を足したら、`Dsl.regexp(...)` の SQL が`(\`name\` REGEXP ?)` から `( \`name\` REGEXP ?)` に変わって既存のテストが落ちた。<b>SQL 結果キャッシュの鍵は字面で作る</b>ので、変えると<b>版を上げた瞬間に全部が入れ替わる</b>。空白は足さないことにした。**あわせて**：`approval-list` の大／中／小を Java から SQL へ戻し、`sql.md`（日英）に「まとめた値で絞る」の節を足した | F-D-05 / F-D-09 / F-D-31 / D-134。`ISelect` / `WhereQuery` / `AggregateConditionTest` / `examples/approval-list` |
| D-136 | サンプル4本目（`approval-ops`）——動いている中を見る | **DB を使わないサンプル。ヘルスチェック・メトリクス・トレース・流量制限・Bot・内部呼び出しを1本に集める。****`@Tag("db")` を付けない**ので、<b>DB の無い CI ジョブ（`build`）で結合テストが走る</b>。`io.jimble.db` プラグインも JDBC ドライバも書かない（`examples/hello` と同じ形）。**ヘルスチェックは `Shutdown.isStopping()` を見る**（NF-O-03 / D-91）。止め始めたら<b>ここだけ先に 503 になり、ふつうのリクエストはまだ通る</b>——そのあいだにロードバランサが新しい人を送らなくなり、処理中のものを返しきってから止まれる。`HEAD` も要る（監視の道具は本文を要らないことが多い）。**トレースは依存に入れただけでは出ない**：`otel.endpoint` が設定にあるときだけ `JimbleOtel.install` を呼ぶ。<b>書いていないのに collector を探しに行くと、起動のたびに接続エラーがログに出る</b>。**gauge の登録はメソッドに切り出した**：`Metrics.reset()` が<b>登録した gauge ごと消す</b>ので、消したあとに呼び直せる形が要る（テストで踏んだ）。**ここで初めて使ったもの**：`BotBlocker` / `Metrics.snapshot()` を返すルート / `JimbleOtel.install` / `Shutdown.isStopping()` を見るヘルスチェック / `dispatcher().call(...)` / `rateLimit` / `server.strict_routes = true` の設定ファイル / `UserAgentInfo`——<b>どれもアプリのコードとしては初である</b>。**「動的な応答に `Cache-Control: no-store` が付く」ことを初めて固定した**（F-X-07）。付かないこと（静的配信）のテストはあったが、<b>付くことのテストはどこにも無かった</b>。**テストで踏んだ落とし穴**：JSON はスラッシュを `\\/` と書くので、`contains("http.GET /limited")` は<b>出ているのに見つからない</b>。もう1つ、`/metrics` を叩いて得た数に<b>その `/metrics` 自身は入らない</b>（メトリクスを記録するのはリクエストを閉じるときで、中身を返すのはその前）| N-3 / NF-O-02〜05 / F-R-13 / F-R-15 / F-W-14 / F-W-16 / F-W-27 / F-H-05 / F-X-07 / D-91 / D-106 / D-124 / D-126 / D-130。`examples/approval-ops` |
| D-137 | チャンクモデルのバッチ（`AbstractChunkBatch`） | **`AbstractBatch` を継承した `AbstractChunkBatch<T>` を足した。**`execute()` は `final` にして差し替えさせず、手を入れるのは `reader()` / `process()` / `write()` の3つだけにした。**きっかけ**：`approval-jobs` の前に、Spring Batch のチャンクに当たる形が欲しいという話。**1かたまり＝1トランザクション**：`chunkSize()` 件たまったら `DBTransaction` で囲って書き、確定する。100万件を1つのトランザクションで抱えない。<b>フレームワークがバッチのトランザクション境界を決めたのはここが初めて</b>である（既存の `AbstractBatch` は `execute()` を素で呼ぶだけで、既定は自動コミットだった）。**読む DB と書く DB は別のインスタンスにした**：`DBTransaction.close()` は `db.close()` を呼びコネクションをプールへ返すので、<b>同じ `DB` で読んでいると最初のチャンクを確定した時点で読みかけが死ぬ</b>。`DBUtil.getMainDB()` が毎回新しい `DB` を返すことを使い、`reader()` と `write()` に別々に渡す形にした（<b>引数をそのまま使えば間違えられない</b>）。**読み方はキー順ページングを勧める**：`KeyPagingReader` を足した。カーソル（`selectListWithFetcher`）でも書けるが、<b>カーソルは開いている間ずっとコネクションを1本押さえる</b>——数時間のバッチではそれが効く。ページングはページとページの間はコネクションを持たない。**SQL は丸ごと呼ぶ側に書かせた**：`KeyPagingReader` は文字列を組み立てず、`(lastKey, limit) -> db.selectList(...)` を受け取るだけにした。組み立てると<b>何番目の `?` に何が入るのかが読めなくなる</b>（原則1）。**キーが進まなければ例外にする**：`WHERE` の向きと `ORDER BY` が食い違っていたりキーが一意でないと、満杯の同じページが返り続ける。<b>黙って永久に回るより落ちたほうがよい</b>（原則5）。**落ちたら全体を失敗**：そのかたまりだけロールバックし、例外をそのまま上げる。黙って次へ進む「スキップ」は入れていない（要るようになってから入れる）。どこまで確定したかは `execute_info` に `chunk_written` / `chunk_committed` / `chunk_failed_at` として残る。**進み具合を定期的に書く**：`batch.progress_seconds`（既定 5 秒）ごとに `batch_history.execute_info` を上書きする。DDL は変えていない。<b>移送元のバッチは開始と終了しか書かないので、長いバッチはずっと「実行中・件数不明」だった。</b>**あわせて直したバグ**：`isCancelOrder()` が<b>親（スケジューラ）の中断を `cancelOrder` に残していなかった</b>。バッチは `isCancelOrder()` を見てループを抜けるのに、抜けたあとの判定はフィールドを見るので、<b>スケジューラを止めて抜けたバッチが「完了」として履歴に残っていた</b>。1行（`cancelOrder = true`）で直る。<b>挙動が変わる</b>ので CHANGELOG に書いた。**検査例外は包む**：`execute()` は検査例外を投げられないので `RuntimeException` で包む。原因のクラスとメッセージはスタックトレースに残る。**書き込みの失敗は最後の1文しか拾えない**：`db.insert()` は失敗しても例外を投げず `db.isError()` が立つだけで、しかも<b>1文ごとに上書きされる</b>。枠の側でも `write()` のあとに一度見ているが、<b>複数文を流すなら `write()` の側で1文ずつ見るのが本筋</b>である（javadoc に書いた）。**ここで固定していないこと**：かたまりが途中で空になる道は<b>実際には通らない</b>（内側のループはかたまりが満たない限り読み続けるので、空で出るのは読み切ったときだけ）。`continue` と `break` のどちらでも同じ結果になり、ミューテーションが生き残ったので、<b>`break` に直して「ここへ来るのは読み切ったときだけ」と書いた</b>。**ミューテーション**：13 個仕込んで 13 個とも落ちた（`@Timeout` は `SEPARATE_THREAD` でないと無限ループを止められない、というのもここで踏んだ） | F-B-06 / F-B-08 / F-B-12 / D-16 / D-125。`AbstractChunkBatch` / `KeyPagingReader` / `AbstractBatch` / `BatchConf` / `ChunkBatchIntegrationTest` / `KeyPagingReaderTest` / `BatchIntegrationTest` |
| D-138 | サンプルの接続先を差し替える環境変数の名前 | **サンプルは `JIMBLE_SAMPLE_DB_*` / `JIMBLE_SAMPLE_PG_*` を見る。**jimble 本体のテスト用（`JIMBLE_TEST_DB_*` / `JIMBLE_TEST_PG_*`）とは分ける。**見つかり方**：D-137 の作業中、`jimble-db` の `MigrationIntegrationTest` が4件落ちた。`down に失敗しました: 002_seed_staff.sql / column "login_id" does not exist` と出るが、<b>この SQL は `jimble-db` のものではない</b>——`examples/approval-auth` のシードである。**何が起きていたか**：サンプルの `application.conf` が `url = ${?JIMBLE_TEST_PG_URL}` を見ていた。開発者が `dbTest` / `pgTest` の接続先を替えるつもりでこの環境変数を立てると、<b>サンプル5本のマイグレーションが自分のデータベースではなく `jimble_test` へ流れ込む</b>。`jimble_test.migration` に他のアプリの履歴が混ざり、down を有効にしたテストが<b>「消えた SQL」として他のアプリのシードを down しようとして落ちる</b>。**落ち方がいちばん悪い**：出るのは `jimble-db` のテストの失敗で、メッセージも SQL の話なので、<b>フレームワークの不具合に見える</b>。実際の原因は環境変数の名前である。**CI は影響を受けていなかった**が、それは<b>CI が URL の上書きを諦めていたから</b>である（`db` ジョブに「URL は上書きしない。blog も `JIMBLE_TEST_DB_URL` を見ているので」というコメントが残っていた）。<b>回避策が仕込まれていたこと自体が印だった</b>。**直し方**：サンプル側（`examples/blog` と `approval-*` 3本、`application.conf` と `application.pgtest.conf`）の変数名を `JIMBLE_SAMPLE_*` に変え、なぜ分けるのかを設定ファイルのコメントに書いた。CI の `db` ジョブは両方を渡す形にし、<b>URL を上書きできるようになった</b>ことをコメントに残した。`testing.md`（日英）に「本体用とサンプル用は別の名前である」を足した。**確かめ方**：`JIMBLE_TEST_PG_URL=...jimble_test` を立てたまま `pgTest` を全部流し、<b>`jimble_test.migration` が汚れないこと</b>と<b>`approval_auth_example` に自分の履歴が入ること</b>を両方見た（以前はこの条件で必ず落ちた） | D-16 / D-137。`examples/blog` / `examples/approval-auth` / `examples/approval-forms` / `examples/approval-list` / `.github/workflows/ci.yml` / `testing.md` |
| D-139 | サンプル5本目（`approval-jobs`）——時間のかかる仕事 | **バッチ・スケジューラ・MQ を1本に集めたサンプル。**主題は<b>「失敗する道を通すこと」</b>である。`examples/blog` の MQ は必ず成功するので、<b>リトライもデッドレターも一度も動いたことがなかった</b>。**わざと失敗させる仕掛けは「積んだデータで決める」**：`data.fail_until` を入れると、その回数までは `error` を返す。設定にすると<b>アプリ全体が同じ振る舞いになり、成功と失敗を並べて見せられない</b>。行に入っていれば、あとから `select * from mq_notice` で「なぜ落ちたか」も読める。**デッドレターは Web から一覧・再投入できるようにした**：`GET /dead` と `POST /dead/retry`。<b>フレームワークは dead を掃除も再投入もしない</b>（消えるのは `completed` だけ）。それでよい——<b>自動でやり直してよいなら、そもそも dead にする必要がない</b>。戻すときは <b>`retry_count` を 0 に戻す</b>のが要点で、戻さないと拾われた瞬間にまた上限を超えて dead に戻る。**チャンクバッチ（D-137）の唯一のサンプル**：`ArchiveChunkBatch` が `request` から `request_archive` へ移す。<b>別テーブルにしたのは「読む DB と書く DB は別」という形が目に見えるようにするため</b>である。**冪等は2枚重ね**（要件 F-M-05）：`NoticeExecutor` は「先に見る」と「`notice (request_id, kind)` の一意キー」の両方を持つ。1だけでは<b>見ると作るのあいだに隙間がある</b>、2だけでは<b>失敗が例外にならないので気づけない</b>。**書いていて踏んだこと**：<b>(a) `isScheduler()` は「スケジューラに載せるか」ではない</b>——「このバッチがスケジューラそのものか」である。cron のつもりで true にすると `BatchRegistry.sync()` が飛ばすので<b>マスタに行ができず、スケジューラから見えないまま</b>になる。手で流せば動いて履歴も残るので気づきにくい。javadoc を書き足した（<b>シグネチャは変えていない</b>）。<b>(b) `sync()` は `cron` を上書きしない</b>——上書きするのは `default_cron` だけで、`cron` は<b>管理画面から変えた値を守る</b>ためにそのまま残る。テストが `cron` を見ていて、<b>コードを直しても古い行を見続けていた</b>（ミューテーションが生き残って気づいた）。`batch_master` を毎回作り直し、見るのを `default_cron` に変えた。<b>(c) `Data.getString()` で日時を取ると varchar として渡り</b>、PostgreSQL が「timestamp の列に character varying」と言って落ちる。`get()` で生のまま渡す。**ミューテーション**：10 個仕込んで 8 個が落ちた。生き残った2個は<b>冪等の2枚を片方ずつ外したもの</b>で、<b>ワーカーが1件ずつ処理する限りもう片方が拾う</b>ため。両方外すと落ちる——テストの「ここで固定していないこと」に書いた | N-3 / F-B-01〜12 / F-M-01〜08 / D-104 / D-137 / D-138。`examples/approval-jobs` / `AbstractBatch`（javadoc のみ） |
| D-140 | サンプル6本目（`approval-pages`）——画面を返す | **DB を使わないサンプル。**jte のレイアウト継承と部品化・静的配信・SPA・MPA・リバースプロキシを1本に集める。`@Tag("db")` を付けないので<b>DB の無い CI ジョブ（`build`）で結合テストが走る</b>。**主題はレイアウトの共有**：<b>リポジトリ全体で `@template.` の実例が1件も無かった</b>（jte は9枚あるが、どれも単独の完結 HTML）。`layout/page.jte` が `<html>` から `</html>` までを持ち、ページは中身だけを書く。部品（`tag/nav.jte` / `tag/card.jte`）も両方置いた——<b>`Content` を受け取る部品</b>と、<b>既定値つきの `@param`</b> がここで初めて出る。**エラー画面もレイアウトを共有させた**（共有しないと 404 だけ見た目の違うサイトになる）。**転送先は jimble をもう1つ別ポートで立てた**：`UpstreamApp` が `X-Forwarded-*` をそのまま返すので、<b>プロキシが何を足して何を落としたかが転送先の目から見える</b>（`Host` は落ちて張り替わる）。**書いていて踏んだこと**：<b>(a) jte のコメントは `<%-- --%>` である。</b>`@* *@` は<b>コメントにならずそのまま出力され、しかも中に書いた `@template.` が実行される</b>——レイアウトの中にそのレイアウト自身の呼び出し例を「コメントのつもりで」書いたら <b>`StackOverflowError`</b> になった。エラーは `Malformed input or input contains unmappable characters` という文字コードの話に見える形で出るので、原因にたどり着きにくい。<b>(b) コメントは入れ子にできない</b>——説明文の中に閉じ記号を書くとそこで終わる（これも踏んだ）。<b>(c) HTML コメントも既定で消える</b>ので、SPA の title 差し替えの目印は<b>静的ファイル側</b>に置くこと。<b>(d) `Response.putData()` は `Data` を返す</b>ので `view()` まで1本で繋げない。<b>(e) HTTP ヘッダの値に非 ASCII は入れられない</b>（`If-None-Match` に日本語を入れてテストが落ちた）。<b>(f) JSON はスラッシュを `\/` と書く</b>（D-136 で踏んだのと同じ）。**あわせてドキュメントを直した**：`view.md`（日英）に<b>レイアウト・部品・コメントの節を新設</b>（レイアウト継承の記述が1文字も無かった）。`assets.md`（日英）は<b>真似できない例</b>（`test-assets` は `src/test/resources` にある）と、<b>「Spa/Mpa も同じ」と読める記述</b>（本数が違う。Asset は2本、Spa/Mpa は4本）、<b>`/assets` 自身には応えないこと</b>を直した。**ミューテーション**：9個仕込んで9個とも落ちた | N-3 / F-W-08 / F-W-10 / F-W-17 / F-W-18 / F-W-20 / F-R-14 / F-X-07。`examples/approval-pages` / `view.md` / `assets.md`（日英） |
| D-141 | サンプル7本目（`approval-data`）——DB の込み入った話 | **データベースを2つ使うサンプル。**参照用の接続・ぶら下げたサブ DB・キャッシュ・分散ロック・`DBValue`・コードマイグレーション・一括登録を1本に集める。**主題は「トランザクションで守れる範囲」**である。承認は<b>申請の状態を変える</b>と<b>通知を1件作る</b>の2つで、これは同じ DB なので囲える。監査ログは<b>別のデータベース</b>なので囲えない——だから<b>確定したあとに書く</b>。逆にすると「承認は戻ったのに承認したというログだけ残る」になる。<b>jimble は分散トランザクションをやらない</b>ので、2つの DB にまたがる以上どちらかは諦める、という形をそのまま見せた。**サブ DB は2つの意味があるので両方書いた**：トップレベルの `db { }` をもう1本（`DBUtil.getDB("名前")`）と、親の `subs { }`（`db.newSubDB("名前")`）。<b>後者にはマイグレーションも codegen も当たらない</b>ので、表と型が要る監査ログは前者にしてある。`subs` は<b>同じデータベースを指すように書いた</b>——「別のサーバーだから共有できない」のではなく<b>そもそも共有しない</b>ことは、同じ DB を指していたほうが分かる。**あわせて直したフレームワークのバグ（`insertBatch`）**：`insertBatch(List<InsertBuilder>)` は<b>先頭のビルダーの SQL だけを使い、残りのビルダーからはパラメータだけを取っていた</b>。`value()` を積む順が違うビルダーが混ざると、<b>例外も警告も無しに値が横にずれて入る</b>。SQL を1本ずつ突き合わせ、違ったら `DB_998` を立てて `null` を返すようにした。<b>挙動が変わる</b>ので CHANGELOG に書いた。**書いていて踏んだこと**：<b>(a) `subs` が別のデータベースを指すと起動しない</b>——`subs` は<b>親のデータソースを作っている途中で</b>繋ぎにいくので、あとに書いたトップレベルの `create_database_sql` は間に合わない（`failed create subs datasource`）。<b>(b) キャッシュに `Data.toString()` を入れていた</b>——`{"rates":"Data(1件) {rates=ArrayList(3件)}"}` になる。<b>壊れるのはキャッシュに当たったときだけ</b>なので、1回目だけ見ていると通る。`Dson.encodes` / `Dson.decodes` に直し、テストも<b>2回目の中身まで見る</b>形にした。<b>(c) `DBValue` の内部キャッシュのキーに DB 名が入っていない</b>——2つの DB で同じキーを使うと混ざる。サンプルではキーの頭を分けて避けた（<b>フレームワーク側は直していない</b>）。**ドキュメントの誤りを4件直した**：`db.md`（日英）の<b>「こちらは接続を共有します／トランザクションも共有されます」は両方とも逆</b>（`subs` は別のプールである）、<b>引数なしの `DBUtil.getDB()` は存在しない</b>（`getMainDB()`）、`subs` にマイグレーションが当たらないことが<b>どこにも書かれていなかった</b>。`sql.md`（日英）の<b>「戻り値は件数のリスト」は `insertBatch` には当てはまらない</b>（採番値である）ので分けて書き、SQL を揃える必要も足した。**ミューテーション**：8個仕込んで、最初に落ちたのは6個。生き残った2個は<b>どちらも「失敗する道をテストが通っていない」</b>だった——<b>(1) `beginTransaction()` を外しても落ちない</b>：`approve` の失敗はどれも<b>1行も書く前に</b>抜ける（404 と 409）ので、ロールバックする道が無かった。`notice (request_id, kind)` に一意キーを足し、<b>先に通知を入れておいてから承認する</b>テストを書いた——状態を変えたあとで通知が弾かれるので、囲っていなければ<b>状態だけ変わって通知が無い申請</b>が残る。<b>(2) `CodeMigration.add()` を外しても落ちない</b>：`migration_code` に<b>前に走ったときの行が残っている</b>ので、記録を読むだけのテストは通ってしまう（CI の新しい DB では落ちるが、手元では気づけない）。<b>行を消してから `CodeMigration.execute()` を呼び</b>、記録と丸めの両方を見る形にした。直したあとは8個とも落ちた | N-3 / F-D-08 / F-D-13 / F-D-14 / F-D-15 / F-D-16 / F-D-19 / F-U-07〜F-U-11 / F-Y-15 / F-G-09 / F-G-18。`examples/approval-data` / `jimble-db`（`DB.insertBatch`） / `db.md` / `sql.md`（日英） |
| D-142 | 公開は<b>コミット済みの木から</b>やる | **`centralBundle` を叩く前に、コミットして push してあること。タグが指す先も目で見ること。****きっかけ**：0.3.0 で<b>公開が 09:28、版上げ一式のコミットが 09:30</b> と、2分だけ順序が逆になった。**このときは実害が無かった**——コミットされたのは版上げの8ファイルだけで、公開の20分以上前からディスクに乗っていたので、<b>公開した木とそのコミットの木は同じ</b>である。<b>ただしそれは、あとから時刻を突き合わせて確かめられただけ</b>で、公開の直前に1行直していたら <b>Central にしか存在しないコードが生まれていた</b>。Central は消せないので、その1行は二度と取り戻せない。**あわせて踏んだ**：`v0.3.0` が<b>公開した木ではなく 38 コミット前（9月9日）を指していた</b>。「push を忘れた」ではなく<b>指し先が違う</b>ので、`git tag` の一覧を見ても気づけない。公開のたびに `git rev-parse HEAD` と `git log -1 --format=%H v<版>` を突き合わせる。**やり直しで直さないこと**：この2つはどちらも<b>次の版を出しても直らない</b>（0.3.0 は Central に残り続ける）。<b>版を1つ使うだけで、食い違いはそのまま残る</b>ので、直すのはタグのほうである | NF-L-04 / D-61 / D-62。`docs/publishing.md` |
| D-143 | 閉じ忘れた DB コネクションを実行の終わりに拾う | **握ったまま抜けた `DB` を `Context` に登録し、実行の終わりにエラーログを出して閉じる。****きっかけ**：「`Context.run()` の終わりに、使った `DB` を全部閉じたい」という話。**調べたら前提が違った**：`closeAfterQuery()` が<b>1文ごとにコネクションをプールへ返している</b>ので、`DB` を閉じ忘れてもふつうは何も残らない（`DbSessionStore#load()` は1メソッドで3本作る。<b>そういう使い方が前提の作り</b>である）。**握ったまま抜ける道は2つだけ**：<b>(1) トランザクション中</b>（`closeAfterQuery()` が返さない）、<b>(2) カーソル</b>（`selectListWithFetcher` は読み終わるまで `ResultSet` を開けておくので `close()` まで返さない）。**だから「作った `DB` を全部登録する」形にしなかった**：後始末の列に<b>1文ごとに返している大多数まで積まれる</b>。Web の1リクエストなら数十で済むが、<b>長いバッチのループなら際限なく増える</b>——コネクションの漏れを直してメモリの漏れを作ることになる。**登録するのは握った瞬間だけ**：`beginTransaction()` と `selectListWithFetcher()` の2か所で登録し、`closeAfterQuery()` と `close()` で外す。登録数は<b>実際に握っている本数</b>で頭打ちになる。**`DBTransaction` にあった同じ仕組みは消した**：あちらに置くと<b>`db.beginTransaction()` を直に呼んだときに拾えない</b>。コネクションを握る当人（`DB`）へ下ろして1か所にした。**見つかった実害**：<b>`examples/approval-list` と `examples/blog` の CSV 出力が、呼ばれるたびにプールから1本ずつ消していた。</b>`fetcher` は畳んでいるが `DB` を畳んでいない。`sql.md`（日英）の「大きい結果」の例も<b>そのまま真似すると漏れる形</b>だった（しかも `selectListWithFetcher` は `ResultSetFetcher` を取るので、載っていた `row -> {}` の形は<b>コンパイルも通らない</b>）。**プールの数字で見る**：テストは Hikari の `getActiveConnections()` を見る。<b>漏れているときも SQL は成功する</b>ので、件数でも例外でも見分けられない。あわせて `Context#pendingCloseTaskCount()` を足し（`pendingExecutorCount()` に倣った）、<b>畳んだものが列に残らない</b>ことも見ている——これが「登録するのは握ったものだけ」を選んだ意味そのものである。**ミューテーション**：7個仕込んで6個が落ちた。生き残った1個は<b>`closeAfterQuery()` のトランザクション枝での登録</b>で、`beginTransaction()` が先に登録しているため<b>消しても誰も落ちない</b>。<b>コードのほうを消した</b>（D-137 と同じ判断）。**やらなかったこと**：HikariCP の `leakDetectionThreshold` は入れていない | F-D-16 / D-16 / D-137。`DB` / `DBTransaction` / `Context` / `ConnectionLeakTest` / `examples/approval-list` / `examples/blog` / `sql.md` / `transaction.md`（日英） |
| D-144 | セキュリティ問題の報告窓口と対応方針（NF-S-08） | **受け口は GitHub の非公開報告（private vulnerability reporting）1本にする。メールの窓口は作らない。****なぜ1本か**：受け口を増やすと<b>片方だけ見落とす</b>。GitHub なら、やりとりも修正も Advisory の公表も同じスレッドで完結する。<b>公開リポジトリにメールアドレスを載せる副作用</b>（迷惑メール、消しても履歴に残る）も避けられる。**約束するのは受領だけ**：「数日以内に受け取ったことを返す」まで。<b>修正の期限は約束しない</b>——個人で開発しているので、「30日以内に修正」と書いて守れないほうが、書いていないより信用を落とす。代わりに<b>影響の大きいものから手を付ける</b>と書いた。**直すのは最新の版だけ**：バックポートはしない。0.x のあいだは上げてもらうのがいちばん早く、Central にまだ3版しか無い。**対象外をはっきり書いた**——ここが<b>いちばん書く値打ちのあるところ</b>である。<b>(1) `examples/` のサンプル</b>：接続情報も鍵も既定値（`jimble` / `jimble`）のままなので、<b>報告されても直しようがない</b>。「本番に置くものではない」と明記した。<b>(2) 依存そのものの脆弱性</b>：Dependency graph と Dependabot が追う（NF-S-07）。ただし<b>「jimble の使い方のせいで手が届く」なら対象</b>と線を引いた。<b>(3) 設定でそう決めたもの</b>：`server.trust_proxy = true` にすれば `X-Forwarded-For` を信じる（既定は `false`）。<b>書いてあるとおりの動きは脆弱性ではない</b>。**ただし例外を1つ立てた**：<b>ドキュメントと実装が食い違っているなら脆弱性として扱う</b>。「そう書いてあるから安全だと思っていた」がいちばん危ない読み違いで、実際このプロジェクトは<b>そういう食い違いを何度も見つけている</b>（`unsignCookie` が署名を検証しない・`subs` が接続を共有すると書いてあった、など）。**置き場は3か所**：`SECURITY.md`（ルート。GitHub が Security タブと Issue 作成画面から案内する）、jimble.io の日英1枚ずつ、README の1行。**日英併記**：`SECURITY.md` を読むのは<b>外部の報告者</b>なので、日本語だけにしない。**手でやることが1つ残る**：<b>リポジトリの設定で private vulnerability reporting を有効にすること</b>。有効になっていないと、`SECURITY.md` が案内する URL が 404 になり、<b>窓口を書いたのに受けられない</b>という最悪の形になる | NF-S-08 / NF-S-06 / NF-S-07 / D-43 / D-128。`SECURITY.md` / `README.md` / `security.md`（日英） |
| D-145 | 版と互換性の約束（NF-C-03 / NF-C-04） | **0.x のあいだはマイナーで壊れうると明記し、非推奨期間の約束は 1.0 から発効させる。****NF-C-03 だけでは決められなかった**：「公開 API は SemVer に従う」と書いても、<b>何が公開 API かが決まっていなかった</b>（`public` なクラスが 520 個・154 パッケージ、`internal` パッケージは0個）。隣の NF-C-04 が未着手のままでは、<b>内部を1つ動かすたびに破壊的変更</b>になり、守れない約束になる。**線は引いたが、クラスは動かさなかった**：`internal` へ実際に移すと<b>いま使っている人の import が全部壊れる</b>。0.3.0 を出した直後にやることではない。代わりに <b>`docs/api-packages.txt` に全パッケージを公開 101 / 内部 49 / 空 4 で分類</b>し、判断の材料（examples が import しているか・docs が名指ししているか・入口クラスの内側でしかないか）も書いた。**線引きが古くならないようにテストで塞いだ**：`ApiSurfaceTest` が<b>ソースの木と表を突き合わせ</b>、片方にしか無いものがあれば落ちる。<b>どちらにするかを決めるのは人の仕事</b>で、テストが塞ぐのは「決め忘れ」だけである（`FrameworkTables` の `ALL` 足し忘れをリフレクションで塞いだのと同じ形。D-68）。あわせて<b>サンプルが内部パッケージを import していないか</b>も見る——サンプルは「真似してよい書き方」の見本なので、そこが内部を触っていたら<b>線かサンプルのどちらかが間違っている</b>。**いちばん値打ちがあったのは挙動の扱い**：jimble でこれまで実際に起きた壊れ方は<b>ほとんどがシグネチャを変えない変更</b>だった（404→405、`insertBatch` が `null`、バッチの履歴が `canceled`）。SemVer の言う「公開 API」はシグネチャの話なので、<b>そこだけ守っても利用者が踏む変更は1件も捕まらない</b>。そこで<b>「壊れていたのを直した」と「仕様を変えた」に分けた</b>——前者に非推奨期間は<b>置けない</b>（`insertBatch` の「1マイナーのあいだは壊れたデータを書きます」とは言えない）。どちらであっても<b>CHANGELOG の「変わったこと（挙動）」に必ず出す</b>、「直した」に埋めない、と書いた。**残る穴を正直に書いた**：<b>1.0 をいつ出すかは決めていない</b>ので、「1.0 から厳密に」は<b>永久に来ない可能性がある</b>。シグネチャの変更を機械で見る（前の版の jar と突き合わせる）道具も入れていない——1.0 に上げるときに決める。**あわせて踏んだ**：<b>`docs/api-packages.txt` が `.gitignore` に飲まれていた。</b>`/docs/*` でまるごと外し、出すものだけ `!` で戻す形なので、<b>戻し忘れると手元では通って CI で落ちる</b>（表が読めないだけなので、原因も分かりにくい）。`!/docs/api-packages.txt` を足し、テストの側にも「`.gitignore` に飲まれていませんか」と出るようにした。**ミューテーション**：3個仕込んで3個とも落ちた（新パッケージを表に足さない／サンプルが内部を import する／表そのものが無い） | NF-C-03 / NF-C-04 / NF-C-01 / D-68 / D-141 / D-143。`docs/api-packages.txt` / `ApiSurfaceTest` / `versioning.md`（日英） |
| D-146 | ドキュメントだけで作れるかの2回目（0.4.0） | **もう一度やって、詰まったのは5件・うち重大1件。**1回目（0.2.0。8件・うち重大1件）から<b>始め方の問題は消えた</b>——「まっさらから書く」の全文を貼るだけでプラグイン3つがマーカー経由で解決し、Java も jte も<b>1回目のビルドで通り</b>、テストも1回目で通った。詰まったのは<b>設定まわりだけ</b>で、API の説明で詰まったものは0件だった。**重大な1件は、1回目とまったく同じ形**：<b>サイトが公開されている版より先に進んでいた</b>。`transaction.md` と `sql.md` に D-143 の記述（閉じ忘れた DB を拾う）が載っていたが、<b>0.3.0 には入っていない</b>。0.3.0 の利用者は「拾われる」と思って書き、拾われないまま漏れる（約束されている ERROR も出ない）。**原因はリリースの外での手動公開**で、D-117 は「反映はタグのときだけ」と決めていたのに、`security.md` / `versioning.md` を早く出したくて手で流したときに一緒に出ていった。<b>自動化しても、手で回す道が残っていれば同じことが起きる</b>。**対応は 0.4.0 を出して揃える**ことにした（手動公開を禁じるのではなく、<b>出したら版も出す</b>）。**残る4件はすべて設定まわり**：<b>(T-2) PostgreSQL の設定例が `password = ${?DB_PASSWORD}` の1行だけ</b>——`${?ENV}` は環境変数が無いと<b>キーごと消える</b>ので、そのまま真似すると `The server requested SCRAM-based authentication...` で止まる（<b>設定の話に見えない</b>）。しかも同じページに「無ければ前の行の値が残ります」と書いてあり、<b>その前の行が無い</b>。<b>(T-3) `create_database_sql` の説明が無い</b>——サイト全体で1回しか出てこず、それも `subs` の落とし穴の中。手で `CREATE DATABASE` すればよいのだが、<b>そう書いてある場所も無い</b>。<b>(T-4) マイグレーションの例が MySQL の DDL だけ</b>——SQL ビルダーが方言を吸収するので<b>マイグレーションもそうだと思いやすい</b>が、こちらはそのまま流している。<b>(T-5) 時刻をどちらの時計で入れるかが書かれていない</b>——`new Date()`（アプリ側）しか例が無く、`Dsl.now()`（DB 側）はサイトに0件。1台なら同じだが、<b>複数台にすると台ごとの時計のずれで、あとから入れた行のほうが古くなる</b>。**5件ともドキュメント側で直した**（ソースは1行も変えていない）。**確かめられなかったこと**：`jimbleRun`（Gradle 9 が入れられない。1回目と同じ）、Central からの解決（この環境から出られないので<b>手元で組んだ 0.4.0 をローカルリポジトリに置いて代用</b>。Central から取れることは 0.3.0 の公開後に確認済み） | NF-D-01 / D-117 / D-143。`docs/docs-only-trial.md` / `db.md` / `codegen.md` / `sql.md`（日英） |
| D-147 | 認証モジュールの下ごしらえ（セッション ID の振り直し／ブロック単位の属性） | **Spring Security のような認証機能を足すにあたって、先に土台の2つを入れた。****調べたら部品は揃っていた**：セッション（保存先4種）・CSRF・Cookie の署名と暗号化・鍵のローテーション・`PasswordUtil`（BCrypt ＋ pepper）・`BasicAuth`・ルート属性。<b>足りないのは部品ではなく「組み立て」</b>で、`examples/approval-auth` の 80 行がそれである。**Spring Security の形は写さない**：`AuthenticationManager` → `ProviderManager` → `AuthenticationProvider` → `UserDetailsService` の層は<b>原則4に真っ向から反する</b>し、`@PreAuthorize` は原則2、`SecurityContextHolder` の `ThreadLocal` は `Context`（`ScopedValue`）と二重になる。**置き場は `jimble-web` の中**（`io.jimble.web.auth`）：`jimble-otel` を別モジュールにしたのは<b>0.88MB の依存を連れてくるから</b>で、今回は<b>新しい依存がゼロ</b>なので分けない。分ければ公開が15本になり、依存の表が1行増え、<b>利用者に「これは要るのか」という判断が毎回1つ増える</b>。OAuth / OIDC は JOSE を連れてくるので、そのときに `jimble-auth-oidc` を切り出す。**(1) セッション ID の振り直し（F-S-13）**：ログインの前後で ID が変わらないと<b>仕込まれた ID がそのまま権限を持つ</b>（セッション固定化）。中身は持ち越す——戻り先の URL や CSRF トークンが消えると<b>ログインした瞬間に元いた場所を見失う</b>。<b>保存はしない</b>（F-S-02 のまま）ので、`save()` を忘れると<b>古い側は消えていて新しい側は無い</b>＝ログインしていない状態に倒れる（<b>閉じるほう</b>なのでこれでよい）。**(2) ブロック単位の属性（F-R-26）**：`.attribute(...)` をルート1本ずつに書くのが大変、という要望。<b>`rateLimit()` が既にやっていたことを、任意のキーに一般化した</b>——`Scope` の `RateLimit` 1本の field を `Map<AttributeKey<?>, Object>` にし、`Route#seal()` の<b>流量制限の特別扱いを消した</b>。`rateLimit()` は `attribute(RateLimit.KEY, ...)` の別名になり、<b>仕組みが1つ減った</b>。<b>パスのノードには付けない</b>（D-69）——同じパスでも別のブロックなら付かない。付ける作りにすると<b>コードを読んでも付いているか分からなくなる</b>ので、`before` / `after` と同じ決まりに揃えた。**ミューテーション**：7個仕込んで、最初に落ちたのは6個。生き残った1個は<b>振り直しで「保存済みの印」を戻す行</b>で、<b>同じリクエストで先に `save()` してから振り直す道</b>をテストが通っていなかった。この道は実在する（例外も出ず、次のリクエストで 401 になるだけなので原因が遠い）ので、<b>コードではなくテストを足して落とした</b> | F-S-13 / F-R-26 / F-S-02 / F-R-10 / F-R-22 / D-69 / NF-L-01。`Session` / `Scope` / `Route` / `Router` / `ScopeAttributeTest` / `DbSessionIntegrationTest` / `routing.md` / `session-security.md`（日英） |
| D-148 | ログインと認可を組み立て済みにする（`Auth` / `Principal`） | **`before(Auth::guard)` 1行と、ルート属性3つ。**Spring Security の層（`AuthenticationManager` → `ProviderManager` → `AuthenticationProvider` → `UserDetailsService`）は写さない——<b>原則4に反する</b>し、注釈は原則2、`SecurityContextHolder` の `ThreadLocal` は `Context` と二重になる。**置き場は `jimble-web` の中**（`io.jimble.web.auth`）：<b>新しい依存がゼロ</b>なので分けない（`jimble-otel` を別にしたのは 0.88MB を連れてくるからで、その基準に照らして分けない）。**閉じ込めたのは3つの判断**：<b>(1) 既定は「要ログイン」</b>——`PUBLIC` の既定を `false` にした。逆にすると<b>書き忘れたルートが黙って開く</b>。<b>(2) 役割違いは 403</b>——401 だと<b>入り直せば見られると思って何度も試させる</b>。<b>(3) 「ログイン不要」と「セッション不要」は別</b>——一緒にすると<b>ログイン画面自身がセッションを持てず誰もログインできない</b>（サンプルで実際に踏んだ形）。**やらないと決めたこと**：401 のときの転送は<b>アプリの `error()` に任せる</b>（設定で切り替える口を作るより、アプリに1か所書いてあるほうが読める）。JWT は<b>出さない</b>（失効できない）。ロックアウト・remember-me・OAuth は入れていない。**`Principal` は3つだけ**（id / 表示名 / 役割）：利用者を丸ごとセッションに入れると<b>名前を直してもログインし直すまで古いまま</b>、<b>権限を剥奪してもセッションが切れるまで効かない</b>、Cookie セッションなら<b>その全部がブラウザへ出ていく</b>。<b>`null` は返さない</b>（`ANONYMOUS`）——判定を書き忘れたコードが 500 で落ちるのは 401 ではない。**`checkPassword` を足した**：`PasswordUtil.check` はハッシュが `null` だと<b>即座に false を返す</b>ので、<b>利用者がいないほうが目に見えて速い</b>（BCrypt は遅いのが仕事である）。応答時間で ID を数えられるので、いなくても<b>1回まわしてから</b> false を返す。**答え合わせは `examples/approval-auth`**：<b>`AuthApp` が 319 行から 219 行になり、結合テスト11本は1行も変えずに通った</b>（挙動は同じで、アプリ側から認可のコードが消えた）。セッション固定化のテストを1本足して12本。**ミューテーション**：6個仕込んで5個が落ちた。生き残った1個は<b>時間合わせ</b>で、<b>時間で落とすテストは共用ランナーでぶれる</b>ため意図的に見ていない（テストに理由を書いた）。**あわせて分かったこと**：サンプルに長年あった「未マッチのとき 401 を返すと存在を漏らす」というコメントは<b>間違いだった</b>——ディスパッチャは<b>未マッチなら `before` を回す前に 404 を投げる</b>ので、その道は通らない。判定自体は残したが、理由は<b>`route()` が `null` を返しうる</b>ことに書き換えた（外すと 401 ではなく NPE の 500 になる） | F-W-28 / F-S-13 / F-R-26 / F-Y-10 / D-69 / D-147 / NF-L-01。`Auth` / `Principal` / `Controller` / `examples/approval-auth` / `auth.md`（日英・新設） |
| D-149 | 総当たりを止める（ロックアウト） | **止めずに、遅くする。**「N 回で M 分ロック」は<b>そのまま嫌がらせの道具になる</b>——わざと間違えるだけで締め出せるので、全員ぶんやれば業務が止まる。代わりに<b>待ち時間を倍にしていく</b>（3回までは待たせない → 1 → 2 → 4 …… 上限 300 秒）。攻撃者から見た試行速度は実質ゼロになり、<b>正規の利用者は数秒待つだけ</b>で済む——被害の大きさが釣り合っている。**流量制限では足りない**：流量制限は <b>IP ごと</b>なので、<b>1つのアカウントに 1000 個の IP から1回ずつ</b>来ると発火しない。こちらは<b>アカウントごと</b>に数える。<b>両方掛ける</b>。**入口は `Auth.attemptLogin` 1本にした**：`Lockout` を直に使う形にすると<b>成功したときに消し忘れる</b>——正しく入れた人が翌日待たされる、という<b>誰も踏まないと気づかない</b>壊れ方をする。**数える単位は「入力されたログイン ID」**：利用者の DB 上の ID を渡すと<b>居ない ID だけ数えられない</b>。総当たりは居ない ID から始まるうえ、<b>待たされるかどうかでどの ID が在るかが分かる</b>。**行は SHA-256 で持つ**：入力をそのまま行にすると<b>攻撃者が好きな文字列で行を作れる</b>（＋ログイン ID が平文で溜まる）。<b>大小と前後の空白もそろえる</b>——そろえないと `alice` / `Alice` / `ALICE` …… と綴りを変えるだけで何回でも試せる。**数えるのは SQL の中で足す**：読んでから足すと<b>並列の失敗を数えそこねる</b>（総当たりは並列で来る）。初回も「UPDATE して0件なら INSERT」ではなく<b>先に `INSERT IGNORE` してから足す</b>——前者は<b>初回に同時に来た2本の片方が一意キーで落ちて数えられない</b>（10本同時のテストで実際に落ちた）。**掃除は失敗のついで**（1時間に1度）：<b>記録が増えるのは失敗したときだけ</b>なので、バッチの常駐スレッドにするより確実（DB セッションの移送元がそれで永遠に溜めていた。D-16 の頃の話と同じ形）。**DB が無ければ何もしない**（1度だけ警告）——例外にすると DB を使わないアプリがログインを組めなくなるが、<b>黙るのはいちばん質が悪い</b>。**ミューテーション**：13個仕込んで、13個とも落ちた。そのうち2個は<b>先に仕込んだテストでは落ちなかった</b>ので、テストを2本足して落とした——1つは<b>桁あふれ</b>（上限で先に打ち切らないと 67 回目で `1L << 63` が負になり、68 回目は Java のシフトが一周して 1 秒に戻る＝<b>数えるほど甘くなる</b>）、もう1つは<b>「間が空いたら忘れる」を読むときにも見ているか</b>（既定では経過の引き算だけで 0 になってしまうので、<b>上限を forget_hours より長くした設定</b>で確かめた）。**サンプルの結合テストに罠があった**：失敗の記録は 24 時間残るので、居ない ID で失敗するテストを<b>同じ日に4回流すと4回目から 429 になる</b>。`@BeforeAll` で消すようにした | F-W-29 / F-W-28 / F-S-09 / D-148 / 原則5。`Lockout` / `LockoutConf` / `Auth#attemptLogin` / `FrameworkTables` / `LockoutTest` / `LockoutIntegrationTest` / `examples/approval-auth` / `auth.md` / `config.md`（日英） |
| D-89 | 読めない設定値は落とす（黙って倒さない） | **`jimbleRun { restartMode }` に書けない値を書いたら例外にする。**前は<b>黙って `on_request` に倒していた</b>ので、書き間違えても「設定したのに効かない」だけが残った。実際、<b>ドキュメントに実装に無い値（`on_change`）が載っていて</b>、そのとおり書いても何も起きなかった（ドキュメント側も直した）。指定が無い（null・空）ときだけ既定に倒し、<b>読めない名前は書ける名前を並べて落とす</b>（F-X-05）。`jimble.env`（D-78）・バッチの `env=`（D-79）・`server.max_header_size`（D-86）と同じ形で、**「書いたのに効かない」を作らない** | F-X-02 / F-X-05。`JimbleRunPluginTest` |
| D-88 | 生成物に「手で直すな」の印を入れる | **生成した Java の先頭に4行のコメントを入れる**（要件 F-G-03 の後半が未実装だった）。生成先のディレクトリは `codegen` のたびに<b>まるごと作り直す</b>ので、そこに手で書いたものは黙って消える。<b>消えることを知らずに直すと、直したはずの変更が戻る</b>。ディレクトリを分けるだけでは、開いたファイルが生成物かどうか分からない。既にコミットしてある `examples/blog` の生成物5件にも同じ印を付けた（`codegenCheck` が一致を見るため）。`GeneratorIntegrationTest` に印の確認を足した | F-G-03。`GeneratorIntegrationTest` |
| D-87 | `XmlData` の数値 getter が常に 0 を返していた | **戻り値の型を直した。**`getShort` / `getInt` / `getLong` / `getFloat` / `getDouble` の<b>戻り値の型が全部 `byte`</b>だった。中では正しく変換していたのに、それを `Byte` にキャストするところで必ず落ち、`catch (Exception)` が **0 を返していた**。<b>「0 が入っている」と区別がつかない</b>ので、気づかないまま使われる形になっていた。例外を握って既定値を返す作りは、握る範囲が広いと<b>バグを隠す</b>（`Data` の getter も同じ作りなので、同種のものが残っている可能性がある）。`XmlDataTest` を足した。あわせて<b>ドキュメントのコード片の収集元に `jimble-util/src/test` を追加</b>（util のページから実コードを引けるようにするため） | F-Y-09 / NF-D-03。`XmlDataTest` |
| D-86 | 書いてあるのに効かない設定を無くす | **(1) `server.max_header_size` を helidon に渡した。**設定キーもドキュメントも前からあったのに、<b>どこからも読まれていなかった</b>（`maxHeaderSize()` の呼び出し元がゼロ）。書いても効かない設定は、書いた側から見ると「効いているのに破られた」と区別がつかない。`Http1Config.maxHeadersSize` に配線した。**(2) `server.host` を足した**（要件 F-H-06。未実装だった）。空なら全部のアドレスで待ち、`127.0.0.1` にするとそのマシンからしか繋がらない。<b>手元で動かす MCP サーバーが外に出てしまう</b>のを設定で閉じられるようにするため。ファイアウォールに頼る形は、設定を忘れたときに黙って公開される。**(3) 起動ログに待受とヘッダ上限を足した**（効いていることが目で見える） | F-H-01 / F-H-06 / NF-S-05。`ServerConfTest` |
| D-85 | `MemoryCache` だけ引数の並びが違っていた | **インターフェースに合わせた。**`ICache#set(key, value, contentType, group)` に対して `MemoryCache` の実装が `set(key, group, value, contentType)` になっていた。<b>4つとも `String` なので `@Override` は通り、コンパイルでは分からない</b>。`ICache` 越しに呼ぶと<b>値とコンテンツタイプとグループが入れ替わって入る</b>。実際にスケジューラのハートビート（`SchedulerControl`）がこの形で呼んでおり、`cache.type = memory` では中身が壊れていた。既存のテストは<b>`MemoryCache` 型の変数から</b>呼んでいたので、間違ったほうの並びを固定していて気づけなかった。**インターフェース越しに呼ぶテストを足した**（実装の型で呼ぶテストだけだと、差し替え可能なはずのものが差し替えられていないことに気づけない） | F-U-07。`MemoryCacheTest` |
| D-84 | 例外のログで<b>いちばん要る情報が落ちていた</b> | **(1) 呼んだ側が書いた説明を出す。**`Log.error(cause, "保存できませんでした: id=3")` はメッセージが<b>例外のもの</b>になり、書いた説明は `objects` に入る。同梱の `LogbackErrorEncoder` はそれを出していなかったので、画面には「書けません」だけが出て<b>何をしていて失敗したのかが消えていた</b>。**(2) 実行 ID を出す**（アクセスログと突き合わせるため）。**(3) メッセージが null でも落ちないようにした。**`throwable.getMessage().getBytes(...)` を素で呼んでいたので、`NullPointerException` のようにメッセージを持たない例外を出すと encode の中で落ち、`catch` が空を返して<b>ログの行がまるごと消えていた</b>（原因の例外側も同じ）。例外の<b>クラス名</b>も出すようにした。**(4) 雛形に `conf/logback.xml` を追加**。設定が無いと logback の既定になり、アクセスログもアプリのログも同じところに混ざる。`access.bot` は `access` の子なので `additivity="false"` が要る | F-U-04 / F-U-06。`LogbackErrorEncoderTest`。`docs/design-m11.md` 14 章 |
| D-83 | テスト間で共有する static を待たずに次へ行かない | **閉じたら、閉じ終わるまで待つ。**`WsIntegrationTest` が<b>「さようなら」を待っていたのに「おわり」を拾って</b>落ちた。`closeReason` と `closed` はテスト間で共有する static で、<b>前のテストが `sendClose` したあと onClose を待たずに終わっていた</b>。遅れて届いた onClose が、次のテストが張り直したラッチを開けて理由まで上書きする。`sendClose` は「送った」までしか保証しない。閉じる側に `closeAndWait` を1つ置き、**全部の close をそこに通した**。onClose を持たないハンドラもあるので<b>待つが落とさない</b>（来たことを確かめるのは close のテストの仕事）。**共有 static ＋ 非同期の後始末**は、順番しだいで落ちる形になる | 実装は `WsIntegrationTest`。要件 F-W-16 |
| D-82 | ドキュメントの読み口 | **注記の枠・前後のリンク・コピーを足した。**jooby.io と並べると、jimble のページは<b>「どれが注意書きなのか」が本文と同じ見た目</b>で、通読の順路も無かった。(1) 注記は GitHub と同じ `> [!NOTE]` 記法にし、<b>引用のレンダラを自前にして</b>枠にする。**知らない印はただの引用として描く**（`[!NOTES]` のような書き間違いで本文が消えるのを防ぐ）。(2) 前後のリンクは<b>目次と同じ並び</b>から出す（別に順序を持たない）。(3) コピーのボタンは<b>生成時ではなく JavaScript で足す</b>（JS が無い環境や印刷で、押せないボタンだけが残らないように）。**外部 CDN には繋がない**方針は変えていない | NF-D-08。`docs/design-m11.md` 14 章 |
| D-81 | 共通設定の読み込みは `include` に任せる | **環境別ファイルがあればそれ<b>だけ</b>を読む。**D-80 では裏で `application.conf` に fallback していたが、実アプリの `application.local.conf` は<b>1行目に `include "application.conf"` と書いてある</b>。両方あると、共通が入っているのが<b>ファイルの include のおかげなのかフレームワークのおかげなのか、ファイルを見ても分からない</b>（原則1）。読むのは1つにして、続きは書いてあるとおりにした。**書き忘れると共通が丸ごと落ちる**ので、共通にしかないキーを<b>葉まで比べて</b>起動ログに名指しで出す（トップレベル名にまとめる。数百件出すとログとして読めない）。値は触らない（未解決の `${?ENV}` で落ちるため） | F-U-01b / F-U-01d。`docs/design-m11.md` 7 章 |
| D-80 | 設定は jar の中だけから読む（**D-71 を撤回**） | **`-Djimble.conf.dir` と jar の外の `conf/` を読む処理を消した。**同じ名前の設定ファイルが jar の中と外の2か所にある形は、<b>「直したのに効かない」の原因がどちらなのか動かしてみるまで分からない</b>。ビルドし直す手間より、<b>動いている jar と設定が1対1である</b>ことを取る。`conf/` をリソースに足すのは残す（中身は jar に入る）。読む順は `application.<env>.conf` &gt; `application.conf` &gt; システムプロパティで、**重ねて読む**（環境別は差分だけ書けばよい）。<b>「環境別が無ければ `application.conf`」は、そのまま「環境別だけを読む」にはしない</b>——実アプリの `application.local.conf` は DB とバッチ管理の差分しか書いておらず、`cipher` / `session` / `server` は共通側にしか無い。片方だけ読む形にすると<b>起動した瞬間に設定が消える</b>。環境ごとに変わる秘密は `${?ENV}` で環境変数から入れる（F-U-13）。読んだファイルは URL のまま起動ログに出すので、<b>jar の中のどれを読んだかまで分かる</b> | F-U-01b。`docs/design-m11.md` 7 章 |
| D-79 | バッチの `env=` 引数 | **食い違っていたらその場で止める。**`env=` は解析していたが<b>どこでも使われていなかった</b>。設定はバッチの入口に来る前に読み終わっているので、受け取っても手遅れである。それでも移送元からの書き方として README に残っており、<b>「本番のつもりで local の設定で流していた」</b>が起こりえた。黙って無視するのが最悪なので、`Conf.env()` と違えば例外にし、`-Djimble.env` を使えと言う | F-B-03 / F-X-05。`docs/design-m11.md` 13 章 |
| D-78 | `-Djimble.env` が読まれていなかった | **`jimble.env` を読む**（`env` も読み続ける）。ドキュメントも README も雛形も `java -Djimble.env=prod` と書いてあったのに、`Conf` が見ていたのは `env` だけだった。**間違えても何も言わずに `local` で動く**ので、`application.prod.conf` が読まれず `isProduction()` も false のまま。本番でこれが起きても動いてしまう | F-U-01 / F-X-05。`docs/design-m11.md` 13 章 |
| D-76 | `jimbleRun` のデバッグ（**D-77 で撤回**） | **アプリのプロセスに JDWP を付けられるようにする**（`debug` / `debugPort` / `debugSuspend` / `debugListen`、`-Pjimble.debug`）。`jimbleRun` は<b>アプリを別プロセスで起動する</b>（要件 F-X-02。Gradle デーモンのヒープや JVM に引きずられないため）ので、IDE の「Gradle タスクをデバッグ実行」で止まるのは<b>Gradle のほう</b>であり、アプリのブレークポイントには止まらない。**ホットリロードでは `debugListen = true`（アプリのほうから IDE に繋ぎに行く）を勧める**。既定（アプリが待ち受ける）だと、作り直しでプロセスが入れ替わるたびに繋ぎ直しが要る。待ち受けは **`localhost` に固定**する（JDWP は任意のコード実行に等しいので外に開けない。あわせて `*:port` は IPv6 の無い環境でアプリが起動しない） | F-X-02 / F-X-05。`docs/design-m11.md` 12 章 |
| D-75 | SPA のパスのマッチ | **本体の `Router`（ルートツリー）を使う。**独自の正規表現マッチをやめた。<b>2つ目のマッチ実装を持っていたこと自体が問題</b>で、移送時に見つけた1件（パスの文字をそのまま正規表現に埋めていて `/a.b/{id}` が `/axb/1` に当たる）を直したあとも、**優先順位（登録順 → 固定 &gt; 変数 &gt; ワイルドカード）・後戻り・パーセントエンコード（デコード済みのパスを見ていた）・重複（黙って先勝ち）**が本体と食い違ったままだった。ツリーは<b>本体とは別のインスタンス</b>を持つ（起動時の一覧や重複判定に SPA のパスを混ぜない。F-W-20）。ワイルドカードも書けるようになった | F-W-17 / F-W-20。`docs/design-m11.md` 11 章 |
| D-74 | 「依頼を積めませんでした」の扱いと、起動の順番 | **(1) 理由を返す。**バッチ管理画面の「いま動かす」は失敗すると 500 と `依頼を積めませんでした` だけを返し、<b>理由はサーバーのログにしか無かった</b>（`MqExecutor.put` が `-1` を返すだけで `db.getError()` を捨てていた）。キュー名と DB のエラーを返すようにした。**(2) キューのテーブルを作ってからハートビートを打つ。**逆だったので、<b>「スケジューラは動いている」と見えているのに `mq_scheduler` がまだ無い</b>隙間があった。管理画面は `isRunning()` を見てから積むので、その隙間に押すと必ず 500 になる | F-B-11 / F-X-05。`docs/design-m11.md` 10 章 |
| D-73 | JSON レスポンスの Content-Type | **`send(Data)` が `application/json; charset=UTF-8` を付ける。**付けていなかった（JSONL 側は最初から付けていたので、単なる付け忘れ）。**ブラウザの `fetch().json()` は Content-Type を見ないので気づけない。**困るのは `curl \| jq`、プロキシ、`Accept` で振り分ける中継、他言語のクライアント。自分で `Content-Type` を設定した場合は上書きしない | F-W-04 / F-H-03。`docs/design-m11.md` 9 章 |
| D-72 | 壊れた Gradle ラッパーの扱い | **掴む前に使えるか確かめ、駄目なら理由を言って PATH の `gradle` に逃がす。**`jimbleRun` は作り直しに `./gradlew` を子プロセスで叩く。`gradlew`（`-jar` で起動する新しい形）と `gradle-wrapper.jar`（`Main-Class` を持たない古い形）が食い違っていると、**保存のたびに「メイン・マニフェスト属性がありません」だけが出て、ホットリロードが壊れたようにしか見えない**。両方とも<b>そこに在る</b>ので、ファイルの有無だけを見ていた既存のチェックでは通ってしまう | F-X-02 / F-X-05。`docs/design-m11.md` 8 章 |
| D-71 | 設定ファイルの置き場（**D-80 で撤回**） | **jar の外の `conf/` を先に読む。**クラスパスにしか無いと<b>値を1つ変えるのにビルドが要る</b>。あわせて `build.gradle.kts` で `conf` をリソースに足す。これで<b>`conf/` の中身はそのまま jar にも入る</b>ので、配った jar だけで動く道は残り、`codegen` / `migrate` / `jimbleRun` / テストもクラスパスから見つけられる。**「jar から出す」のではなく「jar の外に上書き先を作る」**のが要点。移送元の `jooby_base` アプリも同じ形だった。配布物（`installDist`）では起動スクリプトに `-Djimble.conf.dir=$APP_HOME/conf` を渡し、<b>どこから起動しても配布物の `conf/` を読む</b>。同じ名前のファイルが2か所にある形なので、**どれを読んだかを起動ログに出す**（`設定: /opt/app/conf/application.conf（クラスパスより優先）`）。出さないと「直したのに効かない」の原因が追えない | F-U-01b。`docs/design-m11.md` 7 章 |
| D-69 | `before` / `after` / `error` の効く範囲 | **書いたブロック（レキシカルスコープ）に付ける。**パスのノードに付ける形をやめた。パスに付けると「同じパスに後から誰かが足したルート」まで守られ、<b>コードを読んでも効いているかどうか分からない</b>（実際、移送した RSS アプリでは `AdminController` の認証フックが別に登録した SPA のログイン画面まで捕まえていた）。<b>速さの面でも有利</b>で、パスのノードに付ける形はリクエストのたびに親を辿って集め直していた（チェーン walk ＋ `ArrayList` 3本＋`ArrayDeque` 1本）。書いた場所に付くならルートごとのフック列は起動時に決まるので、マッチ時の確保がゼロになる。**確定後にフックを足したら例外**にする（「足したのに効かない」を黙って通さない） | F-R-08 / F-R-09 / F-R-10。`docs/design-m11.md` 3 章 |
| D-67 | `hash.password.encrypt` の既定 | **固定の `false` にせず、`cipher.key` が設定されていれば `true`。**固定の `false` にすると<b>移送してきたアプリが黙って壊れる</b>。保存済みのハッシュは暗号化されているので、鍵を設定しているのに平文の BCrypt として照合すると<b>全員ログインできなくなる</b>。しかも「パスワードが違う」と区別がつかない。何を選んだかは起動ログに出す（`パスワード暗号化=あり/なし`） | F-X-05 / F-Y-10。`docs/design-m11.md` 1.3 |

**あわせて記録：`codegen` が `session` テーブルを吐いていた。**
jimble が作るテーブルの除外一覧（`GeneratorConf.FRAMEWORK_TABLES`）に
`session` が抜けていた。M8 で `batch_*` が抜けていたのと同じである。
**「jimble が作るテーブル」の一覧が作る側と除外する側の 2 か所にある**限り、また抜ける（残件）。
→ **D-68 で済**。名前は `io.jimble.db.FrameworkTables` にしか書かない形にし、作る側（`Migration` / `DBLock` / `BatchTables` …）も外す側（`GeneratorConf#excludeTables()`）も同じ定数を使う。
<b>残る抜け道は「定数を足して `ALL` に足し忘れる」の1つだけ</b>で、それは `FrameworkTablesTest` がリフレクションで塞いでいる。

**あわせて記録：バッチ管理画面で「成功」が一度も出ていなかった。**
`say('保存しました')` の直後に呼ぶ `reload()` が<b>先頭で `say('')` していた</b>ので、
成功のときだけ何も出ない形になっていた（エラーは `return` するので出ていた）。
再読み込みのあとに出すよう直し、緑の帯にして見えるようにした。
**履歴の「詳細」も、表の下ではなく押した行のすぐ下に出す**ようにした
（30 行あると画面外に飛ぶため）。
**履歴をページング式にした**（サーバーは最初から `paging` を返していて、画面が使っていなかった）。
**表が空のときは理由を書く**ようにし、`reload()` を `Promise.allSettled` にした
（`Promise.all` だと1つの失敗で残りの表も空になり、「何も表示されない」だけが残る）。
要件 F-B-11 / F-X-05。

**あわせて記録：ペッパーの入れ方は直していない。**
`BCrypt.hashpw(password) + pepper` はハッシュした<b>後ろ</b>に繋いでいるので、
総当たりを難しくする効果が無い。直すと保存済みのハッシュが全部照合できなくなるため、
効果が無いことと移行の段取りが要ることを Javadoc に書くだけにした。

**あわせて記録：JDK の WebSocket クライアントは前の送信を待つ。**
`WsIntegrationTest` がたまに落ちていた原因はテスト側で、
`sendText()` の戻り値を待たずに 2 回続けて送っていた。
JDK の `java.net.http.WebSocket` は前の送信が終わる前に次を送ると
`IllegalStateException` になり、**その送信は捨てられる。**jimble 側は正しかった。

### 12.4 調査タスクの完了状況

| # | 項目 | 状態 |
|---|---|---|
| N-1 | `jooby_base` 本体の棚卸し | **完了。**`docs/jooby-base-inventory.md` / `docs/jooby-base-class-list.txt`。カバレッジ表は 28 → 50 行に |
| N-5 | `ExtendsJooby` / `AppContext` の精読 | **完了。**`docs/extends-jooby-analysis.md`。F-R-21・22 / F-C-11〜13 / F-E-06 / F-S-12 / F-W-18〜20 / F-X-07 を追加。処理フロー（8.1）を実装仕様に更新 |
| N-6 | 既存 Router の結線設計 | **完了**（M1） |
| N-7 | `WebRequest` / `WebResponse` の jooby 参照の中身 | **完了。**13 行の内訳は `docs/design-m2.md` 1 章。jooby 依存は「ヘッダ・クエリ・フォーム・ファイル・ボディ本文」の5つの入口に集約されていた。**Jetty 内部へのリフレクション回避策が丸ごと不要になる** |

### 12.5 残件

| # | 項目 | 内容 |
|---|---|---|
| N-3 | サンプルアプリの詳細仕様 | **仕様は済**（`docs/design-n3.md`。D-132）。題材は「社内の申請・承認」、機能ごとに単体で完結するサンプルを7本、PostgreSQL 単独。<b>7本すべて実装済み</b>（D-132〜D-136 / D-139 / D-140 / D-141）。**この項目は済** |

---

## 付録A. 参考

- doc.txt（基本概要）
- `docs/design-m1.md`（**M1 設計書**）
- `docs/design-m2.md`（**M2 設計・実装メモ** + N-7 調査結果）
- `docs/design-m3.md`（**M3 設計・作業メモ**）
- `docs/design-m4.md`（**M4 設計・作業メモ**）
- `docs/design-m5.md`（**M5 設計・作業メモ**）
- `docs/design-m6.md`（**M6 設計・作業メモ**）
- `docs/design-m7.md`（**M7 設計・作業メモ**）
- `docs/design-m8.md`（**M8 設計・作業メモ**）
- `docs/probe-helidon.md`（helidon 4.5.4 + Java 25 疎通確認結果）
- `docs/extends-jooby-analysis.md`（`ExtendsJooby` / `AppContext` 精読結果 = M1 設計インプット）
- `docs/jooby-base-inventory.md`（jooby_base 棚卸し結果）
- `docs/jooby-base-class-list.txt`（全 375 クラス一覧）
- `docs/design-m9.md`（**M9 設計・作業メモ**：SSE / MCP / WebSocket / ドキュメントサイト / Maven Central / jimble.io）
- `docs/design-m10.md`（**M10 設計・作業メモ**：AsyncData の先読み）
- `docs/design-protocols.md`（**WebSocket / SSE / MCP / gRPC の検討書**）
- `docs/sample-app-analysis.md`（サンプルアプリ調査結果）
- `jooby_run`（移送元のホットリロードプラグイン。精読結果は `docs/design-m8.md` 5章）
- helidon-webserver 4.5.4 ドキュメント
- jte ドキュメント
- `docs/site/`（**ドキュメントサイトの原稿と出力**。`./gradlew :jimble-docs:site`）
- `docs/publishing.md`（**Maven Central への公開手順と、公開の記録**）
- `docs/docs-only-trial.md`（**「ドキュメントだけで1本作れるか」の実地確認**。Phase 2 の判断基準）
- `CHANGELOG.md`（**版ごとの変更点**）
- <https://github.com/hidemikimura/jimble>（**公開リポジトリ**）
- <https://central.sonatype.com/namespace/io.jimble>（**Maven Central**）
- <https://jimble.io>（**ドキュメント**）
- <https://github.com/hidemikimura/jimble-document>（ドキュメントの配布リポジトリ）
