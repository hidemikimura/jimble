# 変更履歴

版の付け方は [Semantic Versioning](https://semver.org/lang/ja/) に従う。

---

## 0.2.0（未公開）

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
