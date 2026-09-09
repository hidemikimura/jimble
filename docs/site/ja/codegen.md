---
title: マイグレーションとコード生成
summary: SQL の当て方、生成されるクラス、いつ何が走るか
section: データベース
order: 6
---

# マイグレーションとコード生成

**スキーマが先、コードが後**です。SQL を書いて当てると、テーブル定義のクラスがそこから作られます。

| いつ | ローカル | それ以外（CI・staging・本番） |
| --- | --- | --- |
| ビルド時 | `migrate` → `codegen` → `compileJava` | **`compileJava` だけ** |
| 起動時 | 何もしない | **未適用のマイグレーションを当てる** |

> [!NOTE]
> **生成物はリポジトリにコミットします。**そうしないと、DB の無い環境でビルドできません。
> ずれていないかは CI で `codegenCheck` が見ます。

## マイグレーション

`conf/migration/<スキーマ名>/` に SQL を置きます。

```sql
# --- !Ups

CREATE TABLE `note` (
	`id`         BIGINT UNSIGNED AUTO_INCREMENT COMMENT 'ID' PRIMARY KEY,
	`title`      VARCHAR(200) NOT NULL COMMENT 'タイトル',
	`created_at` DATETIME     NOT NULL COMMENT '作成日時'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='メモ';

# --- !Downs

DROP TABLE `note`;
```

```bash
./gradlew migrate
```

- ファイル名は自由。**名前の自然順**に当たります（`001_` を付けるのが分かりやすい）
- `<スキーマ名>` は設定の `scheme`（無ければデータソース名）
- **クラスパスから探します**。`conf/` をリソースに足しておいてください（[設定](./config)）

### 1文ずつの切り分け

1つのファイルに複数の文を書けます。「;」で切りますが、
**文字列・識別子・コメントの中の「;」では切りません。**

どこからどこまでがそれなのかは**製品で違う**ので、繋いでいる製品の決まりで読みます。

| 書き方 | MySQL | PostgreSQL |
|---|---|---|
| `'a\'b'` の `\` | エスケープ | **ただの文字**（`E'a\'b'` のときだけエスケープ） |
| `# ...` | 行コメント | コメントではない |
| `1--2` | コメントではない（`--` のあとに空白が要る） | 行コメント |
| `` `a` `` | 識別子の囲み | 囲みではない |
| `$$ ... $$` | ただの文字 | 文字列（関数の本体や `DO` ブロック） |
| `/* /* */ */` | 最初の `*/` で閉じる | 入れ子にできる |

> [!TRAP]
> **製品の決まりを取り違えると、切り分けが黙って変わります。**
> `insert into t values ('c:\');` を MySQL の読み方で読むと、
> `\'` がエスケープに見えて**文字列が閉じません**。
> そこから先の SQL が全部この1文にくっつきます。

コメントだけになった断片は捨てます（MySQL では「Query was empty」で落ちるためです）。
ただし MySQL / MariaDB の `/*!40101 ... */` `/*M!100301 ... */` は
**実行されるコメント**なので捨てません。

> [!WARN]
> **1文も取り出せなかったら失敗にします。**「全部コメントだった」を成功として通すと、
> down のときに<b>テーブルは残ったまま履歴だけ消える</b>ことになります。

### 記録と失敗

| テーブル | 中身 |
| --- | --- |
| `migration` | 当てた SQL、ハッシュ、状態（`complete` / `up_error` / `down_error`） |
| `migration_history` | **1文ごとの実行履歴**（種類・SQL・成否・日時） |

失敗すると**そこで止まり、アプリは起動しません。**中途半端な状態でリクエストを受けないためです。
失敗したことは `migration` に `up_error` として残り、次回は down → up をやり直します。

> [!WARN]
> **DDL はロールバックされません。**MariaDB / MySQL は `CREATE TABLE` などで暗黙にコミットします。
> 1ファイルに複数の DDL を書くと、**途中まで当たった状態**で止まります。

### 当て終わった SQL を書き換えない

ハッシュを見ているので、**当たったあとに書き換えると失敗します。**

```
適用済みのマイグレーションが書き換えられています: 003_xxx.sql
（down が無効なため巻き戻せません。新しい SQL ファイルを追加してください）
```

`migration.down = true`（既定は `false`）にすると、down → 新しい up でやり直します。
**手で戻すコマンドはありません。**down が動くのは「ファイルが消えた」「ハッシュが変わった」の2つだけです。

### 製品ごとに SQL を分ける

MySQL と PostgreSQL で同じ DDL は書けません（`AUTO_INCREMENT` と `bigserial`、
`ENGINE=InnoDB`、列コメントの書き方）。**ファイル名の接尾辞**で分けます。

```
conf/migration/blog_example/
	001_create_post.mysql.sql        ← mysql / mariadb のときだけ
	001_create_post.postgresql.sql   ← postgresql のときだけ
	002_add_status.sql               ← 接尾辞なし = どちらでも
```

- 接尾辞は `db.<名前>.product` に書ける名前と同じものです（`mariadb` は `mysql` と同じ扱い）
- **接尾辞の無いファイルはどの製品でも当たります。**両方で通る SQL は分けなくていいです
- 飛ばしたファイルは起動ログに出ます

```
マイグレーション: 他の製品向けを飛ばしました（いまは postgresql）: 001_create_post.mysql.sql
```

適用済みは**ファイル名で**覚えているので、`001_create_post.mysql.sql` と
`001_create_post.postgresql.sql` は別の記録になります。
**置いてあるファイルは、他の製品向けでも「消えた」とは見ません**（down しません）。

同じ版に、いまの製品で流れるファイルを2つ置くと落ちます
（`001_x.mariadb.sql` と `001_x.mysql.sql` はどちらも MySQL で流れます）。

> [!TRAP]
> **すでに当てたファイルの名前を変えると、別のファイルになります。**
> `001_create_post.sql` を `001_create_post.mysql.sql` に変えると、
> 同じ SQL がもう一度流れて `Table 'post' already exists` になります。
>
> 名前でそれを見分けて止め、直し方を出します（**製品の接尾辞を落とした名前**で見るので、
> 分けた先で中身が変わっていても分かります）。
>
> ```
> マイグレーション SQL の名前が変わったようです（いまは mysql）:
>   001_create_post.sql → 001_create_post.mysql.sql
> 履歴の名前も変えてください。
>   UPDATE migration SET name = '001_create_post.mysql.sql' WHERE name = '001_create_post.sql';
>   UPDATE migration_history SET name = '001_create_post.mysql.sql' WHERE name = '001_create_post.sql';
> ```
>
> **中身も変わっているときは `hash` も一緒に出ます**（その版はすでに当たっているので、
> 当て直すのではなく記録を合わせます）。
> 出てくる SQL は**その DB の製品のもの**です。もう片方の製品の DB では、
> その製品向けのファイル名に読み替えてください。

> [!WARN]
> **いまの製品向けのファイルが無くなると止まります。**
> `001_create_post.sql` を `001_create_post.postgresql.sql` にだけ変えると、
> MySQL の DB では二度と読まれないファイルになります。
> 黙って down せず、`mysql` 向けを置くか履歴を消すかを選ばせます。

> [!WARN]
> **`ALTER TABLE` で足した列の位置は製品で変わります。**
> MySQL は `AFTER body` と書けますが、PostgreSQL は必ず末尾です。
> 生成されるクラスの列の並びは**物理順**なので、
> 同じスキーマでも製品によって並びが変わります（型も NULL 可否もコメントも同じです）。
> **生成は主にする製品で行ってください。**`codegenCheck` も同じ製品で回します。

### 複数のサーバーが同時に起動しても

`db_lock` の行を `FOR UPDATE` で取ってから当てるので、**1台しか流れません。**
待ちきれなかった側は起動に失敗します（`migration.lock_timeout_seconds`、既定 60 秒）。

> [!NOTE]
> ロック待ちのタイムアウトは**そのセッションだけ**に設定します（`SET SESSION`）。
> サーバー全体の設定は変えません。

### Java で書くマイグレーション

SQL では書けない移行（既存データの詰め替えなど）はクラスで書きます。

```java
CodeMigration.add(new V20260901FillUserKana());
Migration.install();
```

`versionYyyyMmDd()` の順に1回だけ走り、結果は `migration_code` に残ります。
**クラスパスは走査しません**（原則2）。書いたら `add` してください。

> [!TRAP]
> **CLI の `migrate` はコードマイグレーションを走らせません。**
> 登録がアプリのコードにあるためで、動くのは**アプリの起動時**だけです。

## コード生成

```bash
./gradlew codegen
```

DB のスキーマから、データソースごとに3種類を作ります。

| 生成物 | 置き場所 | 何に使うか |
| --- | --- | --- |
| スキーマクラス | `db/<データソース>/BlogExample.java` | `BlogExample.db()`、テーブル一覧 |
| テーブルクラス | `db/<データソース>/table/post/Post.java` | `Post.id` などの `Column` |
| 型付きアクセサ | `db/<データソース>/table_data/post/AbstractPostData.java` | `data.title("...").published(true)` |

```java
public class Post extends Table {

	/* 記事ID */
	public static final Column id = new Column(instance(), "id", long.class, false, null, true);

	private static final List<Column> COLUMNS = List.of(id, title, body, published, created_at);
}
```

列の一覧は**生成時に確定**します（実行時にリフレクションしません）。

> [!WARN]
> **生成先のディレクトリは毎回まるごと作り直します。**
> `db/<データソース>/` の下に手で書いたファイルを置くと、**次の `codegen` で消えます。**
> 生成物の先頭にも印が入ります。

```java
/*
 * このファイルは jimble が作りました（codegen）。手で直さないでください。
 * 直しても次の codegen で消えます。
 */
```

### 型の対応

| DB の型 | Java |
| --- | --- |
| `bigint` | `long` |
| `tinyint` / `bool` | **`boolean`** |
| `int` / `smallint` / `mediumint` | `int` |
| `decimal` / `float` / `double` | `double` |
| `datetime` / `date` / `timestamp` | `java.util.Date` |
| `varchar` / `text` など | `String` |
| `json` | `Data` |
| そのほか（`blob` / `enum` など） | `String` |

PostgreSQL では次も同じ扱いになります。

| DB の型 | Java |
| --- | --- |
| `bigserial` | `long` |
| `serial` / `smallserial` | `int` |
| `numeric` / `real` / `double precision` | `double` |
| `boolean` | `boolean` |
| `jsonb` | `Data` |

> [!TRAP]
> **`tinyint` は桁数に関係なく `boolean`** です。`tinyint(4)` に 0/1 以外を入れているなら
> `smallint` にしてください。
> **`decimal` は `double`** になるので、金額の計算には向きません。

### PostgreSQL のとき

`db.xxx.product = postgresql` を書いておけば、**同じ `./gradlew codegen` が動きます**
（[DB を使う](./db)）。読み方だけが変わり、生成されるクラスの形は同じです。

いくつか、MySQL と揃わないところがあります。

| もの | どうなるか |
| --- | --- |
| `bigserial` / `serial` | 連番として読みます（`nextval(...)` の既定値は出ません） |
| **式インデックス** | `create unique index ... (lower(code))` のようなものは<b>一意キーに数えません</b>。列が取れないので、残った列だけで一意だと誤解しないためです |
| **部分インデックス** | `where` 付きのものも<b>一意キーに数えません</b>。条件に合う行の中でしか一意でないからです |
| 生成される `SchemaSQL` | その製品の DDL で書かれます（コメントは `comment on ...` として別に出ます） |

> [!NOTE]
> **カタログを引けなかったときは落とします。**黙って「テーブルが0件」として
> 生成物を消してしまうと、原因が分からなくなるためです。

### 生成しないテーブル

jimble が自分で作るテーブル（`migration` / `db_cache` / `session` / `batch_*` など）は自動で外れます。
**MQ のキュー表は名前をアプリが決める**ので、自分で書いてください。

```conf
codegen {
	package        = "db"
	exclude_tables = ["mq_blog", "mq_scheduler"]
}
```

> [!NOTE]
> ワイルドカードは使えません（`mq_*` とは書けません）。1つずつ並べてください。

## 設定

```conf
migration {
	on_startup            = "auto"   # auto | true | false。auto はローカル以外で当てる
	down                  = false    # 巻き戻すか
	lock_timeout_seconds  = 60
	resource_dir          = "migration"
}

codegen {
	package        = "db"
	exclude_tables = []
}
```

## CLI から

Gradle が使えないところ（CI・本番）では CLI で同じことができます。

```bash
java -cp app.jar io.jimble.db.cli.JimbleDbCli migrate
java -cp app.jar io.jimble.db.cli.JimbleDbCli codegen src/main/java
```

設定も SQL も**クラスパスから**探します。
Gradle のタスクは [Gradle プラグイン](./gradle) にまとめてあります。
