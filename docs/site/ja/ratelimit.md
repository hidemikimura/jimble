---
title: レートリミット
summary: ルートに宣言する。トークンバケット、メモリ / Redis / DB
section: 基本
order: 9
---

# レートリミット

**ルートに宣言します。**書いたところにだけかかります。

```java
// 1本だけ
post("/api/login", handler)
	.attribute(RateLimit.KEY, RateLimit.perIp(5, Duration.ofMinutes(1)));

// このブロック全部
path("/api", () -> {
	rateLimit(RateLimit.perIp(60, Duration.ofMinutes(1)));

	get("/items", handler);
	get("/items/{id}", handler);
});
```

`before` と同じで**書いたブロックに付きます**（パスのノードではありません）。
内側に別のものを書けば内側が勝ち、ルートが属性で持っていればそれが勝ちます。

## 数え方

**トークンバケット**です。`duration` かけて `limit` 個まで戻ります。
**短いバーストは通り、続けて叩くと止まります。**

> [!NOTE]
> 固定の窓（「1分あたり N 回」）にしなかったのは、
> 窓の境目で**一瞬 2N 回通ってしまう**ためです。

## 数える単位

```java
RateLimit.perIp(60, Duration.ofMinutes(1));                       // 送信元ごと
RateLimit.of(context -> context.session().id(), 10, Duration.ofMinutes(1));   // ログインごと
```

> [!TRAP]
> **`perIp` は `server.trust_proxy` を見ます。**プロキシの後ろに置いているのに
> `false` のままだと、**全員がロードバランサの IP として1つに数えられます**
> （[サーバー設定](./server)）。

社内からのアクセスなど、数えたくないものは外せます。

```java
RateLimit.perIp(60, Duration.ofMinutes(1))
	.exclude(context -> context.request().proxyAddress().startsWith("10."));
```

## 止めたときに返るもの

| | |
| --- | --- |
| ステータス | **429** |
| `Retry-After` | 待つ秒数（切り上げ。最低 1） |
| `X-RateLimit-Limit` / `X-RateLimit-Remaining` | 上限と残り |

**`before` より前で止めます。**認証や DB を触らせないためです。

## 置き場

```conf
rate_limit {
	store   = "memory"   # memory | redis | db
	enabled = true
}
```

| | 台をまたぐか | 向き |
| --- | --- | --- |
| `memory`（既定） | **またがない** | 1台のとき |
| `redis` | またぐ | 複数台に並べるとき |
| `db` | またぐ | Redis が無くて、**回数が少ない**とき |

> [!WARN]
> **`memory` のまま複数台に並べると、全体では台数ぶん通ります。**
> 3台なら 3 倍です。並べるなら `redis` にしてください。

> [!NOTE]
> `redis` は Lua で数えます。「残り」と「最後に足した時刻」を
> **一緒に読み書きしないと数え落ちる**ためです。
> `db` は `SELECT ... FOR UPDATE` なので、**高い頻度には向きません**
> （ログインの試行回数のような用途向け）。テーブルは初めて使うときに作ります。

> [!TRAP]
> **数えられなかったときは通します。**Redis が落ちているだけで
> サイト全体が 429 になるほうが困るからです。
> ただし**黙っては通しません**（`流量制限を数えられませんでした（通します）` がログに出ます）。
