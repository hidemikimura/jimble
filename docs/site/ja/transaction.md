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
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックします
```

黙って戻すと「入れたつもりが入っていない」が残るので、ERROR を出してからロールバックします。
このログが出たら、囲み忘れです。

## 短く書く

```java
DBTransaction.transaction(db, transaction -> {
	db.insert(...);
	db.update(...);
});
```

始めて、渡した処理を走らせて、`commitEndTransaction()` まで済ませます。
例外が出れば `close()` がロールバックします。

## MQ と混ぜるとき

キューに積むのは**コミットしてからです**。

```java
transaction.commitEndTransaction();

PostFeedHandler.notifyNewPost(title);
```

先に流すと、ロールバックしたときに「入っていない記事のお知らせ」だけが届きます。
逆に、DB のキュー（[MQ](./mq)）へ積むのは**トランザクションの中**です。
同じ DB なので、ロールバックすれば積んだものも消えます。
