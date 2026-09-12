---
title: SSE
summary: 進捗を流す。切断は検出できないので、寿命で区切る
section: プロトコル
order: 1
---

# SSE（Server-Sent Events）

サーバーから一方向にイベントを流します。WebSocket と違って、ただの HTTP です。
プロキシもブラウザもそのまま通ります。

```java snippet=sse-basic
```

クライアント側は `EventSource` です。

```javascript
const es = new EventSource("/events");
es.addEventListener("progress", e => console.log(JSON.parse(e.data)));
es.addEventListener("done", () => es.close());
```

## 進捗を送る

```java snippet=sse-route
```

`sse()` を呼んだ時点で、次のヘッダが付きます。

```
Content-Type: text/event-stream
Cache-Control: no-store
X-Accel-Buffering: no
```

`X-Accel-Buffering: no` は nginx 対策です。これが無いと nginx が溜め込んでしまい、
「全部終わってから一気に届く」になります。

## 切断は分かりません

**ここが SSE のいちばん大事なところです。**

ブラウザを閉じても、helidon は切断を教えてくれません。
書き込みは成功し続けるか、あるいは**永久にブロックします**（Java にはソケットの
書き込みタイムアウトがありません）。実際に試したところ、4.8MB / 30秒 書けてしまうことも、
8秒で完全に固まることもありました。

なので jimble の `SseStream` は、**切断を待つのではなく寿命で区切ります**。

```conf
sse {
	max_duration = 5m   # 既定 5分
	max_events           = 0     # 0 = 無制限
	retry         = 3s  # クライアントの再接続間隔
}
```

```java
// このルートだけ変える
try (SseStream sse = context.response().sse(Duration.ofSeconds(30), 100)) {
```

`sse.isOpen()` は「まだ寿命が残っているか」を返します。
**「クライアントが繋がっているか」ではありません。** ループの中で必ず見てください。

```java
for (Data row : rows) {

	if (!sse.isOpen()) {
		break;
	}

	sse.send("progress", ...);

}
```

## 繋ぎ直し

`EventSource` は切れたら自動で繋ぎ直します。`retry` がその間隔です。

**つまり、寿命で切れても勝手に繋ぎ直されます。**
無限に続くストリームを作るのではなく、「5分ぶん流して切る、また繋がる」でよいです。

やり直しの位置を伝えたいときは、イベントに `id` を付けてください。
繋ぎ直しのときに `Last-Event-ID` ヘッダで返ってきます。

## 使いどころ

| 用途 | 向いているか |
| --- | --- |
| 進捗バー、ログの流し込み | 向いている |
| 通知の配信 | 向いている |
| 双方向のやりとり | [WebSocket](./websocket) へ |
| 大量の同時接続を長時間 | 接続数ぶんスレッドを使う。仮想スレッドなので軽いが、寿命は短くする |
