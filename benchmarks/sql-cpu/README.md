# SQL の組み立てと設定の読み出しの CPU

要求ごとに払っていた CPU（SQL の組み立て・SQL 結果キャッシュのキー・Cookie と応答の設定の読み出し）を、
公開版（2.7.11 からは jimble の置き場 `https://repo.jimble.io`、それより前は Maven Central）とこのリポジトリのソースで比べる。jimble 本体のビルドには入れない。

```
./benchmarks/sql-cpu/run.sh
```

| 測るもの | 中身 |
|---|---|
| `BuildBench.*` | DB に繋がずに組み立てるだけ。全列・列の名指し・JOIN・IN（値の数が変わる）・UPDATE・INSERT と、列の多い表（`WideSchema`。60・40・30 列）の全列 SELECT・3 表 JOIN。`*NoSql` は `sql()` を呼ばずビルダーを作って値を集めるだけ |
| `TemplateBench.*` | `BuildBench` の `pkAll`・`joinAll`・`wideJoin3` を `SQL.template` にしたもの（`bind` して SQL とパラメータを取り出すまで）。3.0.0 からなので、`-Pjimble.local=true` のときだけビルドに入る |
| `TemplateCpuPerQuery` | 主キー 1 行の `select` と、キャッシュに当たる `selectListCached` を、ビルダーと template で投げたときのスレッドの CPU 時間。`-Pjimble.local=true` のときだけ |
| `DbPathCpu` | DB の道（組み立て・バインド・ドライバ・結果の読み取り）のスレッドの CPU 時間。主キー 1 行の `select`、20 行・全列の `selectList`、キャッシュに当たる `selectListCached` |
| `DriverCpu` | `DbPathCpu` と同じ道を、JDBC ドライバごとに測る（主キー 1 行の `select`・20 行の `selectList`・主キー 1 行の `update`・20 行の `executeBatch`）。ドライバは設定で替える（下の「ドライバを比べる」） |
| `ProfileLoop` | JFR で内訳を見るために、1 本のスレッドで同じ SELECT を回し続ける（`pk` / `list` / `cached`） |
| `HashBench` | SQL 結果キャッシュのキーなどに使うハッシュを、長さ 64〜4,000 byte で比べる（SipHash・値を変えずに書き直した SipHash・XXH3-64 / 128・rapidhash v3・komihash 5。後ろの 4 つは hash4j。`-Pjimble.local=true` のときだけビルドに入る） |
| `DataReadBench.*` | 結果の行（表名で入れ子にした `Data`）から 12 個読む。列で読む（`row.getLong(Product.id)`）・表の `Data` を取ってから列名で読む・`flattenTable` してから読む |
| `DriverParseBench.*` | MariaDB Connector/J が `prepareStatement` のたびに SQL の字面を解析する手間（`rewriteBatchedStatements=true` の `rewritableParts` と false の `parameterParts`、2.x の別名つきと 3.0 の別名なしの SQL） |
| `SqlBench.pkBuild` | 主キー 1 行の SELECT（全列）の SQL とパラメータを作る |
| `SqlBench.joinBuild` | JOIN・IN・IS NULL・ORDER BY・LIMIT / OFFSET の SELECT（全列）を作る |
| `SqlBench.cachedSelect` | SQL 結果キャッシュ（メモリ）に当たる `selectListCached`。DB には行かない |
| `SqlBench.pkSelect` | 主キー 1 行の `select` を bench 用の MySQL に投げる。時間は往復の待ちを含む |
| `WebBench.cookieResponse` | Context を作り、署名つき Cookie を 1 つ付けて応答し、後始末するまで |
| `CpuPerQuery` | `pkSelect` と `cachedSelect` の 1 回あたりのスレッドの CPU 時間（待ちを含まない） |

ログは `src/main/resources/logback.xml` で INFO にしてある。置かないと logback の既定（DEBUG）になり、
MariaDB のドライバが 1 回ごとに SQL 全文を出して、その書き出しまで測ってしまう（3.0.0 で気づいた。それまでの DB に行く数字はこれを含む）。

テーブル定義は生成物と同じ形（`instance()` は毎回 new、列一覧は静的に宣言）にしてある。
結果は `results/summary.md` に出る（`results/` は版管理しない）。

## ドライバを比べる（`DriverCpu`）

MariaDB Connector/J（既定）と mysql-connector-j を、同じ jimble・同じ SQL で比べる。設定は `-Djimble.env` で替える。

| `-Djimble.env` | ドライバ | 指定 |
|---|---|---|
| （無し） | MariaDB Connector/J 4.0.0 | `application.conf`（aqSell と同じ指定） |
| `cj` | mysql-connector-j 26.10.0 | `application.cj.conf`（上と同じ意味の指定） |
| `cjcache` | 同上 | `application.cjcache.conf`（`cachePrepStmts=true`。クライアント側の prepare の SQL の解析を覚える） |

```
../../gradlew -Pjimble.local=true installDist
java -Xms1g -Xmx1g -Djimble.env=cj -cp "build/install/bench-sql-cpu/lib/*" bench.DriverCpu 20000
```

JVM ごとの JIT のゆれが大きいので、指定を交互に何回も流し、2 ラウンド目からの真ん中の値で比べる。
2026-11-15 に 5 回ずつ流したときの真ん中の値（µs CPU/回。手元の Mac・負荷平均 約 5 で、同じ指定でも 3 割ほどゆれた）：

| | 主キー 1 行 | 20 行の一覧 | UPDATE 1 行 | 20 行の executeBatch |
|---|---:|---:|---:|---:|
| MariaDB 4.0.0 | 33.0 | 52.5 | 22.8 | 480 |
| mysql-connector-j 26.10.0 | 45.7 | 75.6 | 29.6 | 573 |
| 同上＋`cachePrepStmts=true` | 36.7 | 87.3 | 27.5 | 529 |
