---
title: SSE
summary: Stream progress. You cannot detect a disconnect, so bound it by lifetime
section: Protocols
order: 1
---

# SSE (Server-Sent Events)

Events flow one way, out of the server. Unlike WebSocket, this is plain HTTP.
Proxies and browsers pass it through untouched.

```java snippet=sse-basic
```

On the client side it is `EventSource`.

```javascript
const es = new EventSource("/events");
es.addEventListener("progress", e => console.log(JSON.parse(e.data)));
es.addEventListener("done", () => es.close());
```

## Sending progress

```java snippet=sse-route
```

The moment you call `sse()`, these headers go on.

```
Content-Type: text/event-stream
Cache-Control: no-store
X-Accel-Buffering: no
```

`X-Accel-Buffering: no` is there for nginx. Without it, nginx holds the stream back
and you get "nothing, nothing, nothing, then everything at once when it is over".

## You cannot tell when the client goes away

**This is the most important thing about SSE.**

Close the browser and helidon does not tell you. Writes either keep succeeding, or
**block forever** (Java has no socket write timeout). We tried it: sometimes we wrote
4.8MB over 30 seconds into nothing, sometimes it locked up solid after 8 seconds.

So jimble's `SseStream` **does not wait for a disconnect. It bounds the stream by lifetime.**

```conf
sse {
	max_duration = 5m   # default 5 minutes
	max_events           = 0     # 0 = unlimited
	retry         = 3s  # how long the client waits before reconnecting
}
```

```java
// change it for this route only
try (SseStream sse = context.response().sse(Duration.ofSeconds(30), 100)) {
```

`sse.isOpen()` tells you whether there is any lifetime left.
**It does not tell you whether the client is still connected.** Check it inside your loop, always.

```java
for (Data row : rows) {

	if (!sse.isOpen()) {
		break;
	}

	sse.send("progress", ...);

}
```

## Reconnecting

`EventSource` reconnects on its own when the stream breaks. `retry` is the interval.

**Which means the client comes straight back when you cut it at the end of its lifetime.**
Do not build a stream that runs forever. "Stream for five minutes, cut, get reconnected" is fine.

To tell the client where to pick up again, put an `id` on your events.
It comes back in the `Last-Event-ID` header on the reconnect.

## Where to use it

| Use | Good fit? |
| --- | --- |
| Progress bars, streaming logs out | Yes |
| Pushing notifications | Yes |
| Two-way exchange | Go to [WebSocket](./websocket) |
| Many connections at once, held for a long time | One thread per connection. Virtual threads make that cheap, but keep the lifetime short |
