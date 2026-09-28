---
title: DB を使う
summary: データソースの設定、DB 製品の選び方、テーブル定義の生成、select の結果の形
section: データベース
order: 1
---

# DB を使う

## 設定する

`application.conf` にデータソースを書きます。

```conf
db {
	blog_example {
		main     = true
		driver   = "org.mariadb.jdbc.Driver"
		url      = "jdbc:mariadb://127.0.0.1:3306/blog_example?useUnicode=true&characterEncoding=UTF-8"
		url      = ${?DB_URL}
		username = "blog"
		username = ${?DB_USER}
		password = ""
		password = ${?DB_PASSWORD}

		maximum_pool_size      = 10
		connection_pool_type = "hikari"
	}
}

codegen {
	package = "db"
}
```

**秘密はファイルに書かないでください。** `${?ENV_NAME}` の形にしておくと、
環境変数があればそちらが勝ちます。無ければ前の行の値が残ります。

ドライバは `jimble-db` が MySQL / MariaDB と PostgreSQL の両方を持っているので、
ふつうは足す必要はありません。

## DB 製品を選ぶ

`product` に製品を書くと、**SQL ビルダーがその製品用の SQL を出します。**

```conf
db {
	blog_example {
		main     = true
		product  = "postgresql"
		driver   = "org.postgresql.Driver"
		url      = "jdbc:postgresql://127.0.0.1:5432/blog_example"
		username = "blog"
		password = "blog"
		password = ${?DB_PASSWORD}
	}
}
```

> [!TRAP]
> **`${?ENV}` の行だけを書かないでください。**環境変数が無いときに<b>キーごと消えます</b>
> （上の書き方は「まず既定値、あれば環境変数で上書き」の2行組です）。
> パスワードが消えたまま PostgreSQL に繋ぐと、返ってくるのは
> `The server requested SCRAM-based authentication, but no password was provided.` で、
> <b>設定の話に見えません。</b>

## データベースがまだ無いとき

`create_database_sql` を書いておくと、**繋がらなかったときに1度だけ流します。**

```conf
db {
	blog_example {
		...
		create_database_sql = "CREATE DATABASE blog_example"
	}
}
```

書かなければ何もしないので、**手で作ってあるなら要りません。**
本番では<b>権限を持たない利用者で繋ぐほうが普通</b>なので、開発と CI のためのものです。

| 書ける値 | 製品 |
|---|---|
| `mysql`（既定）／ `mariadb` | MySQL 8.x / MariaDB |
| `postgresql` ／ `postgres` ／ `pgsql` | PostgreSQL 16 以降 |

省略すると `mysql` です。知らない値を書くと**起動時に落ちます**。

**アプリのコードは1行も変わりません。**

```java
// この1行が、product に応じて
//   MySQL      SELECT `post`.`id` AS `post__id` ... LIMIT ? OFFSET ?
//   PostgreSQL SELECT "post"."id" AS "post__id" ... LIMIT ? OFFSET ?
// になります
SQL.select().from(Post.instance()).where(Post.id.eq(1L));
```

データソースごとに違う製品を書けます（メインは MySQL、集計用は PostgreSQL、など）。

> [!NOTE]
> **対応している製品は MySQL（MariaDB）と PostgreSQL の2つだけです。**
> 知らない名前を書くと**起動時に落ちます**——黙って MySQL に倒すと、
> PostgreSQL のつもりで書いたアプリに**バッククォートの SQL が飛びます**。
>
> **自前の方言を足す口はありません**（`Dialect` は `sealed` です）。
> 方言には SQL 関数ごとのメソッドが 40 以上あり、
> **jimble が関数を1つ足すたびに、外の実装が壊れる**ためです。
> 対応してほしい製品があれば、枠組みに足す形で受け付けます。

### その製品で書けないもの

**書けないものは、SQL を組み立てたところで `DialectException` になります。**
実行してから製品の構文エラーで落ちる、ということはありません。

| ビルダー | PostgreSQL |
|---|---|
| `Dsl.match(...)`（全文検索） | 書けない。`to_tsvector` は語彙の分割もスコアも別物なので、黙って置き換えません |
| `Dsl.dateFormat(col, "%Y-%m-%d")` | 書けない。`to_char` は書式の言語が違うので、置き換えると**例外にならずに違う文字列**が返ります。Java 側で整えてください |
| `Dsl.jsonExtract(col, "$.a[0]")` | 配列・ワイルドカード・引用符つきキーは書けません。`$.a.b` の形だけ |

逆に、**名前だけ違うものは黙って揃えます**（`RAND` / `RANDOM`、`IFNULL` / `COALESCE`、
`TRUNCATE` / `TRUNC` など）。`Dsl.concat(...)` は PostgreSQL では `||` になります
（PostgreSQL の `concat()` は NULL を空文字として飲み込むので、
名前だけ置き換えると MySQL と結果が変わります）。

どうしてもその製品の書き方を使いたいときは `Dsl.freeSql(...)` で逃げられます。

### 気をつけること

- **空間関数の座標の順が違います。**MySQL 8 の SRID 4326 は緯度・経度、
  PostGIS は経度・緯度です。jimble は吸収しないので、両方で使うなら自分で合わせてください
- マイグレーションの SQL（`conf/migration/...`）は**自分で書いたものがそのまま流れます。**
  両方の製品で動かすなら、`001_xxx.mysql.sql` / `001_xxx.postgresql.sql` と
  **ファイル名の接尾辞**で分けます（接尾辞なしはどちらでも当たります）。
  詳しくは [製品ごとに SQL を分ける](./codegen)

## 起動時に繋ぐ

**設定を書いただけでは繋がりません。**アプリの `main` で明示的に呼びます。

```java
public static void main (String[] args) {

	Migration.install();                                // 起動時マイグレーション（要るなら。DBUtil.load より前）

	DBUtil.load(Conf.conf().config(), App.class);       // 繋がらなければ例外。ここで止まります

	JimbleServer.start(new App());

}
```

`DBUtil.load` の2つ目は**クラスパスの起点**です（マイグレーション SQL をここから探します）。
自分のアプリのクラスを渡してください。

**繋がらなければ `SqlExecuteException`（`DB_007`）を投げ、起動はそこで止まります。**
戻り値はありません（`void`）。受け止めずにそのまま `main` から投げてください。

> [!NOTE]
> 1.x の `DBUtil.load` は繋がらなくても `false` を返すだけで、捨てるとサーバーが起動してしまいました。
> 2.0 で例外になりました（[2.0 への移行](./migrate-2)）。

> [!TRAP]
> **入口が複数あるなら、この並びを1か所にまとめてください。**
> Web・バッチ・スケジューラで別々に書くと、<b>どれか1つだけ古くなります</b>。
> テストからも同じものを呼びます（[落とし穴](./pitfalls) / [テスト](./testing)）。

終わるときは `DBUtil.stop()` です（Web は[グレースフルシャットダウン](./server)が呼びます。
バッチやテストでは自分で呼びます）。

## データソースが複数あるとき

```conf
db {
	main_db { main = true, ... }
	log_db  { ... }
}
```

`DBUtil.getDB("log_db")` で別のデータソースが取れます。
`main = true` のものは `DBUtil.getMainDB()` です（引数なしの `getDB()` はありません）。

トップレベルに並べたものは、**1本ごとに独立した扱い**を受けます。

- `conf/migration/<データソース名>/` のマイグレーションが当たります
- codegen が `db/<データソース名>/` を作ります
- `migration` / `db_lock` / `db_value` / `db_cache` もそちらに作られます

**設定を書き写さずにもう1本ぶら下げたい**だけなら、`subs` を使います。

```conf
db {
	main_db {
		main = true
		...
		subs {
			archive_db { }
		}
	}
}
```

`db.newSubDB("archive_db")` で取ります。

> [!TRAP]
> **接続もトランザクションも共有しません。**`subs` は親とは別のプールです。
> 同じデータベースを指していても別の接続なので、親のトランザクションの中で
> `newSubDB` に書いても**一緒には戻りません**。片方だけ残ります。
> 親から設定を引き継ぐ、それ以上のことはしていないと思ってください。

> [!TRAP]
> **`subs` にはマイグレーションも codegen も当たりません。**
> データソースごとの処理は、トップレベルの `db { }` しか回らないためです。
> だから `subs` に置けるのは「アプリが自分で作る作業用テーブル」までです。
> 表と型が要るもの（別 DB の監査ログなど）は、**トップレベルにもう1本**書いてください。
> また `subs` が別のデータベースを指すなら、**そのデータベースが先に在ること**。
> `subs` は親のデータソースを作る途中で繋ぎにいくので、あとに書いた
> トップレベルの `create_database_sql` は間に合いません
> （`failed create subs datasource` で起動が止まります）。

2本立てと `subs` を両方書いたものが `examples/approval-data` にあります。

## テーブル定義を生成する

```bash
./gradlew codegen
```

DB のスキーマから、テーブルごとのクラスを生成します。

```java
Post.id       // Column
Post.title
Post.instance()   // Table
```

`io.jimble.db` プラグインを入れると、`migrate → codegen → compileJava` が繋がります。
DDL を書いて起動すれば、コードのほうが追いつきます。

```kotlin
plugins {
	id("io.jimble.db")
}
```

生成されたクラスは**リポジトリに入れてください**。
生成物をコミットしないと、DB が無い環境でビルドできなくなります。

## 引く

```java snippet=select-nesting
```

**`select` は `Optional<Data>` を返します。**1件も無ければ空です
（上の `.orElseThrow()` は「必ずある」と決めて取り出す書き方です）。
読めなかったときは空ではなく例外です（下の「エラーの見方」）。

**SELECT の結果はテーブル名でネストします。**
`post` と `comment` を join したとき、両方に `id` があっても衝突しません。

`Column` で引けば、文字列のキーがコードに出てきません。
`row.getData(Post.instance())` で、1テーブルぶんを**平らにして**取り出せます。

> [!TRAP]
> **`extractTableData` は平らにしません。**`{post: {...}}` を<b>ネストしたまま</b>返すので、
> そのまま JSON にすると**入れ子が1段残ります**。
> 平らにしたいときは `getData(テーブル)` か `flattenTable(テーブル)` です。

> [!TRAP]
> **文字列の SQL（`db.select("SELECT ...")`）の結果はネストしません。**ネストはビルダーが列に
> `post__title` の別名を付けるから起きるので、自分で書いた SQL は平らな Data になります。
> 結合すると**同じ名前の列（`id` など）はあとの値で黙って上書き**されるので、別名を付けてください。

### JSON の列

MySQL の `JSON`、PostgreSQL の `json` / `jsonb` の列は、**読んだ時点で `Data`（オブジェクト）か `List`（配列）になります。**

```java
row.getStringList("tags");      // ["a", "b"]  配列の列
row.getData("options");         // オブジェクトの列
row.getString("tags");          // "a"  ← 配列の先頭の要素だけ。JSON の文字ではない
```

**配列の列を `getString` すると先頭の要素だけが返ります**（そうしたときは1度だけ WARN が出ます）。
JSON の文字のまま欲しいなら、SQL で文字にして読んでください——MySQL は `CAST(列 AS CHAR)`、PostgreSQL は `列::text` です。

## エラーの見方

```java snippet=db-error
```

**DB の失敗は例外で返ります。**SQL が通らなかった・繋がらなかったときは `SqlExecuteException`（非検査）です。
受け止めなければ上まで飛んで 500 になり、トランザクションの中なら巻き戻ります。
コードは `getCode()`（`DB_999` など）、元の JDBC の例外は `getCause().getCause()` にあります。

**0件は失敗ではありません。**空の `Optional`・空のリスト・件数 0 で返ります。

| メソッド | 返すもの | 0件のとき |
| --- | --- | --- |
| `select` / `selectCached` | `Optional<Data>` | 空の `Optional` |
| `selectList` / `selectListCached` / `selectListPerformance` | `List<Data>`（`null` は返しません） | 空リスト |
| `insert` | なし（`void`） | —— |
| `insertKey` | 採番された値。採番されなければ `SqlExecuteException` | —— |
| `update` / `delete` | 当たった件数（`int`） | `0` |
| `execute` | 当たった件数（`int`）。DDL と結果セットを返す文は `0` | `0` |
| `executeBatch` / `insertBatch` | 文ごとの件数 / 採番値の一覧 | 空の入力は空リスト |

`executeBatch` / `insertBatch` は**SQL が全部同じでなければなりません。**違うものが混ざっていれば `DB_998` の例外です。

### 「1件も無かった」と「読めなかった」

**別のものとして返ります。**空の `Optional` は「1件も無かった」だけで、読めなかったときは例外です。

```java
Optional<Data> user = db.select(sql, id);      // 読めなければ、ここで SqlExecuteException
if (user.isEmpty()) { return 誰でもない; }      // 空は「1件も無かった」だけ

Data post = db.select(sql, id)
	.orElseThrow(() -> new HttpException(404, "記事がありません"));
```

DB が落ちた日に「そんな利用者はいません」と答えることはありません。

> [!NOTE]
> 1.x の `select` は、0件も失敗も `null` でした（`isError()` を見ないと見分けられませんでした）。
> 2.0 で分かれました。`selectOrThrow` / `selectListOrThrow` は `select` / `selectList` と同じ意味になったので、
> 非推奨です（2.x で消します）。

### 一意制約だけを受け止める

分岐したい失敗は、たいてい一意制約だけです。**それは型で分かれます。**
一意制約に当たると `DuplicateKeyException`（`SqlExecuteException` の子）が飛びます（`insert` / `insertKey` / `update` など、どれでも）。

```java
try {
	long id = db.insertKey(SQL.insert(User.instance()).value(User.email, email));
} catch (DuplicateKeyException e) {
	return 「そのメールアドレスは使われています」;
}
```

> [!TRAP]
> **トランザクションの中で受け止めて続けると、その Tx は確定できません。**
> `tx.commit()` が `TransactionException`（`DB_004`）で断り、全部巻き戻します（[トランザクション](./transaction)）。
> 「あれば更新」に使うなら、トランザクションの外で受け止めるか、
> SQL で書いてください（MySQL は `INSERT ... ON DUPLICATE KEY UPDATE`、PostgreSQL は `INSERT ... ON CONFLICT`）。

### 採番値と件数

**`insert` は何も返しません。**採番値が要るなら `insertKey`、件数が要る `INSERT ... SELECT` などは `execute` です。

```java
db.insert(SQL.insert(Tag.instance()).value(Tag.name, name));             // 入れるだけ
long id = db.insertKey(SQL.insert(Post.instance()).value(...));          // 採番値
int count = db.execute("INSERT INTO archive SELECT * FROM post WHERE ...");  // 件数
```

> [!NOTE]
> 1.x の `insert` は、採番値が取れればその値、取れなければ入った件数を返していました
> （採番列を1本足しただけで意味が変わりました）。2.0 で分けました。
> `insertNoReturnKey` は非推奨です（2.x で消します）。`insert` か `execute` を使ってください。


## マイグレーション

`conf/migration/<スキーマ名>/001_xxx.sql` に置きます。

```sql
# --- !Ups

CREATE TABLE `note` (
	`id`         BIGINT UNSIGNED AUTO_INCREMENT COMMENT 'ID' PRIMARY KEY,
	`title`      VARCHAR(200)  NOT NULL COMMENT 'タイトル',
	`created_at` DATETIME      NOT NULL COMMENT '作成日時'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='メモ';

# --- !Downs

DROP TABLE `note`;
```

```bash
./gradlew migrate
```

適用済みのものは記録されるので、二度は走りません。
複数のサーバーが同時に起動しても、ロックを取るので1つしか流れません。

いつ何が走るか、失敗したときにどうなるか、生成される型の対応は
[マイグレーションとコード生成](./codegen) にまとめてあります。
