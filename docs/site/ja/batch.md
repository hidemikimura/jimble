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
