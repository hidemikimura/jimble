---
title: WebSocket
summary: 双方向。1メッセージごとに Context が作られる
section: プロトコル
order: 2
---

# WebSocket

## 書く

```java snippet=ws-handler
```

## 登録する

```java
ws("/ws/posts", PostFeedHandler::new);
```

HTTP のルートと同じ場所に書きます。起動ログにも `WS /ws/posts` と出ます。

渡すのは**インスタンスではなくコンストラクタ参照**です。
接続ごとにハンドラが作られます。

## 呼ばれる順番

| メソッド | いつ |
| --- | --- |
| `onUpgrade(WsSession)` | HTTP から昇格する前。`false` を返すと 403 |
| `onOpen(WsContext)` | 繋がったあと |
| `onMessage(WsContext, String)` | テキストが来たとき |
| `onBinary(WsContext, byte[])` | バイナリが来たとき |
| `onClose(WsContext, int, String)` | 閉じたとき |
| `onError(WsContext, Throwable)` | 落ちたとき |

すべて `default` 実装があるので、要るものだけ書けば済みます。

## 認証は onUpgrade で

**Cookie が読めるのは `onUpgrade` の時点だけです。**
昇格したあとは、もう HTTP のヘッダはありません。

```java
@Override
public boolean onUpgrade (WsSession session) {

	return "secret".equals(session.cookie("token"));

}
```

`false` を返すと 403 で断ります。

## メッセージごとに Context

**1メッセージにつき `Context` が1つ作られます。**
リクエストやバッチの1実行と同じ扱いです。

つまり `onMessage` の中では、DB もセッションも普通に使えます。
メッセージの処理が終われば、接続が続いていても DB 接続は返されます。

```java snippet=ws-on-message
```

知らないコマンドを黙って捨てないでください。
クライアント側からは「届いていないのか、コマンドが違うのか」が分かりません。

## 他の接続へ送る

繋がっているセッションを自分で持っておきます。

```java
private static final Set<WsSession> SUBSCRIBERS = ConcurrentHashMap.newKeySet();

public static void notifyNewPost (String title) {

	Data event = new Data().putData("type", "new_post").putData("title", title);

	// send() が false なら、その接続はもう無い。そのまま外す
	SUBSCRIBERS.removeIf(session -> !session.send(event));

}
```

サーバーが複数台あるなら、この配り方では他の台に繋がっている人へ届きません。
Redis の pub/sub などを間に入れてください。jimble はそこまでは持っていません。

## SSE との使い分け

クライアントから送るものが無いなら [SSE](./sse) のほうが簡単です。
プロキシの設定も要らず、繋ぎ直しもブラウザがやってくれます。
