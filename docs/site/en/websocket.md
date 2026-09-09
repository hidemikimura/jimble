---
title: WebSocket
summary: Two-way. One Context per message
section: Protocols
order: 2
---

# WebSocket

## Writing one

```java snippet=ws-handler
```

## Registering it

```java
ws("/ws/posts", PostFeedHandler::new);
```

You write it in the same place as your HTTP routes. It shows up in the startup log too, as `WS /ws/posts`.

What you pass is **a constructor reference, not an instance**.
One handler is created per connection.

## The order they are called in

| Method | When |
| --- | --- |
| `onUpgrade(WsSession)` | Before the upgrade from HTTP. Return `false` and you get a 403 |
| `onOpen(WsContext)` | Once the connection is up |
| `onMessage(WsContext, String)` | Text arrived |
| `onBinary(WsContext, byte[])` | Binary arrived |
| `onClose(WsContext, int, String)` | It closed |
| `onError(WsContext, Throwable)` | Something threw |

Every one of them has a `default` implementation, so you write only the ones you need.

## Authenticate in onUpgrade

**`onUpgrade` is the only point at which you can read a cookie.**
After the upgrade there are no HTTP headers any more.

```java
@Override
public boolean onUpgrade (WsSession session) {

	return "secret".equals(session.cookie("token"));

}
```

Return `false` and it is turned away with a 403.

## A Context per message

**One `Context` is created for each message.**
It is treated exactly like a request, or like a single batch execution.

So inside `onMessage` you use the DB and the session as normal.
When the message is done, the DB connection goes back, even though the connection stays up.

```java snippet=ws-on-message
```

Do not silently drop a command you do not recognise.
From the client side there is no way to tell "did it not arrive, or was the command wrong".

## Sending to other connections

You keep the connected sessions yourself.

```java
private static final Set<WsSession> SUBSCRIBERS = ConcurrentHashMap.newKeySet();

public static void notifyNewPost (String title) {

	Data event = new Data().putData("type", "new_post").putData("title", title);

	// send() returning false means that connection is already gone. Drop it
	SUBSCRIBERS.removeIf(session -> !session.send(event));

}
```

If you run more than one server, fanning out this way will not reach anyone connected
to a different one. Put something like Redis pub/sub in between. jimble does not go that far.

## SSE or WebSocket

If the client has nothing to send, [SSE](./sse) is the easier one.
No proxy configuration, and the browser handles the reconnecting for you.
