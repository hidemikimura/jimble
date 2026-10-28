---
name: jimble-batch-write
description: jimble でバッチのクラスを書くときに使う。AbstractBatch（cron・設定・引数）、BatchRegistry の登録と sync() の順番、中断（isCancelOrder）、大量の行を分けて確定するチャンク処理（AbstractChunkBatch・KeyPagingReader・データソースの差し替え）と、それぞれの落とし穴を含む。
---

# jimble のバッチを書く

全体の決まりと、ほかの skill への案内は `jimble-batch`。入口の `main` とスケジューラは `jimble-batch-run`。

```java
import io.jimble.batch.AbstractBatch;
import io.jimble.batch.AbstractChunkBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.BatchRegistry;
import io.jimble.batch.KeyPagingReader;
import io.jimble.db.DB;
import io.jimble.util.data.Data;

import java.util.Iterator;
import java.util.List;
```

## バッチを書く

```java
public class PostCleanupBatch extends AbstractBatch {

	@Override public String batchName () { return "記事の掃除"; }
	@Override public String cron ()      { return "15 3 * * *"; }   // 5フィールド。書けばスケジューラが拾う

	@Override
	public Data defaultBatchSettings () {
		return new Data().putData("days", 30);      // 設定の初期値
	}

	@Override
	public void execute (BatchArgs args) {

		long days = args.cliArgs().containsKey("days")   // 引数があればそちら
			? args.cliArgs().getLong("days")
			: settings().getLong("days");                // 無ければ DB に入れた設定

		while (!isCancelOrder()) {                   // 長い処理では必ず見る
			// 1件ずつ処理する
		}

	}

}
```

- **cron で回すのに要るのは `cron()` だけ**（`isEnableScheduler()` は既定で `true`）。
  **`isScheduler()` は `true` にしない**——スケジューラ自身の印で、`true` にすると `sync()` が飛ばし、マスタに行ができない（スケジューラから見えない）
- `settings()` は DB の設定（初期値は `defaultBatchSettings()`。管理画面から変えられる）
- `args.cliArgs()` に `key=value` で渡したものが入る（メソッド。フィールドではない）
- 失敗は例外で抜ければよい（SQL の失敗も `SqlExecuteException` で抜ける）。履歴は `error` になる
- **`settings()` / `args.cliArgs()` は人が入れた値。**`getLong("days")` は読めない値（`"abc"`）で `DataConversionException`、
  バッチが失敗になる（黙って 0 にはならない）。キーが無い・空なら 0 のままなので、0 を「未設定」として扱うなら自分で分ける

## 登録する。順番が効く

```java
BatchRegistry.add(PostCleanupBatch::new);
BatchRegistry.add(MqWorkerBatch::new);
BatchRegistry.sync(DBUtil.getMainDB());        // ← 登録が全部済んでから
```

> **`sync()` を登録より先に呼ぶと、全部の行が `status = nothing` になる。**
> 「1つも登録されていない」を「全部消えた」として扱うので、
> **スケジューラが何も回さなくなる。**例外も警告も出ない。
> **バッチを持たないアプリは `sync()` を呼ばない。**
>
> **一度 `nothing` になった行は、登録を直して `sync()` し直しても `enable` に戻らない。**
> `sync()` が既にある行で書き換えるのは `name` と `default_*` などだけで、`status` は触らない。
> 管理画面の有効・無効も `nothing` の行は変えられない。**SQL で `status = 'enable'` に戻す。**

> **コードの `cron()` / `allowConcurrentExecutionCount()` / `defaultBatchSettings()` が効くのは、最初に行を作ったときだけ。**
> あとで書き換えても変わるのは `default_cron` などの `default_*` 列で、動くときに見る
> `cron` / `allow_concurrent_execution` / `settings` は変わらない。
> `cron` と `settings` は管理画面で、同時実行数は SQL で変える。

> **`batch_master` を別のアプリと共有しない。**
> 消えた判定はクラス名でやるので、同じテーブルを見るアプリが2つあると
> **お互いの行を `nothing` にし合う。**

## 中断できるようにする

```java
while (!isCancelOrder()) { ... }
```

管理画面から中断を押すと `true` になる。
**見ていないバッチは止められない**（チャンクは自動）。
`AbstractBatch` の外（Domain 層など）からは `Context.current(BatchContext.class).isCancelOrdered()` で同じ指示が見られる。

## 大量に処理する（チャンク）

100万件を1トランザクションで抱えたくないときは `AbstractChunkBatch<T>`。
**読む → 加工する → まとめて書く**を `chunkSize()` 件ごとに確定する。

```java
public class RequestArchiveBatch extends AbstractChunkBatch<Data> {

	@Override public String batchName () { return "古い申請の書庫入れ"; }
	@Override public int chunkSize ()    { return 500; }

	@Override
	protected Iterator<Data> reader (BatchArgs args, DB db) {

		return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
			SELECT id, amount FROM request WHERE status = ? AND id > ? ORDER BY id ASC LIMIT ?
			""", "approved", lastKey, limit));

	}

	@Override
	protected Data process (Data item) {
		return item.getLong("amount") == 0 ? null : item;   // null なら書かない
	}

	@Override
	protected void write (List<Data> items, DB db) {
		for (Data item : items) {
			db.update("UPDATE request SET status = 'archived' WHERE id = ?", item.getLong("id"));
		}
	}

}
```

- **1回の `write()` が1トランザクション**。SQL の失敗は例外のまま抜けさせる
- **中断はかたまりの切れ目で見る。**`isCancelOrder()` を自分で呼ぶ必要はない
- `execute()` は `final`。触るのは `reader()` / `process()` / `write()` の3つだけ
- 途中で落ちたら**そのかたまりだけ戻して、バッチ全体を失敗にする**（黙って次へ進まない）
- **店の DB ごとに回すなら `db(BatchArgs args)` を上書きする**（2.7.8。既定は主）。`DBUtil.getDB(args.cliArgs().getString("data_source"))` のように引数で選び、
  **1 回の実行で1つのデータソース**（実行の記録・失敗・再開の位置がデータソースごとに分かれる）。実行の記録は主に書く

> **`reader()` の `db` と `write()` の `db` は別のインスタンス。渡されたものを使う（混ぜない）。**
> 同じ `DB` で読んでいると、**最初のかたまりを確定した時点で読みかけが死ぬ**
> （トランザクションを閉じるとコネクションがプールへ返るため）。

> **カーソル（`selectListWithFetcher`）ではなく `KeyPagingReader` を使う。**
> カーソルは開いているあいだコネクションを1本押さえ続ける。数時間のバッチでは効いてくる。
> キーは**一意で、`ORDER BY` と揃っていて、走っているあいだ書き換わらない**列にする。

> **`write()` やトランザクションの中で SQL の失敗を `catch` して続けない。**続けても確定は
> `TransactionException`（`DB_004`）で断られ、そのかたまりは巻き戻る。1件ずつ飛ばしたいなら、失敗しない形に先に弾く
> （`onDuplicateKeyUpdate` や存在確認）。

詳しくは <https://jimble.io/ja/batch.md>。
