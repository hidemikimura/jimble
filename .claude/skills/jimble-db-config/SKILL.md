---
name: jimble-db-config
description: jimble の DB と Redis の設定を書くときに使う。db.<名前> のキー（snake_case・知らないキーは起動で落ちる・単位）、プールの大きさと Agroal、落ちても全体を止めないデータソース（required）、管理の表を絞る（framework_tables）、SQL 結果キャッシュ（selectCached・sql_cache・サブ DB）、Redis のパスワード、MySQL と PostgreSQL の違い（DialectException）を含む。
---

# jimble の DB と Redis の設定

全体の決まりと、ほかの skill への案内は `jimble-db`。設定ファイル全般（HOCON・環境変数・単位）は `jimble-config`。

## db.<名前> の設定（要件 D-159）

**snake_case で書く**（`maximum_pool_size` / `minimum_idle` / `idle_timeout` /
`max_lifetime` / `connection_timeout` / `keepalive_time` / `fetch_size` / `schema` / `migration_dir`）。
**camelCase の古い綴りも読めるが、起動時に1度だけ警告が出る**（`scheme` は綴り違いだった）。

**表に無いキーを書くと起動時に落ちる。**以前は黙って無視されたので、
`maximum_pool_size` を打ち間違えたアプリは**プールが既定のまま**だった。

時間は**単位を値に書く**（`idle_timeout = 5m`）。素の数値は落ちる。

**プールの大きさは、書かなければ上限 10・最小 1**（2.2.1。それまでは書かないと HikariCP は最小も 10、Agroal は起動時に落ちた）。
`maximum_pool_size` が 1 未満、`minimum_idle` が負なら起動時に落ちる。
**マイグレーションを当てるデータソースは `maximum_pool_size` を 2 以上**（`jimble-db-migration`）。

**`connection_pool_type = "agroal"` でも、DB が再起動すれば切れた接続は捨てる**（`product` に合った見分け方を渡す。2.2.1）。
`validate_on_borrow = true` は取り出すたびに確かめるので**速さが半分ほど**になる。再起動に備えるだけなら要らない。

## 落ちても全体を止めないデータソース（2.7.8）

店の DB のように、1 つ落ちても起動・死活監視を止めたくないデータソースは **`db.<名前>.required = false`**。
つながらなくても起動を続け、DB が戻ればそのまま使える（HikariCP は数秒かかる）。起動時につながらなかったものには管理の表・マイグレーションを当てない（CLI の `migrate --data-source` で当てる）。
`DBUtil.healthCheck()` は required のものだけ、データソースごとは `DBUtil.healthCheckAll()`。主は required = false にできない。

## 管理の表を絞る（2.7.8）

データソースごとに `db_lock` / `db_value` / `db_cache` / `redis_lock` / `db_log` を作る。使わない DB は
`db.<名前>.framework_tables = ["db_lock"]` で絞る（**`db_lock` は外せない**。マイグレーションのロックに使う）。
外した表を使う API（`DBValue`・`DBCache`・`RedisLock.lock(db, ...)`・`DBLog.save`）をそのデータソースで呼ぶと `IllegalStateException`。

キューの表を置くデータソースを絞るのは `db.<名前>.mq = false`（`jimble-mq-worker`）。

## SQL 結果キャッシュ

- **SQL 結果のキャッシュはデータソースごとに分かれる**（2.2.3）。1つの Redis を複数のアプリや環境で分け合うなら、
  それぞれ `sql_cache.namespace` を別の値にする
- キャッシュのキーとタグはデータソースごと（`DBSource.cacheName()`。2.7.8）。`subs` は親と同じ URL なら親と同じ名前（サブで書けば親のも消える）、
  別の DB を指すなら「親.サブ」。2.7.7 までは別の DB を指すサブでも親と混ざっていた。URL が同じ文字列のときだけ同じ DB とみなす
- **`sql_cache.ttl` は単位を書く**（`5m` / `300s`。無期限は `0s`）。**`ttl = 300` は起動時に落ちる**（2.5.2。
  それまでは入れるたびに失敗してキャッシュが効いていなかった）。`sql_cache.enabled = false` でも見る
- **`selectCached` を使うなら、アプリの表の更新はビルダーで書く。**文字列の SQL の更新（`db.execute("UPDATE ...")` /
  `update(String, ...)` など）はどの行に当たるか読めないので、**SQL 結果キャッシュを丸ごと消す**
  （jimble 自身の表だけに当たる文は消さない。2.5.2）

## Redis

- **Redis にパスワードを掛ける**：`redis.password = ${?REDIS_PASSWORD}`（Redis 6 の ACL なら `redis.username` も。2.2.3）。
  既定の行を先に置く2行の書き方は `jimble-config`
- Redis には**文字列として**読み書きする（2.2.3。それまでの Kryo は、読むときに何のクラスでも作った）。アプリは何もしなくてよい

## 製品の違い

`db.xxx.product` を見て SQL が変わる。識別子の囲み、`ON DUPLICATE KEY UPDATE` と
`ON CONFLICT`、`RAND()` と `RANDOM()` はビルダーが吸収する。
**その製品に無いものは、組み立てたところで `DialectException`**（PostgreSQL でインデックスのヒントなど）。

揃っていないものもある（`greatest`/`least` の NULL の扱い、正規表現の方言、
0 除算で MySQL は NULL・PostgreSQL は落ちる、`cast` できない文字列）。
一覧は `sql.md` の「製品の違いで気をつけること」。

詳しくは <https://jimble.io/ja/db.md> / <https://jimble.io/ja/cache.md> / <https://jimble.io/ja/config.md>。
