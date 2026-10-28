---
name: jimble-db-migration
description: jimble のマイグレーションとコード生成を扱うときに使う。SQL の置き場所（migration_dir）、起動時に流す Migration.install() と DBUtil.load の順番と戻り値、並べた DB を CLI で当てる（migrate・migrateCheck）、何台も同時に起動するとき、jimble が作る表、codegen のクラス名・パッケージ・外す表（exclude_tables）・codegen_name、生成物の扱いと落とし穴を含む。
---

# jimble のマイグレーションとコード生成

全体の決まりと、ほかの skill への案内は `jimble-db`。`db.<名前>` の設定は `jimble-db-config`。

```
conf/migration/<フォルダ名>/001_xxx.sql      # --- !Ups / # --- !Downs
./gradlew migrate
./gradlew codegen
```

**マイグレーションの SQL は方言を吸収しない**——PostgreSQL なら PostgreSQL の DDL を書く。

## マイグレーション

- `<フォルダ名>` は `db.<名前>.migration_dir`。無ければ `schema`、それも無ければデータソース名（2.7.8 から）。
  **同じ表の作りの DB を並べるときは、どれにも同じ `migration_dir` を書く**。同じ SQL がそれぞれの DB に1回ずつ当たり、記録もそれぞれの DB に残る
- **`CodeMigration`（Java で書くマイグレーション）は主にしか当たらない**
- 並べた DB は**起動では当てず、デプロイの段で CLI から当てる**（2.7.8 から）。`db.<名前>.migrate_on_startup = false` を付け、
  `JimbleDbCli migrate --migration-dir shop --parallel 4 --keep-going` → `JimbleDbCli migrateCheck --migration-dir shop` → 起動。
  Gradle は `-Pjimble.migrateArgs="..."`。`migrateCheck` は DB に書かず、未適用・余分・ハッシュ違いがあれば 0 以外で終わる
- 何台が同時に起動しても1台だけが当てる（`db_lock` の行ロック。DDL を流す接続とは別の接続で持つので、DDL の暗黙コミットで外れない）。
  そのため**マイグレーションを当てるデータソースは `maximum_pool_size` を 2 以上にする**（1 だと例外で止まる）
- `required = false` のデータソースで起動時につながらなかったものには当てない（CLI の `migrate --data-source` で当てる）

起動時に流すなら `Migration.install()` を **`DBUtil.load(...)` より前**に呼ぶ。

```java
Migration.install();                                  // 登録するだけ。ここでは流れない
DBUtil.load(Conf.conf().config(), App.class);         // 流れるのはこの中。繋がらなければ例外で止まる
```

- **`install()` の戻り値は「登録したか」。流れたかではない。**`false` になるのは
  `migration.on_startup = false` のときと、**既定の `auto` でローカル（`isLocal()`）のとき**（手元は `./gradlew migrate` で当てる）。
  `if (!Migration.install()) throw ...` と書くと、**手元でも**起動しなくなる。
  **`true` でもまだ1行も流れていない**
- **`DBUtil.load(...)` のあとに呼ぶと流れない。**登録した処理はもう走り終わっている。WARN が1度出るだけで例外にはならない
- **`DBUtil.load(...)` は `void`。**繋がらなければ `SqlExecuteException`（`DB_007`）、流すのに失敗しても例外で落ちる。
  `if` で囲まず、捕まえずに起動を止める

jimble が使う表は、無ければ起動時に作る。空の DB に何台かが同時に初めて起動しても、ほかの台が先に作った表（コメントが「説明:版」）はそのまま使う（2.7.8 から）。
**アプリが jimble と同じ名前の表を持っていると起動で止まる**（コメントで見分けるので、黙って使わない）。

## コード生成

生成物は**リポジトリに入れる**（入れないと DB の無い環境でビルドできない）。
**`db/<データソース>/` は毎回まるごと作り直される。**手で書いたファイルを置くと次の `codegen` で消える。

**クラス名はテーブル名をそのまま UpperCamel にしたもの。**`orders` → `Orders`、
`audit_log` → `AuditLog`。<b>単数形にはしない。</b>
列は静的フィールドで、名前は DB のまま（`Orders.staff_id`）。

パッケージは `db.<データソース名>` の下に来る（`db` は `codegen.package` の既定）。

```java
import db.shop_example.ShopExample;              // スキーマクラス（db() の入口）
import db.shop_example.table.orders.Orders;      // テーブルクラス
```

- 同じ表の作りの DB を並べるときは `db.<名前>.codegen_name` に同じ名前（小文字）を書くと、**生成コードは 1 組だけ**（2.7.8 から）。
  代表（主、無ければ名前の順で最初）の DB から作る。テーブルクラスはどの DB に渡してもその DB で動くので、
  **要求ごとに選んだ DB を渡して実行する**（`Shop.db()` は代表を返す）。`codegen = false` で生成しない

**jimble が自分で作るテーブル**（`migration` / `session` / `db_cache` / `batch_*` / `auth_*` など）は
codegen が自動で外す。**MQ のキュー表は外れない**——名前をアプリが決めるので、jimble の管理テーブルに入っていない。
`DbScheduler` が使う **`mq_scheduler`**（`scheduler.queue_name` の既定）も同じ。
**`session.table` で名前を変えたセッションの表も外れない**（外れるのは既定名の `session` だけ）。外すなら並べる。

```conf
codegen {
	exclude_tables = ["mq_notice", "mq_scheduler"]   # ワイルドカード（mq_*）は使えない。完全一致で1つずつ
}
```

外したテーブルは codegen のログに名前が出る。**アプリのテーブルが jimble の管理テーブルと同じ名前**
（`session` など）だと黙って生成されないので、ログを見る。

## 版を上げたあと

- **DB のコメントに `*/` や `\` があると、codegen はそれを `*&#47;` / `&#92;` にして書く**（2.2.4。生成するソースのコメントが閉じないように）。
  上げたあと `codegenCheck` がそのテーブルだけ差分を出したら、`codegen` で作り直してコミットする
- **2.5.4 で、スキーマのクラスの `SchemaSQL` の組み立て方が変わった**（テーブルごとのメソッドで足す。中身の SQL は同じ）。
  上げたあと `codegenCheck` が差分を出したら、`codegen` で作り直してコミットする

詳しくは <https://jimble.io/ja/codegen.md>。
