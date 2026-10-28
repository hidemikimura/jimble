---
title: 同じ作りの DB を並べる
summary: Web・バッチは 1 組のまま、クライアントごとに DB だけを分ける。設定・要求ごとの DB の選び方・マイグレーション・生成コード・MQ・接続の数・落ちたとき
section: データベース
order: 7
---

# 同じ作りの DB を並べる

Web とバッチのサーバーは 1 組のまま、**同じ表の作りの DB** を `db.<名前>` で何本も並べ、
要求ごとにクライアントの DB を選ぶ使い方です（2.7.8 から、このための設定がそろいました）。

- **向くこと**：クライアントごと（またはクライアントのまとまりごと）に DB を分けたい。大きなクライアントだけ専用の DB にしたい
- **向かないこと**：データソースが**数百以上**。プール・MQ の取り出し・起動の確かめが、データソースの数に比例して増えます。
  数十までを目安にしてください（1 社 1 DB にはしない）

共通の DB（主。`main = true`）には、クライアントの台帳・スケジューラ・バッチの管理などを置き、
店の DB には業務の表と MQ のキューの表を置きます。

## 設定の例

```conf
# 並べる設定の例
db_templates {
	tenant {
		driver             = "org.mariadb.jdbc.Driver"
		username           = ${?SHOP_DB_USER}
		password           = ${?SHOP_DB_PASSWORD}
		maximum_pool_size  = 6
		minimum_idle       = 0              # 使っていない DB の接続を開いたままにしない
		connection_timeout = 3s             # つながらない DB で起動を延ばしすぎない
		migration_dir      = "shop"         # どの店の DB にも同じ SQL を当てる
		migrate_on_startup = false          # 起動では当てず、デプロイの段で CLI から当てる
		codegen_name       = "shop"         # 生成コードは 1 組だけ
		required           = false          # 1 つ落ちても、起動と死活監視を止めない
		framework_tables   = ["db_lock"]    # 店の DB に要らない管理の表を作らない
	}
}

db {
	startup_parallelism = 8                 # 起動時の確かめを並べる

	main {
		main     = true
		driver   = "org.mariadb.jdbc.Driver"
		url      = "jdbc:mariadb://db0/common"
		username = ${?DB_USER}
		password = ${?DB_PASSWORD}
		mq       = false                    # 共通の DB にはキューを置かない
	}

	shop_a = ${db_templates.tenant} { url = "jdbc:mariadb://db1/shop_a" }
	shop_b = ${db_templates.tenant} { url = "jdbc:mariadb://db1/shop_b" }
}

mq {
	hint           = redis                  # 仕事のある DB だけを見に行く
	sweep_interval = 10s
}
```

それぞれの設定は、[DB](./db)・[マイグレーションとコード生成](./codegen)・[MQ](./mq) に詳しく書いてあります。
ひな形（`db_templates`）は `db { }` の外に置き、`${}` で写して上書きします。

## 要求ごとに DB を選ぶ

入口のフィルタで、**Host などから** DB を選び、要求に持たせます。

```java
before(context -> {
	String dataSource = tenants.dataSourceOf(context.request().host());   // 台帳から引く（キャッシュする）
	if (dataSource == null) {
		throw new HttpException(404, "知らないホストです");
	}
	context.attribute(SHOP_DB, dataSource);
});

get("/orders", context -> {
	try (DB db = DBUtil.getDB(context.<String>attribute(SHOP_DB))) {
		context.response().json("orders", db.selectList(SQL.select().from(Order.instance())));
	}
});
```

> [!WARN]
> **選ぶ値を、要求のパラメータ（クエリ・フォーム・JSON）から取らないでください。**
> 値を書き換えるだけで、別のクライアントの DB に当たります。Host のように、
> 経路で決まるものから引き、店の DB の中でも `shop_id` で絞る決まりは残してください。

台帳（Host → データソース名）は共通の DB に置き、引いた結果はキャッシュします（すべての要求が共通の DB に集まるため）。

## 主に残るもの

次のものは、主のデータソースにだけ置かれます（店の DB には要りません）。

- スケジューラ（`batch_master` など）と、スケジューラの MQ のキュー（`mq_scheduler`）
- バッチの実行の記録（`batch_history` など）。チャンク処理を店の DB で回しても、記録は主です（[バッチ](./batch)）
- セッションと認証の表（`session`・`auth_*`）、流量制限
- コードマイグレーション（`CodeMigration`）

## マイグレーションを当てる

店の DB は `migrate_on_startup = false` にして、**デプロイの段で CLI から当てます**。

```bash
java -cp app.jar io.jimble.db.cli.JimbleDbCli migrate --migration-dir shop --parallel 4 --keep-going
java -cp app.jar io.jimble.db.cli.JimbleDbCli migrateCheck --migration-dir shop
# そろっていれば、アプリを起動する
```

1 つ失敗してもほかを続け、最後にデータソースごとの結果を出します。`migrateCheck` は DB に書かずに、
未適用・余分・ハッシュ違いをデータソースごとに出します（[並べた DB に当てる](./codegen#並べた-db-に当てる)）。

## 生成コード

`codegen_name` を同じにすると、生成コードは 1 組だけです（代表は主、無ければ名前の順で最初）。
テーブルクラスは SQL に表の名前しか入れないので、**要求ごとに選んだ DB を渡して実行します**。
`Shop.db()` は代表の DB を返すので、店の DB の処理では使いません（[同じ作りの DB で生成コードを共有する](./codegen#同じ作りの-db-で生成コードを共有する)）。

## MQ

- **キューの表は、業務の表と同じ DB に置きます。**積むのと業務の更新が、同じトランザクションで一緒に巻き戻ります
- 共通の DB など、キューを使わない DB は `mq = false` で外します。外した DB に積むと例外です
- 取り出し役は、データソースを名前の順に、直前に取った DB の次から見ます（1 つの DB に溜まっても、ほかを待たせません）
- `mq.hint = redis` なら、仕事のある DB だけを見に行きます。全部を回るのは `sweep_interval` ごとで、印の無い行の遅れは最悪その間です
- 問い合わせの目安：`hint = off` だと、暇なときでも 1 プロセスで毎秒「キューの数 × 種別の数 × DB の数 × 2 ÷ `poll_max`」本の SELECT が出ます

詳しくは [MQ](./mq) の「キューを置くデータソースを絞る」「仕事のある DB だけを見に行く」を見てください。

## 接続の数

プールは「データソース × 書き込み／読み取り × 親とサブ」ごとにできます。

- 1 プロセスが常に開く本数 ＝ それぞれのプールの `minimum_idle` の合計
- 多いときの本数 ＝ それぞれのプールの `maximum_pool_size` の合計

上の例で店の DB が 30 個なら、店の DB の分は常に 0 本、多いときは 30 × 6 = 180 本です（主の分は別）。
DB サーバーの `max_connections` は、プロセスの数 × 多いときの本数より大きくしてください。

## SQL 結果キャッシュ

SQL 結果キャッシュは**データソースの名前で分かれます**。店の DB どうしで混ざることはありません。
**アプリが自分で持つキャッシュ**（台帳・設定・テンプレートなど）のキーには、**データソースの名前を入れてください**。
入れないと、別のクライアントの値が返ります。

## 1 つの DB が落ちたとき

店の DB は `required = false` にします。

- 起動時につながらなくても起動を続け、DB が戻ればそのまま使えます
- `DBUtil.healthCheck()`（死活監視）は `required = true` のものだけを見るので、1 つ落ちても台ごと外れません
- データソースごとの状態は `DBUtil.healthCheckAll()` で見られます
- 起動時につながらなかった DB には、管理の表とマイグレーションを当てていません。戻ったら CLI の `migrate --data-source` で当ててください

詳しくは [DB](./db#落ちても全体を止めないデータソース) を見てください。
