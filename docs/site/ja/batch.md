---
title: バッチ
summary: cron で回る処理。中断指示、二重起動、管理画面
section: 実行基盤
order: 1
---

# バッチ

## 書く

```java snippet=batch-class
```

- `batchName()` は管理画面に出る名前です
- `isScheduler()` が `true` のものだけ、スケジューラが拾います
- `cron()` は5フィールドの cron 式です
- `settings()` は DB に入れた設定（`defaultBatchSettings()` が初期値）です

## 登録する

走査はしないので、自分で登録します。

```java
BatchRegistry.add(PostCleanupBatch::new);

// DB の一覧を、いま登録されているものに合わせる
BatchRegistry.sync(DBUtil.getMainDB());
```

`sync` は、**コードから消えたバッチの行を `status = nothing` にします**（行は残すので履歴からたどれます）。

> [!WARNING]
> **`sync` は登録を済ませてから呼んでください。**
> 「1つも登録されていない」は「全部消えた」として扱うので、
> 登録し忘れたまま呼ぶと**全部の行が `nothing` になります**（スケジューラが何も回さなくなります）。
> バッチを持たないアプリは呼ばないでください。
>
> **`batch_master` を別のアプリと共有しないでください。**
> 消えた判定はクラス名で行うので、同じテーブルを見るアプリが2つあると
> **お互いの行を `nothing` にし合います。**

## 中断できるようにする

```java
while (!isCancelOrder()) {
	// 1件ずつ処理する
}
```

管理画面から中断を押すと `isCancelOrder()` が `true` になります。
**長く回る処理では必ず見てください。** 見ていないバッチは止められません。

`AbstractBatch` を継承していないところ（呼び出した先の Domain 層など）からは、
コンテキスト越しに同じものが見られます。

```java
BatchContext context = Context.current(BatchContext.class);

for (Data row : rows) {
	if (context.isCancelOrdered()) { break; }
	// ...
}
```

**どちらも同じ指示を見ています。**DB を引くのは
`batch.cancel_check` に1回だけなので、ループの中で毎回呼んで構いません。

> [!TRAP]
> **0.6.0 まで `BatchContext.isCancelOrdered()` は常に false でした。**
> 中断の判定は `AbstractBatch` の側にあり、コンテキストには何も繋がっていませんでした。
> 例外も警告も出ないので、**本番で中断ボタンを押すまで分かりません**（1.0 で繋ぎました）。

## 一定件数ずつ読んでまとめて書く

100万件を1つのトランザクションで抱えたくないときは `AbstractChunkBatch` を継承します。
**読む → 加工する → まとめて書く**を繰り返し、`chunkSize()` 件ごとに確定します。

```java
public class RequestArchiveBatch extends AbstractChunkBatch<Data> {

	@Override public String batchName ()   { return "古い申請の書庫入れ"; }
	@Override public boolean isScheduler () { return true; }
	@Override public String cron ()        { return "0 4 * * *"; }
	@Override public int chunkSize ()      { return 500; }

	@Override
	protected Iterator<Data> reader (BatchArgs args, DB db) {

		// キー順に少しずつ読む
		return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
				SELECT id, amount FROM request
				WHERE status = ? AND id > ?
				ORDER BY id ASC
				LIMIT ?
			""", "approved", lastKey, limit));

	}

	@Override
	protected Data process (Data item) {

		// 書かないものは null を返す（既定は素通し）
		return item.getLong("amount") == 0 ? null : item;

	}

	@Override
	protected void write (List<Data> items, DB db) {

		for (Data item : items) {
			db.update("UPDATE request SET status = 'archived' WHERE id = ?", item.getLong("id"));
		}

	}

}
```

登録の仕方も、管理画面での見え方も、ふつうのバッチと同じです。

- **1回の `write()` が1トランザクション**です。抜けた時点で確定します
- **中断はかたまりの切れ目で見ます。**`isCancelOrder()` を自分で呼ぶ必要はありません
- `execute()` は `final` です。手を入れるのは `reader()` / `process()` / `write()` の3つだけです

### 途中で落ちたとき

**そのかたまりだけ戻して、バッチ全体を失敗にします。**黙って次へ進むことはしません。

どこまで確定したかは `batch_history.execute_info` に残ります。

| キー | 中身 |
|---|---|
| `chunk_read` | 読んだ件数 |
| `chunk_filtered` | `process()` が `null` を返してよけた件数 |
| `chunk_written` | 確定した件数 |
| `chunk_committed` | 確定したかたまりの数 |
| `chunk_failed_at` | 何かたまり目で落ちたか |
| `chunk_canceled` | 中断で抜けたか |

`chunk_written` は **`batch.progress`（既定5秒）ごとに書き換わる**ので、
走っている最中でも管理画面から「いま何件目か」が見えます。

### 読む DB と書く DB は別です

`reader()` に渡る `db` と `write()` に渡る `db` は**別のインスタンス**です。
**渡されたものをそのまま使ってください。**

`DBTransaction` を閉じるとコネクションがプールへ返るので、
同じ `DB` で読んでいると**最初のかたまりを確定した時点で読みかけが死にます。**

> [!TIP]
> **カーソル（`selectListWithFetcher`）ではなく `KeyPagingReader` を勧めます。**
> カーソルは開いているあいだ、ずっとコネクションを1本押さえます。
> 数時間かかるバッチではそれが効いてきます。
> ページングはページとページの間はコネクションを持ちません。

`KeyPagingReader` に渡すキーは、**一意で、`ORDER BY` と揃っていて、走っているあいだ書き換わらない**列にしてください。
キーが進まないまま満杯のページが返ってきたときは例外になります（黙って無限に回るより落ちたほうがよいためです）。

> [!WARNING]
> **`db.insert()` などは失敗しても例外を投げません。**
> `db.isError()` に**その直前の1文**の結果が入るだけです。
> `write()` の中で複数の文を流すなら、1文ごとに見るか、自分で例外を投げてください。

## 二重起動

同じバッチが同時に走る本数は `allowConcurrentExecutionCount()` で決まります。
既定は1本です。前の実行がまだ終わっていなければ、次はスキップされます。

サーバーが複数台でも、DB のテーブルで排他するので1本しか走りません。

## 単体で走らせる

バッチの入口は**自分で書きます**。起動の順番がそのファイルに全部出ます。

```java
public class BlogBatch {

	public static void main (String[] args) {

		DBUtil.load(Conf.conf().config(), BlogBatch.class);   // 1. DB
		BatchTables.install(DBUtil.getMainDB());              // 2. バッチのテーブル

		BatchRegistry.add(PostCleanupBatch::new);             // 3. 登録
		BatchRegistry.sync(DBUtil.getMainDB());

		BatchResult result = BatchExecutor.start(args);       // 4. 実行

		DBUtil.stop();
		System.exit(result.isExecuted() ? 0 : 1);

	}

}
```

```bash
java -cp app.jar blog.BlogBatch env=local class=blog.batch.PostCleanupBatch days=30
```

`key=value` の形で渡したものが `args.cliArgs` に入ります。

cron で回すなら `DbScheduler` を入口にした常駐プロセスを別に立てます。

## 管理画面

`jimble-batch-manager` を入れると、Web からバッチの一覧・実行・中断・履歴が見られます。

```kotlin
implementation("io.jimble:jimble-batch-manager:<版>")
```

```java
install(BatchManagerController::new);
```

**認証は自分で付けてください。** `before` に置くだけです。
何も付けないと誰でもバッチを起動できます。
