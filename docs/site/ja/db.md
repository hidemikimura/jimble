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

		maximumPoolSize      = 10
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
		password = ${?DB_PASSWORD}
	}
}
```

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

	// 繋がらなければ false。ここで止めます
	if (!DBUtil.load(Conf.conf().config(), App.class)) {
		throw new IllegalStateException("DB を読み込めませんでした（このすぐ上のログに原因が出ています）");
	}

	JimbleServer.start(new App());

}
```

`DBUtil.load` の2つ目は**クラスパスの起点**です（マイグレーション SQL をここから探します）。
自分のアプリのクラスを渡してください。

> [!TRAP]
> **戻り値を捨てないでください。**`DBUtil.load` は繋がらなくても例外を投げません
> （原因をログに出して `false` を返します）。捨てると**サーバーは起動してしまい**、
> 最初にリクエストが来たところで落ちます。
> `DBUtil.getMainDB()` が「DB が読み込めていません」と言って落ちるので迷子にはなりませんが、
> **起動した時点で気づけるほうが早い**です。

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

**SELECT の結果はテーブル名でネストします。**
`post` と `comment` を join したとき、両方に `id` があっても衝突しません。

`Column` で引けば、文字列のキーがコードに出てきません。
`row.extractTableData(Post.instance())` で、1テーブルぶんだけ取り出せます。

## エラーの見方

```java snippet=db-error
```

DB のエラーは例外ではなく戻り値で返ります。
`select` 系は `null`、`insert` は `-1`、`update` / `delete` は `-1` です。
理由は `db.getError()` に入っています。

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
