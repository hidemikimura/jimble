---
title: キャッシュとロック
summary: DB / メモリ / Redis の差し替え、SQL 結果の自動無効化、分散ロック
section: データベース
order: 5
---

# キャッシュとロック

## 3つの実装から選ぶ

```conf
cache {
	type = "db"        # db | memory | redis
}
```

| | 置き場所 | プロセスをまたぐ | 期限 |
| --- | --- | --- | --- |
| `db`（**既定**） | `db_cache` テーブル | **またぐ** | **無い** |
| `memory` | JVM のメモリ | またがない | `cache.memory.expire`（秒） |
| `redis` | Redis | **またぐ** | **無い** |

知らない値を書いても落ちません。**警告を出して `db` になります。**
いま何で動いているかは起動ログに出ます（`cache=db`）。

> [!WARN]
> **`db` と `redis` には期限がありません。**古いものを消す仕組みも入っていません。
> 入れっぱなしにすると `db_cache` は増え続けます。
> **消すのは入れた側の仕事です**（`remove` / `removeGroup`）。

## 読み書き

```java
ICache cache = Cache.instance(db);

cache.set("top:posts", json, "application/json");
String json = cache.getString("top:posts");

cache.remove("top:posts");
```

| メソッド | 返り |
| --- | --- |
| `getString(key)` | 文字列。無ければ `null`（メモリ実装だけ `""`） |
| `get(key)` | `CacheData`（作成日時・種類つき）。**`isError()` を見る** |
| `set(key, value, contentType[, group])` | 入ったら `true` |
| `has(key, group)` / `remove(key)` / `removeGroup(group)` | |

> [!NOTE]
> **256KB を超える値はファイルに落ちます**（gzip、`cache.temp_dir`）。
> `CacheData#hasContentFile()` が true になり、そのまま
> `context.response().cache(data)` で `Content-Encoding: gzip` として返せます。
> 古い世代のファイルを消す仕組みは無いので、置き場は定期的に見てください。

## グループでまとめて消す

「記事を1本直したら、その記事に関わるキャッシュを全部捨てたい」ときに使います。

```java snippet=cache-group
```

jimble 自身もこれを使っています。スケジューラは台ごとの生存情報を同じグループに入れ、
`getStringGroup(group)` で**全台ぶんをまとめて読み出します。**

## SQL の結果をキャッシュする

`ICache` は「入れた側が消す」ものでした。
**SQL 結果キャッシュは、更新を見て自分で消えます。**

**既定では無効です。** 使うアプリだけ 1 行書いてください。

```conf
sql_cache.enabled = true
```

```java
Data customer = db.selectCached(
	SQL.select()
		.from(Customer.instance())
		.inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
		.where(Customer.id.eq(1)));
```

```java
// 更新は今までどおり書くだけ。関係するキャッシュだけが消えます
db.update(SQL.update(Customer.instance()).set(Customer.name, "name1").where(Customer.id.eq(1)));
```

| 更新 | 上のキャッシュは |
| --- | --- |
| `UPDATE customer SET name = 'name1' WHERE id = 1` | **消える** |
| `UPDATE customer SET name = 'name2' WHERE id = 2` | 消えない |
| `UPDATE shop SET name = 'shop1' WHERE id = 1` | **消える**（結合先） |
| `UPDATE shop SET name = 'shop2' WHERE id = 2` | 消えない |

**読むほうは明示、消すほうは自動です。**
逆にすると、消し忘れが黙って古いデータになります。

### どう判断しているか

キャッシュ 1 件に**依存タグ**を付けます。

| タグ | 意味 | 何で消えるか |
| --- | --- | --- |
| `customer#id#1` | その行に依存している | その行を書き換えたとき |
| `customer#*` | 行の集合に依存している（一覧） | INSERT / DELETE / 絞れない UPDATE |
| `customer#@` | そのテーブルを読んでいる | **どの行に当たるか読めない**更新 |

タグは 2 つの材料から作ります。

- **SELECT の結果行**から、テーブルごとに**主キーと一意キー**の値を拾う。
  結果はテーブル名でネストしている（[Data のネスト](./db)）ので、**結合先のキーもそのまま拾えます**
- **UPDATE / DELETE の WHERE** から、主キー・一意キーの等値と `IN` を読む。
  読めたらその行タグだけ、読めなければテーブルごと消します

一意キーは codegen が `SHOW INDEX` から拾って生成します。`codegen` を流し直してください。

### 一覧のキャッシュ

行を 1 つに特定していない SELECT には、`customer#*` も付きます。

```java
// shop_id で絞った一覧。顧客が増えたら結果が変わる
db.selectListCached(SQL.select().from(Customer.instance()).where(Customer.shop_id.eq(1)));
```

「特定している」と見なすのは次の場合です。

- WHERE が主キー・一意キーの全列を**値で**縛っている（`WHERE id = 1`、`WHERE id IN (1, 2)`）
- 結合条件が、**すでに特定できているテーブルの列**とキーの全列を縛っている
  （`ON (customer.shop_id = shop.id)` で customer が特定できていれば、shop も 1 行に決まる）

> [!trap]
> **`LIMIT` を付けた SELECT は「特定している」になりません。**
> `ORDER BY name LIMIT 1` は、別の行の名前が変わるだけで返る行が入れ替わります。
> `IN (サブクエリ)` や `= 他テーブルの列` も同じ扱いです。

> [!note]
> **キーの列を SELECT していないとテーブルタグに倒れます。**
> `SELECT id, name` のように列を絞ると、一意キーの `code` で絞った更新を取りこぼすので、
> 安全側（そのテーブルの更新で全部消す）にします。`SELECT *` なら起きません。

### トランザクション

**トランザクションの中では読みも書きもしません。**
まだ確定していない値をキャッシュに残さないためです。
消すほうは溜めておいて、**コミットでまとめて**消します。ロールバックしたら消しません。

### 効かないところ

| | どうなるか |
| --- | --- |
| 生 SQL の更新（`db.execute("UPDATE ...")`） | どこに当たるか読めないので**全部消します** |
| `ON DUPLICATE KEY UPDATE` / `INSERT ... SELECT` | 既存の行が変わりうるので、そのテーブルに触るものを全部消します |
| 別のプロセスから DB を直接書き換えた | 追えません。`sql_cache.ttl`（既定 300 秒）が保険です |
| `sql_cache.store = "memory"` | **その台の中だけ**です。複数台なら `redis` か `db` にしてください |

### 設定

```conf
sql_cache {
	enabled = true       # 既定は false
	store   = "memory"   # memory | redis | db
	ttl     = 300        # 秒。0 で無期限
	max     = 10000      # memory のときだけ。持つ件数の上限
}
```

**既定が `false` なのは、使っていないアプリに代金を払わせないため**です。
有効だと、`selectCached` を 1 度も呼んでいなくても、
すべての `insert` / `update` / `delete` で「どの行に当たるか」を組み立てます
（WHERE を読み、テーブルのキーを引く）。
`false` のあいだは**その処理が 1 行も走りません**。

書き忘れても気づけるようにしてあります。

- 起動時の構成ログに `sql_cache=off` と出ます
- 切ったまま `selectCached` を呼ぶと、**初回だけ**警告が出ます（毎回は出しません）

```
jimble 構成: env=local / session=none / cache=db / sql_cache=off / redis=なし / ...
```

> [!note]
> 切っているときの `selectCached` は、**ただの `selectList` として動きます**。
> 例外にはしません。本番で調べるためにキャッシュを切ったらアプリごと止まる、
> というのは行きすぎだからです。

## ローディングキャッシュ

「無ければ作る」を1か所に書く形です。

```java
LoadingCache<List<Data>> cache = Cache.loadingCache(
	"top:posts", db, Duration.ofMinutes(5), loaderDb -> loaderDb.selectList(...));

List<Data> posts = cache.get();
```

| | |
| --- | --- |
| 値の置き場 | **その JVM のメモリ**（共有キャッシュには「作った印」だけ置く） |
| 期限 | `Duration` を渡さなければ**無期限** |
| 他の台が `clear()` したとき | **1分に1回**だけ共有キャッシュを見に行って捨てる |
| ローダーに渡る `DB` | 新しい接続（呼び出し元のトランザクションとは別） |

> [!TRAP]
> **ローダーが `null` を返しても「読み込み済み」になります。**
> 期限が来るまで `null` を返し続けるので、
> 「たまたま無かった」を覚え込ませたくないなら、空のリストなど**値で表してください。**

## 分散ロック

**Redis を使います。**「複数台のうち1台だけに処理させたい」ときのものです。

```java snippet=redis-lock
```

```java
// 100ms 待って取れなければあきらめる。取れたら 30 秒保持する
try (RedisLockResult lock = RedisLock.tryLock("batch:daily", 100, 30000)) {

	if (lock.status() != RedisLockStatus.Success) {
		return;   // 誰かが動かしている
	}

	// ここが1台だけになる

} catch (IOException ignore) {
}
```

> [!WARN]
> **取れなくても例外は飛びません。**`status()` を必ず見てください。
> 見ないと「ロックしていないのに処理が進む」形になります。

> [!TRAP]
> **同じスレッドからは同じキーを取れてしまいます**（再入できるロックのため）。
> 「もう誰かが持っているはず」を同じスレッドで確かめると、素通りします。

> [!TRAP]
> **`tryLock` の保持時間は延長されません。**30 秒と書いて処理が 40 秒かかると、
> **途中で解放されて2台目が入ってきます。**
> 引数なしの `RedisLock.lock(key)` は、持っている間ずっと延長されます（そのかわり待ち続けます）。

Redis を設定していないときは、**黙って成功させずに例外**になります（要件 F-U-10）。
「ロックが無いのに進んでしまった」を作らないためです。

### DB だけでロックする

Redis が無い場所では `DBLock` を使えます。**トランザクションの中で**、
先に行を作ってから取ります。

```java
DBLock.create(db, "daily");           // 1回だけ
...
if (DBLock.lock(db, "daily")) {       // SELECT ... FOR UPDATE
	// トランザクションが終わるまで1つだけ
}
```

行が無ければ `false` です。解放は**トランザクションの終わり**で、明示的に外す口はありません。

## 設定キー

```conf
cache {
	type          = "db"     # db | memory | redis
	temp_dir      = ""       # 大きい値を落とす場所。空なら一時ディレクトリ
	memory.expire = 0        # memory のときだけ。秒。0 で無期限
}

redis {
	host = ""                # 空なら「Redis 無し」
	port = 6379
	ssl  = false
}
```

> [!NOTE]
> `redis.host` が空なら **Redis 無し**です。アプリは起動します。
> `cache.type = redis` にしていると起動時に警告が出て、使った時点で失敗します。
