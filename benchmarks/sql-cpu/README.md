# SQL の組み立てと設定の読み出しの CPU

要求ごとに払っていた CPU（SQL の組み立て・SQL 結果キャッシュのキー・Cookie と応答の設定の読み出し）を、
Maven Central の公開版とこのリポジトリのソースで比べる。jimble 本体のビルドには入れない。

```
./benchmarks/sql-cpu/run.sh
```

| 測るもの | 中身 |
|---|---|
| `BuildBench.*` | DB に繋がずに組み立てるだけ。全列・列の名指し・JOIN・IN（値の数が変わる）・UPDATE・INSERT と、列の多い表（`WideSchema`。60・40・30 列）の全列 SELECT・3 表 JOIN。`*NoSql` は `sql()` を呼ばずビルダーを作って値を集めるだけ |
| `SqlBench.pkBuild` | 主キー 1 行の SELECT（全列）の SQL とパラメータを作る |
| `SqlBench.joinBuild` | JOIN・IN・IS NULL・ORDER BY・LIMIT / OFFSET の SELECT（全列）を作る |
| `SqlBench.cachedSelect` | SQL 結果キャッシュ（メモリ）に当たる `selectListCached`。DB には行かない |
| `SqlBench.pkSelect` | 主キー 1 行の `select` を bench 用の MySQL に投げる。時間は往復の待ちを含む |
| `WebBench.cookieResponse` | Context を作り、署名つき Cookie を 1 つ付けて応答し、後始末するまで |
| `CpuPerQuery` | `pkSelect` と `cachedSelect` の 1 回あたりのスレッドの CPU 時間（待ちを含まない） |

テーブル定義は生成物と同じ形（`instance()` は毎回 new、列一覧は静的に宣言）にしてある。
結果は `results/summary.md` に出る（`results/` は版管理しない）。
