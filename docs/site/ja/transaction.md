---
title: トランザクション
summary: try-with-resources で囲む。畳み忘れは実行の終わりに拾う
section: データベース
order: 3
---

# トランザクション

注釈はありません。**囲んだところがトランザクションです。**

```java snippet=transaction-mq
```

## 使い分け

| メソッド | 何をするか |
| --- | --- |
| `beginTransaction()` | 始める。他で始まっていれば何もしない |
| `commit()` | 確定する。**トランザクションは続く** |
| `commitEndTransaction()` | 確定して終わる |
| `rollback()` | 戻す。トランザクションは続く |
| `rollbackEndTransaction()` | 戻して終わる |
| `close()` | 開いたままなら戻す |

`commit()` と `commitEndTransaction()` の違いに注意してください。
`commit()` は「ここまでを確定して、まだ続ける」です。
移送元のコードでは `commit()` が中で終わらせていたため、
**そこから先が黙って自動コミットになる**という穴がありました。jimble では直っています。

## 畳み忘れ

try-with-resources を使わずに `beginTransaction()` して、途中で `return` すると、
ロールバックもされず接続もプールへ戻りません。

jimble は**実行（`Context`）の終わりに拾います**。

```
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックして閉じます: blog_example
```

黙って戻すと「入れたつもりが入っていない」が残るので、ERROR を出してからロールバックします。
このログが出たら、囲み忘れです。

**拾うのは `DBTransaction` を使ったときだけではありません。**
`db.beginTransaction()` を直に呼んだときも同じです
（コネクションを握っているのは `DB` のほうなので、そちらで見ています）。

## 閉じ忘れた `DB`

**ふつうの SQL は、`DB` を閉じ忘れても漏れません。**
1文ごとにコネクションをプールへ返しているので、`DBUtil.getMainDB()` を
使い捨てにする書き方でかまいません。

**握ったまま抜ける道は2つだけ**で、どちらも実行の終わりに拾います。

| 握るもの | いつ返るか |
| --- | --- |
| トランザクション中 | `commitEndTransaction()` / `rollbackEndTransaction()` / `close()` |
| カーソル（`selectListWithFetcher`） | `db.close()` |

```
ERROR 閉じられていない DB が残っていました。閉じます: blog_example（selectListWithFetcher はカーソルなので、close() までコネクションを返しません）
```

> [!TRAP]
> **拾うのは最後の砦です。**拾われた時点で ERROR が出ているので、
> <b>ログが出たら直す</b>ものだと思ってください。
> 実行が長いバッチでは、実行の終わりまで1本が握られたままになります。

## 短く書く

```java
DBTransaction.transaction(db, transaction -> {
	db.insert(...);
	db.update(...);
});
```

始めて、渡した処理を走らせて、`commitEndTransaction()` まで済ませます。
例外が出れば `close()` がロールバックします。

## 外へ出すものと、DB に積むもの

**どちらの中で呼ぶかが逆になります。**ここは間違えやすいところです。

| | どこで呼ぶか | なぜ |
| --- | --- | --- |
| **DB のキューへ積む**（[MQ](./mq) の `put()`） | **トランザクションの中** | 同じ DB なので、ロールバックすれば積んだものも消えます |
| **外へ出す**（SSE・メール送信・外部 API） | **コミットしたあと** | 戻せないので、先に流すと取り返しがつきません |

```java
try (DBTransaction transaction = new DBTransaction(db)) {

	transaction.beginTransaction();

	long id = db.insert(...);

	new NoticeExecutor().put(db, data);      // ← 中で積む（DB のキュー）

	transaction.commitEndTransaction();

}

PostFeedHandler.notifyNewPost(title);        // ← 外へ出すのはコミットしてから
```

外へ出すものを先に流すと、ロールバックしたときに
**「入っていない記事のお知らせ」だけが届きます。**

逆に、DB のキューをコミット後に積むと、**積む直前に落ちたぶんが黙って消えます**——
記事は入っているのに、お知らせは誰も知らない状態になります。

> [!TIP]
> **外へ出す処理は、キューの中でやるのがいちばん確実です。**
> トランザクションの中で `put()` して、実際の送信は
> [MQ](./mq) の `execute()` に書きます。こうすると、
> <b>「どちらの中で呼ぶか」を考えなくてよくなります。</b>
